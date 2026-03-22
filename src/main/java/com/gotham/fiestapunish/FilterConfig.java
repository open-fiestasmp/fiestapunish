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

public class FilterConfig {

    static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("fiestapunish");
    private static final Path WORDS_FILE    = CONFIG_DIR.resolve("banned_words.json");
    private static final Path PHRASES_FILE  = CONFIG_DIR.resolve("banned_phrases.json");
    private static final Path SETTINGS_FILE = CONFIG_DIR.resolve("settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Set<String>  bannedWords   = new LinkedHashSet<>();
    private static List<String> bannedPhrases = new ArrayList<>();
    private static String  censorChar    = "#";
    private static boolean logToConsole  = true;
    private static boolean wholeWordOnly = false;

    private static final List<String> DEFAULT_WORDS = Arrays.asList(
        "fuck", "shit", "ass", "bitch", "bastard", "cunt", "dick", "cock",
        "pussy", "asshole", "motherfucker", "fag", "faggot", "nigger", "nigga",
        "retard", "whore", "slut", "piss", "bollocks", "wanker", "twat",
        "kike", "spic", "chink", "gook", "cracker", "kys"
    );

    private static final List<String> DEFAULT_PHRASES = Arrays.asList(
        "i hope you die", "i hope u die", "go kill yourself", "go kys",
        "i will kill you", "i'm going to kill you", "im going to kill you",
        "you should die", "u should die", "kill urself", "end your life",
        "send nudes", "i want to rape", "i wanna rape",
        "i know where you live", "i will swat you", "i'm going to swat",
        "go back to your country", "free robux", "free nitro"
    );

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadWords(); loadPhrases(); loadSettings();
        } catch (IOException e) {
            FiestaPunishMod.LOGGER.error("[FiestaPunish] Config error: {}", e.getMessage());
        }
    }

    private static void loadWords() throws IOException {
        if (!Files.exists(WORDS_FILE)) {
            try (Writer w = Files.newBufferedWriter(WORDS_FILE)) { GSON.toJson(DEFAULT_WORDS, w); }
            bannedWords = new LinkedHashSet<>(DEFAULT_WORDS);
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
        if (!Files.exists(SETTINGS_FILE)) { saveSettings(); return; }
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

    public static void saveSettings() {
        try (Writer w = Files.newBufferedWriter(SETTINGS_FILE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("censorChar", censorChar); m.put("logToConsole", logToConsole); m.put("wholeWordOnly", wholeWordOnly);
            GSON.toJson(m, w);
        } catch (IOException e) { FiestaPunishMod.LOGGER.error("[FiestaPunish] Save error: {}", e.getMessage()); }
    }

    public static void saveWords() {
        try (Writer w = Files.newBufferedWriter(WORDS_FILE)) {
            List<String> s = new ArrayList<>(bannedWords); Collections.sort(s); GSON.toJson(s, w);
        } catch (IOException e) { FiestaPunishMod.LOGGER.error("[FiestaPunish] Save words error: {}", e.getMessage()); }
    }

    public static void savePhrases() {
        try (Writer w = Files.newBufferedWriter(PHRASES_FILE)) { GSON.toJson(bannedPhrases, w); }
        catch (IOException e) { FiestaPunishMod.LOGGER.error("[FiestaPunish] Save phrases error: {}", e.getMessage()); }
    }

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

    public static boolean addWord(String w) {
        boolean a = bannedWords.add(w.toLowerCase(Locale.ROOT)); if (a) saveWords(); return a;
    }
    public static boolean removeWord(String w) {
        boolean r = bannedWords.remove(w.toLowerCase(Locale.ROOT)); if (r) saveWords(); return r;
    }
    public static boolean addPhrase(String p) {
        String pl = p.toLowerCase(Locale.ROOT);
        if (bannedPhrases.contains(pl)) return false;
        bannedPhrases.add(pl); savePhrases(); return true;
    }
    public static boolean removePhrase(String p) {
        boolean r = bannedPhrases.remove(p.toLowerCase(Locale.ROOT)); if (r) savePhrases(); return r;
    }
    public static void reload() { load(); }
}
