package vn.heomc.livingnpc;

import java.util.Map;

record VisitorPurchaseResult(Map<String, Integer> purchased, long spentMinor, long remainingWalletMinor) {
    VisitorPurchaseResult {
        purchased = Map.copyOf(purchased);
    }

    static VisitorPurchaseResult empty(long walletMinor) {
        return new VisitorPurchaseResult(Map.of(), 0L, walletMinor);
    }
}
