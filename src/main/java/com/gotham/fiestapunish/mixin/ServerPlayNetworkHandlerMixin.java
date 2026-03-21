package com.gotham.fiestapunish.mixin;

import com.gotham.fiestapunish.*;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.TimeUnit;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;

    @Inject(
        method = "handleMessage(Lnet/minecraft/network/packet/c2s/play/ChatMessageC2SPacket;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onHandleMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (player == null) return;

        String uuid       = player.getUuidAsString();
        String playerName = player.getName().getString();

        // ── 1. Banned? ───────────────────────────────────────────────────────
        if (PunishmentManager.isBanned(uuid)) {
            ci.cancel();
            long until = PunishmentManager.getBannedUntilMs(uuid);
            if (until == Long.MAX_VALUE)
                player.sendMessage(msg("§c✖ §7You are §cpermanently banned §7from chat on this server."), false);
            else
                player.sendMessage(msg("§c✖ §7You are §cchat-banned §7for another §f" + fmt(until) + "§7."), false);
            return;
        }

        // ── 2. Muted? ────────────────────────────────────────────────────────
        if (PunishmentManager.isMuted(uuid)) {
            ci.cancel();
            long until = PunishmentManager.getMutedUntilMs(uuid);
            player.sendMessage(msg("§c\uD83D\uDD07 §7You are §cmuted§7. Expires in §f" + fmt(until) + "§7."), false);
            return;
        }

        // ── 3. Filter message ────────────────────────────────────────────────
        String original = packet.chatMessage();
        ChatFilterEngine.FilterResult result = ChatFilterEngine.filter(original);

        if (!result.wasCensored) return; // clean — pass through normally

        ci.cancel();

        if (FilterConfig.isLogToConsole()) {
            FiestaPunishMod.LOGGER.info("[FiestaPunish] {} | [{}] -> [{}]",
                    playerName, original, result.filtered);
        }

        // Broadcast filtered version to everyone
        broadcastAs(result.filtered);

        // ── 4. Record & punish ───────────────────────────────────────────────
        PunishmentManager.Action action = PunishmentManager.recordOffence(uuid, playerName);
        int warns = PunishmentManager.getWarnsToday(uuid);
        int left  = PunishmentManager.WARNS_BEFORE_MUTE - warns;

        switch (action) {

            case WARN -> player.sendMessage(msg(
                "§e⚠ §7Please don't use inappropriate language! §8[Warning §c"
                + warns + "§8/§c" + PunishmentManager.WARNS_BEFORE_MUTE
                + "§8 — §e" + left + " left before mute§8]"
            ), false);

            case MUTE_SHORT -> {
                player.sendMessage(msg(
                    "§c\uD83D\uDD07 §7You have been §cmuted for §f"
                    + PunishmentManager.MUTE_SHORT_MINS
                    + " minutes §7for repeated inappropriate language."
                ), false);
                notifyStaff("§7" + playerName + " §7muted §c"
                    + PunishmentManager.MUTE_SHORT_MINS + "min §8(30 warnings used up)");
                FiestaPunishMod.LOGGER.info("[FiestaPunish] {} muted {}min.", playerName, PunishmentManager.MUTE_SHORT_MINS);
            }

            case KICK_AND_MUTE_LONG -> {
                player.sendMessage(msg(
                    "§4✖ §cMuted for §f24 hours §cand kicked — you have been muted §f3 times§c.\n"
                    + "§7Please clean up your language."
                ), false);
                player.getServer().execute(() ->
                    player.networkHandler.disconnect(Text.literal(
                        "§cKicked by FiestaPunish\n"
                        + "§7You were muted 3 times for inappropriate language.\n"
                        + "§eYou may rejoin, but chat is locked for 24 hours."
                    ))
                );
                notifyStaff("§7" + playerName + " §7kicked & muted §c24h §8(3 mutes reached)");
                FiestaPunishMod.LOGGER.info("[FiestaPunish] {} kicked + muted 24h.", playerName);
            }

            case BAN -> {
                player.getServer().execute(() ->
                    player.networkHandler.disconnect(Text.literal(
                        "§c§lYou have been BANNED\n"
                        + "§r§7Reason: Muted 5 times in one month for inappropriate language."
                    ))
                );
                notifyStaff("§c§l" + playerName + " §r§7was §c§lBANNED §r§8(5 monthly mutes)");
                FiestaPunishMod.LOGGER.info("[FiestaPunish] {} BANNED (5 monthly mutes).", playerName);
            }

            case ALREADY_MUTED -> {
                // Fallback — should be caught by pre-check above
                long until = PunishmentManager.getMutedUntilMs(uuid);
                player.sendMessage(msg("§c\uD83D\uDD07 §7Still muted — §f" + fmt(until) + " §7remaining."), false);
            }

            case BANNED -> { /* handled by pre-check */ }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcastAs(String text) {
        if (player == null || player.getServer() == null) return;
        player.getServer().getPlayerManager().broadcast(
            Text.literal("<" + player.getName().getString() + "> " + text), false
        );
    }

    private void notifyStaff(String msg) {
        if (player == null || player.getServer() == null) return;
        Text t = Text.literal("§8[§6FiestaPunish§8] " + msg);
        player.getServer().getPlayerManager().getPlayerList()
            .stream().filter(p -> p.hasPermissionLevel(2))
            .forEach(p -> p.sendMessage(t, false));
    }

    private static Text msg(String s) { return Text.literal(s); }

    static String fmt(long untilMs) {
        if (untilMs == Long.MAX_VALUE) return "permanently";
        long diff = untilMs - System.currentTimeMillis();
        if (diff <= 0) return "0s";
        long h = TimeUnit.MILLISECONDS.toHours(diff);
        long m = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        long s = TimeUnit.MILLISECONDS.toSeconds(diff) % 60;
        if (h > 0)   return h + "h " + m + "m";
        if (m > 0)   return m + "m " + s + "s";
        return s + "s";
    }
}
