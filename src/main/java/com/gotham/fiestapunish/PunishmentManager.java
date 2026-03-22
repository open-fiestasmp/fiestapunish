package com.gotham.fiestapunish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentManager {

    public static final int  WARNS_BEFORE_MUTE      = 30;
    public static final int  MUTES_BEFORE_KICK_MUTE = 3;
    public static final int  MUTES_BEFORE_BAN        = 5;
    public static final long MUTE_SHORT_MINS         = 30;
    public static final long MUTE_LONG_HOURS         = 24;

    private static final Path DATA_FILE =
        FabricLoader.getInstance().getConfigDir().resolve("fiestapunish").resolve("punishments.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, PlayerRecord> RECORDS = new ConcurrentHashMap<>();

    public static void load() {
        if (!Files.exists(DATA_FILE)) return;
        try (Reader r = Files.newBufferedReader(DATA_FILE)) {
            Type t = new TypeToken<Map<String, PlayerRecord>>(){}.getType();
            Map<String, PlayerRecord> loaded = GSON.fromJson(r, t);
            if (loaded != null) RECORDS.putAll(loaded);
        } catch (IOException e) {
            FiestaPunishMod.LOGGER.error("[FiestaPunish] Failed to load punishments: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(DATA_FILE)) { GSON.toJson(RECORDS, w); }
        } catch (IOException e) {
            FiestaPunishMod.LOGGER.error("[FiestaPunish] Failed to save punishments: {}", e.getMessage());
        }
    }

    public static Action recordOffence(String uuid, String playerName) {
        PlayerRecord rec = RECORDS.computeIfAbsent(uuid, k -> new PlayerRecord());
        rec.resetIfNeeded();

        if (rec.bannedUntil != null) {
            if (rec.bannedUntil == Long.MAX_VALUE || System.currentTimeMillis() < rec.bannedUntil)
                return Action.BANNED;
            else rec.bannedUntil = null;
        }

        if (rec.mutedUntil != null) {
            if (System.currentTimeMillis() < rec.mutedUntil)
                return Action.ALREADY_MUTED;
            else rec.mutedUntil = null;
        }

        rec.warnsToday++;
        save();

        if (rec.warnsToday >= WARNS_BEFORE_MUTE) {
            rec.warnsToday = 0;
            rec.mutesThisMonth++;
            rec.shortMutesThisRound++;

            if (rec.mutesThisMonth >= MUTES_BEFORE_BAN) {
                rec.bannedUntil = Long.MAX_VALUE;
                save();
                return Action.BAN;
            }

            if (rec.shortMutesThisRound >= MUTES_BEFORE_KICK_MUTE) {
                rec.shortMutesThisRound = 0;
                rec.mutedUntil = System.currentTimeMillis() + hoursToMs(MUTE_LONG_HOURS);
                save();
                return Action.KICK_AND_MUTE_LONG;
            }

            rec.mutedUntil = System.currentTimeMillis() + minsToMs(MUTE_SHORT_MINS);
            save();
            return Action.MUTE_SHORT;
        }

        return Action.WARN;
    }

    public static boolean isMuted(String uuid) {
        PlayerRecord rec = RECORDS.get(uuid);
        if (rec == null) return false;
        rec.resetIfNeeded();
        return rec.mutedUntil != null && System.currentTimeMillis() < rec.mutedUntil;
    }

    public static boolean isBanned(String uuid) {
        PlayerRecord rec = RECORDS.get(uuid);
        if (rec == null) return false;
        if (rec.bannedUntil == null) return false;
        if (rec.bannedUntil == Long.MAX_VALUE) return true;
        return System.currentTimeMillis() < rec.bannedUntil;
    }

    public static long getMutedUntilMs(String uuid) {
        PlayerRecord rec = RECORDS.get(uuid);
        return (rec == null || rec.mutedUntil == null) ? 0 : rec.mutedUntil;
    }

    public static long getBannedUntilMs(String uuid) {
        PlayerRecord rec = RECORDS.get(uuid);
        return (rec == null || rec.bannedUntil == null) ? 0 : rec.bannedUntil;
    }

    public static int getWarnsToday(String uuid) {
        PlayerRecord rec = RECORDS.get(uuid);
        if (rec == null) return 0;
        rec.resetIfNeeded();
        return rec.warnsToday;
    }

    public static PlayerRecord getRecord(String uuid) { return RECORDS.get(uuid); }

    public static boolean unmute(String uuid) {
        PlayerRecord rec = RECORDS.get(uuid);
        if (rec == null) return false;
        rec.mutedUntil = null; save(); return true;
    }

    public static boolean unban(String uuid) {
        PlayerRecord rec = RECORDS.get(uuid);
        if (rec == null) return false;
        rec.bannedUntil = null; save(); return true;
    }

    public static boolean reset(String uuid) {
        boolean had = RECORDS.remove(uuid) != null; save(); return had;
    }

    private static long minsToMs(long m)  { return m * 60_000L; }
    private static long hoursToMs(long h) { return h * 3_600_000L; }

    public enum Action { WARN, MUTE_SHORT, KICK_AND_MUTE_LONG, BAN, ALREADY_MUTED, BANNED }

    public static class PlayerRecord {
        public int    warnsToday          = 0;
        public int    mutesThisMonth      = 0;
        public int    shortMutesThisRound = 0;
        public Long   mutedUntil          = null;
        public Long   bannedUntil         = null;
        public String dayKey              = todayKey();
        public String monthKey            = monthKey();

        public void resetIfNeeded() {
            String today = todayKey(), month = monthKey();
            if (!today.equals(dayKey))  { warnsToday = 0; dayKey = today; }
            if (!month.equals(monthKey)) { mutesThisMonth = 0; shortMutesThisRound = 0; monthKey = month; }
        }
    }

    private static String todayKey() { return LocalDate.now(ZoneOffset.UTC).toString(); }
    private static String monthKey() {
        LocalDate d = LocalDate.now(ZoneOffset.UTC);
        return d.getYear() + "-" + d.getMonthValue();
    }
}
