package com.uonr.minecraftobsidian;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class UrlPreview {
    private static final int MAX_PREVIEW_LENGTH = 180;

    private UrlPreview() {
    }

    static String fromUrl(String url) {
        String decoded = percentDecode(url);
        if (decoded.length() <= MAX_PREVIEW_LENGTH) {
            return decoded;
        }
        return decoded.substring(0, MAX_PREVIEW_LENGTH - 1) + "...";
    }

    private static String percentDecode(String value) {
        StringBuilder result = new StringBuilder(value.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        for (int i = 0; i < value.length();) {
            char current = value.charAt(i);
            if (current != '%' || i + 2 >= value.length()) {
                result.append(current);
                i++;
                continue;
            }

            bytes.reset();
            int start = i;
            while (i + 2 < value.length() && value.charAt(i) == '%') {
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    break;
                }
                bytes.write((high << 4) + low);
                i += 3;
            }

            if (bytes.size() == 0) {
                result.append(value.charAt(start));
                i = start + 1;
            } else {
                result.append(bytes.toString(StandardCharsets.UTF_8));
            }
        }

        return result.toString();
    }
}
