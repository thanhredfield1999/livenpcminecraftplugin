package vn.heomc.livingnpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

final class ResidentGui implements Listener {
    private static final int CREATE_SLOT = 49;
    private static final int LIST_RELOAD_SLOT = 48;
    private static final int CANCEL_PLACEMENT_SLOT = 50;
    private static final int TOWN_STORE_SLOT = 47;
    private static final int SET_STORAGE_SLOT = 46;
    private static final int WORK_ZONES_SLOT = 45;
    private static final int ACTIVITY_SLOT = 51;
    private static final int HOME_SLOT = 18;
    private static final int PLOT_SLOT = 19;
    private static final int RANGE_SLOT = 20;
    private static final int ROLES_SLOT = 21;
    static final int ROLE_ACTIVITY_SLOT = 22;
    private static final int DETAIL_RELOAD_SLOT = 24;
    private static final int REMOVE_SLOT = 25;
    private static final int BACK_SLOT = 26;
    static final int[] WORK_ZONE_JOB_SLOTS = {9, 11, 13, 15, 17, 28, 30};
    static final int[] ROLE_JOB_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    static final int WORK_ZONE_FISHING_SLOT = 18;
    static final int WORK_ZONE_RANCH_SLOT = 19;
    static final int WORK_ZONE_MARKET_SLOT = 20;
    static final int WORK_ZONE_MINING_SLOT = 21;
    static final int WORK_ZONE_SCENIC_SLOT = 22;
    static final int WORK_ZONE_GATE_SLOT = 23;
    static final int WORK_ZONE_VISITORS_SLOT = 24;
    static final int WORK_ZONE_NAVIGATION_GATES_SLOT = 25;
    static final int WORK_ZONE_SEATS_SLOT = 26;
    static final int WORK_ZONE_BACK_SLOT = 44;
    private static final int ADD_SEAT_SLOT = 49;
    private static final int ADD_RANCH_PEN_SLOT = 49;
    private static final int ADD_MINING_ZONE_SLOT = 49;
    private static final int ADD_NAVIGATION_GATE_SLOT = 49;
    private static final long PLACEMENT_TIMEOUT_MILLIS = 120_000L;
    private final LivingNpcPlugin plugin;
    private final Map<UUID, PlacementSession> placements = new HashMap<>();
    private final Map<UUID, Long> dialogueCooldowns = new HashMap<>();

    ResidentGui(LivingNpcPlugin plugin) {
        this.plugin = plugin;
    }

    void openList(Player player) {
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.VILLAGE_LIST, null, 54, Component.text("Danh sách làng"));
        int slot = 0;
        for (VillageDefinition village : plugin.villages().villages()) {
            if (slot >= 45) break;
            menu.villagesBySlot().put(slot, village.id());
            NpcAccount account = plugin.economy().villageAccount(village.id());
            menu.getInventory().setItem(slot++, item(
                    Material.BELL,
                    village.name(),
                    NamedTextColor.GOLD,
                    List.of(
                            "ID: " + village.id(),
                            "World: " + village.center().world(),
                            "Cư dân: " + plugin.manager().npcs(village.id()).size(),
                            "Kho: " + storageUsage(account),
                            "Điểm giao kho: " + village.deliveryLocations().size(),
                            "Season: " + ReleasePolicy.SEASON,
                            "Điểm ngắm cảnh: " + (village.scenicPoint() == null ? "CHƯA ĐẶT" : "ĐÃ ĐẶT"),
                            "Nhấn để quản lý làng")));
        }
        menu.getInventory().setItem(49, item(
                Material.WRITABLE_BOOK, "Tạo làng mới", NamedTextColor.AQUA,
                List.of("Dùng lệnh:", "/livingnpc lang tao <id> <tên>", "Làng được tạo tại vị trí bạn đứng")));
        openMenu(player, menu);
    }

    private void openVillage(Player player, String villageId) {
        VillageDefinition village = plugin.villages().get(villageId);
        if (village == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.RESIDENT_LIST, null, village.id(), 54,
                Component.text("Làng - " + village.name()));
        int slot = 0;
        for (NPC npc : plugin.manager().npcs(village.id())) {
            if (slot >= 45) {
                break;
            }
            FarmerDefinition definition = plugin.manager().get(npc.getUniqueId());
            menu.residentsBySlot().put(slot, npc.getUniqueId());
            menu.getInventory().setItem(slot, residentItem(npc, definition));
            slot++;
        }
        menu.getInventory().setItem(CREATE_SLOT, item(
                Material.WRITABLE_BOOK,
                "Tạo NPC làm việc",
                NamedTextColor.GOLD,
                List.of("Chọn mẫu NPC", "Sau đó nhấp phải một block để đặt nhà")));
        menu.getInventory().setItem(SET_STORAGE_SLOT, item(
                village.deliveryLocations().isEmpty() ? Material.BARREL : Material.CHEST,
                "Điểm giao kho: " + village.deliveryLocations().size(),
                village.deliveryLocations().isEmpty() ? NamedTextColor.RED : NamedTextColor.GREEN,
                List.of("Nhấn để thêm rương hoặc thùng", "Có thể thêm không giới hạn", "NPC tự chọn kho gần có đường đi")));
        menu.getInventory().setItem(WORK_ZONES_SLOT, item(
                Material.LECTERN,
                ReleasePolicy.seasonTwoRuntimesEnabled() ? "Hạ tầng & sinh hoạt làng" : "Sinh hoạt làng",
                NamedTextColor.AQUA,
                ReleasePolicy.seasonTwoRuntimesEnabled()
                        ? List.of("Điểm câu, chuồng trại, Khu đào, điểm chợ và ghế", "Khu đào có thể setup trước khi mở runtime Miner")
                        : List.of("Đặt điểm chợ và ghế ngồi", "Quản lý sinh hoạt Season 1")));
        menu.getInventory().setItem(LIST_RELOAD_SLOT, item(
                Material.RECOVERY_COMPASS,
                "Tải lại cấu hình",
                NamedTextColor.YELLOW,
                List.of("Đọc lại config.yml, profiles.yml và prices.yml", "Nhấn để thực hiện")));
        NpcAccount town = plugin.economy().villageAccount(village.id());
        menu.getInventory().setItem(TOWN_STORE_SLOT, item(
                Material.CHEST,
                "Kho tổng thị trấn",
                NamedTextColor.GOLD,
                List.of(
                        "Vật phẩm: " + storageUsage(town),
                        "Số dư: " + money(town.balanceMinor()),
                        "Nhấn để xem hàng trong kho")));
        menu.getInventory().setItem(ACTIVITY_SLOT, item(
                Material.BOOK,
                "Hoạt động thị trấn",
                NamedTextColor.AQUA,
                List.of("10 hoạt động gần nhất", "Lọc theo từng nghề", "Nhấn đầu NPC để dịch chuyển tới NPC")));
        boolean pending = placements.containsKey(player.getUniqueId());
        menu.getInventory().setItem(CANCEL_PLACEMENT_SLOT, item(
                pending ? Material.BARRIER : Material.GRAY_DYE,
                "Chọn vị trí: " + (pending ? "[ĐANG CHỜ]" : "[KHÔNG CÓ]"),
                pending ? NamedTextColor.RED : NamedTextColor.GRAY,
                List.of(pending ? "Nhấn để hủy thao tác chọn block" : "Không có thao tác chọn vị trí")));
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại danh sách làng", NamedTextColor.YELLOW, List.of("Chọn một làng khác")));
        openMenu(player, menu);
    }

    private void openTownStore(Player player, String villageId) {
        VillageDefinition village = plugin.villages().get(villageId);
        if (village == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.TOWN_STORE, null, village.id(), 54, Component.text("Kho - " + village.name()));
        NpcAccount town = plugin.economy().villageAccount(village.id());
        int slot = 0;
        for (Map.Entry<String, Integer> entry : town.inventory().entrySet()) {
            if (slot >= 45) {
                break;
            }
            menu.getInventory().setItem(slot++, item(
                    materialForItem(entry.getKey()),
                    itemName(entry.getKey()),
                    NamedTextColor.GREEN,
                    List.of("Số lượng: " + entry.getValue(), "Do các NPC nộp vào kho chung")));
        }
        menu.getInventory().setItem(49, item(
                Material.EMERALD,
                "Tài sản thị trấn",
                NamedTextColor.GOLD,
                List.of(
                        "Số dư: " + money(town.balanceMinor()),
                        "Kho: " + storageUsage(town),
                        "Hàng có giá được bán cuối ca nếu đã bật")));
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về làng " + village.name())));
        openMenu(player, menu);
    }

    private void openActivities(Player player, String villageId, ResidentRole filter) {
        VillageDefinition village = plugin.villages().get(villageId);
        if (village == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.ACTIVITY_LIST, villageId, filter, 54,
                Component.text("Hoạt động - " + village.name()));
        List<NpcActivity> activities = plugin.economy().activities(villageId, filter, 10);
        for (int slot = 0; slot < activities.size(); slot++) {
            NpcActivity activity = activities.get(slot);
            FarmerDefinition definition = plugin.manager().get(activity.npcUuid());
            String name = definition == null ? "NPC không còn tồn tại" : definition.profile().name();
            menu.residentsBySlot().put(slot, activity.npcUuid());
            menu.getInventory().setItem(slot, item(
                    Material.PLAYER_HEAD,
                    name + " - " + activity.action(),
                    roleColor(activity.role()),
                    List.of(
                            "Nghề: " + roleName(activity.role()),
                            "Vật phẩm: " + itemName(activity.itemKey()) + " x" + activity.amount(),
                            "Thời gian: " + activityTime(activity.createdAt()),
                            definition == null ? "NPC không còn tồn tại" : "Nhấn để dịch chuyển tới NPC")));
        }
        ResidentRole[] filters = {
                null, ResidentRole.FARMER, ResidentRole.RANCHER, ResidentRole.FISHER,
                ResidentRole.COOK, ResidentRole.CRAFTER, ResidentRole.MINER, ResidentRole.SECURITY};
        int[] slots = {45, 46, 47, 48, 49, 50, 51, 52};
        for (int index = 0; index < filters.length; index++) {
            ResidentRole role = filters[index];
            if (role != null) menu.rolesBySlot().put(slots[index], role);
            boolean selected = filter == role;
            menu.getInventory().setItem(slots[index], item(
                    selected ? Material.LIME_DYE : Material.GRAY_DYE,
                    role == null ? "Tất cả hoạt động" : roleName(role),
                    selected ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                    List.of(selected ? "Đang xem danh mục này" : "Nhấn để lọc")));
        }
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về danh sách cư dân")));
        openMenu(player, menu);
    }

    private void openWorkZones(Player player, String villageId) {
        VillageDefinition village = plugin.villages().get(villageId);
        if (village == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.VILLAGE_WORK_ZONES, null, village.id(), 45,
                Component.text("Hạ tầng - " + village.name()));
        addReleasedWorkZone(menu, village, WORK_ZONE_FISHING_SLOT, VillageWorkZoneType.FISHING);
        addReleasedWorkZone(menu, village, WORK_ZONE_RANCH_SLOT, VillageWorkZoneType.RANCH);
        addReleasedWorkZone(menu, village, WORK_ZONE_MINING_SLOT, VillageWorkZoneType.MINING);
        menu.getInventory().setItem(WORK_ZONE_MARKET_SLOT, item(
                Material.EMERALD,
                "Điểm chợ: " + (village.marketPoint() == null ? "[CHƯA ĐẶT]" : "[ĐÃ ĐẶT]"),
                village.marketPoint() == null ? NamedTextColor.RED : NamedTextColor.GREEN,
                List.of("Cư dân gặp gỡ và trò chuyện tại đây", "Nhấn rồi chọn block đứng an toàn")));
        menu.getInventory().setItem(WORK_ZONE_SCENIC_SLOT, item(
                Material.SPYGLASS,
                "Điểm ngắm cảnh: " + (village.scenicPoint() == null ? "[CHƯA ĐẶT]" : "[ĐÃ ĐẶT]"),
                village.scenicPoint() == null ? NamedTextColor.RED : NamedTextColor.GREEN,
                List.of("Cư dân thư giãn và trò chuyện tại đây", "Nhấn rồi chọn block đứng an toàn")));
        menu.getInventory().setItem(WORK_ZONE_SEATS_SLOT, item(
                Material.OAK_STAIRS,
                "Ghế nghỉ & bàn ăn: " + village.seats().size(),
                village.seats().isEmpty() ? NamedTextColor.YELLOW : NamedTextColor.GREEN,
                List.of("Đặt Stair để NPC nghỉ hoặc ăn", "Block rắn trước ghế được nhận là bàn", "Nhấn để quản lý")));
        menu.getInventory().setItem(WORK_ZONE_GATE_SLOT, item(
                Material.OAK_FENCE_GATE,
                "Cổng khách: " + (village.visitorGate() == null ? "[CHƯA ĐẶT]" : "[ĐÃ ĐẶT]"),
                village.visitorGate() == null ? NamedTextColor.RED : NamedTextColor.GREEN,
                List.of("Điểm xuất hiện/rời làng của khách vãng lai", "Nhấn rồi chọn block cạnh vị trí đứng")));
        menu.getInventory().setItem(WORK_ZONE_NAVIGATION_GATES_SLOT, item(
                Material.SPRUCE_FENCE_GATE,
                "Cổng điều hướng NPC: " + village.navigationGates().size() + "/32",
                village.navigationGates().isEmpty() ? NamedTextColor.YELLOW : NamedTextColor.GREEN,
                List.of("Chỉ fence gate đã đăng ký mới được NPC mở", "Nhấn để thêm, kiểm tra hoặc xóa cổng")));
        menu.getInventory().setItem(WORK_ZONE_BACK_SLOT, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về màn hình làng")));
        openMenu(player, menu);
    }

    private void openNavigationGates(Player player, String villageId) {
        VillageDefinition village = plugin.villages().get(villageId);
        if (village == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.NAVIGATION_GATE_LIST, null, village.id(), 54,
                Component.text("Cổng điều hướng - " + village.name()));
        for (int index = 0; index < village.navigationGates().size() && index < 45; index++) {
            StoredLocation gate = village.navigationGates().get(index);
            menu.navigationGatesBySlot().put(index, index);
            Location resolved = gate.resolve();
            boolean valid = resolved != null && resolved.getBlock().getBlockData()
                    instanceof org.bukkit.block.data.type.Gate;
            menu.getInventory().setItem(index, item(
                    valid ? Material.SPRUCE_FENCE_GATE : Material.BARRIER,
                    "Cổng " + (index + 1), valid ? NamedTextColor.GREEN : NamedTextColor.RED,
                    List.of("World: " + gate.world(),
                            "XYZ: " + (int) gate.x() + ", " + (int) gate.y() + ", " + (int) gate.z(),
                            valid ? "Trạng thái: fence gate hợp lệ" : "Trạng thái: world chưa load hoặc block đã đổi",
                            "Click trái: dịch chuyển tới cổng", "Shift + click phải: xóa cấu hình")));
        }
        menu.getInventory().setItem(ADD_NAVIGATION_GATE_SLOT, item(
                village.navigationGates().size() >= 32 ? Material.BARRIER : Material.SPRUCE_FENCE_GATE,
                "Thêm cổng điều hướng: " + village.navigationGates().size() + "/32",
                village.navigationGates().size() >= 32 ? NamedTextColor.RED : NamedTextColor.AQUA,
                village.navigationGates().size() >= 32
                        ? List.of("Đã đạt giới hạn 32 cổng")
                        : List.of("Nhấn rồi click phải trực tiếp fence gate", "NPC không tự quét các cổng chưa đăng ký")));
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về Hạ tầng làng")));
        openMenu(player, menu);
    }

    private void addReleasedWorkZone(
            ResidentMenu menu, VillageDefinition village, int slot, VillageWorkZoneType type) {
        if (!ReleasePolicy.workZoneEnabled(type)) return;
        menu.workZonesBySlot().put(slot, type);
        menu.getInventory().setItem(slot, workZoneItem(village, type));
    }

    private void openRanchPens(Player player, String villageId) {
        VillageDefinition village = plugin.villages().get(villageId);
        if (village == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.RANCH_LIST, null, village.id(), 54,
                Component.text("Chuồng trại - " + village.name()));
        int radius = plugin.config().workZoneValidationRadius();
        int vertical = plugin.config().workZoneValidationVerticalRange();
        for (int slot = 0; slot < village.ranchPens().size() && slot < 45; slot++) {
            RanchPen pen = village.ranchPens().get(slot);
            menu.ranchPensBySlot().put(slot, pen.id());
            Location center = pen.center().resolve();
            Map<String, Integer> species = center == null ? Map.of() : ranchSpecies(center, radius, vertical);
            List<String> lore = new ArrayList<>();
            lore.add("World: " + pen.center().world());
            lore.add("XYZ: " + (int) pen.center().x() + ", " + (int) pen.center().y() + ", " + (int) pen.center().z());
            lore.add("Tổng vật nuôi: " + species.values().stream().mapToInt(Integer::intValue).sum());
            if (species.isEmpty()) lore.add(center == null ? "World chưa được load" : "Không có vật nuôi trong vùng quét");
            else species.forEach((name, count) -> lore.add(name + ": " + count));
            lore.add("Click trái: dịch chuyển tới chuồng");
            lore.add("Shift + click phải: xóa chuồng");
            Map.Entry<String, Integer> singleSpecies = species.size() == 1
                    ? species.entrySet().iterator().next() : null;
            menu.getInventory().setItem(slot, item(
                    species.keySet().stream().findFirst().map(this::ranchMaterial).orElse(Material.HAY_BLOCK),
                    "Chuồng " + (slot + 1) + (singleSpecies == null
                            ? "" : " - " + singleSpecies.getKey() + " x" + singleSpecies.getValue()),
                    center == null ? NamedTextColor.RED : species.isEmpty() ? NamedTextColor.YELLOW : NamedTextColor.GREEN,
                    lore));
        }
        menu.getInventory().setItem(ADD_RANCH_PEN_SLOT, item(
                village.ranchPens().size() >= 9 ? Material.BARRIER : Material.HAY_BLOCK,
                "Thêm chuồng: " + village.ranchPens().size() + "/9",
                village.ranchPens().size() >= 9 ? NamedTextColor.RED : NamedTextColor.AQUA,
                village.ranchPens().size() >= 9
                        ? List.of("Đã đạt giới hạn 9 chuồng")
                        : List.of("Nhấn rồi click phải tâm chuồng", "Mỗi chuồng quét bán kính 6, cao +/-3")));
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về Khu nghề & khách vãng lai")));
        openMenu(player, menu);
    }

    private void openMiningZones(Player player, String villageId) {
        VillageDefinition village = plugin.villages().get(villageId);
        if (village == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.MINING_ZONE_LIST, null, village.id(), 54,
                Component.text("Khu đào - " + village.name()));
        for (int slot = 0; slot < village.miningZones().size() && slot < 45; slot++) {
            MiningZone zone = village.miningZones().get(slot);
            menu.miningZonesBySlot().put(slot, zone.id());
            Location corner = zone.corner().resolve();
            boolean loaded = corner != null && zone.chunksLoaded(corner.getWorld());
            int mineable = loaded ? mineableBlockCount(zone, corner) : 0;
            menu.getInventory().setItem(slot, item(Material.IRON_PICKAXE, zone.id(),
                    !loaded ? NamedTextColor.RED : mineable == 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN,
                    List.of(
                            "World: " + zone.corner().world(),
                            "Góc 2x2: " + (int) zone.corner().x() + ", " + (int) zone.corner().z(),
                            "Độ cao: " + zone.minY() + " đến " + zone.maxY(),
                            loaded ? "Block allowlist hiện tại: " + mineable + "/20" : "Trạng thái: world hoặc chunk chưa load",
                            "Click trái: dịch chuyển tới khu", "Shift + click phải: mở xác nhận xóa")));
        }
        menu.getInventory().setItem(ADD_MINING_ZONE_SLOT, item(
                village.miningZones().size() >= 16 ? Material.BARRIER : Material.DEEPSLATE_IRON_ORE,
                "Thêm khu đào: " + village.miningZones().size() + "/16",
                village.miningZones().size() >= 16 ? NamedTextColor.RED : NamedTextColor.AQUA,
                List.of("Chọn block làm góc khu 2x2", "Phạm vi cao mặc định: Y +/-2")));
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về Khu nghề & khách vãng lai")));
        openMenu(player, menu);
    }

    private void openSeats(Player player, String villageId) {
        VillageDefinition village = plugin.villages().get(villageId);
        if (village == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.SEAT_LIST, null, village.id(), 54,
                Component.text("Ghế - " + village.name()));
        int slot = 0;
        for (SeatDefinition seat : village.seats()) {
            if (slot >= 45) break;
            menu.seatsBySlot().put(slot, seat.id());
            boolean valid = SeatValidator.stillValid(seat);
            menu.getInventory().setItem(slot++, item(
                    seat.type() == SeatType.DINING ? Material.COOKED_BEEF : Material.OAK_STAIRS,
                    seat.type() == SeatType.DINING ? "Ghế bàn ăn" : "Ghế nghỉ",
                    valid ? NamedTextColor.GREEN : NamedTextColor.RED,
                    List.of(
                            "XYZ: " + (int) seat.location().x() + ", "
                                    + (int) seat.location().y() + ", " + (int) seat.location().z(),
                            "Hướng nhìn: " + Math.round(seat.location().yaw()) + "°",
                            valid ? "Nhấn để dịch chuyển tới ghế" : "Stair/bàn hoặc lối vào không còn hợp lệ",
                            "Shift + click phải để xóa")));
        }
        menu.getInventory().setItem(ADD_SEAT_SLOT, item(
                Material.OAK_STAIRS, "Thêm ghế", NamedTextColor.AQUA,
                List.of("Nhấn rồi click phải trực tiếp vào Stair", "Plugin tự nhận diện ghế nghỉ hoặc bàn ăn")));
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về Khu nghề & khách vãng lai")));
        openMenu(player, menu);
    }

    void openDetail(Player player, UUID uuid) {
        NPC npc = plugin.manager().npc(uuid);
        FarmerDefinition definition = plugin.manager().get(uuid);
        if (npc == null || definition == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.RESIDENT_DETAIL, uuid, 27, Component.text("Cư dân - " + npc.getName()));
        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        List<BehaviorFlag> flags = java.util.Arrays.stream(BehaviorFlag.values())
                .filter(behavior -> behavior != BehaviorFlag.MASTER
                        && behavior != BehaviorFlag.HARVEST
                        && behavior != BehaviorFlag.PLANT)
                .filter(behavior -> definition.activeRole().usesFarmerSetup()
                        || behavior != BehaviorFlag.SELL_INVENTORY)
                .toList();
        for (int index = 0; index < flags.size(); index++) {
            BehaviorFlag behavior = flags.get(index);
            int slot = slots[index];
            menu.behaviorsBySlot().put(slot, behavior);
            menu.getInventory().setItem(slot, behaviorItem(definition, behavior));
        }
        menu.getInventory().setItem(13, residentItem(npc, definition));
        menu.getInventory().setItem(HOME_SLOT, locationItem(
                Material.RED_BED, "Giường & điểm spawn", definition.home(), true,
                "Nhấn, sau đó nhấp phải đúng block giường"));
        if (definition.activeRole().usesFarmerSetup()) {
            menu.getInventory().setItem(PLOT_SLOT, locationItem(
                    Material.FARMLAND, "Khu ruộng", definition.plot(), definition.plot() != null,
                    "Nhấn, rồi nhấp phải cây hoặc đất ở tâm ruộng"));
            menu.getInventory().setItem(RANGE_SLOT, item(
                    Material.COMPARATOR,
                    "Bán kính ruộng: " + definition.plotRadius() + " "
                            + (definition.plot() == null ? "[CHƯA ĐẶT]" : "[ĐÃ ĐẶT]"),
                    definition.plot() == null ? NamedTextColor.RED : NamedTextColor.GREEN,
                    List.of(
                            "Chuột trái: +1",
                            "Chuột phải: -1",
                            "Giữ Shift: thay đổi 2 block",
                            "Tối đa: " + plugin.config().maxPlotRadius())));
        } else if (definition.activeRole() == ResidentRole.RANCHER) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            StoredLocation ranch = village == null ? null : village.workZone(VillageWorkZoneType.RANCH);
            menu.getInventory().setItem(PLOT_SLOT, item(
                    Material.HAY_BLOCK,
                    "Khu chăn nuôi: " + (ranch == null ? "[CHƯA ĐẶT]" : "[ĐÃ ĐẶT]"),
                    ranch == null ? NamedTextColor.RED : NamedTextColor.GREEN,
                    List.of("Nhấn để mở Khu nghề của làng", "Cần Hay Bale và hàng rào/cổng")));
            menu.getInventory().setItem(RANGE_SLOT, item(
                    Material.WHEAT,
                    "Giới hạn đàn: " + (village == null ? 8 : village.ranchAnimalLimit()) + " mỗi loài",
                    NamedTextColor.GOLD,
                    List.of("Chuột trái: +1", "Chuột phải: -1", "Giữ Shift: thay đổi 4", "Tối thiểu 2, tối đa 64")));
        } else if (definition.activeRole() == ResidentRole.FISHER) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            StoredLocation fishing = village == null ? null : village.workZone(VillageWorkZoneType.FISHING);
            menu.getInventory().setItem(PLOT_SLOT, item(
                    Material.FISHING_ROD,
                    "Điểm câu: " + (fishing == null ? "[CHƯA ĐẶT]" : "[ĐÃ ĐẶT]"),
                    fishing == null ? NamedTextColor.RED : NamedTextColor.GREEN,
                    List.of("Đây là ô NPC đứng câu trên bờ", "NPC tự quét và quăng cần về phía nước gần đó")));
            menu.getInventory().setItem(RANGE_SLOT, item(
                    Material.COD,
                    "Sản lượng Ngư dân",
                    NamedTextColor.AQUA,
                    List.of("25-45 giây mỗi lần thử", "70% thành công", "Tối đa 12 cá mỗi ca")));
        } else if (CivilProfessionRuntime.zoneFor(definition.activeRole()) != null) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            VillageWorkZoneType zone = CivilProfessionRuntime.zoneFor(definition.activeRole());
            StoredLocation center = village == null ? null : village.workZone(zone);
            menu.getInventory().setItem(PLOT_SLOT, item(
                    roleMaterial(definition.activeRole()),
                    workZoneName(zone) + ": " + (center == null ? "[CHƯA ĐẶT]" : "[ĐÃ ĐẶT]"),
                    center == null ? NamedTextColor.RED : NamedTextColor.GREEN,
                    List.of("Nhấn để mở Khu nghề", "Dùng kho làng làm đầu vào và đầu ra")));
            menu.getInventory().setItem(RANGE_SLOT, item(
                    definition.activeRole() == ResidentRole.SECURITY ? Material.SHIELD : Material.CHEST,
                    definition.activeRole() == ResidentRole.SECURITY ? "Tuần tra & báo động" : "Sản lượng nghề",
                    NamedTextColor.AQUA,
                    List.of(definition.activeRole() == ResidentRole.SECURITY
                            ? "Phát hiện quái trong 12 block, không gây damage"
                            : "Tối đa 12 sản phẩm mỗi ca")));
        } else if (definition.activeRole() == ResidentRole.MERCHANT) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            MerchantStall stall = village == null ? null : village.merchantStall(definition.npcUuid());
            menu.getInventory().setItem(PLOT_SLOT, locationItem(
                    Material.LECTERN, "Điểm đứng Dân buôn", stall == null ? null : stall.sellerPoint(),
                    stall != null && stall.sellerPoint() != null,
                    "Nhấn rồi chọn block quầy; hướng nhìn được lưu theo bạn"));
            menu.getInventory().setItem(RANGE_SLOT, locationItem(
                    Material.EMERALD, "Điểm người mua", stall == null ? null : stall.buyerPoint(),
                    stall != null && stall.buyerPoint() != null,
                    "Nhấn rồi chọn ô khách đứng phía trước quầy"));
        } else {
            menu.getInventory().setItem(PLOT_SLOT, item(
                    Material.MAP,
                    "Cấu hình nghề: " + roleName(definition.activeRole()),
                    NamedTextColor.AQUA,
                    List.of(definition.activeRole() == ResidentRole.RESIDENT
                            ? "Người dân sinh hoạt quanh Nhà, không cần khu làm việc"
                            : "Khu làm việc sẽ xuất hiện khi runtime nghề hoàn thành")));
        }
        menu.getInventory().setItem(ROLES_SLOT, item(
                Material.LECTERN,
                "Chọn nghề",
                NamedTextColor.AQUA,
                List.of(
                        "Nghề hiện tại: " + roleName(definition.activeRole()),
                        "Season " + ReleasePolicy.SEASON + ": nghề đã qua release gate",
                        "Nhấn để chọn nghề")));
        ResidentSchedule schedule = definition.schedule(definition.activeRole(), defaultSchedule());
        boolean roleActive = plugin.manager().roleActive(uuid);
        menu.getInventory().setItem(ROLE_ACTIVITY_SLOT, item(
                roleActive ? Material.CLOCK : Material.GRAY_DYE,
                "Trạng thái & lịch làm việc",
                roleActive ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of(
                        "Nghề: " + roleName(definition.activeRole()) + " - " + state(roleActive),
                        "Hiện tại: " + currentActivity(definition),
                        "Làm việc: " + clockTime(schedule.startTick()) + " - " + clockTime(schedule.endTick()),
                        "Nghỉ: " + clockTime(schedule.endTick()) + " - " + clockTime(schedule.startTick()),
                        "Nhấn để bật/tắt và chỉnh thời gian")));
        menu.getInventory().setItem(DETAIL_RELOAD_SLOT, item(
                Material.RECOVERY_COMPASS, "Tải lại cấu hình", NamedTextColor.YELLOW, List.of("Nhấn để tải lại rồi quay về đây")));
        menu.getInventory().setItem(REMOVE_SLOT, item(
                Material.LAVA_BUCKET, "Xóa cư dân", NamedTextColor.RED, List.of("Mở màn hình xác nhận")));
        menu.getInventory().setItem(BACK_SLOT, item(Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về danh sách cư dân")));
        openMenu(player, menu);
    }

    private void openRoles(Player player, UUID uuid) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        if (definition == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.ROLE_LIST, uuid, 36, Component.text("Chọn nghề - " + definition.profile().name()));
        List<ResidentRole> jobs = ReleasePolicy.enabledRoles().stream()
                .sorted()
                .toList();
        int[] jobSlots = ROLE_JOB_SLOTS;
        for (int index = 0; index < jobs.size() && index < jobSlots.length; index++) {
            ResidentRole role = jobs.get(index);
            menu.rolesBySlot().put(jobSlots[index], role);
            menu.getInventory().setItem(jobSlots[index], jobItem(definition, role));
        }
        menu.getInventory().setItem(4, item(
                Material.BOOK,
                "Cách sử dụng",
                NamedTextColor.YELLOW,
                List.of(
                        "Nhấn nghề để chọn",
                        "ON/OFF và lịch nằm trong màn chi tiết",
                        "Nghề khóa chưa tác động thế giới")));
        menu.getInventory().setItem(35, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về màn hình chi tiết NPC")));
        openMenu(player, menu);
    }

    private void openRoleSchedule(Player player, UUID uuid, ResidentRole role) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        if (definition == null || !definition.profile().hasRole(role)) {
            openRoles(player, uuid);
            return;
        }
        ResidentSchedule fallback = defaultSchedule();
        ResidentSchedule schedule = definition.schedule(role, fallback);
        boolean custom = definition.schedules().containsKey(role);
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.ROLE_SCHEDULE, uuid, role, 27,
                Component.text("Chỉnh lịch - " + roleName(role)));
        menu.getInventory().setItem(11, scheduleControl(
                "Giờ bắt đầu: " + clockTime(schedule.startTick()), schedule.startTick(), true));
        menu.getInventory().setItem(13, item(
                plugin.manager().roleActive(uuid) ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                roleName(role) + ": " + state(plugin.manager().roleActive(uuid)),
                plugin.manager().roleActive(uuid) ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of(
                        "Hiện tại: " + currentActivity(definition),
                        "Làm việc: " + clockTime(schedule.startTick()) + " - " + clockTime(schedule.endTick()),
                        "Nghỉ: " + clockTime(schedule.endTick()) + " - " + clockTime(schedule.startTick()),
                        "Nguồn lịch: " + (custom ? "riêng cho nghề" : "mặc định config.yml"),
                        "Nhấn để bật/tắt nghề")));
        menu.getInventory().setItem(15, scheduleControl(
                "Giờ kết thúc: " + clockTime(schedule.endTick()), schedule.endTick(), false));
        menu.getInventory().setItem(22, item(
                Material.MILK_BUCKET,
                "Dùng lịch mặc định",
                NamedTextColor.AQUA,
                List.of(
                        "Mặc định: " + clockTime(fallback.startTick()) + " - " + clockTime(fallback.endTick()),
                        custom ? "Nhấn để xóa lịch riêng" : "Hiện đang dùng lịch mặc định")));
        menu.getInventory().setItem(BACK_SLOT, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về màn hình chi tiết NPC")));
        openMenu(player, menu);
    }

    private void openProfiles(Player player, String villageId) {
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.PROFILE_LIST, null, villageId, 54, Component.text("Chọn mẫu NPC"));
        int slot = 0;
        for (String id : plugin.profiles().ids()) {
            if (slot >= 45) {
                break;
            }
            ResidentProfile profile = plugin.profiles().get(id);
            boolean supported = profile.roles().stream().anyMatch(ReleasePolicy::roleEnabled);
            menu.profilesBySlot().put(slot, id);
            menu.getInventory().setItem(slot, item(
                    supported ? Material.PLAYER_HEAD : Material.BARRIER,
                    profile.name() + " - " + profile.title(),
                    supported ? NamedTextColor.GOLD : NamedTextColor.RED,
                    profileLore(profile, List.of(
                            "Giới tính: " + genderName(profile.gender()),
                            "Nghề: " + profile.roles().stream().sorted().map(this::roleName).toList(),
                            "Skin: " + (profile.skin().isBlank() ? "mặc định" : profile.skin()),
                            supported ? "Nhấn để bắt đầu chọn vị trí" : "Chưa có runtime tương ứng"))));
            slot++;
        }
        menu.getInventory().setItem(49, item(Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về danh sách cư dân")));
        openMenu(player, menu);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ResidentMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        int slot = event.getRawSlot();
        switch (menu.type()) {
            case VILLAGE_LIST -> {
                String villageId = menu.villagesBySlot().get(slot);
                if (villageId != null) {
                    openVillage(player, villageId);
                }
            }
            case RESIDENT_LIST -> {
                UUID uuid = menu.residentsBySlot().get(slot);
                if (uuid != null) {
                    openDetail(player, uuid);
                } else if (slot == CREATE_SLOT) {
                    openProfiles(player, menu.villageId());
                } else if (slot == TOWN_STORE_SLOT) {
                    openTownStore(player, menu.villageId());
                } else if (slot == ACTIVITY_SLOT) {
                    openActivities(player, menu.villageId(), null);
                } else if (slot == SET_STORAGE_SLOT) {
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_TOWN_STORAGE,
                            null,
                            null,
                            menu.villageId(),
                            0,
                            System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == WORK_ZONES_SLOT) {
                    openWorkZones(player, menu.villageId());
                } else if (slot == LIST_RELOAD_SLOT) {
                    reload(player, null);
                } else if (slot == CANCEL_PLACEMENT_SLOT && cancelPlacement(player)) {
                    openVillage(player, menu.villageId());
                } else if (slot == 53) {
                    openList(player);
                }
            }
            case TOWN_STORE -> {
                if (slot == 53) {
                    openVillage(player, menu.villageId());
                }
            }
            case ACTIVITY_LIST -> {
                UUID uuid = menu.residentsBySlot().get(slot);
                if (uuid != null) {
                    NPC npc = plugin.manager().npc(uuid);
                    if (npc != null && npc.isSpawned()) {
                        Location target = safeLocationNear(npc.getEntity().getLocation());
                        if (target != null && player.teleport(target)) {
                            player.sendMessage(Component.text(
                                    "[ĐÃ DỊCH CHUYỂN] Tới " + npc.getName() + ".", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("Không tìm được ô đứng an toàn cạnh NPC.", NamedTextColor.RED));
                        }
                    } else {
                        player.sendMessage(Component.text("NPC hiện không spawn.", NamedTextColor.RED));
                    }
                } else if (slot == 45 && !menu.rolesBySlot().containsKey(slot)) {
                    openActivities(player, menu.villageId(), null);
                } else if (menu.rolesBySlot().containsKey(slot)) {
                    openActivities(player, menu.villageId(), menu.rolesBySlot().get(slot));
                } else if (slot == 53) {
                    openVillage(player, menu.villageId());
                }
            }
            case VILLAGE_WORK_ZONES -> {
                VillageWorkZoneType type = menu.workZonesBySlot().get(slot);
                if (type == VillageWorkZoneType.RANCH) {
                    openRanchPens(player, menu.villageId());
                } else if (type == VillageWorkZoneType.MINING && event.isRightClick()) {
                    openMiningZones(player, menu.villageId());
                } else if (type != null) {
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_WORK_ZONE, null, null, menu.villageId(), type,
                            0, System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == WORK_ZONE_MARKET_SLOT) {
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_MARKET_POINT, null, null, menu.villageId(), null,
                            0, System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == WORK_ZONE_SCENIC_SLOT) {
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_SCENIC_POINT, null, null, menu.villageId(), null,
                            0, System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == WORK_ZONE_VISITORS_SLOT) {
                    toggleVisitors(player, menu.villageId());
                } else if (slot == WORK_ZONE_GATE_SLOT) {
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_VISITOR_GATE, null, null, menu.villageId(), null,
                            0, System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == WORK_ZONE_NAVIGATION_GATES_SLOT) {
                    openNavigationGates(player, menu.villageId());
                } else if (slot == WORK_ZONE_SEATS_SLOT) {
                    openSeats(player, menu.villageId());
                } else if (slot == WORK_ZONE_BACK_SLOT) {
                    openVillage(player, menu.villageId());
                }
            }
            case NAVIGATION_GATE_LIST -> {
                Integer index = menu.navigationGatesBySlot().get(slot);
                VillageDefinition village = plugin.villages().get(menu.villageId());
                StoredLocation gate = village == null || index == null || index >= village.navigationGates().size()
                        ? null : village.navigationGates().get(index);
                if (index != null && event.isShiftClick() && event.isRightClick()) {
                    if (plugin.villages().removeNavigationGate(menu.villageId(), index)) {
                        player.sendMessage(Component.text("[ĐÃ XÓA] Cổng điều hướng đã được gỡ.", NamedTextColor.GREEN));
                    }
                    openNavigationGates(player, menu.villageId());
                } else if (gate != null && event.isLeftClick()) {
                    Location location = gate.resolve();
                    Location target = location == null ? null : safeLocationNear(location);
                    if (target != null && player.teleport(target)) {
                        player.sendMessage(Component.text("[ĐÃ DỊCH CHUYỂN] Tới cổng điều hướng.", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Không tìm được ô đứng an toàn gần cổng.", NamedTextColor.RED));
                    }
                } else if (slot == ADD_NAVIGATION_GATE_SLOT && village != null
                        && village.navigationGates().size() < 32) {
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_NAVIGATION_GATE, null, null, menu.villageId(), null,
                            0, System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == 53) {
                    openWorkZones(player, menu.villageId());
                }
            }
            case RANCH_LIST -> {
                String penId = menu.ranchPensBySlot().get(slot);
                if (penId != null) {
                    VillageDefinition village = plugin.villages().get(menu.villageId());
                    RanchPen pen = village == null ? null : village.ranchPens().stream()
                            .filter(candidate -> candidate.id().equals(penId)).findFirst().orElse(null);
                    if (event.isShiftClick() && event.isRightClick()) {
                        if (plugin.villages().removeRanchPen(menu.villageId(), penId)) {
                            player.sendMessage(Component.text("[ĐÃ XÓA] Chuồng đã được gỡ khỏi làng.", NamedTextColor.GREEN));
                        }
                        openRanchPens(player, menu.villageId());
                    } else if (event.isLeftClick() && !event.isShiftClick() && pen != null) {
                        Location location = pen.center().resolve();
                        Location target = location == null ? null : safeLocationNear(location);
                        if (target != null && player.teleport(target)) {
                            player.sendMessage(Component.text("[ĐÃ DỊCH CHUYỂN] Tới chuồng.", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("Không tìm được ô đứng an toàn tại chuồng.", NamedTextColor.RED));
                        }
                    }
                } else if (slot == ADD_RANCH_PEN_SLOT) {
                    VillageDefinition village = plugin.villages().get(menu.villageId());
                    if (village != null && village.ranchPens().size() < 9) beginPlacement(player, new PlacementSession(
                            PlacementType.SET_RANCH_PEN, null, null, menu.villageId(), null,
                            0, System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == 53) {
                    openWorkZones(player, menu.villageId());
                }
            }
            case MINING_ZONE_LIST -> {
                String zoneId = menu.miningZonesBySlot().get(slot);
                if (zoneId != null) {
                    VillageDefinition village = plugin.villages().get(menu.villageId());
                    MiningZone zone = village == null ? null : village.miningZones().stream()
                            .filter(candidate -> candidate.id().equals(zoneId)).findFirst().orElse(null);
                    if (event.isShiftClick() && event.isRightClick()) {
                        openMiningZoneRemoveConfirm(player, menu.villageId(), zone);
                    } else if (event.isLeftClick() && zone != null) {
                        Location location = zone.corner().resolve();
                        if (location == null || !zone.chunksLoaded(location.getWorld())) {
                            player.sendMessage(Component.text(
                                    "Không thể dịch chuyển: world hoặc chunk của footprint 2x2 chưa load.",
                                    NamedTextColor.RED));
                        } else {
                            Location target = safeMiningTeleportLocation(location);
                            if (target != null && player.teleport(target)) {
                                player.sendMessage(Component.text(
                                        "[ĐÃ DỊCH CHUYỂN] Tới " + zone.id() + ".", NamedTextColor.GREEN));
                            } else {
                                player.sendMessage(Component.text(
                                        "Không tìm được ô đứng an toàn tại " + zone.id() + ".", NamedTextColor.RED));
                            }
                        }
                    }
                } else if (slot == ADD_MINING_ZONE_SLOT) {
                    VillageDefinition village = plugin.villages().get(menu.villageId());
                    if (village != null && village.miningZones().size() < 16) {
                        beginPlacement(player, new PlacementSession(
                                PlacementType.SET_MINING_ZONE, null, null, menu.villageId(), null,
                                0, System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                    } else {
                        player.sendMessage(Component.text("Đã đạt giới hạn 16 Khu đào.", NamedTextColor.RED));
                    }
                } else if (slot == 53) {
                    openWorkZones(player, menu.villageId());
                }
            }
            case MINING_ZONE_REMOVE_CONFIRM -> {
                String zoneId = menu.miningZonesBySlot().get(11);
                if (slot == 11 && zoneId != null) {
                    if (plugin.villages().removeMiningZone(menu.villageId(), zoneId)) {
                        player.sendMessage(Component.text("[ĐÃ XÓA] Khu đào đã được gỡ khỏi làng.", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Không thể xóa Khu đào; dữ liệu chưa được thay đổi.", NamedTextColor.RED));
                    }
                    openMiningZones(player, menu.villageId());
                } else if (slot == 15) {
                    openMiningZones(player, menu.villageId());
                }
            }
            case SEAT_LIST -> {
                String seatId = menu.seatsBySlot().get(slot);
                if (seatId != null) {
                    VillageDefinition village = plugin.villages().get(menu.villageId());
                    SeatDefinition seat = village == null ? null : village.seats().stream()
                            .filter(candidate -> candidate.id().equals(seatId)).findFirst().orElse(null);
                    if (event.isShiftClick() && event.isRightClick()) {
                        if (plugin.manager().removeSeat(menu.villageId(), seatId)) {
                            player.sendMessage(Component.text("[ĐÃ XÓA] Ghế đã được gỡ khỏi làng.", NamedTextColor.GREEN));
                        }
                        openSeats(player, menu.villageId());
                    } else if (seat != null) {
                        Location location = seat.location().resolve();
                        Location target = location == null ? null : safeLocationNear(location);
                        if (target != null) player.teleport(target);
                    }
                } else if (slot == ADD_SEAT_SLOT) {
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_SEAT, null, null, menu.villageId(), null,
                            0, System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == 53) {
                    openWorkZones(player, menu.villageId());
                }
            }
            case RESIDENT_DETAIL -> {
                BehaviorFlag behavior = menu.behaviorsBySlot().get(slot);
                if (behavior != null && plugin.manager().toggle(menu.residentUuid(), behavior)) {
                    openDetail(player, menu.residentUuid());
                } else if (slot == HOME_SLOT) {
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_HOME,
                            menu.residentUuid(),
                            null,
                            null,
                            0,
                            System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == PLOT_SLOT && isFarmer(menu.residentUuid())) {
                    FarmerDefinition definition = plugin.manager().get(menu.residentUuid());
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_PLOT,
                            menu.residentUuid(),
                            null,
                            null,
                            definition == null ? 4 : definition.plotRadius(),
                            System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == RANGE_SLOT && isFarmer(menu.residentUuid())) {
                    adjustRange(player, menu.residentUuid(), event);
                } else if (slot == PLOT_SLOT && isRancher(menu.residentUuid())) {
                    FarmerDefinition definition = plugin.manager().get(menu.residentUuid());
                    if (definition != null) openWorkZones(player, definition.villageId());
                } else if (slot == RANGE_SLOT && isRancher(menu.residentUuid())) {
                    adjustRanchLimit(player, menu.residentUuid(), event);
                } else if ((slot == PLOT_SLOT || slot == RANGE_SLOT) && isFisher(menu.residentUuid())) {
                    FarmerDefinition definition = plugin.manager().get(menu.residentUuid());
                    if (definition != null) openWorkZones(player, definition.villageId());
                } else if ((slot == PLOT_SLOT || slot == RANGE_SLOT) && isCivilProfession(menu.residentUuid())) {
                    FarmerDefinition definition = plugin.manager().get(menu.residentUuid());
                    if (definition != null) openWorkZones(player, definition.villageId());
                } else if ((slot == PLOT_SLOT || slot == RANGE_SLOT) && isMerchant(menu.residentUuid())) {
                    beginPlacement(player, new PlacementSession(
                            slot == PLOT_SLOT ? PlacementType.SET_MERCHANT_SELLER : PlacementType.SET_MERCHANT_BUYER,
                            menu.residentUuid(), null, null, 0,
                            System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == ROLES_SLOT) {
                    openRoles(player, menu.residentUuid());
                } else if (slot == ROLE_ACTIVITY_SLOT) {
                    FarmerDefinition definition = plugin.manager().get(menu.residentUuid());
                    if (definition != null) openRoleSchedule(player, menu.residentUuid(), definition.activeRole());
                } else if (slot == DETAIL_RELOAD_SLOT) {
                    reload(player, menu.residentUuid());
                } else if (slot == REMOVE_SLOT) {
                    openRemoveConfirm(player, menu.residentUuid());
                } else if (slot == BACK_SLOT) {
                    FarmerDefinition definition = plugin.manager().get(menu.residentUuid());
                    if (definition == null || definition.villageId() == null) {
                        openList(player);
                    } else {
                        openVillage(player, definition.villageId());
                    }
                }
            }
            case ROLE_LIST -> {
                ResidentRole role = menu.rolesBySlot().get(slot);
                if (role != null) {
                    selectJob(player, menu.residentUuid(), role);
                } else if (slot == 35) {
                    openDetail(player, menu.residentUuid());
                }
            }
            case ROLE_SCHEDULE -> {
                if (slot == 11 || slot == 15) {
                    adjustSchedule(player, menu.residentUuid(), menu.role(), slot == 11, event);
                } else if (slot == 13) {
                    toggleRoleActive(player, menu.residentUuid());
                } else if (slot == 22) {
                    resetSchedule(player, menu.residentUuid(), menu.role());
                } else if (slot == BACK_SLOT) {
                    openDetail(player, menu.residentUuid());
                }
            }
            case PROFILE_LIST -> {
                String profileId = menu.profilesBySlot().get(slot);
                if (profileId != null) {
                    createFromProfile(player, profileId, menu.villageId());
                } else if (slot == 49) {
                    openVillage(player, menu.villageId());
                }
            }
            case REMOVE_CONFIRM -> {
                if (slot == 11 && plugin.manager().remove(menu.residentUuid())) {
                    player.sendMessage(Component.text("Đã xóa cư dân vĩnh viễn.", NamedTextColor.GREEN));
                    openList(player);
                } else if (slot == 15) {
                    openDetail(player, menu.residentUuid());
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ResidentMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        FarmerDefinition definition = plugin.manager().get(event.getNPC().getUniqueId());
        if (definition == null) {
            return;
        }
        event.setCancelled(true);
        event.setDelayedCancellation(false);
        Player player = event.getClicker();
        if (player.isSneaking()) {
            openDetail(player, definition.npcUuid());
            return;
        }
        long now = System.currentTimeMillis();
        if (dialogueCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        dialogueCooldowns.put(player.getUniqueId(), now + 2_000L);
        org.bukkit.entity.Entity entity = event.getNPC().getEntity();
        FarmerPhase sharedPhase = plugin.manager().phase(event.getNPC().getId());
        FarmerPhase phase = FarmerActivityResolver.resolvePhase(
                definition.activeRole(),
                sharedPhase,
                plugin.fishers().phase(definition.npcUuid()),
                plugin.merchants().phase(definition.npcUuid()),
                plugin.civilProfessions().phase(definition.npcUuid()));
        player.sendMessage(Component.text(
                definition.profile().name() + ": " + dialogue(definition, phase, entity.getLocation()),
                NamedTextColor.GOLD));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        PlacementSession session = placements.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (session.expired(System.currentTimeMillis())) {
            player.sendMessage(Component.text("Thao tác chọn vị trí đã hết hạn. Hãy mở GUI và thử lại.", NamedTextColor.RED));
            reopenAfterPlacement(player, session);
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            reopenAfterPlacement(player, session);
            return;
        }
        switch (session.type()) {
            case CREATE_RESIDENT -> finishCreate(player, session, clicked.getLocation());
            case SET_HOME -> finishHome(player, session, clicked.getLocation());
            case SET_PLOT -> finishPlot(player, session, clicked.getLocation());
            case SET_TOWN_STORAGE -> finishTownStorage(player, session, clicked.getLocation());
            case SET_WORK_ZONE -> finishWorkZone(
                    player, session,
                    session.workZoneType() == VillageWorkZoneType.FISHING
                            ? adjacentLocation(player, clicked, event) : clicked.getLocation());
            case SET_RANCH_PEN -> finishRanchPen(player, session, clicked.getLocation());
            case SET_MINING_ZONE -> finishMiningZone(player, session, clicked.getLocation());
            case SET_MARKET_POINT -> finishMarketPoint(
                    player, session, adjacentLocation(player, clicked, event));
            case SET_SCENIC_POINT -> finishScenicPoint(
                    player, session, adjacentLocation(player, clicked, event));
            case SET_VISITOR_GATE -> finishVisitorGate(
                    player, session, adjacentLocation(player, clicked, event));
            case SET_NAVIGATION_GATE -> finishNavigationGate(player, session, clicked);
            case SET_SEAT -> finishSeat(player, session, clicked);
            case SET_MERCHANT_SELLER, SET_MERCHANT_BUYER -> finishMerchantPoint(
                    player, session, adjacentLocation(player, clicked, event));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        placements.remove(event.getPlayer().getUniqueId());
        dialogueCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void createFromProfile(Player player, String profileId, String villageId) {
        ResidentProfile profile = plugin.profiles().get(profileId);
        if (profile == null || profile.roles().stream().noneMatch(ReleasePolicy::roleEnabled)) {
            player.sendMessage(Component.text("Hồ sơ này không có nghề được mở trong Season 1.", NamedTextColor.RED));
            return;
        }
        if (plugin.manager().usedProfileIds().contains(profile.id().toLowerCase(java.util.Locale.ROOT))) {
            player.sendMessage(Component.text("Hồ sơ cư dân này đã được sử dụng.", NamedTextColor.RED));
            return;
        }
        beginPlacement(player, new PlacementSession(
                PlacementType.CREATE_RESIDENT,
                null,
                profileId,
                villageId,
                4,
                System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
    }

    private ItemStack residentItem(NPC npc, FarmerDefinition definition) {
        ResidentProfile profile = definition.profile();
        List<String> lore = new ArrayList<>();
        lore.add(profile.title() + " / " + roleName(definition.activeRole()));
        lore.add("Giới tính: " + genderName(profile.gender()));
        lore.add("ID Citizens: " + npc.getId());
        lore.add("Hồ sơ nhân vật: " + state(definition.enabled(BehaviorFlag.CHARACTER_PROFILE)));
        if (definition.enabled(BehaviorFlag.CHARACTER_PROFILE)) {
            lore.addAll(ResidentPresentation.characterLines(profile));
        }
        lore.add("Trí tuệ NPC: " + state(definition.enabled(BehaviorFlag.MASTER)));
        lore.add("Làng: " + (definition.villageId() == null ? "chưa gán" : definition.villageId()));
        lore.add("Theo dõi: " + plugin.professionMonitor().diagnostic(definition.npcUuid()).message());
        if (definition.activeRole().usesFarmerSetup()) {
            lore.add("Thu hoạch: " + state(definition.enabled(BehaviorFlag.HARVEST)));
            lore.add("Trồng cây: " + state(definition.enabled(BehaviorFlag.PLANT)));
            lore.add("Tự bán hàng: " + state(definition.enabled(BehaviorFlag.SELL_INVENTORY)));
            NpcAccount account = plugin.economy().account(npc.getUniqueId());
            lore.add("Sản lượng ca: " + account.producedThisShift() + "/" + plugin.config().maxOutputPerShift());
            lore.add("Sẵn sàng: " + plugin.manager().readiness(definition.npcUuid()));
            lore.add("Ruộng: " + (definition.plot() == null ? "chưa gán" : definition.plot().world()));
        } else if (definition.activeRole() == ResidentRole.RESIDENT) {
            lore.add("Hoạt động: sinh hoạt quanh nhà");
        } else if (definition.activeRole() == ResidentRole.RANCHER) {
            lore.add("Hoạt động: " + plugin.ranchers().status(definition.npcUuid(), plugin.config()));
        } else if (definition.activeRole() == ResidentRole.FISHER) {
            lore.add("Hoạt động: câu cá tại Điểm câu của làng");
        } else if (CivilProfessionRuntime.zoneFor(definition.activeRole()) != null) {
            lore.add("Sẵn sàng: " + plugin.manager().activeRoleReadiness(definition.npcUuid()));
        } else if (definition.activeRole() == ResidentRole.MERCHANT) {
            lore.add("Sẵn sàng: " + plugin.manager().activeRoleReadiness(definition.npcUuid()));
        } else {
            lore.add("Hoạt động: runtime nghề chưa triển khai");
        }
        lore.add("Nhấn để xem và quản lý");
        return item(Material.PLAYER_HEAD, profile.name(), NamedTextColor.GOLD, lore);
    }

    private ItemStack behaviorItem(FarmerDefinition definition, BehaviorFlag behavior) {
        boolean enabled = definition.enabled(behavior);
        return item(
                behavior.icon(),
                behavior.displayName() + ": " + state(enabled),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of(behavior.description(), "Nhấn để bật/tắt", "Thay đổi được lưu ngay"));
    }

    private ItemStack roleItem(FarmerDefinition definition, ResidentRole role) {
        RoleProgress progress = definition.progress(role);
        ResidentSchedule schedule = definition.schedule(role, defaultSchedule());
        boolean active = role == definition.activeRole();
        return item(
                roleMaterial(role),
                roleName(role) + (active ? " [ĐANG CHẠY]" : ""),
                active ? NamedTextColor.GREEN : NamedTextColor.GOLD,
                List.of(
                        "Level: " + progress.level(),
                        "XP: " + progress.experience() + "/" + progress.experienceForNextLevel(),
                        "Ca làm: " + clockTime(schedule.startTick()) + " - " + clockTime(schedule.endTick()),
                        role.implemented() ? "Hoạt động: sẵn sàng" : "Hoạt động: chưa triển khai, không tác động thế giới",
                        "Nhấn để chỉnh lịch nghề này"));
    }

    private ItemStack jobItem(FarmerDefinition definition, ResidentRole role) {
        boolean selected = role == definition.activeRole();
        boolean implemented = role.implemented();
        List<String> lore = new ArrayList<>();
        lore.add(selected ? "Trạng thái: ĐANG CHỌN" : implemented ? "Trạng thái: sẵn sàng" : "Trạng thái: CHƯA TRIỂN KHAI");
        lore.add(switch (role) {
            case RESIDENT -> "Đi dạo, quan sát và sinh hoạt quanh nhà";
            case FARMER -> "Thu hoạch, gieo trồng và giao kho";
            case FISHER -> "Sẽ câu cá tại điểm câu được giao";
            case RANCHER -> "Cho ăn, sinh sản và xử lý vật nuôi dư đàn";
            case COOK -> "Lấy thực phẩm từ kho và nấu tại Khu nấu ăn";
            case CRAFTER -> "Dùng nguyên liệu kho để luyện và chế tạo";
            case MINER -> "Khai thác block thật trong Khu đào và phục hồi sau cooldown";
            case SECURITY -> "Tuần tra, phát hiện quái và báo động";
            case MERCHANT -> "Mở quầy tại cửa hàng và phục vụ khách vãng lai";
            default -> "Runtime chưa triển khai";
        });
        if (role == ResidentRole.FARMER) {
            lore.add(plugin.manager().farmerSetup(definition.npcUuid()));
        } else if (role == ResidentRole.RANCHER) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            lore.add(village != null && village.workZone(VillageWorkZoneType.RANCH) != null
                    ? "Khu chăn nuôi: đã sẵn sàng" : "Khu chăn nuôi: chưa đặt");
        } else if (role == ResidentRole.FISHER) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            lore.add(village != null && village.workZone(VillageWorkZoneType.FISHING) != null
                    ? "Điểm câu: đã sẵn sàng" : "Điểm câu: chưa đặt");
        } else if (CivilProfessionRuntime.zoneFor(role) != null) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            VillageWorkZoneType zone = CivilProfessionRuntime.zoneFor(role);
            lore.add(village != null && village.workZone(zone) != null
                    ? workZoneName(zone) + ": đã sẵn sàng" : workZoneName(zone) + ": chưa đặt");
        } else if (role == ResidentRole.MERCHANT) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            MerchantStall stall = village == null ? null : village.merchantStall(definition.npcUuid());
            lore.add(stall != null && stall.complete() ? "Quầy cửa hàng: đã sẵn sàng" : "Quầy cửa hàng: cần đặt 2 điểm");
        }
        lore.add(implemented ? "Chuột trái: chọn nghề" : "Nghề này đang bị khóa");
        return item(
                implemented ? roleMaterial(role) : Material.BARRIER,
                roleName(role) + (selected ? " [HIỆN TẠI]" : ""),
                selected ? NamedTextColor.GREEN : implemented ? NamedTextColor.GOLD : NamedTextColor.RED,
                lore);
    }

    private ItemStack workZoneItem(VillageDefinition village, VillageWorkZoneType type) {
        StoredLocation center = village.workZone(type);
        Location resolved = center == null ? null : center.resolve();
        boolean chunksLoaded = resolved != null && validationAreaChunksLoaded(
                resolved, plugin.config().workZoneValidationRadius());
        WorkZoneValidation validation = !chunksLoaded ? null : WorkZoneValidator.validate(
                resolved, type, plugin.config().workZoneValidationRadius(),
                plugin.config().workZoneValidationVerticalRange());
        String status = center == null ? "CHƯA ĐẶT"
                : !chunksLoaded ? "VÙNG KIỂM TRA CHƯA LOAD"
                : validation.valid() ? "SẴN SÀNG" : "THIẾU TRẠM";
        List<String> lore = new ArrayList<>();
        lore.add(type == VillageWorkZoneType.RANCH
                ? "Số chuồng: " + village.ranchPens().size() + "/9"
                : "Trạng thái: " + status);
        lore.add("Yêu cầu: " + type.required().stream().map(this::stationName).sorted().toList());
        lore.add("Kiểm tra trong bán kính " + plugin.config().workZoneValidationRadius()
                + " block, cao +/-" + plugin.config().workZoneValidationVerticalRange());
        if (validation != null && !validation.valid()) {
            lore.add("Đang thiếu: " + validation.missing().stream().map(this::stationName).sorted().toList());
        }
        if (type == VillageWorkZoneType.MINING) {
            lore.add("Khu đào 2x2: " + village.miningZones().size() + "/16");
            lore.add("Click trái: đặt Trạm mỏ; click phải: quản lý Khu đào");
            if (!ReleasePolicy.roleEnabled(ResidentRole.MINER)) {
                lore.add("Runtime Miner đang khóa; mục này chỉ dùng để setup và kiểm tra hạ tầng");
            }
        }
        lore.add(type == VillageWorkZoneType.RANCH
                ? "Dùng chung cho mọi NPC Chăn nuôi trong làng; mỗi lúc chỉ một NPC thao tác"
                : "Dùng chung cho mọi NPC cùng nghề trong làng");
        if (center != null) {
            lore.add("XYZ: " + (int) center.x() + ", " + (int) center.y() + ", " + (int) center.z());
        }
        lore.add(type == VillageWorkZoneType.RANCH ? "Nhấn để quản lý từng chuồng"
                : type == VillageWorkZoneType.MINING ? "Trạm mỏ và Khu đào được cấu hình riêng"
                : "Nhấn rồi chọn block ở giữa khu");
        return item(
                switch (type) {
                    case WOOD -> Material.STONECUTTER;
                    case COOKING -> Material.FURNACE;
                    case CRAFTING -> Material.SMITHING_TABLE;
                    case MINING -> Material.BLAST_FURNACE;
                    case SECURITY -> Material.BELL;
                    case RANCH -> Material.HAY_BLOCK;
                    case FISHING -> Material.FISHING_ROD;
                },
                workZoneName(type),
                center == null || !chunksLoaded ? NamedTextColor.RED
                        : validation.valid() ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                lore);
    }

    private String workZoneName(VillageWorkZoneType type) {
        return switch (type) {
            case WOOD -> "Khu làm gỗ";
            case COOKING -> "Khu nấu ăn";
            case CRAFTING -> "Khu chế tạo đồ";
            case MINING -> "Khu mỏ";
            case SECURITY -> "Trạm bảo vệ";
            case RANCH -> "Khu chăn nuôi";
            case FISHING -> "Điểm câu";
        };
    }

    private String stationName(Material material) {
        return switch (material) {
            case STONECUTTER -> "Máy cưa (Stonecutter)";
            case CRAFTING_TABLE -> "Bàn chế tạo";
            case FURNACE -> "Lò nung";
            case SMITHING_TABLE -> "Bàn rèn";
            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> "Đe";
            case HAY_BLOCK -> "Kiện rơm";
            case OAK_FENCE -> "Hàng rào hoặc cổng hàng rào";
            case WATER -> "Nước";
            case BLAST_FURNACE -> "Lò luyện kim";
            case BELL -> "Chuông báo động";
            case TARGET -> "Bia mục tiêu";
            default -> material.name();
        };
    }

    private ItemStack scheduleControl(String label, long tick, boolean start) {
        return item(
                start ? Material.LIME_DYE : Material.RED_DYE,
                label,
                NamedTextColor.YELLOW,
                List.of(
                        "Minecraft tick: " + tick,
                        "Chuột trái: muộn hơn 1 giờ",
                        "Chuột phải: sớm hơn 1 giờ",
                        "Giữ Shift: thay đổi 2 giờ"));
    }

    private void adjustSchedule(
            Player player, UUID uuid, ResidentRole role, boolean start, InventoryClickEvent event) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        if (definition == null || role == null) {
            openRoles(player, uuid);
            return;
        }
        ResidentSchedule current = definition.schedule(role, defaultSchedule());
        long step = event.isShiftClick() ? 2000L : 1000L;
        long delta = event.isRightClick() ? -step : step;
        ResidentSchedule updated = start
                ? new ResidentSchedule(current.startTick() + delta, current.endTick())
                : new ResidentSchedule(current.startTick(), current.endTick() + delta);
        if (plugin.manager().setSchedule(uuid, role, updated)) {
            player.sendMessage(Component.text(
                    "[ĐÃ LƯU] Ca " + roleName(role) + ": "
                            + clockTime(updated.startTick()) + " - " + clockTime(updated.endTick()),
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Không thể lưu lịch nghề.", NamedTextColor.RED));
        }
        openRoleSchedule(player, uuid, role);
    }

    private void resetSchedule(Player player, UUID uuid, ResidentRole role) {
        if (role != null && plugin.manager().setSchedule(uuid, role, null)) {
            player.sendMessage(Component.text("[ĐÃ LƯU] Nghề dùng lại lịch mặc định.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Không thể đặt lại lịch nghề.", NamedTextColor.RED));
        }
        openRoleSchedule(player, uuid, role);
    }

    private ResidentSchedule defaultSchedule() {
        return new ResidentSchedule(plugin.config().workStartTick(), plugin.config().workEndTick());
    }

    private String clockTime(long tick) {
        long minutes = Math.floorMod(tick + 6000L, 24000L) * 60L / 1000L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60L, minutes % 60L);
    }

    private String currentActivity(FarmerDefinition definition) {
        FarmerPhase shared = plugin.manager().phase(plugin.manager().npc(definition.npcUuid()).getId());
        if (shared == FarmerPhase.GOING_TO_BED) return "Đang đi tới giường";
        if (shared == FarmerPhase.SLEEPING) return "Đang ngủ";
        if (!plugin.manager().roleActive(definition.npcUuid())) return "Nghề đang tắt";
        if (definition.activeRole() == ResidentRole.RANCHER) {
            return plugin.ranchers().status(definition.npcUuid(), plugin.config());
        }
        FarmerPhase phase = FarmerActivityResolver.resolvePhase(
                definition.activeRole(),
                shared,
                plugin.fishers().phase(definition.npcUuid()),
                plugin.merchants().phase(definition.npcUuid()),
                plugin.civilProfessions().phase(definition.npcUuid()));
        return FarmerActivityResolver.describeActivity(phase);
    }

    private String roleName(ResidentRole role) {
        return switch (role) {
            case RESIDENT -> "Người dân";
            case VISITOR -> "Khách vãng lai";
            case FARMER -> "Nông dân";
            case FISHER -> "Ngư dân";
            case COOK -> "Đầu bếp";
            case CRAFTER -> "Thợ chế tạo";
            case MINER -> "Thợ mỏ";
            case RANCHER -> "Chăn nuôi";
            case SECURITY -> "Bảo vệ";
            case MERCHANT -> "Dân buôn";
            case MELEE_TRAINING -> "Tập kiếm";
            case ARCHERY_TRAINING -> "Tập cung";
            case SPARRING -> "Đấu tập";
        };
    }

    private Material roleMaterial(ResidentRole role) {
        return switch (role) {
            case RESIDENT -> Material.LEATHER_BOOTS;
            case VISITOR -> Material.EMERALD;
            case FARMER -> Material.IRON_HOE;
            case FISHER -> Material.FISHING_ROD;
            case COOK -> Material.COOKED_BEEF;
            case CRAFTER -> Material.CRAFTING_TABLE;
            case MINER -> Material.IRON_PICKAXE;
            case RANCHER -> Material.WHEAT;
            case SECURITY -> Material.SHIELD;
            case MERCHANT -> Material.EMERALD;
            case MELEE_TRAINING -> Material.IRON_SWORD;
            case ARCHERY_TRAINING -> Material.BOW;
            case SPARRING -> Material.WOODEN_SWORD;
        };
    }

    private void beginPlacement(Player player, PlacementSession session) {
        placements.put(player.getUniqueId(), session);
        player.closeInventory();
        player.sendMessage(Component.text("[ĐANG CHỜ] Hãy nhấp phải một block trong vòng 120 giây.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Muốn hủy, gõ /lnpc cancel trong chat.", NamedTextColor.RED));
        player.sendMessage(Component.text("GUI sẽ tự mở lại sau khi lưu vị trí.", NamedTextColor.GRAY));
    }

    boolean cancelPlacement(Player player) {
        boolean cancelled = placements.remove(player.getUniqueId()) != null;
        player.sendMessage(Component.text(
                cancelled ? "[ĐÃ XONG] Đã hủy thao tác chọn vị trí." : "Không có thao tác chọn vị trí.",
                cancelled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        return cancelled;
    }

    private void finishCreate(Player player, PlacementSession session, Location location) {
        if (!(location.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Bed)) {
            player.sendMessage(Component.text(
                    "Phải nhấp phải đúng block giường để tạo và spawn NPC.", NamedTextColor.RED));
            openProfiles(player, session.villageId());
            return;
        }
        ResidentProfile profile = plugin.profiles().get(session.profileId());
        if (profile == null || plugin.manager().usedProfileIds().contains(profile.id().toLowerCase(java.util.Locale.ROOT))) {
            player.sendMessage(Component.text("Hồ sơ không tồn tại hoặc đã được sử dụng.", NamedTextColor.RED));
            openProfiles(player, session.villageId());
            return;
        }
        NPC npc = plugin.manager().create(profile, location, session.villageId());
        if (npc == null) {
            player.sendMessage(Component.text(
                    "Không thể tạo NPC: cạnh giường cần một ô đứng an toàn, phía trên thoáng.", NamedTextColor.RED));
            openProfiles(player, session.villageId());
            return;
        }
        player.sendMessage(Component.text(
                "[ĐÃ XONG] Đã tạo NPC cạnh giường và liên kết giường ngủ.", NamedTextColor.GREEN));
        openDetail(player, npc.getUniqueId());
    }

    private void finishHome(Player player, PlacementSession session, Location location) {
        if (!(location.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Bed)) {
            player.sendMessage(Component.text("Phải nhấp phải đúng block giường.", NamedTextColor.RED));
            openDetail(player, session.residentUuid());
            return;
        }
        if (plugin.manager().setHome(session.residentUuid(), location)) {
            player.sendMessage(Component.text("[ĐÃ XONG] Đã lưu giường và điểm spawn của NPC.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    "Không thể lưu: cạnh giường cần một ô đứng an toàn, phía trên thoáng.", NamedTextColor.RED));
        }
        openDetail(player, session.residentUuid());
    }

    private void finishPlot(Player player, PlacementSession session, Location location) {
        if (plugin.manager().setPlot(session.residentUuid(), location, session.plotRadius())) {
            player.sendMessage(Component.text("[ĐÃ XONG] Đã lưu tâm và bán kính ruộng.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Cư dân không còn tồn tại.", NamedTextColor.RED));
        }
        openDetail(player, session.residentUuid());
    }

    private void finishTownStorage(Player player, PlacementSession session, Location location) {
        if (plugin.villages().setDeliveryChest(session.villageId(), location)) {
            player.sendMessage(Component.text("[ĐÃ XONG] Đã lưu kho làng.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    "Block phải là rương hoặc thùng và cùng world với làng.", NamedTextColor.RED));
        }
        openVillage(player, session.villageId());
    }

    private void finishWorkZone(Player player, PlacementSession session, Location location) {
        VillageDefinition village = plugin.villages().get(session.villageId());
        if (village == null || session.workZoneType() == null
                || !village.center().world().equals(location.getWorld().getName())) {
            player.sendMessage(Component.text("Khu nghề phải cùng world với làng.", NamedTextColor.RED));
            openWorkZones(player, session.villageId());
            return;
        }
        WorkZoneValidation validation = WorkZoneValidator.validate(
                location, session.workZoneType(),
                plugin.config().workZoneValidationRadius(),
                plugin.config().workZoneValidationVerticalRange());
        VillageDefinition overlap = plugin.villages().overlappingWorkZone(
                session.villageId(), session.workZoneType(), location,
                plugin.config().workZoneValidationRadius());
        if (session.workZoneType() == VillageWorkZoneType.FISHING && !isSafeStandingLocation(location)) {
            player.sendMessage(Component.text(
                    "Điểm câu phải là ô đứng an toàn trên bờ, phía trên thoáng và có nền chắc.",
                    NamedTextColor.RED));
            openWorkZones(player, session.villageId());
            return;
        }
        if (overlap != null) {
            player.sendMessage(Component.text(
                    "Không thể đặt: " + workZoneName(session.workZoneType())
                            + " chồng vùng với làng " + overlap.name() + ".",
                    NamedTextColor.RED));
        } else if (!validation.valid()) {
            player.sendMessage(Component.text(
                    "Chưa thể đặt " + workZoneName(session.workZoneType()) + ". Thiếu: "
                            + validation.missing().stream().map(this::stationName).sorted().toList(),
                    NamedTextColor.RED));
        } else if (plugin.villages().setWorkZone(session.villageId(), session.workZoneType(), location)) {
            player.sendMessage(Component.text(
                    "[ĐÃ XONG] Đã lưu " + workZoneName(session.workZoneType()) + ".", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Không thể lưu khu nghề.", NamedTextColor.RED));
        }
        openWorkZones(player, session.villageId());
    }

    private void finishRanchPen(Player player, PlacementSession session, Location location) {
        WorkZoneValidation validation = WorkZoneValidator.validate(
                location, VillageWorkZoneType.RANCH,
                plugin.config().workZoneValidationRadius(), plugin.config().workZoneValidationVerticalRange());
        VillageDefinition village = plugin.villages().get(session.villageId());
        VillageDefinition overlappingVillage = village == null ? null : plugin.villages().overlappingRanchPen(
                session.villageId(), location, plugin.config().workZoneValidationRadius());
        if (village == null || !village.center().world().equals(location.getWorld().getName())) {
            player.sendMessage(Component.text("Chuồng phải cùng world với làng.", NamedTextColor.RED));
        } else if (!validation.valid()) {
            player.sendMessage(Component.text("Chuồng thiếu: " + validation.missing().stream()
                    .map(this::stationName).sorted().toList(), NamedTextColor.RED));
        } else if (plugin.villages().ranchPenOverlaps(
                village, location, plugin.config().workZoneValidationRadius())) {
            player.sendMessage(Component.text("Chuồng mới đang chồng vùng quét với chuồng đã có.", NamedTextColor.RED));
        } else if (overlappingVillage != null) {
            player.sendMessage(Component.text(
                    "Chuồng mới đang chồng vùng quét với làng " + overlappingVillage.name() + ".",
                    NamedTextColor.RED));
        } else if (plugin.villages().addRanchPen(session.villageId(), location)) {
            player.sendMessage(Component.text("[ĐÃ XONG] Đã thêm chuồng vào danh sách.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Không thể lưu chuồng; kiểm tra giới hạn 9 chuồng.", NamedTextColor.RED));
        }
        openRanchPens(player, session.villageId());
    }

    private void finishMiningZone(Player player, PlacementSession session, Location location) {
        if (plugin.villages().addMiningZone(session.villageId(), location)) {
            player.sendMessage(Component.text("[ĐÃ XONG] Đã thêm Khu đào 2x2.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    "Không thể lưu: khu phải cùng world, không chồng khu khác và chưa vượt giới hạn 16.",
                    NamedTextColor.RED));
        }
        openMiningZones(player, session.villageId());
    }

    private Map<String, Integer> ranchSpecies(Location center, int radius, int vertical) {
        Map<String, Integer> species = new java.util.LinkedHashMap<>();
        center.getWorld().getNearbyEntitiesByType(Animals.class, center, radius, vertical, radius).stream()
                .map(this::ranchSpeciesName).filter(java.util.Objects::nonNull)
                .forEach(name -> species.merge(name, 1, Integer::sum));
        return species;
    }

    private int mineableBlockCount(MiningZone zone, Location corner) {
        int count = 0;
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            for (int y = zone.minY(); y <= zone.maxY(); y++) {
                Material material = corner.getWorld().getBlockAt(
                        corner.getBlockX() + x, y, corner.getBlockZ() + z).getType();
                if (CivilProfessionRuntime.miningOutput(material).isPresent()) count++;
            }
        }
        return count;
    }

    private String ranchSpeciesName(Animals animal) {
        if (animal instanceof Cow) return "Bò";
        if (animal instanceof Sheep) return "Cừu";
        if (animal instanceof Chicken) return "Gà";
        if (animal instanceof Pig) return "Lợn";
        if (animal instanceof Rabbit) return "Thỏ";
        return null;
    }

    private Material ranchMaterial(String species) {
        return switch (species) {
            case "Bò" -> Material.COW_SPAWN_EGG;
            case "Cừu" -> Material.SHEEP_SPAWN_EGG;
            case "Gà" -> Material.CHICKEN_SPAWN_EGG;
            case "Lợn" -> Material.PIG_SPAWN_EGG;
            case "Thỏ" -> Material.RABBIT_SPAWN_EGG;
            default -> Material.HAY_BLOCK;
        };
    }

    private void finishMarketPoint(Player player, PlacementSession session, Location location) {
        if (plugin.villages().setSocialPoint(session.villageId(), "cho", location)) {
            player.sendMessage(Component.text("[ĐÃ XONG] Đã lưu điểm chợ.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Điểm chợ phải cùng world với làng.", NamedTextColor.RED));
        }
        openWorkZones(player, session.villageId());
    }

    private void finishScenicPoint(Player player, PlacementSession session, Location location) {
        if (plugin.villages().setSocialPoint(session.villageId(), "ngamcanh", location)) {
            player.sendMessage(Component.text("[ĐÃ XONG] Đã lưu điểm ngắm cảnh.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Điểm ngắm cảnh phải cùng world với làng.", NamedTextColor.RED));
        }
        openWorkZones(player, session.villageId());
    }

    private void finishVisitorGate(Player player, PlacementSession session, Location location) {
        if (isSafeStandingLocation(location)
                && plugin.villages().setVisitorGate(session.villageId(), location)) {
            player.sendMessage(Component.text(
                    "[ĐÃ XONG] Khách sẽ xuất hiện và rời làng tại cổng này.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    "Cổng phải cùng world với làng và có đủ chỗ đứng 2 block.", NamedTextColor.RED));
        }
        openWorkZones(player, session.villageId());
    }

    private void finishNavigationGate(Player player, PlacementSession session, Block block) {
        if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Gate)) {
            player.sendMessage(Component.text("Phải click phải trực tiếp vào fence gate.", NamedTextColor.RED));
        } else if (plugin.villages().addNavigationGate(session.villageId(), block.getLocation())) {
            player.sendMessage(Component.text(
                    "[ĐÃ XONG] NPC có thể dùng cổng này khi nó nằm trên tuyến đường.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    "Không thể lưu: cổng phải cùng world, chưa trùng và làng chưa đủ 32 cổng.", NamedTextColor.RED));
        }
        openNavigationGates(player, session.villageId());
    }

    private void finishMerchantPoint(Player player, PlacementSession session, Location location) {
        FarmerDefinition definition = plugin.manager().get(session.residentUuid());
        if (definition == null || definition.villageId() == null || !isSafeStandingLocation(location)) {
            player.sendMessage(Component.text(
                    "Điểm quầy phải là ô đứng an toàn, có nền chắc và đủ 2 block cao.", NamedTextColor.RED));
        } else {
            boolean seller = session.type() == PlacementType.SET_MERCHANT_SELLER;
            if (plugin.villages().setMerchantPoint(
                    definition.villageId(), definition.npcUuid(), seller, location)) {
                player.sendMessage(Component.text(
                        seller ? "[ĐÃ XONG] Đã lưu điểm đứng Dân buôn và hướng nhìn."
                                : "[ĐÃ XONG] Đã lưu điểm người mua và hướng nhìn.",
                        NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(
                        "Không thể lưu: điểm phải cùng world và không được trùng điểm của quầy khác.",
                        NamedTextColor.RED));
            }
        }
        openDetail(player, session.residentUuid());
    }

    private void finishSeat(Player player, PlacementSession session, Block block) {
        VillageDefinition village = plugin.villages().get(session.villageId());
        if (village == null || !village.center().world().equals(block.getWorld().getName())) {
            player.sendMessage(Component.text("Ghế phải cùng world với làng.", NamedTextColor.RED));
            openSeats(player, session.villageId());
            return;
        }
        SeatValidation validation = SeatValidator.validate(block);
        if (!validation.valid()) {
            player.sendMessage(Component.text(validation.reason(), NamedTextColor.RED));
        } else if (plugin.villages().addSeat(session.villageId(), validation.seat())) {
            String type = validation.seat().type() == SeatType.DINING ? "ghế bàn ăn" : "ghế nghỉ";
            player.sendMessage(Component.text(
                    "[ĐÃ XONG] Đã lưu " + type + "; NPC sẽ nhìn theo hướng "
                            + cardinalDirection(validation.seat().location().yaw()) + ".",
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Không thể lưu ghế.", NamedTextColor.RED));
        }
        openSeats(player, session.villageId());
    }

    private void toggleVisitors(Player player, String villageId) {
        VillageDefinition village = plugin.villages().get(villageId);
        boolean enabled = plugin.config().visitors().enabled();
        if (!enabled && !visitorInfrastructureReady(village)) {
            player.sendMessage(Component.text(
                    "Hãy đặt Cổng khách và ít nhất một quầy Dân buôn đủ hai điểm trước khi bật khách.",
                    NamedTextColor.RED));
        } else if (plugin.setVisitorsEnabled(!enabled)) {
            player.sendMessage(Component.text(
                    !enabled ? "[ĐÃ BẬT] Khách vãng lai có thể đến các làng đủ điều kiện."
                            : "[ĐÃ TẮT] Không tạo thêm khách vãng lai.",
                    !enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Không thể lưu trạng thái khách vãng lai.", NamedTextColor.RED));
        }
        openWorkZones(player, villageId);
    }

    private boolean visitorInfrastructureReady(VillageDefinition village) {
        return village != null && village.visitorGate() != null
                && village.merchantStalls().stream().anyMatch(stall -> stall.complete()
                        && village.visitorGate().world().equals(stall.buyerPoint().world()));
    }

    private boolean isSafeStandingLocation(Location location) {
        Block feet = location.getBlock();
        return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
                && !feet.getRelative(0, -1, 0).isPassable();
    }

    private String cardinalDirection(float yaw) {
        if (yaw == 0.0f) return "Nam";
        if (yaw == 90.0f) return "Tây";
        if (yaw == 180.0f) return "Bắc";
        return "Đông";
    }

    private Location safeLocationNear(Location center) {
        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Location candidate = center.getBlock().getRelative(offset[0], 0, offset[1])
                    .getLocation().add(0.5, 0, 0.5);
            if (isSafeStandingLocation(candidate)) return candidate;
        }
        return null;
    }

    private Location safeMiningTeleportLocation(Location center) {
        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int x = center.getBlockX() + offset[0];
            int z = center.getBlockZ() + offset[1];
            if (!center.getWorld().isChunkLoaded(x >> 4, z >> 4)) continue;
            Location candidate = center.getWorld().getBlockAt(x, center.getBlockY(), z)
                    .getLocation().add(0.5, 0, 0.5);
            if (isSafeStandingLocation(candidate)) return candidate;
        }
        return null;
    }

    private boolean validationAreaChunksLoaded(Location center, int radius) {
        int minChunkX = (center.getBlockX() - radius) >> 4;
        int maxChunkX = (center.getBlockX() + radius) >> 4;
        int minChunkZ = (center.getBlockZ() - radius) >> 4;
        int maxChunkZ = (center.getBlockZ() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!center.getWorld().isChunkLoaded(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private void toggleNpcActive(Player player, UUID uuid) {
        boolean enable = !plugin.manager().npcActive(uuid);
        if (plugin.manager().setNpcActive(uuid, enable)) {
            player.sendMessage(Component.text(
                    enable ? "[ĐÃ BẬT] NPC tiếp tục nghề đã chọn."
                            : "[ĐÃ TẮT] NPC đã dừng mọi hành vi.",
                    enable ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Không thể đổi trạng thái NPC.", NamedTextColor.RED));
        }
        openDetail(player, uuid);
    }

    private void toggleRoleActive(Player player, UUID uuid) {
        boolean enable = !plugin.manager().roleActive(uuid);
        if (plugin.manager().setRoleActive(uuid, enable)) {
            player.sendMessage(Component.text(
                    enable ? "[ĐÃ BẬT] NPC tiếp tục nghề theo lịch đã đặt."
                            : "[ĐÃ TẮT] NPC đã dừng nghề hiện tại.",
                    enable ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } else {
            FarmerDefinition definition = plugin.manager().get(uuid);
            String reason = definition != null && definition.activeRole() == ResidentRole.FARMER
                    ? plugin.manager().farmerSetup(uuid) : "Không thể lưu trạng thái nghề";
            player.sendMessage(Component.text("Không thể bật nghề: " + reason + ".", NamedTextColor.RED));
        }
        FarmerDefinition definition = plugin.manager().get(uuid);
        if (definition != null) openRoleSchedule(player, uuid, definition.activeRole());
    }

    private void toggleFarmerActive(Player player, UUID uuid) {
        boolean enable = !plugin.manager().farmerEnabled(uuid);
        if (plugin.manager().setFarmerEnabled(uuid, enable)) {
            player.sendMessage(Component.text(
                    enable ? "[ĐÃ BẬT] NPC bắt đầu Thu hoạch và Trồng cây khi đúng ca."
                            : "[ĐÃ TẮT] NPC đã dừng Thu hoạch và Trồng cây.",
                    enable ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text(
                    "Không thể bật làm nông: " + plugin.manager().farmerSetup(uuid) + ".",
                    NamedTextColor.RED));
        }
        openDetail(player, uuid);
    }

    private void selectJob(Player player, UUID uuid, ResidentRole role) {
        if (plugin.manager().selectJob(uuid, role)) {
            player.sendMessage(Component.text(
                    "[ĐÃ CHỌN] Nghề hiện tại: " + roleName(role) + ".", NamedTextColor.GREEN));
        } else {
            FarmerDefinition definition = plugin.manager().get(uuid);
            VillageDefinition village = definition == null ? null : plugin.villages().get(definition.villageId());
            String reason = role == ResidentRole.FARMER
                    ? plugin.manager().farmerSetup(uuid)
                    : role == ResidentRole.RANCHER && (village == null || village.workZone(VillageWorkZoneType.RANCH) == null)
                            ? "Chưa đặt Khu chăn nuôi"
                            : role == ResidentRole.FISHER && (village == null || village.workZone(VillageWorkZoneType.FISHING) == null)
                                    ? "Chưa đặt Điểm câu"
                                    : CivilProfessionRuntime.zoneFor(role) != null
                                            && (village == null || village.workZone(CivilProfessionRuntime.zoneFor(role)) == null)
                                                    ? "Chưa đặt " + workZoneName(CivilProfessionRuntime.zoneFor(role))
                                                    : "Không thể lưu thay đổi";
            player.sendMessage(Component.text("Chưa thể chọn nghề: " + reason + ".", NamedTextColor.RED));
        }
        openDetail(player, uuid);
    }

    private boolean isFarmer(UUID uuid) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        return definition != null && definition.activeRole().usesFarmerSetup();
    }

    private boolean isRancher(UUID uuid) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        return definition != null && definition.activeRole() == ResidentRole.RANCHER;
    }

    private boolean isFisher(UUID uuid) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        return definition != null && definition.activeRole() == ResidentRole.FISHER;
    }

    private boolean isCivilProfession(UUID uuid) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        return definition != null && CivilProfessionRuntime.zoneFor(definition.activeRole()) != null;
    }

    private boolean isMerchant(UUID uuid) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        return definition != null && definition.activeRole() == ResidentRole.MERCHANT;
    }

    private void adjustRanchLimit(Player player, UUID uuid, InventoryClickEvent event) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        VillageDefinition village = definition == null ? null : plugin.villages().get(definition.villageId());
        if (village == null) {
            openDetail(player, uuid);
            return;
        }
        int step = event.isShiftClick() ? 4 : 1;
        int limit = Math.clamp(village.ranchAnimalLimit() + (event.isRightClick() ? -step : step), 2, 64);
        if (plugin.villages().setRanchAnimalLimit(village.id(), limit)) {
            player.sendMessage(Component.text("[ĐÃ LƯU] Giới hạn đàn: " + limit + " mỗi loài.", NamedTextColor.GREEN));
        }
        openDetail(player, uuid);
    }

    private Location adjacentLocation(Player player, Block clicked, PlayerInteractEvent event) {
        Location location = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0.0f);
        return location;
    }

    private void reopenAfterPlacement(Player player, PlacementSession session) {
        if (session.residentUuid() == null) {
            if (session.type() == PlacementType.SET_TOWN_STORAGE) {
                openVillage(player, session.villageId());
            } else if (session.type() == PlacementType.SET_WORK_ZONE
                    || session.type() == PlacementType.SET_MARKET_POINT
                    || session.type() == PlacementType.SET_SCENIC_POINT
                    || session.type() == PlacementType.SET_VISITOR_GATE) {
                openWorkZones(player, session.villageId());
            } else if (session.type() == PlacementType.SET_NAVIGATION_GATE) {
                openNavigationGates(player, session.villageId());
            } else if (session.type() == PlacementType.SET_MINING_ZONE) {
                openMiningZones(player, session.villageId());
            } else if (session.type() == PlacementType.SET_SEAT) {
                openSeats(player, session.villageId());
            } else {
                openProfiles(player, session.villageId());
            }
        } else {
            openDetail(player, session.residentUuid());
        }
    }

    private void adjustRange(Player player, UUID residentUuid, InventoryClickEvent event) {
        FarmerDefinition definition = plugin.manager().get(residentUuid);
        if (definition == null || definition.plot() == null) {
            player.sendMessage(Component.text("Hãy đặt tâm ruộng trước.", NamedTextColor.RED));
            openDetail(player, residentUuid);
            return;
        }
        int step = event.isShiftClick() ? 2 : 1;
        int delta = event.isRightClick() ? -step : step;
        int radius = Math.clamp(definition.plotRadius() + delta, 1, plugin.config().maxPlotRadius());
        Location plot = definition.plot().resolve();
        if (plot != null && plugin.manager().setPlot(residentUuid, plot, radius)) {
            player.sendMessage(Component.text("[ĐÃ XONG] Bán kính ruộng đã đặt thành " + radius + ".", NamedTextColor.GREEN));
        }
        openDetail(player, residentUuid);
    }

    private void reload(Player player, UUID returnToResident) {
        plugin.reloadPluginConfig();
        player.sendMessage(Component.text("[ĐÃ XONG] Đã tải lại cấu hình.", NamedTextColor.GREEN));
        if (returnToResident == null) {
            openList(player);
        } else {
            openDetail(player, returnToResident);
        }
    }

    private void openRemoveConfirm(Player player, UUID residentUuid) {
        NPC npc = plugin.manager().npc(residentUuid);
        if (npc == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.REMOVE_CONFIRM,
                residentUuid,
                27,
                Component.text("Xóa " + npc.getName() + "?"));
        menu.getInventory().setItem(11, item(
                Material.LIME_CONCRETE, "Xác nhận xóa vĩnh viễn", NamedTextColor.RED, List.of("Xóa NPC và toàn bộ dữ liệu được giao")));
        menu.getInventory().setItem(15, item(
                Material.RED_CONCRETE, "Hủy", NamedTextColor.GREEN, List.of("Quay lại mà không thay đổi")));
        openMenu(player, menu);
    }

    private void openMiningZoneRemoveConfirm(Player player, String villageId, MiningZone zone) {
        if (zone == null) {
            openMiningZones(player, villageId);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.MINING_ZONE_REMOVE_CONFIRM, null, villageId, 27,
                Component.text("Xóa " + zone.id() + "?"));
        menu.miningZonesBySlot().put(11, zone.id());
        menu.getInventory().setItem(11, item(
                Material.LIME_CONCRETE, "Xác nhận xóa " + zone.id(), NamedTextColor.RED,
                List.of("World: " + zone.corner().world(),
                        "Góc: " + (int) zone.corner().x() + ", " + (int) zone.corner().z(),
                        "Thao tác này chỉ xóa cấu hình Khu đào")));
        menu.getInventory().setItem(15, item(
                Material.RED_CONCRETE, "Hủy", NamedTextColor.GREEN,
                List.of("Quay lại danh sách mà không thay đổi")));
        openMenu(player, menu);
    }

    private ItemStack locationItem(
            Material material, String label, StoredLocation location, boolean done, String instruction) {
        List<String> lore = new ArrayList<>();
        lore.add(done ? "Trạng thái: ĐÃ ĐẶT" : "Trạng thái: CHƯA ĐẶT");
        if (location != null) {
            lore.add("Thế giới: " + location.world());
            lore.add("XYZ: " + (int) Math.floor(location.x()) + ", "
                    + (int) Math.floor(location.y()) + ", " + (int) Math.floor(location.z()));
        }
        lore.add(instruction);
        return item(material, label + ": " + (done ? "[DONE]" : "[PENDING]"),
                done ? NamedTextColor.GREEN : NamedTextColor.RED, lore);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.customName(Component.text(name, color, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(styledLore(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private List<Component> styledLore(List<String> lines) {
        List<Component> result = new ArrayList<>();
        boolean actionSection = false;
        for (String line : lines) {
            boolean action = isActionLine(line);
            if (action && !actionSection && !result.isEmpty()) {
                result.add(Component.empty());
            }
            result.add(styledLoreLine(line, action));
            actionSection = actionSection || action;
        }
        return result;
    }

    private Component styledLoreLine(String line, boolean action) {
        if (line.isBlank()) return Component.empty();
        if (action) {
            NamedTextColor color = line.startsWith("/") ? NamedTextColor.AQUA : NamedTextColor.YELLOW;
            return Component.text("› ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(line, color))
                    .decoration(TextDecoration.ITALIC, false);
        }
        int separator = line.indexOf(':');
        if (separator > 0) {
            String label = line.substring(0, separator + 1);
            String value = line.substring(separator + 1).stripLeading();
            return Component.text(label + " ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(value, valueColor(value)))
                    .decoration(TextDecoration.ITALIC, false);
        }
        return Component.text(line, valueColor(line)).decoration(TextDecoration.ITALIC, false);
    }

    private boolean isActionLine(String line) {
        String normalized = line.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("nhấn")
                || normalized.startsWith("click")
                || normalized.startsWith("chuột")
                || normalized.startsWith("shift")
                || normalized.startsWith("giữ shift")
                || normalized.startsWith("dùng lệnh")
                || normalized.startsWith("/")
                || normalized.startsWith("về ")
                || normalized.startsWith("chọn ");
    }

    private NamedTextColor valueColor(String value) {
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("CHƯA ĐẶT") || normalized.contains("CHƯA LOAD")
                || normalized.contains("KHÔNG TỒN TẠI") || normalized.equals("TẮT")) {
            return NamedTextColor.RED;
        }
        if (normalized.contains("SẴN SÀNG") || normalized.contains("ĐÃ ĐẶT")
                || normalized.contains("ĐANG CHẠY") || normalized.contains("ĐANG CHỌN")
                || normalized.equals("BẬT")) {
            return NamedTextColor.GREEN;
        }
        if (normalized.contains("ĐANG CHỜ") || normalized.contains("ĐANG KHÓA")
                || normalized.contains("CHƯA TRIỂN KHAI") || normalized.contains("THIẾU")) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.WHITE;
    }

    private void openMenu(Player player, ResidentMenu menu) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.BLACK, List.of());
        for (int slot = 0; slot < menu.getInventory().getSize(); slot++) {
            if (menu.getInventory().getItem(slot) == null) {
                menu.getInventory().setItem(slot, filler);
            }
        }
        player.openInventory(menu.getInventory());
    }

    private String state(boolean enabled) {
        return enabled ? "BẬT" : "TẮT";
    }

    private String storageUsage(NpcAccount account) {
        return plugin.config().unlimitedStorage()
                ? account.inventorySize() + " / VÔ HẠN"
                : account.inventorySize() + "/" + plugin.config().inventoryCapacity();
    }

    private String genderName(String gender) {
        return switch (gender.toLowerCase(java.util.Locale.ROOT)) {
            case "male" -> "nam";
            case "female" -> "nữ";
            default -> "không xác định";
        };
    }

    private String money(long minor) {
        return String.format(java.util.Locale.ROOT, "%.2f %s", minor / 100.0, plugin.config().currencyName());
    }

    private Material materialForItem(String key) {
        return switch (key) {
            case "wheat" -> Material.WHEAT;
            case "carrot" -> Material.CARROT;
            case "potato" -> Material.POTATO;
            case "beetroot" -> Material.BEETROOT;
            case "wheat_seeds" -> Material.WHEAT_SEEDS;
            case "cod" -> Material.COD;
            case "salmon" -> Material.SALMON;
            case "tropical_fish" -> Material.TROPICAL_FISH;
            case "pufferfish" -> Material.PUFFERFISH;
            case "chicken" -> Material.CHICKEN;
            case "feather" -> Material.FEATHER;
            case "egg" -> Material.EGG;
            case "cooked_chicken" -> Material.COOKED_CHICKEN;
            case "cooked_cod" -> Material.COOKED_COD;
            case "cooked_salmon" -> Material.COOKED_SALMON;
            case "bread" -> Material.BREAD;
            case "iron_ingot" -> Material.IRON_INGOT;
            case "raw_iron" -> Material.RAW_IRON;
            case "shears" -> Material.SHEARS;
            case "coal" -> Material.COAL;
            case "cobblestone" -> Material.COBBLESTONE;
            default -> Material.PAPER;
        };
    }

    private String itemName(String key) {
        return switch (key) {
            case "wheat" -> "Lúa mì";
            case "carrot" -> "Cà rốt";
            case "potato" -> "Khoai tây";
            case "beetroot" -> "Củ dền";
            case "wheat_seeds" -> "Hạt lúa mì";
            case "cod" -> "Cá tuyết";
            case "salmon" -> "Cá hồi";
            case "tropical_fish" -> "Cá nhiệt đới";
            case "pufferfish" -> "Cá nóc";
            case "chicken" -> "Thịt gà sống";
            case "feather" -> "Lông gà";
            case "egg" -> "Trứng gà";
            case "cooked_chicken" -> "Thịt gà chín";
            case "cooked_cod" -> "Cá tuyết chín";
            case "cooked_salmon" -> "Cá hồi chín";
            case "bread" -> "Bánh mì";
            case "iron_ingot" -> "Phôi sắt";
            case "raw_iron" -> "Sắt thô";
            case "shears" -> "Kéo";
            case "coal" -> "Than";
            case "cobblestone" -> "Đá cuội";
            default -> key;
        };
    }

    private NamedTextColor roleColor(ResidentRole role) {
        return switch (role) {
            case FARMER -> NamedTextColor.GREEN;
            case RANCHER -> NamedTextColor.GOLD;
            case FISHER -> NamedTextColor.AQUA;
            case COOK -> NamedTextColor.RED;
            case CRAFTER -> NamedTextColor.YELLOW;
            case MINER -> NamedTextColor.DARK_GRAY;
            case SECURITY -> NamedTextColor.BLUE;
            case MERCHANT -> NamedTextColor.DARK_GREEN;
            default -> NamedTextColor.GRAY;
        };
    }

    private String activityTime(java.time.Instant createdAt) {
        long seconds = Math.max(0L, java.time.Duration.between(createdAt, java.time.Instant.now()).toSeconds());
        if (seconds < 60L) return seconds + " giây trước";
        if (seconds < 3600L) return seconds / 60L + " phút trước";
        return seconds / 3600L + " giờ trước";
    }

    private String dialogue(FarmerDefinition definition, FarmerPhase phase, Location location) {
        org.bukkit.World world = location.getWorld();
        if (!definition.enabled(BehaviorFlag.MASTER)) {
            return dialogueLine(
                    "Hôm nay tôi được nghỉ. Có lẽ tôi sẽ đi dạo quanh nhà một chút.",
                    "Tôi đang tạm nghỉ việc. Khi nào cần, bạn có thể bật NPC hoạt động trong bảng quản lý.",
                    "Một ngày yên tĩnh cũng tốt. Hiện tôi chưa được giao việc gì cả.");
        }
        if (definition.activeRole() == ResidentRole.RESIDENT) {
            return residentDialogue(phase, world);
        }
        if (definition.activeRole() == ResidentRole.RANCHER) {
            return rancherDialogue(definition, phase, location);
        }
        if (definition.activeRole() == ResidentRole.FISHER) {
            VillageDefinition village = plugin.villages().get(definition.villageId());
            if (village == null || village.workZone(VillageWorkZoneType.FISHING) == null) {
                return "Tôi đã có cần câu, nhưng vẫn chưa được giao Điểm câu có nước.";
            }
            return fisherDialogue(phase, world);
        }
        if (CivilProfessionRuntime.zoneFor(definition.activeRole()) != null) {
            return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
                case GOING_TO_WORK_STATION -> "Tôi đang đi tới "
                        + workZoneName(CivilProfessionRuntime.zoneFor(definition.activeRole())) + ".";
                case PRODUCING -> "Tôi đang hoàn thành một lượt " + roleName(definition.activeRole()) + ".";
                case PATROLLING -> "Tôi đang tuần tra quanh trạm bảo vệ.";
                case ALERTING -> "Có quái vật gần đây! Tôi đang báo động cho làng.";
                case RESTING -> "Tôi đang nghỉ trước lượt làm việc tiếp theo.";
                default -> "Tôi đang chờ lượt làm việc tiếp theo tại "
                        + workZoneName(CivilProfessionRuntime.zoneFor(definition.activeRole())) + ".";
            };
        }
        if (definition.activeRole() != ResidentRole.FARMER) {
            return dialogueLine(
                    "Tôi đang chờ khu làm việc cho nghề " + roleName(definition.activeRole()) + ".",
                    "Nghề " + roleName(definition.activeRole()) + " vẫn chưa có việc phù hợp cho tôi.",
                    "Khi khu làm việc sẵn sàng, tôi sẽ bắt tay vào nghề " + roleName(definition.activeRole()) + ".");
        }
        if (definition.plot() == null) {
            return dialogueLine(
                    "Tôi đã có nhà, nhưng vẫn chưa được giao ruộng. Bạn hãy đặt khu ruộng cho tôi nhé.",
                    "Tôi đã chuẩn bị cuốc rồi, chỉ còn thiếu một khu ruộng để bắt đầu.",
                    "Không có ruộng thì một nông dân như tôi cũng đành đứng chờ thôi.");
        }
        if (!definition.enabled(BehaviorFlag.HARVEST) && !definition.enabled(BehaviorFlag.PLANT)) {
            return "Ruộng đã được giao, nhưng Làm nông vẫn đang TẮT. Hãy Shift + click phải tôi để bật.";
        }
        if (phase == null || phase == FarmerPhase.INACTIVE) {
            String readiness = plugin.manager().readiness(definition.npcUuid());
            if (!plugin.manager().ready(definition.npcUuid())) {
                return "Tôi chưa thể làm việc: " + readiness + ".";
            }
            ResidentSchedule schedule = definition.schedule(ResidentRole.FARMER, defaultSchedule());
            if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE)
                    && !SchedulePolicy.isScheduledTime(world.getTime(), schedule)) {
                return offShiftDialogue(world);
            }
            if (definition.enabled(BehaviorFlag.FOLLOW_SCHEDULE) && world.hasStorm()) {
                return dialogueLine(
                        "Trời đang mưa nên tôi tạm nghỉ. Khi trời quang tôi sẽ ra ruộng.",
                        "Đất đang ướt quá. Tôi sẽ đợi cơn mưa qua rồi làm tiếp.",
                        "Mưa thế này cây cối được uống no, còn tôi tranh thủ nghỉ một lát.");
            }
            return dialogueLine(
                    "Tôi đã sẵn sàng và đang chờ lượt kiểm tra ruộng tiếp theo.",
                    "Ruộng đã sẵn sàng. Tôi sẽ kiểm tra cây trồng ngay đây.",
                    "Tôi đang xem nên bắt đầu từ luống nào trước.");
        }
        String fallback = farmerDialogue(phase);
        return definition.enabled(BehaviorFlag.CHARACTER_PROFILE)
                ? ResidentPresentation.contextualDialogue(definition.profile(), phase, fallback)
                : fallback;
    }

    private String residentDialogue(FarmerPhase phase, org.bukkit.World world) {
        if (world.hasStorm()) {
            return dialogueLine(
                    "Mưa rồi. Tôi sẽ ở gần nhà cho tới khi trời quang.",
                    "Cơn mưa làm con đường vắng hẳn. Bạn cũng nên tìm chỗ trú nhé.");
        }
        return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
            case WANDERING -> dialogueLine(
                    "Tôi đang đi một vòng xem trong xóm có ai cần giúp không.",
                    "Đi bộ một chút giúp tôi biết hôm nay trong làng có chuyện gì.",
                    "Con đường này ngày nào cũng đi, vậy mà lúc nào cũng có điều mới để thấy.");
            case WATCHING_PLAYER -> dialogueLine(
                    greeting(world) + " Bạn mới ghé qua làng à?",
                    greeting(world) + " Hôm nay bạn định làm gì vậy?",
                    "Tôi thấy bạn đi ngang nên muốn chào một tiếng.");
            case LOOKING_AROUND -> dialogueLine(
                    "Hôm nay trong làng khá yên bình.",
                    "Tôi đang xem quanh đây có việc gì cần làm không.",
                    "Đứng đây có thể nhìn được khá nhiều nơi trong làng.");
            case RESTING, INACTIVE -> dialogueLine(
                    greeting(world) + " Tôi đang nghỉ một lát trước khi đi một vòng.",
                    "Mọi việc hôm nay có vẻ ổn. Tôi tranh thủ ngồi lại một chút.",
                    "Nếu trong làng có ai cần giúp, cứ gọi tôi nhé.");
            case GOING_TO_MARKET -> "Tôi đang tới chợ xem hôm nay có hàng gì mới.";
            case SHOPPING -> dialogueLine(
                    "Tôi chỉ xem một vòng thôi, nhưng biết đâu lại tìm được món hữu ích.",
                    "Chợ đông vui thì ngôi làng mới có sức sống.");
            case GOING_TO_SCENIC -> "Tôi đang tới chỗ thoáng một chút để ngắm làng.";
            case SOCIALIZING -> dialogueLine(
                    "Chúng tôi đang hỏi thăm nhau vài câu.",
                    "Lâu lâu dừng lại trò chuyện một chút cũng tốt.");
            case GOING_TO_SEAT -> dialogueLine(
                    "Tôi đang tìm một chỗ ngồi nghỉ chân.",
                    "Tôi ghé ghế nghỉ một lát rồi sẽ đi tiếp.");
            case SITTING_REST -> dialogueLine(
                    "Ngồi xuống một lát thấy khỏe hơn hẳn.",
                    "Tôi chỉ nghỉ chân thôi, lát nữa sẽ tiếp tục.",
                    "Hôm nay trong làng khá yên bình.");
            case SITTING_DINING -> dialogueLine(
                    "Tôi đang dùng bữa, lát nữa sẽ quay lại công việc.",
                    "Bữa ăn đơn giản thế này là đủ lấy sức rồi.",
                    "Bạn đã ăn gì chưa? Ngồi xuống trò chuyện một lát nhé.");
            case STANDING_UP -> dialogueLine(
                    "Nghỉ đủ rồi, tôi đi tiếp đây.",
                    "Đến lúc tiếp tục công việc rồi.");
            case GOING_HOME -> "Trời cũng muộn rồi, tôi đang trở về nhà.";
            case GOING_TO_BED -> "Trời đã khuya, tôi đang đi tới giường ngủ.";
            case SLEEPING -> "Tôi đang ngủ. Sáng mai chúng ta nói chuyện nhé.";
            case SHELTERING -> "Có nguy hiểm ở gần. Tôi phải về nơi an toàn trước đã!";
            default -> "Tôi đang lo vài việc thường ngày quanh làng.";
        };
    }

    private String farmerDialogue(FarmerPhase phase) {
        return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
            case GOING_TO_PLOT -> dialogueLine(
                    "Tôi đang ra ruộng. Một ngày làm việc mới bắt đầu rồi.",
                    "Tôi ra xem những luống cây hôm nay thế nào.",
                    "Hy vọng đêm qua cây lớn tốt. Tôi đang tới ruộng kiểm tra đây.");
            case FINDING_WORK -> dialogueLine(
                    "Tôi đang kiểm tra xem cây nào đã chín.",
                    "Để tôi nhìn từng luống, cây chín cần được thu hoạch trước.",
                    "Không nên bỏ sót cây nào. Tôi đang kiểm tra cả ruộng.");
            case GOING_TO_CROP -> dialogueLine(
                    "Tôi thấy một cây cần chăm sóc ở phía trước.",
                    "Luống bên kia có việc rồi, tôi qua đó ngay.",
                    "Cây kia trông đã sẵn sàng. Để tôi tới xem.");
            case INSPECTING -> dialogueLine(
                    "Để tôi xem cây này đã sẵn sàng chưa.",
                    "Phải nhìn kỹ trước khi thu hoạch, cây non thì nên để lại.",
                    "Lá và màu hạt trông khá tốt. Tôi kiểm tra thêm một chút.");
            case WORKING -> dialogueLine(
                    "Tôi đang làm việc, nông sản sẽ được nộp vào kho làng.",
                    "Thu hoạch xong phải trồng lại ngay để vụ sau không bị chậm.",
                    "Thêm một cây nữa. Ruộng sẽ sớm gọn gàng thôi.");
            case GOING_TO_STORAGE -> dialogueLine(
                    "Tôi đang mang nông sản tới kho của làng.",
                    "Mẻ này đã đủ rồi, tôi đem về kho trước nhé.",
                    "Nông sản phải được cất vào kho trước khi tôi làm tiếp.");
            case DEPOSITING -> dialogueLine(
                    "Tôi đang kiểm lại nông sản trước khi giao vào kho.",
                    "Đã tới kho rồi. Để tôi cất mọi thứ cho gọn.",
                    "Mẻ nông sản này sẽ được ghi vào kho chung của làng.");
            case RETURNING_TO_PLOT -> dialogueLine(
                    "Giao hàng xong rồi, tôi quay lại ruộng đây.",
                    "Kho đã nhận đủ. Ngoài ruộng vẫn còn việc chờ tôi.",
                    "Xong một chuyến giao hàng, giờ tiếp tục chăm ruộng thôi.");
            case GOING_TO_MARKET -> "Tôi đang tới chợ xem giá nông sản hôm nay.";
            case SHOPPING -> "Tôi đang xem trong chợ có dụng cụ hay hạt giống nào hữu ích không.";
            case GOING_TO_SCENIC -> "Làm xong một lượt rồi, tôi đi hóng gió một chút.";
            case SOCIALIZING -> "Tôi đang hỏi thăm xem ruộng của mọi người năm nay thế nào.";
            case GOING_TO_SEAT -> "Đến giờ nghỉ rồi, tôi đang tới chỗ ngồi.";
            case SITTING_REST -> "Tôi ngồi nghỉ lấy sức một lát rồi sẽ làm tiếp.";
            case SITTING_DINING -> dialogueLine(
                    "Tôi đang dùng bữa giữa ca, lát nữa sẽ quay lại ruộng.",
                    "Làm việc cả buổi rồi, tôi ăn nhẹ một chút.",
                    "Bữa trưa xong tôi sẽ tiếp tục chăm các luống cây.");
            case STANDING_UP -> dialogueLine(
                    "Nghỉ đủ rồi, tôi quay lại làm việc đây.",
                    "Đến lúc tiếp tục chăm ruộng rồi.");
            case RESTING -> dialogueLine(
                    "Tôi nghỉ tay một chút rồi sẽ làm tiếp.",
                    "Làm ruộng không nên quá vội. Nghỉ một lát sẽ đỡ sót việc hơn.",
                    "Tôi đang lấy lại sức trước lượt chăm cây tiếp theo.");
            case LUNCH_BREAK -> dialogueLine(
                    "Đến giờ nghỉ trưa rồi. Tôi ăn nhẹ một chút rồi sẽ quay lại ruộng.",
                    "Tôi đang nghỉ giữa ca. Lát nữa tôi sẽ tiếp tục chăm các luống cây.",
                    "Nghỉ trưa một chút sẽ giúp buổi chiều làm việc cẩn thận hơn.");
            case WATCHING_PLAYER -> dialogueLine(
                    "Chào bạn. Cẩn thận đừng giẫm lên đất trồng nhé.",
                    "Bạn tới thăm ruộng à? Cây cối hôm nay phát triển khá tốt.",
                    "Nếu thấy cây nào chín, cứ để tôi chăm sóc nhé.");
            case LOOKING_AROUND -> "Tôi đang nhìn lại các luống để chắc rằng không bỏ sót cây nào.";
            case WANDERING -> "Tôi đi dọc bờ ruộng kiểm tra đất và lối đi một chút.";
            case GOING_HOME -> dialogueLine(
                    "Ca làm đã xong, tôi đang trở về nhà.",
                    "Hôm nay làm được khá nhiều rồi. Phần còn lại để ngày mai.",
                    "Tôi cất cuốc và về nhà đây. Mai lại tiếp tục.");
            case GOING_TO_BED -> "Trời đã khuya, tôi đang về giường nghỉ ngơi.";
            case SLEEPING -> "Tôi đang ngủ để mai còn làm việc.";
            case WAKING_UP -> "Tôi vừa thức dậy và đang chuẩn bị cho ngày mới.";
            case LEAVING_HOME -> "Tôi đang rời khỏi nhà để bắt đầu buổi sáng.";
            case MORNING_ACTIVITY -> "Tôi đã ra khỏi nhà và đang bắt đầu công việc hôm nay.";
            case SHELTERING -> "Có quái vật ở gần! Ruộng để sau, tôi phải tìm chỗ an toàn trước.";
            case INACTIVE -> "Tôi đã sẵn sàng và đang chờ lượt kiểm tra ruộng tiếp theo.";
            case GOING_TO_FISHING_SPOT, CASTING_LINE, WAITING_FOR_BITE, REELING_IN ->
                    "Tôi đang phụ người trong làng chuẩn bị một chuyến câu cá.";
            case GOING_TO_WORK_STATION -> "Tôi đang đi tới trạm nghề.";
            case PRODUCING -> "Tôi đang hoàn thành một lượt sản xuất cho làng.";
            case PATROLLING -> "Tôi đang tuần tra quanh khu vực được giao.";
            case ALERTING -> "Có quái vật gần đây! Tôi đang báo động cho làng.";
            case GOING_TO_STALL, OPENING_STALL, SERVING -> "Tôi đang phụ chuẩn bị khu buôn bán của làng.";
        };
    }

    private String fisherDialogue(FarmerPhase phase, org.bukkit.World world) {
        if (world.hasStorm()) {
            return dialogueLine(
                    "Mặt nước đang động mạnh. Tôi sẽ đợi trời yên rồi mới thả câu.",
                    "Mưa thế này khó nhìn phao lắm. Tôi tạm cất cần câu trước.",
                    "Hôm nay nước không yên. Cá để khi trời quang rồi tính tiếp.");
        }
        return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
            case GOING_TO_FISHING_SPOT -> dialogueLine(
                    "Tôi đang ra điểm câu. Hy vọng hôm nay nước thuận.",
                    "Tôi mang cần ra bờ đây. Chỗ đó thường có cá vào giờ này.",
                    "Muốn có cá tươi thì phải ra bờ từ sớm.");
            case CASTING_LINE -> dialogueLine(
                    "Tôi đang chọn chỗ nước êm để thả câu.",
                    "Một cú quăng vừa đủ xa sẽ không làm đàn cá hoảng.",
                    "Phao đã xuống nước. Giờ phải giữ tay thật nhẹ.");
            case WAITING_FOR_BITE -> dialogueLine(
                    "Suỵt... phao đang rung. Có lẽ cá đang tới gần.",
                    "Câu cá cần kiên nhẫn. Giật cần sớm là mất cả mồi lẫn cá.",
                    "Mặt nước vừa động một chút. Tôi đang chờ đúng thời điểm.",
                    "Có những lúc ngồi yên bên nước cũng là một phần của công việc.");
            case REELING_IN -> dialogueLine(
                    "Cắn câu rồi! Tôi đang kéo dây về.",
                    "Con này giằng khá khỏe, phải giữ dây đều tay.",
                    "Từ từ thôi... kéo mạnh quá là tuột lưỡi câu ngay.");
            case RESTING -> dialogueLine(
                    "Tôi đang nghỉ tay và sẽ thử thả câu lại sau một lát.",
                    "Tôi đang kiểm tra lại dây và lưỡi câu trước lượt tiếp theo.",
                    "Tôi tạm nghỉ ở điểm câu, lát nữa sẽ tiếp tục.");
            case INACTIVE -> offShiftFishingDialogue(world);
            case WATCHING_PLAYER -> dialogueLine(
                    "Chào bạn. Nếu đứng gần bờ, nhớ đừng làm động mặt nước nhé.",
                    "Bạn cũng thích câu cá à? Chỗ này cần kiên nhẫn một chút.");
            case GOING_HOME -> "Tôi đã cất cần câu và đang mang ngày làm việc về nhà.";
            default -> "Tôi đang chuẩn bị cho lượt câu tiếp theo.";
        };
    }

    private String offShiftFishingDialogue(org.bukkit.World world) {
        return world.getTime() >= 13000L
                ? dialogueLine(
                        "Trời tối rồi, tôi sẽ kiểm tra cần câu và nghỉ đến sáng.",
                        "Ban đêm bờ nước không an toàn. Ngày mai tôi quay lại.")
                : dialogueLine(
                        "Chưa tới giờ câu. Tôi đang chuẩn bị dây và lưỡi câu.",
                        "Tôi đang xem gió và mặt nước trước khi bắt đầu ca.");
    }

    private String rancherDialogue(FarmerDefinition definition, FarmerPhase phase, Location location) {
        org.bukkit.World world = location.getWorld();
        if (phase == FarmerPhase.GOING_TO_BED) return "Trời đã khuya, tôi đang đi tới giường ngủ.";
        if (phase == FarmerPhase.SLEEPING) return "Tôi đang ngủ. Sáng mai tôi sẽ kiểm tra đàn vật nuôi.";
        if (world.hasStorm()) {
            String nearby = RanchDialogue.nearbyAnimalLine(
                    location, plugin.economy().villageAccount(definition.villageId()));
            return nearby == null
                    ? "Trời mưa rồi. Tôi đang để đàn vật nuôi yên trong khu chăn nuôi."
                    : "Trời mưa rồi. " + nearby;
        }
        String nearby = RanchDialogue.nearbyAnimalLine(
                location, plugin.economy().villageAccount(definition.villageId()));
        if (nearby != null) return nearby;
        if (world.getTime() < plugin.config().workStartTick()
                || world.getTime() >= plugin.config().workEndTick()) {
            return "Hiện đang ngoài ca chăn nuôi. Tôi sẽ kiểm tra đàn khi tới giờ làm.";
        }
        return plugin.ranchers().status(definition.npcUuid(), plugin.config()) + ".";
    }

    private String offShiftDialogue(org.bukkit.World world) {
        long time = world.getTime();
        if (time >= 13000L || time < 1000L) {
            return dialogueLine(
                    "Trời tối rồi, ruộng để sáng mai tôi sẽ chăm tiếp.",
                    "Một ngày làm việc đã hết. Giờ là lúc trở về nhà nghỉ ngơi.",
                    "Ban đêm không nhìn rõ cây trồng. Tôi sẽ bắt đầu lại khi trời sáng.");
        }
        return dialogueLine(
                "Ca làm của tôi chưa bắt đầu. Tôi đang chuẩn bị dụng cụ.",
                "Vẫn còn sớm, khi đúng giờ tôi sẽ ra ruộng.",
                "Tôi đang chờ tới giờ làm việc rồi sẽ bắt đầu kiểm tra cây.");
    }

    private String greeting(org.bukkit.World world) {
        long time = world.getTime();
        if (time < 1000L) return "Chào buổi sáng.";
        if (time < 6000L) return "Chào buổi sáng.";
        if (time < 12000L) return "Chào buổi chiều.";
        return "Chào buổi tối.";
    }

    private String dialogueLine(String... lines) {
        return lines[java.util.concurrent.ThreadLocalRandom.current().nextInt(lines.length)];
    }

    private List<String> profileLore(ResidentProfile profile, List<String> base) {
        List<String> lore = new ArrayList<>(base);
        lore.addAll(ResidentPresentation.characterLines(profile));
        return lore;
    }
}
