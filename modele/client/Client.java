package modele.client;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import modele.common.Protocol;
import modele.server.*;
import vue.Arena;

public class Client implements Runnable{
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private GameServer server;
    private Joueur joueur;
    private Arena arena;
    private boolean running;
    private Runnable onDisconnected = null;
    private volatile boolean disconnectHandled = false;
    
    public Client(Arena arena) {
        this.arena = arena;
        this.running = true;
    }

    public Client() {
        this.running = true;
    }

    /*Setters */
    public void setSocket(Socket socket) {
        this.socket = socket;
        try {
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("✗ Erreur lors de la création des flux: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void setServer(GameServer server) {
        this.server = server;
    }
    
    public void setJoueur(Joueur joueur) {
        this.joueur = joueur;
    }

    /*Getters */
    public Joueur getJoueur() {
        return joueur;
    }

    public Socket getSocket() {
        return socket;
    }

    public GameServer getServer() {
        return server;
    }

    public void setOnDisconnected(Runnable callback) {
        this.onDisconnected = callback;
    }

    @Override
    public void run() {
        try {
            String message;
            
            while (running && (message = in.readLine()) != null) {
                System.out.println("← Reçu: " + message.trim());
                
                // Si on a une arène (côté client), gérer les messages
                if (arena != null) {
                    handleMessage(message);
                } else {
                    // Sinon (côté serveur), traiter et broadcaster
                    if (server != null) {
                        handleServerMessage(message);
                    }
                }
            }
            
        } catch (IOException e) {
            if (running) {
                System.err.println("✗ Connexion perdue");
            }
        } finally {
            System.out.println("Arrêt du gestionnaire réseau");
            
            // Côté serveur : notifier les autres que ce joueur est parti
            if (server != null && joueur != null) {
                String leftMsg = Protocol.buildMessage(Protocol.MSG_PLAYER_LEFT, String.valueOf(joueur.getid()));
                for (Client c : new java.util.ArrayList<>(server.getclients())) {
                    if (c != this && c.getSocket() != null) {
                        c.send(leftMsg);
                    }
                }
                // Retirer de l'arène de l'hôte
                if (hostArena != null) {
                    hostArena.removeJoueur(joueur.getid());
                }
                System.out.println("✓ Joueur déconnecté: " + joueur.getPseudo() + " (ID: " + joueur.getid() + ")");
            }
            
            // Côté client : déclencher le callback de déconnexion
            triggerDisconnect();
            
            close();
        }
    }

    /*Traite les messages reçus du serveur (côté client)*/
    private void handleMessage(String message) {
        String[] parts = Protocol.parseMessage(message);
        
        if (parts.length == 0) return;
        
        String messageType = parts[0];
        
        switch (messageType) {
            case Protocol.MSG_WELCOME:
                // Message de bienvenue déjà traité dans connectToServer
                break;
                
            case Protocol.MSG_PLAYER_JOINED:
                // Format: JOINED|id|pseudo|x|y
                if (parts.length >= 5) {
                    Joueur nouveauJoueur = new Joueur();
                    nouveauJoueur.setid(Integer.parseInt(parts[1]));
                    nouveauJoueur.setPseudo(parts[2]);
                    nouveauJoueur.setX(Integer.parseInt(parts[3]));
                    nouveauJoueur.setY(Integer.parseInt(parts[4]));
                    nouveauJoueur.setIsHost(false);
                    
                    arena.addJoueur(nouveauJoueur);
                    System.out.println("✓ Nouveau joueur ajouté: " + nouveauJoueur.getPseudo());
                }
                break;
                
            case Protocol.MSG_MOVE:
                // Format: MOVE|id|x|y
                if (parts.length >= 4) {
                    int moveId = Integer.parseInt(parts[1]);
                    int moveX = Integer.parseInt(parts[2]);
                    int moveY = Integer.parseInt(parts[3]);
                    
                    // Ne pas mettre à jour notre propre joueur (déjà fait localement)
                    if (moveId != arena.getLocalPlayerId()) {
                        Joueur movedJoueur = new Joueur();
                        movedJoueur.setid(moveId);
                        movedJoueur.setX(moveX);
                        movedJoueur.setY(moveY);
                        arena.updateJoueur(movedJoueur);
                    }
                }
                break;
                
            case Protocol.MSG_GAME_STATE:
                // Format: STATE|id1|x1|y1|id2|x2|y2|...
                // Mise à jour complète de l'état du jeu
                updateGameState(parts);
                break;
                
            case Protocol.MSG_PLAYER_LEFT:
                // Format: LEFT|id
                if (parts.length >= 2) {
                    int playerId = Integer.parseInt(parts[1]);
                    arena.removeJoueur(playerId);
                    System.out.println("✓ Joueur parti: ID " + playerId);
                }
                break;
                
            case Protocol.MSG_SERVER_STOP:
                // Le serveur a été arrêté
                System.out.println("⚠ Le serveur a été arrêté");
                running = false;
                triggerDisconnect();
                break;
                
            case Protocol.MSG_ERROR:
                if (parts.length >= 2) {
                    System.err.println("✗ Erreur serveur: " + parts[1]);
                }
                break;
                
            default:
                System.out.println("⚠ Message non géré: " + messageType);
                break;
        }
    }

    // Référence à l'arène de l'hôte (pour la mise à jour visuelle côté serveur)
    private static Arena hostArena = null;
    
    public static void setHostArena(Arena arena) {
        hostArena = arena;
    }
    
    /*Traite les messages reçus d'un client (côté serveur)*/
    private void handleServerMessage(String message) {
        String[] parts = Protocol.parseMessage(message);
        if (parts.length == 0) return;
        
        String messageType = parts[0];
        
        switch (messageType) {
            case Protocol.MSG_MOVE:
                // Format: MOVE|id|x|y — mettre à jour le joueur et broadcaster
                if (parts.length >= 4) {
                    int moveId = Integer.parseInt(parts[1]);
                    int moveX = Integer.parseInt(parts[2]);
                    int moveY = Integer.parseInt(parts[3]);
                    
                    // Mettre à jour le joueur dans le modèle serveur
                    if (joueur != null && joueur.getid() == moveId) {
                        joueur.setX(moveX);
                        joueur.setY(moveY);
                    }
                    
                    // Mettre à jour l'arène de l'hôte
                    if (hostArena != null) {
                        Joueur updated = new Joueur();
                        updated.setid(moveId);
                        updated.setX(moveX);
                        updated.setY(moveY);
                        hostArena.updateJoueur(updated);
                    }
                }
                // Broadcaster à tous les autres clients
                broadcastMessage(message);
                break;
                
            default:
                // Pour les autres messages, broadcaster simplement
                broadcastMessage(message);
                break;
        }
    }
    
    /*Broadcast un message à tous les autres clients (côté serveur)*/
    private void broadcastMessage(String message) {
        if (server != null) {
            for (Client client : server.getclients()) {
                if (client != this && client.getSocket() != null) {
                    client.send(message);
                }
            }
        }
    }

    /*Met à jour l'état complet du jeu*/
    private void updateGameState(String[] parts) {
        System.out.println("Mise à jour de l'état du jeu");
    }

    /*Envoie un message au serveur ou au client*/
    public void send(String message) {
        if (out != null) {
            out.println(message);
            out.flush();
        }
    }

    /*Arrête le gestionnaire réseau*/
    public void stop() {
        running = false;
    }
    
    /*Déclenche le callback de déconnexion (une seule fois)*/
    private void triggerDisconnect() {
        if (!disconnectHandled && onDisconnected != null && arena != null) {
            disconnectHandled = true;
            javax.swing.SwingUtilities.invokeLater(onDisconnected);
        }
    }
    
    /*Ferme proprement la connexion*/
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (server != null) {
                server.getclients().remove(this);
            }
        } catch (IOException e) {
            System.err.println("✗ Erreur lors de la fermeture: " + e.getMessage());
        }
    }

    /*Recuperer la liste des ip de serveurs disponible*/
    public static List<String> discoverStreamers(int timeoutMs) {
        List<String> streamerIPs = new ArrayList<>();
        
        try {
            System.out.println("Démarrage de la découverte multicast...");
            
            // UN SEUL socket pour envoyer ET recevoir
            DatagramSocket socket = new DatagramSocket();
            
            socket.setSoTimeout(timeoutMs);
            
            InetAddress group = InetAddress.getByName(Protocol.MULTICAST_GROUP);
            
            // Envoyer requête multicast
            String message = "DISCOVER_GAME";
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, Protocol.DISCOVERY_PORT);
            
            System.out.println("Envoi requête multicast...");
            socket.send(packet);
            
            System.out.println("Attente des réponses...");
            
            // Recevoir les réponses sur le MÊME socket
            byte[] receiveBuffer = new byte[256];
            
            try {
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(
                        receiveBuffer, receiveBuffer.length
                    );
                    socket.receive(receivePacket);
                    
                    String ip = new String(
                        receivePacket.getData(), 
                        0, 
                        receivePacket.getLength()
                    ).trim();
                    
                    System.out.println("Serveur trouvé : " + ip);
                    streamerIPs.add(ip);
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout atteint");
            }
            
            socket.close();
            
            System.out.println("Découverte terminée. Total : " + streamerIPs.size());
            
        } catch (Exception e) {
            System.err.println("Erreur découverte : " + e.getMessage());
            e.printStackTrace();
        }
        
        return streamerIPs;
    }

}
