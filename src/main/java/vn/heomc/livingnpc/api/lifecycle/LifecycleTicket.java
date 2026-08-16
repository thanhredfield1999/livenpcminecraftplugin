package vn.heomc.livingnpc.api.lifecycle;

import java.util.Objects;

/** Handle immutable, sống ngắn hạn; generation không được tái sử dụng trong cùng scope. */
public record LifecycleTicket(String ownerId, long generation) {
    public LifecycleTicket {
        Objects.requireNonNull(ownerId, "ownerId");
        if (ownerId.isBlank()) throw new IllegalArgumentException("ownerId must not be blank");
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
    }
}
