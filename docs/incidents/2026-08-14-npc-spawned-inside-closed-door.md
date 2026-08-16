# Incident: NPC spawn chồng cửa đóng không thể tiếp tục navigation

Ngày: 2026-08-14
Trạng thái: Đã sửa source và kiểm thử tự động; đã xác minh hai nguyên nhân đầu trên Paper/Citizens; artifact cuối chưa được triển khai vì server được launcher ngoài phiên khởi động lại và có người chơi thật

## Triệu chứng tái hiện

Runtime được kích hoạt bằng bot Mineflayer, không cần người chơi thật. Trên Paper 1.21.11 và Citizens 2.0.42 build 4173:

- Steve (`35d40d2f-eac9-464e-a45d-4d5576729903`, Citizens ID 17) được persist tại `StillCliff:1.30,-56.4375,2.4875`, chồng hitbox với cửa đôi spruce đóng ở block Y `-57`.
- Navigation `GOING_TO_PLOT_STAGE` lặp timeout sau 400 tick tại cùng vị trí.
- Không có `NPCOpenDoorEvent` hoặc `NPC_DOOR` trước bản sửa recovery.
- Alaric (`df648175-d295-4e6e-a9f6-eefb0f43ff2f`) vẫn hoàn thành navigation tới plot, nên lỗi được cô lập vào trạng thái Steve chồng cửa thay vì activation bot hoặc tick manager nói chung.

Testcase Paper có kiểm soát dùng `npc select 17` và `npc moveto 1.30 -57 2.4875 StillCliff` trong khi navigation đang active. Không dùng `/reload`, PlugMan, force-load chunk hoặc teleport recovery trong mã plugin.

## Nguyên nhân gốc

Có ba điều kiện kết hợp:

1. Citizens `DoorExaminer` chỉ phát `NPCOpenDoorEvent` từ callback của path node. Khi NPC bắt đầu đã chồng/xuyên cửa, node cửa nằm phía sau điểm bắt đầu và callback không chạy; listener không có cơ hội mở cửa.
2. Recovery ban đầu đưa NPC quay lại điểm approach ở phía trước cửa. NPC đã chồng cửa đóng không thể pathfind ngược qua chính collision đang giữ nó, nên passage timeout.
3. Điều kiện hoàn tất passage dùng khoảng cách 3D tới tâm block cửa. Citizens báo chân entity ở Y `-56.4375` trong khi target tâm block có Y `-57.0`; độ lệch `0.5625` làm khoảng cách 3D luôn vượt margin `0.3`, dù X/Z đã đạt. Vì vậy runtime đã ra khỏi cửa và tiếp tục tới stage nhưng passage vẫn kết thúc `ABORTED_TIMEOUT` thay vì `RESUMED`.

## Bản sửa

- Trong tick trung tâm hiện hữu, chỉ xét các NPC do LivingNPC quản lý, đã spawn, đang navigation và chưa có passage active.
- Mỗi NPC chỉ kiểm tra các voxel giao với hitbox hiện tại (tối đa 12 voxel), không quét chunk/world và không tạo scheduler mới.
- Nếu hitbox giao một cửa đóng, đưa cửa đó vào state machine hiện hữu với marker `RECOVERY_INTERSECTING_CLOSED_DOOR`.
- Recovery bỏ qua target approach không thể tới, giữ quyền sở hữu navigation target phía sau cửa, dừng vận tốc và vào thẳng `WAITING_TO_OPEN`.
- Điều kiện đạt target passage dùng khoảng cách ngang tối đa `0.3` block và dung sai đứng tối đa `0.75` block. Dung sai bao phủ offset Citizens quan sát được `0.5625`, nhưng vẫn loại vị trí sai tầng.
- Luồng event cửa bình thường giữ nguyên hành vi.

## Regression test

`DoubleDoorListenerTest` bổ sung:

1. `findsClosedDoorIntersectingNpcHitboxButNotNearbyDoor`: tìm cửa đóng giao hitbox và bỏ qua cửa chỉ ở gần.
2. `recoveryInsideClosedDoorSkipsUnreachableApproachTarget`: recovery chọn phía sau cửa thay vì target approach.
3. `passagePointAllowsCitizensVerticalFootOffsetButRejectsWrongFloor`: chấp nhận offset Y thực tế của Citizens và từ chối sai tầng.

Mỗi test mới được chạy RED trước khi triển khai helper/policy tương ứng, sau đó GREEN.

## Xác minh

- Focused: `./gradlew.bat test --tests vn.heomc.livingnpc.DoubleDoorListenerTest --console=plain` — pass.
- Full: `./gradlew.bat clean test build --console=plain` — `BUILD SUCCESSFUL`.
- `git diff --check` — pass; chỉ có cảnh báo line-ending đã tồn tại trong working tree.
- Runtime Paper vòng 1: luồng cửa bình thường của Steve hoàn tất `APPROACH_WAIT → CENTERED_WAITING_TO_OPEN → OPEN_WAIT → CROSSING → RESUMED`; Alaric hoàn tất navigation tới plot.
- Runtime Paper vòng 2: testcase Citizens ID 17 phát `RECOVERY_INTERSECTING_CLOSED_DOOR`, chứng minh detector mới chạy; phát hiện và sửa việc recovery pathfind ngược về approach.
- Runtime Paper vòng 3: Steve được đưa ra khỏi cửa và tiếp tục tới stage; passage vẫn `ABORTED_TIMEOUT`, qua đó phát hiện và sửa lỗi khoảng cách 3D/Y offset.
- Artifact cuối có SHA-256 `eb6effce5363cd8fcd3aafd9b940364fbc2539f7b680fa30bd7b0ff1c5ccef16`.
- JAR đang nằm trong server có SHA-256 `99af0e1bd88f906809ee4d17ad1cde750de2ae8f007c5786298bf36a84d6e48b`, nên chưa phải artifact cuối.

## Trạng thái runtime cuối

Trước vòng triển khai cuối, server được một launcher ngoài phiên tự khởi động lúc `2026-08-14 20:13:39 +07:00` bằng `-Xms2G -Xmx6G`; log có người chơi thật `ThanhRedfield`. Không dừng server, không thay JAR dưới JVM đang chạy và không hot-reload. Vì vậy chuỗi `RECOVERY_* → OPEN_WAIT → CROSSING → RESUMED` cho artifact cuối vẫn cần được xác minh trong một cửa sổ restart/deploy được kiểm soát.
