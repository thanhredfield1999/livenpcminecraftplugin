# Incident: bộ lọc route loại nhầm hành lang có nền

Ngày: 2026-08-14
Trạng thái: Đã sửa source và kiểm thử đơn vị; chưa xác minh lại trên Paper/Citizens thực tế

## Triệu chứng

Trong lần kiểm thử runtime với Paper 1.21.11 và Citizens 2.0.42 build 4173, cả hai Farmer đều thất bại trên hành trình dài giữa nhà và ruộng:

- Steve (`35d40d2f-eac9-464e-a45d-4d5576729903`, Citizens ID 17): 26 lần `GOING_TO_PLOT` bị `STUCK`, từ `StillCliff:-4.5,-57,8.5` đến `StillCliff:69.5,-60,0.5`.
- Alaric (`df648175-d295-4e6e-a9f6-eefb0f43ff2f`, Citizens ID 16): 24 lần bị `STUCK`, chủ yếu khi `GOING_HOME`, từ khoảng `StillCliff:60.9873,-60,-13.075` đến `StillCliff:-6.5,-57,8.5`.

Cả hai trường hợp đều có:

- `reason=STUCK`
- `path=absent`
- `range=100`

Khoảng cách ngang lần lượt khoảng `74.4312` và `70.8521`, vẫn nằm trong range nên không phải lỗi do vượt navigation range.

Cùng cửa sổ runtime có 23 lỗi Citizens `DoorExaminer.java:148` cho Alaric, với `point` là `null`. Steve không có lỗi Citizens tương ứng. Vì vậy NPE thuộc nhánh runtime của Alaric, dù chưa đủ bằng chứng để kết luận nó là nguyên nhân thay vì hậu quả của path thất bại.

Không có log `NPC_DOOR` hoặc bằng chứng runtime rằng Steve hay Alaric đã mở hoặc vượt fence gate trong hành trình lỗi.

## Nguyên nhân gốc trong LivingNPC

`VillageRouteExaminer.getNeighbours(...)` dùng `isUnsupportedEdge(source, point)` để loại node ứng viên. Hàm này kiểm tra bốn ô kề node và loại node nếu bất kỳ ô kề nào là khoảng trống không có nền.

Vì vậy một node đứng hợp lệ, có nền ngay bên dưới, vẫn bị xóa khỏi đồ thị chỉ vì hành lang nằm cạnh rãnh, bậc hụt, fence hoặc mép nền. Khi nhiều node liên tiếp bị loại như vậy, Citizens không tạo được path và diagnostics báo `path=absent`.

Quy tắc cộng chi phí cạnh mép đã tồn tại riêng trong `getCost(...)`; biến nó thành bộ lọc tuyệt đối cho cả các node lân cận là quá nghiêm ngặt.

## Bản sửa

- Giữ nguyên chi phí cao cho route cạnh khoảng trống để ưu tiên đường an toàn.
- Chỉ loại node ứng viên khi chính vị trí đứng của node không có nền an toàn.
- Không tăng range/search budget, không teleport recovery, không force-load chunk và không bỏ Citizens examiner.

## Regression test

`VillageRouteExaminerTest` bao phủ hai trường hợp:

1. Node mà chính vị trí đứng không có nền vẫn bị loại.
2. Node có nền hợp lệ nhưng nằm cạnh khoảng trống vẫn được giữ trong danh sách láng giềng.

Test thứ hai thất bại trước bản sửa và pass sau bản sửa.

## Đính chính giả thuyết fence gate

Giả thuyết trước đó rằng `DoubleDoorListener` hủy event fence gate là không đúng: Citizens phát `NPCOpenGateEvent` cho fence gate, không phải `NPCOpenDoorEvent`. Thay đổi listener và test dựng `NPCOpenDoorEvent` với `Gate` giả đã được bỏ.

## Xác minh

- Focused unit tests: pass.
- `./gradlew.bat clean test build --console=plain`: `BUILD SUCCESSFUL`.
- Runtime Paper/Citizens: chưa chạy lại; không được coi là gameplay pass cho đến khi cả Steve và Alaric tạo được path, vượt gate và hoàn thành chu kỳ farm → về nhà → ngủ → thức dậy → trở lại farm.
- Server production hiện đang dừng và không được restart/deploy nếu chưa có phê duyệt rõ ràng.
