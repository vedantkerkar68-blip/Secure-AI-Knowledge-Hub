package com.sakh.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple thread-safe, in-memory fixed-window rate limiter. Windows are tracked
 * per key (e.g. client IP or user email) and expire automatically.
 */
@Service
public class RateLimitService {

    private static final int MAX_WINDOWS = 10_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, Duration windowSize) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSize.toMillis();

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.start < windowStart) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });

        if (windows.size() > MAX_WINDOWS) {
            purge(now);
        }

        return window.count <= limit;
    }

    private void purge(long now) {
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Window w = iterator.next().getValue();
            if (w.start < now - Duration.ofHours(1).toMillis()) {
                iterator.remove();
            }
        }
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start, int count) {
            this.start = start;
            this.count = count;
        }
    }
}