package com.gotham.fiestapunish;

import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.TimeUnit;

/**
 * Chat filter and punishment handler for Minecraft 1.21.11 / fabric-api 0.141.3
 *
 * Confirmed from fabric-api 1.21.11 source (ChatTest.java):
 *   - Decorator lambda: (sender, message) -> Text  [inferred, no explicit types needed]
 *   - ALLOW_CHAT_MESSAGE: (message, sender, params) -> boolean
 *   - message.signedContent() returns String (confirmed from ChatTest line 80)
 *   - No CompletableFuture anywhere
 */
public class ChatEventHandler {

    public static void register() {

        // ── 1. Rewrite message content with censored text ─────────────────────
        // Decorator confirmed from fabric-api 1.21.11 ChatTest.java:
        //   ServerMessageDecoratorEvent.EVENT.register(CONTENT_PHASE, (sender, message) -> { ... })
        ServerMessageDecoratorEvent.EVENT.register(
            ServerMessageDecoratorEvent.CONTENT_PHASE,
            (sender, message) -> {
                if (sender == null) return message;
                String original = message.getString();
                ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(original);
                if (!result.wasCensored) return message;
                return Text.literal(result.filtered);
            }
        );

        // ── 2. Block muted/banned players + apply punishments ─────────────────
        // Confirmed from fabric-api 1.21.11 ChatTest.java:
        //   ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> ...)
        //   message.signedContent() -> String
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
            (message, sender, params) -> {
                if (sender == null) return true;

                String uuid       = sender.getUuidAsString();
                String playerName = sender.getName().getString();

                // Banned?
                if (PunishmentManager.isBanned(uuid)) {
                    long until = PunishmentManager.getBannedUntilMs(uuid);
                    if (until == Long.MAX_VALUE)
                        sender.sendMessage(Text.literal("§c✖ §7You are §cpermanently banned §7from chat."), false);
                    else
                        sender.sendMessage(Text.literal("§c✖ §7Chat banned for §f" + fmt(until) + "§7."), false);
                    return false;
                }

                // Muted?
                if (PunishmentManager.isMuted(uuid)) {
                    long until = PunishmentManager.getMutedUntilMs(uuid);
                    sender.sendMessage(Text.literal("§c\uD83D\uDD07 §7You are §cmuted§7. Expires in §f" + fmt(until) + "§7."), false);
                    return false;
                }

                // Was content censored?
                // signedContent() confirmed from fabric-api ChatTest.java line 80
                String original = message.signedContent();
                ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(original);
                if (!result.wasCensored) return true;

                if (FilterConfig.isLogToConsole()) {
                    FiestaPunishMod.LOGGER.info("[FiestaPunish] {} | [{}] -> [{}]",
                            playerName, original, result.filtered);
                }

                // Record offence and apply punishment
                PunishmentManager.Action action = PunishmentManager.recordOffence(uuid, playerName);
                int warns = PunishmentManager.getWarnsToday(uuid);
                int left  = PunishmentManager.WARNS_BEFORE_MUTE - warns;

                switch (action) {
                    case WARN -> sender.sendMessage(Text.literal(
                        "§e⚠ §7Watch your language! §8[Warning §c" + warns
                        + "§8/§c" + PunishmentManager.WARNS_BEFORE_MUTE
                        + "§8 — §e" + left + " left before mute§8]"
                    ), false);

                    case MUTE_SHORT -> {
                        sender.sendMessage(Text.literal(
                            "§c\uD83D\uDD07 §7Muted for §f" + PunishmentManager.MUTE_SHORT_MINS
                            + " minutes §7for repeated inappropriate language."
                        ), false);
                        notifyStaff(sender, "§7" + playerName + " §7muted §c"
                            + PunishmentManager.MUTE_SHORT_MINS + "min §8(30 warnings)");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} muted {}min.",
                            playerName, PunishmentManager.MUTE_SHORT_MINS);
                    }

                    case KICK_AND_MUTE_LONG -> {
                        sender.sendMessage(Text.literal(
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
                        sender.sendMessage(Text.literal("§c\uD83D\uDD07 §7Still muted — §f" + fmt(until) + " §7left."), false);
                        return false;
                    }

                    case BANNED -> { return false; }
                }

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
