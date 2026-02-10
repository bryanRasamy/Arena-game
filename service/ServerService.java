package service;

import modele.client.*;
import modele.common.*;
import modele.server.*;
import java.net.*;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.io.*;
import java.nio.charset.StandardCharsets;

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

    public static void startGameServer(GameServer gameServer, Consumer<Joueur> onPlayerJoined){
        executor.submit(() -> {
            try {
                while (gameServer.getclients().size() < gameServer.getNombre_joueurs()) {
                    try {
                        Socket clientSocket = gameServer.getServeurSocket().accept();
                        System.out.println("Nouveau client connecte: " + clientSocket.getInetAddress());

                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                        PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);

                        // Lire le message CONNECT du nouveau client
                        String connectMsg = in.readLine();
                        if (connectMsg == null) {
                            clientSocket.close();
                            continue;
                        }

                        String[] parts = Protocol.parseMessage(connectMsg);
                        if (!parts[0].equals(Protocol.MSG_CONNECT) || parts.length < 5) {
                            clientSocket.close();
                            continue;
                        }

                        // Creer le joueur a partir du message CONNECT
                        Joueur newJoueur = new Joueur();
                        newJoueur.setid(Integer.parseInt(parts[1]));
                        newJoueur.setPseudo(parts[2]);
                        newJoueur.setX(Integer.parseInt(parts[3]));
                        newJoueur.setY(Integer.parseInt(parts[4]));

                        // Envoyer les joueurs existants au nouveau client
                        Vector<Client> existingClients = gameServer.getclients();
                        synchronized (existingClients) {
                            for (Client existing : existingClients) {
                                Joueur j = existing.getJoueur();
                                out.println(Protocol.MSG_GAME_STATE + Protocol.SEPARATOR 
                                    + j.getid() + Protocol.SEPARATOR 
                                    + j.getPseudo() + Protocol.SEPARATOR 
                                    + j.getX() + Protocol.SEPARATOR 
                                    + j.getY());
                            }
                        }
                        out.println(Protocol.MSG_STATE_END);

                        // Diffuser JOINED a tous les clients distants existants
                        String joinedMsg = Protocol.MSG_PLAYER_JOINED + Protocol.SEPARATOR 
                            + newJoueur.getid() + Protocol.SEPARATOR 
                            + newJoueur.getPseudo() + Protocol.SEPARATOR 
                            + newJoueur.getX() + Protocol.SEPARATOR 
                            + newJoueur.getY();
                        synchronized (existingClients) {
                            for (Client existing : existingClients) {
                                if (existing.getSocket() != null) {
                                    existing.send(joinedMsg);
                                }
                            }
                        }

                        // Creer le Client et l'ajouter au serveur
                        Client client = new Client();
                        client.setSocket(clientSocket);
                        client.setServer(gameServer);
                        client.setJoueur(newJoueur);
                        gameServer.getclients().add(client);

                        // Notifier le callback UI
                        if (onPlayerJoined != null) {
                            onPlayerJoined.accept(newJoueur);
                        }

                        // Demarrer le thread du client handler
                        executor.submit(client);

                    } catch (SocketException e) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur serveur: " + e.getMessage());
            }
        });
    }

    public static void connectToServer(Joueur localJoueur, Consumer<Joueur> onPlayerReceived){
        executor.submit(() -> {
            try {
                Socket socket = new Socket(Protocol.DEFAULT_SERVER_HOST, Protocol.SERVER_PORT);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                // Envoyer le message CONNECT
                out.println(Protocol.MSG_CONNECT + Protocol.SEPARATOR 
                    + localJoueur.getid() + Protocol.SEPARATOR 
                    + localJoueur.getPseudo() + Protocol.SEPARATOR 
                    + localJoueur.getX() + Protocol.SEPARATOR 
                    + localJoueur.getY());

                // Recevoir les joueurs existants (messages STATE jusqu'a STATE_END)
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equals(Protocol.MSG_STATE_END)) break;

                    String[] parts = Protocol.parseMessage(line);
                    if (parts[0].equals(Protocol.MSG_GAME_STATE) && parts.length >= 5) {
                        Joueur existingJoueur = new Joueur();
                        existingJoueur.setid(Integer.parseInt(parts[1]));
                        existingJoueur.setPseudo(parts[2]);
                        existingJoueur.setX(Integer.parseInt(parts[3]));
                        existingJoueur.setY(Integer.parseInt(parts[4]));

                        if (onPlayerReceived != null) {
                            onPlayerReceived.accept(existingJoueur);
                        }
                    }
                }

                System.out.println("Connecte au serveur avec succes!");

                // Continuer a ecouter les nouveaux messages (JOINED, UPDATE, etc.)
                while ((line = in.readLine()) != null) {
                    String[] parts = Protocol.parseMessage(line);
                    if (parts.length >= 5) {
                        Joueur j = new Joueur();
                        j.setid(Integer.parseInt(parts[1]));
                        j.setPseudo(parts[2]);
                        j.setX(Integer.parseInt(parts[3]));
                        j.setY(Integer.parseInt(parts[4]));

                        if (parts[0].equals(Protocol.MSG_PLAYER_JOINED)) {
                            if (onPlayerReceived != null) {
                                onPlayerReceived.accept(j);
                            }
                        }
                    }
                }

            } catch (IOException e) {
                System.err.println("Erreur de connexion au serveur " + Protocol.DEFAULT_SERVER_HOST + ":" + Protocol.SERVER_PORT + " - " + e.getMessage());
            }
        });
    }
}
