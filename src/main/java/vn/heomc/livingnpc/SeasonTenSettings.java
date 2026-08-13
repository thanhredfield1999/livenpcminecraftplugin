package vn.heomc.livingnpc;

record SeasonTenSettings(
        boolean enabled,
        long breakfastStartTick,
        long breakfastEndTick,
        long lunchStartTick,
        long lunchEndTick,
        long dinnerStartTick,
        long dinnerEndTick,
        int demandBuffer,
        int maxBatchSize,
        int visitorQuotaPerBatch,
        boolean fallbackStoredFood) {
}
