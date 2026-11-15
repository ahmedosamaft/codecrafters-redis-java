
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class ClientHandler {

    public ServerSocketChannel serverSocket;
    public Selector selector;

    public ClientHandler(int port) {
        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new java.net.InetSocketAddress(port));
            serverSocket.setOption(java.net.StandardSocketOptions.SO_REUSEADDR, true);
            selector = Selector.open();
            serverSocket.configureBlocking(false);
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (selector != null) {
                selector.close();
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
