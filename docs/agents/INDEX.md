# Agent Notes Index

Đọc file này trước khi nhận task LivingNPC.

| Workstream | Note | Agent role | Status |
|---|---|---|---|
| Movement core | `movement-core.md` | Java/architecture | IN PROGRESS |
| Gate passage | `gate-passage.md` | Java/correctness | IN PROGRESS |
| Runtime integration | `runtime-integration.md` | integration | BLOCKED BY CORE |
| Paper smoke | `paper-smoke.md` | test/evidence | PENDING |
| Citizens/Paper research | `research-citizens-paper.md` | research | ACTIVE REFERENCE |
| Decisions | `decisions.md` | lead | ACTIVE |

## Required reading order

1. `..\..\AGENTS.md`
2. `README.md`
3. Workstream note.
4. `..\CORE_MOVEMENT_JOURNAL.md`
5. Relevant `..\incidents\*.md`.

## Required write-back

Mọi agent ghi cả PASS lẫn FAIL. Không xóa lịch sử; entry mới phải nêu rõ entry cũ được supersede nếu có.

## Source of truth

- Code/tests: behavior hiện tại.
- `CURRENT_STATE.md`: operational state.
- Workstream notes: accumulated evidence and lessons.
- `CORE_MOVEMENT_JOURNAL.md`: audit timeline.
- Chat: không phải source of truth.
