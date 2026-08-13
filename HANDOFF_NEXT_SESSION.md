# LivingNPC - Handoff Phien Moi

## Muc Tieu Phien Moi

Chi tap trung vao **LivingNPC doi thuong, nhan vat, quan he, nghe nghiep, GUI va farmer runtime**.

- Tam hoan combat Zombie, arena, damage, loot va nang cap chien dau.
- Khong deploy hoac bat combat trong production.
- Source co cac file combat thu nghiem, nhung phien moi khong tiep tuc phan nay neu user chua yeu cau lai.

## Project Va Version

- Source: `E:\AI.WORK\living-npc-plugin`
- GitHub chinh cua plugin: `https://github.com/thanhredfield1999/livenpcminecraftplugin`
- Remote local: `livingnpc`
- Server live: `F:\minecraftserver\villagedefense2026`
- JAR live before the next deployment: `F:\minecraftserver\villagedefense2026\plugins\living-npc-0.5.0-SNAPSHOT.jar`
- Source va live release candidate: `0.6.0-rc.2`.
- Paper: `1.21.11-131`
- Java build target: `21`; server dang chay Java `25.0.1`
- Citizens: `2.0.42-SNAPSHOT` build `4173`
- WorldGuard: `7.0.16`

## Trang Thai Live Hien Tai

- Season 2 RC `0.6.0-rc.1` da deploy luc `23:26` ngay `2026-08-13` sau khi xac nhan Paper dung sach.
- Paper khoi dong thanh cong: `Done (30.089s)` luc `23:26:44`; LivingNPC ghi dung marker `LivingNPC Season 2 enabled` va khong co ERROR/Exception.
- JAR live SHA-256: `3743BE7ABD7AFE3FEFDA455661DBF8073F8BF29FEBC09B191474AF96656C43E2`.
- JAR live da co hai fix quan trong:
  - NPC `LOOKING_AROUND` nhin ngang tam mat, khong con de bi nguoc len troi.
  - Khong xoa `farmers.yml` khi LivingNPC enable truoc luc Citizens nap xong registry.
- Backup gan nhat: `F:\minecraftserver\villagedefense2026\plugins\LivingNPC-backup-20260813-232613`.
- Backup gom JAR RC truoc do va toan bo `plugins\LivingNPC`; hash ca 8 file du lieu/config YAML da doi chieu khop sau deploy.
- Combat source van ton tai de lam sau, nhung bootstrap/listener/tick/command combat khong con duoc dang ky trong ban deploy.
- `economy.yml` live da migrate schema `3`, giu nguyen kho `wheat: 11`, `wheat_seeds: 21`; schema moi persist quota san luong theo nghe.
- LivingNPC enable khong co ERROR/Exception; Oraxen bao khong co model/texture hong.
- Khong dung `/reload`, PlugMan hoac hot-loader. Muon thay JAR phai `stop` Paper sach, backup, thay JAR va start lai.

## Du Lieu NPC Hien Tai

Lang da co:

- ID: `stillcliff_1`
- Ten: `Lang StillCliff`
- World: `StillCliff`

Hai NPC LivingNPC da duoc tiep nhan va du lieu da song qua restart:

- Citizens ID `2`: `ThanhRedfield`
- UUID: `46a5553d-cedc-428f-b51a-4f5ddec03c9b`
- Citizens ID `3`: `Keyden_Redfield`
- UUID: `084e73d7-7aa8-42e1-b7e4-a8dcb4bd9484`

File live:

- `plugins\LivingNPC\farmers.yml`: dang giu ca hai NPC.
- `plugins\LivingNPC\villages.yml`: dang giu `stillcliff_1`.
- Hai NPC chua gan plot.
- Harvest va Plant dang tat.
- `combat-arenas.yml` chua co va combat chua tung duoc bat live.

## Ho So Nhan Vat La Tuy Chon

User da chot: NPC co the chi la worker, khong bat buoc co cau chuyen.

- Toggle `Hồ sơ nhân vật` persist theo NPC va mac dinh TAT.
- Khi tat: GUI/status khong hien lore va right-click dung fallback dialogue cong viec.
- Khi bat: moi hien biography/personality/goals/relationships va dialogue rieng.
- Toggle nay khong anh huong readiness hay farmer runtime.

ThanhRedfield va Keyden_Redfield:

- La hai anh em den tu lang Redfield.
- Thanh la anh; binh tinh va co trach nhiem bao ve em.
- Keyden la em; gan gui va thuong di cung Thanh.
- Thanh thien ve cung; Keyden thien ve kiem.
- Hai nguoi thuong dong hanh, kiem tien, mua sam/nang cap trang bi va tim noi can giup do.
- Combat that de sau. Truoc mat can uu tien lore, hoi thoai, quan he anh-em va hanh vi di cung tu nhien.

Model/persistence da co biography, personality, preferred weapon, goals va relationship theo UUID; file cu migration an toan voi field rong.

## Tinh Nang Da Hoan Thanh

- Nhieu lang doc lap, ke ca cung world.
- Kho ao va tien tach theo village ID.
- Ruong that chi la diem giao hang/animation; kho ao la source of truth.
- Farmer bounded scan cho wheat, carrot, potato va beetroot.
- Di den vi tri dung an toan, nhin cay, cam tool, harvest, replant dung loai va giao kho.
- Readiness fail-closed neu thieu lang, plot, kho, Master AI, Harvest hoac Plant.
- Ngoai ca co wander, look around, watch player, rest va social tai cho/diem ngam canh.
- Social chi trong cung lang; huy khi mua, co monster hoac NPC dang lam viec.
- GUI/command/status chinh da Viet hoa.
- Multi-role, lich rieng, XP/level da co model; farmer la world-action runtime hoan chinh duy nhat.
- Gemini dang tat, khong co network client va khong ton chi phi.
- Farmer crop mutation da atomic replant age 0; path backoff khong lam mat work queue.
- Co `/livingnpc ganlang <npc-id> <lang-id>`; home/plot/kho bi reject neu khac world cua lang.
- Sale chi chay khi ca ket thuc, khong ban nham luc mua; role switch khoi farmer cung ket ca.
- Shutdown suspend runtime truoc khi save; `economy.yml` load loi se khoa ghi fail-closed.
- Khi GUI dang cho click phai chon block, `/lnpc cancel` huy session; chat hien ro lenh nay.
- Worker Citizens duoc set unprotected; Zombie gan worker duoc target vao NPC, trong khi `Tranh quai vat` lam farmer bo viec chay ve nha.
- Look pose dung truc tiep Bukkit Entity#setRotation, khong dung Citizens faceLocation: di/cho/sinh hoat pitch=0; crop/chest tinh pitch xuong va clamp 60 do. Khi bind se remove metadata RESET_PITCH_ON_TICK cu (live Steve tung luu true va pitch lech).
- Wheat lay drop hat truoc khi reset age: giu 1 hat de trong lai, hat du vao kho `wheat_seeds` cho rancher/chicken runtime sau.
- Ruong live nam trong WorldGuard `safezone` spawn. Farmer bo qua BUILD protection cua player trong plot da gioi han, nhung van dung neu `BLOCK_BREAK`/`BLOCK_PLACE` bi DENY ro rang.
- Live da xac nhan Steve thu hoach 4 lan: XP 40, kho co `wheat: 4`, `wheat_seeds: 8`.
- Chan doan moi: Steve da dat XP 110, kho `wheat: 11`, `wheat_seeds: 21`, nhung quota bi day `32/32` vi ban cu tinh ca hat du vao output ca. Ban moi chi tinh nong san chinh vao quota; byproduct hat chi chiem kho. `economy.yml` schema v2 reset bo dem quota bi nhiem dung mot lan, giu nguyen kho/tien.
- Farmer gio giu hoe suot ca/di giua crop; chi doi sang hat khi gieo va bo tool khi ve nha/di kho.
- Farmer xu ly het batch crop da scan roi moi di kho mot lan, khong giao sau tung cay.
- Sau khi giao, NPC quay ve diem dung an toan o vien plot (tim Y +/-2), khong navigator thang vao block cay/tam plot va khong can dat lai plot de reset state.
- GUI NPC co danh muc nghe that: `Nguoi dan`, `Nong dan`, `Ngu dan`. Click trai chon nghe, click phai chinh lich nghe da mo.
- `Nguoi dan` runtime that: wander/watch/rest quanh nha, khong san xuat va khong mutate block. `Nong dan` dung farmer runtime. `Ngu dan` hien ro nhung bi khoa cho den khi co fishing runtime that.
- Nut chung la `NPC hoat dong`, bat/tat MASTER cho nghe dang chon; khong con lay `Lam nong` lam cong tac trung tam.
- Man NPC phan loai theo active role: phan chung co Nha/Chon nghe/NPC hoat dong; Khu ruong/Ban kinh/Sell/readiness chi hien va click duoc khi chon Nong dan. Nguoi dan khong thay config ruong; nghe sau co config rieng.
- Lang co submenu `Khu nghe & khach vang lai`: khu go can STONECUTTER+CRAFTING_TABLE; nau an can FURNACE+CRAFTING_TABLE; che tao can CRAFTING_TABLE+SMITHING_TABLE+ANVIL (chap nhan anvil nut/hong). Validator bounded radius 6, Y +/-3.
- Role `Khach vang lai` la temporary, khong cho resident chon: admin phai set `visitor-gate` va `market-point`; spawn dung tai gate, di bo toi cho, mua hang co gia bang vi random, tru kho + cong tien lang atomically, di bo ve dung gate roi destroy; khong persist restart.
- Visitor mac dinh TAT; GUI ha tang bat/tat toan server sau khi co diem cho. Cap mac dinh 3, interval 1200-2400 ticks, chi spawn khi chunk loaded va co player gan.
- Village delivery storage da la list khong gioi han; migrate legacy `delivery-chest` vao `delivery-locations`. GUI moi lan set la them chest/barrel. Farmer sort gan nhat, yeu cau safe standing + canNavigateTo, skip blocked/high/unloaded, path fail thu chest ke; stuck teleport tat.
- Khu `RANCH` can HAY_BLOCK + bat ky *_FENCE/*_FENCE_GATE. Role `Chan nuoi` selectable khi khu da dat.
- Rancher breed vanilla: cow/sheep 2 wheat, chicken 2 wheat_seeds, pig 2 carrot tu kho ao; chi 2 adult ready cung loai, cap moi loai theo lang mac dinh 8.
- Vuot cap, moi cycle giet toi da 2 adult du, giu it nhat 2 adult lam giong; cam kiem/nhin/vung tay. EntityDeathEvent chuyen drop mob that vao kho ao va clear ground drops neu store thanh cong.
- Fix GUI chon nghe: `Chan nuoi` khong con bi sach huong dan ghi de slot `13`. Shift + click phai NPC mo thang setup; click phai thuong van hoi thoai.
- Tat ca 9 GUI deu fill o trong bang `BLACK_STAINED_GLASS_PANE`; danh sach 54 slot chi dung `0-44`, hang dieu khien `45-53`. GUI Ha tang 36 slot: khu nghe `9,11,13,15,17`; cho `20`; cong `22`; visitor `24`; quay lai `35`. Co test layout chong trung slot.
- Hoi thoai da co nhieu bien the theo role, phase, gio va mua cho Nguoi dan/Nong dan/Chan nuoi/Ngu dan; chi phat khi click phai, cooldown 2 giay, khong spam chat va khong goi Gemini.
- Role `Ngu dan` da co runtime that, dung `Diem cau` VillageWorkZone can WATER. NPC tim nuoc nguon + bo dung an toan + target Citizens co the toi, cam can, tha cau, cho, keo day va khoi phuc item tay cu khi dung.
- Ngu dan chon theo balance user chot: 25-45 giay/lan thu, 70% thanh cong, COD 60% / SALMON 25% / PUFFERFISH 13% / TROPICAL_FISH 2%, toi da 12 ca/ca. Ca vanilla vao kho lang; chua co gia nen khong tu ban.
- Khong hook sau LiteFish `5.9.7`: API StartFishingEvent/CatchEvent bat buoc `Player`, Citizens khong phai Player. Runtime song song an toan, khong gia metadata/can nang LiteFish va khong spawn FishHook/item entity.
- Fisher quota persist rieng trong `economy.yml` schema 3, van ton trong quota chung 32/ca; config min/max duoc normalize, kho day/quota day thi dung, runtime cleanup khi NPC tat/xoa/despawn.
- Fix live Steve: active-role Farmer nhung `harvest=false`, `plant=false` khong con noi dang tim crop. GUI detail co nut rieng `Lam nong: BAT/TAT`; chi bat khi ruong+kho hop le.
- Fix live Jumonka Rancher: neu bo/cuu thieu wheat thi tiep tuc xet ga thay vi return; navigation nho target, khong reset moi 0.5 giay; click phai bao ro 2 ga truong thanh/cooldown/hat/cap/ca/nguoi choi/duong di.
- LivingNPC navigation co Citizens `DoorExaminer` cuc bo cho Farmer/Rancher/Fisher/Visitor: mo/dong cua go va fence gate tren bat ky route hop le; khong doi Citizens global `open-doors: false`.
- Nguoi dan co patrol `DIRT_PATH` that: cache chung theo lang, radius 32, Y +/-4, scan chunk loaded chia 256 cot/tick, reservoir cap 512, target 8-24 block, cooldown 8-18 giay. Ban dem/mua van ve gan nha.
- Farmer co daily plan nghi trua o giua lich nghe: mac dinh 1000 tick, ve gan nha/cat cuoc, sinh hoat nhe, sau do tu quay lai ruong. Nghi trua khong ket thuc ca, khong ban kho va khong reset quota; ho tro lich tuy chinh qua nua dem.
- Ha tang lang co `Ghe nghi & ban an`: admin nhan `Them ghe` roi click phai Stair. Stair khong co block ran phia truoc la ghe nghi; co block ran phia truoc la ghe ban an. Huong Stair duoc luu lam yaw ngoi co dinh.
- Nguoi dan co the chiem ghe nghi con trong sau patrol; Nong dan uu tien ghe ban an trong `LUNCH_BREAK`. Runtime dung Citizens `SitTrait`, khoa yaw/pitch, cam banh mi va vung tay khi an, dung day bang sneak ngan roi tiep tuc phase cu.
- Reservation ghe chi ton tai RAM va duoc giai phong khi dung day, ghe hong, mat activation, gap nguy hiem, den gio ngu, doi role, suspend hoac plugin shutdown. Khong tu spawn ArmorStand rieng; Citizens quan ly helper entity.
- Kho ao cua lang tam thoi vo han qua `economy.unlimited-storage: true` vi he thong ban chua hoan thien. Quota san luong theo ca/nghe van giu nguyen de tranh NPC san xuat vo han; doi flag ve `false` sau nay se bat lai `economy.inventory-capacity`.
- Khu nghe (gom Khu chan nuoi) la ha tang dung chung theo lang, khong gan rieng tung NPC. Ranch runtime quet bounded radius 6, Y +/-3; ho tro bo/cuu/ga/lon/tho, thuc an wheat/wheat_seeds/carrot. Coordinator chi cho 1 NPC thao tac khu/bon dan chong lap moi luc; setup reject cung loai khu cua lang khac neu hai ban kinh bi chong.
- Rancher khi ranh patrol diem dung an toan trong khu thay vi dung im. Runtime ghi nho UUID vat nuoi da tung o trong khu trong phien server; con bi xong trong recovery radius 12 duoc NPC dắt bang leash ve, khong teleport, khong nhan mob hoang va khong gian con dang bi player dắt. Citizens DoorExaminer tu mo/dong door va fence gate theo route; da bo timer LivingNPC mo cong them 60 tick de tranh cong mo lau lam xong them vat nuoi.
- Multi-chuong la implementation chuan dung chung cho moi season, khong tao module/schema ban sao. Moi lang co toi da 9 `ranch-pens.<ranch-id>.center`; `work-zones.ranch` cu chi migrate mot lan thanh `ranch_1` va khong duoc ghi song song tro lai.
- GUI `Chuong trai` list tung chuong, quet bounded radius 6/Y +/-3, hien tat ca loai + so luong; neu chi mot loai thi ten item hien loai va so luong. Click trai teleport admin toi chuong; Shift + click phai xoa; chuong cung/lang khac bi reject neu vung quet chong nhau.
- Rancher luan phien cac chuong. Claim da chuyen tu khoa ca lang sang khoa theo vung chuong, nen nhieu Rancher co the lam song song tai cac chuong khong chong nhau; herd UUID duoc tach theo tung chuong de khong dat con tu chuong A sang B.
- Rancher thu gom roleplay tung item entity san pham cho phep (`EGG`, `*_WOOL`): phai di toi item, nhin/vung tay roi moi dua vao tui; item co thrower bi bo qua de khong nhat do player nem. Tui day thi di giao kho va nha claim chuong.
- Pathfinding season moi khong tao A* rieng: van dung Citizens Navigator + `DoorExaminer`. Rancher bo qua chuong khong hop le/khong co diem trong chuong ma Citizens toi duoc; khi lay thuc an hoac giao san pham se thu cac delivery chest theo khoang cach, bo chest khong co o dung/route hoac bi timeout, roi backoff va cho phep thu lai sau neu tat ca tam thoi bi chan. Khong teleport recovery.

## Viec Uu Tien Tiep Theo

### Season 9 - Da bat dau foundation ngay 2026-08-13

- Da them model `CookingSession` va phase `RESERVED -> LOADED -> COOKING -> COOKED -> COMMITTED`, cho phep rollback tu moi phase active.
- `cooking-sessions.yml` schema 1 ghi nguyen tu, luu accounting input/fuel/output, loaded active ticks va snapshot tung slot de restart reconciliation sau nay.
- Active appliance lock duoc nap ngay khi plugin enable. Listener da chan open/click/drag/hopper, pha block, piston va explosion doi voi block dang co session.
- Journal hong/schema khong ho tro se khoa moi write fail-closed; khong tu sua hay ghi de file hong.
- `season-9.enabled` mac dinh `false`. Chua noi Cook runtime vao lo that, chua reserve kho va chua tao session trong gameplay vi source hien chua co Season 7 WAL/needs va Season 8 KitchenDefinition/MealRequest.
- Khong duoc bat real cooking hoac deploy nhu Season 9 hoan chinh truoc khi Season 6-8 dat tieu chi thoat trong `NEXT_SEASONS_ROADMAP.md`.
- Unit test foundation: lifecycle, persistence/reload, claim collision, terminal unlock, corrupt schema, slot snapshot, InventoryOpen va hopper lock.

1. In-game test GUI: Shift + click phai NPC, xem Chan nuoi/Ngu dan, kiem tra kinh den va tat ca nut dung slot.
2. Dat `Diem cau` tai khu co nuoc va bo dung an toan; chon Ngu dan, test duong di -> tha cau -> cho -> keo -> ca vao kho.
3. Smoke test multi-chuong/pathfinding Rancher: mot chuong hop le + mot chuong bi chan, hai delivery chest trong do chest gan bi chan; xac nhan NPC bo qua target loi, di qua door/fence gate, lay thuc an/giao san pham tai chest du phong va khong teleport.
4. Smoke test daily plan Farmer: kiem tra dang lam -> ve nha nghi trua -> tu quay lai ruong; sau do moi can nhac rancher morning round/patrol/report va fisher giao kho animation.
5. Kiem tra wheat/carrot/potato/beetroot va kho/tien tach biet giua hai lang bang GUI.

## Y Tuong Da Chot De Lam Sau: Dan Buon Va Doan Khach

Trang thai: **TAM HOAN** cho den khi he thong ghe ngoi/nghi ngoi cua season hoan thien. Chua sua code, config hoac server live cho tinh nang nay.

Huong thiet ke da chot:

- Da trien khai MVP job `Dan buon` ngay 2026-08-13: role selectable, lich lam viec, NPC di toi diem nguoi ban va chi mo quay khi dung tai diem; het ca/mua thi dong quay va ve nha.
- Moi NPC Dan buon co mot `MerchantStall` rieng trong `villages.yml` tai `merchant-stalls.<npc-uuid>`, gom `seller-point` va `buyer-point`, deu luu yaw/pitch. Mot lang co nhieu quay; GUI detail NPC dat tung diem rieng, reject diem trung va khac world.
- Visitor khong con mua tai `market-point` xa hoi. Visitor chi reserve mot quay co Dan buon dang `SERVING`, di toi buyer-point, va chi commit giao dich neu Dan buon van mo quay; dong quay giua chuyen thi khach quay ve khong mua.
- `market-point` cu van duoc giu lam diem xa hoi cho cu dan, khong auto-migrate sang quay de tranh gan nham seller/buyer.
- Da chay `gradlew.bat test --no-daemon --console=plain` thanh cong. Chua deploy hoac smoke test tren server live; can restart server sau khi thay jar, tao/gán quay trong GUI roi test duong di hai Dan buon + hai khach.

- NPC cu dan co nghe `Dan buon`, lam viec theo lich va dung chinh xac tai diem quay duoc gan cho NPC do; location phai luu ca toa do va huong nhin.
- Moi lang co danh sach nhieu `Quay dan buon`, khong phai mot `market-point` duy nhat. Moi quay gom `Diem dung dan buon` cho NPC nguoi ban va mot `Diem nguoi mua` rieng o phia truoc; moi NPC mua/ban duoc gan toi da mot quay va moi quay chi co mot NPC chiem dung tai mot thoi diem.
- GUI phai cho them, xoa, xem trang thai va gan tung quay cho tung NPC. Quay thieu mot trong hai diem, trung NPC, chong diem dung hoac khac world thi khong san sang.
- Khach vang lai la nguoi mua: sinh tai `Cong khach`, chon mot Dan buon dang mo quay con san sang, di toi dung `Diem nguoi mua` cua quay do, giao dich, tap hop va quay lai cong truoc khi bien mat.
- Moi chuyen duoc snapshot mot lan gom visit ID, loai khach, nhu cau, vi, gia, gioi han so luong va thanh phan doan; khong reroll khi relog, mo lai GUI, chunk reload hoac restart.
- Truong doan la authority duy nhat cua giao dich. NPC di cung va thu tho chi tao presentation; ca doan chi commit mot transaction tong.
- Hang that nam trong kho ao lang + ho so chuyen + transaction journal. Khong dat hang co gia tri that vao inventory cua lua/la/lac da.
- Lua/la la thu tho chinh va co the hien ruong trang tri. Lac da la bien the hiem hoac thu cuoi cua truong doan, khong gia lap kho ruong.
- Giao dich chi duoc commit khi Dan buon dang o trang thai mo quay va dung tai quay. Animation xem hang dien ra truoc, transaction nguyen tu dien ra tai thoi diem ban giao ro rang.
- Can luong du tru toi thieu theo item, vi huu han, cap tong don vi/chuyen, cap tung item va tran doanh thu theo lang/ngay. Khong cho khach ban het wheat/seeds/carrot can cho nghe khac.
- Khi co Dan buon, hang do Dan buon quan ly khong duoc dong thoi auto-sale cuoi ca.
- Gia nen thap va on dinh; bien dong nhe co ly do theo profile/nhu cau, khong dung dynamic pricing lien tuc va khong ap dung gia dong cho ca LiteFish co trong luong.
- He thong ghe/nghi sau nay phai dung chung seat reservation; khong tao co che ghe rieng cho doan buon. Thieu ghe khong duoc lam ket event.

Pattern tham khao da research:

- Skyrim: lich/tuyen co dinh, nhan vat co tinh nhan dien, kho hang an tach khoi presentation.
- Kenshi: doi hinh truc quan gom truong doan, nguoi di cung va thu tho.
- RimWorld: lifecycle tap hop -> chuan bi -> xuat phat, co timeout va recovery.
- Bannerlord: rui ro tuyen va hau qua kinh te, nhung khong can mo phong combat trong MVP.
- Stardew Valley: lich de nho, stock/chuyen duoc tao mot lan va so luong gioi han.
- Medieval Dynasty: quay lay hang tu kho settlement va doanh thu bi gioi han.
- Graveyard Keeper: thi truong chi hap thu mot luong hang huu han.
- Recettear: profile khach co ngan sach va nhom hang uu tien rieng.

State machine du kien:

```text
Dan buon:
OFF_DUTY -> GOING_TO_STALL -> PREPARING -> OPEN -> SERVING
-> SHORT_BREAK/OPEN -> CLOSING -> RETURNING_HOME -> RESTING

Doan khach:
SCHEDULED -> APPROACHING -> SPAWNING -> FORMING -> ENTERING
-> PARKING_ANIMALS -> WAITING_FOR_MERCHANT -> BROWSING -> TRADING
-> VISITING_OR_RESTING -> REGROUPING -> DEPARTING -> COMPLETED
```

Thu tu trien khai an toan:

1. Ha tang danh sach quay + gan quay theo NPC; smoke test truoc voi mot Dan buon, mot quay va mot khach, lich mo/dong, demand snapshot, giao dich nguyen tu va readiness ro rang.
2. Mo rong nhieu Dan buon/nhieu quay, chon quay san sang khong xung dot; sau do them 1-2 NPC di cung va formation long.
3. Them lua/la presentation, diem buoc thu, chan ride/inventory/leash/feed/breed/damage/drop; sau do moi them lac da hiem.
4. Tich hop nghi ngoi/qua dem va cac bien the doan sau khi season seating on dinh.

Khong nen lam: hang that trong ruong thu, moi NPC mua rieng, force-load chunk de doan di xuyen map, gia thay doi lien tuc, combat caravan trong MVP, hoac de entity presentation lam source of truth.

## Chan Doan Log Production Moi Nhat

Da doc `F:\minecraftserver\villagedefense2026\logs\latest.log` den luc server stop sach `22:15:15` ngay `2026-08-12`. Chua sua live, deploy hoac restart.

- LivingNPC `0.5.0-SNAPSHOT` enable/disable sach, khong co stack trace, `Exception` hay `Could not pass event` cua LivingNPC.
- Fisher `Alex`, UUID `3d1d6e6d-6f19-4214-b794-f3ba0c202a1d`, bi watchdog bao loi hai lan luc `21:58:45` va `22:14:37`: task `fisher` qua 2m1s van o `CASTING_LINE` nhung tay cam `AIR` thay vi `FISHING_ROD`. Runtime bao phuc hoi ngay sau do, nhung loi da lap lai nen can sua dong bo phase/equipment.
- Rancher `Jumonka`, UUID `ed2008d7-60a8-471c-8b1d-0cc9f28903c1`, spam `bat dau task rancher` moi 3-4 giay tu `21:56:44` den `22:01:42` (hon 80 dong). Watchdog co kha nang khoi tao/reset task tren moi cycle, lam deadline khong the phat hien runtime ket va gay spam log.
- Farmer khong co dong `task farmer` trong khoang log nay, nen chua du bang chung ket luan runtime khoe hay loi. Can smoke test co crop chin va xem chuoi `GOING_TO_PLOT -> FINDING_WORK -> WORKING -> delivery`.
- Server da stop sach luc `22:15:08`; LivingNPC disable luc `22:15:10`, tat ca world/chunk save xong.

Thu tu sua de nghi:

1. Fisher giu/dong bo `FISHING_ROD` trong cac phase cau va watchdog khong bao sai khi runtime dang chuyen phase.
2. Rancher tracker chi bat dau task khi vao mot thao tac thuc su, khong reset moi scan/cycle.
3. Bo sung/kiem tra theo doi Farmer va smoke test daily plan tren server test truoc production.

Khong lam trong phien ke tiep tru khi user doi uu tien:

- Combat Zombie/arena/damage/loot.
- Gemini network client.
- Cook, crafter, miner va security runtime.
- Market purchase economy that.

## Lenh Admin Can Nho

```text
/lnpc
```

Luồng thường chỉ còn: chọn làng -> `Kho làng`; chọn NPC -> `Khu ruộng`; nhấn `Làm nông: BẬT`.
Chỉ khi chưa có làng mới dùng `/lnpc lang tao <id> <tên>`. Lệnh kỹ thuật cũ vẫn hoạt động nhưng đã ẩn khỏi help/tab completion.

Chi bat Master AI + Harvest + Plant sau khi kho va plot da dat dung. Readiness dung phai hien:

```text
SAN SANG - bat dau o tick ke tiep khi dung ca
```

## Build Va Git

- Command: `.\gradlew.bat clean test build --console=plain`
- Source hien tai: full `clean test build` thanh cong ngay `2026-08-13`; co test multi-chuong, san pham Rancher, dialogue va fallback pathfinding delivery chest.
- Source build SHA-256: `2DCD47EDD29EFBC30C4973D95421A80C41D1AACD1A2C6D1703054C1F6222C827`.
- Chu y: source build moi hon JAR live vi co combat thu nghiem; **khong deploy nguyen JAR nay neu muc tieu la tam hoan combat**.
- Khi commit/push plugin dung repo `livenpcminecraftplugin`; khong dua bot tester Node.js vao repo plugin.
- Author local repo: `thanhredfield1999 <thanhredfield1999@users.noreply.github.com>`.
- Pathfinding hardening ngay `2026-08-13`: test rieng `RancherPathfindingPolicyTest`, `RanchProductPolicyTest`, `RanchWorkCoordinatorTest` thanh cong. Chua deploy/restart production; van can smoke test Citizens route qua door/fence gate va fallback chest tren server test/live co backup.
- Season 10 foundation ngay `2026-08-13`: them `SeasonTenSettings`, policy breakfast/lunch/dinner, demand snapshot batch va `ServingLedger` idempotent voi visitor quota rieng. `season-10.enabled` mac dinh `false`; khong dang ky scheduler/listener, khong debit kho va khong deploy live vi source chua co Season 6-9 (needs, kitchen, cooking journal). Test foundation va full build can duoc giu xanh truoc khi noi runtime sau nay.
- Season 11 foundation ngay `2026-08-13`: them chu ky `SPRING/SUMMER/AUTUMN/WINTER` theo world full-time, snapshot cycle/day-in-season va modifier stock target/export demand/labor priority. `season-11.enabled` mac dinh `false`; khong noi scheduler/planner, khong sua kho, demand, output hay role live truoc khi Season 10 dat gate.

## Quy Tac An Toan

- Khong tu restart production neu chua duoc user dong y.
- Backup JAR va `plugins\LivingNPC` truoc moi thay doi live.
- Khong sua YAML live khi Paper dang chay neu plugin co the ghi de file do.
- Khong ghi API key vao source/YAML/log.
- Khong de role chua hoan thien mutate world.
- Khong scan entity/block toan world; moi discovery phai bounded va rate-limited.
# Current Release Gate

- Source is now Season 2 release candidate `0.6.0-rc.2`.
- Enabled roles: `RESIDENT`, `FARMER`, `FISHER`, `RANCHER`.
- Season 3 and later runtimes remain release-gated.
- Season 2 still requires controlled Paper smoke, restart, cleanup and performance evidence before final release.
- Lan quan sat tu dong dau tien sau deploy la `INCONCLUSIVE`, khong phai failure: `NPC_HEALTH total=10 ok=0 waiting=10 errors=0`; ca 10 NPC deu `spawned=false` vi khong co player tren server. Can player vao gan lang roi chay `tools\build-deploy-smoke.ps1 -CheckOnly` de lay bang chung Fisher/Rancher.
- RC2 them Citizens route cost dung chung: uu tien DIRT_PATH, tranh nuoc va mep rong, khong chon buoc roi; Fisher chi nhan diem cau co vung dung an toan va toi tam block voi margin 0.4.
- RC2 da deploy luc `23:56` ngay `2026-08-13`; Paper `Done (35.923s)`, khong co LivingNPC ERROR/Exception. Live SHA-256 `60850B45CE118B883DF81F07109B88483703EB1375C296497F0B51DE0D402E31`; backup RC1 + 8 YAML tai `F:\minecraftserver\villagedefense2026\plugins\LivingNPC-backup-20260813-235604`, tat ca YAML khop hash sau startup.
