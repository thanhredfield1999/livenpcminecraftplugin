# LivingNPC - Lo Trinh Theo Season

Tai lieu nay dung "season" nhu mot moc phat hanh gameplay. Plugin chua co he thong tu dong Xuan/Ha/Thu/Dong, vi vay moi season chi duoc mo khi season truoc dat du tieu chi thoat.

## Nguyen Tac Chung

- Chi mo runtime da co hanh vi that, co gioi han scan va fail-closed khi thieu ha tang.
- Moi NPC chi co mot runtime dieu khien Citizens Navigator tai mot thoi diem.
- Khong scan toan world, khong force-load chunk, khong teleport de chua pathfinding.
- World mutation chi chay trong vung da gan va duoc WorldGuard cho phep.
- Kho ao la source of truth; animation, item tren tay va entity tam chi la presentation.
- Feature moi mac dinh tat cho den khi qua unit test, smoke test va soak test.
- Combat, Gemini network client va offline production tiep tuc tam hoan.

## Season 1 - Lang Song Dong

**Trang thai kiem ke `2026-08-13`:** duoc mo trong source va co tren live, nhung chua co du bang chung smoke/soak/performance de dong gate thoat.

**Muc tieu:** lam nen tang doi thuong on dinh, de nguoi choi cam thay NPC co lich song thay vi la may san xuat.

**Noi dung:**

- Nguoi dan: ngu, ve nha, patrol duong, nhin xung quanh, quan sat nguoi choi, ngoi ghe va tro chuyen tai cho/diem ngam canh.
- Nong dan: bon crop vanilla, inspect, harvest, replant, giao kho theo batch va nghi trua.
- Ho so nhan vat va quan he la tuy chon; khong anh huong readiness.
- GUI thiet lap lang, nha, kho, ruong, ghe va lich lam viec.

**Can harden truoc khi mo:**

- Crop khong the toi phai duoc bo qua, khong khoa ca queue.
- Social chi duoc quyen dieu khien Nguoi dan va Nong dan ngoai ca.
- Phat hien nguy hiem la truy van thuan, khong tu gan target cho Zombie.
- Smoke test wheat, carrot, potato, beetroot; lunch; hai delivery chest; hai lang.

**Tieu chi thoat:**

- 3 ngay Minecraft lien tuc khong NPC ket phase qua 2 phut.
- Restart sach khong mat lang, NPC, kho, quota, lich hoac reservation.
- TPS trung vi >= 19.8; LivingNPC trung binh <= 1 ms/tick trong scope season.
- Khong co scan toan world, force-load chunk hoac log lap theo moi tick.

## Season 2 - Song Nuoc Va Nong Trai

**Trang thai kiem ke `2026-08-13`:** experimental; co source va dau vet van hanh tren binary live cu, nhung source release hien tai khoa runtime va chua qua soak gate.

**Muc tieu:** mo rong chuoi thuc pham bang Ngu dan va Chan nuoi sau khi daily-life on dinh.

**Noi dung:**

- Ngu dan: tim source water, di toi bo an toan, cam can, tha cau, cho, keo va dua ca vao kho.
- Ty le ca: cod 60%, salmon 25%, pufferfish 13%, tropical fish 2%; quota 12 ca/ca.
- Chan nuoi: multi-pen, breed, cap vat nuoi, thu gom trung/long, dua con xong ve va patrol chuong.
- Nhieu Rancher co the lam song song tai cac chuong khong chong nhau.

**Can harden truoc khi mo:**

- Fisher luon giu can trong phase cau, clear FishHook khi suspend va backoff sau path fail.
- Rancher claim dung chinh xac chuong dang thao tac; task identity khong reset theo moi scan.
- Test chuong bi chan, delivery chest du phong, door/fence gate va hai Rancher.
- Entity scan chi chay trong radius da cau hinh va khong nhanh hon 200 ticks khi tim cong viec moi.

**Tieu chi thoat:**

- Soak 30 phut Fisher va Rancher, khong hook/leash/helper entity ton du.
- Khong phase cau nao co tay AIR qua hai runtime tick.
- Khong cung mot diagnostic/task-start log lap lai khi state khong doi.
- TPS trung vi >= 19.8; LivingNPC p95 <= 4 ms/tick voi quy mo test.

## Season 3 - Cho Lang

**Muc tieu:** bien san luong thanh mot vong kinh te co presentation ro rang, khong cho visitor rut kho vo han.

**Trang thai kiem ke `2026-08-13`:** MVP da trien khai trong source va mac dinh tat. Binary live cu dang mo visitor/merchant, nhung chua co smoke/soak release va khong duoc cong nhan la Season 3 live.

**Noi dung:**

- Dan buon co seller point va buyer point rieng, mo/dong quay theo lich.
- Visitor sinh tai cong, reserve mot quay dang mo, di bo toi mua, roi quay lai dung cong.
- Giao dich atomic, idempotent; visitor va finite wallet khong persist nhu resident.
- Market/scenic point cu van la diem xa hoi, khong bi tu dong doi thanh quay.

**Dieu kien mo:**

- Them reserve toi thieu cho wheat, seeds va carrot dung boi nghe khac.
- Them demand snapshot cho moi visit; khong reroll trong cung chuyen.
- Visitor van reserve dung quay khi Merchant doi phase; timeout phai release reservation.
- Ban dau cap toi da 3 visitor toan server va feature mac dinh tat.

**Tieu chi thoat:**

- Giao dich khong duplicate khi merchant dong quay, visitor timeout hoac plugin shutdown.
- Entity visitor tro ve baseline sau moi chuyen va sau restart.
- Hai Merchant/hai quay/hai Visitor khong tranh Navigator hay reservation.
- Kho va tien giua cac lang khong bi tron.

## Season 4 - Lang Chuyen Mon Hoa

**Trang thai kiem ke `2026-08-13`:** source only/experimental; binary live cu co chay mot so nghe nhung khong trung implementation source moi va chua dat integration/performance gate.

**Muc tieu:** dua Cook, Crafter, Miner va Security tu prototype thanh nghe co chuoi cung ung ro rang.

**Noi dung:**

- Cook/Crafter san xuat theo stock target va recipe cau hinh, co animation tai dung station.
- Miner dung cac `Khu dao 2x2` do admin dat thu cong, khong dung WorldGuard de xac dinh pham vi dao.
- Moi lang co nhieu Khu dao; NPC tu chon khu con block hop le va co duong di ngan nhat.
- Security patrol, rung chuong va phat alarm de worker tim noi an; chua co combat.
- Request board va reservation dau vao ngan hai NPC cung tieu mot nguyen lieu.

**Dieu kien mo:**

- Khong hard-code recipe gameplay trong runtime; validate recipe graph khi load.
- Cache WorkZone validation; khong quet 1.183 block moi 10 ticks/NPC.
- Miner chi quet dung 4 cot block cua tung Khu dao 2x2, khong quet hang/radius lon.
- Miner khong tao tai nguyen ao lap lai tu cung mot block; block da dao phai co depletion/restoration ro rang.
- Weather policy tach theo nghe: nghe trong nha khong dung vo ly khi mua.

**Tieu chi thoat:**

- Moi recipe co test atomic input/output/quota/rollback.
- Miner co integration test multi-zone 2x2, path fallback va restoration end-to-end.
- Alarm khong quet hostile theo moi global tick cua tung NPC.
- LivingNPC trung binh <= 2 ms/tick, p95 <= 4 ms/tick trong quy mo test season.

## Season 5 - Le Hoi Va Doan Thuong Nhan

**Trang thai kiem ke `2026-08-13`:** source only; config source mac dinh tat, chua co bang chung smoke/soak hoac live.

**Muc tieu:** them bien the theo thoi diem chi sau khi kinh te lang da co stock reserve va market on dinh.

**Noi dung du kien:**

- Market day/event theo lich co dinh, khong dynamic pricing lien tuc.
- Doan khach co leader la authority duy nhat cua giao dich; follower/pack animal chi la presentation.
- Formation long, diem tap hop, cho nghi dung chung SeatManager va lifecycle timeout ro rang.
- Giao thuong lien lang chi duoc mo sau khi transaction journal va reserve policy da soak test.

**Khong dua vao MVP:**

- Combat caravan, hang that trong ruong thu, moi follower mua rieng, route xuyen map force-load chunk.
- Gia bien dong lien tuc, offline simulation, Gemini quyet dinh gia/so luong/toa do.

## Ngan Sach Hieu Nang Bat Buoc

| Hang muc | Gioi han ban dau |
|---|---:|
| Global runtime | 10 ticks |
| Farmer plot scan | >= 100 ticks/NPC, radius <= 8 |
| Ranch scan | >= 200 ticks/Rancher |
| Fisher retry | 500-900 ticks; path fail co backoff |
| Work-zone validation | cache 200 ticks khi zone khong doi |
| Resident path discovery | >= 600 ticks/lang, 256 cot/tick |
| Visitor | toi da 3 toan server |
| Navigation timeout | 400 ticks, khong stuck teleport |
| Production | 32 output chinh/NPC/ca |
| Ranch pens | toi da 9/lang |
| Mining zones | toi da 16/lang, moi zone dung 2x2 cot block |

Dung `spark profiler` hoac Paper timings tren server test. Neu khong co so do chi phi va soak test, khong nang feature tu experimental len stable.

## Thu Tu Trien Khai De Xuat

1. Chot Season 1 va sua het state-machine regression.
2. Soak Fisher/Rancher de mo Season 2.
3. Them stock reserve va smoke test Merchant/Visitor de mo Season 3.
4. Thiet ke request board/recipe config truoc khi mo Season 4.
5. Chi lam event/caravan sau khi bon season dau dat ngan sach TPS.
