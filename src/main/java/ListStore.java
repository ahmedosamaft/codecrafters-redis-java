import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Store of named lists backed by {@link ArrayDeque} (O(1) push and size).
 * Thread-safe: lists live in a {@link ConcurrentHashMap} and each list is guarded by its own monitor,
 * so operations on different lists do not contend.
 */
public class ListStore {

    private final ConcurrentMap<String, ArrayDeque<String>> lists = new ConcurrentHashMap<>();

    /** Appends values in order and returns the new list length. */
    public int pushRight(String key, List<String> values) {
        ArrayDeque<String> list = lists.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (list) {
            list.addAll(values);
            return list.size();
        }
    }

    /** Prepends values one by one (so the last value ends up first) and returns the new list length. */
    public int pushLeft(String key, List<String> values) {
        ArrayDeque<String> list = lists.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (list) {
            for (String value : values) {
                list.addFirst(value);
            }
            return list.size();
        }
    }

    public int length(String key) {
        ArrayDeque<String> list = lists.get(key);
        if (list == null) {
            return 0;
        }
        synchronized (list) {
            return list.size();
        }
    }

    /** Returns the inclusive range [start, stop]; negative indexes count from the end. */
    public List<String> range(String key, int start, int stop) {
        ArrayDeque<String> list = lists.get(key);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            int size = list.size();
            start = Math.max(start < 0 ? size + start : start, 0);
            stop = Math.min(stop < 0 ? size + stop : stop, size - 1);
            if (start > stop) {
                return List.of();
            }

            List<String> result = new ArrayList<>(stop - start + 1);
            int index = 0;
            for (String item : list) {
                if (index > stop) {
                    break;
                }
                if (index >= start) {
                    result.add(item);
                }
                index++;
            }
            return result;
        }
    }

    public String popLeft(String key, int count) {
        ArrayDeque<String> list = lists.get(key);
        if (list == null) {
            return null;
        }
        synchronized (list) {
            String value = null;
            for (int i = 0; i < count; i++) {
                String v = list.pollFirst();
                if (v == null) {
                    break;
                }
                value = v;
            }
            return value;
        }
    }
}
