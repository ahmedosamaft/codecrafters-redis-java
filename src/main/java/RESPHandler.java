
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class RESPHandler {

    public static String encodeBulkString(String str) {
        return "$" + str.length() + "\r\n" + str + "\r\n";
    }

    public static String encodeArray(List<String> command) {
        StringBuilder string = new StringBuilder();

        int length = command.size();
        string.append('*').append(length).append("\r\n");
        for (String word : command) {
            int len = word.length();
            string.append('$').append(len).append("\r\n").append(word).append("\r\n");
        }

        return string.toString();
    }

    public static List<String> parse(ByteBuffer buffer) {
        List<String> result = new ArrayList<>();

        char prefix = (char) buffer.get();

        if (prefix == '+') {
            result.add(readSimple(buffer));
            return result;
        }
        if (prefix != '*') {
            throw new RuntimeException("Invalid RESP: * or + expected");
        }

        int arg = readArg(buffer);

        for (int i = 0; i < arg; i++) {
            if ((char) buffer.get() != '$') {
                throw new RuntimeException("Expected Bulk String");
            }

            int size = readArg(buffer);
            byte[] data = new byte[size];
            buffer.get(data);
            result.add(new String(data));

            buffer.get();
            buffer.get();
        }
        return result;
    }

    public static int readArg(ByteBuffer buffer) {
        // reads an integer argument like '3\r\n' or '5\r\n'
        StringBuilder sb = new StringBuilder();

        while (true) {
            char c = (char) buffer.get();
            if (c == '\r') {
                buffer.get();
                break;
            }
            sb.append(c);
        }

        return Integer.parseInt(sb.toString());
    }

    public static String readSimple(ByteBuffer buffer) {
        // simple string like '+OK\r\n'
        StringBuilder sb = new StringBuilder();

        while (true) {
            char c = (char) buffer.get();
            if (c == '\r') {
                buffer.get();
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
