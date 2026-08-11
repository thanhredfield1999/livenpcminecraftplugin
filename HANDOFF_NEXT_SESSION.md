# LivingNPC - Handoff Phien Moi

## Muc Tieu Phien Moi

Chi tap trung vao **LivingNPC doi thuong, nhan vat, quan he, nghe nghiep, GUI va farmer runtime**.

- Tam hoan combat Zombie, arena, damage, loot va nang cap chien dau.
- Khong deploy hoac bat combat trong production.
- Source co cac file combat thu nghiem, nhung phien moi khong tiep tuc phan nay neu user chua yeu cau lai.

## Project Va Version

- Source: `E:\AI.WORK\botcheckerminecraft\living-npc-plugin`
- GitHub chinh cua plugin: `https://github.com/thanhredfield1999/livenpcminecraftplugin`
- Remote local: `livingnpc`
- Server live: `F:\minecraftserver\villagedefense2026`
- JAR live: `F:\minecraftserver\villagedefense2026\plugins\living-npc-0.5.0-SNAPSHOT.jar`
- LivingNPC: `0.5.0-SNAPSHOT`
- Paper: `1.21.11-131`
- Java build target: `21`; server dang chay Java `25.0.1`
- Citizens: `2.0.42-SNAPSHOT` build `4173`
- WorldGuard: `7.0.16`

## Trang Thai Live Hien Tai

- Server dang chay, port `11619`.
- JAR live SHA-256: `DDA614054E050660DE7573B9CC2E4D14E26772A8BBFD1A445554EC5E455AEF36`.
- JAR live da co hai fix quan trong:
  - NPC `LOOKING_AROUND` nhin ngang tam mat, khong con de bi nguoc len troi.
  - Khong xoa `farmers.yml` khi LivingNPC enable truoc luc Citizens nap xong registry.
- Backup gan nhat: `F:\minecraftserver\villagedefense2026\plugins\LivingNPC-backup-20260812-035351`.
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

## Huong Nhan Vat Da Chot

Moi NPC can co cau chuyen rieng, quan he va muc tieu nhat quan, khong chi la farmer doi ten.

ThanhRedfield va Keyden_Redfield:

- La hai anh em den tu lang Redfield.
- Thanh la anh; binh tinh va co trach nhiem bao ve em.
- Keyden la em; gan gui va thuong di cung Thanh.
- Thanh thien ve cung; Keyden thien ve kiem.
- Hai nguoi thuong dong hanh, kiem tien, mua sam/nang cap trang bi va tim noi can giup do.
- Combat that de sau. Truoc mat can uu tien lore, hoi thoai, quan he anh-em va hanh vi di cung tu nhien.

Model `ResidentProfile` hien chi co:

- `id`, `name`, `gender`, `title`, `roles`, `skin`.

Chua co field cho biography, relationship, personality, preferred weapon, goals hay memories. Phien moi nen mo rong model/persistence nho gon, co migration an toan cho `farmers.yml` cu.

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

## Viec Uu Tien Tiep Theo

1. Mo rong profile de luu lore/personality/relationships/goals theo UUID, co test persistence va migration.
2. Gan lore chinh thuc cho Thanh va Keyden; hien trong GUI/status va dung trong fallback dialogue.
3. Them coordinator cap doi de hai anh em uu tien di cung, doi nhau va khong tranh navigator.
4. Hoan thanh cau hinh farmer live neu user muon test: gan kho, plot, diem cho/ngam canh, sau do moi bat Harvest/Plant.
5. Smoke test pathfinding: nha/ruong -> crop -> kho -> ruong; kiem tra WorldGuard BREAK/PLACE.
6. Kiem tra kho/tien tach biet giua hai lang bang GUI.

Khong lam trong phien ke tiep tru khi user doi uu tien:

- Combat Zombie/arena/damage/loot.
- Gemini network client.
- Fisher, cook, crafter, miner, rancher va security runtime.
- Market purchase economy that.

## Lenh Admin Can Nho

```text
/npc list
/livingnpc list
/livingnpc status 2
/livingnpc status 3
/livingnpc setkho stillcliff_1
/livingnpc setdiem stillcliff_1 cho
/livingnpc setdiem stillcliff_1 ngamcanh
/livingnpc setplot 2 6
/livingnpc setplot 3 6
```

Chi bat Master AI + Harvest + Plant sau khi kho va plot da dat dung. Readiness dung phai hien:

```text
SAN SANG - bat dau o tick ke tiep khi dung ca
```

## Build Va Git

- Command: `.\gradlew.bat clean test build --console=plain`
- Source hien tai: `30/30` test, `BUILD SUCCESSFUL`.
- Source build SHA-256: `12290C21FF7F7D9F111B70188BA68D29996E3E25F043D79E99A52EA3B0DA6048`.
- Chu y: source build moi hon JAR live vi co combat thu nghiem; **khong deploy nguyen JAR nay neu muc tieu la tam hoan combat**.
- Khi commit/push plugin dung repo `livenpcminecraftplugin`; khong dua bot tester Node.js vao repo plugin.
- Author local repo: `thanhredfield1999 <thanhredfield1999@users.noreply.github.com>`.

## Quy Tac An Toan

- Khong tu restart production neu chua duoc user dong y.
- Backup JAR va `plugins\LivingNPC` truoc moi thay doi live.
- Khong sua YAML live khi Paper dang chay neu plugin co the ghi de file do.
- Khong ghi API key vao source/YAML/log.
- Khong de role chua hoan thien mutate world.
- Khong scan entity/block toan world; moi discovery phai bounded va rate-limited.
