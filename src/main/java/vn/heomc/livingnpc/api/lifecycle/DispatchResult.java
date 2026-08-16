package vn.heomc.livingnpc.api.lifecycle;

/** Kết quả ổn định khi đưa một callback hợp lệ vào main-thread scheduler của core. */
public enum DispatchResult {
    SCHEDULED,
    STALE,
    SHUTTING_DOWN,
    SCHEDULER_REJECTED
}
