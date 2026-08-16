# Incident: NPC không có route planner chuyên biệt qua fence gate

Ngày: 2026-08-14
Trạng thái: Đã sửa source và kiểm thử tự động; chưa triển khai hoặc xác minh lại trên Paper/Citizens

## Triệu chứng và bằng chứng

Trong lần kiểm thử runtime đã ghi tại `2026-08-14-route-examiner-rejects-supported-corridors.md`, Farmer thất bại trên các hành trình dài giữa nhà và ruộng với `reason=STUCK`, `path=absent`; không có bằng chứng NPC mở hoặc vượt fence gate. Citizens phát `NPCOpenGateEvent` riêng cho fence gate, nên luồng double-door không cung cấp điều phối route qua gate.

## Nguyên nhân gốc

LivingNPC giao toàn bộ hành trình dài trực tiếp cho một lần `Navigator.setTarget(Location)`. Plugin vẫn cấu hình Citizens `PathfinderType.CITIZENS` và `DoorExaminer`, nhưng không chọn fence gate phù hợp hoặc chia hành trình thành các leg ngắn có thể retry độc lập. Khi route qua một gate thất bại, runtime chỉ retry lại toàn bộ target và không có candidate policy hữu hạn.

## Bản sửa

- Thêm discovery có giới hạn để tìm fence gate trong corridor quanh đoạn current → final target; chỉ đọc chunk đã load, tối đa 16 candidate và không force-load.
- Xếp candidate theo tổng detour, tạo approach/exit ở hai phía gate dựa trên hướng gate và vị trí NPC.
- Thêm `GateRoutePlan` thuần policy: mỗi candidate chỉ được cấp đúng một lần; exhausted là trạng thái rõ ràng.
- Thêm coordinator dùng chung cho các leg `APPROACH → EXIT → FINAL`; chỉ chuyển leg khi NPC thật sự đạt target theo khoảng cách ngang và dung sai Y.
- Citizens tiếp tục pathfind từng leg bằng `Navigator.setTarget(Location)`, `PathfinderType.CITIZENS` và `DoorExaminer`.
- Theo dõi `NPCOpenGateEvent` như intent/telemetry; event mở gate không tự hoàn tất leg.
- Tích hợp Farmer và luồng vào khu chăn nuôi của Rancher qua cùng abstraction; target entity động của Rancher giữ logic Citizens hiện hữu.
- Timeout/failure/exhaustion hủy navigation tạm và backoff. Suspend, removal, role change, reload và disable hội tụ vào cleanup runtime hiện hữu để hủy coordinator.
- Không thêm schema/persistence, custom A*, teleport recovery, timer mở gate, scan không giới hạn hoặc force-load chunk.

## Regression test

- `GateRouteTest`: transition chỉ theo vị trí leg, gate event không đồng nghĩa đã đi qua, sai tầng bị từ chối, candidate cấp đúng một lần và exhausted.
- `GateRouteCoordinatorTest`: chuyển leg, timeout retry candidate kế, exhaustion, completion và cancel cleanup navigation.
- `GateRouteDiscoveryTest`: không đọc block trong chunk chưa load và xếp candidate theo detour.

## Xác minh

- Focused gate/Farmer/Rancher tests: pass.
- `./gradlew.bat clean test build --console=plain`: `BUILD SUCCESSFUL` trong 13 giây.
- Toàn suite: 64 suite, 228 test, 0 failure, 0 error, 0 skipped.
- `git diff --check`: pass; chỉ có cảnh báo line-ending LF/CRLF hiện hữu.
- JAR local: `build/libs/living-npc-0.6.0-rc.2.jar`.
- Không deploy, thay JAR, gửi lệnh, dừng hoặc restart Paper server đang chạy.
- Runtime Paper/Citizens vẫn chưa xác minh. Cần kiểm tra có kiểm soát rằng Farmer và Rancher chọn đúng gate, lần lượt đạt approach/exit/final, Citizens phát `NPCOpenGateEvent`, mỗi candidate không bị thử lặp, exhaustion cleanup/backoff và không có lỗi `DoorExaminer` hoặc watchdog loop.

## Hậu kiểm fail-closed ngày 2026-08-15

Review độc lập sau khi bổ sung signed-plane crossing và restart leg phát hiện năm đường lỗi trong source local:

- `serverTick + timeoutTicks` có thể tràn `long`, khiến leg mới bắt đầu bị timeout ngay.
- `NaN`/infinity lọt qua validation margin hoặc waypoint, dẫn đến timeout mơ hồ hay false positive khoảng cách.
- Hai lần gọi coordinator trong cùng một server tick có thể thỏa exit dwell hai mẫu.
- Candidate ngắn hoặc thoái hóa không thể đồng thời cung cấp entry/exit clearance nhưng trước đó vẫn được nhận.
- Citizens dừng navigation sớm có thể kích hoạt `navigation.start()` lặp không giới hạn trong deadline của một leg.

Nguyên nhân gốc là deadline được biểu diễn bằng phép cộng tuyệt đối, validation chỉ dùng so sánh thứ tự với số thực, dwell đếm poll thay vì tick, record candidate chưa bảo vệ invariant hình học, và restart không có quota riêng theo leg.

Bản sửa nhỏ nhất giữ fail-closed:

- Lưu tick bắt đầu và so elapsed để an toàn qua wrap của `long`; restart không reset deadline.
- Yêu cầu margin, vertical tolerance và toàn bộ tọa độ waypoint hữu hạn.
- Chỉ tăng exit confirmation khi `serverTick` khác tick xác nhận gần nhất.
- Từ chối candidate khác world, không hữu hạn hoặc có khoảng cách ngang nhỏ hơn hai lần exit clearance.
- Cho phép tối đa một rescue restart trên mỗi leg; dừng lần hai hoặc restart thất bại sẽ loại candidate và tiếp tục candidate kế tiếp.

Các regression được chạy RED trước sửa rồi GREEN sau sửa, bao gồm deadline gần `Long.MAX_VALUE`, số thực không hữu hạn, poll lặp cùng tick, candidate ngắn/thoái hóa và quota restart. Focused GateRoute suite pass; review đọc-only cuối trả về `PASS`, không có finding correctness mới. Full `./gradlew.bat clean test build --console=plain` pass với 70 suites, 266 tests, 0 failure/error/skip. Candidate local có SHA-256 `1A7E1DB69B2079A15700C4D9D6D7E206F3F7B0405AE5703BB01E30E87D18EF3F`; JAR live vẫn là `0E6238CF7E5EE32452F53C4A21F4097D2A9D5DCD8CA049CB4C18B901115C414F`. Không deploy, thay JAR, gửi lệnh, dừng hoặc restart Paper; runtime FenceGate vẫn `INCONCLUSIVE`.
