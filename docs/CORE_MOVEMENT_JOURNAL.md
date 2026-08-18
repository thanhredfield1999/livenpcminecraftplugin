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
