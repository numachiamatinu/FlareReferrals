<div align="center">

# FlareReferrals

**Referral rewards for Minecraft servers: milestones, custom currencies and a full in-game editor.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5%20→%2026.x-ff7a5c)
![Platforms](https://img.shields.io/badge/Spigot%20|%20Paper%20|%20Purpur-supported-ffc371)
![Java](https://img.shields.io/badge/Java-8%2B-orange)
![License](https://img.shields.io/badge/license-MIT-green)

</div>

---

## How it works

A new player joins your server because a friend invited him. He types:

```
/referrals <friend>
```

- the **inviter** gets a reward and +1 invite
- the **new player** gets a welcome reward
- at **3 / 5 / 10…** invites the inviter unlocks **milestones** and collects them with a click in the menu

Every player can enter an inviter only once. Referrals from the same IP and "you invite me, I invite you"
can be blocked, and a minimum playtime can be required.

## Features

- **Everything editable in-game** (`/referrals settings`) and in the files
- **Rewards**: custom items (drag them in, name / lore / enchants / NBT kept), Vault money, MobCoins, tokens,
  PlayerPoints or any coin plugin through a command, XP, console commands, messages, broadcasts
- **Title, action bar and boss bar** for every reward
- **Milestones**: once or repeating (every X invites), names with gradients, custom icons and player heads
- **Collect in the menu**: reached milestones glow and are collected with a click (or given automatically)
- **Customizable menus** like DeluxeMenus: drag & drop editor, 1–6 rows, decorations with actions,
  textured heads, admin-only items, own open commands (e.g. `/rewards`), any menu can be turned off
- **Top inviters** menu and chat list
- **Player access**: choose what normal players can open, what they can't use doesn't even show up
- **Offline rewards**: kept and given on the next join
- **Hex colors, gradients, rainbow, click & hover** in every text (MiniMessage and `&` codes)
- **PlaceholderAPI** placeholders
- **Safe updates**: new options and texts are added to your files, your edits are never touched

## Compatibility

| | |
|---|---|
| Server | Spigot, Paper, Purpur and other forks |
| Versions | 1.16.5 → 26.x (one jar for all) |
| Java | 8 or newer |
| Optional | PlaceholderAPI, Vault + an economy plugin |

## Installation

1. Put `FlareReferrals-<version>.jar` in `plugins/`
2. Start the server, all files are created automatically
3. Type `/referrals settings` in-game and set your rewards

If PlaceholderAPI or Vault are missing, they are downloaded from their official pages (Hangar, Modrinth,
GitHub) and started without a restart. Turn it off with `config.yml > auto-install > enabled: false`.

## Commands

Main command `/referrals`. Also `/freferrals`, `/fref` and `/freferral`, useful when another plugin already uses `/referrals`.

| Command | Description | Permission |
|---|---|---|
| `/referrals <player>` | Enter who invited you (both get rewards) | `flarereferrals.use` |
| `/referrals info [player]` | Invites, inviter, clickable milestones | `flarereferrals.info` / `.info.others` |
| `/referrals menu` | Menu with progress and rewards | `flarereferrals.menu` |
| `/referrals rewards` | Inviter reward and welcome reward | `flarereferrals.rewards` |
| `/referrals top` | Top inviters | `flarereferrals.top` |
| `/referrals milestone <n>` | What a milestone gives | `flarereferrals.preview` |
| `/referrals claim [n]` | Collect reached milestones | `flarereferrals.use` |
| `/referrals settings` | Admin menu | `flarereferrals.admin` |
| `/referrals layout [menu]` | Menu editor (main, rewards, top, preview) | `flarereferrals.admin` |
| `/referrals reload` | Reload all files | `flarereferrals.admin` |
| `/referrals admin set\|add\|remove <player> <n>` | Edit invites (`add` gives milestones) | `flarereferrals.admin` |
| `/referrals admin reset\|resetclaimed <player>` | Reset a player | `flarereferrals.admin` |

`flarereferrals.bypass` skips the playtime and same-IP checks.

## Placeholders

| Placeholder | Value |
|---|---|
| `%flarereferrals_invites%` | invites of the player |
| `%flarereferrals_referrer%` | who invited the player |
| `%flarereferrals_next_milestone%` | next milestone |
| `%flarereferrals_next_remaining%` | invites missing to the next milestone |
| `%flarereferrals_claimed%` | milestones reached |
| `%flarereferrals_claimable%` | milestones waiting to be collected |
| `%flarereferrals_top_<n>_name%` / `%flarereferrals_top_<n>_invites%` | top inviters |

## Files

| File | Content |
|---|---|
| `config.yml` | settings, player access, currencies |
| `rewards.yml` | referral and milestone rewards |
| `lang/en.yml` | chat messages |
| `menus/*.yml` | layout and texts of every menu (`admin.yml` = admin menus) |
| `data.yml` | player data |

Every file starts with a short explanation of its options and of the text format.

## Building

```
mvn package
```

The jar is created in `target/`. It is compiled against the Spigot API 1.16.5 with Java 8 bytecode,
MiniMessage is shaded and relocated.

## Statistics

FlareReferrals sends anonymous usage statistics with **TMDStats**, the TMDevelopment usage tracker.
Every 30 minutes (the first time 3 to 6 minutes after the server starts) it sends only:

- plugin id and version
- Minecraft version and server software (Paper, Spigot, Purpur...)
- Java version and number of CPU cores
- number of online players (only the number) and online / offline mode
- a random server id created on your server, not linked to anything
- milestone mode (collect in menu / automatic) and number of milestones

No player names, no IP addresses, no chat or world data.
Turn it off for all TMDevelopment plugins with `enabled: false` in `plugins/TMDStats/config.yml`, then restart the server.

## Support

Bugs and ideas: open an [issue](../../issues).

## License

[MIT](LICENSE) © TMDevelopment. Bundles [Adventure / MiniMessage](https://github.com/KyoriPowered/adventure) (MIT).
