package vn.heomc.livingnpc;

import java.util.UUID;

record MerchantStall(UUID merchantUuid, StoredLocation sellerPoint, StoredLocation buyerPoint) {
    boolean complete() {
        return sellerPoint != null && buyerPoint != null
                && sellerPoint.world().equals(buyerPoint.world());
    }

    MerchantStall withSellerPoint(StoredLocation point) {
        return new MerchantStall(merchantUuid, point, buyerPoint);
    }

    MerchantStall withBuyerPoint(StoredLocation point) {
        return new MerchantStall(merchantUuid, sellerPoint, point);
    }
}
