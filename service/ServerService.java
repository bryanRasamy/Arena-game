package service;

import modele.client.*;
import modele.common.*;
import modele.server.*;
import java.net.*;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;

public class ServerService {
    private static ExecutorService executor=Executors.newCachedThreadPool();

    public static GameServer createServer(){
        GameServer gameServer = new GameServer();
        try {
            ServerSocket serverSocket = new ServerSocket(Protocol.SERVER_PORT);
            gameServer.setServeurSocket(serverSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return gameServer;
    }

    public static void startGameServer(GameServer gameServer,Joueur joueur) throws IOException {
        boolean isRunning=true;

        executor.submit(() -> {
            try {
                while (isRunning && gameServer.getclients().size() <= gameServer.getNombre_joueurs()) {
                    try {
                        Socket clientSocket = gameServer.getServeurSocket().accept();
                        
                        Client client = new Client();
                        client.setSocket(clientSocket);
                        client.setServer(gameServer);
                        client.setJoueur(joueur);

                        gameServer.getclients().add(client);

                        executor.submit(client);
                    } catch (SocketException e) {
                        if (!isRunning) break;
                    }
                }
            } catch (IOException e) {
                System.err.println("✗ Erreur serveur: " + e.getMessage());
            }
        });
        
    }
}
