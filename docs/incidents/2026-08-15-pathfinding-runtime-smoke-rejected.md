# 2026-08-15 pathfinding runtime smoke rejected

## Phạm vi

Candidate `living-npc-0.6.0-rc.2.jar` được build, backup, deploy và khởi động trên Paper/Citizens theo phê duyệt ngày 2026-08-15. Không dùng `/reload`, PlugMan hoặc teleport fallback.

## Artifact và backup

- Candidate SHA-256: `C88D0B44F20A796F77B3F751C553FAC20D2BCAC754DD4593D12B702A8A2EBBCF`.
- Backup trước deploy: `F:\minecraftserver\villagedefense2026\plugins\LivingNPC-backup-20260815-172039`.
- JAR rollback SHA-256: `0E6238CF7E5EE32452F53C4A21F4097D2A9D5DCD8CA049CB4C18B901115C414F`.

## Triệu chứng tái hiện

Paper và LivingNPC khởi động thành công, không có `NoSuchMethodError`, `NoClassDefFoundError` hoặc lỗi enable. Tuy nhiên NPC farmer Steve (`35d40d2f-eac9-464e-a45d-4d5576729903`) lặp lại chu kỳ sau:

1. `GOING_HOME->GOING_TO_BED` tại khoảng `StillCliff:62,-61,-2`.
2. Gate stage đầu được Citizens báo `COMPLETED` trong margin.
3. Stage tiếp theo bị hủy với `reason=PLUGIN`; NPC gần như giữ nguyên vị trí.
4. Diagnostic báo `Bị kẹt ở phase GOING_TO_BED, không tiến triển trong 20 giây`.
5. Runtime chuyển sang retry cooldown rồi tái diễn cùng route.

Evidence tiêu biểu nằm tại `F:\minecraftserver\villagedefense2026\logs\latest.log`, các dòng 859-920 và các chu kỳ lặp sau đó.

## BotChecker

Run ID: `dc4206a6-0f92-490a-b7ab-45050a31dca8`.

Report: `E:\AI.WORK\botcheckerminecraft-botchecker\reports\runtime-20260815\dc4206a6-0f92-490a-b7ab-45050a31dca8.json`.

Verdict là `INCONCLUSIVE_PAIRING`, không phải pass/fail traversal: fixture đòi `serverWorld: StillCliff`, client quan sát `stillcliff`. Client vẫn xác nhận gate block `spruce_door`, facing `east`, lower, closed. Không sửa fixture vì BotChecker là reference read-only trong phạm vi này.

## Đánh giá

- Startup/linkage: pass.
- Cleanup/retry: có chạy; navigation ownership được nhả trước retry.
- Door traversal: chưa được chứng minh; telemetry Steve cho thấy route không tiến triển.
- Fence-gate traversal: chưa có bằng chứng pass độc lập.
- Blocked-route/fallback: bounded retry hoạt động, nhưng không đủ để qua release gate vì route hợp lệ vẫn lặp lỗi.
- Candidate: rejected, không được xem là runtime-verified.

## Smoke tái hiện bổ sung

Artifact live được kiểm lại có SHA-256 `DB4C6827B7C433C865ED1DD0C2E4897D2752C765A5A29C6043961985A14DE987`. BotChecker run `5a902d13-4ee2-42c0-bfb1-e2b85e1f1dd5` giữ player `HeoMC_Tester` đứng yên 290 giây để load chunk, không command, teleport, GUI click hoặc tương tác. Report nằm tại `C:\Users\thanh\AppData\Local\Temp\livingnpc-smoke-reports\5a902d13-4ee2-42c0-bfb1-e2b85e1f1dd5.json`.

World time `19319` kích hoạt đúng bedtime. Trong năm lần lặp, Citizens báo `GOING_TO_BED_GATE reason=COMPLETED` tại `StillCliff:62.3000,-60.0625,-1.7000` dù target approach là `62.5000,-60.0000,-0.5000`: khoảng cách ngang `1.2166`, nhỏ hơn `distanceMargin=1.5000`. Coordinator sau đó đổi sang leg `EXIT`, nhưng Steve chưa căn tới trục cổng và giữ nguyên block `62,-61,-2`. Mỗi chu kỳ kết thúc bằng `ERROR`, rồi `RECOVERED` và retry sau khoảng 44 giây. Khi bedtime kết thúc, cùng vị trí tiếp tục kẹt ở `GOING_TO_PLOT`, nên tác động không chỉ giới hạn route ngủ.

## Root cause

`GateRouteCoordinator` truyền cùng navigation margin cấu hình `1.5` cho cả Citizens completion và `GateRoute.advanceIfReached(...)` ở các leg crossing. Với approach/exit chỉ cách nhau khoảng 2 block, margin này cho phép leg `APPROACH` hoàn tất sớm khi NPC còn cách target `1.2166` block. Route chuyển target trước khi NPC tới entry-side geometry cần thiết để đi qua aperture; retry tái tạo cùng điều kiện nên lặp vô hạn có giới hạn theo watchdog.

Completion policy của crossing cần tolerance riêng, nhỏ hơn navigation margin thông thường và bị chặn theo geometry; leg `FINAL` vẫn có thể dùng margin cấu hình. Cần regression test tái hiện vị trí thực `current=(62.3,-60.0625,-1.7)`, `approach=(62.5,-60,-0.5)`, margin `1.5`: route phải giữ `APPROACH`.

## Rollback và xác minh

Server được dừng sạch qua local RCON. Candidate được thay bằng JAR backup có SHA-256 `0E6238CF7E5EE32452F53C4A21F4097D2A9D5DCD8CA049CB4C18B901115C414F`, rồi Paper được khởi động lại sạch.

Sau rollback:

- Paper đạt `Done`.
- LivingNPC có enable marker.
- Ports `11619` và `36102` đang listen.
- Không có fatal linkage marker trong startup log.
- BotChecker listener riêng trên `127.0.0.1:18080` đã dừng.

## Bước sửa tiếp theo

DeepSeek đã thêm regression `approachKhongAdvanceSomKhiConCachEntryGeometryMacDuTrongNavigationMargin` trong `GateRouteTest`. Trước fix, focused run có `7 tests completed, 1 failed`. Fix nhỏ nhất thêm crossing tolerance `0.75` và dùng `min(configuredMargin, crossingTolerance)` cho `APPROACH`/`EXIT`; `FINAL` tiếp tục dùng margin cấu hình. Focused `GateRouteTest` + `GateRouteCoordinatorTest` đã `BUILD SUCCESSFUL`.

Full verification `./gradlew.bat clean test build --console=plain` đã `BUILD SUCCESSFUL in 15s` với 6 tasks. Source candidate vẫn **chưa runtime-verified**. Không deploy hoặc restart trong lượt này; cần phê duyệt production deploy riêng trước Paper/BotChecker smoke mới.

## Runtime smoke của crossing-tolerance candidate

Sau phê duyệt ngày 2026-08-16, source candidate được build/test lại, backup và deploy bằng clean RCON stop. Full build `BUILD SUCCESSFUL in 21s`; backup trước deploy là `F:\minecraftserver\villagedefense2026\plugins\LivingNPC-backup-20260816-001944`; candidate SHA-256 là `EFC9EB527A0CBDEDE6A65FD8025896E660C2C9122FCDBF8D296070783BA0FAB5`.

Paper và LivingNPC startup thành công. BotChecker run `7f03b968-868b-4c44-9acd-0e172d8db97e` chỉ giữ chunk active. Steve vào `GOING_TO_BED` tại world time `17094`, nhưng candidate vẫn báo `GOING_TO_BED_GATE reason=COMPLETED` tại vị trí `62.3000,-60.0625,-1.7000`, target approach `62.5000,-60.0000,-0.5000`, horizontal `1.2166`, `distanceMargin=1.5000`. Sau đó target đổi sang `57.5000,-60.0000,-1.5000`, Steve không tiến triển và watchdog báo `ERROR` sau 20 giây. Chu kỳ lặp lại nhiều lần.

Kết quả này bác bỏ hiệu lực runtime của fix chỉ thay completion tolerance trong `GateRoute.advanceIfReached(...)`: Citizens vẫn kết thúc navigation callback theo active `distanceMargin=1.5` trước khi route-side geometry guard có thể giữ leg `APPROACH`. Unit regression và full build pass nhưng không mô phỏng đúng callback completion thực tế. Candidate `EFC9EB527A0CBDEDE6A65FD8025896E660C2C9122FCDBF8D296070783BA0FAB5` bị runtime-rejected.

BotChecker cancel API trả HTTP 500; process được dừng và port `18080` được xác minh không listen. Paper sau đó clean-stop qua RCON, JAR backup hash-guarded được restore, và Paper restart thành công. Trạng thái sau rollback:

- Live JAR SHA-256: `DB4C6827B7C433C865ED1DD0C2E4897D2752C765A5A29C6043961985A14DE987`.
- Paper Java PID `64092`; ports `11619` và `36102` listen.
- LivingNPC Season 2 enable marker và Paper `Done (38.347s)` có trong startup log.
- Port BotChecker `18080` không listen.
- Không restore YAML vì không có schema migration hoặc bằng chứng candidate sửa persisted data trong cửa sổ test; backup đủ JAR và tám file dữ liệu vẫn được giữ.

Fix tiếp theo phải điều khiển active Citizens navigation completion margin cho crossing leg, hoặc không chấp nhận Citizens `COMPLETED` như bằng chứng leg hoàn tất khi geometry chưa đạt. Cần RED regression ở coordinator/callback boundary trước patch; test thuần `GateRoute` hiện tại không đủ.

## Source fix tại Citizens callback boundary

Điều tra bytecode Citizens `2.0.42-SNAPSHOT` xác nhận `getLocalParameters()` trả default parameters khi navigator idle; `setTarget(...)` tạo active local parameters từ cấu hình đó. Vì vậy crossing margin phải được đặt trước `setTarget`, còn callback diagnostic phải gắn vào active parameters sau `setTarget`.

DeepSeek thêm RED regressions tại `GateRouteCoordinatorTest`: navigation phải nhận effective margin `0.75` cho `APPROACH`/`EXIT`, giữ margin cấu hình cho `FINAL`, giữ đúng margin khi restart, và không tăng margin cấu hình nhỏ hơn cap. Contract `GateRouteCoordinator.Navigation.start(...)` nay nhận margin hiệu lực. Adapter Farmer/Rancher đặt `distanceMargin` và `pathDistanceMargin` trước `activeParametersAfterTarget(...)`; diagnostics nhận cùng margin thực.

Focused `GateRouteTest` và `GateRouteCoordinatorTest` đã `BUILD SUCCESSFUL`. Full verification `./gradlew.bat clean test build --console=plain` đã `BUILD SUCCESSFUL in 18s` với 6 tasks. Source candidate này vẫn **chưa production runtime-verified**. Không deploy hoặc restart trong lượt sửa này; production tiếp tục chạy rollback JAR `DB4C6827B7C433C865ED1DD0C2E4897D2752C765A5A29C6043961985A14DE987`.
