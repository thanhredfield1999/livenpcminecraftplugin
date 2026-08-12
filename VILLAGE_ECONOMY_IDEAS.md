# LivingNPC Village Economy And Behavior Ideas

Last updated: 2026-08-13

## Vision

LivingNPC should create a village that appears purposeful, coordinates its
workers, sustains its residents and participates in trade without requiring an
LLM to control gameplay.

The core loop is:

```text
Produce -> reserve -> consume -> process -> export surplus
        -> earn money -> pay costs/import shortages -> reinvest
```

Autonomous construction and combat are outside the current scope. The economy
may expose future extension points for building projects, but it must not
reserve construction materials, register a builder runtime or mutate building
blocks while that feature is disabled.

## Current Foundation

The source already contains foundations for:

- Independent villages with separate virtual storage and balances.
- Farmers, fishers, ranchers, cooks, crafters, miners and security workers.
- Residents, temporary visitors, schedules, role XP and work zones.
- Visitor purchases and bounded production quotas.
- Delivery locations, activity history and atomic YAML persistence.
- Citizens navigation, WorldGuard-aware world mutation and fail-closed safety.

The main missing component is not another profession. It is a deterministic
village-level economic brain that connects the existing professions.

## Architecture

Use three layers with clear ownership:

```text
Village Planner
    -> creates requests and work orders
Profession Runtime
    -> executes a profession-specific workflow
NPC Behavior
    -> navigation, equipment, animation, rest and social presentation
```

- The planner never navigates entities or scans the world.
- Profession runtimes never decide global economic policy.
- Behavior code never creates or destroys economic value by itself.
- Gemini, if enabled later, may only paraphrase bounded dialogue context. It
  cannot select coordinates, quantities, prices, jobs, commands or mutations.

## NPC Behavior Core

### Priority Stack

Use a shared priority model:

| Priority | Category | Examples |
| ---: | --- | --- |
| 100 | Emergency | Fire, nearby monster, stuck recovery |
| 90 | Village safety | Alarm, shelter, close market |
| 80 | Mandatory needs | Sleep, food, end-of-shift return |
| 70 | Committed work | Finish harvest, recipe or delivery transaction |
| 60 | New work order | Production, transport or service request |
| 40 | Daily life | Lunch, market visit, social activity |
| 20 | Ambient | Wander, watch player, look around |
| 0 | Idle | Rest in place |

Rules:

- Danger may interrupt every non-emergency task.
- End of shift waits for the current atomic action to complete.
- Social and ambient actions yield immediately to real work.
- An NPC does not switch role or order during a committed task.
- Every claimed order has a lease and deadline.
- Replanning occurs on completion, cancellation, timeout, invalid
  infrastructure, danger, despawn or shutdown, not every tick.

### Common Lifecycle

Prefer a small common lifecycle with profession-specific substates:

```text
INACTIVE
TRAVELING
WORKING
DELIVERING
RESTING
SOCIALIZING
SLEEPING
SHELTERING
EMERGENCY
BLOCKED
```

Examples of profession substates:

```text
Farmer: FIND_CROP -> TRAVEL -> INSPECT -> HARVEST -> REPLANT -> DEPOSIT
Cook: CLAIM_RECIPE -> WAIT_INPUT -> PREPARE -> COOK -> DEPOSIT
Courier: RESERVE -> PICKUP -> TRANSPORT -> DELIVER -> CONFIRM
```

Avoid continuing to expand one shared `FarmerPhase` enum with unrelated states
for every profession.

### Commitment And Recovery

An active task records:

- Intent and work-order ID.
- Source, destination and current substate.
- Start tick, deadline and lease expiry.
- Retry count and last failure reason.
- Reserved inputs and carried outputs.

Navigation recovery:

```text
Try target
-> try alternate safe standing location
-> try alternate delivery point
-> release reservation and return order to queue
-> enter BLOCKED with backoff
-> report an actionable reason in the dashboard
```

Do not teleport NPCs as routine stuck recovery. Teleportation remains an admin
recovery action only.

### Lightweight Personality And Memory

Character profiles remain optional. Traits may adjust presentation and timing
by at most about 5-10 percent:

- Diligent: slightly shorter optional rests.
- Social: more social activity outside work.
- Cautious: reacts to danger slightly earlier.
- Curious: notices players more often.
- Solitary: prefers individual ambient activities.

Persist only useful short-term memory:

- Last successful and failed target.
- Consecutive failure count.
- Current or last work order.
- Last social partner.
- Last danger location.
- Recent activities and delivery point.

Profiles being disabled must never disable the economy or worker runtime.

## Daily Operation

### Morning

- Wake, leave bed and evaluate mandatory needs.
- Planner prepares the day's orders from stock shortages.
- Workers travel to their assigned zones.
- Couriers resume valid requests from the prior day.
- Security begins or changes patrol shift.

### Work Period

- Primary gathering and production occurs.
- Food reserve and urgent dependencies have priority.
- Workers process bounded batches rather than one global target scan.
- Outputs enter the protected virtual economy only after the visible action
  succeeds.

### Lunch

Generalize the existing farmer lunch behavior into a reusable daily plan:

- Finish the current atomic action.
- Put away equipment.
- Return home or visit an assigned meeting point.
- Eat, rest or socialize briefly.
- Return to the work zone without ending the shift.
- Do not sell inventory or reset quotas.
- Stagger breaks so all residents do not move simultaneously.

### End Of Shift

- Stop accepting non-urgent new orders near closing time.
- Finish the current atomic action and deliver carried goods.
- Persist production and activity records.
- Export only stock above the export threshold, never the full store.
- Return home, visit the market or socialize.

### Night

- Residents with valid beds sleep in them.
- Residents without beds rest at home and create a housing warning.
- Security may use a separate night schedule.
- Planner performs only lightweight maintenance.
- Normal production does not run unless explicitly scheduled.

## Village Economy Core

### Stock Policy

Every relevant item may define:

```yaml
stock-targets:
  wheat:
    minimum: 32
    target: 64
    reserve: 16
    export-above: 96
```

- `minimum`: shortage threshold.
- `target`: desired operating stock.
- `reserve`: unavailable to visitors and non-essential recipes.
- `export-above`: only stock above this amount may be sold as surplus.

Useful calculations:

```text
shortage = max(0, target + reservedForOrders - stock - incoming)
surplus  = max(0, stock - exportAbove - reservedForOrders)
daysOfSupply = usableStock / expectedDailyConsumption
```

Display food, fuel, animal feed and tool reserves in days when consumption is
known. Days of supply are more actionable than raw item counts.

### Request Board

Professions and village systems create explicit requests rather than directly
pulling arbitrary items from the store.

A request records:

- Requester or destination work zone.
- Item, quantity, category and purpose.
- Priority, creation time, deadline and retry state.
- Status: `WAITING`, `RESERVED`, `DELIVERING`, `FULFILLED`, `BLOCKED` or
  `CANCELLED`.
- A readable block reason.

Default priority order:

1. Safety.
2. Essential food.
3. Inputs blocking another profession.
4. Tools and maintenance.
5. Export goods.
6. Luxury goods.

Reservations are mandatory:

```text
available -> reserved -> in transit -> delivered -> consumed/produced
```

Failure releases the reservation and must not lose or duplicate goods.

### Data-Driven Recipes

Move hard-coded production recipes to validated YAML:

```yaml
recipes:
  bread:
    profession: cook
    station: cooking
    inputs:
      wheat: 3
    outputs:
      bread: 1
    work-ticks: 100
```

Recipes may include input, output, byproduct, fuel, station, work time and
knowledge requirements. Build a dependency graph at load time and reject
cycles. A request for an output may create requests for missing dependencies.

Keep chains useful and readable. A process should have at most one or two
byproducts, and a byproduct should not be enabled before it has a consumer.

### Village Planner

Run one planner per village at a staggered, low frequency. A planning cycle:

1. Capture a store and worker snapshot.
2. Calculate consumption, reserves, shortages and surplus.
3. Update existing requests and incoming quantities.
4. Resolve missing recipe dependencies.
5. Create bounded work orders.
6. Match orders to eligible workers.
7. Mark exportable goods.
8. Record bottlenecks for the dashboard.

Worker matching considers:

```text
village urgency
role preference and skill
availability and schedule
estimated travel cost
recent route failures
```

Work preferences should remain simple in the GUI: primary role, secondary
role, emergency-only role and prohibited role.

### Consumption And Wellbeing

Start with village-level daily needs instead of per-minute hunger:

- Food units per resident per day.
- Optional extra consumption for heavy work.
- Food diversity groups: grain, vegetables, fish, meat and prepared food.
- Housing readiness, safety and fatigue.

Shortage effects remain soft:

- Prioritize food and suspend luxury production.
- Reduce productivity slightly after prolonged shortage.
- Stop recruitment.
- Change deterministic dialogue and dashboard warnings.

NPCs do not die or permanently leave from hunger in the first implementation.

Fatigue uses four states only: `RESTED`, `NORMAL`, `TIRED`, `EXHAUSTED`.
Lunch, sleep, food and festivals reduce fatigue. Overtime, danger, missing beds
and excessive walking increase it.

### Treasury

Income:

- Visitor sales.
- Contracts and exports.
- Inter-village trade.
- Event and quest rewards.

Expenses:

- Symbolic wages.
- Emergency imports.
- Work-zone maintenance.
- Market and caravan operation.
- Festival costs.
- Storage and profession upgrades.

The currency remains private to LivingNPC. Do not connect to Vault unless a
separate gameplay decision explicitly requires it.

## Profession Behavior

### Farmer

- Produce according to food, ranch and cooking shortages.
- Select the most needed supported crop instead of harvesting indefinitely.
- Process a bounded batch, preserve replant seed and deposit once per batch.
- Stop when target, quota or capacity is reached.
- Never sell stock below reserve.

### Fisher

- Maintain the fishing rod throughout all fishing phases.
- Treat each cast as one task with a deadline and cleanup.
- Return to storage when carry capacity is reached.
- Stop at role quota or stock target.
- Classify catches as raw food, cooking input or market goods.

### Rancher

- Perform bounded morning/cycle checks rather than constant entity scans.
- Preserve breeding adults, breed below target and process only surplus above
  the cap.
- Request feed instead of consuming reserve unexpectedly.
- Later add eggs, wool and milk before expanding slaughter chains.

### Miner

- Work only inside the validated mining zone and allowlist.
- Reserve restoration data before breaking a block.
- Fail closed if restoration persistence is unavailable.
- Select material from actual blocks and village shortages; do not combine real
  ore breaking with unrelated random output.
- Deposit a bounded carried batch.

### Cook And Crafter

- Receive an explicit recipe order.
- Request and reserve inputs.
- Wait visibly when an input is missing.
- Travel to the validated station and perform an animation.
- Commit the atomic input/output transformation only after the visible action.
- Deposit output and release the work order.

Crafter priority: essential tools, profession inputs, replacements, export
goods and finally luxury goods.

### Courier

- Claim compatible requests and group nearby destinations.
- Reserve stock before travel.
- Perform pickup and delivery animations while virtual storage remains the
  source of truth.
- Use limited logical capacity.
- Release reservations on failure, despawn or shutdown.
- Never force-load chunks or use routine teleportation.

### Security

While combat is disabled:

- Patrol bounded routes.
- Detect nearby monsters.
- Ring an alarm with cooldown.
- Mark a temporary danger area.
- Cause workers to shelter or return home.
- Report threats without dealing damage.

### Residents And Visitors

Residents create demand, consume food and participate in daily life. They may
perform temporary low-skill duties such as carrying goods during harvest or
market day, but they do not generate free resources.

Visitor profiles:

- Commoner: bread and prepared food.
- Merchant: surplus crops, fish and wool.
- Artisan: iron, leather and tools.
- Wealthy visitor: quality food and luxury goods.

Visitors buy only exportable goods according to demand and wallet, then leave
through the same gate.

## Medieval Simulation Inspirations

### Seasons And Seasonal Labor

Use a configurable economic season cycle without requiring another seasons
plugin:

- Spring: planting and farm preparation.
- Summer: growth and fishing.
- Autumn: harvest and storage peak.
- Winter: processing, crafting and higher reserves.

Seasons primarily change targets, demand and work priorities rather than
granting large output multipliers. Temporary duties may reassign idle residents
as carriers during harvest, winter preparation or market day without changing
their permanent profession.

### Tool Wear And Maintenance

Represent profession tools with logical action charges instead of per-hit
physical durability. When charges are low, create a tool request for the
crafter. Provide starter/fallback tools or imports to prevent dependency
deadlocks.

### Food Variety And Limited Spoilage

Food diversity grants a small wellbeing or visitor-attraction bonus, capped at
about five percent. Do not require a large list of foods.

Spoilage is a later optional feature. If enabled, process aggregated batches or
one daily percentage, never per-item timers. Raw fish and meat decay faster
than grain or cooked food. Preservation must create gameplay value before
spoilage is enabled.

### Market Day

Run a visible market event every few Minecraft days:

- Planner prepares export goods.
- Couriers stage goods at the market.
- Residents gather and security patrols.
- Bounded visitors arrive.
- Unsold goods return to normal stock.
- A market report records revenue and bottlenecks.

This is preferred over continuous high visitor traffic because it creates a
recognizable village rhythm.

### Timed Contracts

Merchants or villages may request a bounded shipment by a deadline. Completing
it grants currency and reputation. Failure should have a mild cooldown or lost
opportunity, not a destructive penalty.

Contracts force a choice between resident reserves, immediate sales and future
reputation.

### Village Policies

Provide a few explicit trade-offs rather than detailed politics:

- Food: frugal, normal or generous.
- Trade: preserve stock, balanced or export-focused.
- Labor: relaxed, balanced or peak effort.
- Security: open, controlled or closed gate.

No policy should always be optimal.

### Specialization

A village earns one primary and optionally one secondary specialization from
real accomplishments: agriculture, fishing, ranching, mining, crafting or
trade. Bonuses remain small. Specialization makes inter-village trade useful
because one village should not be best at everything.

### Knowledge And Apprenticeship

Village knowledge may unlock a small set of recipes or capabilities through
worker levels, contracts, merchants or trade. Keep the initial knowledge graph
to roughly 8-12 nodes.

An experienced worker may teach one apprentice outside peak work time. This
trades current production for future XP and provides visible social behavior.

### Approval And Reputation

Use one village approval score derived from food days, variety, housing,
safety, workload, market access and recent events. Approval affects recruitment
and visitor quality, not basic runtime eligibility.

Track separate external reputation only when its gameplay exists:

- Trade reputation.
- Regional/village reputation.
- Player reputation with the village.

### Events, Festivals And Chronicle

Deterministic events may offer two or three clear choices, costs and outcomes:

- Long rain or strong fishing season.
- Animal-feed shortage.
- Merchant or neighboring village request.
- Harvest festival.
- Refugee/recruitment request.
- Blocked delivery route.

Limit major events to approximately one every 3-7 days and never delete stock
or damage player buildings without explicit warning and consent.

Festivals consume food and treasury funds, reduce fatigue and improve approval.
They are a useful resource sink and visible social event.

Store only important milestones in a bounded village chronicle, such as first
trade route, resolved shortage, profession milestone and festival. Do not store
every task as history.

### Household Associations

A later lightweight household model may associate 1-3 residents with one home,
shared social preferences and grouped consumption. It must not initially add
birth, aging, inheritance or genetics.

## Activation And Performance

Use three activation levels:

### Active

A player is near the village. Navigation, animation, bounded work scans,
visitors and permitted world mutations run normally.

### Warm

The chunk is loaded but no player is close. Avoid navigation and repeated
entity/block scanning. Only safe maintenance and previously committed virtual
settlement may run.

### Dormant

No nearby player and relevant chunks are unloaded. Do not spawn NPCs, scan,
pathfind, mutate blocks or continuously generate resources.

Initial decision: offline production is disabled. Any future catch-up
simulation needs a separate balance decision, a strict time cap, reduced
efficiency and no rare output or world mutation.

Suggested scheduling:

| Interval | Work |
| --- | --- |
| 10 ticks | Active NPC phase and navigation checks |
| 20-40 ticks | Arrival and nearby danger checks |
| 100 ticks | Bounded profession scans |
| 200 ticks | Request assignment |
| 400 ticks | Stock evaluation |
| 1200 ticks | Planner, visitors and economy flush |
| End of day | Consumption, expenses and report |

Stagger village work so every village does not evaluate on the same server
tick. Do not scan the world globally or force-load chunks.

## Dashboard

Add a town-hall view with actionable information:

```text
Status: STABLE
Population: 8 / 12
Storage: 284 / 512
Treasury: 1,245 Xu dong
Food reserve: 3.2 days
Open requests: 4
Blocked requests: 1
Today's income: 128
Today's expenses: 73
Bottleneck: coal
Suggested worker: miner
Exportable: wheat, cod
```

Suggested sections:

- Overview.
- Stock rules and reserves.
- Request board.
- Production and workers.
- Trade and treasury.
- Population and wellbeing.
- Daily reports and chronicle.
- Alerts and blocked reasons.

## Future Building Extension

Prepare only data contracts while autonomous building is disabled:

```text
VillageProject
ProjectStage
MaterialRequirement
ReservedMaterial
ProjectPriority
ProjectStatus
```

All projects remain `LOCKED`. They do not reserve materials, register a builder
or mutate blocks. Later, a building project may become another consumer of the
same request and recipe system without redesigning the economy.

## Features To Reject Or Defer

- Per-minute hunger and thirst for every NPC.
- Detailed disease or contagion simulation.
- Full birth, aging, inheritance and genetics.
- Crime, prison and deep noble politics.
- Physical item entities for the full economy.
- Unbounded dynamic pricing.
- LLM-controlled jobs, transactions, coordinates or mutations.
- Random disasters that damage player structures.
- Autonomous construction before the economy reaches a stable 1.0.
- Combat mixed into the economy runtime before combat is explicitly resumed.

## Roadmap

### 0.5.x - Runtime Stabilization

- Fix Fisher equipment/phase synchronization.
- Fix Rancher task restart and log spam.
- Smoke test Farmer lunch, batch and delivery behavior.
- Test Cook, Crafter, Miner and Security independently.
- Standardize runtime status and block reasons.
- Verify restart persistence and update stale documentation.

Exit criteria: no repeated watchdog stalls, log spam, lost village data or
runtime task that cannot terminate cleanly.

### 0.6.0 - Behavior Core

- Intent, task, priority, result and block-reason models.
- Task commitment, lease, deadline and retry backoff.
- Emergency interruption and clean resume/cancel behavior.
- Shared daily plan and profession-specific substates.
- Primary/secondary/emergency-only role preferences.

### 0.7.0 - Economy Foundation

- Stock minimum, target, reserve and export threshold.
- Days-of-supply calculations.
- Request Board and atomic reservations.
- Dashboard shortage, surplus and blocked requests.

### 0.8.0 - Production Intelligence

- Validated YAML recipes and dependency graph.
- Cycle detection.
- Work orders and village planner.
- Tool maintenance and useful byproducts.
- Shortage-driven profession selection.

### 0.9.0 - Village Needs

- Daily food consumption and food diversity.
- Fatigue, housing, safety and bounded approval.
- Daily economic report and village chronicle.
- Soft shortage consequences.

### 0.10.0 - Logistics

- Courier profession and capacity.
- Pickup, transit, delivery and reservation recovery.
- Logical storage filters and route batching.
- Travel cost and work-zone efficiency.

### 0.11.0 - Living Market

- Visitor demand profiles and export-only goods.
- Market Day.
- Treasury expenses and emergency imports.
- Bounded price variation, recommended 70-150 percent of base price.
- Timed contracts.

### 0.12.0 - Seasons And Events

- Economic seasons and winter preparation.
- Seasonal temporary duties.
- Choice-based events and festivals.
- Optional spoilage only after balance validation.

### 0.13.0 - Knowledge And Progression

- Village knowledge and recipe unlocks.
- Apprenticeship.
- Limited product quality.
- Merchant knowledge and trade reputation.

### 0.14.0 - Regional Economy

- Village specialization.
- Atomic inter-village import/export.
- Visual caravans without chunk loading.
- Regional contracts and events.

### 0.15.0 - Population And Society

- Population capacity and recruitment readiness.
- Labor shortage recommendations.
- Lightweight households and relationship behavior.
- Village identity and culture presentation.

### 1.0.0 - Stable Living Economy

Required outcomes:

- A village can maintain food using coordinated professions.
- Requests connect professions without duplicate or lost goods.
- Logistics and market activity are visible to players.
- Money has both sources and sinks.
- Seasons/events create rhythm without destructive randomness.
- Restart and save failure behavior are safe and testable.
- Dashboard block reasons explain why work is not progressing.
- The experience is complete without autonomous construction or combat.

## First Vertical Slice

Implement and validate one narrow economy before expanding every profession:

```text
Farmer produces wheat
-> stock policy calculates food days
-> Cook receives a bread work order
-> missing inputs create requests and reservations
-> residents consume bread
-> surplus is prepared for Market Day
-> visitors purchase exportable bread
-> treasury receives revenue
-> end-of-day report records the result
```

After the base loop survives restarts, add logical tool wear so a Crafter must
maintain Farmer/Cook tools. Then add a timed bread contract shortly before a
seasonal reserve deadline. This tests the meaningful decision between resident
food, immediate sales and future reputation.

## Current Decisions

- Autonomous construction: hard off until after the stable economy milestone.
- Combat: deferred and kept separate from the economy roadmap.
- Offline production: off by default.
- Economy: private to LivingNPC, separate from Vault.
- Dynamic prices: bounded, never fully free-floating.
- Hunger consequences: soft; no automatic resident death or permanent exit.
- Gemini: dialogue-only and optional, with deterministic fallback.
- First implementation target: wheat -> bread -> consumption -> Market Day.

## Research References

- MineColonies request system: https://minecolonies.com/wiki/systems/request/
- MineColonies warehouse: https://minecolonies.com/wiki/buildings/warehouse/
- Millenaire: https://www.millenaire.org/
- Manor Lords official wiki: https://wiki.hoodedhorse.com/Manor_Lords/Beginner%27s_Guide/en
- Clanfolk official wiki: https://wiki.hoodedhorse.com/Clanfolk/Game_Mechanics
- Against the Storm official wiki: https://wiki.hoodedhorse.com/Against_the_Storm/Hostility
- Project Sid: https://arxiv.org/html/2411.00114v1

These references are used for general simulation design patterns only. The
LivingNPC implementation should remain original and tailored to Paper,
Citizens, WorldGuard and the HeoMC economy.
