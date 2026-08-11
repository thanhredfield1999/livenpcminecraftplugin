package vn.heomc.livingnpc;

import java.util.EnumSet;
import java.util.Locale;
import org.bukkit.Material;

enum BehaviorFlag {
    MASTER("Trí tuệ NPC", Material.REDSTONE_TORCH, true, "Cho phép NPC thực hiện các hành vi đã bật."),
    HARVEST("Thu hoạch", Material.IRON_HOE, false, "Thu hoạch cây chín. Hành động này thay đổi block."),
    PLANT("Trồng lúa mì", Material.WHEAT_SEEDS, false, "Trồng lúa mì trên đất trống. Hành động này thay đổi block."),
    SELL_INVENTORY("Bán hàng trong kho", Material.EMERALD, false, "Bán hàng có giá trong kho tổng khi hết ca."),
    WANDER("Đi dạo", Material.LEATHER_BOOTS, true, "Đi lại ngắn quanh nhà hoặc khu làm việc."),
    WATCH_PLAYERS("Quan sát người chơi", Material.SPYGLASS, true, "Quay đầu nhìn người chơi ở gần."),
    LOOK_AROUND("Nhìn xung quanh", Material.COMPASS, true, "Thỉnh thoảng quan sát xung quanh khi rảnh."),
    AVOID_MONSTERS("Tránh quái vật", Material.SHIELD, true, "Dừng công việc và về nhà khi có quái vật."),
    FOLLOW_SCHEDULE("Theo lịch và về nhà", Material.CLOCK, true, "Làm việc theo lịch; sinh hoạt quanh nhà ngoài ca."),
    REST("Nghỉ ngắn", Material.CAMPFIRE, true, "Nghỉ ngắn giữa các hành động.");

    private final String displayName;
    private final Material icon;
    private final boolean enabledByDefault;
    private final String description;

    BehaviorFlag(String displayName, Material icon, boolean enabledByDefault, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.enabledByDefault = enabledByDefault;
        this.description = description;
    }

    String displayName() {
        return displayName;
    }

    Material icon() {
        return icon;
    }

    String description() {
        return description;
    }

    String storageKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static EnumSet<BehaviorFlag> safeDefaults() {
        EnumSet<BehaviorFlag> defaults = EnumSet.noneOf(BehaviorFlag.class);
        for (BehaviorFlag behavior : values()) {
            if (behavior.enabledByDefault) {
                defaults.add(behavior);
            }
        }
        return defaults;
    }
}
