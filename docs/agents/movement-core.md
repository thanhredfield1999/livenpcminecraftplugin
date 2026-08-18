# Workstream: Movement Core

## Owner

Java implementation + architecture/RCA agent.

## Scope

- Citizens `Navigator` lifecycle.
- One target per navigation leg.
- `MovementIntent`.
- `WaypointRoutePlanner` and staircase.
- Arrival, timeout, stuck, recovery.
- Chunk/collision preconditions.

## Current lessons

- `setTarget` lặp tạo `REPLACE`, làm reset route.
- `distanceMargin` khác `pathDistanceMargin`.
- `path present` không đồng nghĩa NPC đã đi qua collision/stair.
- `deltaY=-2.5` chứng minh route bypass planner hoặc planner chưa là authority.
- Paper `Mob#getPathfinder()` không áp dụng Player NPC.

## Implemented

- `MovementIntent`.
- `MovementService`.
- Bounded horizontal waypoint.
- Vertical waypoint delta tối đa 1 block.
- Farmer invalid-target preflight.

## Verification

- `MovementServiceTest`: PASS.
- `MovementIntentPolicyTest`: PASS.
- `WaypointRoutePlannerTest`: PASS.
- Full build gần nhất: PASS trước owner-recheck patch; phải chạy lại trước artifact.

## Open defects

- Rancher/Fisher/Civil/Visitor/Merchant/Combat còn direct navigation call path.
- `NavigationRecovery` chưa phân loại đủ nguyên nhân stuck.
- Chưa có Paper smoke CORE PASS.

## Khi agent làm tiếp

Đọc full source `FarmerRuntime`, `RancherRuntime`, `WaypointRoutePlanner`, `NavigationRecovery`, tests và incident docs. Không bật global Citizens budget. Không deploy production khi chưa full build + approval.

## Entry log

### 2026-08-18 — Initial split

- Result: `IN PROGRESS`.
- Evidence: local source/tests và research note.
- Next: gom runtime call path, rồi test arrival/recovery.

### 2026-08-18 — Target installation ownership

- Symptom: `NavigationDiagnostics.activeParametersAfterTarget(...)` tự gọi `navigator.setTarget(...)`.
- RCA: helper instrumentation/configuration giữ luôn side effect target installation; caller route cũng có thể set target, tạo nguy cơ `REPLACE`.
- Fix: diagnostics chỉ lấy local parameters và áp margin; caller set target đúng một lần trước helper.
- Affected callers: Farmer normal/gate route, Rancher gate route.
- Test first failed: `NavigationDiagnosticsTest.appliesLegMarginsToCitizensActiveNavigation` vì test cũ kỳ vọng diagnostics tự set target.
- Test fix: cập nhật contract test để target installation thuộc caller.
- Focused: `NavigationDiagnosticsTest`, `DoubleDoorListenerTest`, `GateRouteCoordinatorTest` — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --no-daemon --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` source/test; runtime `NOT VERIFIED`.
- Lesson: mọi helper tên diagnostics/config không được ẩn mutation navigation.

### 2026-08-18 — NavigationRecovery review

- `NavigationRecovery` chỉ teleport tới safe standing sau timeout; kiểm tra world/chunk/foot/head/support.
- `UNAVAILABLE` giữ intent; `RECOVERED` trả kết quả caller xử lý.
- Existing `NavigationRecoveryTest` pass trong full test.
- Open risk: production movement policy vẫn chưa chứng minh recovery không tạo arrival giả; cần Paper smoke.
- Status: `REVIEWED`, runtime `NOT VERIFIED`.

### 2026-08-18 — Arrival margin contract

- Scope: `FarmerRuntime.navigationTargetReached`.
- Fix: reject negative/non-finite arrival margin trước geometry calculation.
- Existing behavior retained: Citizens block-goal horizontal distance + vertical tolerance `< 1.0`.
- Regression: `FarmerNavigationPolicyTest.rejectsNegativeOrNonFiniteArrivalMargin`.
- Focused: `FarmerNavigationPolicyTest`, `NavigationRecoveryTest` — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --no-daemon --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` unit contract; runtime `NOT VERIFIED`.

## Next

- Thêm arrival contract test cho vertical tolerance, block-footprint và route complete.
### 2026-08-18 — Waypoint vertical continuity

- Symptom: historical production log `deltaY=-2.5000`; planner snap có thể chọn đứng space lệch Y ngoài interpolation.
- Fix: `WaypointRoutePlanner` truyền `previousY`; snap reject candidate lệch quá 1 block; cập nhật previous Y theo waypoint thật.
- Regression: `WaypointRoutePlannerTest.snapCannotIntroduceMoreThanOneBlockVerticalChange`.
- Focused test: `WaypointRoutePlannerTest` — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --no-daemon --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` planner unit; runtime `NOT VERIFIED`.

### 2026-08-18 — Chunk guard review

- `RuntimeChunkAvailability` đã có `loaded` và bounded `loadedArea`.
- Farmer/Civil/Fisher dùng guard ở lifecycle/task entry.
- Waypoint route chưa preload/ticket corridor; không tự force-load trong hot path.
- Status: `REVIEWED`, cross-chunk runtime `NOT VERIFIED`.

### 2026-08-18 — Waypoint route chunk preflight

- Added `RuntimeChunkAvailability.loadedRoute(List<Location>)`.
- `FarmerRuntime.startWaypointRoute` fail-closed nếu bất kỳ waypoint khác world hoặc chunk chưa loaded.
- Không force-load chunk, không tạo plugin ticket trong tick path.
- Regression: `FisherRuntimeActivationLifecycleTest.loadedRouteFailsClosedWhenAnyWaypointChunkIsMissing`.
- Focused: `FisherRuntimeActivationLifecycleTest`, `WaypointRoutePlannerTest` — `BUILD SUCCESSFUL`.
- Full: `./gradlew.bat clean test build --no-daemon --console=plain` — `BUILD SUCCESSFUL`.
- Production/cross-chunk: `NOT VERIFIED`.
- Risk: route dài cần bounded loaded corridor; hiện fail-closed thay vì tự load.
## Next

- Thêm arrival contract test cho vertical tolerance, block-footprint và route complete.
- Bổ sung bounded route chunk preflight nếu source contract cho phép.
- Controlled Paper smoke sau full artifact.
- Không deploy từ slice này.
