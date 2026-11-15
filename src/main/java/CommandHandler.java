import java.util.List;

public class CommandHandler {

    private static final String UNKNOWN_COMMAND = "-ERR unknown command\r\n";
    private static final String NOT_INTEGER = "-ERR value is not an integer or out of range\r\n";
    private static final String SYNTAX_ERROR = "-ERR syntax error\r\n";

    private final KeyValueStore store = new KeyValueStore();
    private final ListStore listStore = new ListStore();

    public String handle(List<String> command) {
        if (command.isEmpty()) {
            return UNKNOWN_COMMAND;
        }
        try {
            return switch (command.get(0).toUpperCase()) {
                case "PING" -> ping(command);
                case "ECHO" -> command.size() == 2 ? RESPHandler.encodeBulkString(command.get(1)) : wrongArgs("echo");
                case "SET" -> set(command);
                case "GET" -> get(command);
                case "RPUSH" -> command.size() < 3
                        ? wrongArgs("rpush")
                        : RESPHandler.encodeInteger(listStore.pushRight(command.get(1), command.subList(2, command.size())));
                case "LPUSH" -> command.size() < 3
                        ? wrongArgs("lpush")
                        : RESPHandler.encodeInteger(listStore.pushLeft(command.get(1), command.subList(2, command.size())));
                case "LRANGE" -> lrange(command);
                case "LLEN" -> command.size() == 2 ? RESPHandler.encodeInteger(listStore.length(command.get(1))) : wrongArgs("llen");
                case "LPOP" -> lpop(command);
                default -> UNKNOWN_COMMAND;
            };
        } catch (NumberFormatException e) {
            return NOT_INTEGER;
        }
    }

    private static String ping(List<String> command) {
        return command.size() == 2 ? "+" + command.get(1) + "\r\n" : "+PONG\r\n";
    }

    private String set(List<String> command) {
        int size = command.size();
        if (size < 3) {
            return wrongArgs("set");
        }
        if (size == 3) {
            store.set(command.get(1), command.get(2));
            return "+OK\r\n";
        }
        if (size != 5) {
            return SYNTAX_ERROR;
        }
        long ttl = Long.parseLong(command.get(4));
        if (ttl <= 0) {
            return "-ERR invalid expire time in 'set' command\r\n";
        }
        long ttlMillis;
        switch (command.get(3).toUpperCase()) {
            case "EX" -> ttlMillis = ttl > Long.MAX_VALUE / 1000 ? Long.MAX_VALUE : ttl * 1000;
            case "PX" -> ttlMillis = ttl;
            default -> {
                return SYNTAX_ERROR;
            }
        }
        store.set(command.get(1), command.get(2), ttlMillis);
        return "+OK\r\n";
    }

    private String get(List<String> command) {
        if (command.size() != 2) {
            return wrongArgs("get");
        }
        String value = store.get(command.get(1));
        return value == null ? "$-1\r\n" : RESPHandler.encodeBulkString(value);
    }

    private String lrange(List<String> command) {
        if (command.size() != 4) {
            return wrongArgs("lrange");
        }
        int start = Integer.parseInt(command.get(2));
        int stop = Integer.parseInt(command.get(3));
        return RESPHandler.encodeArray(listStore.range(command.get(1), start, stop));
    }

    private static String wrongArgs(String name) {
        return "-ERR wrong number of arguments for '" + name + "' command\r\n";
    }
    
    private String lpop(List<String> command) {
        if (command.size() != 2 && command.size() != 3) {
            return wrongArgs("lpop");
        }
        int count = 1;
        if (command.size() == 3) {
            count = Integer.parseInt(command.get(2));
            if (count <= 0) {
                return "-ERR invalid count in 'lpop' command\r\n";
            }
        }
        String value = listStore.popLeft(command.get(1), count);
        return value == null ? "$-1\r\n" : RESPHandler.encodeBulkString(value);
    }
}
