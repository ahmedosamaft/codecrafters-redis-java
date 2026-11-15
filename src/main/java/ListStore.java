import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Store of named lists backed by {@link ArrayDeque} (O(1) push and size).
 * Not thread-safe: intended to be used from the single event-loop thread.
 */
public class ListStore {

    private final Map<String, ArrayDeque<String>> lists = new HashMap<>();

    /** Appends values in order and returns the new list length. */
    public int pushRight(String key, List<String> values) {
        ArrayDeque<String> list = lists.computeIfAbsent(key, k -> new ArrayDeque<>());
        list.addAll(values);
        return list.size();
    }

    /** Prepends values one by one (so the last value ends up first) and returns the new list length. */
    public int pushLeft(String key, List<String> values) {
        ArrayDeque<String> list = lists.computeIfAbsent(key, k -> new ArrayDeque<>());
        for (String value : values) {
            list.addFirst(value);
        }
        return list.size();
    }

    public int length(String key) {
        ArrayDeque<String> list = lists.get(key);
        return list == null ? 0 : list.size();
    }

    /** Returns the inclusive range [start, stop]; negative indexes count from the end. */
    public List<String> range(String key, int start, int stop) {
        ArrayDeque<String> list = lists.get(key);
        if (list == null) {
            return List.of();
        }
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
