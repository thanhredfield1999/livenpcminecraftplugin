# LivingNPC Hermes Handoff

Prepared: 2026-08-14

## Purpose

Continue diagnosing the underlying Citizens `GOING_TO_PLOT` `STUCK` route from the current
LivingNPC workspace. Do not assume the corrected navigation callback lifecycle fixes pathfinding.
The current local work adds bounded evidence collection; it does not contain a proven route fix.

## Mandatory Read Order

Read these before changing code:

1. `AGENTS.md`
2. `CURRENT_STATE.md`
3. `docs/RISK_REGISTER.md`
4. `docs/incidents/2026-08-14-diagnostics-repair-abi-rollback.md`
5. `docs/incidents/2026-08-14-farmer-outside-plot-crop-loop.md`
6. Current source and tests named below
7. `git status --short` and the current diff

`HANDOFF_NEXT_SESSION.md` is historical evidence only. It contains superseded states and is not
authoritative when it conflicts with current source, tests, `CURRENT_STATE.md`, or this handoff.

## Repository State

- Workspace: `E:\AI.WORK\living-npc-plugin`
- Branch: `main`
- Current HEAD: `c5f817d7951566271adcf90abbb915a6a2497da3`
- The working tree contains extensive active uncommitted development.
- Before this handoff file was created, Git reported 27 modified files and 14 untracked entries.
  Counts will drift as work continues; always inspect the current status instead of relying on them.
- Important current files including `AGENTS.md`, `CURRENT_STATE.md`, `docs/`,
  `NavigationDiagnostics.java`, and several tests are untracked relative to HEAD.
- Preserve every unrelated dirty change. Do not clean, reset, restore, or replace the working tree.
- Current source and tests are more authoritative than HEAD or historical notes.
- No commit was created for this handoff.

## Production Boundaries

Production is healthy and running. Do not touch it without new explicit user approval.

- Live JAR: `F:\minecraftserver\villagedefense2026\plugins\living-npc-0.6.0-rc.2.jar`
- Live SHA-256: `F64D03827D1386A856468ABC6937CB0A0A09B63ED1EA8021D49CC9571AE70D0F`
- Last observed Paper PID: `36800`
- Last observed listening ports: `11619` and `36102`
- RCON responded at the last check.
- Farmer verification rollback backup:
  `F:\minecraftserver\villagedefense2026\backups\livingnpc-farmer-runtime-verify-20260814-121249`
- Diagnostics rollback backup:
  `F:\minecraftserver\villagedefense2026\backups\livingnpc-diagnostics-deploy-20260814-113150`

Strict prohibitions without explicit approval:

- Do not deploy or replace the live JAR.
- Do not stop or restart Paper.
- Do not edit live YAML while Paper is running.
- Do not teleport NPCs.
- Do not change world time.
- Do not use `/reload`, PlugMan, or another hot-loader.
- Do not alter production configuration or Citizens data.
- Do not run automated integration work against production.

The invalid diagnostics candidate with SHA-256
`C627E021840EF32C5F64FD058137F91117F3575DF6E0232B199A5007C1CE6FE6` must never be deployed again.
It produced repeated `NoSuchMethodError` failures because a replaced `LivingNpcPlugin.class`
referenced `MiningRestorationStore.tick(long, int)` while the retained live class did not expose that
descriptor. Do not construct or deploy another isolated class-entry repair. Use a complete compatible
build; audit its contents and exercise it on disposable Paper before production approval.

## Production-Verified Farmer Result

Affected Farmer:

- Name: Steve
- LivingNPC UUID: `35d40d2f-eac9-464e-a45d-4d5576729903`
- Citizens NPC ID: `17`
- Home-area observation: approximately `StillCliff:-7,-57,5`
- Assigned plot center: approximately `StillCliff:74,-60,-5`
- Plot radius: `6`

The outside-plot state-machine policy is runtime-verified. Steve transitioned:

```text
FINDING_WORK -> GOING_TO_PLOT
```

The selected target was:

```text
StillCliff:69.5,-60,0.5
```

No `GOING_TO_CROP` attempt occurred while Steve was outside the plot. This proves the crop-selection
ordering fix, not navigation success.

Citizens ended the route as `STUCK`, and Steve did not move materially. The real StillCliff route
and fence-gate traversal remain unverified.

## Confirmed Diagnostics Defect And Local Fix

The runtime log showed historical `GOING_TO_BED` and `WANDERING` callbacks firing again during later
`GOING_TO_PLOT` attempts with increasing elapsed times.

Confirmed root cause from Citizens `2.0.42-SNAPSHOT` bytecode:

- While idle, `Navigator.getLocalParameters()` returns persistent default parameters.
- `Navigator.setTarget(...)` clones those defaults into active per-navigation parameters.
- LivingNPC previously attached single-use callbacks to the defaults before `setTarget(...)`.
- Old callbacks were therefore cloned into later navigation attempts.

Current local Farmer and Rancher ordering:

1. Configure persistent defaults.
2. Call `setTarget(...)`.
3. Obtain the active clone using `getLocalParameters()`.
4. Attach the single-use diagnostic callback to that active clone.

Regression test:

```text
NavigationDiagnosticsTest.obtainsActiveParametersOnlyAfterCitizensCreatesTheNavigation
```

This correction is local-only and not deployed.

## Citizens STUCK Diagnosis

The locally resolved Citizens artifact is `2.0.42-SNAPSHOT` build 4202 with SHA-256
`0D44ABDC2EEF1696764AF66AA4A152541AD211A6141F1D30BF49711A44B6B0D2`.
Because this is a snapshot dependency, confirm the intended runtime artifact before treating local
bytecode as byte-for-byte production truth.

Citizens maps several mechanisms to the same public `CancelReason.STUCK`:

- Current and target worlds differ.
- Target distance exceeds the active navigation range.
- The A* search frontier becomes empty.
- The A* maximum search budget is exceeded.
- The NPC is at an unsafe Y.
- Configured stationary detection fires.

The recorded route was approximately 76.5 blocks while diagnostics reported an effective active
range of approximately 102.3 blocks. Range rejection is therefore unlikely for that attempt, and
raising the range is not evidence-driven.

The strongest current hypothesis is that synchronous Citizens A* produced no usable path before
movement. This is not yet proven. Existing evidence cannot distinguish an empty A* frontier from
search-budget exhaustion or another `STUCK` branch.

Do not make speculative behavioral changes such as:

- Increasing Citizens range.
- Increasing search budgets without matching runtime evidence.
- Enabling teleport recovery.
- Force-loading chunks.
- Switching pathfinder threading.
- Removing safety examiners.
- Assuming the fence gate caused the failure.

Citizens' `DoorExaminer` recognizes fence gates and bottom-half doors as passable path points and can
open a gate when the NPC approaches it. No preserved event proves that Steve's selected path included
or reached the StillCliff gate. Absence of a gate event would not by itself prove a gate defect.

## Latest Local Diagnostic Extension

`NavigationDiagnostics` now records the following while the active strategy still exists:

- Requested LivingNPC target.
- Citizens-normalized target.
- Active strategy class.
- Path state: `none`, `absent`, `empty`, `present`, or `unavailable`.
- Active pathfinder type and effective range.
- Active stationary threshold.
- Active examiner class names.
- Current location, exact deltas, horizontal distance, margins, and elapsed ticks.

Path classification performs at most `iterator().hasNext()`. It does not traverse a route, scan
terrain, load chunks, alter navigation settings, or change recovery behavior.

Relevant files:

- `src/main/java/vn/heomc/livingnpc/NavigationDiagnostics.java`
- `src/main/java/vn/heomc/livingnpc/FarmerRuntime.java`, especially `navigate(...)`
- `src/main/java/vn/heomc/livingnpc/RancherRuntime.java`, especially `startNavigation(...)`
- `src/main/java/vn/heomc/livingnpc/LivingNavigation.java`
- `src/main/java/vn/heomc/livingnpc/VillageRouteExaminer.java`
- `src/test/java/vn/heomc/livingnpc/NavigationDiagnosticsTest.java`
- `src/test/java/vn/heomc/livingnpc/FarmerNavigationPolicyTest.java`
- `src/test/java/vn/heomc/livingnpc/RancherPathfindingPolicyTest.java`

## Latest Verification

Focused verification passed:

```powershell
.\gradlew.bat test --tests vn.heomc.livingnpc.NavigationDiagnosticsTest --tests vn.heomc.livingnpc.FarmerNavigationPolicyTest --tests vn.heomc.livingnpc.RancherPathfindingPolicyTest --console=plain
```

Required full verification passed:

```powershell
.\gradlew.bat clean test build --console=plain
```

The full build reported the already tracked Gradle deprecation warning for Gradle 10 compatibility.
Unit tests do not prove Citizens navigation, real gate traversal, chunk behavior, or Paper event
ordering.

## Next Task

Continue with the smallest evidence-driven diagnosis of Steve's real `GOING_TO_PLOT` route.

Without production approval, limit work to source/bytecode analysis, diagnostic correctness, tests,
and documentation. Do not claim the underlying route is fixed.

Production approval must identify the exact candidate JAR SHA-256 and the controlled observation
scope. Broad permission to continue diagnosis is not deployment approval. After the candidate hash
and observation scope are known, obtain explicit approval for those exact details. Then re-read
`AGENTS.md`, `CURRENT_STATE.md`, both incidents, and the current diff and follow the production safety
process exactly:

1. Confirm that the approved candidate path and SHA-256 still exactly match the file to deploy.
2. Confirm the candidate is a complete build and is not the invalid diagnostics repair archive.
3. Cleanly stop Paper through the approved mechanism.
4. Back up the live JAR and the complete `plugins/LivingNPC` directory with hashes.
5. Deploy only the exact approved complete candidate.
6. Restart and verify startup, ports, RCON, LivingNPC enablement, and absence of linkage errors.
7. Observe only the exact approved scope and the naturally activated controlled Farmer route unless
   the user separately approves
   stronger world intervention.
8. Capture the corrected single active `NPC_NAV_END` record for Steve.
9. Classify the result using normalized target, strategy, path state, stationary threshold, examiner
   set, effective range, elapsed ticks, and movement.
10. Treat fence-gate traversal as verified only with direct runtime evidence.
11. Do not introduce teleport recovery or force-loaded chunks.
12. Record runtime results in the incident and `CURRENT_STATE.md` only when operational facts change.

Interpretation guidance for the next corrected observation:

- Same world, in range, `AStarNavigationStrategy`, and `path=absent` or `path=empty` before movement:
  planning/search-space failure is strongly implicated.
- `strategy=none path=none` means no active strategy was available at callback inspection and is not
  sufficient to classify the underlying branch.
- A non-empty path with no movement and a non-negative stationary threshold implicates post-plan
  movement or stationary handling.
- A Citizens-normalized target different from the requested target shows destination normalization
  and must be considered in terrain inspection.
- A short failure with no path is more consistent with early frontier exhaustion; a later failure
  near the configured search-budget cadence is more consistent with budget exhaustion.
- A gate-open event proves the path reached a gate point; no gate event does not prove the gate was
  the cause.

## Non-Negotiable Conclusion

The outside-plot Farmer policy is fixed and runtime-verified. The callback contamination defect is
fixed locally and unit-verified. The underlying StillCliff Citizens route is unresolved. No current
evidence justifies a pathfinding behavior change, production deployment, or claim of runtime repair.
