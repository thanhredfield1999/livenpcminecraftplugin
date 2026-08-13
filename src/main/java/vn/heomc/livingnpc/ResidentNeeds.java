package vn.heomc.livingnpc;

import java.util.UUID;

final class ResidentNeeds {
    private final UUID npcUuid;
    private String world;
    private int hunger;
    private int thirst;
    private long managedTicks;
    private long hungerDecayTicks;
    private long thirstDecayTicks;

    ResidentNeeds(UUID npcUuid, String world, int hunger, int thirst) {
        this(npcUuid, world, hunger, thirst, 0L, 0L, 0L);
    }

    ResidentNeeds(
            UUID npcUuid,
            String world,
            int hunger,
            int thirst,
            long managedTicks,
            long hungerDecayTicks,
            long thirstDecayTicks) {
        this.npcUuid = npcUuid;
        this.world = world == null ? "" : world;
        this.hunger = Math.clamp(hunger, 0, 100);
        this.thirst = Math.clamp(thirst, 0, 100);
        this.managedTicks = Math.max(0L, managedTicks);
        this.hungerDecayTicks = Math.max(0L, hungerDecayTicks);
        this.thirstDecayTicks = Math.max(0L, thirstDecayTicks);
    }

    boolean advance(long deltaTicks, String currentWorld, NeedsSettings settings) {
        long boundedDelta = Math.clamp(deltaTicks, 0L, settings.maxManagedDeltaTicks());
        if (boundedDelta == 0L) return false;
        world = currentWorld == null ? world : currentWorld;
        managedTicks += boundedDelta;
        hungerDecayTicks += boundedDelta;
        thirstDecayTicks += boundedDelta;

        int hungerLoss = (int) Math.min(hunger, hungerDecayTicks / settings.hungerDecayTicksPerPoint());
        int thirstLoss = (int) Math.min(thirst, thirstDecayTicks / settings.thirstDecayTicksPerPoint());
        hunger -= hungerLoss;
        thirst -= thirstLoss;
        hungerDecayTicks = hunger == 0 ? 0L : hungerDecayTicks % settings.hungerDecayTicksPerPoint();
        thirstDecayTicks = thirst == 0 ? 0L : thirstDecayTicks % settings.thirstDecayTicksPerPoint();
        return true;
    }

    UUID npcUuid() {
        return npcUuid;
    }

    String world() {
        return world;
    }

    int hunger() {
        return hunger;
    }

    int thirst() {
        return thirst;
    }

    long managedTicks() {
        return managedTicks;
    }

    long hungerDecayTicks() {
        return hungerDecayTicks;
    }

    long thirstDecayTicks() {
        return thirstDecayTicks;
    }
}
