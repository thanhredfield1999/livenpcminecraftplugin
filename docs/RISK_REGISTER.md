# LivingNPC Risk Register

Last reviewed: 2026-08-15

This register separates confirmed defects from risks that require a real Paper environment. Closing an item requires evidence, not only a code change.

## Open High Risks

### R-001: Runtime disable does not own a complete stop transition

- Evidence: source now routes runtime disable through `RuntimeStopCoordinator`, including Door passage and guarded-examiner close tasks, manager suspension, claims and temporary entities. This has unit coverage but not complete Paper evidence for every phase.
- Possible effect: a missed task/entity/claim outside the coordinator could continue after `/lnpc reload` disables runtime ticks.
- Current safeguard: one idempotent runtime-stop transition invokes bounded cleanup components and continues after an individual cleanup exception.
- Required verification: disable runtime during each navigation/door phase on a controlled Paper server and assert no later mutation occurs.

### R-002: Citizens API readiness is not independently verified

- Evidence: Citizens documents that global API access may be unavailable until `CitizensEnableEvent` or `CitizensLoadEvent`. LivingNPC has a hard `depend: [Citizens]`, but its managers access Citizens-backed state from the normal plugin lifecycle.
- Possible effect: load-order or registry-readiness failures, especially across Citizens upgrades or unusual startup paths.
- Required safeguard: verify the deployed Citizens lifecycle. If registry access can be null at LivingNPC enable, defer Citizens-dependent initialization until the documented event and make initialization idempotent.
- Required verification: cold start, restart, missing/incompatible Citizens, and Citizens registry-load tests.

### R-003: Whole-file YAML writes occur on the Paper server thread

- Evidence: periodic economy flushes and several state transitions serialize and replace YAML synchronously from tick or event paths.
- Possible effect: a slow disk or large journal/store can exceed Paper's 50 ms tick budget.
- Required safeguard: first instrument representative save latency and data sizes. If necessary, snapshot immutable data on the server thread and serialize in an ordered worker without moving Bukkit access off-thread or weakening crash consistency.
- Required verification: record p50/p95/p99 save duration and main-thread attribution under representative maximum data.

### R-004: Configuration reload has restart-only behavior that is not explicit

- Evidence: `WorldMutationPolicy` snapshots WorldGuard availability and `protection.require-worldguard` during enable; `/lnpc reload` replaces other config objects but not this policy.
- Possible effect: operators believe a protection change applied when the old policy remains active.
- Required safeguard: either safely replace the policy everywhere or report this setting as restart-required. Prefer the smaller restart-required contract unless hot replacement is proven safe.
- Required verification: reload tests for both values with WorldGuard present and absent.

## Open Medium Risks

### R-005: Persistence replacement fallback lacks crash recovery

- Same-directory temporary files reduce risk, but filesystems without atomic moves fall back to replacement without a validated bounded backup and rollback sequence.
- Add backup/recovery only with failure-injection tests for temporary write, validation, backup, replacement, and restoration failures.

### R-006: Enable and disable are not transactional

- A failure during staged initialization can leave earlier components active. One shutdown exception can prevent later cleanup or final persistence.
- Add failure-injection lifecycle tests before restructuring bootstrap. Cleanup should continue after nonfatal component errors while preserving dependency order.

### R-007: WorldGuard query failure is indistinguishable from a policy denial

- Fail-closed behavior is correct, but broad caught linkage/runtime failures currently lack rate-limited diagnostics.
- Test explicit allow, deny, inheritance, overlap priority, global region, and absent WorldGuard against `7.0.16`; then add rate-limited causal logging without changing denial behavior.

### R-008: In-memory path caches need lifecycle invalidation evidence

- Village deletion, center/world changes, config reload, or runtime stop may retain stale cached locations until replacement or plugin unload.
- Verify cache invalidation and bounded cardinality across village delete/recreate and world changes.

### R-009: Dependency snapshots reduce reproducibility

- Paper API and Citizens use `-SNAPSHOT` dependencies. The same version string can resolve to different artifacts over time.
- Evaluate Gradle dependency locking or verified pinned builds without breaking compatibility with the exact deployed Paper/Citizens versions.

### R-010: Unit coverage does not include a managed Paper integration server

- JUnit and Mockito cover domain behavior but not Paper/Citizens/WorldGuard integration.
- Evaluate a separate disposable `run-paper` test environment. Do not point automated integration tasks at production.

### R-014: Guarded replacement for Citizens DoorExaminer needs Paper lifecycle evidence

- Evidence: Citizens 2.0.42 build 4173 produced repeated `DoorExaminer$DoorOpener.run` null-block exceptions, and its delayed close callback does not revalidate `Openable`. Source now disables Citizens' automatic examiner only for managed NPCs and installs `LivingDoorExaminer`, preserving `NPCOpenDoorEvent`/`NPCOpenGateEvent` with null/type guards and owned close-task cleanup.
- Possible effect: Paper/Citizens event ordering, region scheduling, double-door coordination or fence-gate close timing may differ from unit fixtures even though local tests pass.
- Required verification: approved clean restart with the exact candidate hash; observe ordinary door, double door, spawned-inside-door recovery and configured fence gate. Require no upstream `DoorExaminer` in active managed-NPC diagnostics, no null/type exception, no leaked open block and signed-plane crossing evidence from the read-only BotChecker observer.
- Incident: `docs/incidents/2026-08-15-door-navigation-ownership-and-citizens-examiner.md`.

### R-013: Current build uses features deprecated before Gradle 10

- Evidence: the 2026-08-14 full Gradle 9.3.0 build passed but reported deprecated features that will be incompatible with Gradle 10.
- Required safeguard: run the full build with `--warning-mode all`, identify whether warnings come from project scripts or dependencies, and resolve them in a dedicated build-maintenance change.
- Do not upgrade the wrapper as part of an unrelated runtime or persistence fix.

## Closed By Current Safeguards

### R-011: Older plugin could overwrite future economy or needs schemas

- Safeguard: economy, needs, and mining-restoration stores reject invalid or newer schema versions and disable writes; economy mutations also fail closed while its store is unavailable.
- Regression coverage: unsupported-schema files remain byte-for-byte unchanged after attempted writes, and economy production is rejected.

### R-012: Mining restoration tick could inspect the entire journal

- Safeguard: a persistent queue lets each tick inspect at most its explicit budget without copying or traversing the full journal. Deferred and invalid entries rotate for forward progress.
- Regression coverage: repeated ticks over a deferred journal report only the configured bounded work and retain pending data.

## Authoritative References

- Paper scheduling: https://docs.papermc.io/paper/dev/scheduler/
- Paper configuration: https://docs.papermc.io/paper/dev/plugin-configurations/
- Paper debugging: https://docs.papermc.io/paper/dev/debugging/
- Citizens API lifecycle: https://wiki.citizensnpcs.co/API
- Citizens Javadocs: https://jd.citizensnpcs.co/
- WorldGuard dependency guidance: https://worldguard.enginehub.org/en/latest/developer/dependency/
- WorldGuard protection queries: https://worldguard.enginehub.org/en/latest/developer/regions/protection-query/
- OpenCode rules: https://opencode.ai/docs/rules/
- OpenCode MCP configuration: https://opencode.ai/docs/mcp-servers/
- MCP security practices: https://modelcontextprotocol.io/docs/tutorials/security/security_best_practices
