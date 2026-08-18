package vn.heomc.livingnpc;

import java.time.Instant;

public record NpcTelemetryActivity(String role, String action, String item, int amount, Instant createdAt) {
    public NpcTelemetryActivity(String role, String action, String item, int amount) {
        this(role, action, item, amount, null);
    }

    public NpcTelemetryActivity {
        role = role == null ? null : role.substring(0, Math.min(role.length(), 64));
        action = action == null ? null : action.substring(0, Math.min(action.length(), 96));
        item = item == null ? null : item.substring(0, Math.min(item.length(), 96));
        amount = Math.max(0, amount);
    }
}