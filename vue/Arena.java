package vue;

import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import modele.client.*;
import modele.common.Protocol;
import modele.server.GameServer;
import java.util.Vector;

public class Arena extends JPanel {
    
    // Liste des joueurs à afficher
    private Vector<Joueur> joueurs;
    
    // ID du joueur local (pour le mettre en évidence)
    private int localPlayerId = -1;
    
    // Référence au client réseau pour envoyer les mouvements
    private Client networkClient = null;
    
    // Référence au GameServer (pour l'hôte, qui broadcast directement)
    private GameServer hostServer = null;
    
    // Touches actuellement enfoncées (pour mouvement fluide)
    private final Set<Integer> pressedKeys = new HashSet<>();
    
    // Timer pour la boucle de jeu locale
    private Timer gameLoopTimer;
    
    // Couleurs
    private static final Color ARENA_BG = new Color(20, 20, 20);
    private static final Color GRID_COLOR = new Color(40, 40, 40);
    private static final Color LOCAL_PLAYER_COLOR = new Color(70, 130, 180);
    private static final Color OTHER_PLAYER_COLOR = new Color(220, 100, 50);
    
    // Vitesse de déplacement en pixels par tick
    private static final int MOVE_SPEED = (int) Protocol.PLAYER_SPEED;

    public Arena(GameServer gameServer) {
        joueurs = new Vector<>();
        
        setPreferredSize(new Dimension(Protocol.ARENA_WIDTH, Protocol.ARENA_HEIGHT));
        setBackground(ARENA_BG);
        setFocusable(true);

        // Ajouter tous les joueurs existants du serveur
        Vector<Client> clients = gameServer.getclients();
         
        for (Client client : clients) {
            addPlayer(client);
        }
        
        // Gestion du clavier via Key Bindings (plus fiable que KeyListener)
        setupKeyBindings();
        
        // Boucle de jeu pour le mouvement fluide
        gameLoopTimer = new Timer(Protocol.TICK_DELAY_MS, e -> gameLoop());
        gameLoopTimer.start();
        
        System.out.println("Arena créée avec " + joueurs.size() + " joueur(s)");
    }
    
    /**
     * Configure les Key Bindings pour le mouvement (ZQSD + flèches)
     */
    private void setupKeyBindings() {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        
        // Mapping des touches -> codes
        int[][] keyMappings = {
            {KeyEvent.VK_UP, KeyEvent.VK_UP},
            {KeyEvent.VK_DOWN, KeyEvent.VK_DOWN},
            {KeyEvent.VK_LEFT, KeyEvent.VK_LEFT},
            {KeyEvent.VK_RIGHT, KeyEvent.VK_RIGHT},
            {KeyEvent.VK_Z, KeyEvent.VK_UP},
            {KeyEvent.VK_S, KeyEvent.VK_DOWN},
            {KeyEvent.VK_Q, KeyEvent.VK_LEFT},
            {KeyEvent.VK_D, KeyEvent.VK_RIGHT},
        };
        
        for (int[] mapping : keyMappings) {
            int physicalKey = mapping[0];
            int direction = mapping[1];
            
            String pressName = "press_" + physicalKey;
            String releaseName = "release_" + physicalKey;
            
            inputMap.put(KeyStroke.getKeyStroke(physicalKey, 0, false), pressName);
            inputMap.put(KeyStroke.getKeyStroke(physicalKey, 0, true), releaseName);
            
            actionMap.put(pressName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    pressedKeys.add(direction);
                }
            });
            
            actionMap.put(releaseName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    pressedKeys.remove(direction);
                }
            });
        }
    }
    
    /**
     * Boucle de jeu : applique le mouvement du joueur local et envoie au réseau
     */
    private void gameLoop() {
        if (localPlayerId == -1 || pressedKeys.isEmpty()) return;
        
        Joueur localPlayer = getJoueurById(localPlayerId);
        if (localPlayer == null) return;
        
        int dx = 0, dy = 0;
        
        if (pressedKeys.contains(KeyEvent.VK_UP))    dy -= MOVE_SPEED;
        if (pressedKeys.contains(KeyEvent.VK_DOWN))   dy += MOVE_SPEED;
        if (pressedKeys.contains(KeyEvent.VK_LEFT))   dx -= MOVE_SPEED;
        if (pressedKeys.contains(KeyEvent.VK_RIGHT))  dx += MOVE_SPEED;
        
        if (dx == 0 && dy == 0) return;
        
        // Calculer la nouvelle position avec les limites de l'arène
        int newX = Math.max(0, Math.min(Protocol.ARENA_WIDTH - Protocol.PLAYER_SIZE, localPlayer.getX() + dx));
        int newY = Math.max(0, Math.min(Protocol.ARENA_HEIGHT - Protocol.PLAYER_SIZE, localPlayer.getY() + dy));
        
        // Ne rien faire si la position n'a pas changé
        if (newX == localPlayer.getX() && newY == localPlayer.getY()) return;
        
        // Appliquer le mouvement localement
        localPlayer.setX(newX);
        localPlayer.setY(newY);
        
        // Envoyer le mouvement au réseau
        sendMoveToNetwork(localPlayer);
        
        repaint();
    }
    
    /**
     * Envoie la position du joueur local au réseau
     */
    private void sendMoveToNetwork(Joueur joueur) {
        String moveMsg = Protocol.buildMessage(
            Protocol.MSG_MOVE,
            String.valueOf(joueur.getid()),
            String.valueOf(joueur.getX()),
            String.valueOf(joueur.getY())
        );
        
        // Si on est un client connecté, envoyer au serveur
        if (networkClient != null) {
            networkClient.send(moveMsg);
        }
        
        // Si on est l'hôte, broadcaster directement aux clients connectés
        if (hostServer != null) {
            for (Client client : hostServer.getclients()) {
                if (client.getSocket() != null) {
                    client.send(moveMsg);
                }
            }
        }
    }
    
    /**
     * Définit le client réseau pour l'envoi des mouvements (côté client)
     */
    public void setNetworkClient(Client client) {
        this.networkClient = client;
    }
    
    /**
     * Définit le serveur pour le broadcast direct (côté hôte)
     */
    public void setHostServer(GameServer server) {
        this.hostServer = server;
    }
    
    /**
     * Retourne un joueur par son ID
     */
    private Joueur getJoueurById(int id) {
        synchronized (joueurs) {
            for (Joueur j : joueurs) {
                if (j.getid() == id) return j;
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        
        // Anti-aliasing pour un rendu plus lisse
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Dessiner la grille
        drawGrid(g2d);
        
        // Dessiner tous les joueurs
        synchronized (joueurs) {
            for (Joueur joueur : joueurs) {
                drawPlayer(g2d, joueur);
            }
        }
        
        // Afficher les instructions
        drawInstructions(g2d);
        
        // Afficher le nombre de joueurs
        drawPlayerCount(g2d);
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(GRID_COLOR);
        int gridSize = 50;
        
        // Lignes verticales
        for (int x = 0; x < Protocol.ARENA_WIDTH; x += gridSize) {
            g2d.drawLine(x, 0, x, Protocol.ARENA_HEIGHT);
        }
        
        // Lignes horizontales
        for (int y = 0; y < Protocol.ARENA_HEIGHT; y += gridSize) {
            g2d.drawLine(0, y, Protocol.ARENA_WIDTH, y);
        }
    }

    private void drawPlayer(Graphics2D g2d, Joueur joueur) {
        // Couleur selon si c'est le joueur local ou non
        if (joueur.getid() == localPlayerId) {
            g2d.setColor(LOCAL_PLAYER_COLOR);
        } else {
            g2d.setColor(OTHER_PLAYER_COLOR);
        }
        
        // Dessiner le joueur (carré)
        g2d.fillRect(joueur.getX(), joueur.getY(), Protocol.PLAYER_SIZE, Protocol.PLAYER_SIZE);
        
        // Bordure
        g2d.setColor(Color.WHITE);
        g2d.drawRect(joueur.getX(), joueur.getY(), Protocol.PLAYER_SIZE, Protocol.PLAYER_SIZE);
        
        // Afficher le pseudo au-dessus
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(joueur.getPseudo());
        int textX = joueur.getX() + (Protocol.PLAYER_SIZE - textWidth) / 2;
        int textY = joueur.getY() - 5;
        
        // Ombre du texte
        g2d.setColor(Color.BLACK);
        g2d.drawString(joueur.getPseudo(), textX + 1, textY + 1);
        
        // Texte
        g2d.setColor(Color.WHITE);
        g2d.drawString(joueur.getPseudo(), textX, textY);
        
        // Afficher l'ID (pour debug)
        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("Arial", Font.PLAIN, 9));
        g2d.drawString("ID:" + joueur.getid(), joueur.getX(), joueur.getY() + Protocol.PLAYER_SIZE + 12);
    }

    private void drawInstructions(Graphics2D g2d) {
        g2d.setColor(new Color(150, 150, 150));
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        String instructions = "Utilisez les flèches ou ZQSD pour vous déplacer";
        g2d.drawString(instructions, 10, Protocol.ARENA_HEIGHT - 10);
    }

    private void drawPlayerCount(Graphics2D g2d) {
        g2d.setColor(new Color(150, 150, 150));
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        String count = "Joueurs: " + joueurs.size();
        g2d.drawString(count, Protocol.ARENA_WIDTH - 100, 20);
    }

    /**
     * Ajoute ou met à jour un joueur dans l'arène depuis un Client
     */
    public void addPlayer(Client client) {
        synchronized (joueurs) {
            Joueur joueur = client.getJoueur();
            
            // Vérifier si le joueur existe déjà
            boolean exists = false;
            for (Joueur j : joueurs) {
                if (j.getid() == joueur.getid()) {
                    exists = true;
                    break;
                }
            }
            
            // Ajouter seulement s'il n'existe pas
            if (!exists) {
                joueurs.add(joueur);
                System.out.println("✓ Joueur ajouté à l'arène: " + joueur.getPseudo() + " (ID: " + joueur.getid() + ")");
            }
            
            // Définir comme joueur local si c'est l'hôte
            if (joueur.getIsHost()) {
                localPlayerId = joueur.getid();
                System.out.println("✓ Joueur local défini: ID " + localPlayerId);
            }
        }
        repaint();
    }

    /**
     * Ajoute un joueur directement dans l'arène
     */
    public void addJoueur(Joueur joueur) {
        synchronized (joueurs) {
            // Vérifier si le joueur existe déjà
            boolean exists = false;
            for (Joueur j : joueurs) {
                if (j.getid() == joueur.getid()) {
                    exists = true;
                    break;
                }
            }
            
            // Ajouter seulement s'il n'existe pas
            if (!exists) {
                joueurs.add(joueur);
                System.out.println("✓ Joueur ajouté: " + joueur.getPseudo() + " à (" + joueur.getX() + ", " + joueur.getY() + ")");
            }
        }
        repaint();
    }
    
    /**
     * Définit l'ID du joueur local (pour le mettre en évidence)
     */
    public void setLocalPlayerId(int id) {
        this.localPlayerId = id;
        System.out.println("✓ ID joueur local: " + id);
        repaint();
    }
    
    /**
     * Retourne l'ID du joueur local
     */
    public int getLocalPlayerId() {
        return localPlayerId;
    }

    /**
     * Met à jour la position d'un joueur
     */
    public void updateJoueur(Joueur updated) {
        synchronized (joueurs) {
            for (Joueur j : joueurs) {
                if (j.getid() == updated.getid()) {
                    j.setX(updated.getX());
                    j.setY(updated.getY());
                    break;
                }
            }
        }
        repaint();
    }

    /**
     * Retire un joueur de l'arène
     */
    public void removeJoueur(int id) {
        synchronized (joueurs) {
            joueurs.removeIf(j -> j.getid() == id);
            System.out.println("✓ Joueur retiré: ID " + id);
        }
        repaint();
    }
    
    /**
     * Retourne la liste des joueurs (pour debug)
     */
    public Vector<Joueur> getJoueurs() {
        return joueurs;
    }
}