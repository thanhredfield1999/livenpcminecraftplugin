# LivingNPC - Handoff Session Moi

## Cap Nhat Cuoi Phien 2026-08-12

- Da sua NPC `LOOKING_AROUND` nhin ngang tam mat thay vi co the nguoc len troi.
- Da sua race luc khoi dong: LivingNPC khong con xoa `farmers.yml` khi Citizens chua nap xong NPC registry.
- Da khoi phuc ThanhRedfield va Keyden_Redfield vao `farmers.yml`; du lieu song qua restart.
- Da them source combat Zombie bounded cho cap NPC: cung + kiem, cuboid rieng, admin bat thu cong, rut lui duoi 40% mau, 1 Xu/kill, toi da 32 kill/luot.
- Combat source da qua `30/30` test va review blocker; chua deploy JAR combat len live, chua tao `combat-arenas.yml`, chua bat arena production.
- Server live dang chay ban on dinh hash `DDA614054E050660DE7573B9CC2E4D14E26772A8BBFD1A445554EC5E455AEF36` tai thoi diem handoff nay.
- Backup live moi: `F:\minecraftserver\villagedefense2026\plugins\LivingNPC-backup-20260812-035351`.
- GitHub public: `https://github.com/thanhredfield1999/botcheckerminecraft`; repo cha ban dau trong, commit dau chi nen gom `.gitignore` va `living-npc-plugin`.

Lenh combat sau khi deploy ban source moi:

```text
/livingnpc combat tao redfield_zombie stillcliff_1 2 3
/livingnpc combat goc1 redfield_zombie
/livingnpc combat goc2 redfield_zombie
/livingnpc combat rutlui redfield_zombie
/livingnpc combat status redfield_zombie
/livingnpc combat bat redfield_zombie
/livingnpc combat tat redfield_zombie
```

Thu tu: dung tai diem rut lui de `tao`; dung tai hai goc doi dien cua vung ai de `goc1`, `goc2`; kiem tra `status`; chi `bat` khi da dat Zombie thu nghiem trong cuboid.

## Project

- Source: `E:\AI.WORK\botcheckerminecraft\living-npc-plugin`
- Server live: `F:\minecraftserver\villagedefense2026`
- JAR live: `F:\minecraftserver\villagedefense2026\plugins\living-npc-0.5.0-SNAPSHOT.jar`
- LivingNPC: `0.5.0-SNAPSHOT`
- Paper: `1.21.11`
- Java: `21`
- Citizens: `2.0.42-SNAPSHOT` build `4173`
- WorldGuard: `7.0.16`
- Git repo cha chua co commit; project van untracked.

## Ban Build Moi Nhat

- Command: `.\gradlew.bat clean test build --console=plain`
- Result: `BUILD SUCCESSFUL`
- Tests: `29/29`
- JAR SHA-256: `D16848EAFA65CB55203E009A6A66DAC6E994896395AD37F0C712ECC93201BD5F`
- Backup live moi nhat: `F:\minecraftserver\villagedefense2026\plugins\LivingNPC-backup-20260812-032747`
- Backup truoc: `F:\minecraftserver\villagedefense2026\plugins\LivingNPC-backup-20260812-030355`
- Server can restart Paper sach de nap JAR moi. Khong dung `/reload` hoac PlugMan.

## Nguyen Nhan NPC Cu Dung Im

`F:\minecraftserver\villagedefense2026\plugins\LivingNPC\farmers.yml` dang la `farmers: {}`. Hai NPC Citizens cu chua duoc LivingNPC quan ly. Sau restart phai tao lang va dung `/livingnpc tiepnhan <npc-id> <lang-id>`.

## Mo Hinh Da Chot

- Mot world co the co nhieu lang doc lap.
- Moi lang co ID, ten, tam lang, NPC, kho ao 512 item, tien, ruong cua tung NPC, ruong giao hang, diem cho va diem ngam canh.
- NPC lang A khong dung kho/tien cua lang B, ke ca cung world.
- Ruong that chi la diem NPC di toi va dien hoat mo/dong ruong.
- Kho ao cua lang la nguon du lieu chuan, tranh mat item neu nguoi choi pha/lay do trong ruong.
- Gemini chua bat, khong goi Google, khong ton chi phi. Social dialogue dang dung fallback tieng Viet co dieu kien.

## Farmer Runtime Da Lam

Chu trinh:

1. Quet bounded plot.
2. Nhan dien wheat, carrot, potato, beetroot chin.
3. Di toi vi tri dung an toan canh cay.
4. Kiem tra, cam cuoc, pha cay chin.
5. Doi sang hat/cu giong va trong lai dung loai tai cung block.
6. Ghi san luong vao kho ao cua dung lang.
7. Di toi ruong kho, quay mat, swing, phat am thanh mo/dong ruong.
8. Quay lai ruong va tiep tuc lam.

Farmland trong se trong loai cay chiem da so trong ban kinh gan; neu khong co dau hieu thi mac dinh wheat.

Farmer chi `SAN SANG` khi co:

- `village-id` hop le.
- Plot da gan.
- Ruong kho lang con ton tai va la chest/trapped chest/barrel.
- Master AI bat.
- Harvest bat.
- Plant bat.

Runtime con yeu cau dung ca, khong mua, khong co monster gan va co player trong 48 block.

## Social Runtime Da Lam

- Ngoai ca, hai NPC an toan/rảnh/cung lang co the di cho hoac diem ngam canh.
- Chi xet NPC LivingNPC trong cung lang, khong scan toan world.
- Huy social neu mua hoac co monster gan.
- Khong social khi dang lam viec/giao hang.
- Cau thoai fallback tieng Viet dua tren diem cho/ngam canh va thoi gian trong ngay.
- Player trong 20 block moi thay chat.
- Gemini API chua implement client mang. Quyet dinh user: thu fallback/gameplay truoc, chi bat Gemini sau khi on dinh va co ngan sach ro rang.

## Multi-role

- Roles: farmer, fisher, cook, crafter, miner, rancher, security, melee-training, archery-training, sparring.
- Mot `activeRole` tai mot thoi diem.
- Scheduler tu doi role theo lich, giu role hien tai neu lich overlap.
- XP/level rieng tung role, cap 1-100, bonus toi da 20%.
- Farmer la runtime world-action hoan chinh duy nhat.
- Role khac fail-closed, khong mutate world.
- Builder de rieng, cho trait/API plugin cua user.

## GUI Va Viet Hoa

- GUI/command/status/thong bao chinh da Viet hoa.
- `/livingnpc list` mo danh sach lang truoc.
- Chon lang moi thay NPC va kho cua lang.
- GUI role schedule hien level, XP, active role va gio `HH:mm`.
- Dieu khien lich: trai +1 gio, phai -1 gio, Shift thay doi 2 gio.
- Kho lang hien item, so luong, suc chua va so du.
- Item NPC hien readiness va ly do thieu cau hinh.

## Lenh Admin Theo Dung Thu Tu

Vi du tao lang StillCliff:

```text
/livingnpc lang tao stillcliff_1 Làng StillCliff
```

Dat ruong kho: dung cach chest/trapped chest/barrel toi da 6 block, nhin thang vao no:

```text
/livingnpc setkho stillcliff_1
```

Dat diem cho va ngam canh tai vi tri Admin dang dung:

```text
/livingnpc setdiem stillcliff_1 cho
/livingnpc setdiem stillcliff_1 ngamcanh
```

Xem ID Citizens va tiep nhan NPC:

```text
/npc list
/livingnpc tiepnhan <npc-id> stillcliff_1
```

Gan ruong (Admin dung gan tam ruong):

```text
/livingnpc setplot <npc-id> 6
```

Mo GUI va bat Master AI + Harvest + Plant + Schedule:

```text
/livingnpc list
```

Kiem tra:

```text
/livingnpc status <npc-id>
```

Can thay: `SAN SANG - bat dau o tick ke tiep khi dung ca`.

## File Source Chinh Da Them/Sua

- `VillageDefinition.java`
- `VillageStore.java`
- `FarmerDefinition.java`: them `villageId`.
- `FarmerStore.java`: persist `village-id`.
- `NpcEconomy.java`: tai khoan kho/tien theo village ID.
- `FarmerRuntime.java`: replant dung loai, delivery chest, social fallback.
- `FarmerManager.java`: readiness, village filtering, social coordinator.
- `CropScanner.java`: infer dominant crop.
- `ResidentGui.java`, `ResidentMenu.java`: GUI lang/kho/NPC/role.
- `LivingNpcCommand.java`: tao lang, tiep nhan, setkho, setdiem, status.
- `VillageDefinitionTest.java`, `CropScannerTest.java`, `NpcEconomyTest.java`.

## Can Lam Tiep

Uu tien tiep theo:

1. Restart server va smoke test tren StillCliff theo dung lenh tren.
2. Doc `logs/latest.log` sau restart, xac nhan LivingNPC/Citizens/WorldGuard load khong loi.
3. Tao `villages.yml`, tiep nhan 2 NPC cu, dat kho/cho/ngam canh/plot.
4. Kiem tra pathfinding thuc te: ruong -> chest -> ruong.
5. Kiem tra WorldGuard cho phep BREAK + PLACE tai plot.
6. Kiem tra GUI hierarchy va kho/tien hai lang tach biet.
7. Sau smoke test moi tinh den Gemini client that; phai hoi lai ngan sach va chi dung `GEMINI_API_KEY` environment variable.

Module chua lam:

- GUI tao lang truc tiep (hien tao bang command).
- FisherRuntime.
- CookRuntime/CrafterRuntime va recipe reservation.
- MinerRuntime + cuboid/allowlist/quota/WorldGuard.
- RancherRuntime, khong breed.
- Security distress + toi da 2 responders.
- Melee/archery/sparring khong damage.
- Market purchase economy thuc (social `SHOPPING` hien chi la movement/ambient).
- Gemini network client, memory persistence va context prompt that.
- Builder trait/API cua user.

## Luu Y An Toan

- Khong tu restart production neu chua duoc user dong y.
- Backup YAML/JAR live truoc moi thay doi.
- Khong ghi API key vao YAML/source/log.
- Khong cho Gemini chon toa do, gia, so luong, command hay Bukkit action.
- Role chua co runtime phai tiep tuc fail-closed.
