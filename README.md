<div align="center">

# FlareReferrals

**Referral rewards for Minecraft servers: milestones, custom currencies and a full in-game editor.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5%20→%2026.x-ff7a6b)
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
can be blocked, a minimum playtime can be required, and an invite can count only after the new player
played for a while.

## Features

- **Everything editable in-game** (`/referrals settings`) and in the files
- **Rewards**: custom items (drag them in, name / lore / enchants / NBT kept), Vault money, MobCoins, tokens,
  PlayerPoints or any coin plugin through a command, XP, console commands, messages, broadcasts
- **Title, action bar and boss bar** for every reward
- **Milestones**: once or repeating (every X invites), names with gradients, custom icons and player heads
- **Collect menu**: rewards wait in `/referrals collect` with a notification and a preview, or are given right
  away: your choice, for every reward
- **Customizable menus** like DeluxeMenus: drag & drop editor, 1–6 rows, decorations with actions,
  textured heads, admin-only items, own open commands (e.g. `/rewards`), any menu can be turned off
- **Top inviters** menu and chat list
- **Player access**: choose what normal players can open, what they can't use doesn't even show up
- **Offline rewards**: kept and given on the next join
- **Storage**: data.yml (default), SQLite or MySQL / MariaDB, moved with one command (`/referrals migrate`)
- **Network**: lobby, Skyblock, Survival... behind BungeeCord / Velocity share the same invites, every reward
  can be limited to some servers (off by default)
- **Anti-abuse**: same IP, circular invites, minimum playtime, time limit after the first join, invite that
  counts only after X minutes of play
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

Main command `/referrals`. Also `/freferrals`, `/fref`, `/freferral` and `/referral`, useful when another plugin already uses `/referrals`.

| Command | Description | Permission |
|---|---|---|
| `/referrals <player>` | Enter who invited you (both get rewards) | `flarereferrals.use` |
| `/referrals info [player]` | Invites, inviter, clickable milestones | `flarereferrals.info` / `.info.others` |
| `/referrals menu` | Menu with progress and rewards | `flarereferrals.menu` |
| `/referrals rewards` | Inviter reward and welcome reward | `flarereferrals.rewards` |
| `/referrals top` | Top inviters | `flarereferrals.top` |
| `/referrals milestone <n>` | What a milestone gives | `flarereferrals.preview` |
| `/referrals collect` | Rewards waiting to be collected | `flarereferrals.collect` |
| `/referrals claim [n]` | Collect everything waiting (or one milestone), used by the [Collect] buttons | everyone |
| `/referrals settings` | Admin menu | `flarereferrals.admin` |
| `/referrals layout [menu]` | Menu editor (main, collect, rewards, top, preview) | `flarereferrals.admin` |
| `/referrals reload` | Reload all files | `flarereferrals.admin` |
| `/referrals admin set\|add\|remove <player> <n>` | Edit invites (`add` gives milestones) | `flarereferrals.admin` |
| `/referrals admin reset\|resetclaimed <player>` | Reset a player | `flarereferrals.admin` |
| `/referrals migrate <yaml\|sqlite\|mysql> [merge]` | Copy the data to another storage | `flarereferrals.admin` |
| `/referrals version` | Plugin version, server info and update check (also from the console) | `flarereferrals.command.version` |

`flarereferrals.bypass` skips the playtime, time limit and same-IP checks.

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
| `data.yml` | player data (`storage: yaml`) |
| `data/data.db` | player data (`storage: sqlite`) |

Every file starts with a short explanation of its options and of the text format.

## Storage and network

By default everything is saved in `data.yml` and nothing else is needed. To change:

1. set `storage.type` in `config.yml` (and `storage.mysql` for MySQL / MariaDB)
2. run `/referrals migrate <yaml|sqlite|mysql>`: it shows what it will copy, then `... confirm` copies it
   (the old data stays as a backup) and sets `storage.type` for you
3. restart the server

To link several servers (lobby, Skyblock, Survival...) install FlareReferrals on every one of them with the
same MySQL settings, then in each `config.yml`:

```yaml
network:
  enabled: true
  server-name: skyblock   # different on every server
  rewards-here: true      # false on the lobby: it saves invites, rewards are given on the other servers
```

Invites, inviters and the top are the same on every server. In `rewards.yml` every reward can be limited
with `servers: [skyblock]` (default `[all]`). A reward for another server waits there until the player joins it.
If two servers already had their own data, use `/referrals migrate mysql merge` on the second one.

## Building

```
mvn package
```

The jar is created in `target/`. It is compiled against the Spigot API 1.16.5 with Java 8 bytecode,
MiniMessage is shaded and relocated.

## Anonymous statistics

This plugin sends anonymous usage statistics to TMDevelopment every 30 minutes (the first time 3 to 6 minutes
after the server starts): plugin version, Minecraft version, server software, Java version, CPU core count,
online player count, online/offline mode and a random server id generated on your server.
The country of the server is derived from its IP address (only the country code is kept).

FlareReferrals also sends a few anonymous settings of its own:

- `milestone_mode`: rewards collected in the menu or given right away
- `collect_menu`: collect menu setting, On / Off
- `milestones`: number of milestones
- `storage`: yaml / sqlite / mysql
- `network`: single server or network

No player names or UUIDs, no IP addresses (they are never stored), no server names, no chat, world or config data.
Data of a server is deleted 30 days after its last report; only anonymous totals are kept.
We use it to know which versions to support.

You can turn it off for this plugin with `stats: false` in `config.yml`, or for all TMDevelopment plugins in
`plugins/TMDStats/config.yml` (`enabled: false`).

Update check: FlareReferrals reads the latest public release from GitHub (at startup, then at most every
6 hours) to tell admins about new versions. It only reads, nothing is sent. Turn it off with `update-check: false` in `config.yml`.

## Support

Bugs and ideas: open an [issue](../../issues).

## License

[MIT](LICENSE) © TMDevelopment. Bundles [Adventure / MiniMessage](https://github.com/KyoriPowered/adventure) (MIT).
