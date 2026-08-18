# Nghiên cứu Paper/Minecraft pathfinding cho LivingNPC

**Baseline:** Paper 1.21.11 API; mục tiêu runtime Paper 1.21.11, Citizens 2.0.42-SNAPSHOT build 4173. Citizens Javadocs hiện public hiển thị 2.0.43-SNAPSHOT; tên API cần đối chiếu lại với build 4173 trước compile.

## Kết luận chính

1. **Paper Entity Pathfinder API chỉ áp dụng `Mob`.** `Mob#getPathfinder()` trả `com.destroystokyo.paper.entity.Pathfinder`; `Pathfinder#getEntity()` cũng trả `Mob`. `Player` chỉ kế thừa `LivingEntity`, không kế thừa `Mob`. Citizens Player NPC vì vậy không dùng trực tiếp `Player#getPathfinder()`/Paper Pathfinder.
2. **Citizens `Navigator` là API đúng cho Player NPC.** `npc.getNavigator().setTarget(Location)` dùng Citizens navigation; `setTarget(Iterable<Vector>)` chạy chuỗi điểm bằng Citizens movement logic, không pathfinding; `setStraightLineTarget` cố ý bỏ pathfinding. `NPC#setUseMinecraftAI` chỉ dùng Minecraft AI khi NPC/entity type hỗ trợ; không biến Bukkit `Player` thành `Mob`.
3. **Paper door flags không giải quyết Citizens Player NPC.** `setCanOpenDoors` và `setCanPassDoors` thuộc Paper `Pathfinder`, chỉ gọi được trên `Mob`. Không có Paper API tương đương cho Player NPC. Citizens có tham số riêng `npc.pathfinding.citizens.open-doors`; đây là cơ chế Citizens, không phải Paper Pathfinder.
4. **Fence gate không có flag mở/can-pass trong Paper Pathfinder API.** Door API chỉ mô hình hóa `Door`/`Openable`; `FenceGate` cũng là block state cần kiểm tra trực tiếp. Với Player NPC, CORE phải tự nhận diện gate, mở bằng Bukkit block state/event policy, rồi kiểm tra lối đi bằng bounding box/collision.
5. **Stair/chênh cao/fall là khác biệt giữa planner và movement.** Citizens exposes `fallDistance`, `experimental-jumps`, `check-bounding-boxes`, `maximum-search-blocks`; Paper Pathfinder không exposes jump height, max fall distance, A* iteration limit hay bounding-box toggle. `PathResult` thành công không đảm bảo NPC thực tế vượt được bước/gate/collision.
6. **Loaded chunks là prerequisite movement.** Paper `World` cung cấp `getChunkAtAsync(...)`, `addPluginChunkTicket(...)`, `getLoadedChunks()`, `getIntersectingChunks(BoundingBox)`. Không nên force-load vô hạn trong hot path. Preload bounded corridor trước navigation; kiểm tra chunk/entity ticking khi route crossing chunk boundary.

## Paper API: áp dụng được / không áp dụng được

| API | Player NPC | Mob NPC / entity Mob | Ý nghĩa |
|---|---:|---:|---|
| `Mob#getPathfinder()` | **Không** | **Có** | Paper vanilla-like pathfinder manager |
| `Pathfinder#findPath(Location)` | Không | Có | Tìm path theo game pathfinding rules |
| `Pathfinder#moveTo(Location,double)` | Không | Có | Bắt Mob chạy path |
| `setCanOpenDoors(boolean)` | Không | Có | Cho Mob mở door theo logic entity |
| `setCanPassDoors(boolean)` | Không | Có | Cho Mob đi qua door đã mở |
| `setCanFloat(boolean)` | Không | Có | Mob navigation trong water |
| `Entity#getBoundingBox()` | **Có** | **Có** | Hitbox hiện tại của entity |
| `Entity#collidesAt(Location)` | **Có** | **Có** | Kiểm tra entity collide tại vị trí |
| `Entity#wouldCollideUsing(BoundingBox)` | **Có** | **Có** | Kiểm tra collision với box đưa vào |
| `Entity#getFallDistance/setFallDistance` | **Có** | **Có** | Runtime physics state; không phải planner fall limit |
| `World#getChunkAtAsync(...)` | **Có** | **Có** | Request chunk load async; callback/future sau |
| `World#addPluginChunkTicket(...)` | **Có** | **Có** | Giữ chunk loaded; phải scope/release rõ |
| `BlockData#getCollisionShape(Location)` | **Có** | **Có** | Collision shape block state, gồm state cửa/gate |
| Citizens `NPC#getNavigator()` | **Có** | **Có** | Navigation abstraction cho NPC |
| Citizens `Navigator#setTarget(Location)` | **Có** | **Có** | Citizens path strategy |
| Citizens `Navigator#setTarget(Iterable<Vector>)` | **Có** | **Có** | Waypoint movement, bỏ pathfinder |
| Citizens `NavigatorParameters#fallDistance` | **Có** | **Có** | Citizens planner parameter |
| Citizens `NavigatorParameters#range` | **Có** | **Có** | Giới hạn khoảng tìm path |
| Citizens `NavigatorParameters#stationaryTicks` | **Có** | **Có** | Dừng bao nhiêu tick trước `STUCK` |

Nguồn API Paper:
- Entity Pathfinder docs: https://docs.papermc.io/paper/dev/entity-pathfinder
- `Mob`: https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Mob.html
- `Pathfinder`: https://jd.papermc.io/paper/1.21.11/com/destroystokyo/paper/entity/Pathfinder.html
- `Player`: https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html
- `Entity`: https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Entity.html
- `World`: https://jd.papermc.io/paper/1.21.11/org/bukkit/World.html
- `BlockData#getCollisionShape`: https://jd.papermc.io/paper/1.21.11/org/bukkit/block/data/BlockData.html
- `Door`: https://jd.papermc.io/paper/1.21.11/org/bukkit/block/data/type/Door.html

## Citizens API và behavior liên quan

`Navigator`:
- `canNavigateTo(Location)` và overload nhận `NavigatorParameters` cho preflight.
- `setTarget(Location)` pathfind.
- `setTarget(Iterable<Vector>)` chạy các vector tuần tự bằng Citizens movement logic, **không pathfinding**.
- `setStraightLineTarget(Location)` đi thẳng, **không pathfinding**.
- `isNavigating`, `isPaused`, `getPathStrategy`, `cancelNavigation` dùng telemetry/control.

`NavigatorParameters`:
- `range(float)`: khoảng pathfinding; Citizens FAQ ghi default path range 25, có thể chỉnh tới 100 qua command; xa hơn nên chia waypoint.
- `stationaryTicks(int)`: số tick đứng yên trước cancel `STUCK`.
- `fallDistance(int)`: giới hạn fall trong Citizens planner; cần test giá trị với build 4173.
- `updatePathRate(int)`: mặc định 20 tick, chủ yếu target-following.
- `pathDistanceMargin` và `distanceMargin`: phân biệt khoảng path tới target với ngưỡng coi navigation hoàn tất.
- `pathfinderType(PathfinderType)`: `MINECRAFT` dùng Minecraft pathfinder; A* Citizens cho phép `BlockExaminer`, nhưng semantics khác.
- `examiner(BlockExaminer)`: chỉ khả dụng với Citizens A*; không dùng khi `PathfinderType.MINECRAFT`.
- `debug(boolean)`: debug path bằng client-side flower.

Nguồn:
- `Navigator`: https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/Navigator.html
- `NavigatorParameters`: https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/NavigatorParameters.html
- `NPC`: https://jd.citizensnpcs.co/net/citizensnpcs/api/npc/NPC.html
- Citizens API wiki: https://wiki.citizensnpcs.co/API
- Citizens settings source: https://github.com/CitizensDev/Citizens2/blob/master/main/src/main/java/net/citizensnpcs/Settings.java

## Doors, fence gates, collision

### Door

Paper Pathfinder docs chỉ nêu `setCanOpenDoors` cho entity có khả năng mở door, và `setCanPassDoors` cho door đã mở. Docs nói rõ behavior phụ thuộc Minecraft pathfinding rules; không phải generic “unlock every obstacle”. Zombie/villager semantics khác nhau.

Citizens Settings source có:
- `npc.pathfinding.citizens.open-doors` — Citizens pathfinder mở door khi pathfinding, “should close them as well”.
- `npc.pathfinding.citizens.check-bounding-boxes` — kiểm tra bounding boxes, ví dụ giữa fence, trong door, half-block.

Runtime evidence NPC dừng trước gate không thể kết luận là “Paper door flag thiếu”: Player NPC không nhận Paper Pathfinder; Citizens A* examiner/bounding-box/open-door path phải kiểm riêng. Fence gate còn không được Paper docs liệt kê trong hai door flags.

### Collision/bounding box

`Entity#getBoundingBox()` lấy box theo location/size hiện tại. `collidesAt` và `wouldCollideUsing` áp dụng cho mọi `Entity`, gồm Citizens Player NPC. `BlockData#getCollisionShape(Location)` trả `VoxelShape` block state tại vị trí; dùng được để kiểm tra cửa/gate mở/đóng, slab, stair, fence, trapdoor.

Planner nên dùng cùng footprint NPC, không chỉ block center: corridor hợp lệ khi swept bounding box không cắt collision shape; gate passage cần kiểm tra trạng thái open và chiều rộng theo NPC box.

### Stairs/chênh cao

Paper Pathfinder không public jump/fall tuning. Citizens có `experimental-jumps` và `fallDistance`, nhưng `experimental-jumps` tăng CPU cost và được đánh dấu experimental trong source setting. Citizens issue #1173 ghi MCNavigationStrategy yếu khi NPC ở mép block thấp hơn target; AStar tốt hơn nhưng từng có hành vi jump liên tục lên cao. Đây là evidence lịch sử, không phải guarantee 1.21.11.

Nguồn:
- https://github.com/CitizensDev/Citizens2/issues/1173
- https://github.com/CitizensDev/Citizens2/issues/979
- https://github.com/CitizensDev/Citizens2/issues/2353

## Fall distance, physics, protected NPC

`Entity#getFallDistance/setFallDistance` chỉ đọc/ghi physics state. Không biến fall thành path planner rule. Citizens `fallDistance` là planner parameter; cần phân biệt hai lớp:

- Planner: cấm/chấp nhận cạnh path có độ rơi.
- Runtime: gravity, velocity, ground collision, fall damage xử lý entity.

Citizens NPC `isProtected()` mô tả bảo vệ khỏi damage, movement và event state changes; protected flag có thể làm test movement lệch với vanilla mob. CORE phải ghi rõ policy protected/unprotected trước khi dùng teleport/velocity/manual move.

## Loaded chunks và Paper 1.21.x

Paper `World` API có async chunk request và plugin chunk ticket. `getLoadedChunks()` chỉ quan sát hiện trạng; không đảm bảo chunk vẫn loaded tới lúc NPC đi tới. `addPluginChunkTicket` load chunk và giữ plugin ticket; dùng bounded route window, release sau khi NPC rời vùng.

Paper world config:
- `update-pathfinding-on-block-update: true`: cập nhật pathfinding Mob khi block update; tắt có thể giảm CPU nhưng làm path phản ứng chậm/không cập nhật khi cửa/gate đổi.
- `stuck-entity-poi-retry-delay`: retry POI khi entity navigation stuck; chủ yếu POI/entity navigation, không phải Citizens Player NPC universal timeout.
- Paper 1.21.9+ `getKeepSpawnInMemory` không còn functional vì vanilla bỏ concept spawn chunks; không dựa vào spawn chunks để giữ route.
- `delay-chunk-unloads-by` trì hoãn unload không thay thế preload/ticket route.

Nguồn:
- World config: https://docs.papermc.io/paper/reference/world-configuration
- Paper vanilla-like config: https://docs.papermc.io/paper/vanilla
- Paper issue #10903 (1.21 unload delay): https://github.com/PaperMC/Paper/issues/10903
- Paper issue #13406 (1.21.10 config evidence, includes pathfinding/chunk settings): https://github.com/PaperMC/Paper/issues/13406
- Paper issue #12043 (villager pathfinding through walls): https://github.com/PaperMC/Paper/issues/12043
- Paper issue #12335 (villager pathfinding stuck): https://github.com/PaperMC/Paper/issues/12335

## Giới hạn pathfinding

### Paper

Paper docs xác nhận Pathfinder bị giới hạn bởi Minecraft pathfinding logic. Public API không có max search blocks, path range, jump height hay fall limit. `findPath` trả nullable `PathResult`; `moveTo` trả boolean. Path theo entity target có thể tự cập nhật, nhưng Javadoc nói behavior không được đảm bảo và phụ thuộc game behavior.

### Citizens

Citizens expose giới hạn rõ hơn:
- `range`: khoảng tìm path, tránh tính toán xa.
- `maximum-search-blocks`: Settings source ghi mặc định 1024 cho A*.
- `stationaryTicks`: cancel `STUCK`.
- `updatePathRate`: recalc target path rate.
- `pathDistanceMargin`/`distanceMargin`: tránh false stuck/false complete.

Citizens FAQ ghi nếu NPC teleport tới destination thì thường path failed; default path range 25, tối đa 100 qua command, route xa nên chia điểm trung gian:
https://wiki.citizensnpcs.co/Frequently_Asked_Questions

## Khuyến nghị CORE movement

1. Dùng Citizens `Navigator` làm authority cho Player NPC; không cast `Player` sang `Mob`, không gọi Paper `Pathfinder`.
2. Preflight `canNavigateTo`; ghi `NavigatorParameters` local sau `setTarget`, không sửa default global nếu route-specific.
3. Chia route thành segment bounded theo `range`; mỗi segment phải có checkpoint ở cửa/gate/stair landing.
4. Gate/door state machine riêng: detect `Door`/fence gate, quyết định mở, chờ block state update, kiểm tra clearance bằng `Entity#getBoundingBox` + `BlockData#getCollisionShape`, rồi resume path.
5. Stair/chênh cao: classify ΔY từng edge; chỉ cho jump/fall theo policy; không coi path tồn tại là movement success.
6. Loaded-chunk guard trước từng segment; preload bounded chunks async/ticket; không force-load toàn tuyến vô hạn.
7. STUCK detector dùng position delta + velocity + collision + block state, không chỉ `isNavigating`; khi stuck phân biệt `path absent`, `collision`, `gate closed`, `chunk unavailable`, `fall/landing`, `protected`.
8. Test matrix riêng Player NPC và Mob NPC trên Paper 1.21.11: flat, stair, 1-block rise, 2-block wall, open/closed door, fence gate, narrow fence corridor, slab/trapdoor, drop 1/2/3+, chunk boundary, unloaded destination.

## Evidence gaps

- Không tìm thấy Paper issue/PR 1.21.x công khai mô tả riêng Citizens Player NPC gate failure; Paper issues chủ yếu Mob/villager/vanilla navigation.
- Citizens public Javadocs là 2.0.43-SNAPSHOT, khác target 2.0.42-SNAPSHOT build 4173. Cần inspect exact Citizens jar/Javadoc khi chốt signature.
- Không có Paper public API để lấy vanilla path node type, jump height, max fall, gate traversal policy cho Player NPC. Không suy đoán các phần này từ `Mob#getPathfinder()`.

## Sources

URL đã liệt kê inline theo từng mục. Nguồn chính: Paper Docs/Javadocs 1.21.11; Citizens Javadocs/API wiki; Paper/Citizens GitHub issues/source.

Không sửa code plugin. Chỉ tạo file nghiên cứu này.
