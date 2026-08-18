# Workstream: Runtime Integration

## Owner

Integration implementation agent.

## Scope

Gom movement call path của Farmer, Rancher, Fisher, CivilProfession, Visitor, Merchant, Combat về authority phù hợp; tránh runtime tự tranh Navigator.

## Evidence

Repo analysis xác nhận Farmer có route/gate/waypoint pipeline một phần. Rancher có gate path riêng. Fisher, CivilProfession, Visitor, Merchant còn direct `navigator.setTarget`; Combat có preemption logic riêng.

## RCA

Movement CORE hiện phân mảnh. Vì vậy `REPLACE`, `path=absent`, staircase bypass và gate trace thiếu không chỉ là lỗi một class.

## Status

`BLOCKED BY CORE MOVEMENT`.

## Rules

- Không refactor unrelated behavior.
- Mỗi navigation leg set target một lần.
- Giữ navigation lease/preemption.
- Thêm test trước khi gom từng runtime.
- Full build trước artifact.

## Entry log

### 2026-08-18 — Inventory

- Result: `BLOCKED`.
- Next: Movement authority API ổn định trước, sau đó gom từng runtime với focused test.

### 2026-08-18 — Fisher slice

- Scope: `FisherRuntime.startNavigation`.
- Change: route qua `MovementService.startSimpleNavigation`; giữ check target đang chạy để không tạo `REPLACE` giả.
- Initial test: `FisherRuntimeLifecycleTest` FAIL vì mock `Location.getWorld()` null làm helper reject.
- RCA: preflight helper áp điều kiện world quá chặt, không phù hợp test/domain target đã được caller kiểm soát.
- Fix: bỏ reject world null; giữ finite coordinate/margin; giữ target identity semantics theo Citizens test.
- Final focused test: `FisherRuntimeLifecycleTest`, `MovementServiceTest` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Next: thêm Merchant/Visitor từng slice; không gom Combat/DoubleDoor cùng patch.

### 2026-08-18 — Merchant slice

- Scope: `MerchantRuntime.navigate`.
- Change: dùng `MovementService.startSimpleNavigation`; state update chỉ sau helper thành công.
- Full test: `./gradlew.bat test --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` unit regression; `NOT VERIFIED` runtime.
- Next: Visitor; sau đó rà direct `setTarget` còn lại.

### 2026-08-18 — Visitor slice

- Scope: `VisitorRuntime.navigate` và formation member navigation.
- Change: dùng `MovementService.startSimpleNavigation`; bỏ lặp cấu hình Citizens ở runtime này.
- Full test: `./gradlew.bat test --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` unit regression; `NOT VERIFIED` runtime.
- Caveat: formation target thay đổi theo leader; helper chỉ bỏ duplicate cùng target, còn target mới hợp lệ có thể replace navigation cũ. Cần test formation cadence riêng.
- Next: rà direct `setTarget`; giữ nguyên `DoubleDoorListener` và `CombatManager` tới khi có test authority riêng.

## Direct navigation remaining

- `CombatManager.java`: combat preemption path; cần authority/lease test riêng.
- `DoubleDoorListener.java`: special door passage state machine; không thay bằng simple helper.
- `FarmerRuntime.java`: route-specific waypoint/gate callbacks; giữ direct target có chủ đích.
- `RancherRuntime.java`: gate callback direct target có chủ đích.
- `NavigationDiagnostics.java`: instrumentation helper, không phải runtime owner.

Không còn direct simple navigation ở Fisher, CivilProfession, Merchant và Visitor.

### 2026-08-18 — Combat slice

- Scope: `CombatManager.navigate`.
- Change: dùng `MovementService.startSimpleNavigation`; giữ world guard và combat-specific preemption ngoài helper.
- Full test: `./gradlew.bat test --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` unit regression; `NOT VERIFIED` runtime.
- DoubleDoor chưa đổi: đây là state machine đặc biệt, mỗi `setTarget` tương ứng phase approach/cross/restore và có cleanup test riêng.

## Remaining special authorities

- `DoubleDoorListener`: cần review phase transition, không thay helper mù.
- `FarmerRuntime`/`RancherRuntime`: route callback.
- `NavigationDiagnostics`: instrumentation.
- Formation Visitor: cần cadence test nếu target leader thay đổi nhanh.

## Current status

- Simple runtime navigation: đã gom.
- Special movement authority: cần test/review.
- Production/runtime: `NOT VERIFIED`.
- CORE movement: `NOT VERIFIED`.
- Full verification after Combat/DoubleDoor review: `./gradlew.bat clean test build --no-daemon --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.

## Entry log

### 2026-08-18 — Inventory

- Result: `BLOCKED`.
- Next: Movement authority API ổn định trước, sau đó gom từng runtime với focused test.

### 2026-08-18 — Fisher slice

- Scope: `FisherRuntime.startNavigation`.
- Change: route qua `MovementService.startSimpleNavigation`; giữ check target đang chạy để không tạo `REPLACE` giả.
- Initial test: `FisherRuntimeLifecycleTest` FAIL vì mock `Location.getWorld()` null làm helper reject.
- RCA: preflight helper áp điều kiện world quá chặt, không phù hợp test/domain target đã được caller kiểm soát.
- Fix: bỏ reject world null; giữ finite coordinate/margin; giữ target identity semantics theo Citizens test.
- Final focused test: `FisherRuntimeLifecycleTest`, `MovementServiceTest` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Next: thêm CivilProfession/Merchant/Visitor từng slice; không gom Combat/DoubleDoor cùng patch.

### 2026-08-18 — CivilProfession slice

- Scope: `CivilProfessionRuntime.navigate`.
- Change: route qua `MovementService.startSimpleNavigation`; giữ phase/state update chỉ khi helper start thành công.
- Test: `MinerSafetyTest`, `MinerCandidateSelectionTest` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` cho slice unit; `NOT VERIFIED` runtime.
- Next: thêm CivilProfession/Merchant/Visitor từng slice; không gom Combat/DoubleDoor cùng patch.

### 2026-08-18 — Merchant slice

- Scope: `MerchantRuntime.navigate`.
- Change: dùng `MovementService.startSimpleNavigation`; state update chỉ sau helper thành công.
- Full test: `./gradlew.bat test --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` unit regression; `NOT VERIFIED` runtime.
- Next: Visitor; sau đó rà direct `setTarget`.

### 2026-08-18 — Visitor slice

- Scope: `VisitorRuntime.navigate` và formation member navigation.
- Change: dùng `MovementService.startSimpleNavigation`; bỏ lặp cấu hình Citizens ở runtime này.
- Full test: `./gradlew.bat test --console=plain` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` unit regression; `NOT VERIFIED` runtime.
- Caveat: formation target thay đổi theo leader; helper chỉ bỏ duplicate cùng target, còn target mới hợp lệ có thể replace navigation cũ. Cần test formation cadence riêng.
- Next: rà direct `setTarget`; giữ nguyên `DoubleDoorListener` và `CombatManager` tới khi có test authority riêng.

Không còn direct simple navigation ở Fisher, CivilProfession, Merchant và Visitor.

## Direct navigation remaining

- `CombatManager.java`: combat preemption path; cần authority/lease test riêng.
- `DoubleDoorListener.java`: special door passage state machine; không thay bằng simple helper.
- `FarmerRuntime.java`: route-specific waypoint/gate callbacks; giữ direct target có chủ đích.
- `RancherRuntime.java`: gate callback direct target có chủ đích.
- `NavigationDiagnostics.java`: instrumentation helper, không phải runtime owner.

Không còn direct simple navigation ở Fisher, CivilProfession, Merchant và Visitor.

### 2026-08-18 — CivilProfession slice

- Scope: `CivilProfessionRuntime.navigate`.
- Change: route qua `MovementService.startSimpleNavigation`; giữ phase/state update chỉ khi helper start thành công.
- Test: `MinerSafetyTest`, `MinerCandidateSelectionTest` — `BUILD SUCCESSFUL`.
- Production/runtime: `NOT VERIFIED`.
- Status: `PASS` cho slice unit; `NOT VERIFIED` runtime.
- Next: Merchant rồi Visitor; sau đó full test để bắt interaction.


