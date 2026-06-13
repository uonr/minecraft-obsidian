package com.uonr.minecraftobsidian;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class ObsidianUrls {
    private static final String OBSIDIAN_SCHEME = "obsidian";

    private ObsidianUrls() {
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
