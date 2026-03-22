package com.gotham.fiestapunish;

import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.TimeUnit;

/**
 * All Minecraft API calls use Yarn 1.21.11 names.
 * Confirmed from fabric-api 1.21.11 ChatTest.java:
 *   - Decorator: (sender, message) -> Text   [inferred, no CompletableFuture]
 *   - ALLOW_CHAT_MESSAGE: (message, sender, params) -> boolean
 *   - message.signedContent() -> String
 */
public class ChatEventHandler {

    public static void register() {

        // Rewrite censored words in the message before it broadcasts
        ServerMessageDecoratorEvent.EVENT.register(
            ServerMessageDecoratorEvent.CONTENT_PHASE,
            (sender, message) -> {
                if (sender == null) return message;
                ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(message.getString());
                return result.wasCensored ? Text.literal(result.filtered) : message;
            }
        );

        // Block muted/banned players and record punishments
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
            (message, sender, params) -> {
                if (sender == null) return true;

                String uuid       = sender.getUuidAsString();
                String playerName = sender.getName().getString();

                if (PunishmentManager.isBanned(uuid)) {
                    long until = PunishmentManager.getBannedUntilMs(uuid);
                    sender.sendMessage(Text.literal(until == Long.MAX_VALUE
                        ? "§c✖ §7You are permanently banned from chat."
                        : "§c✖ §7Chat banned for §f" + fmt(until) + "§7."), false);
                    return false;
                }

                if (PunishmentManager.isMuted(uuid)) {
                    long until = PunishmentManager.getMutedUntilMs(uuid);
                    sender.sendMessage(Text.literal("§c\uD83D\uDD07 §7You are muted. Expires in §f" + fmt(until) + "§7."), false);
                    return false;
                }

                ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(message.signedContent());
                if (!result.wasCensored) return true;

                if (FilterConfig.isLogToConsole())
                    FiestaPunishMod.LOGGER.info("[FiestaPunish] {} | [{}] -> [{}]",
                        playerName, message.signedContent(), result.filtered);

                PunishmentManager.Action action = PunishmentManager.recordOffence(uuid, playerName);
                int warns = PunishmentManager.getWarnsToday(uuid);
                int left  = PunishmentManager.WARNS_BEFORE_MUTE - warns;

                switch (action) {
                    case WARN -> sender.sendMessage(Text.literal(
                        "§e⚠ §7Watch your language! §8[Warning §c" + warns + "§8/§c"
                        + PunishmentManager.WARNS_BEFORE_MUTE + "§8 — §e" + left + " left§8]"), false);

                    case MUTE_SHORT -> {
                        sender.sendMessage(Text.literal("§c\uD83D\uDD07 §7Muted for §f"
                            + PunishmentManager.MUTE_SHORT_MINS + " minutes§7."), false);
                        notifyStaff(sender, "§7" + playerName + " muted §c"
                            + PunishmentManager.MUTE_SHORT_MINS + "min");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} muted {}min.", playerName, PunishmentManager.MUTE_SHORT_MINS);
                    }

                    case KICK_AND_MUTE_LONG -> {
                        sender.sendMessage(Text.literal("§4✖ §cMuted 24h and being kicked."), false);
                        sender.getServer().execute(() -> sender.networkHandler.disconnect(
                            Text.literal("§cKicked by FiestaPunish\n§7Muted 3 times. Chat locked 24h.\n§eYou may rejoin.")));
                        notifyStaff(sender, "§7" + playerName + " kicked & muted §c24h");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} kicked + muted 24h.", playerName);
                    }

                    case BAN -> {
                        sender.getServer().execute(() -> sender.networkHandler.disconnect(
                            Text.literal("§c§lYou have been BANNED\n§r§7Reason: Muted 5 times in one month.")));
                        notifyStaff(sender, "§c§l" + playerName + " §r§7BANNED");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} BANNED.", playerName);
                    }

                    case ALREADY_MUTED -> {
                        sender.sendMessage(Text.literal("§c\uD83D\uDD07 §7Still muted — §f"
                            + fmt(PunishmentManager.getMutedUntilMs(uuid)) + " §7left."), false);
                        return false;
                    }

                    case BANNED -> { return false; }
                }

                return true;
            }
        );
    }

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
