# Workstream: Gate Passage

## Owner

Java implementation + correctness review agent.

## Scope

- Door/fence gate discovery.
- Approach side and direct blockage.
- FIFO max 8 waiter.
- `NPCOpenGateEvent`, `Openable`, explicit open.
- OPEN/CROSS/EXIT/RELEASE lifecycle.
- Cancel/timeout/ALREADY_OPEN cleanup.

## Current lessons

- `requestGate` default phải fail-closed.
- `ALREADY_OPEN` vẫn giữ owner tới crossing complete.
- FIFO promotion tạo owner mới; request kế tiếp phải nhận diện owner recheck, không trả duplicate làm fail giả.
- Route phải gọi `releaseGate` ở complete/fail/cancel.
- `GatePassageService` static active là lifecycle risk, cần review.

## Implemented

- `GateRouteCoordinator` state machine.
- `DoorPassageCoordinator` FIFO.
- `GatePassageService` Openable/event/trace.
- Owner recheck sau promotion.

## Verification

- `GateRouteCoordinatorTest`: PASS.
- `DoorPassageCoordinatorTest`: PASS.
- Production gate PASS: NOT VERIFIED.

## Open defects

- Chưa có controlled FIFO smoke 3 NPC.
- Chưa có đủ log `APPROACH`, `OPEN`, `CROSS`, `EXIT` trên production.
- Chưa chứng minh geometry chọn đúng gate theo intent.

## Review: DoubleDoorListener

- `APPROACHING` chỉ đổi target sang `sides.after()` khi `passageTargetState` xác nhận đã tới approach.
- `WAITING_TO_OPEN` giữ velocity zero, chờ bounded ticks, rồi gọi event trước mutation.
- `WAITING_TO_CROSS` chờ block state update trước `CROSSING`.
- `CROSSING` hoàn tất khi target exit đạt; cleanup đóng cửa, restore parameters/target và release lease.
- Preemption không restore/cancel navigation của owner mới; đã có regression test.
- Timeout/despawn/world mismatch/exception đều teardown fail-safe và release lease.
- Kết luận: state machine source/test hợp lệ; Paper runtime gate passage vẫn `NOT VERIFIED`.

## Entry log

### 2026-08-18 — FIFO owner recheck

- Symptom: owner promoted bị `DUPLICATE`.
- RCA: service gọi request lại dù coordinator đã promote owner.
- Fix: `owns(npc,key)` recheck trước request.
- Focused tests: PASS.
- Full build sau fix: PENDING.
- Status: `NOT VERIFIED` runtime.

