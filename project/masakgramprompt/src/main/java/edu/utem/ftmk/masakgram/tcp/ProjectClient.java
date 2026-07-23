package edu.utem.ftmk.masakgram.tcp;

import java.io.*;
import java.net.Socket;

public class ProjectClient {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public ProjectClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String sendRequest(String request) {
        if (writer != null) {
            writer.println(request);
            try {
                return reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "ERROR:Connection Lost";
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}