package com.gotham.fiestapunish;

import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.concurrent.TimeUnit;

public class ChatEventHandler {

    public static void register() {

        // Censor bad words in message before broadcast
        ServerMessageDecoratorEvent.EVENT.register(
            ServerMessageDecoratorEvent.CONTENT_PHASE,
            (sender, message) -> {
                if (sender == null) return message;
                ChatFilterEngine.FilterResult r = ChatFilterEngine.filter(message.getString());
                return r.wasCensored ? Text.literal(r.filtered) : message;
            }
        );

        // Block muted/banned; record offences and punish
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
            (message, sender, params) -> {
                if (sender == null) return true;

                String uuid = sender.getUuidAsString();
                String name = sender.getName().getString();

                if (PunishmentManager.isBanned(uuid)) {
                    long until = PunishmentManager.getBannedUntilMs(uuid);
                    sender.sendMessage(Text.literal(until == Long.MAX_VALUE
                        ? "§c✖ §7You are permanently banned from chat."
                        : "§c✖ §7Chat banned for §f" + fmt(until) + "§7."), false);
                    return false;
                }

                if (PunishmentManager.isMuted(uuid)) {
                    sender.sendMessage(Text.literal("§c\uD83D\uDD07 §7You are muted. Expires in §f"
                        + fmt(PunishmentManager.getMutedUntilMs(uuid)) + "§7."), false);
                    return false;
                }

                // In Yarn, SignedMessage is a record — content is in signedBody().content()
                String original = message.signedBody().content();
                ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(original);
                if (!result.wasCensored) return true;

                if (FilterConfig.isLogToConsole())
                    FiestaPunishMod.LOGGER.info("[FiestaPunish] {} | [{}] -> [{}]",
                        name, original, result.filtered);

                PunishmentManager.Action action = PunishmentManager.recordOffence(uuid, name);
                int warns = PunishmentManager.getWarnsToday(uuid);
                int left  = PunishmentManager.WARNS_BEFORE_MUTE - warns;

                switch (action) {
                    case WARN -> sender.sendMessage(Text.literal(
                        "§e⚠ §7Watch your language! §8[Warning §c" + warns + "§8/§c"
                        + PunishmentManager.WARNS_BEFORE_MUTE + "§8 — §e" + left + " left§8]"), false);

                    case MUTE_SHORT -> {
                        sender.sendMessage(Text.literal("§c\uD83D\uDD07 §7Muted for §f"
                            + PunishmentManager.MUTE_SHORT_MINS + " minutes§7."), false);
                        notifyStaff(sender, "§7" + name + " muted §c" + PunishmentManager.MUTE_SHORT_MINS + "min");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} muted {}min.", name, PunishmentManager.MUTE_SHORT_MINS);
                    }

                    case KICK_AND_MUTE_LONG -> {
                        sender.sendMessage(Text.literal("§4✖ §cMuted 24h and being kicked."), false);
                        getServer(sender).execute(() -> sender.networkHandler.disconnect(
                            Text.literal("§cKicked by FiestaPunish\n§7Muted 3 times. Chat locked 24h.\n§eYou may rejoin.")));
                        notifyStaff(sender, "§7" + name + " kicked & muted §c24h");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} kicked + muted 24h.", name);
                    }

                    case BAN -> {
                        getServer(sender).execute(() -> sender.networkHandler.disconnect(
                            Text.literal("§c§lYou have been BANNED\n§r§7Reason: Muted 5 times in one month.")));
                        notifyStaff(sender, "§c§l" + name + " §r§7BANNED");
                        FiestaPunishMod.LOGGER.info("[FiestaPunish] {} BANNED.", name);
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

    // In Yarn, ServerPlayerEntity doesn't expose getServer() directly —
    // access it via the world reference
    private static net.minecraft.server.MinecraftServer getServer(ServerPlayerEntity p) {
        return ((ServerWorld) p.getWorld()).getServer();
    }

    private static void notifyStaff(ServerPlayerEntity sender, String msg) {
        net.minecraft.server.MinecraftServer srv = getServer(sender);
        if (srv == null) return;
        Text t = Text.literal("§8[§6FiestaPunish§8] " + msg);
        srv.getPlayerManager().getPlayerList()
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
