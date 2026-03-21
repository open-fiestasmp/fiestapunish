package com.gotham.fiestapunish;

import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.filter.FilteredMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Hooks into Fabric API's ServerMessageEvents to intercept and filter chat.
 * This approach is version-stable — no fragile Mixin method-name matching needed.
 *
 * Flow:
 *  1. ALLOW_CHAT_MESSAGE — fires before broadcast. Used to block muted/banned players
 *     and trigger punishment logic.
 *  2. ServerMessageDecoratorEvent (CONTENT phase) — modifies the message text
 *     to replace banned content with ######.
 */
public class ChatEventHandler {

    public static void register() {

        // ── Phase 1: Decorator — rewrites message content with censored text ──
        ServerMessageDecoratorEvent.EVENT.register(
            ServerMessageDecoratorEvent.CONTENT_PHASE,
            (sender, message) -> {
                if (sender == null) return CompletableFuture.completedFuture(message);

                String original = message.getString();
                ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(original);

                if (!result.wasCensored) return CompletableFuture.completedFuture(message);

                // Return censored text
                return CompletableFuture.completedFuture(Text.literal(result.filtered));
            }
        );

        // ── Phase 2: ALLOW_CHAT_MESSAGE — runs after decoration, before broadcast ─
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
            (message, sender, params) -> {
                if (sender == null) return true;

                String uuid       = sender.getUuidAsString();
                String playerName = sender.getName().getString();

                // ── Banned? ───────────────────────────────────────────────────
                if (PunishmentManager.isBanned(uuid)) {
                    long until = PunishmentManager.getBannedUntilMs(uuid);
                    if (until == Long.MAX_VALUE)
                        sender.sendMessage(txt("§c✖ §7You are §cpermanently banned §7from chat."), false);
                    else
                        sender.sendMessage(txt("§c✖ §7Chat banned for §f" + fmt(until) + "§7."), false);
                    return false; // block message
                }

                // ── Muted? ────────────────────────────────────────────────────
                if (PunishmentManager.isMuted(uuid)) {
                    long until = PunishmentManager.getMutedUntilMs(uuid);
                    sender.sendMessage(txt("§c\uD83D\uDD07 §7You are §cmuted§7. Expires in §f" + fmt(until) + "§7."), false);
                    return false; // block message
                }

                // ── Check if the message was censored ─────────────────────────
                // The decorator already rewrote the text; we check the original
                String original = message.getSignedContent();
                ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(original);

                if (!result.wasCensored) return true; // clean, pass through

                if (FilterConfig.isLogToConsole()) {
                    FiestaPunishMod.LOGGER.info("[FiestaPunish] {} | [{}] -> [{}]",
                            playerName, original, result.filtered);
                }

                // ── Record offence and apply punishment ───────────────────────
                PunishmentManager.Action action = PunishmentManager.recordOffence(uuid, playerName);
                int warns = PunishmentManager.getWarnsToday(uuid);
                int left  = PunishmentManager.WARNS_BEFORE_MUTE - warns;

                switch (action) {

                    case WARN -> sender.sendMessage(txt(
                        "§e⚠ §7Watch your language! §8[Warning §c" + warns
                        + "§8/§c" + PunishmentManager.WARNS_BEFORE_MUTE
                        + "§8 — §e" + left + " left before mute§8]"
                    ), false);

                    case MUTE_SHORT -> {
                        sender.sendMessage(txt(
                            "§c\uD83D\uDD07 §7Muted for §f" + PunishmentManager.MUTE_SHORT_MINS
                            + " minutes §7for repeated inappropriate language."
                        ), false);
                        notifyStaff(sender, "§7" + playerName + " §7muted §c"
                            + PunishmentManager.MUTE_SHORT_MINS + "min §8(30 warnings)");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} muted {}min.", playerName, PunishmentManager.MUTE_SHORT_MINS);
                    }

                    case KICK_AND_MUTE_LONG -> {
                        sender.sendMessage(txt(
                            "§4✖ §cMuted 24h and being kicked — muted §f3 times§c."
                        ), false);
                        sender.getServer().execute(() ->
                            sender.networkHandler.disconnect(Text.literal(
                                "§cKicked by FiestaPunish\n"
                                + "§7Muted 3 times — chat locked for 24 hours.\n"
                                + "§eYou may rejoin."
                            ))
                        );
                        notifyStaff(sender, "§7" + playerName + " §7kicked & muted §c24h §8(3 mutes)");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} kicked + muted 24h.", playerName);
                    }

                    case BAN -> {
                        sender.getServer().execute(() ->
                            sender.networkHandler.disconnect(Text.literal(
                                "§c§lYou have been BANNED\n"
                                + "§r§7Reason: Muted 5 times in one month."
                            ))
                        );
                        notifyStaff(sender, "§c§l" + playerName + " §r§7was §c§lBANNED §r§8(5 monthly mutes)");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} BANNED.", playerName);
                    }

                    case ALREADY_MUTED -> {
                        long until = PunishmentManager.getMutedUntilMs(uuid);
                        sender.sendMessage(txt("§c\uD83D\uDD07 §7Still muted — §f" + fmt(until) + " §7left."), false);
                        return false;
                    }

                    case BANNED -> { return false; }
                }

                // Allow the (already-decorated/censored) message through
                return true;
            }
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void notifyStaff(ServerPlayerEntity sender, String msg) {
        if (sender.getServer() == null) return;
        Text t = Text.literal("§8[§6FiestaPunish§8] " + msg);
        sender.getServer().getPlayerManager().getPlayerList()
            .stream().filter(p -> p.hasPermissionLevel(2))
            .forEach(p -> p.sendMessage(t, false));
    }

    private static Text txt(String s) { return Text.literal(s); }

    private static String fmt(long untilMs) {
        if (untilMs == Long.MAX_VALUE) return "permanently";
        long diff = untilMs - System.currentTimeMillis();
        if (diff <= 0) return "0s";
        long h = TimeUnit.MILLISECONDS.toHours(diff);
        long m = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        long s = TimeUnit.MILLISECONDS.toSeconds(diff) % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
