# FiestaPunish

A server-side Fabric 1.21.1 mod for Fiesta SMP that filters inappropriate language in chat and automatically punishes repeat offenders.

> Built with the assistance of [Claude AI](https://claude.ai) by Anthropic.

---

## Features

- Censors bad words and phrases with `######`
- Detects leetspeak and Unicode character substitutions
- Automatic punishment ladder: warn → mute → kick → ban
- Staff notifications on every punishment
- Fully configurable via in-game commands or JSON files

## Punishment Ladder

| Trigger | Action |
|---|---|
| Bad word/phrase | Message censored, player warned |
| 30 warnings/day | Muted 30 minutes |
| 3 mutes | Kicked + muted 24 hours |
| 5 mutes/month | Permanently banned |

## Commands *(OP level 2)*

```
/fiestapunish words list/add/remove
/fiestapunish phrases list/add/remove
/fiestapunish test <message>
/fiestapunish info/unmute/unban/reset <player>
/fiestapunish set censorchar/log/wholeword <value>
/fiestapunish reload
/fiestapunish status
```

## Config

Files are auto-generated at `config/fiestapunish/` on first launch:
- `banned_words.json` — single words
- `banned_phrases.json` — multi-word patterns
- `settings.json` — behaviour toggles
- `punishments.json` — player records (auto-managed)

## Requirements

- Fabric Loader ≥ 0.16.0 + Fabric API
- Minecraft 1.21.1
- Java 21
- Server-side only — players don't need to install anything
