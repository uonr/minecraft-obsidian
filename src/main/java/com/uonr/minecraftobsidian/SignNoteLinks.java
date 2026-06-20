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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a sign's text to an Obsidian note via per-vault {@code Minecraft Sign.md} files.
 *
 * <p>Each vault keeps its own mapping file at its root, holding {@code `key` -> [[Note]]} entries.
 * Storing the target as a wikilink lets Obsidian keep it up to date when the note is moved or
 * renamed, so the link survives reorganization for free. The sign itself only carries the human
 * readable key, and the owning vault is found by scanning all vaults at lookup time.
 */
final class SignNoteLinks {
    static final String MAPPING_FILE_NAME = "Minecraft Sign.md";

    record Resolution(ObsidianVault vault, String linkTarget, boolean ambiguous) {
    }

    record BindResult(boolean success, boolean ambiguous, String vaultName) {
    }

    private record ParsedEntry(String key, String linkRaw) {
    }

    private record CachedFile(long modified, Map<String, String> entries) {
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
        String matchedTarget = null;
        int matches = 0;
        for (ObsidianVault vault : vaults) {
            String target = entriesFor(vault).get(key);
            if (target != null) {
                matches++;
                if (matchedVault == null) {
                    matchedVault = vault;
                    matchedTarget = target;
                }
            }
        }
        if (matchedVault == null) {
            return Optional.empty();
        }
        return Optional.of(new Resolution(matchedVault, matchedTarget, matches > 1));
    }

    /** Writes {@code key -> [[file]]} into the vault referenced by {@code vaultRef} (its id or name). */
    BindResult bind(String key, String vaultRef, String file) {
        refresh();
        ObsidianVault target = findVault(vaultRef);
        if (target == null || key == null || key.isEmpty() || file == null || file.isBlank()) {
            return new BindResult(false, false, target == null ? null : target.name());
        }

        boolean usedElsewhere = false;
        for (ObsidianVault vault : vaults) {
            if (!vault.id().equals(target.id()) && entriesFor(vault).containsKey(key)) {
                usedElsewhere = true;
                break;
            }
        }

        boolean ok = writeEntry(target, key, file);
        if (ok) {
            cache.remove(mappingFile(target));
            refresh();
        }
        return new BindResult(ok, usedElsewhere, target.name());
    }

    /** Builds an {@code obsidian://open} URL using the vault id (stable across renames). */
    static String openUrl(Resolution resolution) {
        String file = fileFromLink(resolution.linkTarget());
        return "obsidian://open?vault=" + encode(resolution.vault().id()) + "&file=" + encode(file);
    }

    /** A readable {@code obsidian://open} URL (vault name, undecoded path) for on-screen previews. */
    static String displayUrl(Resolution resolution) {
        return "obsidian://open?vault=" + resolution.vault().name() + "&file=" + fileFromLink(resolution.linkTarget());
    }

    private Map<String, String> entriesFor(ObsidianVault vault) {
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

    private boolean writeEntry(ObsidianVault vault, String key, String file) {
        Path path = mappingFile(vault);
        String entryLine = "- " + MarkdownInlineCode.encode(key) + " → [[" + stripMdExtension(file) + "]]";
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

    private static Map<String, String> parse(Path file) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                parseLine(line).ifPresent(entry -> entries.putIfAbsent(entry.key(), entry.linkRaw()));
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
        return Optional.of(new ParsedEntry(key, linkRaw));
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
