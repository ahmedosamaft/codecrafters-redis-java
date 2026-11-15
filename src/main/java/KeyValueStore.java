import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * String key/value store with per-key expiry. Expired keys are evicted lazily on access.
 * Thread-safe: backed by a {@link ConcurrentHashMap}.
 */
public class KeyValueStore {

    private record Entry(String value, long expiresAtMillis) {

        static final long NO_EXPIRY = Long.MAX_VALUE;

        boolean isExpired(long now) {
            return now >= expiresAtMillis;
        }
    }

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    public void set(String key, String value) {
        entries.put(key, new Entry(value, Entry.NO_EXPIRY));
    }

    public void set(String key, String value, long ttlMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = ttlMillis > Long.MAX_VALUE - now ? Entry.NO_EXPIRY : now + ttlMillis;
        entries.put(key, new Entry(value, expiresAt));
    }

    public String get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(System.currentTimeMillis())) {
            // Conditional remove so a concurrent SET of the same key is not discarded
            entries.remove(key, entry);
            return null;
        }
        return entry.value();
    }
}
