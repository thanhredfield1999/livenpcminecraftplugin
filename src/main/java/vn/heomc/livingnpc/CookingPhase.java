package vn.heomc.livingnpc;

enum CookingPhase {
    RESERVED,
    LOADED,
    COOKING,
    COOKED,
    COMMITTED,
    ROLLED_BACK;

    boolean active() {
        return this != COMMITTED && this != ROLLED_BACK;
    }

    boolean canTransitionTo(CookingPhase next) {
        if (next == null || next == this || !active()) return false;
        if (next == ROLLED_BACK) return true;
        return next.ordinal() == ordinal() + 1;
    }
}
