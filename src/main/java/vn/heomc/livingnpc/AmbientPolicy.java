package vn.heomc.livingnpc;

final class AmbientPolicy {
    private AmbientPolicy() {
    }

    static AmbientAction choose(
            int roll,
            boolean canWatchPlayer,
            boolean canWander,
            boolean canLookAround,
            boolean canRest) {
        if (canWatchPlayer && roll < 20) {
            return AmbientAction.WATCH_PLAYER;
        }
        if (canWander && roll < 45) {
            return AmbientAction.WANDER;
        }
        if (canLookAround && roll < 70) {
            return AmbientAction.LOOK_AROUND;
        }
        if (canRest) {
            return AmbientAction.REST;
        }
        if (canLookAround) {
            return AmbientAction.LOOK_AROUND;
        }
        if (canWander) {
            return AmbientAction.WANDER;
        }
        if (canWatchPlayer) {
            return AmbientAction.WATCH_PLAYER;
        }
        return null;
    }
}
