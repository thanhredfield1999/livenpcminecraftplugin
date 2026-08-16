# Project Collaboration Rules

## Language

- Luôn trao đổi với người dùng bằng tiếng Việt.
- Thực hiện phân tích, kế hoạch, báo cáo tiến độ, commit message và tài liệu kỹ thuật bằng tiếng Việt.
- Giữ tên định danh mã nguồn, API, lệnh, đường dẫn và thuật ngữ bắt buộc theo ngôn ngữ hoặc quy ước hiện có để bảo đảm tính tương thích.
- Viết comment và nội dung kiểm thử bằng tiếng Việt khi phù hợp; giữ tiếng Anh khi framework, API hoặc quy ước hiện hữu yêu cầu.
- Giữ nguyên nội dung tiếng Việt hướng đến người chơi khi chỉnh sửa mã lân cận, trừ khi tác vụ yêu cầu thay đổi nội dung đó.

## Task Types

Dự án bao gồm nhiều loại tác vụ Minecraft. Quy tắc áp dụng theo loại:

### Java Plugin (LivingNPC, custom plugin)
- Áp dụng đầy đủ Bug Fix Rule, Minecraft Safety, Persistence, Source Of Truth bên dưới.
- Full verification: `.\gradlew.bat clean test build --console=plain`.
- Unit test không chứng minh Paper/Citizens/WorldGuard/restart/performance.

### Skript (.sk)
- Đọc docs plugin Skript/addon liên quan trước khi viết.
- Test trên dev server, không trên production.
- Skript dùng skript-reflect hoặc Java bridge: áp dụng thêm Minecraft Safety.
- Không `/skript reload` trên production nếu chưa test dev server.

### Config YAML (MythicMobs, DeluxeMenus, LiteFish, LiteFarm, Oraxen, ItemsAdder, Nexo, v.v.)
- Tra docs plugin qua Firecrawl developer index hoặc wiki trước khi viết.
- Backup config trước khi sửa trên production.
- Test trên dev server trước.
- Không sửa config live khi plugin đang write vào file đó.

### Datapack / Resource Pack
- Đúng format target Minecraft version.
- Test trên dev server.

### Server Admin (Paper config, permission, economy, tuning)
- Tra Paper docs / spark profiler docs qua Firecrawl.
- Thay đổi performance-critical cần benchmark trước/sau.
- Không restart production chưa được duyệt.

## Model Routing Policy

Phân vai model theo task, không dùng một model cho tất cả:

| Vai trò | Model | Khi nào |
|---|---|---|
| Default/general/docs/task thường | `cx/gpt-5.5` | Task nhẹ, Notion update, docs, Skript/config đơn giản |
| Lead/root cause/architecture/design | `cx/gpt-5.6-sol` | Persistence, lifecycle, Citizens, WorldGuard, scheduling, concurrency, deploy planning |
| Coding/implementation chính | `cx/gpt-5.6-terra` | Code Java plugin, Skript phức tạp, feature implementation |
| Log analysis/reproduction/test design | `cx/gpt-5.6-luna` | Debug log, reproduce bug, evidence analysis, test strategy |
| Correctness review (cross-provider) | `ag/claude-sonnet-4-6` | Review code sau implementation, tách provider khỏi GPT implementer |
| Evidence/verification review | `cx/gpt-5.6-luna-review` | Review test evidence, runtime logs |
| Final critical/release review | `ag/claude-opus-4-6-thinking` | Release gate, reviewer bất đồng, cross-provider final authority |
| Quality escalation/second opinion | `ag/claude-opus-4-6-thinking` | Khi GPT fail 2 vòng hoặc user yêu cầu (thay DeepSeek flash) |
| Escalation tiếp nếu Claude cũng fail | `ds/deepseek-v4-flash` | Chỉ khi cả GPT và Claude fail |
| DeepSeek escalation cuối | `ds/deepseek-v4-pro` | Chỉ khi tất cả trên fail |

Quy tắc:
- Review model phải tách khỏi implementer. Ưu tiên cross-provider review: GPT code → Claude review.
- Claude Opus dùng cho: final review, release gate, escalation, architecture cực khó. Không dùng cho task thường.
- Claude Sonnet dùng cho: correctness review thường xuyên. Khi Opus bị rate limit, Sonnet thay thế.
- DeepSeek là escalation cuối cùng, không phải provider fallback hay coder mặc định.
- GPT provider fallback dùng GPT khác trước, không nhảy Claude/DeepSeek cho task thường.
- Không tự động dùng `ds/deepseek-v4-pro-max`, `ds/deepseek-reasoner`.
- Antigravity (`ag/*`) quota hữu hạn: chỉ dùng Claude Sonnet/Opus theo vai trò trên, không route cho task thường.
- Router chỉ đổi model, không dịch prompt. Code/path/log/error giữ nguyên văn.

### Escalation Chain

```
cx/gpt-5.5 (task thường)
  → cx/gpt-5.6-sol / terra / luna (task khó, theo vai trò)
    → ag/claude-opus-4-6-thinking (GPT fail 2 vòng, hoặc final review)
      → ds/deepseek-v4-flash (Claude cũng fail)
        → ds/deepseek-v4-pro (flash cũng fail)
```

### Review Chain (cross-provider)

```
GPT code (terra) → Claude review (sonnet) → pass/fail
Nếu fail hoặc reviewer bất đồng → Claude Opus final review
Nếu release gate → Claude Opus bắt buộc
```

## Research Policy

Thứ tự tra cứu, không nhảy bước:

1. Local repo source, tests, docs, Git history.
2. Firecrawl developer index cho programming questions (Paper API, Citizens, WorldGuard, Bukkit, Skript, plugin docs).
3. Firecrawl web search cho GitHub issues, merged PR, changelog, version compatibility.
4. DeepSeek second opinion nếu GPT fail 2 vòng.

Giới hạn mỗi task: tối đa 3 searches, 5 scrapes. Crawl cần phê duyệt user.
Không gửi secrets, private logs, player data qua Firecrawl.
Không dùng Perplexity nếu Firecrawl đủ.

## Notion Integration

Notion là lớp quản lý dự án và knowledge dashboard, không phải source of truth kỹ thuật.

### Trước khi code
- Đọc repository rules, `CURRENT_STATE.md`, risk register, source, tests, Git diff.
- Đọc Notion task liên quan chỉ để lấy scope, priority, ownership, management context.
- Khi Notion mâu thuẫn source/docs trong repo: theo repo, báo cáo conflict.

### Trong khi làm
- Cập nhật task status `In Progress` khi thật sự bắt đầu.
- Không ghi credentials, secrets, private player data, sensitive production logs vào Notion.
- Không đánh dấu runtime verified, deployed, release approved mà không có evidence cụ thể và user phê duyệt.

### Sau khi xong
- Ghi changed files, focused tests, full verification command, review result, commit/PR link, remaining risks, runtime verification status.
- Phân biệt: unit verification, controlled Paper verification, production verification.
- Git giữ technical state chính thức. Notion link về repo files/commit hashes, không duplicate thành competing source of truth.
- Không xóa/archive Notion content nếu chưa được duyệt.

## Session Startup

Before changing code:

1. Read `CURRENT_STATE.md`.
2. Read `docs/RISK_REGISTER.md` when the task touches persistence, lifecycle, Citizens, WorldGuard, configuration reload, scheduling, or deployment.
3. Read only the roadmap or historical handoff sections relevant to the task.
4. Inspect the current source, tests, and Git diff. Historical notes are evidence, not current truth.
5. Preserve unrelated user changes in the working tree.

Before cross-project integration, re-read each participating reference's
`CURRENT_STATE.md` and relevant current source and tests. Do not rely on earlier
conversation context or an older handoff. Keep references read-only unless the user
explicitly requests a cross-repository change.

## Project Baseline

- Target Paper: `1.21.11`.
- Java toolchain: `21`.
- Citizens: `2.0.42-SNAPSHOT`.
- WorldGuard: `7.0.16`.
- Full verification: `.\gradlew.bat clean test build --console=plain`.
- Production deployment or restart always requires explicit user approval.

## Bug Fix Rule

For every confirmed defect:

1. Record a reproducible symptom or concrete log/source evidence.
2. Identify the root cause rather than patching only the symptom.
3. Add a regression test that fails before the fix when practical.
4. Implement the smallest correct fix.
5. Run focused tests, then the full verification command.
6. Update `CURRENT_STATE.md` only when current operational state changes.
7. Record production-impacting defects in `docs/incidents/` with symptom, root cause, fix, regression test, and runtime verification status.

Do not claim a Paper, Citizens, WorldGuard, restart, or performance issue is verified by unit tests alone.

## Minecraft Safety

- Never use `/reload`, PlugMan, or another plugin hot-loader. `/lnpc reload` is only the plugin's supported configuration reload.
- Do not restart, deploy to, or modify production without explicit approval.
- Back up the plugin JAR and `plugins/LivingNPC` before an approved deployment.
- Do not edit live plugin YAML while Paper can write to it.
- Do not enable release-gated roles or runtimes as part of an unrelated change.
- Do not add unbounded world, block, entity, chunk, journal, or collection scans to a server-thread tick.
- Do not force-load chunks merely to operate NPC runtimes.
- Preserve fail-closed behavior for unavailable dependencies and corrupt or unsupported persisted data.
- Treat Citizens navigation and Bukkit world/entity access as server-thread operations unless the relevant API explicitly documents otherwise.

## Persistence And Configuration

- Persisted YAML is durable external data, not an internal implementation detail.
- Schema changes require migration, downgrade/future-version rejection, corrupt-file, and restart tests.
- Never overwrite a file with a schema newer than the running plugin supports.
- Never silently replace corrupt or unsupported persisted data.
- Preserve unknown live data unless a documented migration explicitly owns it.
- Avoid synchronous whole-file writes in frequent server-thread paths; measure before changing threading because Bukkit state is not generally thread-safe.
- Never store credentials in YAML, source, logs, prompts, or Git.
- Settings that cannot be safely applied by `/lnpc reload` must be documented and reported as restart-required.

## Source Of Truth

When sources disagree, use this precedence:

1. Current source and tests for implemented behavior.
2. `CURRENT_STATE.md` for current release and operational state.
3. Current ADRs, risk register, and release gates.
4. `README.md` for stable user/developer guidance.
5. Historical handoff, roadmap, incident, and deployment records.

Do not infer current production state from an older deployment record. Do not treat an uncommitted build artifact as approved for deployment.
