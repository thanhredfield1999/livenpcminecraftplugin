# LivingNPC Core API/SPI — kiến trúc đề xuất

> Trạng thái: đề xuất kiến trúc, chưa phải public contract đã phát hành.
> Bối cảnh: Paper 1.21.11, Java 21, Citizens 2.0.42; baseline hiện tại `0.6.0-rc.2`.

## 1. Mục tiêu và nguyên tắc bất biến

LivingNPC cần cho phép addon độc lập mở rộng NPC, nghề, hành vi và tích hợp dữ liệu mà không phụ thuộc vào `*Manager`, runtime, store hay class package-private hiện tại. Contract phải:

1. **Fail-closed**: thiếu core, sai API major, capability không được cấp, lifecycle không hợp lệ hoặc dữ liệu không tin cậy thì không mutate NPC/world và trả kết quả có lý do rõ ràng.
2. **Core sở hữu runtime**: core là nơi duy nhất sở hữu Citizens NPC, task Bukkit, navigation lease, tick loop, shutdown và world mutation policy.
3. **Addon chỉ dùng contract**: addon chỉ compile với artifact API/SPI; không import `vn.heomc.livingnpc` implementation classes, không cast về `LivingNpcPlugin`, không gọi reflection/internal manager.
4. **Main-thread rõ ràng**: mọi API đụng Bukkit/Citizens/world đều chỉ gọi trên server thread; API async chỉ dành cho dữ liệu thuần và phải marshal kết quả về main thread.
5. **Dữ liệu immutable**: request, snapshot, event payload và capability đều là immutable record/interface; không trả mutable Bukkit object lâu dài nếu có thể trả ID/value object.
6. **Versioned contract**: dùng semantic version cho API và schema version riêng cho persistence; không dùng package implementation làm ABI.
7. **Một owner, một writer**: mỗi aggregate/persistence namespace có đúng một owner; addon được cấp namespace riêng, không sửa file core.

## 2. Tách module và dependency graph

Đề xuất Gradle multi-project nhưng có thể triển khai trước bằng source set/package:

```text
livingnpc-api       (Java thuần; không Paper/Citizens dependency)
       ▲
livingnpc-spi       (Java thuần; extension points, registration, Result)
       ▲
livingnpc-core     (Paper + Citizens; implementation và bridge)
       ▲
addon-*             (compileOnly api/spi; runtime depend LivingNPC)
```

- `livingnpc-api`: các type mà addon consumer được phép dùng.
- `livingnpc-spi`: các type addon provider implement; không expose internal managers.
- `livingnpc-core`: plugin hiện tại, adapter từ manager/store/runtime sang facade.
- `livingnpc-testkit` (tuỳ chọn, testFixtures): fake API/event clock, không được kéo vào runtime.
- Artifact coordinates gợi ý: `vn.heomc:livingnpc-api`, `vn.heomc:livingnpc-spi`, `vn.heomc:livingnpc-core`.
- API/SPI không tham chiếu `JavaPlugin`, `org.bukkit.*`, `net.citizensnpcs.*`; dùng `NpcId`, `WorldKey`, `BlockPosition`, `LocationSnapshot`, `Component`/text abstraction hoặc primitive immutable.

Package policy:

```text
vn.heomc.livingnpc.api
vn.heomc.livingnpc.api.npc
vn.heomc.livingnpc.api.role
vn.heomc.livingnpc.api.village
vn.heomc.livingnpc.api.navigation
vn.heomc.livingnpc.api.event
vn.heomc.livingnpc.api.capability
vn.heomc.livingnpc.spi
vn.heomc.livingnpc.spi.role
vn.heomc.livingnpc.spi.persistence
vn.heomc.livingnpc.spi.lifecycle
```

`vn.heomc.livingnpc.internal`, class implementation hiện tại và mọi manager không nằm trên compile classpath của addon.

## 3. Bootstrap và dependency contract fail-closed

Core publish một entry point ổn định qua Bukkit ServicesManager:

```java
public interface LivingNpcProvider {
    ApiVersion apiVersion();
    Set<CapabilityId> capabilities();
    LivingNpcApi api();
}

public interface LivingNpcApi {
    ApiVersion apiVersion();
    CapabilityView capabilities();
    NpcDirectory npcs();
    RoleDirectory roles();
    VillageDirectory villages();
    EventBus events();
    ExtensionRegistry extensions(); // chỉ có qua SPI/permission phù hợp
}
```

Addon không tự `new LivingNpcApi` và không dùng plugin singleton. Trong `onLoad/onEnable`:

1. lấy `RegisteredServiceProvider<LivingNpcProvider>`;
2. kiểm tra provider plugin đang `isEnabled()`;
3. kiểm tra API major chính xác, minor trong range `[min,max]`;
4. kiểm tra từng capability bắt buộc;
5. nếu fail: disable addon (`PluginManager.disablePlugin(this)`), log một lỗi actionable, không đăng listener/task và không sửa dữ liệu;
6. capability optional thì degrade có chủ đích, không coi `null` là capability.

Contract gợi ý:

```java
public record ApiRequirement(int major, int minMinor, int maxMinor) {}
public record DependencyContract(
    String addonId, ApiRequirement api, Set<CapabilityId> required,
    Set<CapabilityId> optional) {}
public sealed interface BindResult permits Bound, Rejected {}
```

`LivingNpcProvider` đăng ký trước khi mở extension, nhưng chỉ sau khi core đã validate Citizens bắt buộc. Nếu Citizens không có/không đúng version, core không đăng service và không nhận addon. `plugin.yml` của addon nên có `depend: [LivingNPC]`; nếu addon có thể chạy độc lập thì `softdepend` nhưng vẫn phải tự fail-closed khi bind thất bại.

Không đảm bảo ABI qua `LivingNpcPlugin` hoặc package `vn.heomc.livingnpc`; mọi class đó là internal. API artifact cần được kiểm tra binary compatibility trong CI.

## 4. API consumer: services, value objects và Result

### 4.1 NpcDirectory

```java
public interface NpcDirectory {
    Optional<NpcView> find(NpcId id);
    Stream<NpcView> findByVillage(VillageId id);
    Result<NpcSnapshot, ApiError> snapshot(NpcId id);
}

public record NpcId(long citizensId, UUID stableId) {}
public record NpcSnapshot(NpcId id, String profileKey, Set<RoleId> roles,
                          Optional<VillageId> village, NpcLifecycleState state) {}
```

`NpcView` chỉ đọc. Không trả `NPC`, `Entity`, `LivingEntity`, manager hoặc store. Nếu cần entity hiện tại, API trả `Optional<EntityHandle>` sống ngắn hạn và mọi operation vẫn đi qua core service.

### 4.2 Command/service có ownership

```java
public interface NpcCommandService {
    Result<OperationId, ApiError> requestRole(NpcId npc, RoleId role, CommandContext ctx);
    Result<LeaseHandle, ApiError> acquireActivity(NpcId npc, ActivityClaim claim);
    Result<Void, ApiError> releaseActivity(LeaseHandle lease);
}
```

Mỗi mutation có `CommandContext(addonId, correlationId, actor, deadline)` và policy kiểm tra capability/permission. Addon không được tự teleport, tự đặt block, tự tạo Bukkit task điều khiển NPC hay chiếm navigation.

### 4.3 Result và lỗi ổn định

Không dùng exception để biểu diễn điều kiện nghiệp vụ bình thường:

```java
public sealed interface Result<T,E> permits Success, Failure {}
public record Failure<T,E>(E error) implements Result<T,E> {}
public enum ApiErrorCode {
    CORE_NOT_READY, UNSUPPORTED_API, CAPABILITY_DENIED, NOT_FOUND,
    STALE_HANDLE, CONFLICT, OWNERSHIP_REQUIRED, INVALID_REQUEST,
    PERSISTENCE_UNAVAILABLE, WORLD_MUTATION_DENIED, RELOAD_IN_PROGRESS,
    SHUTTING_DOWN, RATE_LIMITED
}
public record ApiError(ApiErrorCode code, String message, boolean retryable) {}
```

Thông báo cho addon không được phụ thuộc text log; addon switch theo `ApiErrorCode`.

## 5. SPI cho addon provider

Addon đăng ký extension bằng ID ổn định và priority, core quyết định khi nào invoke:

```java
public interface LivingNpcExtension {
    ExtensionDescriptor descriptor();
    void onBind(ExtensionContext context);
    void onUnbind(UnbindReason reason);
}

public interface RoleProvider extends LivingNpcExtension {
    RoleDefinition definition();
    RoleDecision decide(RoleContext context);
    void onTick(RoleTickContext context); // budget và cancellation do core quản lý
}

public interface PersistenceContributor {
    String namespace(); // reverse-DNS, ví dụ com.example.market
    SchemaVersion schemaVersion();
    PersistenceSnapshot capture(Collection<NpcId> ids);
    void restore(PersistenceSnapshot snapshot);
    MigrationResult migrate(SchemaVersion from, SchemaVersion to, RawData data);
}
```

SPI không cho addon thay thế core policy. `RoleDecision` là data (intent/priority/deadline), ví dụ `RequestNavigation`, `RequestInteract`, `Yield`, `Idle`; core validate và arbitrate qua `NavigationLeaseManager`. Addon không nhận `BukkitTask`; `ScheduledWork`/`CancellationToken` do core cung cấp và tự huỷ khi unload/disable.

Extension registration phải atomic: validate descriptor, namespace, schema, required capabilities, duplicate ID và budget trước khi publish. Nếu `onBind` lỗi, rollback registration của addon đó, disable extension; không làm core fail toàn bộ. Nếu extension vi phạm nhiều lần, circuit-breaker và phát event diagnostic.

## 6. Events và capabilities

Event bus phân biệt synchronous decision event và asynchronous notification:

```java
public interface EventBus {
    <E extends LivingNpcEvent> Subscription subscribe(
        Class<E> type, EventOptions options, Consumer<E> listener);
}
public sealed interface LivingNpcEvent permits CoreReady, NpcLoaded, NpcUnloaded,
    NpcRoleChanged, ActivityLeaseChanged, WorldMutationRejected, CoreReloading,
    CoreReloaded, CoreStopping, ExtensionFault {}
```

- Event payload immutable, có `CoreGeneration generation`, `Instant/tick`, `NpcId` và correlation ID.
- Event thứ tự được đảm bảo trong cùng main-thread; không đảm bảo thứ tự giữa async observers.
- Listener lỗi bị cô lập, log kèm addon ID; không làm hỏng tick loop.
- Event cancellation chỉ tồn tại ở các event explicitly cancellable; mặc định notification không thể chặn core.

Capability là quyền + khả năng runtime, không chỉ feature flag:

```java
record CapabilityId(String value) {}
// livingnpc:npc.read, livingnpc:role.register, livingnpc:navigation.request,
// livingnpc:world.mutate, livingnpc:persistence.namespace, livingnpc:diagnostics.read
```

Capability `world.mutate` luôn đi qua `WorldMutationService`, tôn trọng WorldGuard và `fail-closed`; addon không được nhận bypass. Capability snapshot có generation; khi reload/dependency mất, handle cũ bị stale.

## 7. Ownership, persistence và lifecycle

### 7.1 Ownership

- Core sở hữu Citizens NPC, profile/role lõi, village definitions, schedules, economy, needs, navigation leases, Bukkit tasks/listeners và mọi world restoration.
- Addon sở hữu logic extension và namespace dữ liệu riêng. Không ghi `profiles.yml`, `prices.yml`, `recipes.yml`, economy/needs stores của core.
- `ownerId` được ghi vào activity lease, task, mutation journal và extension registration. Core chỉ release tài nguyên đúng owner; khi addon mất bất thường, watchdog cleanup theo owner.
- Tác vụ chạy phải có `OperationId`, deadline tick, cancellation token; reload/disable huỷ và join/cleanup bounded, không để task mồ côi.

### 7.2 Persistence

Mỗi namespace riêng, ví dụ `plugins/LivingNPC/extensions/com.example.market/v1/data.yml`, dùng `AtomicYamlStore`/journal tương đương nhưng API expose `StorageService` chứ không expose File path tuỳ ý. Envelope tối thiểu:

```yaml
format: livingnpc-extension
namespace: com.example.market
schema: 1
coreApiMajor: 1
writtenBy: com.example.market@2.3.0
records: ...
```

- Atomic temp + replace; backup trước migration; không overwrite file nếu parse/schema tương lai/malformed.
- `schema` integer monotonic, migration explicit từ từng version; không tự đoán.
- Nếu migrate thất bại: namespace read-only/disabled, giữ file gốc và log; core vẫn chạy các addon khác.
- Snapshot chỉ commit khi lifecycle đạt `READY`; flush theo core scheduler, bounded và không block main thread bằng I/O lớn.
- Addon không được lưu Citizens entity UUID làm identity duy nhất; dùng `NpcId.stableId`/profile key và xử lý `STALE_HANDLE` sau restore.

### 7.3 Lifecycle và reload

#### Contract scheduling tối thiểu đã có trong core

Stage 0 có `vn.heomc.livingnpc.api.lifecycle.LifecycleScope`,
`LifecycleTicket` và `DispatchResult`. Contract không phụ thuộc Paper/Citizens:

- `open(ownerId)` cấp generation tăng toàn scope; ticket cũ không thể current lại (chống ABA).
- `invalidate(ownerId)` làm stale mọi callback outstanding của owner đó.
- `dispatch(ticket, action)` kiểm tra ticket lúc submit và kiểm tra lại lúc callback chạy.
- `close()` tuyến tính với submit/callback: từ chối admission mới và không hoàn tất trong khi
  callback đã được chấp nhận đang chạy.
- Scheduler từ chối được trả thành `SCHEDULER_REJECTED`; stale/stop là kết quả ổn định, không
  expose `BukkitTask`, `JavaPlugin`, Citizens hoặc exception implementation.

Implementation `LifecycleTaskScope` vẫn package-private. Chưa publish provider qua
`ServicesManager`; addon chưa được phép bind trực tiếp cho tới Stage 1.

State machine core:

```text
CREATED -> VALIDATING -> READY -> RELOADING -> READY
                         |             |
                         v             v
                       DEGRADED      DEGRADED
                         \-----------> STOPPING -> STOPPED
```

- `CREATED/VALIDATING`: chỉ bind metadata, chưa schedule work.
- `READY`: publish service/capabilities, fire `CoreReady` sau khi stores và Citizens bridge sẵn sàng.
- `RELOADING`: từ chối mutation mới (`RELOAD_IN_PROGRESS`), freeze admission, drain bounded, snapshot addon, stop runtimes, reload core config/stores, revalidate, restore addon namespaces, increment generation, republish capability snapshot, fire `CoreReloaded`.
- `STOPPING`: huỷ tasks/leases/extensions theo owner; persistence flush best-effort nhưng không bỏ qua cleanup tiếp theo nếu một cleanup lỗi (phù hợp `RuntimeStopCoordinator`). Không nhận API mutation.
- Không hỗ trợ Paper `/reload`, PlugMan hoặc hot-unload. “Reload” chính thức chỉ là command/service của LivingNPC với invariant trên.

## 8. Mapping từ monolith hiện tại

Đây là facade-first, không rewrite một lần:

| Hiện tại | API/SPI đích | Quy tắc |
|---|---|---|
| `LivingNpcPlugin` private fields/accessors | `LivingNpcProvider` + `LivingNpcApi` | chỉ plugin tạo implementation |
| `FarmerManager`, `FisherManager`, `RancherManager`, `CivilProfessionManager` | core `RoleDirectory`/`RoleRuntime` adapter | manager vẫn internal |
| `NavigationLeaseManager` | `ActivityClaimService` | addon chỉ request claim, core arbitrate |
| `OwnedTaskRegistry`, `RuntimeStopCoordinator` | `TaskScope`/`LifecycleContext` | owner tagging bắt buộc |
| `AtomicYamlStore`, `NeedsStore`, `NpcEconomyStore` | `StorageService` + core-owned stores | addon namespace riêng |
| `WorldMutationPolicy` | `WorldMutationService` capability | thiếu WorldGuard/policy => reject |
| `ProfileRegistry`, `VillageStore` | read-only `Profile/VillageDirectory` | snapshot immutable |
| `NavigationDiagnostics` | `DiagnosticsView` optional capability | redact UUID/location nếu không cấp quyền |

Bước chuyển tiếp: tạo `api` package/artifact trước; core facade gọi manager hiện tại. Mọi accessor package-private trên `LivingNpcPlugin` giữ internal và không đưa vào Javadoc public.

## 9. Staged rollout

### Stage 0 — Contract freeze (không đổi hành vi)

- Chốt glossary, API major `1`, capability IDs, error codes, lifecycle/generation.
- Tạo module `livingnpc-api` và ArchUnit/Revapi rule: API không import Paper/Citizens/internal.
- Tạo testkit fake provider và contract tests cho bind/version/capability.
- Acceptance: addon mẫu compile chỉ với API/SPI; `jdeps` không thấy implementation dependency.

### Stage 1 — Read-only facade

- Publish `LivingNpcProvider` qua ServicesManager.
- Expose Npc/Profile/Village snapshots, events `CoreReady/NpcLoaded/Unloaded`, diagnostics optional.
- Không cho mutation; mọi dữ liệu core vẫn do manager/store cũ sở hữu.
- Acceptance: restart/reload không đổi dữ liệu; thiếu Citizens không publish service; addon disabled sạch.

### Stage 2 — Controlled commands

- Expose role request, activity lease và navigation intent; adapter bọc manager hiện tại.
- Thêm owner/correlation/deadline, main-thread guard, rate limit, fail-closed world mutation.
- Acceptance: lease preemption/reclaim, stale generation, shutdown cleanup, WorldGuard unavailable đều có test.

### Stage 3 — SPI provider và persistence namespace

- Cho addon đăng ký `RoleProvider`, `DialogueProvider`/`ActivityProvider` theo capability.
- Bật `PersistenceContributor`, migration/backup/read-only failure isolation.
- Acceptance: một addon lỗi migration/bind/tick không làm dừng addon/core khác; file core không bị chạm.

### Stage 4 — Tách module và phát hành

- Đưa public types sang artifact version độc lập; core chỉ implement API/SPI.
- Javadoc contract, compatibility matrix Paper/Citizens, sample addon, integration test trên Paper thật.
- Chỉ đổi API major khi có migration guide; deprecate ít nhất một minor cycle.

## 10. Rủi ro và biện pháp

| Rủi ro | Tác động | Giảm thiểu |
|---|---|---|
| Addon giữ `NPC`/Entity sau unload | stale object, crash hoặc mutate sai | handle có generation, chỉ snapshot; `STALE_HANDLE`, invalidation event |
| Addon tick chậm/throw | watchdog và ảnh hưởng NPC khác | budget per extension, timeout/cooperative cancellation, circuit-breaker, isolate exception |
| Race async với reload/disable | ghi dữ liệu sau shutdown | main-thread admission, generation token, drain bounded, owner-scoped tasks |
| API lộ Bukkit/Citizens | ABI vỡ khi Paper/Citizens đổi | API Java thuần, adapter core, binary compatibility CI |
| Capability bị coi là feature flag | addon bypass policy | capability signed/issued bởi core, check ở mỗi mutation, deny mặc định |
| Schema addon ghi đè core | mất dữ liệu/rollback khó | namespace reverse-DNS, path sandbox, atomic write, backup, read-only future schema |
| Citizens thay đổi lifecycle | NPC không bind/lease sai | Citizens bridge duy nhất trong core, integration test trên version matrix |
| Reload không atomic | runtime dùng config cũ/mới lẫn nhau | freeze admission, increment generation, rebuild service snapshot rồi publish |
| Manager hiện tại có static/package coupling | migration kéo dài | facade-first, strangler adapters, cấm import bằng build/checkstyle |
| Addon giả mạo ID/duplicate provider | service ambiguity | plugin name + descriptor ID, một registration mỗi owner, reject duplicate |

## 11. Tiêu chí chấp nhận trước khi gọi là public API

- Có Javadoc cho thread/lifecycle/ownership/error semantics.
- API/SPI build được trên Java 21 mà không cần Paper/Citizens.
- Contract tests cho version range, missing service, missing capability, stale generation, shutdown và malformed/future schema.
- Integration tests Paper 1.21.11 + Citizens 2.0.42 cho enable order, NPC load/unload, clean stop, reload chính thức và WorldGuard fail-closed.
- Sample addon không import class ngoài `vn.heomc.livingnpc.api`/`spi` và không cần reflection.
- Binary compatibility report giữa từng release; changelog nêu rõ API/SPI/schema migration.
- Không phát hành Stage 2/3 nếu chưa có controlled evidence về tick performance, restart persistence và cleanup.
