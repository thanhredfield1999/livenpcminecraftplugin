package vn.heomc.livingnpc;

record RoleProgress(long experience) {
    static final int MAX_LEVEL = 100;

    RoleProgress {
        experience = Math.max(0L, experience);
    }

    int level() {
        return Math.min(MAX_LEVEL, 1 + (int) Math.sqrt(experience / 25.0));
    }

    long experienceForNextLevel() {
        if (level() >= MAX_LEVEL) {
            return experience;
        }
        long next = level();
        return next * next * 25L;
    }

    double speedMultiplier() {
        return 1.0 + ((level() - 1) / 99.0) * 0.2;
    }

    RoleProgress add(long amount) {
        return amount <= 0L ? this : new RoleProgress(Math.addExact(experience, amount));
    }
}
