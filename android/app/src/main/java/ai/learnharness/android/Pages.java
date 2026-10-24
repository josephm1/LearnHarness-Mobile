package ai.learnharness.android;

import java.util.ArrayList;
import java.util.List;

/** Deterministic page boundaries: no animated scrolling or clipped long paragraphs. */
public final class Pages {
    private Pages() {}
    public static List<String> split(String text, int limit) {
        if (limit < 80) throw new IllegalArgumentException("Page size is too small");
        List<String> result = new ArrayList<>();
        String rest = text == null ? "" : text.trim();
        while (rest.length() > limit) {
            int end = rest.lastIndexOf("\n\n", limit);
            if (end < limit / 3) end = rest.lastIndexOf(' ', limit);
            if (end < limit / 3) end = limit;
            // Never bisect a UTF-16 surrogate pair.
            if (end > 0 && Character.isHighSurrogate(rest.charAt(end - 1))) end--;
            result.add(rest.substring(0, end).trim());
            rest = rest.substring(end).trim();
        }
        if (!rest.isEmpty() || result.isEmpty()) result.add(rest);
        return result;
    }
}
