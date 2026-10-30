# Changelog — FlareReferrals (TMDevelopment, formerly FlaresRewards)

Versions go up by one step for every update: 1.0.1, 1.0.2, 1.0.3...

## 1.0.23
### New (all off until you turn them on)
- **Who invited you?** `invite-hint` in config.yml (or `/referrals settings`): new players who didn't write
  `/referrals <name>` yet get a reminder, in chat (clickable), title, action bar, boss bar and with a sound, as you
  choose. First one after `delay-seconds`, again every `repeat-minutes`, at most `max-per-join` times, only for
  players who joined in the last `only-new-days` days (and only on the first join with `only-first-join`). With a
  minimum playtime it starts once they played enough; it stops when they wrote it or when
  `max-days-after-first-join` is over. Admins never get it. Texts in `lang/en.yml > hint`.
  New button for the main menu, "Who invited you?" (`who-invited`, add it in the editor): click and write the name
  in chat. It shows who invited you once done, or how many minutes to play first.
- **Invited players**: `/referrals invited` (menu with pages, `/referrals invited chat` in chat) lists the players
  you invited, the newest first, with the date and whether the invite counts already. Staff: `/referrals invited
  <player>` (`flarereferrals.info.others`). Turn it on with `player-access.invited: true` (or in Player access),
  new menu `menus/invited.yml`, new button `invited-list` for the main menu. Permission `flarereferrals.invited`.
- **Reward events**: `/referrals event start <multiplier> <time> [reason]` (e.g. `2 48h Weekend!`) multiplies the
  rewards of the invites made during the event: currencies (money, tokens, XP levels...) and, if you want, items.
  Choose which rewards (inviter, welcome, milestones). Message, title and sound when it starts, boss bar with the
  time left, action bar and reminders if you want, a message to players who join. A reward keeps its multiplier
  also when it is collected later, and an invite that counts later (playtime) keeps the event of the moment it was
  made. `/referrals event stop`, `/referrals event` for the time left. Survives restarts and runs on every server
  of a network. Needs `events.enabled: true`, permission `flarereferrals.event` (admins have it).
- **New placeholders**: `position`, `invited_count`, `pending_invites`, `referred`, `can_enter`, `top_<n>_line`,
  `event_active`, `event_multiplier`, `event_time`, `event_reason`. Their texts are in `config.yml > placeholders`.
- `/referrals by <player>`: works also for inviters called like a command (Top, Menu, Help...). A player called
  like a command the new player can't use (Admin, Reload...) can now be entered with `/referrals <name>` too.
- `%luckperms_primary_group_name%` works in the top for offline players too, and without PlaceholderAPI
  the LuckPerms prefix, suffix and group are filled by the plugin itself.

### Improved
- Saving from the in-game editors and `/referrals settings` changes only the values that changed in `menus/`,
  `rewards.yml` and `config.yml`: your quotes, comments and layout stay as you wrote them (before, the whole file
  was written again and on 1.16.5 - 1.17 its comments were lost). New options of an update are written with
  their explanation also on 1.16.5 - 1.17.
- New texts of an update are added to `lang/en.yml` and `menus/admin.yml` with their explanation, and the rest of
  the file stays as it is: on 1.16.5 - 1.17 their comments (yours too) were lost at every update.
- Comments you write above the first option of a file (also above the explanation of the plugin) stay.
- Starting an event while another one runs tells the admin it was replaced. The collect menu shows the amounts of a
  reward earned in an event already multiplied.
- data.yml is written in the background and once for all the changes of the same moment: no more lag spikes on
  servers with many players.

### Fixed
- A player could make PlaceholderAPI placeholders show their value by writing them as a name
  (`/referrals %placeholder%`): names are now checked and what players type is never read as placeholders or colors.
- A name that doesn't exist made the server read every saved player from the disk: on big servers writing
  `/referrals <random>` again and again caused lag.
- Repeating milestones ("every 5 invites") were given again for the same number after an admin removed invites.
- `/referrals admin resetclaimed` also deleted rewards still waiting in the collect menu: now only received
  milestones are forgotten, as the help says.
- Network (MySQL): two players on two servers could invite each other at the same moment and both get the rewards.
- `%flarereferrals_next_milestone%` showed the step of a repeating milestone (5) instead of the next number (10).
- Rewards written by hand like `ENDER_PEARL:64` or `DIAMOND_SWORD:3` could end in one slot on some servers: they
  are given in normal stacks.
- `network.sync-seconds` changed with `/referrals reload` is used right away.

## 1.0.22
- **Chest builder**: build a menu in a real chest (single, double, trapped chest, barrel, shulker box), look at it
  and type `/referrals layout add <name>`. The menu is saved in `menus/<name>.yml` and opens with `/<name>`.
  Items keep slot, name, lore, enchantments, colors, model data, heads and NBT; a head named `{player}` shows
  the head of the player who opens it. Lore lines that start with `[` are actions and options:
  `[close]` `[open] <menu>` `[back]` `[refresh]` `[player] <cmd>` `[console] <cmd>` `[chat]` `[message]`
  `[broadcast]` `[actionbar]` `[title]` `[sound]` `[delay]` `[server]`, `[left]` / `[right]` / `[shift-left]` /
  `[shift-right]` in front of an action, `[permission] <node>`, `[admin-only]`, `[update]`, and `[button] <id>`
  to place a button of the plugin (head, milestones, collect, top...). A lore line that must start with `[` is `\[`.
  - `/referrals layout import <menu>`: the chest replaces the items of any menu, the buttons of the plugin stay
    where they are (or move where you put their `[button]`).
  - `/referrals layout chest <menu>`: puts a menu in an empty chest with its actions in the lore, to change it
    by hand and import it again. Importing it back without changes gives the same file.
  - `/referrals layout remove <name>` (asks to confirm) and `/referrals layout list`. Before an import or a remove
    the old file is copied to `backups/`. After an import you get a summary and the menu opens in the editor.
  - The menus made with it are in `/referrals settings > Menu layouts` too (edit, rows, title, on / off, delete).
- The same actions and options work in every menu file: `permission: "node"`, `update: true` and the new actions.
- **`/referrals about`** (also `version` / `ver`, also from the console): plugin version in green when it is the
  latest or in red "OUTDATED! Please update!" with the download link, server version and type, Java, storage,
  network, update notifier on / off, plugin hooks (PlaceholderAPI, Vault, LuckPerms) and a clickable list of every
  permission with what it does (`/referrals about permissions`). The new texts are added to `lang/en.yml` by themselves.
- **Ranks in the top**: PlaceholderAPI placeholders in the top menu (`menus/top.yml`) and in `/referrals top` are now
  of the player of each line, not of the one looking at it. For a rank before the name:
  `"&#ff7a6b#{position} %luckperms_prefix%&f{player}"`. With LuckPerms the rank shows also for offline players.
- Menus in the plugin colors: borders are red instead of gray, the slot markers of the editor orange, and the last
  aqua / pink button names coral. Menu files you never changed get the new colors by themselves.
- Editor: items with enchantments, colors or NBT put in a menu are saved complete (before only material, name,
  lore and glow), items without a name keep their Minecraft name. Moving an "admin only" decoration no longer turns
  it into a normal one, and shift + right click makes only that slot admin only (before the whole border).
  An editor left open while the menu is imported, reset or reloaded doesn't save over it, and it says when it
  could not save.
- The open commands of the menus are no longer taken away and added back at every reload: no more warnings about
  "/<command> is already used", and a deleted menu frees its command at once.
- **A file with a mistake is never overwritten**: a missing quote or a TAB in config.yml, rewards.yml, lang or menu
  files used to be replaced by the default file at the next save, and the changes were lost. Now the console (and
  `/referrals reload`) says the file and the line of the mistake, the file is not touched, and the last working values
  stay until you fix it. With a mistake in config.yml or rewards.yml at startup the plugin waits to be fixed instead
  of starting with default settings (`/referrals` tells you the file and the line).
- **logs/referrals.log**: every invite (also the blocked ones, with the reason: same IP, playtime, circular...),
  reward given, admin command, migration and menu change, with date and server. Off with `logs.referrals: false`.
- Open menus update by themselves when a new invite or a reward to collect arrives (also from another server).
- **Developer API**: `FlareReferralsAPI` (invites, inviter, top) and the events `ReferralEvent` (cancellable, before an
  invite is saved) and `ReferralCompleteEvent` (when an invite counts).
- LuckPerms added to `softdepend`, every permission in plugin.yml has a clear description.

## 1.0.21
- Collect menu: rewards (inviter, welcome, milestones) wait in `/referrals collect` until the player takes them,
  with a clickable notification (chat, action bar, title, sound) and a reminder every few minutes. Preview of
  what each reward gives, "Collect all", rewards that don't fit in the inventory stay in the menu.
  On by default (`rewards.collect-menu`), each reward can be "collect menu" or "right away" (`delivery`).
- Storage: data.yml (default), SQLite or MySQL / MariaDB. `/referrals migrate <yaml|sqlite|mysql>` copies all the data
  (shows a summary first, keeps the old data as a backup, sets storage.type for you).
- Network mode (off by default): lobby, Skyblock, Survival... share invites, inviters and the top through MySQL.
  The inviter is found on every server, playtime is counted on the whole network, messages reach the player on
  the server he is on. Every reward can be limited with `servers` in rewards.yml (also in the reward menu).
  `rewards-here: false` for a lobby that only saves invites. `migrate mysql merge` joins two servers' data.
- Every reward is saved as its own entry: it can't be given twice, not even by two servers at the same time,
  and a reward interrupted by a crash is listed in the console.
- New anti-abuse options (off by default, also in /referrals settings): time limit after the first join to enter
  the inviter, invite that counts only after X minutes of play.
- `stats: false` in config.yml turns off the anonymous statistics of this plugin only.
- `/referrals version` (or `ver`, also from the console): plugin version, Minecraft / server / Java version,
  storage, and whether a new version is out with a download link. Admins get a clickable message when they join.
  It reads the public GitHub releases (betas only for beta users), at most every 6 hours, nothing is sent.
  Turn it off with `update-check: false`. Permission `flarereferrals.command.version` (included in `flarereferrals.admin`).
- Cleaner `/referrals help`: title with the version, short lines, the admin commands listed one by one.
- New alias `/referral`.
- FlareReferrals keeps its own color everywhere: coral (`#FF5F6D` → `#FFC371`), and the yellow highlights in
  messages, help and menus are now coral too (`#FF7A6B`). Texts you never edited are updated by themselves,
  edited ones are not touched.
- Fixed: placeholders asked by scoreboard / TAB plugins from their own threads could fail, `top_0` or
  negative positions gave an error, boss bars of rewards stayed on screen after `/reload`.
- Rewards that are turned off now wait (they are given when turned on again) instead of being skipped, also for
  invites made while they are off.
- Updating keeps the help, info and command texts you edited (only the texts you never changed are updated).
- Fixed: in the admin menus Q, number keys, F, middle and double click also confirmed or toggled things (for
  example deleting a milestone): every menu now reacts only to left / right clicks, with or without shift.
- Fixed: "block same IP" could be skipped by an inviter who joined while the option was off. Only a short hash of
  the IP is kept, as before.
- On servers older than 1.16.5 the plugin turns itself off with a clear message instead of an error.
- Items you put in the reward editor or the menu editor stay in your inventory too.
- rewards.yml keeps plain items short ("DIAMOND:2") and its explanations when it is saved from the editor.
- Deleting a milestone tells you how many players haven't received it yet (they get it back if you make it again).
- Turned-off currencies are no longer shown to players; no reminder right after a "new reward" message;
  player heads don't ask Mojang on offline-mode servers; "1 invite" / "1 day" / "1 minute" instead of "1 invites";
  clearer texts.
- The rewards menu shows "Claimed" only for milestones the player really received; after `admin set` or
  `admin resetclaimed` it says the milestone comes with the next invite. `resetclaimed` is in the help.
- Network: a milestone that can be collected only on another server says where in the rewards menu too; a reward
  nobody can collect (only this server in `servers`, but `rewards-here: false`) is reported in the console.
- New options added to config.yml and to the language / menu files keep their explanations (Minecraft 1.18.1+).
- Milestones converted from 1.0.20 no longer show "Since: 01/01/1970".
- Coming from 1.0.20, the inviter and welcome rewards stay "right away" like before: only milestones wait in the
  collect menu. Every reward can be changed in its menu.
- data.yml is converted to the new format by itself (a copy of the old file goes in backups/). A broken data.yml
  now stops the plugin instead of being replaced by an empty one.

## 1.0.20
- Statistics (TMDStats) are now sent to tmstudio.dev, the new TMDevelopment site. The old address no longer exists.

## 1.0.19
- Anonymous usage statistics with TMDStats (TMDevelopment tracker) instead of bStats: plugin and Minecraft version,
  server software, Java version, CPU cores, player count, online mode, milestone mode and number of milestones.
  No player names, no IPs. Turn them off in plugins/TMDStats/config.yml.
- Checked again on the API of 1.16.5, 1.21.11 and 26.3.

## 1.0.18
- Main command is /referrals again (easier for players). /freferrals, /fref and /freferral still work as aliases.
- Texts written as "/freferrals" by 1.0.17 are changed back to /referrals automatically.

## 1.0.17
- Main command is now /freferrals, also /fref and /freferral (for everyone; /referrals and /ref removed).
- Clickable texts and menu actions with "/referrals" in your files are updated to /freferrals automatically.

## 1.0.16
- Renamed to FlareReferrals. On first start plugins/FlaresRewards is copied to plugins/FlareReferrals
  (config, messages, menus, rewards, player data). Old permissions flaresrewards.* and placeholders
  %flaresrewards_*% keep working next to the new flarereferrals.* / %flarereferrals_*%. Remove the old jar.
- One jar for Spigot, Paper, Purpur and other forks from 1.16.5 to the latest version (26.x), Java 8 or newer.
  Uses only the Spigot API (no Paper-only features), MiniMessage is bundled inside the jar.
  Heads with textures, glow, menu commands and the command list work on every version.
- Default prefix FLARE, help header FLARE REFERRALS (updated only if you didn't change them).
- Fixed: old default texts were not updated automatically in 1.0.15.

## 1.0.15
- Texts rewritten around how referrals really work: the new player writes /referrals <name> with the
  name of the player who really invited him to the server. Fixed wrong lines like "You can't invite yourself"
  (now: "You can't enter your own name. Write the name of the player who invited you."), plus same IP,
  circular, playtime, success, inviter notice, broadcast, help line, settings and referral-rewards menus.
- Same IP message now says which player.
- Existing servers: texts still at the old default are updated automatically, edited texts are kept.

## 1.0.14
- lang/en.yml now has only chat messages. Menu texts moved to the menu files:
  menus/main.yml, rewards.yml, top.yml, preview.yml (section "texts", title = the "title" option),
  admin menus (settings, editors) in the new menus/admin.yml.
- On first start the old "gui" section is removed from lang/en.yml (backup: en.yml.before-menus.bak).
  Edited menu texts must be copied by hand from the backup into the menu files.

## 1.0.13
- Milestones to collect: with milestones.claim-in-menu: true (default, also a switch in /referrals settings)
  a reached milestone is not given automatically anymore. The player gets a clickable [Collect] message,
  the milestone glows in the menu ("Ready to collect!", click = collect), shows ★ in /referrals info
  (click = collect) and /referrals claim collects everything. Reminder on join when something is waiting.
  Inviter and welcome rewards stay automatic.
- New placeholder %flaresrewards_claimable%

## 1.0.12
- Menu size from 1 to 6 rows (9-54 slots), in-game (Menu layouts, right-click = +1 row) or rows: in the file.
  The menu list shows the size in rows and slots. Buttons outside a smaller menu are kept and come back when it grows.

## 1.0.11
- "Nothing here" item (e.g. empty top) is now in the visual center of the list area
- Help and admin explanations: shorter (one line + example), only two colors (yellow command, light gray text)
- Dark gray (&8) replaced with light gray (&7) in all default texts, for readability
- Fixed: "Missing lang" line in the title / action bar / boss bar menu (YAML read the key "off" as false)

## 1.0.10
- One command: /referrals (removed /invite, /invites, /referral). /ref is a short version for admins only:
  normal players don't see it at all
- /referrals info: shorter line above the milestones ("Milestones (click)")

## 1.0.9
- Things that are turned off (menu disabled, or not allowed in player-access / permissions) now look like they
  don't exist: no "not available" message, hidden from tab completion and from /referral help,
  "Unknown command" when typed. open-commands of a hidden menu are removed from the player's command list.
- /referral info: the "Milestones" line only mentions clicking / opens the menu when the player can open it

## 1.0.8
- Every menu can be turned off: enabled: false in menus/<menu>.yml, or the on/off switch under each menu in
  /invite settings > Menu layouts. Off = nobody can open it (commands, open-commands and the buttons to it disappear)
- Short, minimal explanations at the top of every file (config.yml, rewards.yml, lang/en.yml, menus/*.yml):
  colors, hex, gradient, rainbow, click/hover and the options of that file. Existing files get the new
  explanations automatically on start; values are never touched

## 1.0.7
- admin-only for every item of the customizable menus (buttons and decorations): admins see it, normal players
  get the menu without it. In the editor: shift + right-click an item to switch it (red "[Admin only]" tag);
  in the files: admin-only: true. The admin settings button and the Edit button are admin-only by default.
- Currencies: on start the console says once which currencies can't work and why (e.g. "/mobcoins does not exist",
  "no economy plugin connected to Vault"); their rewards are skipped silently instead of spamming errors.
  The currency menu shows them in red with the reason. Example currencies (mobcoins, tokens, coins) are off on
  new installs.
- Player access: choose what normal players (without admin) can open — info, main menu, referral rewards,
  top, reward preview. config.yml > player-access or /invite settings > Player access (off = admins only).
  Per-group control with permissions flaresrewards.info / .menu / .rewards / .top / .preview
- Buttons and click hints for things a player can't open are hidden (menus, /referral info, tab completion)
- open-commands of the menus respect the same access rules

## 1.0.6
- /invite admin: writing a command incomplete or wrong now explains it instead of showing the help page:
  /invite admin -> clickable list of all actions with what they do;
  /invite admin set <player> without amount -> explanation + example of "set" (same for add, remove, reset, resetclaimed);
  unknown action, missing player, missing / wrong amount -> clear message
- Confirmation shows before → after (e.g. Steve: 2 → 5 invites)
- /referral info: the whole milestone line is clickable (works even if the lang line is edited); the
  "Milestones" header opens the menu with all milestones
- Milestone status: ✔ only when the invites are really reached, ✖ otherwise (before, a milestone received
  once stayed ✔ even after invites were removed). Same in the menu.
- Tab completion suggests amounts; /referral milestone <not a number> explains the command

## 1.0.5
- open-commands in every menu file: e.g. `open-commands: [rewards]` in menus/main.yml -> /rewards opens it
  (registered live, also after /referral reload; optional open-permission)
- Every item of every customizable menu now has material / name / lore / glow written in its file, ready to edit.
  Existing files are completed automatically on start.
- Items put in the menu with the in-game editor are saved in a readable form (material, name, lore, glow,
  amount, custom-model-data, actions: []) instead of raw item data; heads keep their texture (texture:...)
- Editor: list areas (milestones, top players, reward items) show numbered slot markers, so they can be
  moved or removed for real (before, the milestone items came back because they are generated automatically)
- Top entries name / lore can be changed in menus/top.yml

## 1.0.4
- Fixed: plugin did not start on 1.0.3 ("duplicate element: rewards" in the command list)
- Every class is now checked at build time so this kind of startup error can't slip through again

## 1.0.3
- Customizable menus (like DeluxeMenus): main menu, referral rewards, top and reward preview have a layout
  file in menus/ (title, rows, slot of every button, material/name/lore overrides, decorations with click actions)
- In-game layout editor: /invite settings > Menu layouts (or /referral layout <menu>). The menu opens in edit
  mode: drag buttons, put glass or custom heads from your inventory, remove items, shift + right-click an
  empty slot to add a button or glass; closing saves. Change rows / title, restore default
- Heads everywhere: head:{player}, head:<name>, texture:<minecraft-heads value or id> in menu files;
  "Add a head" button in the reward Items menu; milestone icon from a head (right-click the icon button)
- Title, subtitle, action bar and boss bar for every reward (inviter, welcome, milestones), shown to the
  player who receives it. Editable in the new "Title, action bar & boss bar" menu (with "Test on me")
  and in rewards.yml > display
- Menu texts refreshed again (old lang file backed up as en.yml.v2.bak)

## 1.0.2
- New "Referral rewards" menu (inviter reward + welcome reward), separate from milestones.
  Admins: left-click edit, right-click on/off. Players can open it too (/referral rewards) to see the rewards.
- Fixed the strange square characters when viewing lines in menus (lines are now shown exactly as written)
- Placeholders now use {player} {invites} {prefix}... (old %player% still works); no more clash with PlaceholderAPI
- Lines menu (commands / messages / broadcast) redesigned: item name = live preview,
  left-click edit, right-click remove, shift-click move up
- Chat prompts: clickable [Click to edit the current text] fills the chat bar with the old value, clickable [Cancel]
- Reward editor shows a preview of items (custom names), money, commands and messages directly on the buttons
- New milestone suggests the next free amount (3, 5, 10, 15...)
- Menu texts update automatically on new versions (old lang file backed up as en.yml.v1.bak)

## 1.0.1
- Admin menu `/invite settings` with toggles, reward editors and milestone list
- Milestones every 3 / 5 / 10... invites (once or repeating) with items, money, custom currencies, commands, messages, broadcast
- Custom items can be dragged into the Items menu (name, lore, enchants, NBT kept)
- Currencies: Vault, command based (MobCoins, Tokens, private coins...), XP
- Every message, the /referral info panel and all menu texts editable in lang/en.yml
- Clickable milestones in /referral info, player menu, top menu, PlaceholderAPI placeholders
- Ready-to-use single jar: creates all its files on first start
- Auto-install: downloads and starts PlaceholderAPI and Vault by itself if missing (no restart)
- Auto-update of config.yml and lang/en.yml: new options/messages are added, edited ones are kept
