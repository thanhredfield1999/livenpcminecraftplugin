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
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
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
    private static final int HOME_SLOT = 18;
    private static final int PLOT_SLOT = 19;
    private static final int RANGE_SLOT = 20;
    private static final int ROLES_SLOT = 21;
    private static final int DETAIL_RELOAD_SLOT = 24;
    private static final int REMOVE_SLOT = 25;
    private static final int BACK_SLOT = 26;
    private static final long PLACEMENT_TIMEOUT_MILLIS = 120_000L;
    private final LivingNpcPlugin plugin;
    private final Map<UUID, PlacementSession> placements = new HashMap<>();
    private final Map<UUID, Long> dialogueCooldowns = new HashMap<>();

    ResidentGui(LivingNpcPlugin plugin) {
        this.plugin = plugin;
    }

    void openList(Player player) {
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.VILLAGE_LIST, null, 54, Component.text("LivingNPC - Danh sách làng"));
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
                            "Kho: " + account.inventorySize() + "/" + plugin.config().inventoryCapacity(),
                            "Rương giao hàng: " + (village.deliveryChest() == null ? "CHƯA ĐẶT" : "ĐÃ ĐẶT"),
                            "Điểm chợ: " + (village.marketPoint() == null ? "CHƯA ĐẶT" : "ĐÃ ĐẶT"),
                            "Điểm ngắm cảnh: " + (village.scenicPoint() == null ? "CHƯA ĐẶT" : "ĐÃ ĐẶT"),
                            "Nhấn để quản lý làng")));
        }
        menu.getInventory().setItem(49, item(
                Material.WRITABLE_BOOK, "Tạo làng mới", NamedTextColor.AQUA,
                List.of("Dùng lệnh:", "/livingnpc lang tao <id> <tên>", "Làng được tạo tại vị trí bạn đứng")));
        player.openInventory(menu.getInventory());
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
                "Tạo cư dân mới",
                NamedTextColor.GOLD,
                List.of("Mở thư viện hồ sơ cư dân", "Sau đó nhấp phải một block để tạo")));
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
                        "Vật phẩm: " + town.inventorySize() + "/" + plugin.config().inventoryCapacity(),
                        "Số dư: " + money(town.balanceMinor()),
                        "Nhấn để xem hàng trong kho")));
        boolean pending = placements.containsKey(player.getUniqueId());
        menu.getInventory().setItem(CANCEL_PLACEMENT_SLOT, item(
                pending ? Material.BARRIER : Material.GRAY_DYE,
                "Chọn vị trí: " + (pending ? "[ĐANG CHỜ]" : "[KHÔNG CÓ]"),
                pending ? NamedTextColor.RED : NamedTextColor.GRAY,
                List.of(pending ? "Nhấn để hủy thao tác chọn block" : "Không có thao tác chọn vị trí")));
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại danh sách làng", NamedTextColor.YELLOW, List.of("Chọn một làng khác")));
        player.openInventory(menu.getInventory());
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
                        "Kho: " + town.inventorySize() + "/" + plugin.config().inventoryCapacity(),
                        "Hàng có giá được bán cuối ca nếu đã bật")));
        menu.getInventory().setItem(53, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về làng " + village.name())));
        player.openInventory(menu.getInventory());
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
        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        BehaviorFlag[] flags = BehaviorFlag.values();
        for (int index = 0; index < flags.length; index++) {
            BehaviorFlag behavior = flags[index];
            int slot = slots[index];
            menu.behaviorsBySlot().put(slot, behavior);
            menu.getInventory().setItem(slot, behaviorItem(definition, behavior));
        }
        menu.getInventory().setItem(13, residentItem(npc, definition));
        menu.getInventory().setItem(HOME_SLOT, locationItem(
                Material.RED_BED, "Nhà", definition.home(), true, "Nhấn, sau đó nhấp phải một block"));
        menu.getInventory().setItem(PLOT_SLOT, locationItem(
                Material.FARMLAND, "Khu ruộng", definition.plot(), definition.plot() != null, "Nhấn, sau đó nhấp phải tâm ruộng"));
        menu.getInventory().setItem(RANGE_SLOT, item(
                Material.COMPARATOR,
                "Bán kính ruộng: " + definition.plotRadius() + " " + (definition.plot() == null ? "[CHƯA ĐẶT]" : "[ĐÃ ĐẶT]"),
                definition.plot() == null ? NamedTextColor.RED : NamedTextColor.GREEN,
                List.of(
                        "Chuột trái: +1",
                        "Chuột phải: -1",
                        "Giữ Shift: thay đổi 2 block",
                        "Tối đa: " + plugin.config().maxPlotRadius())));
        menu.getInventory().setItem(ROLES_SLOT, item(
                Material.CLOCK,
                "Nghề và lịch làm việc",
                NamedTextColor.AQUA,
                List.of(
                        "Nghề đang chạy: " + definition.activeRole().storageKey(),
                        "Số nghề được giao: " + definition.profile().roles().size(),
                        "Nhấn để xem level, XP và chỉnh lịch")));
        menu.getInventory().setItem(DETAIL_RELOAD_SLOT, item(
                Material.RECOVERY_COMPASS, "Tải lại cấu hình", NamedTextColor.YELLOW, List.of("Nhấn để tải lại rồi quay về đây")));
        menu.getInventory().setItem(REMOVE_SLOT, item(
                Material.LAVA_BUCKET, "Xóa cư dân", NamedTextColor.RED, List.of("Mở màn hình xác nhận")));
        menu.getInventory().setItem(BACK_SLOT, item(Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về danh sách cư dân")));
        player.openInventory(menu.getInventory());
    }

    private void openRoles(Player player, UUID uuid) {
        FarmerDefinition definition = plugin.manager().get(uuid);
        if (definition == null) {
            openList(player);
            return;
        }
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.ROLE_LIST, uuid, 27, Component.text("Nghề & lịch - " + definition.profile().name()));
        int slot = 0;
        for (ResidentRole role : definition.profile().roles().stream().sorted().toList()) {
            menu.rolesBySlot().put(slot, role);
            menu.getInventory().setItem(slot, roleItem(definition, role));
            slot++;
        }
        menu.getInventory().setItem(13, item(
                Material.BOOK,
                "Cách sử dụng",
                NamedTextColor.YELLOW,
                List.of(
                        "1. Chọn một nghề ở hàng trên",
                        "2. Chỉnh giờ bắt đầu và kết thúc",
                        "3. NPC tự đổi nghề theo lịch",
                        "Lịch chồng nhau: giữ nghề đang chạy")));
        menu.getInventory().setItem(BACK_SLOT, item(
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về màn hình chi tiết NPC")));
        player.openInventory(menu.getInventory());
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
                roleMaterial(role),
                roleName(role),
                role == definition.activeRole() ? NamedTextColor.GREEN : NamedTextColor.GOLD,
                List.of(
                        "Trạng thái: " + (role == definition.activeRole() ? "ĐANG CHẠY" : "đang chờ"),
                        "Ca: " + clockTime(schedule.startTick()) + " - " + clockTime(schedule.endTick()),
                        "Nguồn lịch: " + (custom ? "riêng cho nghề" : "mặc định config.yml"),
                        "Thay đổi được lưu ngay")));
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
                Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về danh sách nghề")));
        player.openInventory(menu.getInventory());
    }

    private void openProfiles(Player player, String villageId) {
        ResidentMenu menu = new ResidentMenu(
                ResidentMenu.Type.PROFILE_LIST, null, villageId, 54, Component.text("LivingNPC - Hồ sơ cư dân"));
        int slot = 0;
        for (String id : plugin.profiles().ids()) {
            if (slot >= 45) {
                break;
            }
            ResidentProfile profile = plugin.profiles().get(id);
            boolean supported = profile.hasRole(ResidentRole.FARMER);
            menu.profilesBySlot().put(slot, id);
            menu.getInventory().setItem(slot, item(
                    supported ? Material.PLAYER_HEAD : Material.BARRIER,
                    profile.name() + " - " + profile.title(),
                    supported ? NamedTextColor.GOLD : NamedTextColor.RED,
                    List.of(
                            "Giới tính: " + genderName(profile.gender()),
                            "Nghề: " + profile.roles().stream().sorted().map(this::roleName).toList(),
                            "Skin: " + (profile.skin().isBlank() ? "mặc định" : profile.skin()),
                            supported ? "Nhấn để bắt đầu chọn vị trí" : "Chưa có runtime tương ứng")));
            slot++;
        }
        menu.getInventory().setItem(49, item(Material.ARROW, "Quay lại", NamedTextColor.YELLOW, List.of("Về danh sách cư dân")));
        player.openInventory(menu.getInventory());
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
                } else if (slot == PLOT_SLOT) {
                    FarmerDefinition definition = plugin.manager().get(menu.residentUuid());
                    beginPlacement(player, new PlacementSession(
                            PlacementType.SET_PLOT,
                            menu.residentUuid(),
                            null,
                            null,
                            definition == null ? 4 : definition.plotRadius(),
                            System.currentTimeMillis() + PLACEMENT_TIMEOUT_MILLIS));
                } else if (slot == RANGE_SLOT) {
                    adjustRange(player, menu.residentUuid(), event);
                } else if (slot == ROLES_SLOT) {
                    openRoles(player, menu.residentUuid());
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
                    openRoleSchedule(player, menu.residentUuid(), role);
                } else if (slot == BACK_SLOT) {
                    openDetail(player, menu.residentUuid());
                }
            }
            case ROLE_SCHEDULE -> {
                if (slot == 11 || slot == 15) {
                    adjustSchedule(player, menu.residentUuid(), menu.role(), slot == 11, event);
                } else if (slot == 22) {
                    resetSchedule(player, menu.residentUuid(), menu.role());
                } else if (slot == BACK_SLOT) {
                    openRoles(player, menu.residentUuid());
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
        Player player = event.getClicker();
        long now = System.currentTimeMillis();
        if (dialogueCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        dialogueCooldowns.put(player.getUniqueId(), now + 2_000L);
        event.getNPC().faceLocation(player.getEyeLocation());
        FarmerPhase phase = plugin.manager().phase(event.getNPC().getId());
        player.sendMessage(Component.text(
                definition.profile().name() + ": " + dialogue(definition, phase),
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
            case CREATE_RESIDENT -> finishCreate(player, session, adjacentLocation(player, clicked, event));
            case SET_HOME -> finishHome(player, session, adjacentLocation(player, clicked, event));
            case SET_PLOT -> finishPlot(player, session, clicked.getLocation());
            case SET_TOWN_STORAGE -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        placements.remove(event.getPlayer().getUniqueId());
        dialogueCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void createFromProfile(Player player, String profileId, String villageId) {
        ResidentProfile profile = plugin.profiles().get(profileId);
        if (profile == null || !profile.hasRole(ResidentRole.FARMER)) {
            player.sendMessage(Component.text("Hồ sơ này chưa có runtime nông dân để tạo an toàn.", NamedTextColor.RED));
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
        lore.add("Trí tuệ NPC: " + state(definition.enabled(BehaviorFlag.MASTER)));
        lore.add("Thu hoạch: " + state(definition.enabled(BehaviorFlag.HARVEST)));
        lore.add("Trồng cây: " + state(definition.enabled(BehaviorFlag.PLANT)));
        lore.add("Tự bán hàng: " + state(definition.enabled(BehaviorFlag.SELL_INVENTORY)));
        NpcAccount account = plugin.economy().account(npc.getUniqueId());
        lore.add("Sản lượng ca: " + account.producedThisShift() + "/" + plugin.config().maxOutputPerShift());
        lore.add("Làng: " + (definition.villageId() == null ? "chưa gán" : definition.villageId()));
        lore.add("Sẵn sàng: " + plugin.manager().readiness(definition.npcUuid()));
        lore.add("Ruộng: " + (definition.plot() == null ? "chưa gán" : definition.plot().world()));
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
                        role == ResidentRole.FARMER ? "Hoạt động: sẵn sàng" : "Hoạt động: chưa triển khai, không tác động thế giới",
                        "Nhấn để chỉnh lịch nghề này"));
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

    private String roleName(ResidentRole role) {
        return switch (role) {
            case FARMER -> "Nông dân";
            case FISHER -> "Ngư dân";
            case COOK -> "Đầu bếp";
            case CRAFTER -> "Thợ chế tạo";
            case MINER -> "Thợ mỏ";
            case RANCHER -> "Chăn nuôi";
            case SECURITY -> "Bảo vệ";
            case MELEE_TRAINING -> "Tập kiếm";
            case ARCHERY_TRAINING -> "Tập cung";
            case SPARRING -> "Đấu tập";
        };
    }

    private Material roleMaterial(ResidentRole role) {
        return switch (role) {
            case FARMER -> Material.IRON_HOE;
            case FISHER -> Material.FISHING_ROD;
            case COOK -> Material.COOKED_BEEF;
            case CRAFTER -> Material.CRAFTING_TABLE;
            case MINER -> Material.IRON_PICKAXE;
            case RANCHER -> Material.WHEAT;
            case SECURITY -> Material.SHIELD;
            case MELEE_TRAINING -> Material.IRON_SWORD;
            case ARCHERY_TRAINING -> Material.BOW;
            case SPARRING -> Material.WOODEN_SWORD;
        };
    }

    private void beginPlacement(Player player, PlacementSession session) {
        placements.put(player.getUniqueId(), session);
        player.closeInventory();
        player.sendMessage(Component.text("[ĐANG CHỜ] Hãy nhấp phải một block trong vòng 120 giây.", NamedTextColor.YELLOW));
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
        ResidentProfile profile = plugin.profiles().get(session.profileId());
        if (profile == null || plugin.manager().usedProfileIds().contains(profile.id().toLowerCase(java.util.Locale.ROOT))) {
            player.sendMessage(Component.text("Hồ sơ không tồn tại hoặc đã được sử dụng.", NamedTextColor.RED));
            openProfiles(player, session.villageId());
            return;
        }
        NPC npc = plugin.manager().create(profile, location, session.villageId());
        if (npc == null) {
            player.sendMessage(Component.text("Citizens không thể tạo cư dân tại block đã chọn.", NamedTextColor.RED));
            openProfiles(player, session.villageId());
            return;
        }
        player.sendMessage(Component.text("[ĐÃ XONG] Đã tạo cư dân và đặt vị trí nhà.", NamedTextColor.GREEN));
        openDetail(player, npc.getUniqueId());
    }

    private void finishHome(Player player, PlacementSession session, Location location) {
        if (plugin.manager().setHome(session.residentUuid(), location)) {
            player.sendMessage(Component.text("[ĐÃ XONG] Đã lưu vị trí nhà.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Cư dân không còn tồn tại.", NamedTextColor.RED));
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

    private Location adjacentLocation(Player player, Block clicked, PlayerInteractEvent event) {
        Location location = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0.0f);
        return location;
    }

    private void reopenAfterPlacement(Player player, PlacementSession session) {
        if (session.residentUuid() == null) {
            openProfiles(player, session.villageId());
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
        player.openInventory(menu.getInventory());
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
            meta.displayName(Component.text(name, color));
            meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    private String state(boolean enabled) {
        return enabled ? "BẬT" : "TẮT";
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
            default -> Material.PAPER;
        };
    }

    private String itemName(String key) {
        return switch (key) {
            case "wheat" -> "Lúa mì";
            case "carrot" -> "Cà rốt";
            case "potato" -> "Khoai tây";
            case "beetroot" -> "Củ dền";
            default -> key;
        };
    }

    private String dialogue(FarmerDefinition definition, FarmerPhase phase) {
        if (!definition.enabled(BehaviorFlag.MASTER)) {
            return "Hôm nay tôi đang được cho nghỉ. Bạn hãy bật Trí tuệ NPC trong bảng quản lý nhé.";
        }
        if (definition.activeRole() != ResidentRole.FARMER) {
            return "Tôi đang chờ khu làm việc cho nghề " + roleName(definition.activeRole()) + ".";
        }
        if (definition.plot() == null) {
            return "Tôi đã có nhà, nhưng vẫn chưa được giao ruộng. Bạn hãy đặt khu ruộng cho tôi nhé.";
        }
        return switch (phase == null ? FarmerPhase.INACTIVE : phase) {
            case GOING_TO_PLOT -> "Tôi đang ra ruộng. Một ngày làm việc mới bắt đầu rồi.";
            case FINDING_WORK -> "Tôi đang kiểm tra xem cây nào đã chín.";
            case GOING_TO_CROP -> "Tôi thấy một cây cần chăm sóc ở phía trước.";
            case INSPECTING -> "Để tôi xem cây này đã sẵn sàng chưa.";
            case WORKING -> "Tôi đang làm việc, nông sản sẽ được nộp vào kho tổng.";
            case GOING_TO_STORAGE -> "Tôi đang mang nông sản tới kho của làng.";
            case DEPOSITING -> "Tôi đang giao nông sản vào kho làng.";
            case RETURNING_TO_PLOT -> "Giao hàng xong rồi, tôi quay lại ruộng đây.";
            case GOING_TO_MARKET -> "Tôi đang tới chợ của làng để xem hàng.";
            case SHOPPING -> "Tôi đang xem các quầy hàng cùng một người bạn trong làng.";
            case GOING_TO_SCENIC -> "Tôi đang tới điểm ngắm cảnh của làng.";
            case SOCIALIZING -> "Tôi đang trò chuyện với một người bạn trong làng.";
            case RESTING -> "Tôi nghỉ một chút rồi sẽ làm tiếp.";
            case WATCHING_PLAYER -> "Chào bạn. Hôm nay bạn ghé thăm làng à?";
            case LOOKING_AROUND -> "Ngôi làng hôm nay trông thật yên bình.";
            case WANDERING -> "Tôi đang đi dạo quanh khu vực của mình.";
            case GOING_HOME -> "Ca làm đã xong, tôi đang trở về nhà.";
            case SHELTERING -> "Có quái vật ở gần! Tôi phải tìm chỗ an toàn.";
            case INACTIVE -> "Chào bạn. Hiện tôi đang ở ngoài giờ làm việc.";
        };
    }
}
