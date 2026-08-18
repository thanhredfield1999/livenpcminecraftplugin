# Nghiên cứu Anticheat hybrid — 2026-08-19

## Phạm vi

Mục tiêu: nghiên cứu hệ thống chống gian lận cho Paper `1.21.11`, Java `21`, kết hợp plugin server và client verifier bắt buộc trước khi vào server. Đây là tài liệu nghiên cứu, chưa phải quyết định triển khai và chưa sửa source LivingNPC.

## Bằng chứng local

- `CURRENT_STATE.md`: LivingNPC target Paper `1.21.11`, Citizens `2.0.42-SNAPSHOT`, Java `21`; plugin hiện có lifecycle `onEnable`/`onDisable`, tick task, config migration, telemetry JSON và runtime stop coordinator.
- `build.gradle.kts`: chỉ có Paper API, Citizens, WorldGuard, BlueMap; chưa có ProtocolLib/PacketEvents và chưa có module client.
- `plugin.yml`: LivingNPC `depend: [Citizens]`, chưa có anticheat entry point.
- `MovementService.java`: movement domain hiện phục vụ NPC Citizens, không phải player movement. Không được tái sử dụng trực tiếp cho anticheat nếu chưa tách contract.
- `NpcTelemetryCollector.java`: có pattern bounded telemetry, immutable snapshot và async export; có thể tham khảo cho evidence pipeline, không dùng làm bằng chứng player cheating nếu chưa định nghĩa schema riêng.
- `docs/RISK_REGISTER.md`: nhấn mạnh main-thread safety, bounded work, lifecycle cleanup, fail-closed persistence và giới hạn unit test so với Paper runtime.

## Nguồn web và kết luận

### Spigot thread user cung cấp

URL: `https://www.spigotmc.org/threads/how-to-develop-an-minecraft-anti-cheat.621673/`

Trang yêu cầu đăng nhập nên extractor chỉ xác nhận tiêu đề/thread, không đủ nội dung để làm nguồn kỹ thuật.

### Snowiiii AntiCheat guide

URL: `https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7`

Nguồn cộng đồng, dùng làm hướng dẫn/giả thuyết, không thay official API contract. Các điểm liên quan:

- Packet-level processing có thêm dữ liệu và timing so với Bukkit event; nên có preprocessor cập nhật player state trước checks.
- Tách preprocessor, check và action; không dồn toàn bộ check vào một listener.
- Buffer nhiều vi phạm thay vì kick từ một flag; lag, teleport, plugin velocity và môi trường tạo false positive.
- Setback cần lưu vị trí hợp lệ của server; không phải check nào cũng nên setback.
- Server-side vẫn cần thiết kể cả khi có client-side; client có thể bị giả mạo, bypass hoặc không chạy.
- Có thể dùng Bukkit API cho command/permission và packet library cho dữ liệu movement/combat.

### Grim Anticheat

URLs: `https://grim.ac`, `https://hangar.papermc.io/GrimAnticheat/GrimAnticheat`

Grim quảng bá mô phỏng movement khả dĩ của client, lag compensation, world replication và xử lý đa luồng. Kết luận áp dụng: nên ưu tiên server-authoritative movement simulation, không dựa riêng vào heuristic tốc độ đơn giản. Không copy code/API khi chưa xác minh license, version và compatibility.

### ProtocolLib

URLs: `https://www.protocollib.com`, `https://github.com/dmulloy2/ProtocolLib/releases`

ProtocolLib hỗ trợ intercept/read/write packet và release notes có hỗ trợ `1.21.11`. Tuy nhiên đây là dependency ngoài, phải pin version đã kiểm chứng; packet ID không được hardcode vì thay đổi theo protocol. Cần chọn ProtocolLib hay PacketEvents bằng spike compatibility trước khi đưa vào plugin.

### Paper

Paper docs cho biết có packet rate limit và `moved-too-quickly-multiplier`; đây là baseline bảo vệ server, không thay anticheat. Paper API event, scheduler và world/entity state vẫn phải tuân thủ main-thread contract. Packet pipeline không được tự ý gọi Bukkit world API từ Netty thread.

### Client-side verifier

Minecraft Java hỗ trợ `custom_payload`/plugin channel ở Configuration và Play state. Kênh này phù hợp để handshake client mod với server, nhưng không tự chứng minh binary client là chính thức. Client có thể gửi payload giả nếu attacker tự viết client/mod giả.

Nguồn tham khảo: Minecraft protocol packet table, PaperMC forum client-side anticheat discussion, ADM Anticheat listing. ADM/Forge không phải compatibility proof cho Paper Java 1.21.11.

## Kết luận kỹ thuật

1. Không thể coi “bắt buộc cài phần mềm client” là bằng chứng tuyệt đối. Nó chỉ nâng chi phí bypass và cung cấp telemetry/attestation bổ sung.
2. Server phải là authority cho movement, combat, inventory, block interaction và economy. Client report chỉ là signal, không được tự quyết định ban.
3. Client verifier nên là module/mod riêng, không nhúng vào LivingNPC. Server plugin anticheat nên là plugin riêng hoặc module độc lập; không trộn vào NPC lifecycle.
4. Handshake phải challenge-response theo phiên:
   - server tạo nonce ngẫu nhiên mới mỗi connection;
   - client trả protocol version, build ID, nonce binding và chữ ký/attestation;
   - server chống replay bằng nonce expiry, connection binding và sequence;
   - TLS chỉ bảo vệ đường truyền, không chứng minh client binary.
5. Không thu thập file, process list, credential, token, nội dung riêng tư hoặc scan máy quá mức. Client verifier phải công bố dữ liệu thu thập và cơ chế uninstall.
6. Enforcement ban đầu: `OBSERVE` và `REQUIRE_VERIFIED` theo cohort. Chưa kick/ban tự động dựa một check.

## Kiến trúc đề xuất

- `anticheat-core`: immutable player state, tick clock, buffer/violation level, exemptions, check result, evidence schema; pure Java test.
- `anticheat-paper`: join/configuration lifecycle, Bukkit events, teleport/velocity exemptions, permissions, commands, action executor trên main thread.
- `anticheat-packets`: adapter ProtocolLib hoặc PacketEvents; preprocessor packet state; version pin và integration test.
- `anticheat-client-contract`: protocol schema, challenge/response, version negotiation, timeout và failure codes; không chứa platform-specific client code.
- `client-verifier`: Fabric/NeoForge/launcher component do repo riêng quản lý; ký artifact và phát hành checksum/signature.
- `evidence-store`: bounded, redactable, rate-limited evidence; không ghi full packet stream lâu dài.

## MVP checks

1. Verification gate: chưa verified thì giữ ở configuration/login gate hoặc kick với hướng dẫn tải client.
2. Illegal packet: malformed, impossible values, unexpected sequence, excessive packet rate.
3. Movement baseline: finite coordinates, illegal pitch, impossible delta, timer/blink, horizontal/vertical simulation với lag/velocity/environment compensation.
4. Interaction consistency: reach/raycast, impossible digging/use, inventory transaction consistency.
5. Combat phase sau: rotation/reach/attack sequence; cần simulation và test bot, không dùng one-shot heuristic.

## Acceptance criteria sơ bộ

- Non-client hoặc client sai protocol không vào được server khi policy `REQUIRE_VERIFIED`.
- Replay response cũ bị từ chối.
- Timeout/mất client fail-closed theo policy, không làm treo main thread.
- Teleport, knockback, vehicle, elytra, liquid, powder snow, piston/slime và plugin velocity tạo exemption đúng, không flag giả.
- Mỗi violation có check ID, tick, latency context, exemption context, score và evidence redaction.
- Không có world/chunk scan không bounded trong tick.
- `onDisable` hủy listener/task, flush bounded evidence và không còn mutation sau stop.
- Unit test không được báo Paper/Citizens runtime đã verified; cần controlled Paper test riêng.

## Rủi ro cần chốt trước code

- Có chấp nhận chỉ Java Edition client mod không? Fabric, NeoForge hay launcher riêng?
- Người chơi có bắt buộc cài phần mềm ngoài Minecraft launcher không?
- Có cần hỗ trợ Lunar/Badlion/OptiFine/Sodium và mod gameplay hợp lệ không?
- Chính sách `OBSERVE`, `REQUIRE_VERIFIED`, `KICK`, `BAN`; ai duyệt ban?
- Có backend licensing/attestation server không? Nếu không, chỉ có protocol handshake, không có trusted attestation.
- Chọn ProtocolLib hay PacketEvents sau spike trên Paper `1.21.11`.
- Anticheat là plugin riêng hay module trong LivingNPC. Khuyến nghị plugin riêng, chỉ tích hợp API event với LivingNPC nếu cần.

## Nguồn

- https://www.spigotmc.org/threads/how-to-develop-an-minecraft-anti-cheat.621673/
- https://gist.github.com/Snowiiii/2c306f3e8926bc7fb8acaaa8c3c105d7
- https://grim.ac
- https://hangar.papermc.io/GrimAnticheat/GrimAnticheat
- https://www.protocollib.com
- https://github.com/dmulloy2/ProtocolLib/releases
- https://docs.papermc.io/paper/reference/global-configuration
- https://minecraft.wiki/w/Java_Edition_protocol/Packets
- https://forums.papermc.io/threads/anti-cheating-on-the-client-side.491/

## Trạng thái

- Research: hoàn tất vòng 1.
- Design: chưa chốt.
- Code: chưa bắt đầu.
- Runtime/deploy: chưa thực hiện.
- LivingNPC source: chỉ đọc; chưa chỉnh sửa.
- Production: không đụng tới.

## Model routing đã xác nhận

- Implementation Java: `cx/gpt-5.6-terra`.
- Architecture/RCA: `cx/gpt-5.6-sol`.
- Test/log: `cx/gpt-5.6-luna`.
- Review: `cc/claude-sonnet-4-6`.
- Final/release gate: `cc/claude-opus-4-6`.
