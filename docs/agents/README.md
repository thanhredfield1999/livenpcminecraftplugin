# LivingNPC Agent Workstreams

Mục đích: mỗi agent làm đúng một workstream, đọc note trước khi làm, ghi lại thành công/thất bại/RCA/test sau khi làm.

## Cách dùng

1. Agent đọc `AGENTS.md`.
2. Agent đọc `docs/agents/INDEX.md`.
3. Agent đọc note workstream được giao.
4. Agent đọc incident liên quan trước khi sửa.
5. Agent chỉ sửa đúng scope.
6. Agent ghi kết quả vào note workstream và `docs/CORE_MOVEMENT_JOURNAL.md`.
7. Agent không tự deploy production, commit, reset hoặc xóa unrelated changes.

## Workstream

- `movement-core.md`: Citizens navigation, waypoint, staircase, arrival, recovery. Agent: Java implementation / architecture review.
- `gate-passage.md`: door/fence gate, FIFO, Openable, approach/cross/exit/release. Agent: Java implementation / correctness review.
- `runtime-integration.md`: gom call path Farmer/Rancher/Fisher/Civil/Visitor/Merchant/Combat. Agent: integration implementation.
- `paper-smoke.md`: build artifact, controlled Paper smoke, log evidence. Agent: test/evidence review.
- `research-citizens-paper.md`: GitHub/issues/API research, version caveat, source links. Agent: research.
- `decisions.md`: quyết định kiến trúc, scope, trade-off, approval. Agent: lead/orchestrator.

## Quy tắc bàn giao

Agent sau không tin kết luận miệng. Agent sau đọc note và kiểm tra lại source/test/log nếu claim ảnh hưởng code hoặc runtime.

Mỗi entry phải có:

- Date/time.
- Agent/model/role.
- Scope.
- Evidence.
- Result: `PASS`, `FAIL`, `BLOCKED`, `NOT VERIFIED`.
- RCA.
- Files/tests changed.
- Next action.

Production claim phải có artifact hash, backup path, process/log timestamp và controlled smoke evidence.

## Agent routing hiện tại

- Architecture/RCA: `cx/gpt-5.6-sol`.
- Java implementation: `cx/gpt-5.6-terra`.
- Log/test/evidence: `cx/gpt-5.6-luna`.
- Cross-provider code review: `ag/claude-sonnet-4-6` khi cần user approval.
- Release gate/final review: `ag/claude-opus-4-6-thinking` khi cần.

Tên model là routing policy; không phải dependency chạy trong plugin.
