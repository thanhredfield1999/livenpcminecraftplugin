package vn.heomc.livingnpc;

public record NpcTelemetryInventoryItem(String item, int amount) {
    public NpcTelemetryInventoryItem {
        item = item == null ? null : item.substring(0, Math.min(item.length(), 96));
        amount = Math.max(0, amount);
    }
}