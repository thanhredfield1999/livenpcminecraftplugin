# Contract phối hợp BlueMap – BotChecker

Ngày cập nhật: 2026-08-19
Scope: `E:\AI.WORK\living-npc-plugin` và server test `F:\minecraftserver\villagedefense2026`

## 1. Boundary

LivingNPC/BlueMap sở hữu runtime NPC, telemetry và marker. BlueMap world map native load/restart là source render; BotChecker chỉ là observer read-only.

BotChecker không được:

- sửa `E:\AI.WORK\botcheckerminecraft` trong scope task này;
- sửa source/config/world/BlueMap data;
- dùng command, teleport, GUI click, NPC/entity interaction hoặc tự tạo block mutation;
- dùng `/reload`, PlugMan, hot-loader, deploy hoặc restart;
- lưu credential, password, token, API key, connection string hoặc raw auth data.

BotChecker chỉ được kết nối bằng tester đã phê duyệt, đọc entity stream, block/chunk cache, telemetry và marker endpoint, rồi ghi report riêng.

## 2. Runtime baseline thực tế

- Paper: `1.21.11-131`
- Minecraft API: `1.21.11-R0.1-SNAPSHOT`
- Java runtime server: `25.0.1`
- Build toolchain: Java `21`
- LivingNPC: `0.6.0-rc.2`
- Citizens: `2.0.42-SNAPSHOT (build 4173)`
- BlueMap runtime: `5.16`
- BlueMap compile API: `de.bluecolored:bluemap-api:2.7.7`
- Paper game port: `11619`
- BlueMap web port: `8100`
- Marker/live viewer URL:
  `http://127.0.0.1:8100`
- Marker endpoint:
  `http://127.0.0.1:8100/maps/stillcliff/live/markers.json`

Artifact hash phải được ghi riêng trong mỗi BotChecker report. Không lấy hash từ tài liệu lịch sử.

## 3. Telemetry path và file semantics

Path thực tế hiện tại:

```text
F:\minecraftserver\villagedefense2026\plugins\LivingNPC\telemetry\latest.json
```

Path tương đối trong plugin config:

```yaml
telemetry:
  export:
    enabled: false
    file: telemetry/latest.json
    interval-ticks: 100
```

Khi export được bật, path resolve bên trong `plugins/LivingNPC`. Plugin ghi snapshot JSON replace-only, thường qua file tạm cùng thư mục rồi atomic move khi filesystem hỗ trợ. Đây không phải append log.

BotChecker phải:

1. đọc file sau khi thấy replacement/mtime mới;
2. parse toàn bộ JSON trước khi dùng;
3. giữ mỗi sample immutable;
4. ghi `observedAt` theo UTC ISO-8601 của observer;
5. không dùng thời điểm đọc làm `createdAt` activity;
6. coi file thiếu, parse lỗi hoặc stale là `INCONCLUSIVE_OBSERVATION`.

Snapshot thực tế đã kiểm tra có `schemaVersion: 1`, `capacity: 512`, `totalRecorded`, `events`, `gates` và `economy`.

## 4. JSON schema contract

Top-level bắt buộc:

```json
{
  "schemaVersion": 1,
  "capacity": 512,
  "totalRecorded": 0,
  "events": [],
  "gates": [],
  "economy": {}
}
```

Field bắt buộc:

- `schemaVersion`: integer; BotChecker phải reject version lớn hơn version hỗ trợ.
- `capacity`: integer giới hạn buffer.
- `totalRecorded`: integer counter, không dùng làm timestamp.
- `events`: array; có thể rỗng.
- `gates`: array; có thể rỗng.
- `economy`: object nếu economy snapshot được bật; có thể vắng khi feature không có dữ liệu.
- `visitors`: object tùy chọn khi visitor runtime bật.

Event object hiện tại:

- `schemaVersion`, `type`, `npcId`, `name`, `skinName`, `role`, `villageId`, `world`;
- `npcBlock`, `npcPrecise`, `targetBlock`, `targetPrecise`;
- `state`, `phase`, `navigation`, `path`, `obstacle`, `semanticPoint`, `blockProbes`;
- `timestampTick`, `timestampMillis`;
- `account` tùy chọn.

Event position object:

- `world`, `xBlock`, `yBlock`, `zBlock`, `x`, `y`, `z`, `yaw`, `pitch`.

Event navigation object nếu khác `null`:

- `navigating`, `targetWorld`, `target`, `strategy`, `path`, `examiners`, `pathfinder`, `range`;
- `stationaryTicks`, `distanceMargin`, `pathMargin`, `cancelReason`, `elapsedTicks`.

Gate object:

- `id`, `world`, `x`, `y`, `z`, `material`, `open`, `status`, `action`, `timestampTick`.

Economy object:

```text
villages[] -> villageId, balanceMinor, currencyUnit, totalEarnedMinor,
              totalSpentMinor, inventory[], roleProduction[], activities[], center
```

Inventory item: `item`, `amount`.
Role production: `role`, `amount`.
Activity: `role`, `action`, `item`, `amount`, `createdAt`.

`createdAt` là field bắt buộc khi BotChecker đánh giá activity timestamp. Nếu `null` hoặc thiếu, activity timestamp verdict là `INCONCLUSIVE`; không tự thay bằng `observedAt`.

## 5. World identity và dimension semantics

World name dùng để pair entity, block, telemetry và marker. So sánh phải normalize trim + lowercase:

```text
StillCliff == stillcliff
minecraft:overworld == overworld
```

Dimension là identity riêng, không thay thế world name. BotChecker phải ghi cả hai. World/dimension thiếu, mâu thuẫn hoặc đổi ngoài transition được scenario cho phép thì verdict `INCONCLUSIVE_PAIRING`.

BlueMap map ID có thể khác world ID. LivingNPC lookup map theo `map.getId()` trước, rồi fallback `map.getWorld().getId()`. BotChecker không được dùng map ID thay cho world identity.

## 6. NPC và entity identity

Discovery phải trả đúng một entity. Sau discovery, pin:

- Citizens/NPC UUID;
- client entity ID;
- object generation nếu observer hỗ trợ;
- entity name/role làm metadata, không làm identity chính.

Trong observation window, latch:

- entity gone/spawn/replacement;
- entity ID hoặc UUID đổi;
- tracking start/stop;
- world/dimension change;
- gate chunk unload/reload.

Identity replacement hoặc uniqueness mất: `INCONCLUSIVE_PAIRING`, không PASS.

Known historical fixture, chỉ dùng nếu scenario hiện tại xác nhận còn đúng:

- Alex UUID: `3d1d6e6d-6f19-4214-b794-f3ba0c202a1d`
- server world: `StillCliff`
- dimension: `overworld`
- door block: `(-17,-60,-67)`

Không hardcode fixture mới từ lịch sử.

## 7. Timestamp, timezone và freshness

Có hai clock:

- `timestampTick`: server tick, dùng thứ tự tick; không phải thời gian thực.
- `timestampMillis`/`createdAt`: wall-clock event time.

Activity `createdAt` serialize dạng ISO-8601 UTC, ví dụ:

```text
2026-08-18T14:59:31.581253700Z
```

Timezone contract: `Z` là UTC. UI có thể format theo timezone người xem, nhưng không được sửa raw timestamp.

BotChecker phải ghi `observedAt` UTC ISO-8601 và kiểm tra:

- timestamp parse được;
- activity list không đi ngược thời gian bất thường;
- event không nằm quá xa `observedAt` theo freshness budget của scenario;
- snapshot có mtime/replacement mới hơn baseline.

Không dùng browser time để tạo timestamp. Không coi `20 ticks = 1 giây` là wall-clock khi server lag.

## 8. Marker và live viewer

Marker API thuộc BlueMap, không phải telemetry schema. Marker set hiện do `BlueMapMarkerService` quản lý. Marker detail/icon là dữ liệu hiển thị; marker không chứng minh entity đã crossing.

BotChecker có thể kiểm tra:

- marker endpoint HTTP `200`;
- `Content-Type: application/json`;
- JSON parse được;
- marker set/ID không duplicate;
- map/world và position đúng scenario;
- detail/activity đúng snapshot sau freshness delay.

Live viewer:

```text
http://127.0.0.1:8100
```

Raw endpoint:

```text
http://127.0.0.1:8100/maps/stillcliff/live/markers.json
```

Evidence thực tế ngày 2026-08-19: endpoint trả HTTP `200`, `application/json`, payload `3732` bytes. Đây chỉ là marker endpoint verification, không phải crossing PASS.

## 9. BlueMap refresh/update behavior

BlueMap native renderer xử lý map khi server load/restart. Theo quyết định hiện tại, LivingNPC không còn listener hoặc service tự schedule tile sau block/cửa mutation.

- Không có debounce/queue refresh riêng.
- Không gọi `RenderManager.scheduleMapUpdateTask(...)`.
- Không purge map sau block event.
- Map có thể stale giữa hai lần server restart/load; đây là behavior được chấp nhận.

Mutation listener:

- Không còn đăng ký listener riêng cho BlueMap block refresh.
- `BlockBreakEvent`, `BlockPlaceEvent`, cửa và redstone không tạo refresh task LivingNPC.
- NPC door/gate vẫn đổi world state theo gameplay; BlueMap load lại ở lần server restart/load.

Không còn log refresh LivingNPC cần kiểm tra.

Phân biệt ba lớp:

1. server world state;
2. BlueMap native renderer/load/restart;
3. browser tile/cache.

`Update Map` chỉ ép browser lấy tile hiện có, không tạo world mutation. Map stale giữa restart là behavior được chấp nhận theo quyết định hiện tại.

Không có controlled refresh gate trong phase này; không dùng log schedule lịch sử để kết luận behavior hiện tại.

## 10. Door/fence-gate crossing oracle

BotChecker phải lấy block state từ client chunk cache/observer API, không suy diễn từ BlueMap:

- tọa độ block;
- material;
- lower/upper door part;
- facing;
- open/closed;
- chunk tracked liên tục.

PASS traversal chỉ khi:

1. identity pinned duy nhất;
2. world/dimension pair ổn định;
3. material/facing đúng;
4. aperture hợp lệ;
5. entity chuyển động liên tục qua signed plane;
6. không backtrack/discontinuity vượt ngưỡng;
7. có sample phía đối diện;
8. có exit confirmation/dwell;
9. không có tracking/chunk gap;
10. log không có exception passage/door examiner.

Verdict:

- `PASS_TRAVERSAL`
- `FAIL_TRAVERSAL`
- `INCONCLUSIVE_PAIRING`
- `INCONCLUSIVE_STATE`
- `INCONCLUSIVE_OBSERVATION`

Cửa mở/đóng nhanh có thể chỉ để lại state cuối trong BlueMap. Tile đổi không chứng minh crossing; trajectory hợp lệ mới chứng minh crossing.

## 11. Files đã sửa/liên quan

Trong phase BlueMap hiện tại, không sửa BotChecker adapter và không sửa `E:\AI.WORK\botcheckerminecraft`.

Files source BlueMap liên quan trong repo:

- `src/main/java/vn/heomc/livingnpc/bluemap/BlueMapMarkerService.java` (marker only)
- `src/main/java/vn/heomc/livingnpc/DoubleDoorListener.java` (door gameplay only; no BlueMap refresh hook)
- `src/main/java/vn/heomc/livingnpc/LivingNpcPlugin.java`
- `src/main/java/vn/heomc/livingnpc/NpcTelemetryJson.java`
- `src/main/java/vn/heomc/livingnpc/NpcTelemetryActivity.java`
- `src/main/resources/config.yml`
- `build.gradle.kts`

Documentation file cập nhật:

```text
E:\AI.WORK\living-npc-plugin\docs\BLUEMAP_BOTCHECKER_CONTRACT.md
```

Không có file BotChecker nào bị sửa.

## 12. Test và evidence thực tế

### Unit/build

- Focused BlueMap tests:
  `./gradlew.bat test --tests '*BlueMap*' --console=plain`
- Kết quả mới nhất: `BUILD SUCCESSFUL in 5s`, 4 actionable tasks.
- Một lần gọi trước dùng cú pháp PowerShell `.\\gradlew.bat` trong Bash và nhận `command not found`; chạy lại đúng `./gradlew.bat`, không phải lỗi code.
- BotChecker unit tests (read-only logic only):
  `npm test`
- Kết quả: `23 passed`, `0 failed`, `593.0167ms`.
- Full build sau khi gỡ refresh:
  `./gradlew.bat clean test build --console=plain`
- Kết quả thực tế: `BUILD SUCCESSFUL in 23s`, 6 tasks executed.
- Server test sau deploy bản gỡ refresh: `Done (49.040s)`; remapped JAR không chứa refresh classes.
- Lệnh có cảnh báo Gradle deprecated features; không có test/build failure.
- `git diff --check`: pass cho thay đổi liên quan.
- Lần `clean test` trước đó gặp lỗi Gradle test artifact tạm:
  `NoSuchFileException: build/test-results/test/binary/in-progress-results...`.
  Chạy lại không clean đã pass; không coi lỗi transient đó là test failure của logic.

### Controlled Paper runtime

Evidence thật từ server test:

- Paper startup: `Done (80.185s)`.
- LivingNPC connected:
  `LivingNPC BlueMap markers connected to BlueMap 5.16 / API DEV.`
- BlueMap web server bound port `8100`.
- Telemetry file đọc được tại path thực tế ở mục 3; controlled sample mtime `2026-08-18T18:51:49.059671Z`, `5147 bytes`, parse `schemaVersion=1`, `events=0`, `gates=4`, `activities=32`, `createdAt=32/32`.
- Marker endpoint HTTP `200`, JSON, `3732` bytes.
- Historical pre-removal test có log schedule, nhưng feature đã được gỡ theo quyết định mới; không dùng log cũ làm evidence behavior hiện tại.
- Controlled block/cửa refresh hiện không chạy và không phải release gate; map stale giữa restart là behavior được chấp nhận.
- BotChecker observer API được chạy read-only trên `127.0.0.1:18080` với telemetry path thực tế, health trả `{"ok":true}`, scenario listing trả `farming`, `fishing`, `gui-smoke`, `npc-quest`; không tạo run vì các scenario hiện tại có command/GUI/interaction/mutation trái contract.
- Observer process đã dừng sau health check; không còn listener `18080` sau cleanup.

Mức evidence hiện tại:

- Unit/build: `PASS`.
- Controlled Paper startup/marker endpoint: `PASS`.
- Controlled block/cửa map refresh: `NOT RUN` theo quyết định bỏ feature.
- BlueMap world map refresh: chấp nhận tại server load/restart, không runtime-verified giữa session.
- BotChecker crossing: chưa chạy trong phase này.
- Production verification: `NOT RUN`.

## 13. Known risks và blockers

- Map không được kỳ vọng đổi giữa session sau block/cửa mutation; reload tại server restart/load là behavior chấp nhận.
- Không còn `scheduleMapUpdateTask` trong LivingNPC runtime.
- Cửa đổi state nhanh có thể chỉ hiện state cuối trong BlueMap.
- Marker/live endpoint và world renderer có cache/timing riêng.
- Source default telemetry export `enabled: false`, nhưng server test config hiện tại đang bật `enabled: true`, `interval-ticks: 20`; BotChecker phải ghi rõ config observed, không tự sửa live YAML đang bị Paper ghi.
- World/entity pairing phải normalize case; history đã có lỗi `StillCliff` vs `stillcliff`.
- Current Paper logs còn NPC sleep/navigation diagnostics; chưa dùng làm BlueMap PASS/FAIL nếu không liên quan trực tiếp marker/refresh.
- Unit test không chứng minh Paper event ordering, BlueMap renderer, Citizens navigation, restart hoặc performance.
- Không có production verification trong phase này.

## 14. Dừng phase

Đã ghi contract và tạm dừng phase ngày 2026-08-19 theo quyết định bỏ BlueMap incremental refresh. BlueMap map chỉ cần load/render lại khi server test được restart. Không còn bước controlled refresh cần chạy. Ngày mai tiếp tục từ checkpoint này nếu có yêu cầu mới; không tự sửa BotChecker adapter, deploy production hoặc restart production.

Tài liệu tham chiếu:

- `docs/bluemap-refresh-research.md`
- `CURRENT_STATE.md`
- `docs/RISK_REGISTER.md`
- `README.md`
- https://bluemap.bluecolored.de/wiki/customization/Markers.html
- https://bluemap.bluecolored.de/wiki/FAQ.html
- https://docs.papermc.io/paper/dev/scheduler/
