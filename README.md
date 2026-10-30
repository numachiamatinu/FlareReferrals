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
- **Chest builder**: build a menu in a real chest, look at it and type `/referrals layout add <name>`.
  Actions go in the lore (`[close]`, `[open] <menu>`, `[player] <cmd>`, `[left]` / `[right]`, `[permission]`...),
  items keep enchantments, colors and NBT, `[button] collect` places a button of the plugin
- **Top inviters** menu and chat list, with ranks: PlaceholderAPI placeholders are of the player of each line
  (`%luckperms_prefix%`), also for offline players
- **Player access**: choose what normal players can open, what they can't use doesn't even show up
- **Who invited you?** (off by default): a reminder to new players in chat, title, action bar or boss bar, and a
  button for the main menu, until they write `/referrals <name>`
- **Invited players** (off by default): `/referrals invited` lists the players you invited, with the date and
  whether the invite counts already, in a menu with pages or in chat
- **Reward events** (off by default): `/referrals event start 2 48h` doubles the rewards of the invites made in the
  next 48 hours, with boss bar, title and messages, on every server of a network
- **Offline rewards**: kept and given on the next join
- **Storage**: data.yml (default), SQLite or MySQL / MariaDB, moved with one command (`/referrals migrate`)
- **Network**: lobby, Skyblock, Survival... behind BungeeCord / Velocity share the same invites, every reward
  can be limited to some servers (off by default)
- **Anti-abuse**: same IP, circular invites, minimum playtime, time limit after the first join, invite that
  counts only after X minutes of play
- **Hex colors, gradients, rainbow, click & hover** in every text (MiniMessage and `&` codes)
- **PlaceholderAPI** placeholders
- **Safe updates**: new options and texts are added to your files, your edits are never touched
- **Safe files**: a file with a mistake is never overwritten, the console says the file and the line
- **Log**: `logs/referrals.log` with every invite (also the blocked ones and why), reward and admin change
- **Developer API** and events for other plugins

## Compatibility

| | |
|---|---|
| Server | Spigot, Paper, Purpur and other forks |
| Versions | 1.16.5 → 26.x (one jar for all) |
| Java | 8 or newer |
| Optional | PlaceholderAPI, Vault + an economy plugin, LuckPerms (ranks in the top) |

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
| `/referrals invited [chat]` | The players you invited (needs `player-access.invited: true`) | `flarereferrals.invited` |
| `/referrals invited <player>` | The players someone else invited | `flarereferrals.info.others` |
| `/referrals by <player>` | Same as `/referrals <player>`, for inviters called like a command (Top, Menu...) | `flarereferrals.use` |
| `/referrals event` | The reward event running now (needs `events.enabled: true`) | everyone |
| `/referrals event start <x> <time> [reason]` / `stop` | Start or stop a reward event, e.g. `start 2 48h` | `flarereferrals.event` |
| `/referrals claim [n]` | Collect everything waiting (or one milestone), used by the [Collect] buttons | everyone |
| `/referrals settings` | Admin menu | `flarereferrals.admin` |
| `/referrals layout [menu]` | Menu editor (main, collect, rewards, top, preview and your own menus) | `flarereferrals.admin` |
| `/referrals layout add <name>` | New menu from the chest you look at, opened with `/<name>` | `flarereferrals.admin` |
| `/referrals layout import <menu>` | The chest replaces the items of a menu (buttons of the plugin stay) | `flarereferrals.admin` |
| `/referrals layout chest <menu>` | Puts a menu in the empty chest you look at | `flarereferrals.admin` |
| `/referrals layout remove <name>` | Deletes a menu made with `add` (a copy goes in `backups/`) | `flarereferrals.admin` |
| `/referrals layout list` | Every menu | `flarereferrals.admin` |
| `/referrals reload` | Reload all files | `flarereferrals.admin` |
| `/referrals admin set\|add\|remove <player> <n>` | Edit invites (`add` gives milestones) | `flarereferrals.admin` |
| `/referrals admin reset\|resetclaimed <player>` | Reset a player | `flarereferrals.admin` |
| `/referrals migrate <yaml\|sqlite\|mysql> [merge]` | Copy the data to another storage | `flarereferrals.admin` |
| `/referrals about` | Plugin version (green = latest, red = outdated), server, Java, storage, update notifier, plugin hooks, clickable permission list (`about permissions`). Also `version` / `ver`, also from the console | `flarereferrals.command.version` |

`flarereferrals.bypass` skips the playtime, time limit and same-IP checks.

## Placeholders

| Placeholder | Value |
|---|---|
| `%flarereferrals_invites%` | invites of the player |
| `%flarereferrals_invited_count%` | players he invited, also the ones still waiting for their playtime |
| `%flarereferrals_pending_invites%` | invites waiting for `referral.verify-after-minutes` |
| `%flarereferrals_referrer%` | who invited the player |
| `%flarereferrals_referred%` | he entered who invited him (Yes / No) |
| `%flarereferrals_can_enter%` | he can still enter who invited him (Yes / No) |
| `%flarereferrals_position%` | position in the top |
| `%flarereferrals_next_milestone%` | invites of the next milestone (repeating ones too: every 5 with 7 invites = 10) |
| `%flarereferrals_next_remaining%` | invites missing to the next milestone |
| `%flarereferrals_claimed%` | milestones reached |
| `%flarereferrals_claimable%` | milestones waiting to be collected |
| `%flarereferrals_top_<n>_name%` / `_invites` / `_line` | top inviters (`_line` = `placeholders.top-line` in config.yml) |
| `%flarereferrals_event_active%` / `_multiplier` / `_time` / `_reason` | reward event running now |

Yes / No and the text for nobody are in `config.yml > placeholders`.

## Chest builder

Build the menu in a chest (single = 3 rows, double = 6, barrel and shulker box = 3), look at it and type
`/referrals layout add vip`: the menu is saved in `menus/vip.yml` and players open it with `/vip`.
Every item keeps its slot, name, lore, enchantments, colors, model data, heads and NBT.
A head named `{player}` shows the head of the player who opens the menu.

Lore lines that start with `[` are actions or options, they are not shown in the menu:

| Lore line | What it does |
|---|---|
| `[close]` `[back]` `[refresh]` | close the menu, open the previous one, draw it again |
| `[open] <menu>` | open another menu (of the plugin or made with the chest builder) |
| `[player] <cmd>` / `[console] <cmd>` | run a command as the player / the console (`{player}` and PlaceholderAPI work) |
| `[chat] <text>` `[message] <text>` `[broadcast] <text>` `[actionbar] <text>` | chat, messages |
| `[title] title;subtitle;in;stay;out` | title on the screen |
| `[sound] <sound> [volume] [pitch]` | sound (`entity.player.levelup` or `ENTITY_PLAYER_LEVELUP`) |
| `[delay] <ticks>` | waits before the next actions |
| `[server] <name>` | sends the player to another server of the BungeeCord / Velocity network |
| `[left]` `[right]` `[shift-left]` `[shift-right]` in front of an action | only that click: `[right] [open] top` |
| `[permission] <node>` | only players with it see the item |
| `[admin-only]` | only admins see it |
| `[update]` | drawn again every second (live placeholders) |
| `[button] <id>` | a button of the plugin goes here: `head`, `milestones`, `referral-rewards`, `collect`, `top`, `settings`, `close`, `back` |

A lore line that must really start with `[` is written `\[`.
`/referrals layout chest <menu>` puts any menu (also main, top, collect...) in an empty chest, with its actions
in the lore: change it and read it back with `/referrals layout import <menu>`. Before an import or a remove,
the old file is copied to `backups/`.

## Files

| File | Content |
|---|---|
| `config.yml` | settings, player access, "who invited you?" reminder, reward events, placeholders, currencies |
| `rewards.yml` | referral and milestone rewards |
| `lang/en.yml` | chat messages |
| `menus/*.yml` | layout and texts of every menu (`admin.yml` = admin menus), and the menus made with the chest builder |
| `data.yml` | player data (`storage: yaml`) |
| `data/data.db` | player data (`storage: sqlite`) |
| `data/event.yml` | the reward event running now (`/referrals event`) |

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

## Developer API

Add `FlareReferrals` to `softdepend` in your plugin.yml.

```java
int invites = FlareReferralsAPI.getInvites(player.getUniqueId());
UUID inviter = FlareReferralsAPI.getInviter(player.getUniqueId());
List<FlareReferralsAPI.TopEntry> top = FlareReferralsAPI.getTop(10);

@EventHandler
public void onReferral(ReferralEvent e) {          // before the invite is saved, can be cancelled
    if (blocked(e.getPlayer())) e.setCancelled(true);
}

@EventHandler
public void onInvite(ReferralCompleteEvent e) {    // the invite counts
    // e.getInviter(), e.getInviterName(), e.getInvites()
}
```

Package `dev.tmdevelopment.flarereferrals.api`.

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
- `invite_hint`: "who invited you?" reminder On / Off
- `events`: reward events On / Off
- `invited_list`: `/referrals invited` On / Off

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
