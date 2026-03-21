package com.gotham.fiestapunish;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FiestaCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) ->
            dispatcher.register(
                CommandManager.literal("fiestapunish")
                    .requires(src -> src.hasPermissionLevel(2))

                    // /fiestapunish status
                    .then(CommandManager.literal("status")
                        .executes(ctx -> {
                            ok(ctx.getSource(),
                                "§6FiestaPunish §7Status\n" +
                                "§7Words:       §f" + FilterConfig.getWordCount() + "\n" +
                                "§7Phrases:     §f" + FilterConfig.getPhraseCount() + "\n" +
                                "§7Censor char: §f" + FilterConfig.getCensorChar() + "\n" +
                                "§7Console log: §f" + FilterConfig.isLogToConsole() + "\n" +
                                "§7Whole word:  §f" + FilterConfig.isWholeWordOnly()
                            );
                            return 1;
                        })
                    )

                    // /fiestapunish reload
                    .then(CommandManager.literal("reload")
                        .executes(ctx -> {
                            FilterConfig.reload();
                            PunishmentManager.load();
                            ok(ctx.getSource(), "Reloaded — §f" + FilterConfig.getWordCount()
                                + " §7words, §f" + FilterConfig.getPhraseCount() + " §7phrases.");
                            return 1;
                        })
                    )

                    // ── Word management ──────────────────────────────────────

                    .then(CommandManager.literal("words")
                        .then(CommandManager.literal("list")
                            .executes(ctx -> {
                                List<String> words = new ArrayList<>(FilterConfig.getBannedWords());
                                Collections.sort(words);
                                ok(ctx.getSource(), "Words (§f" + words.size() + "§a):\n§f"
                                    + String.join("§7, §f", words));
                                return 1;
                            })
                        )
                        .then(CommandManager.literal("add")
                            .then(CommandManager.argument("word", StringArgumentType.word())
                                .executes(ctx -> {
                                    String w = StringArgumentType.getString(ctx, "word");
                                    if (FilterConfig.addWord(w)) ok(ctx.getSource(), "Added word: §f'" + w + "'");
                                    else err(ctx.getSource(), "§f'" + w + "' §7is already filtered.");
                                    return 1;
                                })
                            )
                        )
                        .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("word", StringArgumentType.word())
                                .executes(ctx -> {
                                    String w = StringArgumentType.getString(ctx, "word");
                                    if (FilterConfig.removeWord(w)) ok(ctx.getSource(), "Removed word: §f'" + w + "'");
                                    else err(ctx.getSource(), "§f'" + w + "' §7was not found.");
                                    return 1;
                                })
                            )
                        )
                    )

                    // ── Phrase management ────────────────────────────────────

                    .then(CommandManager.literal("phrases")
                        .then(CommandManager.literal("list")
                            .executes(ctx -> {
                                List<String> phrases = FilterConfig.getBannedPhrases();
                                StringBuilder sb = new StringBuilder("Phrases (§f" + phrases.size() + "§a):\n");
                                for (int i = 0; i < phrases.size(); i++)
                                    sb.append("§7").append(i + 1).append(". §f").append(phrases.get(i)).append("\n");
                                ok(ctx.getSource(), sb.toString().trim());
                                return 1;
                            })
                        )
                        .then(CommandManager.literal("add")
                            .then(CommandManager.argument("phrase", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String p = StringArgumentType.getString(ctx, "phrase");
                                    if (FilterConfig.addPhrase(p)) ok(ctx.getSource(), "Added phrase: §f\"" + p + "\"");
                                    else err(ctx.getSource(), "Phrase already exists.");
                                    return 1;
                                })
                            )
                        )
                        .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("phrase", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String p = StringArgumentType.getString(ctx, "phrase");
                                    if (FilterConfig.removePhrase(p)) ok(ctx.getSource(), "Removed phrase: §f\"" + p + "\"");
                                    else err(ctx.getSource(), "Phrase not found.");
                                    return 1;
                                })
                            )
                        )
                    )

                    // ── Test ─────────────────────────────────────────────────

                    .then(CommandManager.literal("test")
                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String msg = StringArgumentType.getString(ctx, "message");
                                ChatFilterEngine.FilterResult r = ChatFilterEngine.filter(msg);
                                if (r.wasCensored) ok(ctx.getSource(), "§cFiltered: §f" + r.filtered);
                                else               ok(ctx.getSource(), "§aClean — no matches found.");
                                return 1;
                            })
                        )
                    )

                    // ── Settings ──────────────────────────────────────────────

                    .then(CommandManager.literal("set")
                        .then(CommandManager.literal("censorchar")
                            .then(CommandManager.argument("char", StringArgumentType.word())
                                .executes(ctx -> {
                                    String c = StringArgumentType.getString(ctx, "char");
                                    FilterConfig.setCensorChar(c);
                                    ok(ctx.getSource(), "Censor character → §f'" + c + "'");
                                    return 1;
                                })
                            )
                        )
                        .then(CommandManager.literal("log")
                            .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                    FilterConfig.setLogToConsole(v);
                                    ok(ctx.getSource(), "Console logging " + (v ? "§aenabled" : "§cdisabled") + "§a.");
                                    return 1;
                                })
                            )
                        )
                        .then(CommandManager.literal("wholeword")
                            .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                    FilterConfig.setWholeWordOnly(v);
                                    ok(ctx.getSource(), "Whole-word matching " + (v ? "§aenabled" : "§cdisabled") + "§a.");
                                    return 1;
                                })
                            )
                        )
                    )

                    // ── Player management ─────────────────────────────────────

                    .then(CommandManager.literal("info")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
                                if (target == null) { err(ctx.getSource(), "Player §f'" + name + "' §7not found or offline."); return 0; }
                                String uuid = target.getUuidAsString();
                                PunishmentManager.PlayerRecord rec = PunishmentManager.getRecord(uuid);
                                if (rec == null) { ok(ctx.getSource(), "§f" + name + " §7has a clean record."); return 1; }
                                rec.resetIfNeeded();
                                String muteStr = (rec.mutedUntil != null && System.currentTimeMillis() < rec.mutedUntil)
                                    ? "§cYes §8(§f" + fmtMs(rec.mutedUntil) + "§8 left)"  : "§aNo";
                                String banStr = (rec.bannedUntil != null && System.currentTimeMillis() < rec.bannedUntil)
                                    ? (rec.bannedUntil == Long.MAX_VALUE ? "§cPermanent" : "§cYes §8(§f" + fmtMs(rec.bannedUntil) + "§8 left)") : "§aNo";
                                ok(ctx.getSource(),
                                    "§6Record for §f" + name + "\n" +
                                    "§7Warns today:       §f" + rec.warnsToday + " §8/ " + PunishmentManager.WARNS_BEFORE_MUTE + "\n" +
                                    "§7Mutes this month:  §f" + rec.mutesThisMonth + " §8/ " + PunishmentManager.MUTES_BEFORE_BAN + "\n" +
                                    "§7Consec. mutes:     §f" + rec.shortMutesThisRound + " §8/ " + PunishmentManager.MUTES_BEFORE_KICK_MUTE + "\n" +
                                    "§7Muted:             " + muteStr + "\n" +
                                    "§7Banned:            " + banStr
                                );
                                return 1;
                            })
                        )
                    )

                    .then(CommandManager.literal("unmute")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
                                if (target == null) { err(ctx.getSource(), "Player §f'" + name + "' §7not found or offline."); return 0; }
                                PunishmentManager.unmute(target.getUuidAsString());
                                ok(ctx.getSource(), "§f" + name + " §7has been unmuted.");
                                target.sendMessage(Text.literal("§a✔ §7Your mute has been lifted by a staff member."), false);
                                return 1;
                            })
                        )
                    )

                    .then(CommandManager.literal("unban")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
                                if (target == null) { err(ctx.getSource(), "Player §f'" + name + "' §7not found or offline. Use UUID if needed."); return 0; }
                                PunishmentManager.unban(target.getUuidAsString());
                                ok(ctx.getSource(), "§f" + name + " §7has been unbanned from chat.");
                                return 1;
                            })
                        )
                    )

                    .then(CommandManager.literal("reset")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
                                if (target == null) { err(ctx.getSource(), "Player §f'" + name + "' §7not found or offline."); return 0; }
                                PunishmentManager.reset(target.getUuidAsString());
                                ok(ctx.getSource(), "§f" + name + " §7's entire punishment record has been reset.");
                                return 1;
                            })
                        )
                    )
            )
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void ok(ServerCommandSource src, String msg) {
        src.sendFeedback(() -> Text.literal("§8[§6FiestaPunish§8] §r" + msg), false);
    }

    private static void err(ServerCommandSource src, String msg) {
        src.sendFeedback(() -> Text.literal("§8[§cFiestaPunish§8] §r" + msg), false);
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
