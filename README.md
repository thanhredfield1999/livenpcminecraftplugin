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
- Farmers take one configurable lunch break around the middle of their assigned shift, return near home without ending the shift, then naturally walk back to the plot and resume work.
- Nearby hostile mobs interrupt all lower-priority behavior and send the farmer home. Rain and the end of the work schedule do the same.
- Ambient timing and action selection are randomized per resident, preventing synchronized identical movement.
- Mature wheat, carrot, potato and beetroot crops reset atomically to age zero. Empty farmland uses the dominant crop inside the assigned plot. Output enters the village virtual store; no generated item entities are dropped.

LivingNPC uses assigned beds for sleep and village seats for rest and lunch. A configured Stair is a rest seat; a solid block directly in front of the Stair classifies it as a dining seat. Citizens owns the sitting helper entity through `SitTrait`, while LivingNPC reserves the seat, locks the NPC to its Stair direction and releases it before the NPC resumes work.

## Build

```powershell
./gradlew.bat clean test build
```

Output: `build/libs/living-npc-0.5.0-SNAPSHOT.jar`.

Important behavior controls are in `plugins/LivingNPC/config.yml`: `activation-range`, `danger-range`, the work window, bounded scan interval, inspection duration, ambient timing/player notice/wander radius and Citizens navigation parameters.

Farmer daily-plan controls are under `farmer.daily-plan`. Lunch is enabled by default and lasts `1000` Minecraft ticks (one in-game hour). The break is centered inside each farmer's configured schedule, including custom schedules that cross midnight. Lunch does not trigger end-of-shift sales or reset production quota.

## Quick Setup

After installing the jar and restarting Paper, use only `/lnpc` for normal setup:

1. Open a village and click `Kho làng`, then right-click its chest or barrel.
2. Open a worker and click `Khu ruộng`, then right-click the plot center.
3. Choose the NPC job, then click `NPC hoạt động: BẬT`.

While the plugin is waiting for a right-clicked block, type `/lnpc cancel` in chat to cancel the placement.

Each NPC has a job menu. `Người dân` provides normal village wandering and ambient behavior without production. `Nông dân` uses the configured plot and storage. `Ngư dân`, `Chăn nuôi`, `Đầu bếp`, `Thợ chế tạo`, `Thợ mỏ` and `Bảo vệ` have dedicated bounded runtimes and require their village work zones. `NPC hoạt động` is the shared on/off switch for the selected job.

Village infrastructure is configured from `Khu nghề & khách vãng lai`:

- Woodworking requires a stonecutter and crafting table.
- Cooking requires a furnace and crafting table.
- Crafting requires a crafting table, smithing table and any usable anvil variant.
- Stations must be inside the bounded validation area around the selected center before the zone can be saved.
- Ranching requires a hay bale plus any fence or fence gate. Ranchers consume village virtual-store food to put two ready adults into vanilla love mode: wheat for cows/sheep, wheat seeds for chickens and carrots for pigs.
- Ranch zones are shared village infrastructure rather than per-NPC assignments. Cow, sheep, chicken, pig and rabbit breeding is supported; rabbits use carrots. One rancher owns the zone operation at a time, and overlapping ranch zones from another village are rejected so two NPCs cannot select the same herd concurrently.
- Each village has a configurable per-species animal limit, default 8. Above the limit, a rancher handles at most 2 surplus adults per cycle while preserving at least two adult breeders. Actual mob death drops are captured into village virtual storage without duplicating ground drops.
- An idle rancher patrols safe reachable points inside the bounded ranch instead of standing at the zone center. Animals observed inside the ranch are remembered for the current server session; if one escapes within the bounded recovery radius, the rancher can visibly lead that known herd member back without teleporting or claiming unrelated wild animals. Animals already leashed by a player are never taken.
- Citizens `DoorExaminer` owns wooden-door and fence-gate traversal. It treats gates as pathable, opens them when the NPC reaches them and closes them after passage; LivingNPC does not keep a second timer that leaves livestock gates open.
- `Ghế nghỉ & bàn ăn` stores shared village seats. Click `Thêm ghế`, then right-click a Stair. A Stair without a solid block in front becomes a rest seat; one facing a solid table block becomes a dining seat. The Stair direction fixes the NPC's seated yaw.
- Villages can have unlimited delivery chests/barrels. Workers sort valid points by distance, require safe standing space and a real Citizens path, skip blocked/high/unloaded points, and try the next location when navigation fails. Stuck teleport is disabled.

`Khách vãng lai` is a temporary, non-selectable NPC role. The admin must set both `Cổng khách` and `Điểm chợ`. Guests spawn exactly at that gate, walk to the market, buy bounded quantities of priced virtual-store goods using their own generated wallet, credit the village balance, walk back to the same gate and are destroyed only after arriving. Guests are not persisted across restarts.

If no village exists yet, create it once with `/lnpc lang tao <id> <tên>`. All older commands remain available for advanced administration but are not part of the normal workflow.

## Villages

LivingNPC supports multiple independent villages in the same world and across different worlds. Each village has its own NPC list, delivery chest, virtual store, balance, market point and scenic point. Storage is temporarily unlimited by default through `economy.unlimited-storage: true`; per-shift production quotas still prevent runaway production. Set it to `false` later to restore `economy.inventory-capacity`.

`/lnpc` opens the village list. Selecting a village shows only that village's workers and virtual store. Storage, worker creation, home, plot, radius, work toggle and optional behavior settings are managed in the GUI.

Farmer readiness requires a village, a valid delivery chest, an assigned plot, Master AI, Harvest and Plant. During a shift, a farmer detects the dominant nearby vanilla crop, harvests a mature crop, replants the same crop, walks to the village delivery chest, performs a visible deposit animation and returns to work. The chest is an animation/delivery point; the protected source of truth remains the village's virtual store.

Mature wheat uses one harvested seed for immediate replanting. Surplus seeds are stored as `wheat_seeds` in the village virtual store, remain unsold without a configured price, and are reserved for a future rancher/chicken-care runtime.

Outside work hours, two safe and idle NPCs from the same village may visit an assigned market or scenic point and use deterministic Vietnamese dialogue based on the point and time of day. Social activity is cancelled during storms or nearby monster danger. Gemini API dialogue remains disabled until gameplay is validated and a non-zero budget is explicitly configured.

## Deferred Combat Source

Experimental combat source exists but is deferred. It is not shown in normal help or tab completion and should not be enabled or deployed as part of the farmer workflow.

Run `/lnpc` to open the GUI. `/lnpc help` shows the same three-step setup in chat.

The GUI is the primary administration workflow. Choosing Create, Home, Plot or Storage closes the menu and starts a two-minute position-selection session. Right-click a block with the main hand to save the position; the plugin then reopens the previous GUI. Use the GUI cancel item to leave placement mode. Older commands remain available only for advanced administration.

## Safety GUI

Run `/lnpc` to open the resident control panel.

- The first page lists every managed resident with name, title, gender, Citizens ID, plot and safety state.
- Click a resident to inspect and toggle every action independently.
- `Làm nông` controls Master AI, harvest and planting together, so they cannot be left half-configured through the normal GUI.
- `Làm nông` is **OFF by default** and can only be enabled after village, storage and plot are valid.
- `Sell inventory` is also **OFF by default**. Enabling harvest does not automatically create money.
- `Character profile` is optional and **OFF by default**. A resident can remain a plain worker without biography, relationships or special dialogue.
- Toggle changes are written to `plugins/LivingNPC/farmers.yml` immediately.
- The create button opens the medieval profile library and spawns the selected supported resident where the admin is standing.

### Multi-role schedule GUI

1. Run `/lnpc` and click a resident.
2. Click `Nghề và lịch làm việc`.
3. Click the role whose schedule you want to edit.
4. Left-click a start/end control to move it one hour later; right-click to move it one hour earlier. Hold Shift to change two hours.
5. Click `Dùng lịch mặc định` to remove that role's custom schedule and use `config.yml` again.

The GUI shows normal clock time as `HH:mm`; admins do not need to calculate Minecraft ticks. Every change is saved immediately. If schedules overlap, the resident keeps its current active role until that shift ends. Roles without a completed runtime remain fail-closed even when selected by the scheduler.

## Medieval Profiles And Skins

Edit `plugins/LivingNPC/profiles.yml`, then run `/lnpc reload`. Each entry supports:

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

Legacy `profession` values are migrated when loaded. Farmer, fisher, cook, crafter, miner, rancher and security roles run only after their required station, zone and safety checks pass. Training roles remain fail-closed.

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

The GUI shows village balance, inventory usage and per-shift output. `/lnpc status <id>` is available for detailed diagnostics.

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

Commands require `livingnpc.admin` (operator by default). Permanent NPC removal is available from the GUI confirmation screen.

## Research references

- Citizens API: https://wiki.citizensnpcs.co/API
- Citizens Javadocs: https://jd.citizensnpcs.co/
- Citizens Maven repository: https://maven.citizensnpcs.co/#/repo/net/citizensnpcs/citizens-main/
- Paper API: https://jd.papermc.io/paper/1.21.11/
