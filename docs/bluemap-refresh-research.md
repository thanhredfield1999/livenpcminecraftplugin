# BlueMap refresh và block state

Ngày nghiên cứu: 2026-08-18
Target: Paper 1.21.11, BlueMap 5.16, BlueMap API 2.7.7

## Kết luận

BlueMap native tự theo dõi world/chunk thay đổi và render lại khi server load/restart. LivingNPC không tự schedule refresh tile theo block event.

## Hành vi cần kỳ vọng

- Người chơi phá/đặt block: world state đổi; map được chấp nhận stale tới lần server restart/load kế tiếp.
- Cửa/fence gate mở/đóng: block state đổi. BlueMap có thể cập nhật sau chu kỳ render kế tiếp; không bảo đảm hiển thị từng frame.
- Cửa đổi state rất nhanh rồi đổi lại: BlueMap có thể chỉ hiển thị state cuối cùng.
- Marker LivingNPC và custom web panel là luồng riêng, cập nhật qua BlueMap API/live marker JSON; không cần map purge.

## Quy trình kiểm tra an toàn

1. Thực hiện block mutation trong vùng đã render.
2. Chờ server lưu world.
3. Mở BlueMap, bấm `Update Map` để buộc trình duyệt lấy tile mới.
4. Dùng `/bluemap` xem trạng thái renderer.
5. Không dùng `/reload` hoặc hot-loader.

## Chính sách refresh

Không dùng `scheduleMapUpdateTask`, `purge` hoặc listener riêng để refresh map sau block/cửa mutation. Server restart/load là mốc chấp nhận để BlueMap render lại.

## Source đã đối chiếu

- BlueMap Markers: https://bluemap.bluecolored.de/wiki/customization/Markers.html
  - POI `detail` hỗ trợ HTML.
  - POI `icon` nhận URL local/remote.
  - Dynamic marker dùng BlueMap API.
- BlueMap Customisation: https://bluemap.bluecolored.de/community/Customisation.html
  - JS/CSS đặt trong webroot và khai báo ở `webapp.conf`.
  - Custom JS/CSS không phải map renderer.
- BlueMap FAQ: https://bluemap.bluecolored.de/wiki/FAQ.html
  - Map chờ world save trước khi thay đổi xuất hiện.
  - `Update Map` buộc browser lấy tile mới.
  - BlueMap tự theo dõi chunk thay đổi và chỉ convert chunk thay đổi.
  - `/bluemap purge` dùng để cập nhật lại map trong tình huống cần thiết.
- Paper Scheduler: https://docs.papermc.io/paper/dev/scheduler/
  - 20 tick/giây chỉ là tick time; không dùng tick làm timestamp thời gian thực.
  - Main-thread task ảnh hưởng hiệu năng.

## Quyết định implementation

Phase hiện tại: không có code refresh chunk riêng.

Giữ:

- `BlueMapMarkerService` cho marker động.
- BlueMap native renderer khi server load/restart.
- Custom web JS/CSS cho economy, icon và activity.

Không mở lại task refresh tile nếu chưa có yêu cầu mới và controlled benchmark rõ ràng.
