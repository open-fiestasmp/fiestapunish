package com.gotham.fiestapunish;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public class ChatFilterEngine {

    public static FilterResult filter(String original) {
        if (original == null || original.isEmpty()) return new FilterResult(original, false);

        String normalized = normalize(original);
        char[] chars = original.toCharArray();
        boolean censored = false;

        // Phrases first
        for (String phrase : FilterConfig.getBannedPhrases()) {
            if (phrase.isEmpty()) continue;
            String normPhrase = normalize(phrase).toLowerCase(Locale.ROOT);
            String normMsg = normalized.toLowerCase(Locale.ROOT);
            int idx = normMsg.indexOf(normPhrase);
            while (idx >= 0) {
                maskChars(chars, idx, idx + normPhrase.length());
                censored = true;
                idx = normMsg.indexOf(normPhrase, idx + normPhrase.length());
            }
        }

        // Individual words
        for (String word : FilterConfig.getBannedWords()) {
            if (word.isEmpty()) continue;
            String normWord = normalize(word).toLowerCase(Locale.ROOT);
            String normMsg  = normalized.toLowerCase(Locale.ROOT);
            String pat = FilterConfig.isWholeWordOnly()
                ? "(?i)\\b" + Pattern.quote(normWord) + "\\b"
                : "(?i)" + Pattern.quote(normWord);
            Matcher m = Pattern.compile(pat).matcher(normMsg);
            while (m.find()) {
                maskChars(chars, m.start(), m.end());
                censored = true;
            }
        }

        return new FilterResult(new String(chars), censored);
    }

    public static String normalize(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            int cpLen = Character.charCount(cp);
            String cpNfkd = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFKD);
            String base = cpNfkd.replaceAll("\\p{Mn}", "");
            char mapped = base.isEmpty() ? (char) cp : base.charAt(0);
            sb.append(leetMap(mapped));
            i += cpLen;
        }
        while (sb.length() < input.length()) sb.append(' ');
        return sb.length() > input.length() ? sb.substring(0, input.length()) : sb.toString();
    }

    private static char leetMap(char c) {
        return switch (c) {
            case '0' -> 'o';
            case '1' -> 'i';
            case '3' -> 'e';
            case '4' -> 'a';
            case '5' -> 's';
            case '6' -> 'g';
            case '7' -> 't';
            case '8' -> 'b';
            case '9' -> 'g';
            case '@' -> 'a';
            case '$' -> 's';
            case '!' -> 'i';
            case '+' -> 't';
            case '|' -> 'i';
            case '(' -> 'c';
            case '<' -> 'c';
            default  -> c;
        };
    }

    private static void maskChars(char[] chars, int start, int end) {
        String cc = FilterConfig.getCensorChar();
        char fill = (cc == null || cc.isEmpty()) ? '#' : cc.charAt(0);
        for (int i = start; i < end && i < chars.length; i++) {
            if (chars[i] != ' ') chars[i] = fill;
        }
    }

    public static class FilterResult {
        public final String  filtered;
        public final boolean wasCensored;
        FilterResult(String f, boolean c) { filtered = f; wasCensored = c; }
    }
}
