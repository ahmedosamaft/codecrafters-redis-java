
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class Main {

    private static final int PORT = 6379;
    private static final int BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final CommandHandler commandHandler = new CommandHandler();

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
        String response = commandHandler.handle(command);
        logger.info(() -> "Response: " + response);
        client.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
    }
}
