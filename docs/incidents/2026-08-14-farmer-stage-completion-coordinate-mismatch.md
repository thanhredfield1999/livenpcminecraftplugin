# Farmer bỏ qua kết quả hoàn tất stage do lệch hệ tọa độ

## Triệu chứng

Trong kiểm thử runtime trên Paper, Farmer `Steve` hoàn tất tuyến `GOING_TO_PLOT_STAGE` theo Citizens nhưng `FarmerRuntime` không chuyển ngay sang stage cuối:

- target được cấu hình: `StillCliff:29.5000,-60.0000,-8.5000`
- vị trí hoàn tất: `StillCliff:27.9181,-60.0625,-9.9026`
- Citizens báo `reason=COMPLETED` và `path=present`
- khoảng cách tới tọa độ target tâm block là `2.1142`, lớn hơn margin `1.5`

Sau đó runtime thử lại/chuyển tuyến trong trạng thái không đồng bộ và phát sinh `STUCK`, `path=absent` trên hành trình đến ruộng.

Bằng chứng nằm trong `F:/minecraftserver/villagedefense2026/logs/latest.log` tại sự kiện `NPC_NAV_END` lúc `20:44:14` ngày 2026-08-14.

## Nguyên nhân gốc

Citizens `VectorGoal` lượng tử hóa target sang tọa độ block bằng `getBlockX()`, `getBlockY()` và `getBlockZ()`. Với target `(29.5, -60, -8.5)`, block-goal thực tế là `(29, -60, -9)`.

Citizens hoàn tất hợp lệ khi NPC nằm trong `distanceMargin` quanh block-goal. `FarmerRuntime.navigationTargetReached`, trái lại, đo lại khoảng cách tới tọa độ tâm block `.5`. Hai phép kiểm tra dùng hai tâm khác nhau, nên Farmer có thể từ chối một kết quả mà Citizens vừa xác nhận hoàn tất.

Dữ liệu chunk được đọc chỉ-đọc xác nhận vị trí dừng và stage đều nằm trên `dirt_path` liên tục; đây không phải khoảng trống hoặc farmland làm đứt graph tại vị trí dừng.

## Bản sửa

`FarmerRuntime.navigationTargetReached` dùng block-goal của target, giống phép lượng tử hóa trong Citizens `VectorGoal`, cho cả trục ngang và kiểm tra sai lệch dọc. Margin và cấu hình pathfinder không thay đổi.

## Regression test

`FarmerNavigationPolicyTest.acceptsCompletionAroundCitizensBlockGoalForCenteredTarget` dùng nguyên tọa độ từ log runtime. Test thất bại trước bản sửa và đạt sau bản sửa.

Test biên `rejectsCenteredTargetOutsideCitizensBlockGoalMargin` bảo đảm helper không nới margin ngoài block-goal của Citizens.

Focused verification:

```text
./gradlew.bat test --tests vn.heomc.livingnpc.FarmerNavigationPolicyTest --tests vn.heomc.livingnpc.LivingNavigationTest --console=plain
BUILD SUCCESSFUL
```

Full verification:

```text
./gradlew.bat clean test build --console=plain
223 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

Artifact đã build: `build/libs/living-npc-0.6.0-rc.2.jar`, SHA-256
`b2b3263eeedfcfcc8e1dd495821603038b5394878eba626c76fad579217b4f06`.

## Trạng thái xác minh runtime

Bản sửa block-goal đã được deploy và Paper khởi động thành công lúc `21:30:26`. Nó loại bỏ sai lệch khi nhận kết quả stage, nhưng phép thử runtime phát hiện thêm một defect độc lập: từ center tới plot entry còn `41.67` block, `LivingNavigation.stage` cũ trả `null`, còn Citizens vượt ngân sách A* `maximum-search-blocks: 1024` và trả `STUCK`, `path=absent` sau 4 tick.

Hiện tượng lặp lại từ `21:34:31` với target `StillCliff:69.5000,-60.0000,-9.5000`. Farmer khác vẫn đi được các tuyến ngắn 1–13 block, nên Citizens không hỏng toàn cục.

Bản sửa tiếp theo giới hạn mỗi stage ở 30 block và cho phép tạo nhiều stage tiến về target sau khi đã đi qua village center. Không tăng ngân sách A* toàn server. Runtime verification cho bản sửa nhiều stage vẫn đang chờ deploy artifact mới.

## Cập nhật 2026-08-16

Đoạn trên đã lỗi thời. Kiến trúc multi-stage cùng `LivingNavigation.stage` không còn trong source;
việc chia tuyến dài hiện do kiến trúc gate-aware (`GateRouteDiscovery`, `GateRoutePlan`,
`GateRoute`, `GateRouteCoordinator`) đảm nhiệm với các leg `APPROACH`/`EXIT`/`FINAL`. Không khôi
phục multi-stage cũ.

Cùng lớp sai lệch tọa độ đã tái xuất hiện trong `GateRoute.reached`, gây bế tắc tất định cho
Farmer `Steve` trên leg `APPROACH`. Chi tiết và bản sửa:
`docs/incidents/2026-08-16-gate-route-block-goal-completion-deadlock.md`.
