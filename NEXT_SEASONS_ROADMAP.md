# LivingNPC - Roadmap Season 6 Tro Di

Tai lieu nay tiep noi sau Season 1-5, tap trung vao doi song co muc dich, nhu cau doi/khat va bep nau that. Moi season phai doc lap, mac dinh tat va chi mo khi season truoc dat du tieu chi thoat.

## Hien Trang Va Khoang Trong

Source hien tai da co:

- NPC tim giuong, di ngu va thuc day theo gio.
- Nguoi dan patrol `DIRT_PATH`, di cho, ngam canh, ngoi ghe nghi va Nong dan ngoi ban an luc nghi trua.
- Citizens Navigator + `DoorExaminer` di qua cua go va fence gate; khong stuck teleport.
- Khu nau an duoc validate bang `FURNACE` + `CRAFTING_TABLE`.
- Recipe dau bep doc tu `recipes.yml`; input/output duoc doi nguyen tu trong kho ao.

Nhung phan con thieu:

- Sau khi thuc day NPC khong co buoc bat buoc roi khoi khu vuc giuong. NPC co the dung canh giuong cho den lan patrol tiep theo.
- Lang chua co danh sach diem sinh hoat co loai, muc dich, gio mo cua va suc chua.
- Chua co state doi, khat, no, nhu cau uu tien hoac du lieu persist theo NPC.
- Ghe ban an moi la presentation; chua tieu thu do an hoac giai quyet nhu cau.
- Dau bep hien chi dung gan tam khu bep trong 60 tick, sau do doi item trong kho ao. Lo that khong co phien nau, nhien lieu, inventory lock hay recovery sau restart.
- `ReleasePolicy` hien de `SEASON = 2` va chi mo `RESIDENT`, `FARMER`, `FISHER`, `RANCHER`. Season 2 RC phai qua live gate truoc khi mo Season 6.

## Nguyen Tac Xuyen Suot

- Chi dat cac **diem co y nghia gameplay**, khong bat admin dat tung node duong di. Citizens tu tim duong giua cac diem.
- Moi NPC chi co mot `activity authority` dieu khien Navigator tai mot thoi diem.
- Season 6 phai cung cap lease API trung tam. Sleep, role runtime, alarm, seating, social va needs chi duoc navigate sau khi claim lease; preemption phai release owner cu ro rang.
- Uu tien theo thu tu: nguy hiem/bao dong -> ngu -> khat nghiem trong -> doi nghiem trong -> ca lam viec -> sinh hoat tu do.
- Doi va khat tao lich song, khong bien NPC thanh may phai an lien tuc. NPC khong chet vi thieu do an/nuoc trong MVP.
- Kho ao va transaction journal la source of truth kinh te. Item trong lo la phien nau dang duoc khoa, khong phai kho cong cong.
- Khong force-load chunk, khong teleport chua pathfinding, khong scan toan world.
- Moi feature moi mac dinh tat va co schema migration mot chieu, backup va rollback ro rang.
- `villages.yml`, needs va transaction journal co schema rieng. Migration ghi file tam, validate, backup roi moi replace; rollback nghia la khoi phuc backup va binary cu, khong reverse-migrate tai cho.

## Cong Kiem Ke Truoc Season 6

**Muc tieu:** chot mot baseline dung voi trang thai da hoan thanh cua Season 1-5.

**Ket qua kiem ke `2026-08-13`: KHONG DAT.** Source sau do da duoc chot thanh Season 2 release candidate `0.6.0-rc.1`, chi mo `RESIDENT`, `FARMER`, `FISHER`, `RANCHER`. Season 2 van phai qua smoke/soak, restart, cleanup va performance gate truoc khi thanh final release; Season 3 tro len van bi khoa.

**Phai lam:**

- Doi chieu `ReleasePolicy`, GUI, scheduler, listener va config de biet role/runtime nao thuc su dang mo.
- Cap nhat `README.md`, `SEASON_ROADMAP.md` va version release cho trung voi build.
- Chay full test, smoke test tung role va xac nhan restart sach.
- Khong goi mot season la hoan thanh neu code van bi release gate khoa hoac chua qua smoke test live.

**Tieu chi thoat:**

- Mot bang feature matrix ghi ro `source only`, `experimental`, `enabled`, `smoke-tested` va `live`.
- Khong co role duoc GUI cho chon nhung scheduler khong chay, hoac runtime chay nhung GUI noi dang khoa.
- Day la cong phat hanh, khong tinh thanh mot season gameplay moi.

## Season 6 - Nhip Song Va Diem Sinh Hoat

**Muc tieu:** NPC roi giuong sau khi thuc day va di den cac dia diem trong lang theo lich co muc dich thay vi dung cho hoac wander ngau nhien quanh nha.

### Ha tang moi

Them `ActivityPoint` dung chung theo lang:

- `HOME_EXIT`: diem roi khoi nha, gan rieng theo NPC hoac nha.
- `DINING`: ban an; lien ket voi `SeatType.DINING` hien co.
- `WATER`: gieng, voi nuoc, chau nuoc hoac diem lay nuoc hop le.
- `SOCIAL`: quang truong, cho, khu tro chuyen.
- `SCENIC`: diem ngam canh.
- `REST`: ghe nghi.
- `WORK_BREAK`: diem nghi gan noi lam viec.

Moi diem co ID, loai, interaction block, safe standing location + yaw/pitch, suc chua, khung gio tuy chon va danh sach role duoc dung. Reservation chi ton tai RAM; location va policy duoc persist trong `villages.yml`.

Tat ca reservation dung mot service va resource ID chuan. `DINING` lien ket ghe phai reserve dung mot `SeatDefinition`; diem an dung co slot ID rieng, khong dem capacity tach roi lai reserve ghe lan nua.

### Hanh vi

- Chuoi buoi sang bat buoc: `SLEEPING -> WAKING_UP -> LEAVING_HOME -> MORNING_ACTIVITY`.
- NPC thuc day phai ra `HOME_EXIT` truoc khi chon patrol, an sang, uong nuoc, xa hoi hoac di lam.
- Neu khong dat `HOME_EXIT`, runtime tim safe standing gan giuong nhu hien tai roi roi khoi ban kinh giuong; GUI hien canh bao, khong khoa toan NPC.
- Diem dich duoc chon theo gio, role, nhu cau va suc chua; khong random lai moi 10 tick.
- Mot `ActivityPlan` duoc snapshot cho den khi den noi, timeout, nguy hiem hoac lich lam viec thay doi.
- Khi diem day/bi chan/chunk unload, release reservation va thu toi da 2 diem cung loai; sau do backoff.
- Khong dat waypoint trung gian cho cau thang. Neu Citizens khong the di tu giuong xuong tang duoi, admin sua kien truc/door hoac dat `HOME_EXIT` o diem co the toi; plugin khong teleport.
- Timeout chi dem loaded active ticks. Plan o state `PAUSED_CHUNK_UNLOADED` khong bi tinh la ket va khong force-load chunk.

### GUI

- Lang co menu `Diem sinh hoat`, them/xoa/xem tung loai diem.
- NPC co `Cua nha` tuy chon de giai quyet nha nhieu tang hoac nhieu loi ra.
- GUI kiem tra cung world, safe standing, chunk loaded va `canNavigateTo` tai luc dat. Day chi la kiem tra best-effort; runtime van revalidate bounded truoc moi lan reserve/use.
- Hien trang thai: con trong, dang duoc ai reserve, route fail gan nhat va lan thu lai.

### Khong dua vao season nay

- Doi, khat, tieu thu item, nấu an va nhu cau player.
- A* rieng, waypoint tung block, teleport xuong tang hoac force-load nha.

### Tieu chi thoat

- 10 NPC o nha mot va hai tang deu roi khu giuong trong vong 600 loaded server ticks sau khi thuc day neu co route hop le.
- NPC di lam, di nghi, di cho va ve ngu ma khong co hai runtime tranh Navigator.
- Test wake-up trung gio vao ca, alarm khi dang ngoi, doi role va gio ngu: moi NPC luon chi co mot navigation lease holder.
- Restart khong mat activity point; reservation RAM duoc clear sach.
- Diem bi pha/khong con safe bi vo hieu hoa fail-closed va khong gay log spam.
- Soak 3 ngay Minecraft: khong NPC ket `LEAVING_HOME` hoac mot dich qua 2 phut.

## Season 7 - Nhu Cau Doi Va Khat

**Muc tieu:** moi NPC co nhu cau ca nhan lam dong luc de di an/uong, nhung van on dinh, de hieu va khong pha lich nghe.

### Mo hinh nhu cau

Persist theo UUID trong mot store rieng, vi du `needs.yml`:

- `hunger`: 0-100, 100 la no.
- `thirst`: 0-100, 100 la du nuoc.
- `managed-ticks`, world identity va schema version.
- Trang thai UI: `NO`, `HOI DOI`, `DOI`, `RAT DOI`; tuong tu cho khat.

Cap nhat theo delta loaded/managed ticks co cap, khong tru theo moi tick. Khong dung world time vi no wrap va co the bi `/time set`; khong mo phong nhu cau/offline vo han.

Gia tri khoi dau de can bang:

- NPC moi/migrate lan dau: hunger 55-75, thirst 45-70. Khong reroll gia tri da persist moi lan thuc day.
- Mot bua day du: +45 hunger; mon nhe: +15 den +25.
- Mot lan uong: +40 thirst.
- Cooldown toi thieu giua hai lan an/uong de khong loop.

### State va uu tien

- `SEEKING_WATER -> DRINKING` khi thirst duoi nguong.
- `SEEKING_FOOD -> WAITING_FOR_MEAL -> EATING` khi hunger duoi nguong.
- Khat nghiem trong uu tien hon doi; nguy hiem va ngu van cao hon tat ca.
- Doi/khat nhe cho den break hoac het thao tac dang lam; nghiem trong moi ngat cong viec an toan.
- Dang cam ingredient, dat ca, dat leash, giao dich hoac transaction khong bi cat ngang truoc diem commit/rollback an toan.

### Nuoc uong

- `WATER` point la presentation va reservation. Khi den noi, NPC nhin vao nguon, cam chai/coc roleplay, uong va cap nhat thirst.
- MVP khong rut block nuoc va khong spawn chai item. Nguon phai la cauldron co nuoc, waterlogged/water source duoc allowlist, hoac diem bep duoc admin xac nhan.
- Neu khong co diem nuoc hop le, nhu cau dung o muc canh bao, NPC khong chet va khong spam tim duong.

### Do an

- Chi tieu thu item an duoc nam trong allowlist va co gia tri dinh duong cau hinh.
- Season 7 phai tao transaction journal/WAL dung chung, persist truoc khi doi kho hoac needs. Season 8-10 tai su dung journal nay; Season 9 chi bo sung payload cooking session.
- Moi lan an co `meal-attempt-id` va cac phase `SERVING_RESERVED -> STOCK_DEBITED -> NEED_APPLIED -> COMPLETE/ROLLED_BACK`. Stock debit va hunger credit deu idempotent khi replay sau restart.
- Tru mot suat an khoi kho lang chi khi NPC da den ghe ban an va bat dau an; khong credit hunger neu debit chua commit.
- Neu khong co ghe, NPC co the an dung tai `DINING` point; thieu ghe khong khoa nhu cau.
- Season nay cho phep dung food da co trong kho. Bep that cua Season 8-9 se tro thanh nguon cung chinh.

### GUI va chan doan

- GUI NPC hien hunger/thirst, dich dang toi, suat an dang reserve va ly do bi block.
- Admin co toggle toan server, decay rate, threshold va debug reset; mac dinh feature tat.
- Khong cho admin chinh tung tick hoac tao gia tri am/qua 100.

### Tieu chi thoat

- NPC thuc day, uong, an va di lam dung thu tu ma khong ket o giuong/ban an.
- Hai NPC tranh mot ghe/diem nuoc dung reservation, khong dung chong entity.
- Transaction an khong duplicate khi restart, doi role, nguy hiem hoac navigation timeout.
- Crash sau moi phase reserve/debit/need credit deu phuc hoi theo journal, bao toan tong stock va consumption.
- Khong co do an: NPC van tiep tuc song, bao `thieu suat an`, retry co backoff >= 200 ticks.
- Soak 3 ngay Minecraft voi 20 NPC: moi NPC an/uong trong muc tieu, khong scan toan world va p95 runtime nhu cau <= 1 ms/tick.

## Season 8 - Bep Lang Va Chuoi Cung Ung Bua An

**Muc tieu:** bien Dau bep thanh role phuc vu nhu cau that, co bep, kho nguyen lieu, quay nhan mon va ke hoach nau theo so nguoi dang doi.

### Ha tang bep

Thay mot tam `COOKING` chung bang `KitchenDefinition`:

- Mot hoac nhieu appliance ao gan voi block: `FURNACE`, `SMOKER`; campfire de sau vi co event va slot model rieng.
- `PREP`: crafting table/cutting point de so che.
- `PANTRY`: chest/barrel animation; kho ao van la source of truth.
- `SERVING`: diem nhan mon gan ban an.
- `WATER`: diem nuoc bep co the phuc vu ca nau an va nhu cau uong.

Moi appliance co ID va chi mot owner/session tai mot thoi diem. Hai kitchen khac lang khong duoc lien ket cung block. Season 8 chi claim block va chay timer ao; khong dat item vao block inventory va khong doi trang thai vanilla cua lo.

### Meal request board

- Need system tao `MealRequest` theo village, khong tao mot request moi moi tick/NPC.
- Gom nhu cau thanh batch nho theo recipe, so NPC dang doi va stock target.
- Dau bep chon recipe co ingredient du, appliance dung loai va nhu cau cao nhat.
- Reserve nguyen lieu truoc khi di lay; timeout/role switch/shutdown phai rollback.
- Khong nau vo han chi de day kho; gioi han batch, stock target va output moi ca.

### Recipe schema moi

Moi recipe bep can co:

- `appliance`, `input`, `amount`, `fuel`, `cook-time-ticks`.
- `output`, `output-amount`, `nutrition`, `hydration` tuy chon.
- `servings`, `stock-target`, `priority` va animation/tool.
- Validate Material, fuel, output, thoi gian, graph va tong dinh duong khi reload.

Chuyen `bread` ve role `COOK` neu gameplay muon Dau bep chiu trach nhiem bua an; `CRAFTER` khong nen tao food.

### Runtime dau bep

State de xuat:

```text
IDLE -> CLAIMING_REQUEST -> RESERVING_INGREDIENTS -> GOING_TO_PANTRY
-> COLLECTING -> GOING_TO_PREP -> PREPARING -> GOING_TO_APPLIANCE
-> LOADING -> COOKING -> UNLOADING -> GOING_TO_SERVING
-> SERVING -> CLEANUP -> IDLE
```

- NPC phai di den pantry, prep va appliance that; nhin dung block, cam dung ingredient/tool va swing theo action.
- Khong bien doi input thanh output ngay sau mot timer chung 60 tick nhu runtime hien tai.
- `LOADING/COOKING/UNLOADING` trong Season 8 la presentation + journal transaction ao. Inventory lo that chi bat dau o Season 9.
- Khi khong co request/ingredient/appliance, Dau bep patrol ngan trong bep hoac nghi, khong spam bat dau task.

### Tieu chi thoat

- 1 Dau bep phuc vu duoc it nhat 3 NPC doi theo request gom batch ma khong nau thua stock target.
- Hai Dau bep dung hai appliance song song; khong claim cung request, ingredient hoac lo.
- Thieu nguyen lieu/nhiên lieu/appliance hien chan doan ro va rollback reserve.
- Tat bep, doi role, mua, ngu hoac bao dong chi dung tai diem transaction an toan.
- Recipe invalid/cyclic lam bep fail-closed, khong lam mat kho.

## Season 9 - Nau That, Khoa Lo Va Phuc Hoi Giao Dich

**Muc tieu:** mot phien nau thuc su dung `FURNACE`/`SMOKER`, co ingredient, nhien lieu, thoi gian nau va output that; nguoi choi khong the rut item qua cac co che Paper duoc ho tro hoac pha lo de nhan ban do.

### Dinh nghia `real cooking`

- Dau bep claim mot appliance block cu the.
- Transaction journal reserve input va fuel tu kho lang truoc khi nap lo.
- Lo that hien ingredient/fuel, phat trang thai lit/smoke/sound va chay dung `cook-time-ticks`.
- Output chi duoc commit vao kho lang sau khi smelt thanh cong va Dau bep hoan tat `UNLOADING`.
- Item trong inventory lo trong luc co session chi thuoc session do; no khong phai do mien phi cho player.

### `CookingSession` persist

Moi session can co:

- `session-id`, village ID, cook UUID, appliance ID va block location.
- Recipe ID, input/fuel da reserve, expected output, start/deadline.
- Phase: `RESERVED`, `LOADED`, `COOKING`, `COOKED`, `COMMITTED`, `ROLLED_BACK`.
- So luong `reserved`, `loaded`, `consumed`, `residual`, `produced` va snapshot tung slot de reconcile dung sau partial smelt.

### Khoa chong lam dung

Trong khi session dang active, huy va thong bao ro cho player doi voi:

- Mo/lay/dat/shift-click/number-key/drag item trong inventory lo.
- Hopper/dropper/hopper minecart day vao hoac hut ra (`InventoryMoveItemEvent`).
- Pha block, piston dich chuyen, explosion va thay block bang plugin khac neu event co the chan.
- Dat fuel/input ngoai vao slot hoac lay output truoc khi Dau bep commit.

Neu inventory dang co viewer thi khong claim, hoac dong viewer truoc khi reserve. Luc enable, lock index phai duoc nap truoc khi player co the mo inventory; `InventoryOpenEvent` bi chan den khi reconcile xong.

Paper khong the chan moi thay doi truc tiep tu plugin/NMS khac. Cam ket khoa chi ap dung cho event cancellable duoc ho tro. Snapshot thay doi bat thuong se freeze session, reconcile item session xac dinh duoc va rollback/yeu cau admin; khong tu dong cap ca refund va output.

Khong khoa ca khu bep. Chi khoa dung appliance dang co session. OP co permission bypass de chan doan, nhung bypass phai huy session va rollback/kiem ke truoc khi cho thao tac, khong duoc lay truc tiep output dang journal.

### Thu tu giao dich an toan

1. Claim appliance va tao session ID duy nhat.
2. Reserve input/fuel trong journal; chua cap output.
3. Dat dung so luong presentation vao lo va danh dau session active.
4. Cho vanilla/Paper smelt event hoac timer appliance hop le hoan thanh.
5. Xac minh block, recipe va output khop snapshot.
6. Dau bep unload; doi chieu va xoa item session khoi lo, sau do commit output mot lan vao kho ao.
7. Release appliance, request va meal reservation.

Neu loi truoc buoc 6: dau tien thu hoi/account residual va output vat ly cua session, sau do chi refund luong journal quy dinh, toi da mot lan. Neu loi sau commit: xoa stale presentation neu con, khong rollback va khong commit lai.

### Restart va chunk lifecycle

- Plugin disable dung: pause session, snapshot lo va persist journal truoc khi save economy.
- Enable: reconcile tung session voi block va inventory thuc te truoc khi cho player mo lo.
- Chunk unload khong duoc force-load; session pause. Chunk load moi reconcile va tiep tuc/rollback.
- Lo mat/hong/world khong ton tai: rollback reserve theo journal; khong spawn item xuong dat.
- Lo co item la truoc luc duoc lien ket: GUI tu choi claim cho den khi admin lam trong hoac import co chu y.
- Cook progress dung loaded active ticks, khong wall-clock deadline; server offline/chunk unload khong tu nau xong.

### Tuong thich hopper va lo cua player

- Chi appliance duoc admin lien ket voi LivingNPC moi bi quan ly.
- Khi khong co active session, lo hoat dong vanilla binh thuong neu policy cho phep.
- Khuyen nghi mode an toan: appliance LivingNPC la lo chuyen dung; player co the xem trang thai qua GUI read-only, khong dung chung de nau ca nhan.

### Tieu chi thoat

- Player khong lay duoc input, fuel hoac output qua click, drag, shift-click, hotbar, hopper, pha block hay explosion khi session active.
- 100 lan nau thanh cong cho dung 100 lan output, khong duplicate/mat item.
- Restart tai moi phase deu commit hoac rollback dung mot lan.
- Hai Dau bep/hai lo va mot Dau bep/nhieu lo khong deadlock claim.
- Khi recipe/appliance bi sua luc dang nau, session cu reconcile an toan truoc khi config moi duoc dung.
- Co integration test event lock va smoke test Paper that; unit test timer don thuan la chua du.
- Test viewer mo san, startup chua reconcile, double-click/creative action, stale output sau commit, output bat thuong va crash ngay truoc/sau luc fuel bi tieu thu.

## Season 10 - Phuc Vu Bua An Va Doi Song Hoan Chinh

**Muc tieu:** noi Dau bep, meal request, ghe ban an va nhu cau thanh mot vong doi song ro rang tu sang den toi.

**Trang thai source ngay 2026-08-13:** da bat dau foundation thuần domain, mac dinh tat va chua noi scheduler/live runtime. Source co policy ba bua theo world full-time, demand snapshot theo batch/buffer, quota visitor tach resident va serving reservation idempotent trong RAM. Chua co transaction persist, hunger/thirst, kitchen/cooking session hoac meal output cua Season 7-9, vi vay khong duoc bat `season-10.enabled` hay debit kho tu foundation nay.

### Gameplay

- Bua sang sau khi roi nha, bua trua theo break, bua toi truoc gio ngu.
- Dau bep dua batch mon ra `SERVING`; NPC dang doi reserve mot serving, di toi ban, an va giai phong ghe.
- Suat an la ledger trong kho lang, khong spawn item entity tren ban. Reservation serving thay the direct-food reservation cua Season 7, khong debit them lan hai.
- Presentation co tray/item tren tay, ngoi, animation an va hoi thoai ngan theo mon.
- Nuoc do bep phuc vu co the duoc lay tai `WATER` point; bep khong tao nuoc vo han neu server bat chi phi tai nguyen.
- Khong co Dau bep: NPC duoc dung food du tru don gian theo policy, de lang khong soft-lock.

### Can bang

- Moi NPC toi da 2-3 bua/ngay Minecraft tuy decay config.
- Dau bep nau theo demand snapshot + buffer nho, khong nau theo moi NPC rieng le.
- Bao toan reserve wheat, seed, carrot va nguyen lieu nghe khac truoc khi tao meal request.
- Visitor/caravan chi dung serving khi co quota rieng; khong rut het bua cua resident.

### Tieu chi thoat

- Vong `thuc day -> roi nha -> uong/an -> di lam -> nghi/an trua -> lam tiep -> an toi -> ve ngu` chay 3 ngay Minecraft lien tuc.
- Thieu ghe, thieu Dau bep, thieu mon, bep hong va bao dong deu co fallback, khong ket state.
- Kho, request, serving, hunger/thirst va cooking journal khop sau restart.
- 20 resident + 2 Dau bep khong tao hon mot request/NPC/tick, khong scan toan world va giu ngan sach TPS season.

## Season 11 - Mua Kinh Te Va Chuan Bi Mua Dong

**Muc tieu:** tao nhip kinh te Xuan/Ha/Thu/Dong de lang thay doi muc tieu du tru, nhu cau xuat hang va uu tien lao dong ma khong phu thuoc plugin season khac.

**Trang thai source ngay 2026-08-13:** da co foundation domain/config mac dinh tat. Policy tinh mua tu world full-time theo chu ky co cau hinh, tao snapshot bat bien gom cycle/day-in-season va ba modifier phan tram. Foundation chua duoc scheduler goi, chua sua stock target, visitor demand, role selection hoac san luong thuc te.

### Gameplay

- Xuan uu tien gieo trong va chuan bi nong trai; Ha giu nhip san xuat/cau ca; Thu tang thu hoach va tich tru; Dong tang reserve, giam xuat hang va uu tien che bien.
- Modifier chi ap dung len target, demand va priority tai thoi diem planner tao snapshot; khong sua base config va khong cong don qua moi tick/reload.
- Khong tang truc tiep output moi action. Lao dong mua vu chi de xuat/reassign NPC ranh qua authority/lease cua Season 6, khong doi nghe vinh vien.
- Chu ky dung world full-time de `/time set` khong lam quay lui mua; `start-day` cho phep can moc khi admin san sang phat hanh.

### Tieu chi thoat

- Qua du bon mua va restart khong doi cycle/day-in-season; boundary chinh xac tai ngay bat dau moi mua.
- Planner tao cung mot snapshot cho cung village/day va khong nhan modifier hai lan.
- Winter reserve khong lam meal serving, hat giong hoac thuc an vat nuoi soft-lock; export chi dung phan vuot reserve.
- Hai lang co the dung policy rieng ma khong tron snapshot, request hoac stock target.
- Khong scan world/NPC theo tick; chi tinh snapshot tai rollover ngay hoac khi config reload hop le.

## Thu Tu Trien Khai Bat Buoc

1. Cong kiem ke release Season 1-5.
2. Season 6: activity point + wake-up exit + activity authority.
3. Season 7: hunger/thirst + consumption transaction + water/dining behavior.
4. Season 8: kitchen definition + meal request + Dau bep state machine.
5. Season 9: physical appliance + lock event + persistent cooking journal + restart reconciliation.
6. Season 10: serving, meal schedule, fallback va can bang toan lang.
7. Season 11: chu ky kinh te, modifier snapshot va lao dong mua vu.

Foundation Season 10-11 co the duoc viet/test som, nhung Season 10 chi duoc noi runtime sau khi journal Season 7, request/kitchen Season 8 va cooking reconciliation Season 9 ton tai; Season 11 chi noi planner sau khi Season 10 dat tieu chi thoat.

Khong gop Season 7-9 thanh mot lan phat hanh. Neu lam cung luc, loi pathfinding, loi nhu cau va loi transaction lo se kho tach, de gay duplicate item hoac NPC ket lau dai.

## Danh Sach Test Xuyen Season

- Nha mot tang, hai tang, cau thang hep, cua go dong va bed sat tuong.
- Khong co `HOME_EXIT`, diem bi chan, diem day, chunk unload va khac world.
- Hai NPC tranh mot ghe; 20 NPC dung nhieu diem.
- Khat va doi cung luc, bat dau ca lam, nghi trua, bao dong va gio ngu.
- Khong co food, khong co water, pantry day va output dat stock target.
- Mot/two cooks, mot/two appliances, role switch va cook despawn.
- Restart tai moi cooking phase; crash sau reserve, sau load, sau smelt va sau commit.
- Restart tai moi meal phase; crash sau serving reserve, stock debit, need credit va release.
- Player click/drag/shift-click/hotbar, hopper, pha lo, explosion va admin bypass.
- Viewer mo lo truoc luc claim, open trong startup reconcile, double-click/creative va stale physical output sau commit.
- Recipe reload khi dang nau va appliance bi xoa khoi config.
- Hai lang cung world khong tron point, request, kho, needs hay cooking session.
- `/time set`, day wrap, NPC despawn/respawn, world unload va feature toggle/reload khi dang co plan.
- File YAML hong/cut ngan, loi ghi, migration fail va khoi phuc backup fail-closed.
- Moi crash test phai giu invariant: `virtual stock + session-owned physical items + committed consumption/output` chi thay doi dung mot lan.

## Viec Chua Dua Vao Roadmap Nay

- Hunger/thirst cua player.
- NPC chet, benh hoac combat do doi/khat.
- Restaurant economy voi player, tip, dynamic pricing lien tuc.
- Dat mon bang Gemini hoac de LLM quyet dinh recipe/so luong.
- Offline production khi chunk unload.
- Hang that luu lau dai trong lo, ruong thu hoac item entity presentation.
