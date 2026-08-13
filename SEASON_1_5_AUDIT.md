# LivingNPC - Cong Kiem Ke Season 1-5

Ngay kiem ke: `2026-08-13`.

> Historical gate result. Source has since advanced to `ReleasePolicy.SEASON = 2` as
> release candidate `0.6.0-rc.1`. This document still records why the earlier
> Season 1 baseline failed; it is not evidence that the current Season 2 RC has
> passed live smoke, restart, cleanup or performance gates.

Xac minh source tai thoi diem kiem ke: full `clean test build` thanh cong, `48` test suite / `150` test, khong failure, error hoac skipped. JAR build co SHA-256 `5932563A62FD504E5D54AB8CD9FAD948D6EE1199AAD541094D932553D9A5FBA6`. Ket qua nay chi xac nhan source build va unit/integration test; khong thay the smoke/soak Paper live.

## Ket Luan Cong

**KHONG DAT de mo Season 6.** Baseline phat hanh duoc cong nhan van la **Season 1**, nhung moi chi dat muc `enabled/live co dieu kien`; chua co du bang chung de danh dau Season 1 `smoke-tested` day du theo checklist va chua co soak/performance report dat tieu chi thoat.

Season 2-5 chua duoc cong nhan la release. Source hien tai khoa fail-closed cac runtime nay, trong khi tien trinh live dang chay mot binary cu van tick cac nghe Season 2-4. Trang thai source, GUI/config live va runtime live vi vay dang khong dong nhat.

Khong nang `ReleasePolicy.SEASON`, khong mo them role va khong bat dau Season 6 cho den khi tat ca blocker muc `P0` va `P1` ben duoi duoc dong.

## Dinh Nghia Trang Thai

| Trang thai | Y nghia |
|---|---|
| `source only` | Co code/test hoac schema, nhung release gate khong cho runtime chay. |
| `experimental` | Co the bat trong ban test, chua dat day du smoke/soak/performance gate. |
| `enabled` | Release policy, GUI, scheduler va listener cung cho phep chay. |
| `smoke-tested` | Da hoan thanh checklist tren Paper/Citizens that va luu bang chung. |
| `live` | Binary hien tai tren server production dang chay feature. |

`Co mot vai lan chay thanh cong`, XP da tang hoac co unit test khong tu dong dong nghia voi `smoke-tested`.

## Bang Feature Matrix

| Season / pham vi | Source hien tai | Release source | Smoke/soak da chung minh | Live hien tai | Ket luan |
|---|---|---|---|---|---|
| Season 1: Nguoi dan, Nong dan, ghe, cho/ngam canh | Co | `enabled`: `RESIDENT`, `FARMER` | Chi co bang chung khoi dong/restart va mot so smoke cu; chua co bien ban du 10 muc, 3 ngay Minecraft va spark budget | Co | `enabled`, `live`, chua dat gate thoat |
| Season 2: Ngu dan, Chan nuoi | Co runtime va unit test | `source only`: bi `ReleasePolicy` khoa | Co dau vet live/XP va test rieng; chua co soak 30 phut, helper cleanup, hai Rancher va p95 | Binary cu dang tick; co NPC active | `experimental`, khong phai release |
| Season 3: Dan buon, Khach vang lai | Co MVP, reserve quay, demand snapshot va giao dich idempotent | `source only`: bi `ReleasePolicy` khoa; config source mac dinh tat | Unit test economy/policy co; chua co smoke hai quay/hai khach, restart va entity baseline | Binary cu dang tick; `visitors.enabled: true`, co Merchant va ha tang quay/cong | `experimental`, khong phai release |
| Season 4: Cook, Crafter, Miner, Security | Co prototype/runtime; source moi co recipe registry va mo 2x2 co restoration | `source only`: bi `ReleasePolicy` khoa | Co unit test thanh phan; chua co recipe integration day du, Miner end-to-end va performance gate | Binary cu dang tick mot implementation cu; co Miner/Cook active | `source only/experimental`, khong phai release |
| Season 5: market day va caravan presentation | Co policy lich, follower va pack-animal presentation | `source only`: bi gate Season 1; `season-5.enabled: false` | Chi co unit test `MarketDayPolicy`; chua co smoke/soak live | Config live cu khong co `season-5`; khong co bang chung live | `source only`, chua phat hanh |

## Bang Doi Chieu Gate

### Source hien tai

- Version build: `0.5.0-SNAPSHOT`.
- `ReleasePolicy.SEASON = 1`.
- Role duoc mo: `RESIDENT`, `FARMER`.
- GUI loc profile/role qua `ReleasePolicy`.
- `LivingNpcPlugin` chi dang ky listener Rancher va tick Fisher/Rancher/Civil/Merchant/Visitor khi `SEASON > 1`.
- Config source dat `visitors.enabled: false` va `season-5.enabled: false`.
- Ket qua: source moi nhat tu nhan la Season 1 va khoa runtime Season 2-5 dung nhu chu y release.

### Server live dang chay

- Paper log luc `03:14:37` ngay `2026-08-13` nap `LivingNPC 0.5.0-SNAPSHOT` va server `Done` luc `03:14:43`.
- Log enable ghi chuoi cu `LivingNPC enabled with ...`, khong ghi `LivingNPC Season 1 ...`; day khong phai binary build tu source gate hien tai.
- Decompile binary remap dang chay xac nhan global scheduler tick Rancher, Fisher, Civil professions, Merchant va Visitor khong co release guard.
- Config live dat `visitors.enabled: true`.
- Du lieu live co active role `fisher`, `rancher`, `miner`, `merchant`, `cook`; Fisher, Rancher va Miner da co XP.
- Day la binary experimental cu dang van hanh nhieu season, khong phai bang chung cac season da dat release gate.

## Blocker

### P0 - An Toan Van Hanh

- File `plugins/living-npc-0.5.0-SNAPSHOT.jar` goc khong con trong thu muc `plugins`; chi con ban cache remap trong `plugins/.paper-remapped`. **Khong restart live** truoc khi co backup va mot JAR release da duoc chot, neu khong plugin co the khong duoc nap lai.
- Binary live khong trung source hien tai. Khong the dung version `0.5.0-SNAPSHOT` de phan biet hai artifact.
- Live dang mo visitor va tick nghe Season 2-4 trong khi tai lieu/release source noi chi Season 1. Can chot rollback ve Season 1 hoac phat hanh tung season qua gate; khong tiep tuc trang thai lai.

### P1 - Release Gate

- Hoan thanh va luu ket qua day du checklist `SEASON_1_RELEASE.md`, gom hai lang, bon crop, lunch, fallback chest, restart, 3 ngay Minecraft va spark/timings.
- Season 2 can soak Fisher/Rancher 30 phut, cleanup hook/leash/helper, multi-pen/path fallback va p95.
- Season 3 can smoke hai Merchant/hai quay/hai Visitor, timeout/restart/entity baseline va giao dich khong duplicate.
- Season 4 can chot request/reservation dau vao, recipe integration, Miner multi-zone/restoration end-to-end va performance budget.
- Season 5 can chi duoc xet sau khi Season 1-4 dat gate; market day/caravan hien chua du tieu chi live.

### P2 - Quan Ly Phat Hanh

- Doi version artifact khi noi dung release thay doi; khong tai su dung `0.5.0-SNAPSHOT` cho binary co behavior khac nhau.
- Moi bien ban smoke/soak can ghi ngay, binary SHA-256, Paper/Citizens version, config feature flag va ket qua restart.
- Sau khi chot binary, cap nhat dong thoi `README.md`, `SEASON_ROADMAP.md`, `HANDOFF_NEXT_SESSION.md` va hash live.

## Thu Tu Dong Cong

1. Stop Paper sach trong cua so bao tri da duoc phep; backup JAR/cache remap va toan bo `plugins/LivingNPC`.
2. Chot mot artifact Season 1 tu source hien tai, dat version release moi va xac nhan build/test.
3. Deploy artifact do, xac nhan log hien `LivingNPC Season 1`, role bi khoa khong con tick va du lieu role season sau van duoc bao toan.
4. Chay day du checklist Season 1 va luu spark/timings; chi khi dat moi danh dau Season 1 `smoke-tested`.
5. Mo Season 2, 3, 4, 5 theo tung release rieng. Moi lan chi nang gate sau khi season do dat smoke/soak va restart.
6. Lap lai kiem ke. Chi mo Season 6 khi matrix khong con chenhlech giua source, GUI, scheduler, config va live.

## Lenh Xac Minh Source

```powershell
.\gradlew.bat clean test build --no-daemon --max-workers=1 --no-parallel --console=plain
Get-FileHash -Algorithm SHA256 .\build\libs\living-npc-*.jar
```

Khong dung `/reload`, PlugMan hoac hot-loader cho cong phat hanh nay.
