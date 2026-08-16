# Incident: ownership navigator cửa bị tranh chấp và callback DoorExaminer không an toàn

Ngày: 2026-08-15
Trạng thái: Đã sửa source và kiểm thử tự động; chưa triển khai hoặc xác minh artifact mới trên Paper/Citizens

## Triệu chứng và bằng chứng

Trên Paper 1.21.11 với Citizens 2.0.42 build 4173, Alex (`3d1d6e6d-6f19-4214-b794-f3ba0c202a1d`) đứng trước cửa tại StillCliff và lặp lại passage mà không vượt cửa.

Tại thời điểm quan sát `2026-08-15 04:24:37 +0700`, `logs/latest.log` của JAR live cũ chứa:

- 7.512 dòng `NPC_DOOR ... result=APPROACH_WAIT` cho Alex.
- 7.511 dòng `NPC_DOOR ... result=PREEMPTED` cho Alex.
- 64 stack frame `DoorExaminer$DoorOpener.run(DoorExaminer.java:148)`.
- 128 dòng thông báo `point is null` liên quan callback block của Citizens.

Các số đếm chỉ mô tả defect trên JAR live cũ; chúng không phải bằng chứng runtime cho bản sửa mới.

## Nguyên nhân gốc

Có hai lớp lỗi độc lập nhưng cộng hưởng:

1. `FisherRuntime` và `DoubleDoorListener` cùng điều khiển một Citizens `Navigator`. Door đặt target approach/crossing, nhưng Fisher không có callback preempt thực và có thể tiếp tục diễn giải hoặc thay target của passage. Door sau đó phát hiện target khác và ghi `PREEMPTED`, tạo target ping-pong.
2. Citizens `DoorExaminer` 2.0.42 có callback block không fail-closed:
   - `DoorOpener.run()` dereference `point` mà không kiểm tra null; lỗi này đã xuất hiện trực tiếp trong log live.
   - callback đóng cửa chạy trễ qua nhiều lần `Navigator.setTarget()` và cast `BlockData` sang `Openable` mà không xác minh block vẫn là door/gate. Source artifact hiện hành vẫn có đường cast này.

Source upstream liên quan:

- `DoorExaminer`: https://github.com/CitizensDev/CitizensAPI/blob/c0937a11b54bc7bbd0aa92f31155f835242fd764/src/main/java/net/citizensnpcs/api/astar/pathfinder/DoorExaminer.java
- Citizens issue lịch sử cùng lớp stale block callback: https://github.com/CitizensDev/Citizens2/issues/1768
- `CitizensNavigator.setTarget()` thay strategy bằng `CancelReason.REPLACE`: https://github.com/CitizensDev/Citizens2/blob/58d1a975644897ccc4a9565b18fd65517e7c902a/main/src/main/java/net/citizensnpcs/npc/ai/CitizensNavigator.java

## Bản sửa

### Ownership navigator

- Dùng `NavigationLeaseManager` chung cho Door và Fisher.
- Door giữ priority 90; Fisher giữ priority 30.
- Fisher nhận callback preempt, giữ journey ở trạng thái pause và chỉ đặt lại target sau khi reclaim lease thành công.
- Fisher cài target trước rồi mới cấu hình active `localParameters` mà Citizens vừa clone cho target đó; timeout/quota hủy navigation còn sở hữu trước khi nhả lease.
- Các boundary quota, suspend, despawn và role-stop dùng teardown exception-safe: mọi bước navigator/hook/hand/state/lease đều được thử, state nội bộ được xóa trước side effect, exception đầu tiên được giữ và lỗi sau được gắn suppressed. Fisher không hủy navigator hoặc nhả lease của owner priority cao hơn.
- `FisherManager` cô lập lỗi lookup Citizens, tạo runtime, đọc trạng thái role/sleep/active-role, cleanup runtime stale, chuyển sang ngủ và xử lý lỗi tick theo từng UUID, nên một Fisher lỗi không chặn Fisher kế tiếp. Lookup lỗi giữ runtime hiện có thay vì coi NPC là stale; shutdown vẫn thử dọn toàn bộ runtime trước khi truyền exception đầu tiên lên coordinator.
- Khi resident chuyển sang sleep, Fisher chỉ hủy navigator nếu vẫn là lease owner; nếu owner `sleep` priority cao hơn đã preempt thì target mới được giữ nguyên. Điều này ngăn target Fisher orphan ở các nhánh sleep trả sớm mà chưa claim lease.
- `NavigationLeaseManager` ghi owner mới trước callback preempt và cô lập callback lỗi, nên exception ở owner cũ không thể rollback arbitration.
- Door kiểm tra lease trong từng tick; nếu owner đã đổi thì đóng phần cửa do passage mở, dừng task và không restore target của owner cũ.
- Target bị Citizens clear được phân biệt với target mới do component khác đặt. Chỉ trường hợp target bị clear mới được phép phục hồi original target; target owner mới không bị ghi đè.
- `finish` và `abort` chỉ restore khi Door vẫn giữ lease.

### Examiner cửa fail-closed

- Thêm `LivingDoorExaminer` giữ contract `NPCOpenDoorEvent`/`NPCOpenGateEvent` nhưng kiểm tra null, material và `Openable` tại cả thời điểm mở lẫn đóng.
- Managed NPC đặt metadata chính thức `pathfinder-open-doors=false` để Citizens không tự chèn thêm `DoorExaminer` lỗi.
- `LivingNavigation` loại `DoorExaminer` upstream khỏi parameters, giữ nguyên các examiner khác và cài đúng một `LivingDoorExaminer`.
- Task đóng cửa/cổng có timeout hữu hạn, chỉ đóng đúng material mà chính callback đã mở, và được đóng/hủy trong runtime-stop cleanup.
- Task đóng được sở hữu ngay sau khi mở thành công, pin đúng Citizens `PathStrategy`, đóng bù nếu scheduler lỗi và teardown trong `finally` nếu close/cancel ném exception.
- Không thêm teleport recovery, force-load chunk, scan world/chunk hoặc pathfinder riêng.

### Oracle live read-only

BotChecker bổ sung observer chống false positive:

- Discovery bắt buộc đúng một entity; sau đó pin cả entity ID và UUID.
- Discovery bắt buộc đúng một entity trong toàn bộ cửa sổ; observer pin object generation, entity ID và UUID, đồng thời latch `entityGone` và uniqueness trên `entitySpawn`/`entityUpdate`/`entityMoved` ngay giữa các sample. Vị trí pinned entity từ `entityMoved` cũng đi qua continuity/aperture/discontinuity oracle và raw evidence, nhưng chỉ poll định kỳ được tăng exit confirmations/dwell.
- PASS cần entry-side observation, chuỗi liên tục trong aperture, hai mẫu kẹp mặt phẳng đều nằm trong corridor/vertical envelope, giới hạn bước nhảy, nhiều mẫu exit-side và dwell tối thiểu.
- Jump lớn, mất entity, UUID đổi, ambiguity, timeout hoặc quay lại dải mặt phẳng không thể PASS.
- Report timeout/INCONCLUSIVE vẫn giữ identity, configuration và toàn bộ raw observations đã thu.
- Scenario observer không chat, interact, attack, dig, place, teleport hoặc tự di chuyển bot.

## Regression test

LivingNPC:

- `LivingDoorExaminerTest`: null callback, stale material, schedule failure, navigation replacement, bounded close state và close đúng `Openable`.
- `LivingNavigationTest`: loại examiner upstream, giữ một examiner guard và giữ route examiner.
- `FarmerManagerNavigationConfigTest`: metadata tắt examiner mặc định được cài cho managed NPC.
- `NavigationLeaseManagerTest`: Door preempt Fisher, callback preempt lỗi không giữ owner cũ, Fisher reclaim sau release và Door nhận biết owner priority cao hơn.
- `DoubleDoorListenerTest`: phân biệt target clear với owner mới; owner mới thắng kể cả NPC vừa đạt target cũ; startup/cleanup exception không rò passage hoặc lease.
- `FisherRuntimeLifecycleTest`: quota/timeout cancel-before-release, `setTarget -> getLocalParameters` đúng contract clone của Citizens, cleanup vẫn hoàn tất khi cancel navigation, xóa hook hoặc restore equipment ném lỗi, owner priority cao hơn không bị Fisher hủy navigator, cùng một exception instance không gây self-suppression, và sleep cleanup phân biệt Fisher owner với sleep owner.
- `FisherManagerLifecycleTest`: lookup Citizens, tạo runtime, state decision, cleanup stale/sleep/tick/definition-update failure không chặn Fisher kế tiếp; shutdown dọn mọi runtime và xóa manager state dù một runtime ném lỗi; aggregation exception chống self-suppression.
- `GateRouteTest`/`GateRouteCoordinatorTest`: candidate hữu hạn và đủ clearance, deadline gần `Long.MAX_VALUE`, exit dwell trên hai server tick khác nhau, biên vertical tolerance và tối đa một rescue restart mỗi leg.

BotChecker:

- `crossing.test.ts`: signed transition, continuous aperture, boundary samples, geometry validation, sampled exit confirmation/dwell, event geometry không tự xác nhận PASS, backtrack và discontinuity.
- `entity-observer.test.ts`: ambiguity liên tục, UUID bắt buộc, object generation và identity continuity.
- `runner-session.test.ts`/`crossing-report.test.ts`: step timeout cục bộ, `entityGone`, transient identity/range/geometry excursion giữa hai poll, lifecycle discontinuity, timeout `INCONCLUSIVE_TRACKING` và raw evidence.

Các testcase defect được chạy RED trước implementation rồi GREEN sau bản sửa.

## Xác minh

- LivingNPC focused ownership/examiner suites: pass; final independent review of the four lifecycle findings returned `PASS`.
- Final read-only GateRoute/GateRouteCoordinator review: `PASS`; không có finding correctness mới trong năm vùng deadline wrap, finite validation, distinct-tick dwell, candidate geometry và bounded restart.
- `./gradlew.bat clean test build --console=plain`: `BUILD SUCCESSFUL`; 71 suites, 289 tests, 0 failures/errors/skipped.
- `git diff --check`: pass; chỉ có cảnh báo LF/CRLF hiện hữu.
- BotChecker `npm run typecheck`: pass.
- BotChecker `npm test`: 101 tests, 99 pass, 0 fail; 2 POSIX signal tests skipped as inapplicable on Windows; all repository scenarios parse under the strict schema. Coordinated pairing also verifies the runtime lower door/fence-gate material and facing from the client chunk cache, latches transient replacement or gate-chunk unload, and permits normal open/close state changes.
- BotChecker `npm run build`: pass.
- Artifact local: `build/libs/living-npc-0.6.0-rc.2.jar`, SHA-256 `616073ee9b896d8a2912c36ed91d7b0b05d9044225eccadffae0ec952063a0df`. Các candidate `220e5d9810663534a7c14e046d0bd01664c7a773a27e20ee440be81d1e9feb6b` và `11320d2d9e3cb3851f9b5c97f34a10d9d3d9a60de6a1d66cecc3407042534a85` đã obsolete.
- JAR live quan sát tại `F:\minecraftserver\villagedefense2026\plugins\living-npc-0.6.0-rc.2.jar`: SHA-256 `0e6238cf7e5ee32452f53c4a21f4097d2a9d5dcd8ca049cb4c18b901115c414f`.
- Paper vẫn chạy PID 43004 và listen port 11619 trong lần kiểm tra. Không stop, copy JAR, deploy, restart hoặc hot-reload.

## Trạng thái runtime cuối

Artifact mới chưa được triển khai sạch, nên Door và FenceGate vẫn là `INCONCLUSIVE` ở cấp live. Chỉ được chuyển sang PASS sau một cửa sổ deploy/restart được phê duyệt, xác minh hash artifact đang chạy, rồi thu trajectory BotChecker có signed-plane crossing và exit dwell cùng log plugin không còn loop/exception.
