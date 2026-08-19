# LivingNPC CORE Movement Journal

Mục đích: ghi toàn bộ nghiên cứu, thay đổi, thành công, thất bại, RCA, test, review và runtime smoke của CORE movement. Đây là tài liệu tham khảo giữa các phase; không kết luận từ trí nhớ chat.

## Quy tắc ghi nhận

Mỗi lần thay đổi phải ghi:

- Thời điểm.
- Phạm vi/file.
- Symptom hoặc mục tiêu.
- Evidence nguồn/local log.
- RCA.
- Thay đổi.
- Test command và kết quả thật.
- Production deploy/smoke nếu có.
- Status: PASS, FAIL, BLOCKED hoặc NOT VERIFIED.
- Việc còn lại và risk.

Không ghi credentials, token, private player data hoặc log nhạy cảm. Không đánh dấu runtime PASS bằng unit test בלבד.

## 2026-08-19 — Controlled Paper pathfinding RCA: Alaric gate route

### Follow-up instrumentation slice

- Added `GateRouteCoordinator` navigation generation and current-leg accessors.
- Gate navigation callback operation now records `GOING_TO_PLOT_GATE_GATE_<LEG>_GEN_<N>` instead of collapsing all legs under one operation name.
- Existing `Navigation` implementations remain source-compatible through default overloads; production behavior unchanged.
- Added regression test proving each leg start increments generation and transitions `APPROACH` to `EXIT`.
- Focused/full unit result: `./gradlew.bat test --no-daemon --console=plain` — `BUILD SUCCESSFUL`.
- Earlier clean build had transient `NoClassDefFoundError` in unrelated `WorldMutationPolicyReloadTest`; immediate rerun passed.
- Latest full verification: `./gradlew.bat clean test build --no-daemon --console=plain` — `BUILD SUCCESSFUL`; artifact `build/libs/living-npc-0.6.0-rc.2.jar` created. Runtime artifact remains not deployed.
- Controlled Paper/runtime: not rerun after instrumentation; no deploy/restart/reload.
- Status: `INSTRUMENTED`, RCA narrowing pending controlled smoke.


- Scope: Farmer Alaric, controlled Paper `F:\\minecraftserver\\villagedefense2026`, world `StillCliff`, current source only; no deploy/restart/reload.
- Evidence: repeated `NPC_NAV_END` for `uuid=df648175-d295-4e6e-a9f6-eefb0f43ff2f`, operation `GOING_TO_PLOT_GATE`, reason `PLUGIN`, `path=present`, active `distanceMargin=0.7500`, target `StillCliff:57.5000,-60.0000,-1.5000`, current `StillCliff:39.5001,-60.0000,-1.0750`, elapsed `400` ticks. Server log count in captured session: `27` gate `PLUGIN`, `5` `NPC_DESPAWNED`; no gate crossing verdict.
- Source trace: `FarmerRuntime.startGateRoute` creates gate legs with Citizens `distanceMargin=0.0` and `pathDistanceMargin=0.0`; `GateRouteCoordinator` separately requires exact approach/exit signed-plane conditions. Citizens API docs confirm `pathDistanceMargin` controls pathfinder stop distance and `distanceMargin` controls navigation completion margin.
- Confirmed: Citizens creates a path (`path=present`), so A* path construction is not the primary failure. `PLUGIN` is our callback cancellation/replacement boundary, not proof of Citizens `STUCK`; `NavigationDiagnostics` maps callback reason directly.
- Confirmed: Alaric retries same route from same position, then runtime changes phase to `INACTIVE`; subsequent tick restarts intent. This is a retry loop, not successful pathfinding.
- Strong RCA: plugin-owned gate coordinator waits for exact target/route-leg arrival, while Citizens callback terminates current navigation before exact leg arrival. The controlled log does not yet identify whether callback trigger is Citizens goal completion, plugin target replacement, or door/gate examiner interaction; current diagnostics lack callback generation/owner and route-leg identity. Therefore do not claim one narrower sub-cause.
- Secondary confirmed risk: `RuntimeChunkAvailability` rejects unloaded target/area, but gate route start only checks candidate chunk and target range; no per-leg loaded-area evidence is logged. `NPC_ACTIVITY` reported `world hoặc chunk làm việc chưa load`, but this is not enough to prove target chunk was unloaded at callback time.
- Research: Citizens `Navigator#setTarget(Location)` replaces current navigation; `cancelNavigation()` ends current target; `pathDistanceMargin` and `distanceMargin` have distinct contracts. `fallDistance(1)` fix is present and reduces one-block terrain rejection, but does not resolve this gate loop.
- Status: `CONTROLLED RUNTIME FAIL` for Alaric gate traversal; `PATHFINDER ENGINE PARTIAL PASS` (path present); `CROSSING NOT VERIFIED`; production unchanged.
- Decision: no blind margin/timeout increase, no teleport recovery, no live YAML edit. Next fix must add route-leg/generation/loaded-state diagnostics and a RED regression at callback boundary, then controlled Paper smoke.

### 2026-08-19 — Recovery local sau RCA gate

- Controlled evidence: Alaric đứng cố định tại `StillCliff:37.4996,-60.0000,-1.0750`; `feet=OAK_FENCE`, `head=AIR`, `support=GRASS_BLOCK`; gate plan chọn approach đầu `StillCliff:41.500,-60.000,-10.500`, cách `10.2388`; Citizens path vẫn `present` nhưng callback `PLUGIN` sau `160` ticks.
- RCA refinement: route retry hiện không có recovery khi leg hết retry; NPC bị kẹt tại block collision gần route và lặp `GOING_TO_PLOT -> INACTIVE`. Đây là navigation recovery gap, không phải A* path absent.
- Change: `GateRouteCoordinator.Navigation` thêm fail-closed local recovery hook. Sau `MAX_LEG_RESTARTS`, gọi `NavigationRecovery.recover` quanh vị trí hiện tại bán kính `2`, rồi restart cùng leg nếu recovery thành công. Không teleport qua gate/target.
- Test: thêm regression `timeoutSauMotLanRestartThuRecoveryLocalRoiStartLaiLeg`; `./gradlew.bat test --no-daemon --console=plain` — `BUILD SUCCESSFUL`; `git diff --check` pass.
- Controlled deploy/smoke: backup tạo tại `F:\\minecraftserver\\villagedefense2026\\backups\\LivingNPC_20260819_220440`; controlled server restart lúc `22:08`, plugin load `LivingNPC v0.6.0-rc.2`, startup `Done (41.761s)`. SHA-256 source JAR và live JAR trùng: `136caeb8f39f78e250fc7d6a8d07a555c0c01014a0c8fe274bd8cc6fa07080fc`.
- BotChecker run `bed2d7cb-ee78-45b6-b6a2-9fbe8c736e72` kết nối thành công và `settle_after_join` pass; scenario `npc-quest` fail timeout vì fixture còn placeholder `NPC_NAME_HERE`, target `0,64,0`, không phải gate fixture.
- Controlled log sau deploy: Alaric vẫn đứng `StillCliff:37.4996,-60,-1.075`, `feet=OAK_FENCE`, và fail trước gate tại `GOING_TO_BED_WAYPOINT reason=PLUGIN` sau `400` ticks. Gate recovery chưa được exercise.
- Follow-up fix: `WaypointRouteCoordinator` có local recovery một lần khi Citizens dừng trước waypoint; reset recovery counter khi sang leg; `FarmerRuntime` nối `NavigationRecovery` cho waypoint route. Không recovery vô hạn.
- Regression test `stopsOnceAndRestartsAfterLocalRecovery` ban đầu fail vì fixture fake giữ `navigating=true`, đã sửa fixture để mô phỏng callback stop; test pass.
- Verification sau fix: `./gradlew.bat clean test build --no-daemon --console=plain` — `BUILD SUCCESSFUL`; `git diff --check` pass. Live JAR SHA-256 `53bc3504796928fc6b87085a210c1b69aeafc196ebdd23bce5ecac4f7b2117e0` trùng build JAR.
- Route topology review: `WaypointRoutePlanner.plan()` chỉ nội suy đoạn thẳng giữa start/target; không đọc hàng rào. `GateRouteDiscovery` chỉ chọn gate đã cấu hình, nằm trong corridor `8` block quanh đoạn thẳng và có `exit` gần target hơn. Nếu tuyến đúng cần vòng ra ngoài corridor hoặc gate không nằm trên đoạn thẳng, planner tạo route trực tiếp kiểu đường đen; Citizens chỉ pathfind trên target đó. `GateRoutePlan` không tự khám phá topology fence, chỉ nối các candidate gate đã được discovery.
- Kết luận hình học: đường đen không phải Citizens tự chọn route đỏ sai; plugin đang cấp target/waypoint thiếu constraint gate. Fix đúng cần route graph/corridor qua `approach -> gate -> exit -> destination`, không tăng timeout hoặc recovery.
- Topology slice: `GateRouteDiscovery` không còn loại gate chỉ vì nằm ngoài corridor thẳng; gate configured còn được xét trong bán kính route tối đa, để hỗ trợ route đỏ vòng qua hàng rào. Khi farmer có gate config nhưng không tìm được candidate hợp lệ, `FarmerRuntime` fail-closed thay vì fallback direct waypoint. Thêm test `findsConfiguredGateOutsideDirectCorridorWhenItMovesTowardGoal`.
- Giới hạn slice: đây chưa phải obstacle graph tổng quát; chỉ gate configured là topology source. Cần controlled runtime chứng minh route thật qua `APPROACH -> EXIT -> FINAL`; chưa deploy bản này.
- Controlled deploy lần 2: backup `F:\\minecraftserver\\villagedefense2026\\backups\\LivingNPC_20260819_230100`; server stop/save pass; plugin load `LivingNPC v0.6.0-rc.2`; startup `Done (51.928s)`. Alaric chưa spawn sau cold start (`NPC_ACTIVITY ... spawned=false`), nên behavior smoke chưa có verdict. Status `ARTIFACT DEPLOYED CONTROLLED`, `RUNTIME SMOKE PENDING`.

## Baseline

- Repo: `E:\AI.WORK\living-npc-plugin`
- Target Paper: `1.21.11`
- Java: `21`
- Citizens: `2.0.42-SNAPSHOT`, build `4173`
- Plugin: `LivingNPC v0.6.0-rc.2`
- Production verification: chưa PASS.
- Không deploy/restart production nếu chưa build/test pass và chưa được user duyệt.

## 2026-08-18 — Research movement

### Kết luận Citizens/Paper

- Citizens Player NPC dùng `NPC#getNavigator()`. Paper `Mob#getPathfinder()` không áp dụng cho Player NPC.
- `Navigator#setTarget(Location)` là một navigation. Không gọi lặp mỗi tick; target mới thay navigation cũ và tạo `NavigationReplaceEvent`/reason `REPLACE`.
- `Navigator#setTarget(Iterable<Vector>)` chạy waypoint movement, không pathfinding.
- `getDefaultParameters()` và local navigation parameters có lifecycle riêng; route-specific settings phải áp dụng đúng navigation hiện tại.
- `distanceMargin` và `pathDistanceMargin` khác semantics.
- Citizens `fallDistance` là planner parameter; khác physics `Entity#setFallDistance`.
- Paper door flags chỉ dành cho `Mob` Pathfinder. Fence gate cần policy riêng qua Bukkit `Openable`, collision và clearance.
- `STUCK` phải phân biệt path absent, collision, gate đóng, chunk chưa load, fall/landing và protected state.

Nguồn:

- https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/Navigator.html
- https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/NavigatorParameters.html
- https://wiki.citizensnpcs.co/API
- https://docs.papermc.io/paper/dev/entity-pathfinder
- https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Entity.html
- https://github.com/CitizensDev/Citizens2/issues/1173
- https://github.com/CitizensDev/Citizens2/issues/979
- https://github.com/CitizensDev/Citizens2/issues/2353
- https://github.com/PaperMC/Paper/issues/12043
- https://github.com/PaperMC/Paper/issues/12335

Báo cáo chi tiết: `research-paper-citizens-pathfinding-vi.md`.

## 2026-08-18 — Thành công đã xác nhận

### Planner

- `WaypointRoutePlanner` chia route dài thành segment bounded.
- Đã thêm giới hạn vertical delta tối đa 1 block giữa các waypoint.
- Đã thêm regression test cho staircase/chênh cao.

### Gate coordinator

- `GateRouteCoordinatorTest` pass sau contract request/release.
- Gate release được gọi ở complete/fail/cancel.
- Default `requestGate` đã fail-closed, không mặc định `true`.
- `GatePassageService` có FIFO, Openable verification, explicit open và trace.
- Đã xử lý owner recheck sau FIFO promotion để tránh `DUPLICATE` làm route fail giả.

### Movement contract

- Tạo `MovementIntent` và `MovementService`.
- `FarmerRuntime` validate target trước Citizens.
- Có telemetry `NPC_MOVE_INTENT` và invalid-target failure.

### Build/test

Focused đã pass:

```text
MovementServiceTest
MovementIntentPolicyTest
WaypointRoutePlannerTest
GateRouteCoordinatorTest
DoorPassageCoordinatorTest
```

Full verification gần nhất:

```text
./gradlew.bat clean test build --no-daemon --console=plain
```

Kết quả: `BUILD SUCCESSFUL` trước thay đổi FIFO owner recheck; focused test sau thay đổi cũng `BUILD SUCCESSFUL`. Cần chạy lại full verification trước artifact/deploy tiếp theo.

## 2026-08-18 — Thất bại/RCA đã xác nhận

### `GOING_TO_BED_WAYPOINT reason=STUCK`, `deltaY=-2.5000`

RCA: một số call path tạo waypoint/direct target ngoài `WaypointRoutePlanner`; planner chưa là movement authority toàn hệ thống.

Status: FIX một phần. Planner vertical bound đã có test, nhưng toàn bộ runtime chưa gom về pipeline.

### `GOING_TO_PLOT_GATE reason=REPLACE`, `path=absent`

RCA: nhiều runtime tự sở hữu Citizens Navigator; gọi target mới trong navigation cũ thay route. Gate/waypoint/recovery chỉ phủ một phần Farmer path.

Status: BLOCKED cho CORE PASS. Cần gom call path.

### Gate thiếu trace/open/cross/exit

RCA: route dừng trước approach nhưng không luôn gọi gate request; một nhánh Rancher còn thiếu `gateNavigator.setTarget(legTarget)`. FIFO owner promotion cũng từng khiến owner cũ bị xử lý như duplicate.

Status: đã sửa code cục bộ, chưa controlled smoke PASS.

### Inventory/storage

Đã có model 5 slot × tối đa 5 món, threshold 4 slot và marker storage. Chưa có runtime evidence đủ để kết luận harvest → storage → deposit → return PASS.

Status: NOT VERIFIED; tạm dừng ưu tiên cho tới CORE movement PASS.

## 2026-08-18 — Runtime integration Fisher slice

- Scope: `FisherRuntime.startNavigation`.
- Change: dùng `MovementService.startSimpleNavigation`; không set lại target nếu Citizens đang chạy cùng target.
- First attempt: `FisherRuntimeLifecycleTest` FAIL do helper reject target mock có world null.
- RCA: preflight world check quá chặt; caller đã chịu trách nhiệm world policy.
- Fix: giữ finite coordinate/margin validation, bỏ reject world null.
- Focused result: `FisherRuntimeLifecycleTest`, `MovementServiceTest` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` cho slice unit; `NOT VERIFIED` runtime.

## 2026-08-18 — Runtime integration CivilProfession slice

- Scope: `CivilProfessionRuntime.navigate`.
- Change: route qua `MovementService.startSimpleNavigation`; state update chỉ sau helper start thành công.
- Focused result: `MinerSafetyTest`, `MinerCandidateSelectionTest` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` unit slice; `NOT VERIFIED` runtime.

## 2026-08-18 — Runtime integration Merchant slice

- Scope: `MerchantRuntime.navigate`.
- Change: dùng `MovementService.startSimpleNavigation`; state update chỉ sau helper thành công.
- Full test: `./gradlew.bat test --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` unit regression; `NOT VERIFIED` runtime.

## 2026-08-18 — Runtime integration Visitor slice

- Scope: `VisitorRuntime.navigate` và formation member navigation.
- Change: dùng `MovementService.startSimpleNavigation`; giữ state update sau helper.
- Full test: `./gradlew.bat test --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Caveat: formation target thay đổi theo leader; cần cadence test riêng.
- Status: `PASS` unit regression; `NOT VERIFIED` runtime.

## 2026-08-18 — Runtime integration Merchant/Visitor + full build

- Merchant và Visitor simple navigation đã route qua `MovementService`.
- Direct targets còn lại đều thuộc special authority: Farmer/Rancher route callbacks, DoubleDoor passage, Combat preemption, diagnostics.
- Full verification: `./gradlew.bat clean test build --no-daemon --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`; chưa deploy.
- Status: `PASS` source/test slice; CORE runtime `NOT VERIFIED`.

## 2026-08-18 — Runtime integration Combat slice

- Scope: `CombatManager.navigate`.
- Change: dùng `MovementService.startSimpleNavigation`; giữ combat preemption ngoài helper.
- Full test: `./gradlew.bat test --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- DoubleDoor giữ nguyên vì là state machine đặc biệt, đã có cleanup tests.
- Status: `PASS` unit slice; `NOT VERIFIED` runtime.

## 2026-08-18 — DoubleDoor review

- Reviewed `DoubleDoorListener` phase transitions and cleanup.
- Focused tests: `DoubleDoorListenerTest`, `DoorPassageCoordinatorTest` — `BUILD SUCCESSFUL`.
- Source state machine handles approach/open wait/cross/exit/restore/release.
- No code change in this slice; changing it would risk existing cleanup contract.
- Production gate smoke: `NOT VERIFIED`.

## 2026-08-18 — Diagnostics target ownership fix

- Symptom: `NavigationDiagnostics.activeParametersAfterTarget` gọi `setTarget` ẩn; caller route cũng set target.
- RCA: side effect duplicate có thể tạo `REPLACE`.
- Fix: diagnostics chỉ cấu hình local parameters; Farmer/Rancher caller set target một lần.
- Regression: test cũ fail đúng contract cũ; cập nhật test ownership.
- Focused + full build: `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.

## 2026-08-18 — NavigationRecovery review

- World/chunk/standing validation và intent-retain contract đã có test.
- Không thêm code trong slice review.
- Production movement recovery: `NOT VERIFIED`.

## 2026-08-18 — Arrival margin contract

- `FarmerRuntime.navigationTargetReached` reject negative/non-finite margin.
- Regression `FarmerNavigationPolicyTest` + `NavigationRecoveryTest` pass.
- Full build pass.
- Runtime: `NOT VERIFIED`.

## 2026-08-18 — Waypoint vertical continuity

- Planner snap now rejects waypoint candidate Y delta above 1 block from previous actual waypoint.
- Regression `WaypointRoutePlannerTest.snapCannotIntroduceMoreThanOneBlockVerticalChange` pass.
- Full build pass.
- Runtime/chunk boundary: `NOT VERIFIED`.

## 2026-08-18 — Waypoint chunk preflight

- Added bounded `RuntimeChunkAvailability.loadedRoute`.
- Farmer waypoint route fails closed when any waypoint chunk unavailable.
- Regression + full build pass.
- No force-load/ticket in hot path.
- Cross-chunk Paper runtime: `NOT VERIFIED`.

## 2026-08-18 — Gate passage lifecycle hardening

- Symptom/risk: `GatePassageService.active` là static reference, không bị clear khi runtime stop; sau disable/reload có thể route gọi nhầm coordinator cũ hoặc giữ state FIFO.
- RED: `GatePassageServiceTest` không compile trước khi có lifecycle API (`isActive`, `shutdown`, `resume`).
- Fix: giữ service instance trong `LivingNpcPlugin`; shutdown gọi `DoorPassageCoordinator.shutdown()` rồi clear static reference; resume đăng ký lại reference; thêm cleanup/resume vào `RuntimeStopCoordinator`.
- Focused: `./gradlew.bat test --tests vn.heomc.livingnpc.GatePassageServiceTest --console=plain` — GREEN.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — Rancher gate leg duplicate target

- Symptom/risk: `RancherRuntime` gọi `gateNavigator.setTarget(legTarget)` hai lần trong cùng `Navigation.start`; Citizens có thể replace navigation lần đầu bằng lần hai, reset path/callback và góp phần làm NPC đứng yên.
- RED: `RancherNavigationArchitectureTest.gateLegSetsTargetOncePerNavigationStart` fail vì đếm 2 lời gọi.
- Fix: xóa lời gọi `setTarget` thứ hai; mỗi gate leg chỉ tạo một navigation.
- Focused: `./gradlew.bat test --tests vn.heomc.livingnpc.RancherNavigationArchitectureTest --tests vn.heomc.livingnpc.GateRouteCoordinatorTest --console=plain` — GREEN.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — Rancher ordinary navigation missing target

- Symptom: Rancher paths without configured gate reached `startNavigation`, but method only validated/attached diagnostics and never called `navigator.setTarget(target)`.
- Impact: `RANCH_ENTER`/ordinary Rancher navigation could report started while Citizens had no new target; NPC remained still.
- RED: `RancherNavigationArchitectureTest.ordinaryRancherNavigationSetsTargetInsideStartNavigation` failed; expected one target call, found zero.
- Fix: call `navigator.setTarget(target)` once after range validation and before active-parameter/diagnostic attachment.
- Focused: `./gradlew.bat test --tests vn.heomc.livingnpc.RancherNavigationArchitectureTest --tests vn.heomc.livingnpc.GateRouteCoordinatorTest --console=plain` — GREEN.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — Audit Fisher/CivilProfession navigation

- Fisher `startNavigation` đi qua `MovementService.startSimpleNavigation`; regression hiện hữu xác nhận `setTarget` được gọi trước cấu hình local parameters và test focused pass.
- CivilProfession `navigate` cũng đi qua `MovementService`; không phát hiện path attach diagnostics mà thiếu target như Rancher.
- Không thêm code trong slice này.
- Focused: `./gradlew.bat test --tests vn.heomc.livingnpc.FisherRuntimeLifecycleTest --tests vn.heomc.livingnpc.MovementServiceTest --tests vn.heomc.livingnpc.FarmerNavigationPolicyTest --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`.

## 2026-08-18 — Merchant sleep navigation cleanup

- Symptom: `MerchantRuntime.releaseForSleep()` only changed phase/open state; active Citizens navigation remained running.
- Impact: Merchant could keep moving after sleep/release lifecycle transition.
- RED: `MerchantRuntimeTest.releaseForSleepCancelsActiveNavigation` failed because `cancelNavigation()` was not called.
- Fix: cancel active navigator when NPC is spawned before setting `INACTIVE`.
- Focused: Merchant/Fisher/Movement tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — DoubleDoorListener shutdown cleanup

- Symptom: shutdown canceled passage tasks, closed doors, and released leases, but did not run navigator cleanup.
- Impact: NPC could keep passage navigation after runtime stop; passage-specific parameters could remain active.
- RED: `DoubleDoorListenerShutdownArchitectureTest` failed because shutdown did not call `terminatePassageCleanup`.
- Fix: shutdown resolves NPC and runs passage cleanup before lease release; missing NPC still closes doors safely.
- Existing null-target and world-abort tests initially exposed cleanup expectation; preserved cancel behavior for non-restorable target.
- Focused: `DoubleDoorListenerShutdownArchitectureTest` + `DoubleDoorListenerTest` — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — Fence gate failed-open FIFO cleanup

- Symptom: `GatePassageService.open()` returned `false` for non-`Openable` block but kept coordinator owner.
- Impact: next NPC stayed queued behind orphaned owner until external cleanup.
- RED: `GatePassageServiceArchitectureTest` failed because non-openable branch lacked `coordinator.release(...)`.
- Fix: release owner with `GATE_NOT_OPENABLE` before returning `false`.
- Focused: gate passage/coordinator tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — GateRouteListener monitor task lifecycle

- Symptom: mỗi gate event tạo `BukkitRunnable` monitor nhưng listener không giữ task handle và không chặn event sau runtime stop.
- Impact: monitor task có thể chạy sau stop; gate FIFO release/mutation callback tiếp tục tồn tại ngoài lifecycle runtime.
- Fix: thêm `OwnedTaskRegistry`, `shutdown/resume`, runtime-stop guard, giữ task handle và wiring vào `LivingNpcPlugin`.
- Focused: GateRoute listener/passage/door tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — GateRouteListener completed-task removal

- Symptom: monitor task được cancel khi gate đóng/timeout nhưng vẫn giữ trong `OwnedTaskRegistry` tới runtime stop.
- Fix: giữ task holder và remove task ngay khi monitor kết thúc tự nhiên; shutdown vẫn cancel task đang chạy.
- Focused: `GateRouteListenerArchitectureTest` + gate passage tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — LivingDoorExaminer late close callback guard

- Symptom: `DoorOpener` callback có thể chạy sau runtime stop và tạo `CloseTask` mới qua constructor lambda trực tiếp.
- Impact: task đóng cửa mới xuất hiện sau khi shutdown đã dọn registry.
- RED: `LivingDoorExaminerLifecycleArchitectureTest` fail vì `scheduleManagedClose` thiếu guard `accepting`.
- Fix: `scheduleManagedClose` fail-closed khi stopped; constructor dùng method reference này thay vì tạo `CloseTask` trực tiếp.
- Focused: `LivingDoorExaminerLifecycleArchitectureTest`, `LivingDoorExaminerTest`, `LivingNavigationTest` — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — GateRouteListener fail-closed after runtime stop

- Symptom: stopped `GateRouteListener` returned before canceling `NPCOpenGateEvent`; Citizens could still open managed fence gate after runtime stop.
- RED: `GateRouteListenerLifecycleArchitectureTest` failed because stopped guard lacked `event.setCancelled(true)`.
- Fix: cancel event when listener is not accepting; preserve normal manager-null behavior.
- Focused: gate listener lifecycle/architecture + `LivingDoorExaminerTest` — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — LivingDoorExaminer stale open callback guard

- Symptom: stale Citizens callback could reach `openIfStillValid`/`openAfterAuthorization` after runtime stop.
- Impact: callback could fire door/gate event or mutate block open state after examiner shutdown.
- RED: lifecycle architecture test failed because `openAfterAuthorization` lacked `!accepting` guard.
- Fix: both open paths fail closed before event dispatch or block mutation when runtime stopped; null block also rejected safely.
- Focused: lifecycle, examiner, door passage tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — LivingNavigation examiner dedup audit

- `allowDoors()` loại toàn bộ Citizens `DoorExaminer`, giữ tối đa một `LivingDoorExaminer`, giữ tối đa một `VillageRouteExaminer`, rồi ép `PathfinderType.CITIZENS`.
- `enterBuildings()` chỉ delegate `allowDoors()`; không tạo examiner thứ hai.
- Các call site Farmer/Rancher/DoubleDoor đều dùng entry point này; không phát hiện duplicate examiner hoặc pathfinder replacement ngoài chủ ý.
- Regression: `LivingNavigationTest`, `FisherRuntimeLifecycleTest`, `FarmerNavigationPolicyTest` — `BUILD SUCCESSFUL`.
- Không sửa code trong slice này.
- Runtime Paper: `NOT VERIFIED`.

## 2026-08-18 — MovementService same-target parameter refresh

- Research: Citizens local parameters là bản copy tạo khi `setTarget`; config local vẫn cần chỉnh sau target.
- Bug: same-target shortcut trả `true` trước khi reapply speed/margin/path parameters; phase/config reload có thể giữ config cũ.
- RED: `MovementServiceTest.reappliesLocalParametersWhenSameTargetKeepsCurrentNavigation` fail.
- Fix: target mới chỉ gọi `setTarget` một lần; same target bỏ `setTarget` nhưng vẫn reapply toàn bộ local parameters.
- Focused: MovementService/LivingNavigation/Fisher lifecycle — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — MovementService speed input validation

- Symptom: `startSimpleNavigation` validated target/margin nhưng nhận `NaN`, vô hạn hoặc speed `<= 0`.
- Impact: Citizens có thể nhận movement parameters không hợp lệ; config lỗi không fail-closed tại movement entry point.
- RED: `MovementServiceInputTest` fail khi speed `NaN`.
- Fix: reject non-finite và non-positive `speedModifier` trước `setTarget`/parameter mutation.
- Focused: MovementService/LivingNavigation tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — Rancher gate-leg active parameter order

- Audit: direct `setTarget` paths còn lại gồm DoubleDoor lifecycle và Rancher waypoint/gate ownership; không thay đổi vì có authority riêng.
- Bug: Rancher gate leg cấu hình `getLocalParameters()` trước `setTarget`; Citizens tạo local parameter copy khi target mới, nên gate leg có thể dùng config cũ.
- RED: `RancherNavigationArchitectureTest.gateLegAppliesActiveParametersAfterTargetCreation` fail trước fix.
- Fix: sau `gateNavigator.setTarget(legTarget)`, lấy active parameters rồi áp dụng `LivingNavigation`, speed, margin, path margin, teleport margin và stuck policy.
- Focused: Rancher/gate/farmer navigation tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — Farmer waypoint-leg active parameter order

- Bug: `startWaypointRoute()` gọi `navigator.setTarget(legTarget)` rồi chỉ attach diagnostics; không cấu hình active local parameters sau target.
- Impact: waypoint leg có thể dùng parameters cũ sau local target copy của Citizens.
- RED: `RancherNavigationArchitectureTest.waypointLegAppliesActiveParametersAfterTargetCreation` fail.
- Fix: lấy `activeParametersAfterTarget`, rồi áp dụng examiner, speed, margins, teleport margin và stuck policy trước attach diagnostics.
- Focused: Rancher/Farmer/gate tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — Farmer ordinary navigation active parameter order

- Bug: `FarmerRuntime.navigate()` cấu hình parameters trên local state trước khi các route branches tạo target; ordinary path sau `setTarget` chỉ attach diagnostics.
- Impact: active target có thể dùng examiner/speed/stuck policy cũ hoặc default.
- RED: `FarmerNavigationActiveParametersTest` fail trước fix.
- Fix: sau `setTarget`, lấy active parameters rồi apply speed multiplier, examiner, margins, teleport margin và stuck policy trước diagnostics.
- Focused: Farmer/Rancher navigation tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-18 — DoubleDoorListener target transition audit

- Passage start: `setTarget` rồi `configurePassage` trên active local parameters.
- Center transition: `setTarget(sides.after())` rồi `configurePassage`; crossing transition reapply passage parameters.
- Restore: original target được set trước `restoreParameters`; failure path cancel navigation và release lease.
- Không phát hiện duplicate target hoặc config-order bug trong DoubleDoor authority.
- Regression: `DoubleDoorListenerTest`, `DoubleDoorListenerShutdownArchitectureTest`, `NavigationRecoveryTest` — `BUILD SUCCESSFUL`.
- Không sửa code trong slice này.
- Runtime Paper: `NOT VERIFIED`.

## 2026-08-18 — GateRouteCoordinator timeout and cleanup audit

- Timeout cancel navigation, release gate qua candidate rejection, rồi chuyển candidate tiếp theo hoặc clear coordinator.
- Complete cancel, release gate, chuyển gate tiếp theo hoặc clear plan.
- Cancel cancel navigation, release gate, clear plan/route.
- FarmerRuntime clear coordinator sau mọi result khác `IN_PROGRESS`; suspend/preempt/failure cũng cancel và null coordinator.
- Regression: `GateRouteCoordinatorTest`, `FarmerNavigationPolicyTest`, `NavigationRecoveryTest` — `BUILD SUCCESSFUL`.
- Không phát hiện lease leak hoặc coordinator stale trong source hiện tại.
- Không sửa code trong slice này.
- Runtime Paper: `NOT VERIFIED`.

## 2026-08-18 — WaypointRouteCoordinator failure cleanup

- Bug: timeout hoặc `navigator` dừng trả `FAILED` nhưng không gọi `navigation.cancel()`.
- Impact: `FarmerRuntime` null coordinator sau failure, navigator có thể tiếp tục chạy target cũ.
- RED: `WaypointRouteCoordinatorTest.timeoutCancelsActiveWaypointNavigationBeforeFailure` fail.
- Fix: failure path cancel navigation trước khi trả `FAILED`; áp dụng cho timeout và navigator stopped.
- Focused: Waypoint/coordinator/planner/Farmer tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime Paper: `NOT VERIFIED`.

## 2026-08-19 — Research và cleanup failed start của WaypointRouteCoordinator

- Research source: Citizens `Navigator` chỉ giữ một target; `cancelNavigation()` hủy navigation đang chạy. Citizens API 2.0.43-SNAPSHOT Javadoc: https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/Navigator.html.
- Research contract: Citizens Wiki ghi local parameters là bản copy tạo khi `setTarget`; source: https://wiki.citizensnpcs.co/API.
- Verified local root cause: `WaypointRouteCoordinator.start()` và leg transition trả `FAILED` khi `navigation.start()` fail nhưng không cleanup navigation cũ.
- RED: `WaypointRouteCoordinatorTest.failedStartCancelsAnyStaleNavigationBeforeFailure` fail trước fix.
- Fix: failed initial start và failed leg transition gọi `navigation.cancel()` trước `FAILED`; timeout/stopped path đã có cùng cleanup.
- Focused và full build: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Evidence level: local source/test `Verified`; Citizens exact `2.0.42-SNAPSHOT` runtime `Unknown`; Paper runtime `NOT VERIFIED`.

## 2026-08-19 — WaypointRouteCoordinator input contract research

- Đối chiếu `WaypointRouteCoordinator` với `GateRouteCoordinator`: constructor waypoint trước đây không reject `null navigation`, `null waypoints`, margin âm/non-finite hoặc timeout `<= 0`.
- Đây là boundary input rõ từ source local; không cần suy luận Citizens runtime.
- RED: `WaypointRouteCoordinatorTest.rejectsInvalidCoordinatorConfiguration` fail trước fix.
- Fix: fail-closed bằng `IllegalArgumentException`; giữ `List.copyOf` để reject waypoint null.
- Focused và full build: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Evidence level: local source/test `Verified`; Citizens exact `2.0.42-SNAPSHOT` runtime `Unknown`; Paper runtime `NOT VERIFIED`.

## 2026-08-19 — Waypoint deadline overflow research

- Đối chiếu `tick - startedTick` với `GateRouteCoordinator` và Java `long` arithmetic: phép trừ elapsed tick giữ đúng trong cửa sổ timeout nhỏ, kể cả qua `Long.MAX_VALUE` wrap khi tick tăng đơn điệu.
- Không sửa production; thêm regression `WaypointRouteCoordinatorTest.deadlineNearLongMaxDoesNotTimeoutEarly`.
- Focused và full build: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Evidence: arithmetic/local test `Verified`; Paper/Citizens runtime `NOT VERIFIED`.

## 2026-08-19 — WaypointRoutePlanner finite Location research

- Paper Javadoc xác nhận `Location.isFinite()` kiểm tra mọi component hữu hạn: https://jd.papermc.io/paper/org/bukkit/Location.html.
- Root cause local: planner chỉ kiểm tra world/segment; `NaN` hoặc vô hạn có thể đi vào distance/interpolation và tạo route invalid.
- RED: `WaypointRoutePlannerTest.rejectsNonFiniteLocations` fail trước fix.
- Fix: reject `!start.isFinite()` hoặc `!target.isFinite()` trước tính toán.
- Focused và full build: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Evidence: local source/test `Verified`; Paper/Citizens runtime exact `NOT VERIFIED`.

## 2026-08-19 — Research Citizens readiness exact 2.0.42-SNAPSHOT

- Build dùng exact `net.citizensnpcs:citizens-main:2.0.42-SNAPSHOT`; JAR thực tế có trong Gradle cache.
- Bytecode exact JAR xác nhận `CitizensAPI.setImplementation()` chạy đầu `Citizens.onEnable()`, NPC registry được tạo trong cùng lifecycle trước khi plugin hoàn tất enable.
- Exact JAR có `CitizensEnableEvent`; không có `CitizensLoadEvent`. Wiki hiện tại nhắc cả hai, nhưng không khớp artifact target.
- `plugin.yml` LivingNPC có `depend: [Citizens]`, nên Citizens enable trước LivingNPC theo Paper plugin dependency ordering.
- Quyết định: không thêm deferred initialization dựa trên event không có trong exact artifact; source đã fail-closed khi registry null và có `CitizensRegistryReadinessTest`.
- Evidence: exact artifact/bytecode + local tests `Verified` cho contract hiện tại; cold-start/restart Paper `NOT VERIFIED`.

## 2026-08-19 — Invalid config startup fail-closed

- Research Paper lifecycle: `onEnable()` khởi tạo resource; `onDisable()` chịu cleanup. Source: https://docs.papermc.io/paper/dev/how-do-plugins-work.
- Root cause local: `LivingNpcPlugin.onEnable()` return khi config `INVALID`/`UNSUPPORTED`, nhưng không disable plugin; server có thể giữ plugin enabled với state nửa khởi tạo.
- RED: `LivingNpcPluginStartupArchitectureTest.invalidConfigDisablesPluginInsteadOfReturningEnabledWithPartialState` fail trước fix.
- Fix: gọi `getServer().getPluginManager().disablePlugin(this)` trước return.
- Focused: startup/config/runtime-stop tests — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Evidence: source/test `Verified`; Paper lifecycle runtime exact `NOT VERIFIED`.

## 2026-08-19 — Research scheduler interval boundary

- `LivingNpcConfig.load()` chỉ clamp `tick-interval` tối thiểu `10L`; không có upper bound.
- Paper `BukkitScheduler.runTaskTimer(Plugin, Runnable, long, long)` nhận `long`; Javadoc không công bố upper bound ngoài API type.
- Chưa có product/config contract xác định max interval và chưa có Paper reproduction chứng minh `Long.MAX_VALUE` gây lỗi trên target `1.21.11`.
- Quyết định: không thêm upper clamp hoặc đổi arithmetic từ suy đoán; cần contract hoặc controlled Paper reproduction trước.
- Evidence: API/source review `Verified`; runtime behavior với giá trị cực đại `Unknown`; Paper runtime `NOT VERIFIED`.

## 2026-08-19 — Telemetry exporter rejected executor cleanup

- Research Paper scheduler: async task chạy thread riêng; task handle có thể cancel. Source: https://docs.papermc.io/paper/dev/scheduler.
- Root cause local: `NpcTelemetryExporter.exportSnapshot()` set `writeQueued=true` trước `executor.execute(...)`; executor có thể reject và ném `RuntimeException`, nhưng cờ không reset, exporter kẹt vĩnh viễn.
- RED: `NpcTelemetryExportTest.rejectedExecutorDoesNotStallFutureExports` fail với `RejectedExecutionException`.
- Fix: catch `RuntimeException`, reset `writeQueued`, lưu status lỗi, log warning, trả `false`.
- Focused và full `./gradlew.bat clean test build --console=plain`: `BUILD SUCCESSFUL`.
- Evidence: source/test `Verified`; Paper async scheduler runtime `NOT VERIFIED`.

## 2026-08-19 — Telemetry cancellation write guard

- Research Paper scheduler: async task chạy thread riêng; cancel không chứng minh callback đã dừng ngay. Source: https://docs.papermc.io/paper/dev/scheduler.
- Root cause local: `cancel()` chỉ set cờ; `writeSnapshot()` public/package-private không kiểm tra cờ. Callback stale hoặc caller sau cancel vẫn ghi file, có thể ghi đè exporter mới cùng path sau reload.
- RED: `NpcTelemetryExportTest.cancelledExporterDoesNotWriteSnapshot` fail trước fix.
- Fix: `writeSnapshot()` return trước khi ghi nếu exporter đã cancelled.
- Focused và full `./gradlew.bat clean test build --console=plain`: `BUILD SUCCESSFUL`.
- Evidence: source/test `Verified`; async race trên Paper runtime exact `NOT VERIFIED`.

## 2026-08-19 — Research concurrent telemetry exporter ownership

- Production path chỉ tạo một `NpcTelemetryExporter`; `startTelemetryExport()` cancel exporter cũ trước khi tạo exporter mới.
- Hai exporter cùng path chỉ xuất hiện trong unit test mô phỏng; snapshot contract xác định replace-only, không xác định generation ordering giữa writer cũ/mới.
- `cancel()` không thể dừng callback đã qua cancellation check và đang giữa filesystem write; chặn tuyệt đối cần generation/lock protocol mới.
- Quyết định: không thêm protocol theo suy đoán. Guard trước write đã có; race còn lại `Unknown`, cần controlled Paper/filesystem reproduction và product contract trước khi sửa.
- Evidence: source/test contract `Verified`; concurrent async ordering trên Paper/filesystem target `Unknown`; Paper runtime `NOT VERIFIED`.

## 2026-08-19 — Research WorldMutationPolicy reload contract

- WorldGuard docs xác nhận Region API thread-safe, nhưng dependency availability và policy flag vẫn là state runtime cần lifecycle rõ ràng. Source: https://worldguard.enginehub.org/en/latest/developer/regions.
- `WorldMutationPolicy` snapshot `worldGuardAvailable` và `requireWorldGuard` tại constructor.
- `reloadPluginConfig()` không thay policy; gọi `restartRequiredReasons(...)` và báo `protection.require-worldguard` hoặc `WorldGuard availability` cần restart.
- Đây là contract restart-required đã triển khai, không phải silent stale config.
- Quyết định: không hot-replace policy; chưa có evidence dependency reload an toàn trên Paper/WorldGuard `7.0.16`.
- Evidence: source contract `Verified`; controlled WorldGuard reload runtime `NOT VERIFIED`.

## 2026-08-19 — Research VillagePathCache invalidation

- `VillagePathCache.states` tạo state theo village ID đang có trong `VillageStore.villages()`; mỗi state giữ bounded `paths` theo `maxCachedPaths`.
- `VillageStore` hiện không có API xóa village hoặc đổi center; `/lnpc reload` cũng không reload `VillageStore`.
- Config reload thay scan settings; scan kế tiếp rebuild `pending/paths` theo center hiện tại của store.
- Không có source/test evidence cho village deletion, center mutation, hoặc world change runtime. Risk R-008 chưa phải defect confirmed.
- Quyết định: không thêm `clear()`/invalidation theo suy đoán; cần contract lifecycle village và controlled test trước.
- Evidence: current source `Verified`; deletion/center-change behavior `Unknown`; Paper runtime `NOT VERIFIED`.

## 2026-08-19 — Profession reset contract

- User contract: khi NPC đổi nghề, chỉ giữ cấu hình giường/nhà (`FarmerDefinition.home`); mọi cấu hình nghề và runtime assignment khác phải reset.
- Research source: `FarmerDefinition`, `FarmerManager.selectJob`, `FarmerStore`, `FisherRuntime`, `RancherRuntime`, `CivilProfessionRuntime`, `FarmerRuntime`, `ResidentGui`.
- Trước fix, `withActiveRole()` giữ `villageId`, `plot`, `plotRadius`, profile/roles, progress, schedules và behaviors; đây là stale-state bug.
- Work zones fishing/ranch/mining nằm trong `VillageStore`, là cấu hình dùng chung của làng; không xóa khi đổi nghề NPC.
- Reset baseline là ordinary resident: giữ `npcUuid` + `home`; đặt `villageId=null`, `plot=null`, `plotRadius=4`, `ResidentProfile.adopted(name)`, `activeRole=RESIDENT`, progress RESIDENT=0, schedules rỗng, `BehaviorFlag.safeDefaults()`.
- Chọn nghề mới chạy transaction reset baseline trước, sau đó thêm role mới sạch với progress 0; không tự gán village/work zone/schedule. Admin phải cấu hình lại assignment sau đổi nghề.
- `FarmerRuntime.updateDefinition()` suspend trước nhận definition mới; Fisher/Rancher/Civil runtime cũng release khi role không còn sở hữu. Khi đến giờ nghỉ, shared sleep runtime dùng `home` để đưa NPC về ngủ.
- Regression RED/GREEN: `ResidentProfileTest.resettingProfessionKeepsOnlyHomeAndReturnsToOrdinaryResident`, `assigningNewProfessionStartsFromOrdinaryResidentDefaults`.
- Verification: focused role tests pass; `./gradlew.bat clean test build --console=plain` `BUILD SUCCESSFUL`.
- Runtime Paper/Citizens/restart: `NOT VERIFIED`; chưa deploy/restart.

## 2026-08-19 — RCA gate closed và time-transition recovery

- Runtime evidence: sau `/time set 1500`, Alaric chuyển `GOING_TO_BED -> GOING_TO_PLOT`; route gate tiếp tục chạy. Trước đó gate `StillCliff:49:-60:-17` lặp `FIFO_OWNER` + `EVENT_CANCELLED` rồi navigator dừng.
- RCA gate: `GatePassageService.open()` tự phát `NPCOpenGateEvent`; `GateRouteListener` bắt lại event và gọi coordinator lần hai. Coordinator trả `WAITING`, listener cancel event, nên gate không mở. Đây là re-entry bug.
- Fix: bỏ synthetic `NPCOpenGateEvent` trong `GatePassageService`; route đã được `GateRouteDiscovery` validate/configure, sau đó gọi `LivingDoorExaminer.openAfterAuthorization()` trực tiếp. Event listener chỉ xử lý callback Citizens tự phát.
- RCA time: `/time set` làm phase đổi ngay qua `work-end-tick=12000` và bedtime; đó là trigger. Bug khiến NPC kẹt là gate re-entry + navigator fail giữ phase, không phải thời gian đơn độc.
- Test: `./gradlew.bat test --tests '*GatePassageServiceTest' --console=plain` pass; focused Gate/Navigation/Farmer pass; `git diff --check` pass; `./gradlew.bat clean test build --console=plain` `BUILD SUCCESSFUL`.
- Runtime candidate sau fix: `NOT VERIFIED`; chưa deploy artifact mới. Cần controlled Paper smoke gate đóng, `/time set` qua `12000` và `23000->0`, không hot-reload.

## 2026-08-19 — Gom movement entry point cho leg thường/gate

- Symptom: Farmer/Rancher vẫn có các callback `WaypointRouteCoordinator`, `GateRouteCoordinator` và ordinary navigation gọi `Navigator.setTarget(...)` trực tiếp. Các call path này bỏ qua validation finite target, cấu hình `allowDoors`, `pathDistanceMargin`, `destinationTeleportMargin` và `stuckAction` tập trung trong `MovementService`.
- Root cause: movement authority chưa bao phủ toàn bộ leg callback; source có wrapper nhưng special-route callback còn bypass wrapper.
- Fix: Farmer ordinary/waypoint/gate legs và Rancher ordinary/gate legs gọi `MovementService.startSimpleNavigation(...)`; giữ `NavigationDiagnostics.activeParametersAfterTarget(...)` để bám active Citizens parameter clone. Không sửa `DoubleDoorListener`, vì passage cần điều khiển target riêng trong state machine.
- Regression: cập nhật `RancherNavigationArchitectureTest` và `FarmerNavigationActiveParametersTest` để khóa movement entry point mới và thứ tự attach diagnostics sau navigation start.
- Focused test lần đầu fail 4 architecture assertions do test còn tìm `setTarget` cũ; sửa test theo contract mới. Lần hai còn 1 assertion Farmer tương tự; sửa xong.
- Full verification: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`, 6 tasks, 470 tests đã chạy ở test phase, không failure/error.
- `git diff --check`: pass. Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — GateRoute final target fail-closed validation

- Audit phát hiện `GateRoute.Candidate` đã validate `approach/exit`, nhưng `GateRoute` constructor chưa validate `finalTarget` khác world hoặc tọa độ non-finite.
- Root cause: route plan có thể nhận final target sai, giữ navigation tới timeout thay vì reject tại boundary.
- Fix: reject `finalTarget` khác world với gate hoặc có tọa độ không hữu hạn; `reached(...)` cũng fail-closed với margin/vertical tolerance không hợp lệ.
- Regression: `GateRouteTest.finalTargetKhacWorldHoacKhongHuuHanBiTuChoiSom`.
- Focused: `./gradlew.bat test --tests '*GateRouteTest' --tests '*GateRouteCoordinatorTest' --tests '*FarmerNavigationPolicyTest' --console=plain` — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — RCA NavigationRecovery không phải arrival

- Phát hiện: `NavigationRecovery.recover(...)` teleport NPC tới safe standing gần target. Kết quả `RECOVERED` không chứng minh NPC đã tới target, interaction range, block clearance mục tiêu hoặc điều kiện phase.
- Call path sai: CivilProfession chuyển sang production; Farmer trả `ARRIVED`; Fisher chuyển casting; Rancher trả success interaction; Visitor chuyển shopping; Merchant mở stall.
- Fix: mọi caller giữ fail/retry/backoff hoặc phase inactive; chỉ success khi arrival check riêng xác nhận target. Không coi teleport recovery là completion.
- Regression: `NavigationRecoveryArrivalArchitectureTest` khóa các success branch cũ không quay lại.
- Focused: recovery/sleep/fisher tests và architecture test pass.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — Gate lifecycle audit sau recovery RCA

- Gate coordinator unit coverage đã có timeout, candidate fallback, effective margin, same-tick exit confirmation, cancel và release path.
- `GatePassageService` giữ owner khi `ALREADY_OPEN`; release vẫn thuộc route complete/fail/cancel.
- Chưa có source evidence đủ để kết luận geometry chọn đúng gate trong Paper world hoặc FIFO 3 NPC ngoài unit fixture.
- Quyết định: không sửa gate state machine ở slice này; ưu tiên controlled Paper smoke sau artifact sạch.
- Full build sau recovery fix: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Runtime gate/chunk/Citizens: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — Arrival predicate audit: CivilProfession/Merchant

- Audit phát hiện hai call path còn dùng `distanceSquared` tới tọa độ tâm target để kết luận arrival.
- Root cause: Citizens `VectorGoal` có thể hoàn tất theo block-goal, trong khi khoảng cách tới tâm block lớn hơn margin; cùng coordinate-system defect đã được chứng minh ở GateRoute runtime.
- Fix: CivilProfession và Merchant dùng chung `FarmerRuntime.navigationTargetReached(...)`, giữ block-goal + vertical tolerance semantics.
- Regression: `ArrivalPredicateArchitectureTest` khóa hai call path dùng shared predicate.
- Focused: `./gradlew.bat test --tests '*MerchantRuntimeTest' --tests '*MovementServiceTest' --tests '*FarmerNavigationPolicyTest' --console=plain` — `BUILD SUCCESSFUL`; architecture test pass.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — Arrival predicate audit: rest/patrol

- Audit tiếp tục phát hiện `FarmerRuntime.restAtHome` và patrol của `CivilProfessionRuntime` dùng khoảng cách tới target center để kết thúc leg.
- Root cause cùng coordinate mismatch Citizens block-goal vs block center; có thể tạo false negative sau Citizens completion hoặc đổi phase không đồng nhất.
- Fix: dùng `navigationTargetReached(...)` cho hai nhánh; không đổi các distance dùng để chọn điểm, aggro, social range hoặc recovery nearest candidate.
- Regression architecture mở rộng: `ArrivalPredicateArchitectureTest` yêu cầu 3 call path CivilProfession dùng shared predicate và Merchant dùng shared predicate.
- Focused tests: `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — Arrival predicate audit: mining station

- CivilProfession miner còn một nhánh station arrival dùng `distanceSquared(station) <= 1.0` trước khi bắt đầu mining.
- Root cause: nhánh này vẫn dùng predicate center-distance riêng, không đồng nhất với Citizens block-goal và các work-station path khác.
- Fix: dùng `FarmerRuntime.navigationTargetReached(current, station, config.navigationDistanceMargin())`.
- Regression: mở rộng `ArrivalPredicateArchitectureTest` yêu cầu 4 call path CivilProfession dùng shared predicate.
- Focused và full build: `BUILD SUCCESSFUL`.
- Crop guard `currentWork.location()` giữ nguyên: đây là interaction safety radius sau arrival result, không phải navigation completion predicate. Paper runtime vẫn cần smoke để chứng minh.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — Independent review: crop guard và mining station

- Review độc lập xác nhận mining station dùng shared `navigationTargetReached(...)` là đúng: station là Citizens navigation target; predicate cũ đo center-distance và có thể reject sau Citizens block-goal completion.
- Crop guard `current.distanceSquared(currentWork.location()) <= 9.0` giữ nguyên. Đây là interaction-safety radius tới crop block sau khi `navigationResult()` đã xác nhận arrival tới `standingTarget`; không phải duplicate navigation predicate.
- Các distance còn lại trong mining chỉ dùng selection/ranking candidate, không kết luận arrival.
- Status: source/unit hợp lệ; Paper/Citizens runtime **NOT VERIFIED**.

## 2026-08-19 — Arrival predicate audit: Visitor

- `VisitorRuntime` dùng `distanceSquared(target)` để chuyển `GOING_TO_MARKET` sang `SHOPPING`.
- Root cause: target là Citizens navigation location; center-distance không đồng nhất block-goal semantics.
- Fix: dùng `FarmerRuntime.navigationTargetReached(...)`. Gate arrival vẫn bị chặn riêng bởi `GOING_TO_GATE`; không mở shopping khi chưa qua gate state machine.
- Regression: `ArrivalPredicateArchitectureTest` khóa Visitor dùng shared predicate.
- Focused và full build: `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — Rancher interaction-radius audit

- `RancherRuntime.approach` giữ center-distance tới animal entity: đây là interaction radius để leash/feed, không phải Citizens static navigation arrival.
- Food chest và delivery chest cũng giữ center-distance: thao tác mở/giao cần khoảng cách tới block, không chỉ block-goal completion.
- Return target dùng safe-standing replanning; không kết luận business arrival bằng predicate này.
- Quyết định: không sửa Rancher distance checks ở slice này. Đổi sang shared navigation predicate sẽ có nguy cơ cho phép interaction khi NPC chưa đủ gần entity/chest.
- Status: source reasoning hợp lệ; Paper/Citizens runtime **NOT VERIFIED**.

## 2026-08-19 — Movement intent finite-coordinate hardening

- Audit `setTarget`: remaining direct calls nằm trong `DoubleDoorListener` passage state machine, không phải ordinary movement authority.
- RCA: `MovementIntentPolicy.valid(...)` chỉ reject NaN bằng self-inequality; `Infinity` vẫn có thể qua validation layer trước khi `MovementService.startSimpleNavigation(...)` reject.
- Fix: fail-closed với current và target coordinates không hữu hạn.
- Regression: `MovementIntentPolicyTest.movementIntentRejectsInfiniteTarget`.
- Focused và full build: `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — NavigationRecovery finite-coordinate hardening

- Audit phát hiện `NavigationRecovery.recover(...)` chỉ kiểm tra `world != null`; target/current có thể mang `Infinity` trước khi gọi chunk lookup và safe-standing search.
- RCA: validation không đồng nhất với `MovementService`/`GateRoute`, có nguy cơ tính block index hoặc truy cập world bằng tọa độ invalid.
- Fix: reject fail-closed current/target không hữu hạn trước world/chunk access.
- Regression: `NavigationRecoveryTest.recoveryRejectsNonFiniteTargetBeforeWorldAccess`.
- Focused và full build: `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — GateRouteCoordinator fail-closed start validation

- Audit phát hiện `GateRouteCoordinator.start(...)` chỉ kiểm tra world, chưa reject `Infinity` trong current/finalTarget và candidate null trước `GateRoutePlan`.
- RCA: input lỗi có thể lọt qua route lifecycle, gây exception hoặc route fail không kiểm soát.
- Fix: reject current/finalTarget không hữu hạn và candidate null trước tạo plan.
- Regression: `GateRouteCoordinatorTest.startTuChoiViTriKhongHuuHanVaCandidateNull`.
- Focused và full build: `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — Waypoint coordinator input hardening

- Audit phát hiện `WaypointRouteCoordinator` dùng `List.copyOf(waypoints)` nhưng chưa validate phần tử null, world hoặc tọa độ hữu hạn.
- RCA: route planner có thể tạo coordinator với waypoint lỗi; lỗi chỉ lộ khi `clone()`/arrival check, không fail-closed tại boundary.
- Fix: reject waypoint null, world null, non-finite; giữ empty list là route `FAILED` hợp lệ vì planner có thể trả tuyến rỗng.
- Regression: mở rộng `WaypointRouteCoordinatorTest` cho null và `Infinity` waypoint.
- Focused và full build: `BUILD SUCCESSFUL`.
- Controlled Paper/Citizens runtime: **NOT VERIFIED**. Không deploy/restart.

## 2026-08-19 — Production runtime smoke: candidate rejected and rollback

- Candidate SHA-256: `2083695d61ed87f6964451cf113d5126440fc287821f403199919d002568e1bf`.
- Backup: `F:\\minecraftserver\\villagedefense2026\\plugins\\LivingNPC-backup-smoke-20260819-131731`; rollback JAR SHA-256: `370db1ee5313afb56e170296a09fbd240f412035b0b9801cc43ea7a4ab9b4f83`.
- Startup/linkage pass: Paper `1.21.11-131`, Citizens `2.0.42-SNAPSHOT`, LivingNPC enable, Paper `Done (82.019s)`.
- Runtime rejected: `GOING_TO_BED_GATE` báo `COMPLETED` ở `horizontal=1.1486` dù crossing margin `0.75`; Rancher gate báo `STUCK`, path absent, còn cách target `67.7016`; nhiều NPC lặp `SLEEP_REJECTED`, `GOING_TO_BED` watchdog và recovery loop.
- RCA hiện tại: candidate chưa qua gate geometry/runtime semantics; unit/full build không đủ mô phỏng Citizens callback + world geometry. Không xem là runtime pass.
- Candidate đã clean-stop; rollback JAR hash-guarded; server restart thành công. Sau rollback: Paper `Done (43.049s)`, LivingNPC enable, ports `11619`/`36102` listen, PID `32192`.
- Không rollback YAML; telemetry/log giữ lại để RCA. Production runtime smoke: **REJECTED**.

## 2026-08-19 — Baseline sleep loop RCA after rollback

- Sau rollback về JAR hash `370db1ee5313afb56e170296a09fbd240f412035b0b9801cc43ea7a4ab9b4f83`, cùng lỗi tiếp tục tái hiện; không quy kết lỗi này riêng candidate.
- Evidence: NPC đứng quanh `StillCliff:-32.008,-53.125,-11.0928` / `-32.3276,-53.125,-9.901`, target standing `-30.5,-54,-11.5` / `-30.5,-54,-9.5`; runtime arrival dùng margin `1.5`, nhưng `sleep(...)` yêu cầu shared predicate margin `1.0` rồi reject.
- Root cause: bedtime navigation completion margin rộng hơn interaction-safe sleep margin; phase bị reset `GOING_TO_BED -> INACTIVE`, sau đó retry/recovery vô hạn.
- Decision: cap ordinary arrival margin riêng cho `FarmerPhase.GOING_TO_BED` ở `min(config.navigationDistanceMargin(), 1.0)`. Không tăng sleep radius; gate margin giữ semantics riêng.
- Regression: `ArrivalPredicateArchitectureTest.bedtimeArrivalUsesInteractionSafeMarginCap`.
- Focused và full build: `BUILD SUCCESSFUL`. Không deploy candidate; production tiếp tục chạy rollback JAR.

## 2026-08-19 — Bedtime margin must be applied at Citizens start boundary

- Candidate bedtime cap tại `navigationResult(...)` chưa đủ: runtime log `13:50:37` cho thấy Citizens đã tự kết thúc trước callback, với `distanceMargin=1.5000`, current horizontal `1.5620` và `1.8711`; sau đó phase vẫn `GOING_TO_BED`, navigator đã dừng.
- Root cause: `navigate(...)`, gate leg và waypoint leg vẫn khởi động Citizens bằng `config.navigationDistanceMargin()`; cap sau completion không thể ngăn Citizens `COMPLETED` sớm.
- Decision: tính `navigationMargin` ngay tại `navigate(...)`; `GOING_TO_BED` dùng `min(config.navigationDistanceMargin(), 1.0)`, truyền margin này xuyên qua simple, gate, waypoint và diagnostics. Các phase khác giữ config margin.
- Runtime smoke bản boundary cho thấy simple/gate bedtime đã dùng `distanceMargin=1.0000`; Nanac đạt `GOING_TO_BED -> SLEEPING`. Waypoint bedtime vẫn dùng `1.5000`, lộ thiếu sót ở constructor `WaypointRouteCoordinator`.
- Đã sửa truyền `navigationMargin` vào waypoint coordinator. Full build + `git diff --check`: pass.
- Candidate mới chưa deploy; server đang chạy artifact trước đó để giữ evidence.

## 2026-08-19 — Farmer work transaction guard

- Nghiên cứu Firecrawl đối chiếu Medieval Dynasty, Manor Lords và Banished. Nguồn tham khảo: Medieval Dynasty official, Manor Lords Official Wiki, Banished `Citizens`, `Professions`, `AI`.
- Banished mô tả action queue/state machine: movement chỉ là action; work phải có mutation/output và chọn task kế tiếp sau khi action hoàn tất.
- Runtime Alaric đã chứng minh phase `GOING_TO_PLOT -> GOING_TO_CROP -> INSPECTING -> WORKING`, nhưng log cũ chưa chứng minh crop mutation/output thành công.
- RCA source: `FarmerRuntime.performWork()` mutate crop trước, sau đó `economy.addCarriedProduction(...)` có thể reject khi carried storage/quota không đủ. Khi reject, crop đã reset tuổi nhưng output/XP/activity không commit; đây là transaction inconsistency.
- Fix: lưu `BlockData` trước harvest; nếu output bị reject thì restore crop và không giữ `actionSucceeded`/`produced`; ghi `NPC_STORAGE ... harvest_rollback reason=OUTPUT_REJECTED`.
- Regression: `NpcEconomyTest.harvestPreflightRejectsWhenCarriedStorageCannotAcceptOutput` pass.
- Verification: `./gradlew.bat clean test build --console=plain` pass; `git diff --check` pass.
- Status: CODE VERIFIED, RUNTIME NOT VERIFIED, NOT DEPLOYED.
- Còn lại: smoke farmer sau deploy để xác nhận mutation/output; bedtime gate/waypoint `STUCK` là issue riêng.

## 2026-08-19 — Plot arrival semantic guard (runtime evidence)

- Alaric runtime smoke: Citizens `COMPLETED` tại horizontal `1.5750`, nhưng runtime semantic guard giữ `GOING_TO_PLOT` cho tới khi NPC thật sự vào plot.
- Regression geometry test pass.
- Status: runtime plot-arrival PASS; bedtime và ambient navigation chưa đạt release.

## 2026-08-19 — Plot arrival semantic guard

- Alaric runtime smoke: Citizens `COMPLETED` tại horizontal `1.5750`, nhưng block NPC vẫn ngoài plot radius (`x=59`, plot center `x=68`, radius `8`). Runtime chuyển `GOING_TO_PLOT -> FINDING_WORK`, tạo route lặp.
- Fix: `GOING_TO_PLOT` chỉ chuyển `FINDING_WORK` khi `requiresPlotEntry(...)` xác nhận NPC đã nằm trong plot; completion sớm giữ intent và backoff, không chọn work ngoài plot.
- Regression: `FarmerNavigationPolicyTest.citizensCompletionOutsidePlotDoesNotCountAsPlotEntry`.
- Focused test và full build: `BUILD SUCCESSFUL`; `git diff --check`: pass. Chưa deploy.

## Current status

- Phase 1: IN PROGRESS — special movement authorities còn cần review/test.
- Phase 2: IN PROGRESS — planner staircase đã có bounded-Y fix và regression test.
- Phase 3: PENDING — arrival/recovery/gate smoke.
- Phase 4: PENDING — full build mới và controlled Paper smoke.
- Production CORE PASS: **chưa đạt**.

## Next actions

1. Truy vết và gom `setTarget` trực tiếp của Rancher, Fisher, CivilProfession, Visitor, Merchant và Combat về movement entry point phù hợp.
2. Giữ nguyên tắc mỗi leg chỉ set target một lần; retry chỉ khi navigation thật sự kết thúc/stuck/timeout.
3. Tách arrival theo target margin, vertical tolerance và block clearance; không coi `path present` là movement success.
4. Test FIFO ba NPC, gate closed/open, `ALREADY_OPEN`, cancel, timeout và release.
5. Full build.
6. Controlled Paper smoke: flat, staircase, door, fence gate, FIFO, chunk boundary, recovery.
7. Chỉ ghi PASS khi log có đủ evidence runtime.

## Evidence policy

Unit test chỉ chứng minh logic Java. Không dùng unit test để kết luận Citizens/Paper/gate/chunk/restart/performance đã verified. Mọi runtime claim phải kèm timestamp, process/artifact hash, log marker và status rõ ràng.
## 2026-08-19 — Phase 1 status contract

- Quyết định: không dùng machine learning cho movement core. Route graph/config là source of truth; Citizens chỉ pathfind trong segment đã chọn.
- Thêm `NpcStatus` snapshot immutable với các trục độc lập: `LifeStatus`, `ScheduleStatus`, `IntentStatus`, `RouteStatus`, `ActionStatus`.
- Status giữ `routeId`, `segmentId`, target, failure/recovery, retry count, stuck ticks và last progress tick. Không thay thế `FarmerPhase`; chưa nối behavior để tránh đổi route ngoài scope.
- `NpcStatusTest`: initial state, schema rejection, counter validation và field normalization.
- Verification: `./gradlew.bat test --tests '*NpcStatusTest' --console=plain` pass; `./gradlew.bat clean test build --console=plain` pass; `git diff --check` pass.
- Runtime/BotChecker: chưa chạy. Bước kế tiếp là adapter telemetry `NPC_STATUS`, rồi controlled BotChecker observer có player giữ chunk load và `/time set` trên server được duyệt. Không force-load chunk, không dùng BotChecker làm dependency plugin.
- Status: source/unit `VERIFIED`; Paper/BotChecker `NOT VERIFIED`; chưa deploy.

## 2026-08-19 — NPC_STATUS controlled deployment

- Candidate telemetry trước mapping đã deploy với hash `26418eb7a707f2b12c58ae0217e6f5650065c837be5d656fad977f3dcd261f2c`; backup `F:\\minecraftserver\\villagedefense2026\\plugins\\LivingNPC-backup-status-20260819-162354`.
- Player activation đã làm Alaric spawn; log có `NPC_ACTION` và `NPC_HEALTH total=10 ok=1 waiting=2 errors=7`.
- Telemetry ghi `254` event `NPC_STATUS`. Review phát hiện mapping sai: field `phase` ghi intent, route ghi `FOLLOWING_SEGMENT` khi navigator đã dừng.
- Sửa mapping: phase giữ `FarmerPhase` thật; route chỉ `FOLLOWING_SEGMENT` khi navigator đang chạy; không tự gán `ARRIVED`/`STUCK` khi thiếu evidence.
- Full build và `git diff --check` pass. Candidate mapping deploy hash `6548740548e33e2d59d70c7ce06e3259b94d6598f12cc65cc547f42cb6a0619d`; backup `F:\\minecraftserver\\villagedefense2026\\plugins\\LivingNPC-backup-status-map-20260819-163337`; Paper startup `Done (35.807s)`.
- BotChecker build pass (`146 pass`, `2 Windows signal skipped`), nhưng live observer fail trước scenario do Mineflayer/Protodef `ItemWrittenBookPage` malformed array `16957698`; không phải LivingNPC evidence. BotChecker đã dừng.
- `/time set` chưa chạy. Runtime mapping sau candidate cuối chưa verified. Không claim status contract Paper pass.
- Status: source/unit `VERIFIED`; startup/deploy `VERIFIED`; BotChecker/time-jump `NOT VERIFIED`.

## 2026-08-19 — Controlled plot-gate verification and RCA

- User delegated autonomous operation. Controlled world-time mutation executed on the running Paper process: `time set 1000`; restored to observed `1769` after probe.
- BotChecker connected to `127.0.0.1:11619`, protocol `774`, health/food `20/20`; no movement, teleport, GUI click, entity interaction or command from client.
- Candidate gate-arrival artifact was deployed before probe. Runtime showed exact approach contract rejecting block-goal fallback, but Citizens still returned `COMPLETED` at `StillCliff:56.4260,-60.0625,-1.5247`, target `57.5000,-60.0000,-1.5000`, horizontal `1.0742`, then runtime released navigation and retried `GOING_TO_PLOT`.
- Confirmed: block-goal fallback fix prevents false gate-leg advancement. Remaining defect: Citizens completion is treated as navigation termination before exact GateRoute arrival; retry lifecycle drops route after bounded restart. Farmer action remained unverified; no `NPC_STORAGE` evidence.
- Latest source experiment keeps gate navigation margin capped at `0.75` and exact arrival guard; no production deploy of this follow-up experiment. Full verification: `./gradlew.bat clean test build --console=plain` passed, `483 tests`; `git diff --check` passed.
- Runtime status: time restored; Paper and BotChecker processes remain active for continued controlled observation. No production release claim.
- Follow-up probe removed recovery threshold `Math.max(margin, 2.25)` from `GateRoute.approachReached()` and `advanceApproachAndRequestGate()`; focused/full verification passed (`483 tests`). Controlled runtime still repeated `GOING_TO_PLOT -> INACTIVE` after Citizens `COMPLETED` at horizontal `1.0743`; no gate trace or farmer action. Root cause now isolated to Citizens navigation termination/route callback lifecycle before GateRoute can own exact-arrival retry. Do not change crossing margin or deploy another speculative patch without a new seam-level reproduction.
- Seam trace: `Citizens COMPLETED -> navigator.isNavigating=false -> exact GateRoute arrival=false -> one bounded restart -> same early completion -> MAX_LEG_RESTARTS -> GateRoute FAILED -> FarmerRuntime.navigationFailed() clears route -> next tick rediscovers gate`. This explains route churn; it is not evidence that gate 57 is opened or crossed. Required next fix must preserve immutable route/segment failure state and avoid rediscovery, while keeping bounded retry and explicit STUCK/FAILED telemetry.
- Candidate segment-retry fix deployed with backup `F:/minecraftserver/villagedefense2026/plugins/LivingNPC-backup-segment-retry-20260819-180106`, JAR SHA-256 `6a08a01e03cdee93291efc792c19d9ec637cb866a9de6e2de7e35037cdc0d5ac`. Paper startup passed at `18:02:06`. Runtime logs show `GOING_TO_PLOT->GOING_TO_PLOT` after early Citizens completion; phase no longer falls to `INACTIVE`, and gate plan is not re-emitted every 3 seconds. Gate crossing/farmer action remains unverified because fresh BotChecker start failed `EADDRINUSE` on `127.0.0.1:8080`; world time was restored from controlled `1000` to `1769`.


## 2026-08-19 — BotChecker controlled activation observer

- Fresh BotChecker direct process dùng `PROTOCOL_DIAGNOSTICS=true`, `MC_PORT=11619`, `MC_VERSION=1.21.11`; negotiated version `1.21.11`, protocol `774`.
- Run `c39bafed-67d3-4163-a564-dfeede92dd1a` pass: report `E:\\AI.WORK\\botcheckerminecraft-botchecker\\reports\\c39bafed-67d3-4163-a564-dfeede92dd1a.json`; duration `290000 ms`; client health/food `20/20`; GUI closed; chunk/player activation có hiệu lực.
- Không gửi `/time set`, không teleport, không click, không tương tác NPC. BotChecker đã dừng sau run; API listener `8080` đã dọn.
- Alaric spawn và `NPC_STATUS` mapping mới runtime xuất hiện đúng: `phase=GOING_TO_PLOT`, `route=FOLLOWING_SEGMENT`, `navigating=true`.
- Farmer behavior chưa pass: Alaric lặp `GOING_TO_PLOT -> INACTIVE -> GOING_TO_PLOT` tại plot entry/gate; nhiều lần target `57,-60,-2`/`57,-60,-4`, position khoảng `57,-60,-3`; không có `GOING_TO_CROP`, `INSPECTING`, `WORKING`, `NPC_STORAGE` trong run.
- Health snapshot biến động `ok=5`, sau `ok=6`, rồi cuối window `ok=2 waiting=2 errors=6`; đây là runtime evidence có lỗi, không claim stable.
- BotChecker protocol failure trước đó là intermittent. Fresh run pass, nhưng chưa đủ evidence để sửa decoder; không nới array guard.
- Kết luận: Phase 1 `NPC_STATUS` runtime verified ở field phase/route/navigation; farmer route/plot-entry vẫn blocker. `/time set` gate chưa chạy vì chưa có farmer action baseline ổn định.
- Status: source/unit `VERIFIED`; Paper startup/deploy `VERIFIED`; BotChecker observer `VERIFIED`; farmer action/time-jump `NOT VERIFIED`.

## 2026-08-19 — Tiếp tục sau mất điện: kiểm tra checkpoint và verification

- Working tree sau mất điện vẫn giữ toàn bộ thay đổi chưa commit; không có stash mất. Có 2 file JAR `SkinsRestorer-15.11.1.jar` và `SkinsRestorer-15.12.5.jar` untracked; không đưa vào scope LivingNPC.
- Blocker hiện tại giữ nguyên theo runtime evidence: Farmer lặp `GOING_TO_PLOT` quanh plot/gate, chưa có `GOING_TO_CROP`, `INSPECTING`, `WORKING` hoặc `NPC_STORAGE`. Không deploy/restart sau mất điện.
- Source review: `FarmerRuntime` đã giữ intent khi Citizens kết thúc sớm; `GateRouteCoordinator` retry bounded; `NavigationRecovery` fail-closed với tọa độ không hữu hạn; `NpcStatus` telemetry adapter đã có. Chưa đủ seam evidence để sửa thêm mà không đoán.
- Focused verification: `cmd.exe /c "gradlew.bat test --tests vn.heomc.livingnpc.GateRouteCoordinatorTest --tests vn.heomc.livingnpc.FarmerNavigationPolicyTest --tests vn.heomc.livingnpc.NpcStatusTest --tests vn.heomc.livingnpc.ArrivalPredicateArchitectureTest --console=plain"` — `BUILD SUCCESSFUL`.
- Full verification: `cmd.exe /c "gradlew.bat clean test build --console=plain"` — `BUILD SUCCESSFUL` trong 16 giây, 6 tasks. `git diff --check` pass.
- Một lần chạy filter wildcard nhiều test class thất bại vì Gradle không tìm thấy test theo pattern gộp; không phải lỗi source. Chạy lại bằng class names đầy đủ đã pass.
- Status: checkpoint `SAFE`; source/unit `VERIFIED`; Paper/BotChecker mới `NOT VERIFIED`; production `UNCHANGED`.
- Next: tạo seam-level test cho route giữ trạng thái `FAILED/STUCK` sau exhausted segment retry, rồi mới cân nhắc code. Không speculative deploy.

## 2026-08-19 — Gate segment retry seam regression

- Mục tiêu: khóa contract sau khi Citizens kết thúc sớm: bounded restart không được tạo lại candidate/plan mới trong cooldown; route cũ phải giữ active state.
- Regression thêm vào `GateRouteCoordinatorTest.exhaustedLegRetryGiuRouteKhongRediscoverCandidateTrongCooldown`.
- Test xác nhận: restart lần đầu ở tick `10`; exhausted retry ở tick `20` vào cooldown tới tick `30`; tick `29` không set target; tick `30` retry đúng approach cũ. Không rediscover candidate.
- Focused: `cmd.exe /c "gradlew.bat test --tests vn.heomc.livingnpc.GateRouteCoordinatorTest.exhaustedLegRetryGiuRouteKhongRediscoverCandidateTrongCooldown --console=plain"` — `BUILD SUCCESSFUL`.
- Full: `cmd.exe /c "gradlew.bat clean test build --console=plain"` — `BUILD SUCCESSFUL` trong 22 giây, 6 tasks. `git diff --check` pass.
- Không cần source fix trong slice này: contract đã tồn tại ở `GateRouteCoordinator`; regression khóa hành vi chống route rediscovery tại coordinator seam. Farmer/Paper runtime vẫn chưa verified.
- Status: regression `VERIFIED`; production `UNCHANGED`; CORE runtime `NOT VERIFIED`.

## 2026-08-19 — Firecrawl research và fix active Citizens gate parameters

- Firecrawl developer search + scrape nguồn Citizens:
  - `https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/NavigatorParameters.html`: `distanceMargin` là completion cutoff đo theo block distance; `pathDistanceMargin` là khoảng pathfinder cần đi tới target, không phải completion cutoff; `range` giới hạn phạm vi pathfinding; `activeParametersAfterTarget` cần cấu hình local clone sau `setTarget`.
  - `https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/Navigator.html`: `setTarget(Iterable<Vector>)` là waypoint movement không pathfinding; route hiện dùng `setTarget(Location)` qua `MovementService`.
  - `https://github.com/CitizensDev/Citizens2/issues/2399`: issue cũ xác nhận Citizens pathfinding phụ thuộc mạnh vào `default-distance-margin`, `default-path-distance-margin`, `range`; không phải proof trực tiếp cho bug hiện tại.
- Đối chiếu runtime/source: gate start truyền `margin=0.0` vào `MovementService`, nhưng sau đó không cấu hình lại active parameter clone; Citizens có thể giữ `distanceMargin` cũ và báo `COMPLETED` tại block-goal trước exact gate approach/exit. Điều này khớp log `COMPLETED` tại horizontal `1.0742` khi exact gate arrival chưa đạt.
- Fix: sau `activeParametersAfterTarget(...)` trong `FarmerRuntime.startGateRoute`, cấu hình active clone với `distanceMargin(0.0)`, `pathDistanceMargin(0.0)`, `destinationTeleportMargin(0.0)`, speed/stuck action và door policy.
- Regression: `FarmerNavigationActiveParametersTest.gateNavigationConfiguresActiveParameterCloneAfterTarget`.
- Focused: `FarmerNavigationActiveParametersTest`, `GateRouteCoordinatorTest` — `BUILD SUCCESSFUL`.
- Full: `cmd.exe /c "gradlew.bat clean test build --console=plain"` — `BUILD SUCCESSFUL` trong 18 giây, 6 tasks. `git diff --check` pass.
- Status: source/unit `VERIFIED`; controlled Paper/BotChecker `NOT VERIFIED`; production `UNCHANGED`; không deploy/restart.

## 2026-08-19 — Đồng bộ Rancher gate active parameters và audit target ownership

- `git grep` xác nhận direct `navigator.setTarget(...)` còn lại chỉ thuộc `DoubleDoorListener` passage state machine và `MovementService`; không phát hiện ordinary Farmer/Rancher/Fisher/Civil/Visitor/Merchant/Combat path bypass mới.
- `DoubleDoorListener` giữ contract đặc biệt: approach, crossing, restore original target. Không đổi vì đã có cleanup/restore tests và không cùng gate-route completion path.
- `RancherRuntime` ordinary navigation vẫn cấu hình active parameters sau target; gate navigation nay dùng exact Citizens margin `0.0` và route-level margin riêng.
- Full verification lặp lại: `cmd.exe /c "gradlew.bat clean test build --console=plain"` — `BUILD SUCCESSFUL` trong 23 giây, 6 tasks. `git diff --check` pass.
- Không có thêm source fix hợp lệ từ audit tĩnh. Runtime Farmer/Rancher vẫn `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — Checkpoint sau full verification

- Full: `cmd.exe /c "gradlew.bat clean test build --console=plain"` — `BUILD SUCCESSFUL` trong 19 giây, 6 tasks.
- Hygiene: `git diff --check` pass.
- Working tree giữ nguyên các thay đổi chưa commit và file untracked ngoài scope; không reset, commit, deploy hoặc restart.
- Runtime Paper/BotChecker: `NOT VERIFIED`. Production: `UNCHANGED`.

## 2026-08-19 — Đóng R-013 Gradle build warning

- `--warning-mode all` xác định warning tại `build.gradle.kts:41`: truy cập `Task.project` trong execution phase.
- Fix tối thiểu: snapshot `version.toString()` thành `pluginVersion` ở configuration phase; `processResources` dùng snapshot này.
- Verification: `cmd.exe /c "gradlew.bat clean test build --warning-mode all --console=plain"` — `BUILD SUCCESSFUL` trong 23 giây, 6 tasks; không còn Gradle deprecation warning. Java compiler note trong `CombatManager` là API deprecated riêng, không thuộc R-013.
- `git diff --check`: pass. Production/runtime Paper: unchanged/not verified.

## 2026-08-19 — R-002 Citizens registry readiness audit

- Source audit: `plugin.yml` declares hard `depend: [Citizens]`; Season 2 active managers use `registryOrNull()` and fail closed when Citizens registry is null or throws `IllegalStateException`.
- Focused: `CitizensRegistryReadinessTest`, `CitizensRegistryGuardTest`, `LivingNpcPluginStartupArchitectureTest` — `BUILD SUCCESSFUL` trong 5 giây.
- `CivilProfessionManager` còn direct registry access nhưng thuộc Season 4, hiện release-gated (`ReleasePolicy.SEASON=2`); không mở rộng scope.
- Artifact hiện tại: `build/libs/living-npc-0.6.0-rc.2.jar`, SHA-256 `7eeb8b043e8f03ec6d08a1c659e159d3247c8240982626169f70dcdbf43b69f1`.
- Status: source/unit `VERIFIED`; Paper cold start/restart/missing Citizens `NOT VERIFIED`; production `UNCHANGED`; không deploy/restart.

## 2026-08-19 — R-001 runtime stop audit

- `LivingNpcPlugin.onDisable()` hủy tick, telemetry, BlueMap tasks trước khi gọi `RuntimeStopCoordinator.stop()`.
- `startTickTask()` gọi coordinator `stop()` khi `runtime.enabled=false`, rồi `start()` khi bật lại; repeated stop không chạy cleanup lần hai.
- Focused: `RuntimeStopCoordinatorTest`, `LivingNpcPluginStartupArchitectureTest` — `BUILD SUCCESSFUL` trong 2 giây.
- Full: `cmd.exe /c "gradlew.bat clean test build --console=plain"` — `BUILD SUCCESSFUL` trong 25 giây, 6 tasks. `git diff --check` pass.
- Status: source/unit `VERIFIED`; controlled Paper disable during active navigation/door passage `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — R-003 synchronous YAML write audit

- Source audit found main persistence paths in `AtomicYamlStore`, `NpcEconomyStore`, and `NeedsStore`; all use temporary file plus atomic move fallback.
- `SaveTelemetry` records `YAML_SAVE`, elapsed microseconds, and file bytes; threshold `5000` microseconds logs warning. `SaveTelemetryTest` covers threshold and save integrity/failure behavior.
- Không chuyển I/O sang async khi chưa có Paper workload evidence; Bukkit state snapshot/thread safety và crash consistency cần giữ nguyên.
- Status: instrumentation/source `VERIFIED`; representative Paper p50/p95/p99 latency `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — R-004 WorldGuard reload contract

- `WorldMutationPolicy` snapshots WorldGuard availability and `protection.require-worldguard` at enable; hot replacement remains unsafe/unproven.
- `/lnpc reload` still reloads safe config, then reports explicit restart-required reasons for policy drift: `protection.require-worldguard`, `WorldGuard availability`.
- Focused: `WorldMutationPolicyReloadTest`, `LivingNpcPluginStartupArchitectureTest` — `BUILD SUCCESSFUL` trong 3 giây.
- Full: `cmd.exe /c "gradlew.bat clean test build --console=plain"` — `BUILD SUCCESSFUL` trong 18 giây, 6 tasks. `git diff --check` pass.
- Status: source/unit `VERIFIED`; controlled Paper reload/restart `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — R-005/R-006 persistence và lifecycle audit

- R-005: `ConfigSchemaMigration` tạo `.bak-*` trước migration; `AtomicYamlStore` dùng temp + atomic move fallback; tests giữ nguyên corrupt/unsupported data và fail-closed khi ghi lỗi.
- R-005 chưa có failure injection giữa non-atomic replacement và restoration; không thêm backup unbounded cho mọi YAML path khi chưa có seam và yêu cầu cụ thể.
- R-006: `RuntimeStopCoordinatorTest`, `RuntimeManagerShutdownTest`, `ConfigSchemaMigrationTest` cover stop continuation, manager cleanup, invalid config disable và migration failure preservation.
- `onEnable` chưa transactional toàn phần; cần bootstrap failure-injection seam riêng trước khi tái cấu trúc.
- Focused: `ConfigSchemaMigrationTest`, `SaveTelemetryTest`, `RuntimeManagerShutdownTest` — `BUILD SUCCESSFUL` trong 4 giây.
- Full: `cmd.exe /c "gradlew.bat clean test build --console=plain"` — `BUILD SUCCESSFUL` trong 22 giây, 6 tasks. `git diff --check` pass.
- Status: persistence/stop source-unit `VERIFIED`; crash recovery và enable rollback `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — R-007 WorldGuard query failure diagnostics

- `WorldMutationPolicy.allows` và mining-region query tiếp tục fail-closed khi gặp `RuntimeException` hoặc `LinkageError`.
- Thêm warning rate-limited tối đa một lần mỗi 60 giây, ghi operation và exception type; không log location/player payload.
- Full: `cmd.exe /c "gradlew.bat clean test build --no-daemon --console=plain"` — `BUILD SUCCESSFUL` trong 29 giây, 6 tasks. `git diff --check` pass.
- Hai lần chạy Gradle trước gặp `NoSuchFileException` trên test binary `in-progress-results`; chẩn đoán daemon/test-worker state, `gradlew.bat --stop` rồi chạy `--no-daemon` đã pass.
- Controlled Paper WorldGuard `7.0.16` query semantics vẫn `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — R-008 VillagePathCache lifecycle

- Fix: `VillagePathCache.tick()` prunes states whose village ID no longer exists, clears all states when patrol is disabled, and `shutdown()` clears state explicitly.
- `FarmerManager.shutdown()` invokes `pathCache.shutdown()` before final save; cache paths remain bounded by `maxCachedPaths` per current village.
- Regression: `VillagePathCacheLifecycleTest` covers shutdown and disabled-patrol stale-state removal.
- First full retry exposed missing `org.bukkit.Bukkit` import at `FarmerRuntime.java:1794`; added import, then focused test and full build passed.
- Full: `cmd.exe /c "gradlew.bat clean test build --no-daemon --console=plain"` — `BUILD SUCCESSFUL` trong 28 giây, 6 tasks. `git diff --check` pass.
- Status: source/unit `VERIFIED`; Paper village delete/reload/world-change lifecycle `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — R-009 dependency reproducibility audit

- `build.gradle.kts` uses mutable Paper `1.21.11-R0.1-SNAPSHOT` and Citizens `2.0.42-SNAPSHOT` coordinates.
- `gradlew.bat dependencies --configuration compileClasspath` passed; graph confirms transitive conflict resolution, including Guava `33.3.1-jre -> 33.5.0-jre`, Gson `2.11.0 -> 2.13.2`, and Fastutil `8.5.15 -> 8.5.18`.
- Không thêm dependency locking/verification metadata khi chưa có artifact hash từ Paper runtime tương ứng; lock sai có thể làm lệch compatibility.
- Required evidence: disposable controlled Paper environment, exact Paper `1.21.11-131`, Citizens build `4173`, resolved artifact metadata/hash comparison.
- Status: risk confirmed; source unchanged; reproducible runtime build `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — R-010 managed Paper integration gap

- Repository audit found no `run-paper`, Paperclip, disposable server fixture, or managed Paper lifecycle script.
- Nested BotChecker content is observer/reference material; it cannot prove current plugin startup, Citizens linkage, WorldGuard queries, door/gate crossing, persistence restart, or shutdown cleanup.
- Historical live-server evidence remains historical and is not promoted to current candidate evidence.
- Required controlled setup: exact Paper `1.21.11-131`, Citizens build `4173`, WorldGuard `7.0.16`, isolated data directory, candidate JAR hash, controlled journey, telemetry/log capture, teardown.
- No server process started, restarted, deployed, or modified.
- Status: unit/source `VERIFIED`; managed Paper integration `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — R-014 LivingDoorExaminer focused audit

- `LivingDoorExaminer` rejects null/stale block callbacks, unsupported material, invalid distance, cancelled authorization and callbacks after shutdown.
- Close tasks validate expected material/Openable state, NPC world/navigation ownership and bounded timeout; shutdown finishes each task independently and `resume()` is idempotent.
- Focused: `LivingDoorExaminerTest`, `LivingDoorExaminerLifecycleArchitectureTest`, `DoubleDoorListenerTest`, `GateRouteListenerTest` — `BUILD SUCCESSFUL` trong 6 giây.
- Status: source/unit `VERIFIED`; Paper event ordering, actual double-door/fence-gate timing and managed-NPC crossing remain `NOT VERIFIED`; no server action.

## 2026-08-19 — R-006 enable ordering audit

- `LivingNpcPlugin.onEnable()` creates stores/managers, registers listeners, constructs `RuntimeStopCoordinator`, then wires commands and schedules tick/telemetry/BlueMap tasks.
- Listener registration precedes coordinator construction, but Bukkit unregisters plugin listeners on disable; scheduled runtime work starts only after coordinator exists.
- No transactional bootstrap rollback refactor added without a failure-injection seam; source change would be speculative.
- Focused: `LivingNpcPluginStartupArchitectureTest`, `RuntimeStopCoordinatorTest`, `RuntimeManagerShutdownTest` — `BUILD SUCCESSFUL` trong 4 giây.
- Full: `cmd.exe /c "gradlew.bat clean test build --no-daemon --console=plain"` — `BUILD SUCCESSFUL` trong 25 giây, 6 tasks. `git diff --check` pass.
- Status: stop path source/unit `VERIFIED`; enable rollback `NOT VERIFIED`; production `UNCHANGED`.

## 2026-08-19 — Open-risk batch checkpoint

- R-001 through R-010 and R-014 đã được audit. Không còn source/test fix an toàn nào có thể thay controlled Paper evidence cho các risk còn mở.
- R-013 đã đóng; R-011/R-012 đã đóng bằng safeguards hiện hữu.
- Full local test sau batch: `cmd.exe /c "gradlew.bat test --no-daemon --console=plain"` — `BUILD SUCCESSFUL` trong 7 giây.
- Artifact hiện tại: `build/libs/living-npc-0.6.0-rc.2.jar`, SHA-256 `04deb137687e94b07fdc77138fb0900f8b6dc15ec7a7a0e35cf53253a6d80791`.
- `git diff --check` pass.
- Blocker còn lại: controlled Paper cold start/restart, Citizens/WorldGuard/door/gate lifecycle, persistence recovery, performance telemetry, dependency artifact hashes. Cần isolated Paper environment; không dùng production.
- Production `UNCHANGED`; chưa deploy/restart.

## 2026-08-19 — Isolated Paper cold-start smoke và BlueMap RCA

- Clone isolated: `E:\AI.WORK\living-npc-paper-smoke-rc2`; fixture gốc `E:\AI.WORK\living-npc-paper-test` không sửa.
- Smoke lần 1 với candidate trước fix fail enable: `NoClassDefFoundError: de/bluecolored/bluemap/api/markers/Marker` tại `LivingNpcPlugin.java:84`; nguyên nhân `softdepend: BlueMap` nhưng class BlueMap nằm trên load path khi constructor `BlueMapMarkerService` chạy.
- Fix: `LivingNpcPlugin` giữ BlueMap integration dạng lazy `Class.forName` + reflection; thiếu/không tương thích thì fail-closed, LivingNPC vẫn enable.
- Unit focused `LivingNpcPluginOptionalDependencyTest`: PASS. Full `clean test build --no-daemon`: PASS, 29s.
- Smoke lần 2 candidate SHA-256 `136caeb8f39f78e250fc7d6a8d07a555c0c01014a0c8fe274bd8cc6fa07080fc`: Paper `1.21.11-131`, Citizens `2.0.42-SNAPSHOT (build 4173)`, WorldGuard `7.0.16`; `LivingNPC Enabling` → `LivingNPC Season 2 enabled` → `Done (20.112s)`, BlueMap disabled by config, không có LivingNPC exception.
- Đã dừng isolated server. Chưa chạy journey/door crossing/BotChecker; production không đổi.

## 2026-08-19 — Isolated Paper smoke khi LivingNPC enable

- Khởi động `E:\AI.WORK\living-npc-paper-smoke-rc2`, canh log đến `LivingNPC Enabling` và `Done (14.839s)`.
- Paper `1.21.11-131`, Citizens `2.0.42-SNAPSHOT (build 4173)`, WorldGuard `7.0.16` load đúng.
- LivingNPC enable pass; log có `LivingNPC Season 2 enabled`; không có exception LivingNPC trong cửa sổ smoke.
- Telemetry export chạy; `YAML_SAVE` khoảng `5056–6326` micros, file `802` bytes. Đây là sample isolated nhỏ, chưa đại diện p95 production.
- 10 Citizens NPC tồn tại nhưng đều `spawned=false`; không có navigation/door/gate journey evidence. Verdict: cold-start `PASS`, gameplay `INCONCLUSIVE`.
- Đã dừng toàn bộ child Paper PID `36032` trên isolated port `25576`; production không chạm.

## 2026-08-19 — BotChecker hosted smoke trên isolated Paper

- Candidate: `LivingNPC` JAR chạy trong `E:\AI.WORK\living-npc-paper-smoke-rc2`; Paper `1.21.11-131`; Citizens build `4173`; WorldGuard `7.0.16`.
- BotChecker runner dùng loopback `127.0.0.1:25576`, scenario `living-npc-smoke`, run `368b9296-ea19-41f5-b4ee-78e6c2800c83`.
- Client connect/protocol `1.21.11`/`774`: PASS. Health `20`, food `20`, GUI closed: PASS.
- `assert_nearby_entity ThanhRedfield <=48`: FAIL timeout; không có NPC trong client entity stream.
- Scenario current-area run `3e48e2bf-17ed-4cb4-91ef-718322b0f3dc`: bounded walk timeout sau `25094ms`, vị trí chỉ tới `(-96.42,61.88,152.42)` từ spawn `(-136.5,65,184.5)`; không teleport/click/interact.
- Server `NPC_HEALTH total=10 ok=0 waiting=10 errors=0`; toàn bộ NPC, gồm ThanhRedfield, Alex, Jumonka, Steve, đều `spawned=false`, diagnostic `NPC chưa spawn`.
- Verdict: Paper/client connectivity `PASS`; NPC gameplay journey `FAIL` do fixture runtime không spawn NPC; Farmer/Rancher gate crossing `NOT TESTED`, không được kết luận source navigation fail từ run này.
- Đã dừng BotChecker và Paper child isolated. Production không chạm.

## 2026-08-19 — Fix persisted Citizens NPC spawn lifecycle

- Runtime evidence trước fix: Citizens `Loaded 10 NPCs`, nhưng `NPC_HEALTH total=10 ok=0 waiting=10 errors=0`; toàn bộ managed NPC `spawned=false`.
- Source trace: `FarmerManager.bindLoadedNpcs()` chỉ `configureWorkerNpc` + tạo runtime; `npc.spawn(...)` chỉ tồn tại trong flow create mới. Persisted NPC sau restart không được spawn lại.
- Fix: khi bind loaded NPC, nếu chưa spawn và `getStoredLocation()` có world/chunk loaded, gọi `npc.spawn(stored)`; nếu spawn fail giữ fail-closed, không tạo runtime giả.
- Regression `FarmerManagerLoadedNpcSpawnTest`: PASS. Full `clean test build --no-daemon`: PASS, 29s.
- Candidate SHA-256 `be3edbef055b27b872b102a57eb4a04bb2aca98280daf12a2b8e642818b22893`.
- Paper smoke sau fix: 8 NPC phát sinh `NPC_ACTION` với vị trí thật; không còn trạng thái toàn bộ `spawned=false`. Fixture có `BED_BLOCK_MISSING` ở các home cũ, nên NPC vào error/rest; đây là data fixture gap, không sửa production source.
- BotChecker current-area run `a4e14a83-c869-4dce-a9bc-3d56c9ce1bb1`: client state PASS nhưng pathfinder `No path to the goal!` tại waypoint; gate journey chưa test.
- Verdict: persisted-NPC spawn lifecycle `CONTROLLED PAPER PASS`; NPC work/door/gate `NOT VERIFIED` vì fixture thiếu bed và terrain route không đi được.
