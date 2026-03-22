package com.gotham.fiestapunish;

import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.TimeUnit;

/**
 * Mojang mappings — confirmed from fabric-api 1.21.11 source:
 *   Decorator:          (sender, message) -> Component   [inferred types]
 *   ALLOW_CHAT_MESSAGE: (message, sender, params) -> boolean
 *   ServerPlayer:       net.minecraft.server.level.ServerPlayer
 *   Component:          net.minecraft.network.chat.Component
 *   message.signedContent() -> String   [confirmed from ChatTest.java]
 *   player.sendSystemMessage(Component) [confirmed from networking test]
 *   player.getStringUUID()              [confirmed from fabric-api source]
 *   player.connection.disconnect(Component)
 *   server.getPlayerList().getPlayers()
 *   player.hasPermissions(int)
 */
public class ChatEventHandler {

    public static void register() {

        // Rewrite banned content with ##### before broadcast
        ServerMessageDecoratorEvent.EVENT.register(
            ServerMessageDecoratorEvent.CONTENT_PHASE,
            (sender, message) -> {
                if (sender == null) return message;
                ChatFilterEngine.FilterResult r = ChatFilterEngine.filter(message.getString());
                return r.wasCensored ? Component.literal(r.filtered) : message;
            }
        );

        // Block muted/banned players; record offences and apply punishments
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
            (message, sender, params) -> {
                if (sender == null) return true;

                String uuid = sender.getStringUUID();
                String name = sender.getName().getString();

                if (PunishmentManager.isBanned(uuid)) {
                    long until = PunishmentManager.getBannedUntilMs(uuid);
                    sender.sendSystemMessage(Component.literal(until == Long.MAX_VALUE
                        ? "§c✖ §7You are permanently banned from chat."
                        : "§c✖ §7Chat banned for §f" + fmt(until) + "§7."));
                    return false;
                }

                if (PunishmentManager.isMuted(uuid)) {
                    sender.sendSystemMessage(Component.literal(
                        "§c\uD83D\uDD07 §7You are muted. Expires in §f"
                        + fmt(PunishmentManager.getMutedUntilMs(uuid)) + "§7."));
                    return false;
                }

                ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(message.signedContent());
                if (!result.wasCensored) return true;

                if (FilterConfig.isLogToConsole())
                    FiestaPunishMod.LOGGER.info("[FiestaPunish] {} | [{}] -> [{}]",
                        name, message.signedContent(), result.filtered);

                PunishmentManager.Action action = PunishmentManager.recordOffence(uuid, name);
                int warns = PunishmentManager.getWarnsToday(uuid);
                int left  = PunishmentManager.WARNS_BEFORE_MUTE - warns;

                switch (action) {
                    case WARN -> sender.sendSystemMessage(Component.literal(
                        "§e⚠ §7Watch your language! §8[Warning §c" + warns + "§8/§c"
                        + PunishmentManager.WARNS_BEFORE_MUTE + "§8 — §e" + left + " left§8]"));

                    case MUTE_SHORT -> {
                        sender.sendSystemMessage(Component.literal("§c\uD83D\uDD07 §7Muted for §f"
                            + PunishmentManager.MUTE_SHORT_MINS + " minutes§7."));
                        notifyStaff(sender, "§7" + name + " muted §c" + PunishmentManager.MUTE_SHORT_MINS + "min");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} muted {}min.", name, PunishmentManager.MUTE_SHORT_MINS);
                    }

                    case KICK_AND_MUTE_LONG -> {
                        sender.sendSystemMessage(Component.literal("§4✖ §cMuted 24h and being kicked."));
                        sender.getServer().execute(() -> sender.connection.disconnect(
                            Component.literal("§cKicked by FiestaPunish\n§7Muted 3 times. Chat locked 24h.\n§eYou may rejoin.")));
                        notifyStaff(sender, "§7" + name + " kicked & muted §c24h");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} kicked + muted 24h.", name);
                    }

                    case BAN -> {
                        sender.getServer().execute(() -> sender.connection.disconnect(
                            Component.literal("§c§lYou have been BANNED\n§r§7Reason: Muted 5 times in one month.")));
                        notifyStaff(sender, "§c§l" + name + " §r§7BANNED");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} BANNED.", name);
                    }

                    case ALREADY_MUTED -> {
                        sender.sendSystemMessage(Component.literal("§c\uD83D\uDD07 §7Still muted — §f"
                            + fmt(PunishmentManager.getMutedUntilMs(uuid)) + " §7left."));
                        return false;
                    }

                    case BANNED -> { return false; }
                }

                return true;
            }
        );
    }

    private static void notifyStaff(ServerPlayer sender, String msg) {
        if (sender.getServer() == null) return;
        Component t = Component.literal("§8[§6FiestaPunish§8] " + msg);
        sender.getServer().getPlayerList().getPlayers()
            .stream().filter(p -> p.hasPermissions(2))
            .forEach(p -> p.sendSystemMessage(t));
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
