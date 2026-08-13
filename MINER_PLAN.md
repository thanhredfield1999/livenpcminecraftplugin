# LivingNPC - Ke Hoach Miner Khu Dao 2x2

## Muc Tieu

Thay co che Miner phu thuoc WorldGuard bang cac khu dao nho do admin dat thu cong. Moi khu dao co footprint co dinh 2x2 block. Mot lang co the dat nhieu khu o nhieu vi tri; NPC tu chon khu hop le, di bo toi va dao dung block nam trong khu do.

WorldGuard van co the bao ve server theo cach thong thuong, nhung khong con la du lieu xac dinh khu dao cua LivingNPC.

## Mo Hinh Ha Tang

- `Tram mo`: mot work zone dung de kiem tra `STONECUTTER` va `BLAST_FURNACE`, hien thi readiness va lam diem cho/nghi cua Miner.
- `Khu dao`: danh sach rieng trong lang, khong ghi de `work-zones.mining`.
- Moi Khu dao luu `id`, `corner`, `min-y` va `max-y`.
- `corner` la goc ma admin click; footprint gom bon cot block: `(x,z)`, `(x+1,z)`, `(x,z+1)`, `(x+1,z+1)`.
- Ban dau gioi han toi da 16 Khu dao moi lang.
- Chieu cao mac dinh la `Y +/- 2` quanh block admin click, tong toi da 5 tang.
- Khu dao phai cung world voi lang.
- Hai Khu dao trong cung lang khong duoc chong footprint va khoang Y.
- Khu dao cua hai lang khac nhau cung khong duoc chong nhau de tranh hai NPC cung claim mot block.

Schema de xuat trong `villages.yml`:

```yaml
mining-zones:
  mine_1:
    corner:
      world: StillCliff
      x: 120
      y: 54
      z: -32
    min-y: 52
    max-y: 56
  mine_2:
    corner:
      world: StillCliff
      x: 138
      y: 48
      z: -20
    min-y: 46
    max-y: 50
```

## Luong Setup GUI

1. Admin mo `/lnpc` -> chon lang -> `Khu nghe & khach vang lai`.
2. `Tram mo` tiep tuc duoc dat mot lan de xac nhan nghe Miner co ha tang.
3. Click `Danh sach khu dao` de mo menu 54 slot.
4. Click `Them khu dao`, sau do click phai block lam goc 2x2.
5. GUI hien `mine_1`, `mine_2`... voi world, toa do, khoang Y va so block hop le hien tai.
6. Click trai teleport admin toi khu de kiem tra.
7. Shift + click phai de xoa khu sau man xac nhan.
8. Khi them khu, plugin hien particle vien 2x2 trong thoi gian ngan cho rieng admin; khong chay particle lien tuc.

Readiness cua Miner can bao ro:

- Chua dat Tram mo.
- Chua co Khu dao.
- Tat ca Khu dao khac world.
- Tat ca chunk cua Khu dao dang unload.
- Khong con block hop le.
- Khong co o dung an toan/duong di.
- San sang va dang chon `mine_n`.

## Luong Hanh Vi Miner

State machine de xuat:

```text
OFF_DUTY
-> WAITING_AT_STATION
-> SELECTING_ZONE
-> SELECTING_BLOCK
-> GOING_TO_BLOCK
-> INSPECTING_BLOCK
-> MINING
-> COLLECTING
-> SELECTING_BLOCK
-> RETURNING_TO_STATION
```

Thu tu action:

1. Kiem tra lich, Master AI, nguoi choi gan NPC hoac khu mo va Tram mo hop le.
2. Lay snapshot cac Khu dao co chunk dang load.
3. Bo qua khu het block, dang bi Miner khac claim hoac dang trong backoff pathfinding.
4. Trong moi khu, chi doc toi da `2 x 2 x 5 = 20` block.
5. Tim o dung an toan ke block va dung Citizens `canNavigateTo` de xac nhan route.
6. Chon candidate co chi phi uu tien: khu dang lam -> khoang cach gan -> block gan mat san.
7. NPC di toi o dung, nhin block, dung lai inspect ngan, lay cuoc va vung tay theo nhịp.
8. Khi hoan tat, block bi depletion theo policy duoc chot va output vao kho ao.
9. NPC xu ly mot batch nho trong cung khu truoc khi chuyen khu, tranh doi route lien tuc.
10. Khi khu het block, NPC release claim va chon khu ke tiep; neu tat ca het thi ve Tram mo.

## Block Duoc Phep

Allowlist ban dau:

- `STONE` -> cobblestone/stone output theo balance.
- `DEEPSLATE` -> cobbled_deepslate.
- `COAL_ORE`, `DEEPSLATE_COAL_ORE` -> coal.
- `IRON_ORE`, `DEEPSLATE_IRON_ORE` -> raw_iron.

Khong dao:

- Bed, chest, barrel, station va block co inventory.
- Stair, fence, door, sign, light va block trang tri.
- Block nam ngoai footprint 2x2 hoac ngoai `min-y/max-y`.
- Block o chunk unload.
- Block khong co o dung an toan.
- Block da duoc NPC khac reserve.

## Depletion Va Restoration

Khong duoc cong kho ao nhieu lan tu cung mot block con nguyen.

Phuong an MVP de xuat:

- Khi dao thanh cong, doi ore/stone thanh block nen tam thoi nhu `COBBLESTONE` hoac `DEEPSLATE` tuy tang.
- Ghi journal gom world, toa do, material goc, material tam va thoi diem restore.
- Restore theo batch nho sau cooldown cau hinh, chi khi chunk dang load.
- Neu block da bi player thay doi sau khi NPC dao, khong ghi de thay doi cua player.
- Khi plugin shutdown/restart, journal tiep tuc duoc doc; khong mat block dang cho restore.
- Chi cong output sau khi mutation va journal cung thanh cong; neu save fail thi fail-closed.

Khong nen dung co che chi animation roi cong kho trong khi block van nguyen, vi se tao tai nguyen vo han tu cung mot block.

## Claim Va Nhieu Miner

- Claim theo `mining-zone-id`, khong khoa toan lang.
- Mot khu chi co mot Miner thao tac tai mot thoi diem.
- Nhieu Miner co the dao song song o cac khu khac nhau.
- Claim duoc release khi het batch, path fail, danger, het ca, role switch, despawn hoac shutdown.
- Claim chi ton tai RAM; block reservation va restoration journal moi can persist.
- Mot block candidate phai duoc reserve truoc khi NPC bat dau navigation.

## Toi Uu Chong Lag

- Khong scan radius 6 nhu runtime cu.
- Moi lan scan toi da 20 block moi khu; khong scan khu co chunk unload.
- Khong kiem tra tat ca khu moi 10 ticks; `SELECTING_ZONE` chay toi da moi 200 ticks khi khong co viec.
- Cache danh sach block hop le cua khu trong 200 ticks va invalidate khi Miner mutate block.
- Chi goi `canNavigateTo` cho toi da 4 candidate gan nhat moi vong chon.
- Khu path fail co backoff toi thieu 100 ticks, sau do thu khu khac.
- Khong force-load chunk, khong A* rieng, khong stuck teleport.
- Khong particle/hologram lien tuc.
- Moi Miner xu ly mot block tai mot thoi diem va batch toi da 4 block truoc khi chon lai khu.

Ngan sach de xuat:

| Hang muc | Gioi han |
|---|---:|
| Khu dao moi lang | 16 |
| Footprint moi khu | 2x2 cot |
| Chieu cao mac dinh | 5 block |
| Block doc toi da/khu/scan | 20 |
| Candidate path check/vong | 4 |
| Idle rescan | >= 200 ticks |
| Path failure backoff | >= 100 ticks |
| Batch moi lan claim | <= 4 block |
| Navigation timeout | 400 ticks |

## Migration

- Khong tu dong chuyen `work-zones.mining` cu thanh Khu dao, vi diem cu la tam vung radius 6 va khong xac dinh duoc goc 2x2 an toan.
- Giu `work-zones.mining` lam Tram mo neu station validation thanh cong.
- Admin phai dat moi it nhat mot Khu dao 2x2.
- Bo yeu cau region `lnpc-mine-*` khoi GUI, readiness va Miner runtime.
- Khong xoa WorldGuard integration chung; Farmer va cac mutation khac van co the dung protection policy neu can.

## Test Bat Buoc

- Luu/load/xoa nhieu Khu dao va migration file lang cu.
- Reject khu khac world, trung/chong khu va vuot cap 16.
- Chi block trong dung footprint 2x2 va khoang Y duoc chon.
- Khu gan bi chan thi NPC chon khu xa co route.
- Hai Miner khong claim cung khu; co the lam song song o hai khu.
- Path timeout release claim va backoff, khong lap route moi tick.
- Block mutation, economy output va journal la atomic/fail-closed.
- Restart giua luc block cho restore khong duplicate output va khong mat restoration.
- Player thay doi block tam thi restore khong ghi de.
- Soak test nhieu khu voi `spark`: LivingNPC p95 <= 4 ms/tick va khong force-load chunk.

## Thu Tu Trien Khai

1. Them model `MiningZone`, persistence va test overlap/footprint.
2. Them GUI danh sach, placement 2x2 va xoa khu.
3. Doi readiness, bo yeu cau `lnpc-mine-*` va giu Tram mo rieng.
4. Them coordinator claim theo khu va block reservation.
5. Doi Miner scanner sang 2x2x5, bounded candidate/path checks.
6. Hoan thien depletion/restoration journal atomic.
7. Unit test, integration test, smoke test Citizens va soak test `spark` truoc production.
