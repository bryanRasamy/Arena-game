package service;

import modele.client.*;
import modele.common.*;
import modele.server.*;
import vue.Arena;

import java.net.*;
import java.util.Enumeration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;

public class ServerService {
    private static ExecutorService executor = Executors.newCachedThreadPool();
    private static Random random = new Random();
    private static Arena hostArena = null; // Arena de l'hôte
    private static volatile boolean isRunning = false; // État du serveur
    private static Thread discoveryThread; // Thread pour la découverte multicast
    public static String lastConnectionError = null; // Dernier message d'erreur de connexion

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

        // Marquer le serveur comme actif et démarrer la découverte multicast
        isRunning = true;
        startDiscoveryResponder();

        executor.submit(() -> {
            try {
                int nextPlayerId = 1;
                
                while (isRunning) {
                    try {
                        System.out.println("⏳ En attente de connexions... (" + gameServer.getclients().size() + "/" + gameServer.getNombre_joueurs() + ")");
                        Socket clientSocket = gameServer.getServeurSocket().accept();
                        System.out.println("✓ Nouvelle connexion reçue: " + clientSocket.getInetAddress());
                        
                        // Lire les informations du joueur
                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        String connectMessage = in.readLine();
                        
                        if (connectMessage != null && connectMessage.startsWith(Protocol.MSG_CONNECT)) {
                            
                            // Vérifier si le serveur est plein
                            if (gameServer.getclients().size() >= gameServer.getNombre_joueurs()) {
                                PrintWriter rejectOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
                                rejectOut.println(Protocol.buildMessage(Protocol.MSG_SERVER_FULL, "Serveur plein"));
                                clientSocket.close();
                                System.out.println("✗ Connexion refusée: serveur plein");
                                continue;
                            }
                            
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
                        if (isRunning) {
                            System.out.println("Erreur socket: " + e.getMessage());
                        } else {
                            System.out.println("Serveur arrêté");
                        }
                        break;
                    }
                }
                
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
        lastConnectionError = null;
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
            
            // Lire la réponse du serveur
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response = in.readLine();
            
            // Vérifier si le serveur est plein
            if (response != null && response.startsWith(Protocol.MSG_SERVER_FULL)) {
                System.out.println("✗ Serveur plein !");
                lastConnectionError = "Le serveur est plein !\nNombre maximum de joueurs atteint.";
                socket.close();
                return null;
            }
            
            if (response != null && response.startsWith(Protocol.MSG_WELCOME)) {
                String[] parts = Protocol.parseMessage(response);
                joueur.setid(Integer.parseInt(parts[1]));
                joueur.setX(Integer.parseInt(parts[2]));
                joueur.setY(Integer.parseInt(parts[3]));
                
                System.out.println("✓ Connecté avec succès! ID: " + joueur.getid() + " Position: (" + joueur.getX() + ", " + joueur.getY() + ")");
            }
            
            return client;
            
        } catch (IOException e) {
            System.err.println("✗ Erreur de connexion: " + e.getMessage());
            lastConnectionError = "Impossible de se connecter au serveur.\n" + e.getMessage();
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

    /**
     * Arrête le serveur, la découverte multicast, et ferme toutes les connexions
     */
    public static void stopServer(GameServer gameServer) {
        System.out.println("=== ARRÊT DU SERVEUR ===");
        isRunning = false;

        // Arrêter la découverte multicast
        if (discoveryThread != null) {
            discoveryThread.interrupt();
            discoveryThread = null;
            System.out.println("✓ Découverte multicast arrêtée");
        }

        // Envoyer SERVER_STOP à tous les clients avant de fermer
        String stopMsg = Protocol.buildMessage(Protocol.MSG_SERVER_STOP);
        for (Client client : gameServer.getclients()) {
            if (client.getSocket() != null) {
                client.send(stopMsg);
            }
        }

        // Petit délai pour que les clients reçoivent le message
        try { Thread.sleep(200); } catch (InterruptedException e) {}

        // Fermer toutes les connexions clients
        for (Client client : gameServer.getclients()) {
            if (client.getSocket() != null) {
                client.close();
            }
        }
        gameServer.getclients().clear();

        // Fermer le serveur socket
        try {
            if (gameServer.getServeurSocket() != null && !gameServer.getServeurSocket().isClosed()) {
                gameServer.getServeurSocket().close();
            }
        } catch (IOException e) {
            System.err.println("✗ Erreur lors de la fermeture du serveur: " + e.getMessage());
        }

        hostArena = null;
        System.out.println("✓ Serveur arrêté");
    }

    /*Ajouter le serveur au groupe multiccast*/
    private static void startDiscoveryResponder() {
        discoveryThread = new Thread(() -> {
            MulticastSocket socket = null;
            try {
                socket = new MulticastSocket(Protocol.DISCOVERY_PORT);
                InetAddress group = InetAddress.getByName(Protocol.MULTICAST_GROUP);
                
                // Trouver une interface PHYSIQUE IPv4
                NetworkInterface networkInterface = findPhysicalIPv4Interface();
                
                if (networkInterface == null) {
                    System.err.println("Impossible de démarrer la découverte : aucune interface physique IPv4");
                    return;
                }
                
                System.out.println("Interface multicast : " + networkInterface.getName() + " (" + networkInterface.getDisplayName() + ")");
                
                // Rejoindre le groupe
                InetSocketAddress groupAddress = new InetSocketAddress(group, Protocol.DISCOVERY_PORT);
                socket.joinGroup(groupAddress, networkInterface);
                
                System.out.println("Serveur visible sur multicast " + Protocol.MULTICAST_GROUP);
                
                byte[] buffer = new byte[256];
                
                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    String request = new String(packet.getData(), 0, packet.getLength());
                    
                    if (request.equals("DISCOVER_GAME")) {
                        // Utiliser l'IP de l'interface physique + port
                        String myIP = getIPFromInterface(networkInterface);
                        String response = myIP + ":" + Protocol.SERVER_PORT;
                        System.out.println("Requête découverte reçue, envoi : " + response);
                        
                        byte[] responseData = response.getBytes();
                        
                        DatagramPacket responsePacket = new DatagramPacket(
                            responseData, responseData.length,
                            packet.getAddress(), packet.getPort()
                        );
                        
                        DatagramSocket sendSocket = new DatagramSocket();
                        sendSocket.send(responsePacket);
                        sendSocket.close();
                    }
                }
                
                socket.leaveGroup(groupAddress, networkInterface);
                socket.close();
                
            } catch (Exception e) {
                if (isRunning) {
                    e.printStackTrace();
                }
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        }, "Discovery-Responder");
        discoveryThread.start();
    }

    /*Trouve une interface réseau physique IPv4 (WiFi/Ethernet) pour le multicast*/
    private static NetworkInterface findPhysicalIPv4Interface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            
            String name = ni.getName().toLowerCase();
            String displayName = ni.getDisplayName().toLowerCase();
            
            // IGNORER docker, veth, virtual, loopback
            if (name.contains("docker") || name.contains("veth") || name.contains("br-") || name.contains("vboxnet") || displayName.contains("virtual") || displayName.contains("loopback")) {
                continue;
            }
            
            if (!ni.isUp() || !ni.supportsMulticast()) {
                continue;
            }
            
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                    return ni;
                }
            }
        }
        
        return null;
    }

    private static String getIPFromInterface(NetworkInterface ni) {
        Enumeration<InetAddress> addresses = ni.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                return addr.getHostAddress();
            }
        }
        return "unknown";
    }

    /**
     * Retourne l'IP physique locale (WiFi/Ethernet), pas localhost
     */
    public static String getLocalPhysicalIP() {
        try {
            NetworkInterface ni = findPhysicalIPv4Interface();
            if (ni != null) {
                String ip = getIPFromInterface(ni);
                if (!"unknown".equals(ip)) {
                    return ip;
                }
            }
        } catch (SocketException e) {
            System.err.println("Erreur lors de la récupération de l'IP physique: " + e.getMessage());
        }
        // Fallback
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}