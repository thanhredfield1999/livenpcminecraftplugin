# LivingNPC

Paper plugin MVP for persistent Citizens farmers. It is intentionally separate from the Mineflayer tester in the repository root.

## Target

- Paper `1.21.11` (verified server build `1.21.11-131`)
- Java `21+`
- Citizens `2.0.42-SNAPSHOT` (verified server build `4173`)

## Behavior

- Citizens owns NPC entity persistence and navigation.
- LivingNPC stores home, plot and plot radius in `plugins/LivingNPC/farmers.yml`.
- A farmer works only during the configured daytime window, in clear weather, and while a player is within the activation range.
- Plot scans are bounded by `max-plot-radius` and rate-limited by `work-scan-interval-ticks`.
- Farmers visibly face crops, hold seeds/tools and swing before changing blocks.
- Before acting, farmers walk to a safe adjacent block, crouch briefly to inspect the crop, then use the visible tool.
- During idle work periods they take uneven breaks, look around, watch a nearby visible player or walk a short route around the plot.
- Nearby hostile mobs interrupt all lower-priority behavior and send the farmer home. Rain and the end of the work schedule do the same.
- Ambient timing and action selection are randomized per resident, preventing synchronized identical movement.
- Mature crops reset to age zero and empty farmland is planted with wheat. Enabled harvest output enters a bounded private NPC inventory; no generated item entities are dropped.

The MVP does not fake sleeping, sitting, eating or social conversations. Those actions require assigned beds, chairs, meeting places and storage so their animations correspond to real locations rather than playing arbitrary poses.

## Build

```powershell
./gradlew.bat clean test build
```

Output: `build/libs/living-npc-0.5.0-SNAPSHOT.jar`.

Important behavior controls are in `plugins/LivingNPC/config.yml`: `activation-range`, `danger-range`, the work window, bounded scan interval, inspection duration, ambient timing/player notice/wander radius and Citizens navigation parameters.

## Setup

1. Install matching Citizens and put the LivingNPC jar in `plugins/`.
2. Restart Paper. Do not use PlugMan or a plugin hot-loader.
3. Stand at the resident's home and run `/livingnpc create <name>`.
4. Stand near the center height of the farm and run `/livingnpc setplot <npc-id> [radius]`.
5. Use `/livingnpc status <npc-id>` to inspect its current phase.

## Villages

LivingNPC supports multiple independent villages in the same world and across different worlds. Each village has its own NPC list, delivery chest, 512-item virtual store, balance, market point and scenic point.

```text
/livingnpc lang tao <id> <tên hiển thị>
/livingnpc tiepnhan <npc-id> <làng-id>
/livingnpc setkho <làng-id>
/livingnpc setdiem <làng-id> cho
/livingnpc setdiem <làng-id> ngamcanh
```

`setkho` uses the chest, trapped chest or barrel the player is looking at within six blocks. Social points use the player's current position. `/livingnpc list` opens the village list first; selecting a village shows only that village's residents and store.

Farmer readiness requires a village, a valid delivery chest, an assigned plot, Master AI, Harvest and Plant. During a shift, a farmer detects the dominant nearby vanilla crop, harvests a mature crop, replants the same crop, walks to the village delivery chest, performs a visible deposit animation and returns to work. The chest is an animation/delivery point; the protected source of truth remains the village's virtual store.

Outside work hours, two safe and idle NPCs from the same village may visit an assigned market or scenic point and use deterministic Vietnamese dialogue based on the point and time of day. Social activity is cancelled during storms or nearby monster danger. Gemini API dialogue remains disabled until gameplay is validated and a non-zero budget is explicitly configured.

## Bounded Zombie Combat

The source includes an admin-controlled combat arena runtime for a two-NPC team. It is fail-closed until an admin creates an arena, sets both cuboid corners and explicitly starts the run.

```text
/livingnpc combat tao <arena-id> <village-id> <archer-id> <swordsman-id>
/livingnpc combat goc1 <arena-id>
/livingnpc combat goc2 <arena-id>
/livingnpc combat rutlui <arena-id>
/livingnpc combat status <arena-id>
/livingnpc combat bat <arena-id>
/livingnpc combat tat <arena-id>
```

- The create command uses the admin's current position as the initial retreat point.
- Combat targets only Zombies inside the configured cuboid; there is no global entity scan.
- The archer deals 3 damage every 30 ticks. The swordsman deals 4 damage every 20 ticks.
- The pair retreats together when either resident reaches 40% health, and they never die from a hit handled by this runtime.
- Retreat has a 20-second timeout so failed Citizens navigation cannot lock either NPC permanently.
- A confirmed NPC kill credits 1.00 to the private village account, with a maximum of 32 kills per run.
- Farmer behavior is suspended while combat owns the pair. Existing hand equipment is restored when the run ends or the plugin shuts down.
- Combat remains manual-only. It does not yet travel between worlds, discover villages, purchase upgrades or persist character biography.

Run `/livingnpc` or `/livingnpc help [1-3]` at any time for an in-game guide. Guide commands are clickable suggestions with hover descriptions. `/livingnpc guide` is an alias for the same pages, and unknown subcommands automatically show page 1.

The GUI is the primary administration workflow. Choosing Create, Home or Plot closes the menu and starts a two-minute position-selection session. Right-click a block with the main hand to save the position; the plugin then reopens the previous GUI and changes the item from `[PENDING]` to `[DONE]`. Use the GUI cancel item or `/livingnpc cancel` to leave placement mode. All original commands remain available for manual administration.

## Safety GUI

Run `/livingnpc list` while standing at spawn to open the resident control panel.

- The first page lists every managed resident with name, title, gender, Citizens ID, plot and safety state.
- Click a resident to inspect and toggle every action independently.
- `Master AI` stops navigation, tools, crop inspection and all behavior immediately.
- `Harvest crops` and `Plant wheat` are **OFF by default**, including when older `farmers.yml` data is migrated. These are the only current actions that change world blocks.
- `Sell inventory` is also **OFF by default**. Enabling harvest does not automatically create money.
- Toggle changes are written to `plugins/LivingNPC/farmers.yml` immediately.
- The create button opens the medieval profile library and spawns the selected supported resident where the admin is standing.

### Multi-role schedule GUI

1. Run `/livingnpc list` and click a resident.
2. Click `Nghề và lịch làm việc`.
3. Click the role whose schedule you want to edit.
4. Left-click a start/end control to move it one hour later; right-click to move it one hour earlier. Hold Shift to change two hours.
5. Click `Dùng lịch mặc định` to remove that role's custom schedule and use `config.yml` again.

The GUI shows normal clock time as `HH:mm`; admins do not need to calculate Minecraft ticks. Every change is saved immediately. If schedules overlap, the resident keeps its current active role until that shift ends. Roles without a completed runtime remain fail-closed even when selected by the scheduler.

## Medieval Profiles And Skins

Edit `plugins/LivingNPC/profiles.yml`, then run `/livingnpc reload`. Each entry supports:

```yaml
profiles:
  alaric_fieldhand:
    name: Alaric
    gender: male
    title: Fieldhand
    roles: [farmer]
    skin: MinecraftUsername
```

Citizens fetches the skin belonging to the configured Minecraft Java username. The plugin does not download, bundle or redistribute skin files. Profile IDs should be unique; one profile can be active on only one managed resident. A profile can declare multiple roles. LivingNPC persists one active role plus separate XP, level and schedule data for every assigned role; only one role scheduler may control an NPC at a time.

Legacy `profession` values are migrated when loaded. Farmer is currently the only complete world-action module. Fisher, cook, crafter, miner, rancher, security and training roles remain fail-closed until their required station, zone and safety modules are configured.

## Private NPC Economy

LivingNPC `0.4.0-SNAPSHOT` has an economy domain fully separate from Essentials and Vault:

- One private balance per Citizens NPC UUID.
- Bounded virtual inventory; no item entities are dropped for generated output.
- Default quota: one normal item per successful harvest, inventory capacity 64 and maximum 32 outputs per shift.
- Rare LiteFarm output is disabled.
- End-of-shift sale requires both `economy.sell-at-shift-end: true` and the NPC's `Sell inventory` GUI toggle.
- Prices come from `plugins/LivingNPC/prices.yml`; unpriced items remain in inventory.
- Balances use integer minor units. Sales use idempotent transaction IDs and a journal in `plugins/LivingNPC/economy.yml`.
- LivingNPC never calls Vault deposit/withdraw and never creates fake player economy accounts.

Default private prices:

```yaml
npc-prices:
  wheat: 2.5
  carrot: 3.0
  potato: 3.0
  beetroot: 2.5
```

The GUI and `/livingnpc status <id>` show private balance, inventory usage and per-shift output.

## Work Targets

Future professions use a common `WorkZone` with `MANUAL_TARGET` or `AUTO_DISCOVER`. AUTO never means global scanning: discovery remains inside the assigned world/radius/vertical range, uses an allowlist and fails closed.

Lumberjack rollout remains manual-first. A complete tree plan must be validated before the first log is broken. Oversized/connected trees, work-zone boundary contact or protected landmark intersection reject the entire candidate, protecting giant decorative trees at spawn.

## Gemini Contract

Gemini is limited to dialogue and whitelist intents: `WORK`, `REST`, `VISIT_MARKET`, `SOCIALIZE`. It cannot choose coordinates, prices, quantities, commands or direct Bukkit actions.

The gateway remains disabled by default and uses deterministic fallback dialogue. It becomes eligible only when all are true:

- `gemini.enabled: true`
- `gemini.monthly-budget-usd` is greater than zero
- the server process has `GEMINI_API_KEY`

Configured caps are 10 requests/minute globally and one request per NPC per five minutes. Never put the API key in YAML, source, logs or the jar. The network SDK implementation is deferred until a restricted key and non-zero hard monthly budget are installed.

Commands require `livingnpc.admin` (operator by default). `/livingnpc remove <npc-id>` permanently removes both the LivingNPC record and Citizens NPC.

## Research references

- Citizens API: https://wiki.citizensnpcs.co/API
- Citizens Javadocs: https://jd.citizensnpcs.co/
- Citizens Maven repository: https://maven.citizensnpcs.co/#/repo/net/citizensnpcs/citizens-main/
- Paper API: https://jd.papermc.io/paper/1.21.11/
