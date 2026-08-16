# Farmer Outside-Plot Crop Loop

Date: 2026-08-14

## Symptom

Farmer Steve (`35d40d2f-eac9-464e-a45d-4d5576729903`) remained near his home at
`StillCliff:-7,-57,5` while assigned to the plot centered at `StillCliff:74,-60,-5` with radius 6.
The live log repeatedly recorded `FINDING_WORK -> GOING_TO_CROP` targets 74-77 blocks away,
followed one tick later by `GOING_TO_CROP -> FINDING_WORK` with navigation cancelled and no movement.
A `GOING_TO_PLOT` route to an entry approximately 76.5 blocks away failed the same way.

The NPC was spawned, player-activated, eligible, and in `ACTIVE_SHIFT`. No exception, schedule,
activation, or spawn failure explained the loop.

## Root Cause

`FarmerRuntime` routed inactive Farmers through `findPlotEntry()` and `GOING_TO_PLOT`, but
`FINDING_WORK` selected crop targets without first confirming that the NPC was within the assigned
plot. Failed crop navigation returned directly to `FINDING_WORK`, and ambient completion also set
that phase directly. A Farmer outside the plot could therefore repeatedly select distant crops
instead of recovering through the plot-entry phase.

Chest delivery did not have this gap: after depositing, it explicitly used `findPlotEntry()` and
`RETURNING_TO_PLOT` before resuming crop selection.

## Fix

Before scanning or selecting crops in `FINDING_WORK`, `FarmerRuntime` now checks whether the NPC is
within the plot's horizontal radius and vertical tolerance. An outside-plot Farmer uses the existing
`findPlotEntry()` route and enters `GOING_TO_PLOT`; crop selection does not run on that tick.

The change intentionally preserves the existing plot-entry candidate algorithm. Fence gates are
not detected explicitly; passable perimeter standing blocks remain the source of entry candidates.

## Regression Test

`FarmerNavigationPolicyTest.outsidePlotFarmerMustReenterBeforeSelectingCrops` covers Steve's
outside-plot position and verifies that a Farmer at the plot does not require recovery.

Verification completed locally:

- `FarmerNavigationPolicyTest`: passed.
- All `Farmer*Test` tests: passed.
- `.\gradlew.bat clean test build --console=plain`: passed.

## Runtime Verification

An approved controlled production deployment was completed on 2026-08-14 using the complete
locally built JAR with SHA-256
`F64D03827D1386A856468ABC6937CB0A0A09B63ED1EA8021D49CC9571AE70D0F`. The candidate contained
the current complete class set, including both `FarmerRuntime.requiresPlotEntry(...)` and
`MiningRestorationStore.tick(long, int)`. The previous live JAR and all eight LivingNPC data files
were backed up with verified hashes at
`F:\minecraftserver\villagedefense2026\backups\livingnpc-farmer-runtime-verify-20260814-121249`.

Paper reached `Done (32.415s)`, LivingNPC enabled normally, RCON and both configured ports remained
available, and the observation contained no LivingNPC linkage exception or health error.

The affected Steve produced the following real Paper evidence after the natural Farmer shift began:

```text
NPC_ACTION uuid=35d40d2f-eac9-464e-a45d-4d5576729903 ... phase=FINDING_WORK->GOING_TO_PLOT ... pos=StillCliff:-4,-57,7 target=StillCliff:69,-60,0
NPC_NAV_END uuid=35d40d2f-eac9-464e-a45d-4d5576729903 operation=GOING_TO_PLOT reason=STUCK ... target=StillCliff:69.5000,-60.0000,0.5000
```

No `GOING_TO_CROP` attempt occurred while Steve remained outside the plot. This runtime-verifies the
fixed phase-selection policy: an outside-plot Farmer attempts plot entry before crop selection.

The real StillCliff route and fence-gate traversal remain unverified. Citizens immediately or
repeatedly ended the plot-entry route as `STUCK`, Steve did not move materially from the home area,
and he later despawned when no player remained nearby. This incident is fixed for crop-selection
ordering, but the separate Citizens route failure still requires controlled diagnosis.

### Follow-up diagnostics defect

The runtime log also exposed a separate local diagnostics lifecycle defect. Historical
`GOING_TO_BED` and `WANDERING` callbacks fired again during every later `GOING_TO_PLOT` attempt with
continuously increasing elapsed times. Citizens `2.0.42-SNAPSHOT` bytecode confirmed the cause:
when idle, `Navigator.getLocalParameters()` returns persistent default parameters, while
`Navigator.setTarget(...)` clones those defaults into active per-navigation parameters. LivingNPC
was attaching single-use callbacks to the defaults before calling `setTarget(...)`, so every old
callback was cloned into future navigation attempts.

The local Farmer and Rancher call sites now configure defaults, call `setTarget(...)`, then attach
diagnostics to the active parameter clone returned by `getLocalParameters()`. Regression coverage in
`NavigationDiagnosticsTest.obtainsActiveParametersOnlyAfterCitizensCreatesTheNavigation` verifies
that ordering. Focused diagnostics/Farmer/Rancher tests and the full clean build passed.

This follow-up change is not deployed. It removes cross-navigation callback contamination but does
not by itself prove or fix the underlying StillCliff pathfinder `STUCK` result. Another deployment
and controlled observation require explicit approval.

### Route diagnosis status

Citizens `2.0.42-SNAPSHOT` build 4202 bytecode maps several distinct failures to `STUCK`: a
different-world or out-of-range preflight rejection, an empty A* frontier, the maximum A* search
budget, unsafe Y, and configured stationary detection. The recorded 76.5-block route was within the
reported active range of approximately 102.3 blocks, so increasing range is not evidence-driven.
The strongest current hypothesis is that synchronous Citizens A* produced no usable path before
movement, but the existing callback did not preserve path state and cannot distinguish an empty
frontier from search-budget exhaustion or another `STUCK` branch.

Local diagnostics now also capture Citizens' normalized target, active path strategy and path
presence, stationary threshold, and examiner types from the active per-navigation parameters. This
is bounded observation only: it does not scan terrain, load chunks, alter pathfinding settings, or
change recovery behavior. Focused `NavigationDiagnosticsTest`, `FarmerNavigationPolicyTest`, and
`RancherPathfindingPolicyTest` verification passed, followed by a successful
`.\gradlew.bat clean test build --console=plain`. The underlying StillCliff route remains unresolved
until an explicitly approved controlled runtime observation supplies the new evidence.
