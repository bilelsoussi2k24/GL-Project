package security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe sliding-window rate limiter.
 *
 * <p>Entries are pruned as they are read and the map is swept periodically, so a
 * public endpoint cannot grow memory without bound by sending traffic from many
 * different addresses. Keys are deliberately short strings (an IP address, or an
 * email plus an address).</p>
 */
public final class RateLimiter {

    private final int limit;
    private final long windowMillis;
    private final int maxKeys;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicBoolean sweeping = new AtomicBoolean();
    private final AtomicLong lastSweep = new AtomicLong();

    public RateLimiter(int limit, long windowMillis, int maxKeys) {
        if (limit < 1 || windowMillis < 1 || maxKeys < 1) {
            throw new IllegalArgumentException("Rate limiter bounds must be positive");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.maxKeys = maxKeys;
    }

    /** True when the key has already used its whole allowance in the current window. */
    public boolean isLimited(String key) {
        Window window = windows.get(key);
        if (window == null) return false;
        synchronized (window) {
            prune(window, System.currentTimeMillis());
            return window.hits.size() >= limit;
        }
    }

    /** Records one event. Returns false when the key is over its limit. */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            prune(window, now);
            if (window.hits.size() >= limit) return false;
            window.hits.addLast(now);
            return true;
        }
    }

    /** Records one event without checking the limit first. */
    public void record(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            prune(window, now);
            window.hits.addLast(now);
        }
        sweepIfNeeded(now);
    }

    /** Forgets the key, e.g. after a successful sign-in. */
    public void reset(String key) {
        windows.remove(key);
    }

    private void prune(Window window, long now) {
        long cutoff = now - windowMillis;
        while (!window.hits.isEmpty() && window.hits.peekFirst() < cutoff) {
            window.hits.pollFirst();
        }
    }

    /**
     * Removes stale keys periodically and, in the worst case, caps the map size.
     * The sweep is best-effort and never blocks request handling for long.
     */
    private void sweepIfNeeded(long now) {
        long last = lastSweep.get();
        boolean due = now - last >= windowMillis || windows.size() > maxKeys;
        if (!due || !lastSweep.compareAndSet(last, now)) return;
        if (!sweeping.compareAndSet(false, true)) return;
        try {
            long cutoff = now - windowMillis;
            for (Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator(); it.hasNext(); ) {
                Window window = it.next().getValue();
                synchronized (window) {
                    prune(window, now);
                    if (window.hits.isEmpty() || window.hits.peekLast() < cutoff) it.remove();
                }
            }
            // A flood of unique addresses can still outrun the expiry; bound it hard.
            if (windows.size() > maxKeys) {
                int target = Math.max(1, maxKeys * 3 / 4);
                for (String key : windows.keySet()) {
                    if (windows.size() <= target) break;
                    windows.remove(key);
                }
            }
        } finally {
            sweeping.set(false);
        }
    }

    private static final class Window {
        private final Deque<Long> hits = new ArrayDeque<>();
    }
}
