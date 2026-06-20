package com.uonr.minecraftobsidian;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

final class ObsidianUrls {
    private static final String OBSIDIAN_SCHEME = "obsidian";

    private ObsidianUrls() {
    }

    /** The {@code vault} (may be null) and {@code file} of an {@code obsidian://open?...} URL. */
    record OpenTarget(String vault, String file) {
    }

    /** Parses a file-targeting Obsidian URL into its vault and file. Empty when there is no file. */
    static Optional<OpenTarget> parseOpenTarget(String url) {
        if (!isObsidianUrl(url)) {
            return Optional.empty();
        }
        try {
            String query = new URI(url.trim()).getRawQuery();
            if (query == null) {
                return Optional.empty();
            }
            String vault = null;
            String file = null;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String name = eq >= 0 ? pair.substring(0, eq) : pair;
                String value = eq >= 0 ? pair.substring(eq + 1) : "";
                if (name.equals("vault")) {
                    vault = decode(value);
                } else if (name.equals("file")) {
                    file = decode(value);
                }
            }
            if (file == null || file.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new OpenTarget(vault, file));
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    /**
     * True only for a plain "open this note" URL: action {@code open}, with just {@code vault} and
     * {@code file} (and no heading/block anchor). Those become wikilinks; everything else is kept
     * verbatim as a Markdown link.
     */
    static boolean isSimpleNoteOpen(String url) {
        if (!isObsidianUrl(url)) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            if (!"open".equalsIgnoreCase(uri.getHost())) {
                return false;
            }
            String query = uri.getRawQuery();
            if (query == null) {
                return false;
            }
            boolean hasFile = false;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String name = eq >= 0 ? pair.substring(0, eq) : pair;
                if (name.equals("file")) {
                    hasFile = true;
                    String file = decode(eq >= 0 ? pair.substring(eq + 1) : "");
                    if (file.indexOf('#') >= 0 || file.indexOf('^') >= 0) {
                        return false;
                    }
                } else if (!name.equals("vault")) {
                    return false;
                }
            }
            return hasFile;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    /** Value of a query parameter on an Obsidian URL, percent-decoded. */
    static Optional<String> queryParam(String url, String name) {
        if (!isObsidianUrl(url)) {
            return Optional.empty();
        }
        try {
            String query = new URI(url.trim()).getRawQuery();
            if (query == null) {
                return Optional.empty();
            }
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String key = eq >= 0 ? pair.substring(0, eq) : pair;
                if (key.equals(name)) {
                    return Optional.of(decode(eq >= 0 ? pair.substring(eq + 1) : ""));
                }
            }
            return Optional.empty();
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    static boolean isObsidianUrl(String value) {
        if (value == null) {
            return false;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty() || containsControlCharacter(trimmed)) {
            return false;
        }

        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            return scheme != null && OBSIDIAN_SCHEME.equals(scheme.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
