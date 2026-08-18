package vn.heomc.livingnpc;

public record NpcTelemetryRoleProduction(String role, int amount) {
    public NpcTelemetryRoleProduction {
        role = role == null ? null : role.substring(0, Math.min(role.length(), 64));
        amount = Math.max(0, amount);
    }
}