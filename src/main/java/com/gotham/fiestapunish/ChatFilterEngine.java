package com.gotham.fiestapunish;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/**
 * Core filtering engine for FiestaPunish.
 *
 * Detection pipeline per message:
 *  1. Normalize: strip accents, map homoglyphs (𝔣 → f), map leetspeak (3 → e, @ → a, …)
 *  2. Match banned PHRASES first (multi-word, censored in-place in the original)
 *  3. Match banned WORDS (single tokens, censored in-place in the original)
 *
 * Both steps work on the *normalized* shadow of the original string so that
 * leet/unicode tricks are caught, but the replacement is applied to the
 * *original* indices so the resulting message looks natural.
 */
public class ChatFilterEngine {

    // ── Public API ────────────────────────────────────────────────────────────

    public static FilterResult filter(String original) {
        if (original == null || original.isEmpty()) return new FilterResult(original, false);

        String normalized = normalize(original);
        boolean censored  = false;

        // Work on a char array of the original so we can mask selectively
        char[] chars = original.toCharArray();

        // 1. Phrases first (they can span multiple words, take priority)
        for (String phrase : FilterConfig.getBannedPhrases()) {
            if (phrase.isEmpty()) continue;
            String normPhrase = normalize(phrase);
            int idx = normalized.toLowerCase(Locale.ROOT).indexOf(normPhrase.toLowerCase(Locale.ROOT));
            while (idx >= 0) {
                // find end position in normalized string
                int end = idx + normPhrase.length();
                // Map back to original-string span (lengths match after normalize,
                // because our normalizer is character-preserving in length)
                maskChars(chars, idx, end);
                censored = true;
                idx = normalized.toLowerCase(Locale.ROOT).indexOf(normPhrase.toLowerCase(Locale.ROOT), end);
            }
        }

        // 2. Individual words
        for (String word : FilterConfig.getBannedWords()) {
            if (word.isEmpty()) continue;
            // If the word contains spaces it's really a phrase — handle like phrase
            String normWord = normalize(word).toLowerCase(Locale.ROOT);
            String normMsg  = normalized.toLowerCase(Locale.ROOT);

            String patternStr = FilterConfig.isWholeWordOnly()
                    ? "(?i)\\b" + Pattern.quote(normWord) + "\\b"
                    : "(?i)"    + Pattern.quote(normWord);
            Matcher m = Pattern.compile(patternStr).matcher(normMsg);
            while (m.find()) {
                maskChars(chars, m.start(), m.end());
                censored = true;
            }
        }

        return new FilterResult(new String(chars), censored);
    }

    // ── Normalizer ────────────────────────────────────────────────────────────

    /**
     * Returns a same-length normalized version of the input where:
     *  - Unicode accents / diacritics are stripped  (é → e)
     *  - Common homoglyphs are mapped                (0 → o, 1 → i/l, 3 → e, …)
     *  - Leet-speak digits/symbols are mapped        (@ → a, $ → s, 4 → a, 5 → s, 7 → t)
     *  - Fancy Unicode letters are mapped to ASCII   (𝔣 → f, ａ → a, …)
     *
     * The output is guaranteed to have the SAME LENGTH as the input so that
     * index-based masking works correctly.
     */
    public static String normalize(String input) {
        // Step 1 — Unicode NFKD decomposition collapses fancy letters
        String nfkd = Normalizer.normalize(input, Normalizer.Form.NFKD);

        // Step 2 — rebuild at original length, mapping char by char
        // (NFKD can add combining chars; we strip them in the loop)
        StringBuilder sb = new StringBuilder(input.length());
        int origIdx = 0;
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            int cpLen = Character.charCount(cp);

            // Get the NFKD representation of just this codepoint
            String cpNfkd = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFKD);
            // Strip combining characters (category Mn) and take the base char
            String base = cpNfkd.replaceAll("\\p{Mn}", "");
            char mapped = base.isEmpty() ? (char) cp : base.charAt(0);

            // Step 3 — homoglyph / leet map
            sb.append(leetMap(mapped));

            i += cpLen;
            origIdx++;
        }

        // Pad/trim to exact input length (safety — should never be needed)
        while (sb.length() < input.length()) sb.append(' ');
        return sb.length() > input.length() ? sb.substring(0, input.length()) : sb.toString();
    }

    private static char leetMap(char c) {
        return switch (c) {
            case '0'            -> 'o';
            case '1'            -> 'i';  // also used as 'l' but 'i' covers more
            case '3'            -> 'e';
            case '4'            -> 'a';
            case '5'            -> 's';
            case '6'            -> 'g';
            case '7'            -> 't';
            case '8'            -> 'b';
            case '9'            -> 'g';
            case '@'            -> 'a';
            case '$'            -> 's';
            case '!'            -> 'i';
            case '+'            -> 't';
            case '|'            -> 'i';
            case '('            -> 'c';
            case '<'            -> 'c';
            default             -> c;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void maskChars(char[] chars, int start, int end) {
        String censorChar = FilterConfig.getCensorChar();
        char fill = (censorChar == null || censorChar.isEmpty()) ? '#' : censorChar.charAt(0);
        for (int i = start; i < end && i < chars.length; i++) {
            if (chars[i] != ' ') chars[i] = fill;  // preserve spaces within phrases
        }
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public static class FilterResult {
        public final String  filtered;
        public final boolean wasCensored;
        FilterResult(String f, boolean c) { filtered = f; wasCensored = c; }
    }
}
