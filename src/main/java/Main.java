
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

public class Main {

    private static final int PORT = 6379;
    private static final int BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentLinkedDeque> list_store = new ConcurrentHashMap<>();
    private static final PriorityQueue<HashMap.Entry<String, Long>> expirationQueue = new PriorityQueue<>(
            (a, b) -> Long.compare(a.getValue(), b.getValue())
    );

    public static void main(String[] args) {
        System.out.println("Server starting on port " + PORT);
        ClientHandler clientHandler = new ClientHandler(PORT);

        try {
            while (true) {
                clientHandler.selector.select();
                Iterator<SelectionKey> iterator = clientHandler.selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isAcceptable()) {
                        accept(key, clientHandler);
                    } else if (key.isReadable()) {
                        read(key);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        } finally {
            clientHandler.close();
        }
    }

    private static void accept(SelectionKey key, ClientHandler clientHandler) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel clientSocket = server.accept();
        if (clientSocket != null) {
            clientSocket.configureBlocking(false);
            clientSocket.register(clientHandler.selector, SelectionKey.OP_READ);
        }
    }

    private static void read(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        if (client.read(buffer) == -1) {
            client.close();
            return;
        }
        buffer.flip();
        List<String> command = RESPHandler.parse(buffer);
        logger.info(() -> "Command: " + command);
        String response = handleCommand(command);
        logger.info(() -> "Response: " + response);
        client.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
    }

    public static String handleCommand(List<String> command) {
        if (command.isEmpty()) {
            return "-ERR unknown command\r\n";
        }
        return switch (command.get(0).toUpperCase()) {
            case "PING" ->
                command.size() == 2 ? "+" + command.get(1) + "\r\n" : "+PONG\r\n";
            case "ECHO" ->
                command.size() == 2
                ? RESPHandler.encodeBulkString(command.get(1))
                : wrongArgs("echo");
            case "SET" -> {
                if (command.size() < 3) {
                    yield wrongArgs("set");
                }
                store.put(command.get(1), command.get(2));
                if (command.size() == 5
                        && ("EX".equalsIgnoreCase(command.get(3)) || "PX".equalsIgnoreCase(command.get(3)))) {
                    long ttl = Long.parseLong(command.get(4));
                    synchronized (expirationQueue) { // Lock the expiration queue while updating it
                        if ("EX".equalsIgnoreCase(command.get(3))) { // expire in seconds 
                            expirationQueue.add(new HashMap.SimpleEntry<>(command.get(1), System.currentTimeMillis() + ttl * 1000));
                        } else if ("PX".equalsIgnoreCase(command.get(3))) { // expire in milliseconds
                            expirationQueue.add(new HashMap.SimpleEntry<>(command.get(1), System.currentTimeMillis() + ttl));

                        }
                    }
                }

                yield "+OK\r\n";
            }
            case "GET" -> {
                if (command.size() != 2) {
                    yield wrongArgs("get");
                }
                while (!expirationQueue.isEmpty() && expirationQueue.peek().getValue() < System.currentTimeMillis()) {
                    var entry = expirationQueue.poll();
                    store.remove(entry.getKey());
                }
                String value = store.get(command.get(1));
                yield value == null ? "$-1\r\n" : RESPHandler.encodeBulkString(value);
            }
            case "RPUSH" -> {
                if (command.size() < 3) {
                    yield wrongArgs("rpush");
                }
                String list_name = command.get(1);
                list_store.putIfAbsent(list_name, new ConcurrentLinkedDeque<>());
                ConcurrentLinkedDeque<String> list = list_store.get(list_name);
                for (int i = 2; i < command.size(); i++) {
                    list.addLast(command.get(i));
                }
                yield ":" + list.size() + "\r\n"; // ConcurrentLinkedDeque.size() is O(N)
            }
            case "LRANGE" -> {
                if (command.size() != 4) {
                    yield wrongArgs("lrange");
                }
                String list_name = command.get(1);
                int start = Integer.parseInt(command.get(2));
                int stop = Integer.parseInt(command.get(3));
                ConcurrentLinkedDeque<String> list = list_store.get(list_name);
                if (list == null) {
                    yield "*0\r\n";
                }
                int size = list.size();
                if (start < 0) {
                    start = size + start;
                }
                if (stop < 0) {
                    stop = size + stop;
                }
                start = Math.max(start, 0);
                stop = Math.min(stop, size - 1);
                if (start > stop) {
                    yield "*0\r\n";
                }
                List<String> sb = new ArrayList<>();
                int index = 0;
                for (String item : list) {
                    if (index >= start && index <= stop) {
                        sb.add(item);
                    }
                    index++;
                }
                yield RESPHandler.encodeArray(sb);
            }
            default ->
                "-ERR unknown command\r\n";
        };
    }

    private static String wrongArgs(String name) {
        return "-ERR wrong number of arguments for '" + name + "' command\r\n";
    }
}
