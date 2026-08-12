package vn.heomc.livingnpc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class ResidentMenu implements InventoryHolder {
    enum Type {
        VILLAGE_LIST,
        RESIDENT_LIST,
        TOWN_STORE,
        ACTIVITY_LIST,
        VILLAGE_WORK_ZONES,
        SEAT_LIST,
        RESIDENT_DETAIL,
        ROLE_LIST,
        ROLE_SCHEDULE,
        PROFILE_LIST,
        REMOVE_CONFIRM
    }

    private final Type type;
    private final UUID residentUuid;
    private final String villageId;
    private final Map<Integer, UUID> residentsBySlot = new HashMap<>();
    private final Map<Integer, BehaviorFlag> behaviorsBySlot = new HashMap<>();
    private final Map<Integer, String> profilesBySlot = new HashMap<>();
    private final Map<Integer, ResidentRole> rolesBySlot = new HashMap<>();
    private final Map<Integer, String> villagesBySlot = new HashMap<>();
    private final Map<Integer, VillageWorkZoneType> workZonesBySlot = new HashMap<>();
    private final Map<Integer, String> seatsBySlot = new HashMap<>();
    private final ResidentRole role;
    private final Inventory inventory;

    ResidentMenu(Type type, UUID residentUuid, int size, Component title) {
        this(type, residentUuid, null, null, size, title);
    }

    ResidentMenu(Type type, UUID residentUuid, ResidentRole role, int size, Component title) {
        this(type, residentUuid, null, role, size, title);
    }

    ResidentMenu(Type type, UUID residentUuid, String villageId, int size, Component title) {
        this(type, residentUuid, villageId, null, size, title);
    }

    ResidentMenu(Type type, String villageId, ResidentRole role, int size, Component title) {
        this(type, null, villageId, role, size, title);
    }

    private ResidentMenu(Type type, UUID residentUuid, String villageId, ResidentRole role, int size, Component title) {
        this.type = type;
        this.residentUuid = residentUuid;
        this.villageId = villageId;
        this.role = role;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    Type type() {
        return type;
    }

    UUID residentUuid() {
        return residentUuid;
    }

    String villageId() {
        return villageId;
    }

    Map<Integer, UUID> residentsBySlot() {
        return residentsBySlot;
    }

    Map<Integer, BehaviorFlag> behaviorsBySlot() {
        return behaviorsBySlot;
    }

    Map<Integer, String> profilesBySlot() {
        return profilesBySlot;
    }

    Map<Integer, ResidentRole> rolesBySlot() {
        return rolesBySlot;
    }

    ResidentRole role() {
        return role;
    }

    Map<Integer, String> villagesBySlot() {
        return villagesBySlot;
    }

    Map<Integer, VillageWorkZoneType> workZonesBySlot() {
        return workZonesBySlot;
    }

    Map<Integer, String> seatsBySlot() {
        return seatsBySlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
