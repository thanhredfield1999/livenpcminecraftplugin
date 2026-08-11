package vn.heomc.livingnpc;

record SaleResult(int itemCount, long totalMinor, String transactionId) {
    static final SaleResult EMPTY = new SaleResult(0, 0L, "");
}
