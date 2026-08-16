package vn.heomc.livingnpc.api.lifecycle;

import java.util.Optional;

/**
 * Scope generation cho callback bất đồng bộ cần quay lại main thread.
 *
 * <p>Mỗi lần {@link #open(String)} tạo generation mới và làm ticket trước của cùng owner stale.
 * Callback được kiểm tra cả lúc submit lẫn lúc scheduler thực thi. Scope đóng không thể mở lại.
 */
public interface LifecycleScope extends AutoCloseable {
    Optional<LifecycleTicket> open(String ownerId);

    void invalidate(String ownerId);

    boolean isCurrent(LifecycleTicket ticket);

    DispatchResult dispatch(LifecycleTicket ticket, Runnable action);

    @Override
    void close();
}
