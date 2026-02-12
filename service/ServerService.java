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
    private static Arena hostArena = null;              // Arene de l'hôte
    private static volatile boolean isRunning = false;  // État du serveur
    private static Thread discoveryThread;              // Thread pour la découverte multicast
    public static String lastConnectionError = null;    // Dernier message d'erreur de connexion
    private static CaptureZone currentZone = null;       // Zone de capture active
    private static Thread captureZoneThread = null;      // Thread de gestion de la zone
    private static volatile boolean gameOver = false;     // Partie terminée

    /*Crée un nouveau serveur de jeu*/
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

    /*Démarre le serveur et commence à accepter les connexions*/
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
        
        // Ajouter l'hôte à son arene
        if (hostArena != null) {
            hostArena.addJoueur(joueurHost);
            hostArena.setLocalPlayerId(joueurHost.getid());
        }
        
        System.out.println("✓ Hôte ajouté: " + joueurHost.getPseudo() + " à la position (" + joueurHost.getX() + ", " + joueurHost.getY() + ")");

        // Marquer le serveur comme actif et démarrer la découverte multicast
        isRunning = true;
        gameOver = false;
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
                                String.valueOf(nouveauJoueur.getY()),
                                String.valueOf(Protocol.SCORE_TO_WIN)
                            );
                            out.println(welcomeMsg);
                            
                            // Envoyer la liste de tous les joueurs existants au nouveau client
                            for (Client existingClient : new java.util.ArrayList<>(gameServer.getclients())) {
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
                            
                            // Envoyer la zone de capture actuelle si elle existe
                            if (currentZone != null && currentZone.isActive()) {
                                String zoneMsg = Protocol.buildMessage(
                                    Protocol.MSG_ZONE_SPAWN,
                                    String.valueOf(currentZone.getX()),
                                    String.valueOf(currentZone.getY()),
                                    String.valueOf(currentZone.getWidth()),
                                    String.valueOf(currentZone.getHeight())
                                );
                                out.println(zoneMsg);
                                
                                // Envoyer aussi la progression si quelqu'un capture
                                if (currentZone.getCapturingPlayerId() != -1) {
                                    String zoneUpdate = Protocol.buildMessage(
                                        Protocol.MSG_ZONE_UPDATE,
                                        String.valueOf(currentZone.getCapturingPlayerId()),
                                        currentZone.getCapturingPlayerName().isEmpty() ? "none" : currentZone.getCapturingPlayerName(),
                                        String.valueOf(currentZone.getCaptureProgress())
                                    );
                                    out.println(zoneUpdate);
                                }
                            }
                            
                            // Envoyer les scores actuels
                            broadcastScores(gameServer);
                            
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
                            
                            for (Client existingClient : new java.util.ArrayList<>(gameServer.getclients())) {
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

    /*Connecte un client à un serveur distant*/
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
            
            // Lire la réponse du serveur (utiliser le BufferedReader du client pour ne pas perdre de messages)
            String response = client.readLine();
            
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
                
                // Synchroniser le score cible depuis le serveur
                if (parts.length >= 5) {
                    Protocol.SCORE_TO_WIN = Integer.parseInt(parts[4]);
                    System.out.println("✓ Score cible synchronisé: " + Protocol.SCORE_TO_WIN);
                }
                
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

    /*Assigne une position aléatoire valide à un joueur dans l'arène*/
    private static void assignRandomPosition(Joueur joueur) {
        // Zones sûres pour ne pas apparaître hors de l'arène
        int maxX = Protocol.ARENA_WIDTH - Protocol.PLAYER_SIZE;
        int maxY = Protocol.ARENA_HEIGHT - Protocol.PLAYER_SIZE;
        
        int x = random.nextInt(maxX);
        int y = random.nextInt(maxY);
        
        joueur.setX(x);
        joueur.setY(y);
    }

    /*Arrête le serveur, la découverte multicast, et ferme toutes les connexions*/
    public static void stopServer(GameServer gameServer) {
        System.out.println("=== ARRÊT DU SERVEUR ===");
        isRunning = false;

        // Arrêter la capture de zone
        stopCaptureZone();

        // Arrêter la découverte multicast
        if (discoveryThread != null) {
            discoveryThread.interrupt();
            discoveryThread = null;
            System.out.println("✓ Découverte multicast arrêtée");
        }

        // Envoyer SERVER_STOP à tous les clients avant de fermer
        String stopMsg = Protocol.buildMessage(Protocol.MSG_SERVER_STOP);
        for (Client client : new java.util.ArrayList<>(gameServer.getclients())) {
            if (client.getSocket() != null) {
                client.send(stopMsg);
            }
        }

        // Petit délai pour que les clients reçoivent le message
        try { Thread.sleep(200); } catch (InterruptedException e) {}

        // Fermer toutes les connexions clients
        for (Client client : new java.util.ArrayList<>(gameServer.getclients())) {
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
            
            // Ignorer docker, veth, virtual, loopback👉
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

    /*Retourne l'IP physique locale (WiFi/Ethernet)*/
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
        
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /*=== CAPTURE DE ZONE ===*/
    public static void startCaptureZone(GameServer gameServer) {
        captureZoneThread = new Thread(() -> {
            try {
                // Attendre un peu avant la première zone
                Thread.sleep(Protocol.ZONE_SPAWN_DELAY_MS);
                
                while (isRunning) {
                    // Faire apparaître une nouvelle zone
                    spawnNewZone(gameServer);
                    
                    // Boucle de vérification toutes les secondes
                    while (isRunning && !gameOver && currentZone != null && currentZone.isActive() && !currentZone.isCaptured()) {
                        captureZoneTick(gameServer);
                        Thread.sleep(1000); // Tick toutes les secondes
                    }
                    
                    if (!isRunning || gameOver) break;
                    
                    // Zone capturée — notifier reset et attendre avant respawn
                    broadcastToAll(gameServer, Protocol.buildMessage(Protocol.MSG_ZONE_RESET));
                    if (hostArena != null) {
                        hostArena.setCaptureZone(null);
                    }
                    currentZone = null;
                    
                    Thread.sleep(Protocol.ZONE_RESPAWN_DELAY_MS);
                }
            } catch (InterruptedException e) {
                System.out.println("Thread capture de zone interrompu");
            }
        }, "CaptureZone-Thread");
        captureZoneThread.setDaemon(true);
        captureZoneThread.start();
        System.out.println("✓ Système de capture de zone démarré");
    }

    private static void spawnNewZone(GameServer gameServer) {
        int maxX = Protocol.ARENA_WIDTH - Protocol.ZONE_SIZE;
        int maxY = Protocol.ARENA_HEIGHT - Protocol.ZONE_SIZE;
        int zoneX = random.nextInt(Math.max(1, maxX));
        int zoneY = random.nextInt(Math.max(1, maxY));
        
        currentZone = new CaptureZone(zoneX, zoneY, Protocol.ZONE_SIZE, Protocol.ZONE_SIZE);
        
        System.out.println("✦ Zone de capture apparue à (" + zoneX + ", " + zoneY + ")");
        
        // Notifier tous les clients
        String spawnMsg = Protocol.buildMessage(
            Protocol.MSG_ZONE_SPAWN,
            String.valueOf(zoneX),
            String.valueOf(zoneY),
            String.valueOf(Protocol.ZONE_SIZE),
            String.valueOf(Protocol.ZONE_SIZE)
        );
        broadcastToAll(gameServer, spawnMsg);
        
        // Mettre à jour l'arène de l'hôte
        if (hostArena != null) {
            hostArena.setCaptureZone(currentZone);
        }
    }

    // Verrou pour synchroniser l'accès à la zone de capture
    private static final Object zoneLock = new Object();
    
    /*Tick de capture : vérifie quels joueurs sont dans la zone et met à jour la progression.*/
    private static void captureZoneTick(GameServer gameServer) {
        synchronized (zoneLock) {
        if (currentZone == null || !currentZone.isActive() || currentZone.isCaptured()) return;
        
        // Trouver les joueurs dans la zone
        java.util.List<Client> playersInZone = new java.util.ArrayList<>();
        
        for (Client client : new java.util.ArrayList<>(gameServer.getclients())) {
            Joueur j = client.getJoueur();
            if (j != null && currentZone.containsPlayer(j.getX(), j.getY(), Protocol.PLAYER_SIZE)) {
                playersInZone.add(client);
            }
        }
        
        int currentCapturerId = currentZone.getCapturingPlayerId();
        
        if (!playersInZone.isEmpty()) {
            // Vérifier si le joueur qui capture est toujours dans la zone
            boolean currentCapturerStillInZone = false;
            for (Client c : playersInZone) {
                if (c.getJoueur().getid() == currentCapturerId) {
                    currentCapturerStillInZone = true;
                    break;
                }
            }
            
            if (currentCapturerId != -1 && currentCapturerStillInZone) {
                // Le joueur qui capture est toujours là — continuer la progression
                double increment = 1.0 / Protocol.CAPTURE_TIME_SECONDS;
                double newProgress = Math.min(1.0, currentZone.getCaptureProgress() + increment);
                currentZone.setCaptureProgress(newProgress);
                
                System.out.println("⏳ " + currentZone.getCapturingPlayerName() + " capture: " + (int)(newProgress * 100) + "%");
                
                if (newProgress >= 1.0) {
                    // Zone capturée !
                    currentZone.setCaptured(true);
                    currentZone.setActive(false);
                    String winnerName = currentZone.getCapturingPlayerName();
                    int winnerId = currentZone.getCapturingPlayerId();
                    System.out.println("🏆 " + winnerName + " a capturé la zone !");
                    
                    // Incrémenter le score du joueur
                    for (Client c : gameServer.getclients()) {
                        if (c.getJoueur() != null && c.getJoueur().getid() == winnerId) {
                            c.getJoueur().setScore(c.getJoueur().getScore() + 1);
                            break;
                        }
                    }
                    
                    // Broadcaster les scores mis à jour
                    broadcastScores(gameServer);
                    
                    String capturedMsg = Protocol.buildMessage(
                        Protocol.MSG_ZONE_CAPTURED,
                        String.valueOf(winnerId),
                        winnerName
                    );
                    broadcastToAll(gameServer, capturedMsg);
                    
                    if (hostArena != null) {
                        hostArena.showCaptureVictory(winnerName);
                    }
                    
                    // Vérifier si le joueur a atteint le score cible
                    for (Client c : gameServer.getclients()) {
                        if (c.getJoueur() != null && c.getJoueur().getid() == winnerId) {
                            if (c.getJoueur().getScore() >= Protocol.SCORE_TO_WIN) {
                                // Victoire finale !
                                gameOver = true;
                                System.out.println("🏆🏆 " + winnerName + " a gagné la partie avec " + c.getJoueur().getScore() + " points !");
                                String gameWonMsg = Protocol.buildMessage(
                                    Protocol.MSG_GAME_WON,
                                    String.valueOf(winnerId),
                                    winnerName,
                                    String.valueOf(c.getJoueur().getScore())
                                );
                                broadcastToAll(gameServer, gameWonMsg);
                                
                                if (hostArena != null) {
                                    hostArena.showGameWon(winnerName, c.getJoueur().getScore());
                                }
                            }
                            break;
                        }
                    }
                    return;
                }
            } else {
                // Personne ne capture encore, ou le captureur est parti — le premier joueur dans la zone prend le relais
                Client firstPlayer = playersInZone.get(0);
                Joueur j = firstPlayer.getJoueur();
                
                if (currentCapturerId == -1) {
                    // Personne ne capturait — nouveau début
                    currentZone.setCapturingPlayerId(j.getid());
                    currentZone.setCapturingPlayerName(j.getPseudo());
                    currentZone.setCaptureProgress(1.0 / Protocol.CAPTURE_TIME_SECONDS);
                    System.out.println("⏳ " + j.getPseudo() + " commence la capture");
                } else {
                    // Le captureur précédent a quitté la zone — reset et nouveau début
                    System.out.println("✗ " + currentZone.getCapturingPlayerName() + " a quitté la zone");
                    currentZone.setCapturingPlayerId(j.getid());
                    currentZone.setCapturingPlayerName(j.getPseudo());
                    currentZone.setCaptureProgress(1.0 / Protocol.CAPTURE_TIME_SECONDS);
                    System.out.println("⏳ " + j.getPseudo() + " commence la capture");
                }
            }
        } else {
            // Personne dans la zone — la progression se réinitialise
            if (currentZone.getCapturingPlayerId() != -1) {
                System.out.println("✗ " + currentZone.getCapturingPlayerName() + " a quitté la zone");
                currentZone.resetCapture();
            }
        }
        
        // Envoyer la mise à jour à tous
        String updateMsg = Protocol.buildMessage(
            Protocol.MSG_ZONE_UPDATE,
            String.valueOf(currentZone.getCapturingPlayerId()),
            currentZone.getCapturingPlayerName().isEmpty() ? "none" : currentZone.getCapturingPlayerName(),
            String.valueOf(currentZone.getCaptureProgress())
        );
        broadcastToAll(gameServer, updateMsg);
        
        // Mettre à jour l'arène de l'hôte
        if (hostArena != null) {
            hostArena.setCaptureZone(currentZone);
        }
        } // fin synchronized
    }

    /*Envoie un message à tous les clients connectés (sockets réseau uniquement, pas l'hôte).*/
    private static void broadcastToAll(GameServer gameServer, String message) {
        for (Client client : new java.util.ArrayList<>(gameServer.getclients())) {
            if (client.getSocket() != null) {
                client.send(message);
            }
        }
    }

    /*Envoie les scores de tous les joueurs à tous les clients.*/
    public static void broadcastScores(GameServer gameServer) {
        StringBuilder sb = new StringBuilder(Protocol.MSG_SCORE_UPDATE);
        for (Client client : new java.util.ArrayList<>(gameServer.getclients())) {
            Joueur j = client.getJoueur();
            if (j != null) {
                sb.append(Protocol.SEPARATOR).append(j.getid());
                sb.append(Protocol.SEPARATOR).append(j.getPseudo());
                sb.append(Protocol.SEPARATOR).append(j.getScore());
            }
        }
        sb.append(Protocol.END_MESSAGE);
        String msg = sb.toString();
        broadcastToAll(gameServer, msg);
        
        // Mettre à jour l'arène de l'hôte aussi
        if (hostArena != null) {
            hostArena.updateScores(gameServer);
        }
    }

    /*Arrête le système de capture de zone.*/
    public static void stopCaptureZone() {
        if (captureZoneThread != null) {
            captureZoneThread.interrupt();
            captureZoneThread = null;
        }
        currentZone = null;
        System.out.println("✓ Système de capture de zone arrêté");
    }
}