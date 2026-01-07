package typeshi;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class MultiplayerClient {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public MultiplayerClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    // Send message to host
    public void send(String msg) {
        out.println(msg);
    }

    // Listen for messages asynchronously
    public void listenForMessages(Consumer<String> onMessage) {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    onMessage.accept(line); // pass message to handler
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public String receive() throws IOException {
        return in.readLine();
    }

    // Close connection
    public void close() throws IOException {
        socket.close();
    }
}
