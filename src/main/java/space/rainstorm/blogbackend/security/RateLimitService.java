package space.rainstorm.blogbackend.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, WindowInfo> windows = new ConcurrentHashMap<>();

    public boolean isAllowed(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();

        WindowInfo info = windows.compute(key, (k, old) -> {
            if (old == null || now > old.windowStart + windowMillis) {
                WindowInfo created = new WindowInfo();
                created.windowStart = now;
                created.count = 1;
                return created;
            }

            old.count++;
            return old;
        });

        return info.count <= limit;
    }

    public long getRemainingSeconds(String key, long windowMillis) {
        WindowInfo info = windows.get(key);
        if (info == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long remain = (info.windowStart + windowMillis) - now;
        return Math.max((remain + 999) / 1000, 0);
    }

    private static class WindowInfo {
        long windowStart;
        int count;
    }
}