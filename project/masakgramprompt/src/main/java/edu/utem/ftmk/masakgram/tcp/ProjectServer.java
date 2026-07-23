package edu.utem.ftmk.masakgram.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ProjectServer {
    private static final int PORT = 8888;

    public static void main(String[] args) {
        System.out.println("Starting Masakgram Server on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                
                // Spawn a handler thread for each client
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}