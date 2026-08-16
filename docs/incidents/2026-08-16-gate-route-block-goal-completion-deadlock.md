# Gate route bế tắc vì đo hoàn tất leg sai hệ tọa độ

## Triệu chứng

Trong phiên Paper ngày 2026-08-16 (`F:/minecraftserver/villagedefense2026/logs/latest.log`,
server khởi động lúc `17:55:14`, plugin `living-npc-0.6.0-rc.2.jar`), Farmer `Steve`
`35d40d2f-eac9-464e-a45d-4d5576729903` không bao giờ rời khỏi `StillCliff:59,-60,-1`.

Từ `18:10:51` đến hết phiên, NPC lặp vô tận chu kỳ khoảng 1 giây:

```text
NPC_ACTION ... name="Steve" role=farmer phase=INACTIVE->GOING_TO_PLOT navigation=false->true
           pos=StillCliff:59,-60,-1 target=StillCliff:60,-60,-1 targetDistance=1.30
NPC_NAV_END ... operation=GOING_TO_PLOT_GATE reason=COMPLETED
           current=StillCliff:59.2656,-60.0000,-0.8936 target=StillCliff:60.5000,-60.0000,-0.5000
           horizontal=1.2956 distanceMargin=0.7500 pathMargin=0.7500
           path=present strategy=AStarNavigationStrategy elapsedTicks=1
NPC_ACTION ... name="Steve" role=farmer phase=GOING_TO_PLOT->INACTIVE navigation=true->false
           pos=StillCliff:59,-60,-1 target=none
```

Farmer `Alaric` trên tuyến không có gate vẫn đi bình thường
(`operation=GOING_TO_PLOT reason=PLUGIN ... horizontal=15.8528 path=present elapsedTicks=380`),
nên Citizens không hỏng toàn cục.

## Nguyên nhân gốc

Citizens `VectorGoal` lượng tử hóa target sang tọa độ block bằng `getBlockX/Y/Z`. Với leg
target `(60.5, -60, -0.5)` thì block-goal là `(60, -60, -1)`.

- Khoảng cách từ `(59.2656, -60, -0.8936)` tới block-goal là `0.7421`, nhỏ hơn hoặc bằng
  `distanceMargin=0.75`, nên Citizens kết thúc navigation với `reason=COMPLETED` ngay
  `elapsedTicks=1`, tức NPC đã đứng sẵn trong vùng đích và không hề di chuyển.
- `GateRoute.reached` lại đo tới tọa độ tâm block `(60.5, -60, -0.5)`, được `1.2956`, lớn hơn
  `0.75`, nên `advanceIfReached` từ chối chuyển leg.

Hai phép kiểm tra dùng hai tâm khác nhau, đúng cùng lớp lỗi đã ghi trong
`docs/incidents/2026-08-14-farmer-stage-completion-coordinate-mismatch.md`. Bản sửa lần đó chỉ
áp dụng cho `FarmerRuntime.navigationTargetReached`; kiến trúc gate-aware về sau thêm
`GateRoute.reached` và lặp lại chính sai lệch đó cho leg `APPROACH`/`EXIT`/`FINAL`.

Hệ quả là bế tắc tất định, không phải chậm trễ:

1. `GateRouteCoordinator.tick` thấy `advanceIfReached` sai và `navigation.navigating()` sai.
2. Cho phép đúng một lần `restartLeg`, Citizens lại `COMPLETED` sau 1 tick tại đúng vị trí cũ.
3. Lần kế tiếp `legRestartCount >= MAX_LEG_RESTARTS`, candidate bị loại, plan cạn, trả `FAILED`.
4. `FarmerRuntime.navigationResult` chuyển phase về `INACTIVE` và đặt backoff; vòng lặp lặp lại.

Việc thắt `distanceMargin` xuống `0.75` cho leg crossing (ghi trong `CURRENT_STATE.md`) không
sửa được lỗi này, chỉ thu nhỏ vùng Citizens chấp nhận; sai lệch tâm đo vẫn còn nguyên.

Tuyến dài khoảng `41.67` block và ngân sách A* `maximum-search-blocks: 1024` không phải nguyên
nhân của triệu chứng này. Kiến trúc gate-aware đã chia tuyến thành các leg ngắn và leg đầu tiên
chỉ dài `1.30` block; NPC bế tắc trước khi bất kỳ leg dài nào được khởi động. Log cùng phiên vẫn
cho thấy `STUCK path=absent elapsedTicks=4` trên các tuyến `55`–`126` block không có gate, đó là
giới hạn tầm A* riêng biệt và không được xử lý trong bản sửa này.

## Bản sửa

`GateRoute.reached` chấp nhận vị trí nằm trong margin quanh **một trong hai** tâm: tọa độ target
đã cấu hình, hoặc block-goal mà Citizens thực sự dùng. Kiểm tra sai lệch dọc áp dụng cho từng tâm
tương ứng.

Không thay đổi ngân sách A* toàn cục, không teleport, không force-load chunk, không thêm scan.
Bằng chứng crossing vẫn do `signedGateProgress` bảo đảm: leg `APPROACH` vẫn buộc NPC ở phía vào
(`progress <= -0.3`), leg `EXIT` vẫn buộc vượt mặt phẳng gate (`progress >= 0.3`) và xác nhận trên
hai server tick khác nhau. Bản sửa nằm trong `GateRoute` nên áp dụng cho cả Farmer và Rancher.

## Regression test

- `GateRouteTest.approachAdvanceKhiCitizensHoanTatTaiBlockGoalCuaTargetTrungTam` dùng nguyên tọa độ
  runtime của Steve và yêu cầu leg chuyển `APPROACH -> EXIT`.
- `GateRouteCoordinatorTest.citizensHoanTatTaiBlockGoalChuyenLegThayViLapLaiApproach` chứng minh
  coordinator phát lệnh navigation tới `exit` thay vì khởi động lại `approach`.

`GateRouteCoordinatorTest.citizensCompleteOutsideEffectiveCrossingMarginKhoiDongLaiApproach` được
sửa tọa độ. Vị trí cũ `(41.4845, 64, -13.3854)` chỉ cách block-goal `0.6191`, tức là vị trí mà
Citizens đã xác nhận hoàn tất; khẳng định "khởi động lại approach" tại đó chính là hành vi bế tắc
nói trên. Vị trí mới `(41.4845, 64, -14.3854)` nằm ngoài margin quanh cả hai tâm, nên vẫn kiểm tra
đúng ý định ban đầu là loại kết quả hoàn tất sớm thật sự.

## Verification

RED trước khi sửa:

```text
.\gradlew.bat test --tests vn.heomc.livingnpc.GateRouteTest --tests vn.heomc.livingnpc.GateRouteCoordinatorTest --console=plain
GateRouteCoordinatorTest > citizensHoanTatTaiBlockGoalChuyenLegThayViLapLaiApproach() FAILED
GateRouteTest > approachAdvanceKhiCitizensHoanTatTaiBlockGoalCuaTargetTrungTam() FAILED
22 tests completed, 2 failed
BUILD FAILED in 8s
```

GREEN sau khi sửa:

```text
.\gradlew.bat test --tests vn.heomc.livingnpc.GateRouteTest --tests vn.heomc.livingnpc.GateRouteCoordinatorTest --tests vn.heomc.livingnpc.GateRouteDiscoveryTest --tests vn.heomc.livingnpc.FarmerNavigationPolicyTest --console=plain
BUILD SUCCESSFUL in 8s
```

Full verification:

```text
.\gradlew.bat clean test build --console=plain
BUILD SUCCESSFUL in 19s
76 suites, 340 tests, 0 failures, 0 errors, 0 skipped
```

## Trạng thái xác minh runtime

Chưa xác minh runtime. Không deploy, không restart, không sửa server live trong tác vụ này.
Bản sửa cần một controlled Paper smoke để chứng minh Steve thực sự đi qua gate tới ruộng và không
còn vòng lặp `GOING_TO_PLOT -> INACTIVE`.
