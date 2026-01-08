package typeshi;

import java.io.*;
import java.net.*;

public class
MultiplayerServer {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;

    public MultiplayerServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Waiting for player...");
        clientSocket = serverSocket.accept();
        System.out.println("Player connected!");

        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new PrintWriter(clientSocket.getOutputStream(), true);
    }

    public void send(String msg) {
        out.println(msg);
    }

    public String receive() throws IOException {
        return in.readLine();
    }

    public void close() throws IOException {
        clientSocket.close();
        serverSocket.close();
    }

    // Simple listener for incoming messages
    public void listenForMessages() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Client: " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
