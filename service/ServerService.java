package service;

import modele.client.*;
import modele.common.*;
import modele.server.*;
import vue.Arena;

import java.net.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;

public class ServerService {
    private static ExecutorService executor = Executors.newCachedThreadPool();
    private static Random random = new Random();
    private static Arena hostArena = null; // Arena de l'hôte

    /**
     * Crée un nouveau serveur de jeu
     */
    public static GameServer createServer() {
        GameServer gameServer = new GameServer();
        try {
            ServerSocket serverSocket = new ServerSocket(Protocol.SERVER_PORT);
            gameServer.setServeurSocket(serverSocket);
            System.out.println("✓ Serveur créé sur le port " + Protocol.SERVER_PORT);
        } catch (Exception e) {
            System.err.println("✗ Erreur lors de la création du serveur: " + e.getMessage());
            e.printStackTrace();
        }
        return gameServer;
    }

    /**
     * Démarre le serveur et commence à accepter les connexions
     */
    public static void startGameServer(GameServer gameServer, Joueur joueurHost, Arena arena) throws IOException {
        // Sauvegarder l'arena de l'hôte pour pouvoir y ajouter les joueurs
        hostArena = arena;
        
        // Ajouter l'hôte comme premier joueur avec position aléatoire
        joueurHost.setid(0); // L'hôte a toujours l'ID 0
        joueurHost.setIsHost(true);
        assignRandomPosition(joueurHost);
        
        // Créer un client fictif pour l'hôte
        Client hostClient = new Client();
        hostClient.setServer(gameServer);
        hostClient.setJoueur(joueurHost);
        gameServer.getclients().add(hostClient);
        
        // Ajouter l'hôte à son arena
        if (hostArena != null) {
            hostArena.addJoueur(joueurHost);
            hostArena.setLocalPlayerId(joueurHost.getid());
        }
        
        System.out.println("✓ Hôte ajouté: " + joueurHost.getPseudo() + " à la position (" + joueurHost.getX() + ", " + joueurHost.getY() + ")");

        executor.submit(() -> {
            try {
                int nextPlayerId = 1;
                
                while (gameServer.getclients().size() < gameServer.getNombre_joueurs()) {
                    try {
                        System.out.println("⏳ En attente de connexions... (" + gameServer.getclients().size() + "/" + gameServer.getNombre_joueurs() + ")");
                        Socket clientSocket = gameServer.getServeurSocket().accept();
                        System.out.println("✓ Nouvelle connexion reçue: " + clientSocket.getInetAddress());
                        
                        // Lire les informations du joueur
                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        String connectMessage = in.readLine();
                        
                        if (connectMessage != null && connectMessage.startsWith(Protocol.MSG_CONNECT)) {
                            String[] parts = Protocol.parseMessage(connectMessage);
                            
                            // Créer le joueur avec les infos reçues
                            Joueur nouveauJoueur = new Joueur();
                            nouveauJoueur.setid(nextPlayerId++);
                            nouveauJoueur.setPseudo(parts.length > 1 ? parts[1] : "Joueur" + nouveauJoueur.getid());
                            nouveauJoueur.setIsHost(false);
                            assignRandomPosition(nouveauJoueur);
                            
                            // Créer le client
                            Client client = new Client();
                            client.setSocket(clientSocket);
                            client.setServer(gameServer);
                            client.setJoueur(nouveauJoueur);
                            
                            // Ajouter à la liste
                            gameServer.getclients().add(client);
                            
                            // Envoyer un message de bienvenue avec l'ID et la position
                            PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
                            String welcomeMsg = Protocol.buildMessage(
                                Protocol.MSG_WELCOME,
                                String.valueOf(nouveauJoueur.getid()),
                                String.valueOf(nouveauJoueur.getX()),
                                String.valueOf(nouveauJoueur.getY())
                            );
                            out.println(welcomeMsg);
                            
                            // Envoyer la liste de tous les joueurs existants au nouveau client
                            for (Client existingClient : gameServer.getclients()) {
                                if (existingClient != client) {
                                    Joueur j = existingClient.getJoueur();
                                    String playerMsg = Protocol.buildMessage(
                                        Protocol.MSG_PLAYER_JOINED,
                                        String.valueOf(j.getid()),
                                        j.getPseudo(),
                                        String.valueOf(j.getX()),
                                        String.valueOf(j.getY())
                                    );
                                    out.println(playerMsg);
                                }
                            }
                            
                            // Ajouter le nouveau joueur à l'arena de l'hôte
                            if (hostArena != null) {
                                hostArena.addJoueur(nouveauJoueur);
                                System.out.println("✓ Joueur ajouté à l'arène hôte: " + nouveauJoueur.getPseudo());
                            }
                            
                            // Informer tous les autres clients (pas l'hôte) du nouveau joueur
                            String joinMsg = Protocol.buildMessage(
                                Protocol.MSG_PLAYER_JOINED,
                                String.valueOf(nouveauJoueur.getid()),
                                nouveauJoueur.getPseudo(),
                                String.valueOf(nouveauJoueur.getX()),
                                String.valueOf(nouveauJoueur.getY())
                            );
                            
                            for (Client existingClient : gameServer.getclients()) {
                                if (existingClient != client && existingClient.getSocket() != null) {
                                    existingClient.send(joinMsg);
                                }
                            }
                            
                            System.out.println("✓ Joueur connecté: " + nouveauJoueur.getPseudo() + " (ID: " + nouveauJoueur.getid() + ")");
                            
                            // Démarrer le thread du client
                            executor.submit(client);
                        }
                    } catch (SocketException e) {
                        System.out.println("Serveur arrêté");
                        break;
                    }
                }
                
                System.out.println("✓ Nombre maximum de joueurs atteint (" + gameServer.getNombre_joueurs() + ")");
                
            } catch (IOException e) {
                System.err.println("✗ Erreur serveur: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Connecte un client à un serveur distant
     */
    public static Client connectToServer(String serverHost, int serverPort, Joueur joueur, Arena arena) {
        try {
            System.out.println("⏳ Tentative de connexion à " + serverHost + ":" + serverPort);
            
            // Créer la socket
            Socket socket = new Socket(serverHost, serverPort);
            System.out.println("✓ Connexion établie");
            
            // Créer le client avec l'arène
            Client client = new Client(arena);
            client.setSocket(socket);
            client.setJoueur(joueur);
            
            // Envoyer le message de connexion
            String connectMsg = Protocol.buildMessage(Protocol.MSG_CONNECT, joueur.getPseudo());
            client.send(connectMsg);
            System.out.println("→ Envoi: " + connectMsg.trim());
            
            // Lire la réponse de bienvenue
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String welcomeMsg = in.readLine();
            
            if (welcomeMsg != null && welcomeMsg.startsWith(Protocol.MSG_WELCOME)) {
                String[] parts = Protocol.parseMessage(welcomeMsg);
                joueur.setid(Integer.parseInt(parts[1]));
                joueur.setX(Integer.parseInt(parts[2]));
                joueur.setY(Integer.parseInt(parts[3]));
                
                System.out.println("✓ Connecté avec succès! ID: " + joueur.getid() + " Position: (" + joueur.getX() + ", " + joueur.getY() + ")");
            }
            
            return client;
            
        } catch (IOException e) {
            System.err.println("✗ Erreur de connexion: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Assigne une position aléatoire valide à un joueur dans l'arène
     */
    private static void assignRandomPosition(Joueur joueur) {
        // Zones sûres pour ne pas apparaître hors de l'arène
        int maxX = Protocol.ARENA_WIDTH - Protocol.PLAYER_SIZE;
        int maxY = Protocol.ARENA_HEIGHT - Protocol.PLAYER_SIZE;
        
        int x = random.nextInt(maxX);
        int y = random.nextInt(maxY);
        
        joueur.setX(x);
        joueur.setY(y);
    }
}