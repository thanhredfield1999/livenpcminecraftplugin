package vn.heomc.livingnpc;

import java.util.Map;

record VisitorDemandSnapshot(String visitId, long walletMinor, Map<String, Integer> demand) {
    VisitorDemandSnapshot {
        demand = Map.copyOf(demand);
    }

    boolean empty() {
        return demand.isEmpty();
    }
}
