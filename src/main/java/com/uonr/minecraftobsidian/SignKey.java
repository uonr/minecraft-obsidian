package com.uonr.minecraftobsidian;

import java.text.Normalizer;

/**
 * Turns the text on a sign into a stable lookup key. The same rule is applied on both ends (when
 * reading a sign and when reading {@code Minecraft Sign.md}) so "what is on the sign" matches.
 */
final class SignKey {
    private SignKey() {
    }

    /** Strip each line, drop blank lines, collapse inner whitespace, join with one space, normalize to NFC. */
    static String fromLines(String... lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String collapsed = line.strip().replaceAll("\\s+", " ");
            if (collapsed.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(collapsed);
        }
        if (builder.length() == 0) {
            return "";
        }
        return Normalizer.normalize(builder.toString(), Normalizer.Form.NFC);
    }

    /** Normalizes a raw key (e.g. read from the mapping file) the same way a sign would be normalized. */
    static String normalize(String raw) {
        return fromLines(raw);
    }
}
