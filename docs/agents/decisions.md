# Technical Decisions Log

## Rules

Quyết định chặn code phải ghi trước khi phase tiếp theo bắt đầu. Khi source/runtime mâu thuẫn note, source/tests hiện tại thắng; phải ghi conflict.

## D-001 — Movement authority

- Date: 2026-08-18
- Decision: Citizens Navigator là movement engine cho Player NPC; LivingNPC là authority về intent, route, gate, arrival và recovery.
- Reason: Paper Pathfinder chỉ áp dụng Mob.
- Status: ACCEPTED.

## D-002 — One target per leg

- Decision: Không gọi `setTarget` mỗi tick. Mỗi leg set một target; chỉ restart khi navigation complete/fail/stuck/timeout.
- Reason: target mới thay navigation cũ và tạo `REPLACE`.
- Status: ACCEPTED.

## D-003 — Gate state machine riêng

- Decision: Gate dùng APPROACH → FIFO → OPEN → CROSS → EXIT → RELEASE. Không coi gần gate là đã mở.
- Status: ACCEPTED; runtime PASS pending.

## D-004 — Fail closed

- Decision: unknown gate, missing block, invalid target, unavailable dependency và corrupt data đều fail closed.
- Status: ACCEPTED.

## D-005 — No production claim from unit tests

- Decision: Unit test chỉ chứng minh Java logic. Paper/Citizens/gate/chunk/restart phải controlled smoke có evidence.
- Status: ACCEPTED.

## D-006 — Scope order

- Decision: CORE movement PASS trước storage, skin và feature mở rộng.
- Status: ACCEPTED.

## Entry log

### 2026-08-18

- All decisions above recorded from source/research/runtime evidence.
- No production CORE PASS yet.
