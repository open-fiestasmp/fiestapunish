package com.gotham.fiestapunish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads and manages:
 *  - config/fiestapunish/banned_words.json   (single words + leet variants)
 *  - config/fiestapunish/banned_phrases.json (full sentence patterns)
 *  - config/fiestapunish/settings.json       (behaviour toggles)
 */
public class FilterConfig {

    static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("fiestapunish");
    private static final Path WORDS_FILE    = CONFIG_DIR.resolve("banned_words.json");
    private static final Path PHRASES_FILE  = CONFIG_DIR.resolve("banned_phrases.json");
    private static final Path SETTINGS_FILE = CONFIG_DIR.resolve("settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Runtime data ─────────────────────────────────────────────────────────
    private static Set<String>  bannedWords   = new LinkedHashSet<>();
    private static List<String> bannedPhrases = new ArrayList<>();

    // ── Settings ─────────────────────────────────────────────────────────────
    private static String  censorChar    = "#";
    private static boolean logToConsole  = true;
    private static boolean wholeWordOnly = false;

    // ── Defaults ─────────────────────────────────────────────────────────────
    private static final List<String> DEFAULT_WORDS = Arrays.asList(
        // Common profanity
        "fuck", "shit", "ass", "bitch", "bastard", "cunt",
        "dick", "cock", "pussy", "asshole", "motherfucker",
        "fag", "faggot", "nigger", "nigga", "retard",
        "whore", "slut", "piss", "bollocks", "wanker", "twat",
        // Leet / substitution variants are handled by the normalizer,
        // but keeping explicit variants here for extra coverage
        "f4ck", "sh1t", "a55", "b1tch", "d1ck", "c0ck",
        // Slurs and hate terms
        "kike", "spic", "chink", "gook", "wetback", "cracker",
        // Threats / severe
        "kys", "kill yourself", "go die", "end yourself"
    );

    private static final List<String> DEFAULT_PHRASES = Arrays.asList(
        // Death wishes / threats
        "i hope you die",
        "i hope u die",
        "go kill yourself",
        "go kys",
        "i will kill you",
        "i'm going to kill you",
        "im going to kill you",
        "i want to kill you",
        "you should die",
        "u should die",
        "kill urself",
        "end your life",
        // Sexual harassment
        "send nudes",
        "send pics",
        "show me your",
        "i want to rape",
        "i wanna rape",
        // Doxxing / irl threats
        "i know where you live",
        "i'll find your address",
        "i will swat you",
        "i'm going to swat",
        // Severe discrimination
        "go back to your country",
        "you don't belong here",
        // Spam / advertising patterns
        "free robux",
        "free nitro",
        "click this link",
        "join my server",
        "free gift card"
    );

    // ── Load / Save ──────────────────────────────────────────────────────────

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadWords();
            loadPhrases();
            loadSettings();
        } catch (IOException e) {
            FiestaPunishMod.LOGGER.error("[FiestaPunish] Config load error: {}", e.getMessage());
        }
    }

    private static void loadWords() throws IOException {
        if (!Files.exists(WORDS_FILE)) {
            try (Writer w = Files.newBufferedWriter(WORDS_FILE)) { GSON.toJson(DEFAULT_WORDS, w); }
            bannedWords = new LinkedHashSet<>(DEFAULT_WORDS);
            FiestaPunishMod.LOGGER.info("[FiestaPunish] Created default banned_words.json");
        } else {
            try (Reader r = Files.newBufferedReader(WORDS_FILE)) {
                Type t = new TypeToken<List<String>>(){}.getType();
                List<String> list = GSON.fromJson(r, t);
                bannedWords = new LinkedHashSet<>();
                if (list != null) list.forEach(w -> bannedWords.add(w.toLowerCase(Locale.ROOT)));
            }
        }
    }

    private static void loadPhrases() throws IOException {
        if (!Files.exists(PHRASES_FILE)) {
            try (Writer w = Files.newBufferedWriter(PHRASES_FILE)) { GSON.toJson(DEFAULT_PHRASES, w); }
            bannedPhrases = new ArrayList<>(DEFAULT_PHRASES);
            FiestaPunishMod.LOGGER.info("[FiestaPunish] Created default banned_phrases.json");
        } else {
            try (Reader r = Files.newBufferedReader(PHRASES_FILE)) {
                Type t = new TypeToken<List<String>>(){}.getType();
                List<String> list = GSON.fromJson(r, t);
                bannedPhrases = new ArrayList<>();
                if (list != null) list.forEach(p -> bannedPhrases.add(p.toLowerCase(Locale.ROOT)));
            }
        }
    }

    private static void loadSettings() throws IOException {
        if (!Files.exists(SETTINGS_FILE)) {
            saveSettings();
        } else {
            try (Reader r = Files.newBufferedReader(SETTINGS_FILE)) {
                Type t = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> m = GSON.fromJson(r, t);
                if (m != null) {
                    if (m.containsKey("censorChar"))    censorChar    = (String)  m.get("censorChar");
                    if (m.containsKey("logToConsole"))  logToConsole  = (Boolean) m.get("logToConsole");
                    if (m.containsKey("wholeWordOnly")) wholeWordOnly = (Boolean) m.get("wholeWordOnly");
                }
            }
        }
    }

    public static void saveSettings() {
        try (Writer w = Files.newBufferedWriter(SETTINGS_FILE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("censorChar",    censorChar);
            m.put("logToConsole",  logToConsole);
            m.put("wholeWordOnly", wholeWordOnly);
            GSON.toJson(m, w);
        } catch (IOException e) {
            FiestaPunishMod.LOGGER.error("[FiestaPunish] Failed to save settings: {}", e.getMessage());
        }
    }

    public static void saveWords() {
        try (Writer w = Files.newBufferedWriter(WORDS_FILE)) {
            List<String> sorted = new ArrayList<>(bannedWords);
            Collections.sort(sorted);
            GSON.toJson(sorted, w);
        } catch (IOException e) {
            FiestaPunishMod.LOGGER.error("[FiestaPunish] Failed to save words: {}", e.getMessage());
        }
    }

    public static void savePhrases() {
        try (Writer w = Files.newBufferedWriter(PHRASES_FILE)) {
            GSON.toJson(bannedPhrases, w);
        } catch (IOException e) {
            FiestaPunishMod.LOGGER.error("[FiestaPunish] Failed to save phrases: {}", e.getMessage());
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public static Set<String>  getBannedWords()   { return bannedWords; }
    public static List<String> getBannedPhrases() { return bannedPhrases; }
    public static String  getCensorChar()         { return censorChar; }
    public static boolean isLogToConsole()        { return logToConsole; }
    public static boolean isWholeWordOnly()       { return wholeWordOnly; }
    public static int     getWordCount()          { return bannedWords.size(); }
    public static int     getPhraseCount()        { return bannedPhrases.size(); }

    public static void setCensorChar(String c)     { censorChar    = c; saveSettings(); }
    public static void setLogToConsole(boolean b)  { logToConsole  = b; saveSettings(); }
    public static void setWholeWordOnly(boolean b) { wholeWordOnly = b; saveSettings(); }

    public static boolean addWord(String word) {
        boolean added = bannedWords.add(word.toLowerCase(Locale.ROOT));
        if (added) saveWords();
        return added;
    }

    public static boolean removeWord(String word) {
        boolean removed = bannedWords.remove(word.toLowerCase(Locale.ROOT));
        if (removed) saveWords();
        return removed;
    }

    public static boolean addPhrase(String phrase) {
        String p = phrase.toLowerCase(Locale.ROOT);
        if (bannedPhrases.contains(p)) return false;
        bannedPhrases.add(p);
        savePhrases();
        return true;
    }

    public static boolean removePhrase(String phrase) {
        boolean removed = bannedPhrases.remove(phrase.toLowerCase(Locale.ROOT));
        if (removed) savePhrases();
        return removed;
    }

    public static void reload() { load(); }
}
