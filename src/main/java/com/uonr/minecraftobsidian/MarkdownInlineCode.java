package com.uonr.minecraftobsidian;

import java.util.Optional;

/**
 * Encodes and decodes CommonMark inline code spans. Sign keys are stored inside backtick fences so
 * arbitrary sign text (brackets, pipes, arrows, spaces) cannot collide with the surrounding
 * Markdown or wikilink syntax in {@code Minecraft Sign.md}.
 */
final class MarkdownInlineCode {
    private MarkdownInlineCode() {
    }

    record Span(String content, int start, int end) {
    }

    /** Wraps content in a fence long enough that the content's own backticks cannot close it. */
    static String encode(String content) {
        String fence = "`".repeat(longestBacktickRun(content) + 1);
        boolean pad = content.isEmpty()
                || content.startsWith("`")
                || content.endsWith("`")
                || (content.startsWith(" ") && content.endsWith(" "));
        String inner = pad ? " " + content + " " : content;
        return fence + inner + fence;
    }

    /** Finds the first inline code span on a single line and decodes its content. */
    static Optional<Span> firstSpan(String line) {
        int n = line.length();
        int i = 0;
        while (i < n) {
            if (line.charAt(i) != '`') {
                i++;
                continue;
            }

            int openLen = runLength(line, i);
            int contentStart = i + openLen;
            int j = contentStart;
            while (j < n) {
                if (line.charAt(j) != '`') {
                    j++;
                    continue;
                }
                int closeLen = runLength(line, j);
                if (closeLen == openLen) {
                    return Optional.of(new Span(decode(line.substring(contentStart, j)), i, j + closeLen));
                }
                j += closeLen;
            }

            // No closing fence of equal length: the opening run is literal, keep scanning past it.
            i = contentStart;
        }
        return Optional.empty();
    }

    private static String decode(String content) {
        // CommonMark: strip one leading and trailing space when both are present and the span is not all spaces.
        if (content.length() >= 2
                && content.charAt(0) == ' '
                && content.charAt(content.length() - 1) == ' '
                && !content.strip().isEmpty()) {
            return content.substring(1, content.length() - 1);
        }
        return content;
    }

    private static int runLength(String value, int start) {
        int i = start;
        while (i < value.length() && value.charAt(i) == '`') {
            i++;
        }
        return i - start;
    }

    private static int longestBacktickRun(String value) {
        int max = 0;
        int current = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '`') {
                current++;
                max = Math.max(max, current);
            } else {
                current = 0;
            }
        }
        return max;
    }
}
