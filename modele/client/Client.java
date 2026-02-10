package modele.client;

import java.io.*;
import java.net.*;
import java.util.Vector;

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
                    // Sinon (côté serveur), broadcaster aux autres clients
                    if (server != null) {
                        broadcastMessage(message);
                    }
                }
            }
            
        } catch (IOException e) {
            if (running) {
                System.err.println("✗ Connexion perdue");
            }
        } finally {
            System.out.println("Arrêt du gestionnaire réseau");
            close();
        }
    }

    /**
     * Traite les messages reçus du serveur (côté client)
     */
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

    /**
     * Broadcast un message à tous les autres clients (côté serveur)
     */
    private void broadcastMessage(String message) {
        if (server != null) {
            for (Client client : server.getclients()) {
                if (client != this && client.getSocket() != null) {
                    client.send(message);
                }
            }
        }
    }

    /**
     * Met à jour l'état complet du jeu
     */
    private void updateGameState(String[] parts) {
        // À implémenter plus tard pour la synchronisation complète
        System.out.println("Mise à jour de l'état du jeu");
    }

    /**
     * Envoie un message au serveur ou au client
     */
    public void send(String message) {
        if (out != null) {
            out.println(message);
            out.flush();
        }
    }

    /**
     * Arrête le gestionnaire réseau
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Ferme proprement la connexion
     */
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

}
