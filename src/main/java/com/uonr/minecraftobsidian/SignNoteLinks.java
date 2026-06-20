package com.uonr.minecraftobsidian;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a sign's text to an Obsidian target via per-vault {@code Minecraft Sign.md} files.
 *
 * <p>Each vault keeps its own mapping file at its root. A simple "open this note" link is stored as
 * a wikilink ({@code `key` -> [[Note]]}) so Obsidian keeps it up to date when the note is moved or
 * renamed. Any other Obsidian URL (search, create, a heading/block anchor, advanced-uri, ...) is
 * kept verbatim as a Markdown link ({@code `key` -> [link](obsidian://...)}). The sign only carries
 * the human readable key; the owning vault is found by scanning all vaults at lookup time.
 */
final class SignNoteLinks {
    static final String MAPPING_FILE_NAME = "Minecraft Sign.md";

    /** A resolved target: either a wikilink (note path) or a verbatim Obsidian URL. */
    record NoteLink(boolean wikilink, String value) {
    }

    record Resolution(ObsidianVault vault, NoteLink link, boolean ambiguous) {
    }

    record BindResult(boolean success, boolean ambiguous, String vaultName) {
    }

    private record ParsedEntry(String key, NoteLink link) {
    }

    private record CachedFile(long modified, Map<String, NoteLink> entries) {
    }

    private List<ObsidianVault> vaults = List.of();
    private long vaultsStamp = Long.MIN_VALUE;
    private final Map<Path, CachedFile> cache = new LinkedHashMap<>();

    /** Re-reads the vault registry and any changed mapping files. A no-op when nothing changed. */
    void refresh() {
        Path registry = ObsidianVaults.configFile();
        long stamp = lastModified(registry);
        if (stamp != vaultsStamp) {
            vaults = ObsidianVaults.discover();
            vaultsStamp = stamp;
        }
        for (ObsidianVault vault : vaults) {
            Path file = mappingFile(vault);
            long modified = lastModified(file);
            CachedFile cached = cache.get(file);
            if (cached == null || cached.modified() != modified) {
                cache.put(file, new CachedFile(modified, parse(file)));
            }
        }
    }

    boolean contains(String key) {
        return resolve(key).isPresent();
    }

    /** First vault whose mapping file contains {@code key}; flags whether more than one vault matched. */
    Optional<Resolution> resolve(String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        ObsidianVault matchedVault = null;
        NoteLink matchedLink = null;
        int matches = 0;
        for (ObsidianVault vault : vaults) {
            NoteLink link = entriesFor(vault).get(key);
            if (link != null) {
                matches++;
                if (matchedVault == null) {
                    matchedVault = vault;
                    matchedLink = link;
                }
            }
        }
        if (matchedVault == null) {
            return Optional.empty();
        }
        return Optional.of(new Resolution(matchedVault, matchedLink, matches > 1));
    }

    /**
     * Writes {@code key -> link} for the clipboard {@code url}. A simple note-open URL becomes a
     * wikilink in the owning vault; anything else is stored verbatim as a Markdown link.
     */
    BindResult bind(String key, String url) {
        refresh();
        if (key == null || key.isEmpty() || url == null || url.isBlank() || vaults.isEmpty()) {
            return new BindResult(false, false, null);
        }

        Optional<ObsidianUrls.OpenTarget> open = ObsidianUrls.isSimpleNoteOpen(url)
                ? ObsidianUrls.parseOpenTarget(url)
                : Optional.empty();
        ObsidianVault noteVault = open.map(target -> findVault(target.vault())).orElse(null);

        ObsidianVault target;
        NoteLink link;
        if (open.isPresent() && noteVault != null) {
            target = noteVault;
            link = new NoteLink(true, stripMdExtension(open.get().file()));
        } else {
            ObsidianVault byParam = findVault(ObsidianUrls.queryParam(url, "vault").orElse(null));
            target = byParam != null ? byParam : vaults.get(0);
            link = new NoteLink(false, url.trim());
        }

        boolean usedElsewhere = false;
        for (ObsidianVault vault : vaults) {
            if (!vault.id().equals(target.id()) && entriesFor(vault).containsKey(key)) {
                usedElsewhere = true;
                break;
            }
        }

        boolean ok = writeEntry(target, key, link);
        if (ok) {
            cache.remove(mappingFile(target));
            refresh();
        }
        return new BindResult(ok, usedElsewhere, target.name());
    }

    /** The URL actually opened. Wikilinks use the vault id (stable across renames); URLs pass through. */
    static String openUrl(Resolution resolution) {
        NoteLink link = resolution.link();
        if (!link.wikilink()) {
            return link.value();
        }
        String file = fileFromLink(link.value());
        return "obsidian://open?vault=" + encode(resolution.vault().id()) + "&file=" + encode(file);
    }

    /** A readable form for on-screen previews. */
    static String displayUrl(Resolution resolution) {
        NoteLink link = resolution.link();
        if (!link.wikilink()) {
            return link.value();
        }
        return "obsidian://open?vault=" + resolution.vault().name() + "&file=" + fileFromLink(link.value());
    }

    private Map<String, NoteLink> entriesFor(ObsidianVault vault) {
        CachedFile cached = cache.get(mappingFile(vault));
        return cached == null ? Map.of() : cached.entries();
    }

    private ObsidianVault findVault(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String decoded = percentDecode(ref);
        for (ObsidianVault vault : vaults) {
            if (vault.id().equals(decoded)) {
                return vault;
            }
        }
        for (ObsidianVault vault : vaults) {
            if (vault.name().equals(decoded)) {
                return vault;
            }
        }
        for (ObsidianVault vault : vaults) {
            if (vault.name().equalsIgnoreCase(decoded)) {
                return vault;
            }
        }
        return null;
    }

    private boolean writeEntry(ObsidianVault vault, String key, NoteLink link) {
        Path path = mappingFile(vault);
        String entryLine = "- " + MarkdownInlineCode.encode(key) + " → " + render(link);
        try {
            List<String> lines = Files.isRegularFile(path)
                    ? new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8))
                    : new ArrayList<>();

            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                Optional<ParsedEntry> parsed = parseLine(lines.get(i));
                if (parsed.isPresent() && parsed.get().key().equals(key)) {
                    lines.set(i, entryLine);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                if (lines.isEmpty()) {
                    lines.add("# Minecraft Sign");
                    lines.add("");
                }
                lines.add(entryLine);
            }

            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not write Obsidian mapping file {}", path, exception);
            return false;
        }
    }

    /** Renders the value side of an entry. URLs with spaces or parentheses use the angle-bracket form. */
    private static String render(NoteLink link) {
        if (link.wikilink()) {
            return "[[" + link.value() + "]]";
        }
        String url = link.value();
        boolean needsAngles = url.indexOf('(') >= 0 || url.indexOf(')') >= 0 || containsWhitespace(url);
        return needsAngles ? "[link](<" + url + ">)" : "[link](" + url + ")";
    }

    private static Map<String, NoteLink> parse(Path file) {
        Map<String, NoteLink> entries = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                parseLine(line).ifPresent(entry -> entries.putIfAbsent(entry.key(), entry.link()));
            }
        } catch (IOException | RuntimeException exception) {
            MinecraftObsidianClient.LOGGER.warn("Could not read Obsidian mapping file {}", file, exception);
        }
        return entries;
    }

    private static Optional<ParsedEntry> parseLine(String line) {
        Optional<MarkdownInlineCode.Span> span = MarkdownInlineCode.firstSpan(line);
        if (span.isEmpty()) {
            return Optional.empty();
        }
        String key = SignKey.normalize(span.get().content());
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String rest = line.substring(span.get().end());

        Optional<String> markdownUrl = markdownLinkUrl(rest);
        if (markdownUrl.isPresent() && ObsidianUrls.isObsidianUrl(markdownUrl.get())) {
            return Optional.of(new ParsedEntry(key, new NoteLink(false, markdownUrl.get())));
        }

        int open = rest.indexOf("[[");
        if (open < 0) {
            return Optional.empty();
        }
        int close = rest.indexOf("]]", open + 2);
        if (close < 0) {
            return Optional.empty();
        }
        String linkRaw = rest.substring(open + 2, close).trim();
        if (linkRaw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedEntry(key, new NoteLink(true, linkRaw)));
    }

    /** Extracts the destination of the first {@code [label](url)} or {@code [label](<url>)} on the line. */
    private static Optional<String> markdownLinkUrl(String rest) {
        int label = rest.indexOf('[');
        if (label < 0) {
            return Optional.empty();
        }
        int sep = rest.indexOf("](", label);
        if (sep < 0) {
            return Optional.empty();
        }
        int urlStart = sep + 2;
        if (urlStart >= rest.length()) {
            return Optional.empty();
        }
        String url;
        if (rest.charAt(urlStart) == '<') {
            int end = rest.indexOf('>', urlStart + 1);
            if (end < 0) {
                return Optional.empty();
            }
            url = rest.substring(urlStart + 1, end);
        } else {
            int end = rest.indexOf(')', urlStart);
            if (end < 0) {
                return Optional.empty();
            }
            url = rest.substring(urlStart, end);
        }
        url = url.trim();
        return url.isEmpty() ? Optional.empty() : Optional.of(url);
    }

    /** Reduces a wikilink target to the bare file path: drops a {@code |alias} and any {@code #}/{@code ^} ref. */
    private static String fileFromLink(String linkTarget) {
        String target = linkTarget;
        int pipe = target.indexOf('|');
        if (pipe >= 0) {
            target = target.substring(0, pipe);
        }
        int cut = target.length();
        int heading = target.indexOf('#');
        if (heading >= 0) {
            cut = Math.min(cut, heading);
        }
        int block = target.indexOf('^');
        if (block >= 0) {
            cut = Math.min(cut, block);
        }
        return stripMdExtension(target.substring(0, cut).trim());
    }

    private static String stripMdExtension(String value) {
        if (value.length() > 3 && value.regionMatches(true, value.length() - 3, ".md", 0, 3)) {
            return value.substring(0, value.length() - 3);
        }
        return value;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static Path mappingFile(ObsidianVault vault) {
        return vault.root().resolve(MAPPING_FILE_NAME);
    }

    private static long lastModified(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        } catch (IOException exception) {
            return -1L;
        }
    }

    private static String encode(String value) {
        StringBuilder builder = new StringBuilder();
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int c = raw & 0xFF;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~' || c == '/';
            if (unreserved) {
                builder.append((char) c);
            } else {
                builder.append('%')
                        .append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return builder.toString();
    }

    private static String percentDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }
}
