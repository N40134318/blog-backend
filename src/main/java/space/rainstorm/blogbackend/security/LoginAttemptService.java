package space.rainstorm.blogbackend.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_MILLIS = 10 * 60 * 1000L;

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (info.lockUntil > now) {
            return true;
        }

        if (info.lockUntil > 0 && info.lockUntil <= now) {
            attempts.remove(key);
        }

        return false;
    }

    public void recordFail(String key) {
        long now = System.currentTimeMillis();

        attempts.compute(key, (k, old) -> {
            if (old == null) {
                AttemptInfo info = new AttemptInfo();
                info.failCount = 1;
                info.lockUntil = 0;
                return info;
            }

            old.failCount++;

            if (old.failCount >= MAX_FAIL_COUNT) {
                old.lockUntil = now + LOCK_MILLIS;
            }

            return old;
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    public long getRemainingLockSeconds(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null || info.lockUntil <= 0) {
            return 0;
        }

        long remain = info.lockUntil - System.currentTimeMillis();
        return Math.max(remain / 1000, 0);
    }

    private static class AttemptInfo {
        int failCount;
        long lockUntil;
    }
}