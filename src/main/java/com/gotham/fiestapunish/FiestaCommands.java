package com.gotham.fiestapunish;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FiestaCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) ->
            dispatcher.register(
                Commands.literal("fiestapunish")
                    .requires(src -> src.hasPermission(2))

                    .then(Commands.literal("status")
                        .executes(ctx -> {
                            ok(ctx.getSource(),
                                "§6FiestaPunish Status\n" +
                                "§7Words:       §f" + FilterConfig.getWordCount() + "\n" +
                                "§7Phrases:     §f" + FilterConfig.getPhraseCount() + "\n" +
                                "§7Censor char: §f" + FilterConfig.getCensorChar() + "\n" +
                                "§7Console log: §f" + FilterConfig.isLogToConsole() + "\n" +
                                "§7Whole word:  §f" + FilterConfig.isWholeWordOnly());
                            return 1;
                        })
                    )

                    .then(Commands.literal("reload")
                        .executes(ctx -> {
                            FilterConfig.reload();
                            PunishmentManager.load();
                            ok(ctx.getSource(), "Reloaded — §f" + FilterConfig.getWordCount()
                                + " §7words, §f" + FilterConfig.getPhraseCount() + " §7phrases.");
                            return 1;
                        })
                    )

                    .then(Commands.literal("words")
                        .then(Commands.literal("list")
                            .executes(ctx -> {
                                List<String> w = new ArrayList<>(FilterConfig.getBannedWords());
                                Collections.sort(w);
                                ok(ctx.getSource(), "Words (" + w.size() + "):\n§f" + String.join("§7, §f", w));
                                return 1;
                            })
                        )
                        .then(Commands.literal("add")
                            .then(Commands.argument("word", StringArgumentType.word())
                                .executes(ctx -> {
                                    String word = StringArgumentType.getString(ctx, "word");
                                    if (FilterConfig.addWord(word)) ok(ctx.getSource(), "Added: §f'" + word + "'");
                                    else err(ctx.getSource(), "'§f" + word + "§r' already filtered.");
                                    return 1;
                                })
                            )
                        )
                        .then(Commands.literal("remove")
                            .then(Commands.argument("word", StringArgumentType.word())
                                .executes(ctx -> {
                                    String word = StringArgumentType.getString(ctx, "word");
                                    if (FilterConfig.removeWord(word)) ok(ctx.getSource(), "Removed: §f'" + word + "'");
                                    else err(ctx.getSource(), "'§f" + word + "§r' not found.");
                                    return 1;
                                })
                            )
                        )
                    )

                    .then(Commands.literal("phrases")
                        .then(Commands.literal("list")
                            .executes(ctx -> {
                                List<String> p = FilterConfig.getBannedPhrases();
                                StringBuilder sb = new StringBuilder("Phrases (" + p.size() + "):\n");
                                for (int i = 0; i < p.size(); i++)
                                    sb.append("§7").append(i + 1).append(". §f").append(p.get(i)).append("\n");
                                ok(ctx.getSource(), sb.toString().trim());
                                return 1;
                            })
                        )
                        .then(Commands.literal("add")
                            .then(Commands.argument("phrase", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String phrase = StringArgumentType.getString(ctx, "phrase");
                                    if (FilterConfig.addPhrase(phrase)) ok(ctx.getSource(), "Added phrase.");
                                    else err(ctx.getSource(), "Phrase already exists.");
                                    return 1;
                                })
                            )
                        )
                        .then(Commands.literal("remove")
                            .then(Commands.argument("phrase", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String phrase = StringArgumentType.getString(ctx, "phrase");
                                    if (FilterConfig.removePhrase(phrase)) ok(ctx.getSource(), "Removed phrase.");
                                    else err(ctx.getSource(), "Phrase not found.");
                                    return 1;
                                })
                            )
                        )
                    )

                    .then(Commands.literal("test")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String msg = StringArgumentType.getString(ctx, "message");
                                ChatFilterEngine.FilterResult r = ChatFilterEngine.filter(msg);
                                if (r.wasCensored) ok(ctx.getSource(), "§cFiltered: §f" + r.filtered);
                                else ok(ctx.getSource(), "§aClean.");
                                return 1;
                            })
                        )
                    )

                    .then(Commands.literal("set")
                        .then(Commands.literal("censorchar")
                            .then(Commands.argument("char", StringArgumentType.word())
                                .executes(ctx -> {
                                    String c = StringArgumentType.getString(ctx, "char");
                                    FilterConfig.setCensorChar(c);
                                    ok(ctx.getSource(), "Censor char → §f'" + c + "'");
                                    return 1;
                                })
                            )
                        )
                        .then(Commands.literal("log")
                            .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                    FilterConfig.setLogToConsole(v);
                                    ok(ctx.getSource(), "Console log " + (v ? "§aenabled" : "§cdisabled") + "§a.");
                                    return 1;
                                })
                            )
                        )
                        .then(Commands.literal("wholeword")
                            .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                    FilterConfig.setWholeWordOnly(v);
                                    ok(ctx.getSource(), "Whole-word " + (v ? "§aenabled" : "§cdisabled") + "§a.");
                                    return 1;
                                })
                            )
                        )
                    )

                    .then(Commands.literal("info")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                if (t == null) { err(ctx.getSource(), "'§f" + name + "§r' not online."); return 0; }
                                String uuid = t.getStringUUID();
                                PunishmentManager.PlayerRecord rec = PunishmentManager.getRecord(uuid);
                                if (rec == null) { ok(ctx.getSource(), "§f" + name + " §7is clean."); return 1; }
                                rec.resetIfNeeded();
                                ok(ctx.getSource(),
                                    "§6" + name + "\n" +
                                    "§7Warns:  §f" + rec.warnsToday + "§8/§f" + PunishmentManager.WARNS_BEFORE_MUTE + "\n" +
                                    "§7Mutes:  §f" + rec.mutesThisMonth + "§8/§f" + PunishmentManager.MUTES_BEFORE_BAN + " (month)\n" +
                                    "§7Consec: §f" + rec.shortMutesThisRound + "§8/§f" + PunishmentManager.MUTES_BEFORE_KICK_MUTE + "\n" +
                                    "§7Muted:  " + (PunishmentManager.isMuted(uuid) ? "§cYes §8(" + fmtMs(rec.mutedUntil) + ")" : "§aNo") + "\n" +
                                    "§7Banned: " + (PunishmentManager.isBanned(uuid) ? (rec.bannedUntil == Long.MAX_VALUE ? "§cPermanent" : "§cYes") : "§aNo"));
                                return 1;
                            })
                        )
                    )

                    .then(Commands.literal("unmute")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                if (t == null) { err(ctx.getSource(), "'§f" + name + "§r' not online."); return 0; }
                                PunishmentManager.unmute(t.getStringUUID());
                                ok(ctx.getSource(), "§f" + name + " §7unmuted.");
                                t.sendSystemMessage(Component.literal("§a✔ §7Your mute was lifted by staff."));
                                return 1;
                            })
                        )
                    )

                    .then(Commands.literal("unban")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                if (t == null) { err(ctx.getSource(), "'§f" + name + "§r' not online."); return 0; }
                                PunishmentManager.unban(t.getStringUUID());
                                ok(ctx.getSource(), "§f" + name + " §7unbanned from chat.");
                                return 1;
                            })
                        )
                    )

                    .then(Commands.literal("reset")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                if (t == null) { err(ctx.getSource(), "'§f" + name + "§r' not online."); return 0; }
                                PunishmentManager.reset(t.getStringUUID());
                                ok(ctx.getSource(), "§f" + name + " §7record reset.");
                                return 1;
                            })
                        )
                    )
            )
        );
    }

    private static void ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal("§8[§6FiestaPunish§8] §r" + msg), false);
    }

    private static void err(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal("§8[§cFiestaPunish§8] §r" + msg), false);
    }

    private static String fmtMs(long untilMs) {
        if (untilMs == Long.MAX_VALUE) return "§cpermanent";
        long diff = untilMs - System.currentTimeMillis();
        if (diff <= 0) return "§aexpired";
        long h = TimeUnit.MILLISECONDS.toHours(diff);
        long m = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        long s = TimeUnit.MILLISECONDS.toSeconds(diff) % 60;
        return "§f" + (h > 0 ? h + "h " : "") + (m > 0 ? m + "m " : "") + s + "s";
    }
}
