# BotChecker ThanhRedfield route timeout — 2026-08-15

## Mục đích

Xác minh live Citizens metadata/locator của NPC `ThanhRedfield` bằng BotChecker trên Paper local, với một tester duy nhất. Phiên này chỉ được phép kết nối, quan sát trạng thái, đi bộ bằng client pathfinder bounded và đọc entity stream; không dùng command, teleport, click GUI, tương tác NPC/entity, reload, restart hoặc deploy.

## Phạm vi và phiên bản

- Paper endpoint: `127.0.0.1:11619`.
- Paper process quan sát sau phiên: PID `43004`.
- Minecraft: `1.21.11`, protocol `774`.
- BotChecker API: loopback `127.0.0.1:18080`, đã dừng sau phiên.
- Tester: `HeoMC_Tester`.
- Credential/auth: không ghi nhận; giá trị nhạy cảm được giữ kín.
- Diagnostics packet/raw protocol: tắt.
- Scenario: `citizens-thanhredfield-current-area`.
- BotChecker report: `E:/AI.WORK/botcheckerminecraft-botchecker/reports/0ed4f819-c981-4ad2-b70e-0e2c3c5e55ec.json`.

## Mốc thời gian UTC

| Mốc | Thời gian |
|---|---|
| Submit request | `2026-08-15T04:21:32.630328Z` |
| Bot connected / run started | `2026-08-15T04:21:33.205Z` |
| `assert_state` bắt đầu | `2026-08-15T04:21:33.885Z` |
| `assert_state` passed | `2026-08-15T04:21:33.886Z` |
| `go_to` bắt đầu | `2026-08-15T04:21:33.886Z` |
| `go_to` timeout | `2026-08-15T04:21:58.891Z` |
| Run finished | `2026-08-15T04:21:58.893Z` |
| Tổng runtime BotChecker | `25.688 giây` |
| Paper log tester login | `01:21:33` theo đồng hồ log Paper |
| Paper log tester disconnect | `01:21:58` theo đồng hồ log Paper |

BotChecker report dùng UTC ISO; Paper `latest.log` dùng đồng hồ server hiển thị `01:21:33`–`01:21:59`. Hai chuỗi sự kiện khớp cùng phiên login/logout; không suy diễn timezone từ log ngoài các mốc đã đối chiếu.

## Kết quả từng bước

1. `state-before` — PASS, `1 ms`.
   - Health: `20`.
   - Food: `20`.
   - GUI: `closed`.
2. `waypoint-near-current-area` — FAIL, `25.005 giây`.
   - Action: `go_to`, `travel: walk`.
   - Bot xuất phát thực tế: `StillCliff (33.699277, -60.000000, -21.745995)`.
   - Waypoint: `StillCliff (50, -60, -35)`.
   - Range: `8` block.
   - Kết quả: `Timeout: waypoint-near-current-area`.
3. Assertion locator `ThanhRedfield` — KHÔNG CHẠY.
   - Vì route thất bại trước khi bot tới vùng kiểm tra.

## Đối chiếu log Paper/LivingNPC

Trong cùng cửa sổ server:

- `HeoMC_Tester` đăng nhập tại `StillCliff (33.699277, -60.0, -21.745995)`.
- Paper ghi cảnh báo `HeoMC_Tester moved too quickly! 11.188515449722097,0.0,-3.169684350153151` lúc `01:21:36`.
- `ThanhRedfield` không ở vùng fixture cũ `StillCliff (41, -61, -13)` trong lúc tester chạy. Log runtime ghi NPC đang ở khoảng `StillCliff (73–74, -60/-61, -81)`.
- Khoảng cách xấp xỉ giữa tester và NPC trong phiên này là khoảng `80` block, vượt locator/probe range `48` block.
- LivingNPC log cho thấy NPC vẫn có runtime activity (`RESTING`, `WATCHING_PLAYER`, `WANDERING`) và không bị xác nhận stale trong phiên.
- Citizens ghi lặp exception: `Cannot invoke "org.bukkit.block.Block.getType()" because "point" is null.` Các exception xuất hiện trong khoảng phiên và tiếp tục ở các thời điểm khác; cần điều tra riêng, không kết luận đây là nguyên nhân duy nhất của BotChecker timeout.
- Tester disconnect sau khi scenario dừng; không có bằng chứng tester đã thực hiện command, teleport hoặc tương tác NPC.

## Kết luận

Phiên này **FAIL có kiểm soát**, nhưng **không phải bằng chứng locator `ThanhRedfield` hỏng**. Nó thất bại ở bước di chuyển client trước assertion vì:

1. Vị trí spawn thực tế của tester là `33.7,-21.75`, khác fixture waypoint đã dự đoán.
2. Vị trí `ThanhRedfield` biến động và trong phiên này ở khoảng `73,-81`, ngoài bán kính quan sát bounded `48` block.
3. Client movement/pathfinder phát sinh timeout; Paper đồng thời ghi `moved too quickly`.
4. Citizens có lỗi `point=null` trong cùng thời gian, là một tín hiệu runtime cần phân tích riêng.

Không được đánh dấu gate “ThanhRedfield locator live-pass”. Gate hiện tại vẫn là: `Jumonka` live-pass; `ThanhRedfield` chưa live-verify trực tiếp.

## Tác động và cleanup

- Không deploy JAR LivingNPC.
- Không restart hoặc reload Paper.
- Không chỉnh live YAML.
- Không mở rộng scan/range, force-load chunk hoặc lưu raw packet.
- BotChecker API đã dừng sau run.
- Kiểm tra sau cleanup: không có listener `127.0.0.1:18080`.
- Paper vẫn lắng nghe `0.0.0.0:11619` và `[::]:11619`, PID `43004`.

## Việc cần làm tiếp theo

1. Không retry route mù với cùng fixture.
2. Thiết kế probe observer chỉ đọc để ghi nhận entity stream khi NPC thực sự nằm trong bán kính `48` block; không điều khiển vị trí bằng command/teleport.
3. Điều tra riêng Citizens `point=null` bằng stack trace/version/config hiện hành và controlled Paper evidence.
4. Điều tra movement `moved too quickly`/pathfinder route quanh khu spawn; không coi đây là lỗi locator.
5. Khi có vị trí tester/NPC ổn định và an toàn, chạy lại một smoke duy nhất rồi đối chiếu report với log theo cùng mốc UTC/server time.

## Tài liệu nguồn

- BotChecker report: `E:/AI.WORK/botcheckerminecraft-botchecker/reports/0ed4f819-c981-4ad2-b70e-0e2c3c5e55ec.json`.
- Scenario: `E:/AI.WORK/botcheckerminecraft-botchecker/scenarios/citizens-thanhredfield-current-area.json`.
- Paper log: `F:/minecraftserver/villagedefense2026/logs/latest.log`.
- Trạng thái LivingNPC tại thời điểm ghi nhận: `E:/AI.WORK/living-npc-plugin/CURRENT_STATE.md`.
- Risk register: `E:/AI.WORK/living-npc-plugin/docs/RISK_REGISTER.md`.

Báo cáo này là evidence log cho tab LivingNPC đọc lại; không thay đổi kết luận release gate và không thay thế phê duyệt deploy/runtime.
