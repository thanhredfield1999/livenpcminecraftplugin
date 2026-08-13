package vn.heomc.livingnpc;

record SeasonalEconomyModifiers(
        int stockTargetPercent,
        int exportDemandPercent,
        int laborPriorityPercent) {

    int adjustStockTarget(int baseTarget) {
        return adjust(baseTarget, stockTargetPercent);
    }

    int adjustExportDemand(int baseDemand) {
        return adjust(baseDemand, exportDemandPercent);
    }

    int adjustLaborPriority(int basePriority) {
        return adjust(basePriority, laborPriorityPercent);
    }

    private static int adjust(int baseValue, int percent) {
        long adjusted = Math.max(0L, baseValue) * Math.max(0L, percent) / 100L;
        return (int) Math.min(Integer.MAX_VALUE, adjusted);
    }
}
