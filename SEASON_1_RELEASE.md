# LivingNPC Season 1

## Pham Vi Phat Hanh

- `Nguoi dan`: ve nha, ngu, patrol duong `DIRT_PATH`, nhin quanh, quan sat nguoi choi, ngoi ghe va giao tiep tai diem cho/diem ngam canh.
- `Nong dan`: inspect, harvest va replant wheat/carrot/potato/beetroot, xu ly theo batch, giao kho fallback va nghi trua.
- Ho so nhan vat va quan he van tuy chon, khong tham gia readiness.
- GUI Season 1 chi mo hai nghe va ha tang sinh hoat. Du lieu nghe season sau khong bi xoa nhung runtime bi khoa fail-closed.
- Combat, visitor, merchant, fisher, rancher, cook, crafter, miner, security va Gemini network client khong duoc bootstrap trong Season 1.

## Gioi Han An Toan

- Global tick khong nhanh hon 10 ticks.
- Farmer scan khong nhanh hon 100 ticks/NPC.
- Ban kinh ruong bi clamp toi da 8 block.
- Navigation timeout mac dinh 400 ticks, stuck teleport tat.
- Khong force-load chunk va khong scan toan world.
- Kho ao la source of truth; chest chi la diem animation giao hang.

## Smoke Test Bat Buoc

1. Tao hai lang, moi lang co NPC, nha, ruong va hai diem giao kho rieng.
2. Dat rương gan bi chan va rương xa co duong di; xac nhan Farmer bo rương loi va giao tai rương fallback.
3. Test rieng wheat, carrot, potato va beetroot: crop chin duoc harvest, replant age 0 va vao dung kho lang.
4. Xac nhan crop khong co o dung an toan bi bo qua, crop ke tiep van duoc xu ly.
5. Xac nhan Farmer dang lam ve nha/ban an nghi trua va tu quay lai ruong, khong reset quota hay ban kho giua ca.
6. Dat diem cho, diem ngam canh va hai ghe; xac nhan chi mot NPC reserve mot ghe va reservation duoc release khi roi ghe/nguy hiem/tat plugin.
7. Tao Zombie gan NPC co `Tranh quai vat`; xac nhan NPC rut ve nhung LivingNPC khong gan target cho Zombie.
8. Restart Paper sach; doi chieu `villages.yml`, `farmers.yml` va `economy.yml` de bao dam lang, NPC, kho, quota, lich va ha tang khong mat.
9. Chay lien tuc ba ngay Minecraft; khong phase nao ket qua 2 phut va khong log lap moi tick.
10. Do bang spark/timings: TPS trung vi >= 19.8 va LivingNPC trung binh <= 1 ms/tick trong quy mo Season 1.

## Build

```powershell
.\gradlew.bat clean test build --no-daemon --max-workers=1 --no-parallel --console=plain
```

Khong deploy bang `/reload` hoac PlugMan. Stop Paper sach, backup JAR va `plugins/LivingNPC`, thay JAR, sau do start lai.
