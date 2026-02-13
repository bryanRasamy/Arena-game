package vue;

import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.*;
import modele.client.*;
import modele.common.Protocol;
import modele.common.CaptureZone;
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
    private static final Color ZONE_COLOR = new Color(255, 215, 0, 60);        // Or transparent
    private static final Color ZONE_BORDER_COLOR = new Color(255, 215, 0);     // Or
    private static final Color ZONE_PROGRESS_COLOR = new Color(50, 205, 50);   // Vert
    
    // Zone de capture
    private CaptureZone captureZone = null;
    private String captureVictoryMessage = null;
    private long victoryMessageTime = 0;
    
    // Scores des joueurs
    private Map<Integer, int[]> playerScores = new LinkedHashMap<>(); // id -> {score}
    private Map<Integer, String> playerNames = new LinkedHashMap<>();  // id -> pseudo
    
    // Victoire finale
    private String gameWonMessage = null;
    private Runnable onGameWon = null; // Callback pour retour au menu
    
    // Vitesse de déplacement en pixels par tick
    private static final int MOVE_SPEED = (int) Protocol.PLAYER_SPEED;

    public Arena(GameServer gameServer) {
        joueurs = new Vector<>();
        
        setMinimumSize(new Dimension(Protocol.ARENA_WIDTH, Protocol.ARENA_HEIGHT));
        setPreferredSize(new Dimension(Protocol.ARENA_WIDTH, Protocol.ARENA_HEIGHT));
        setBackground(Color.BLACK);
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
    
    /*Configure les Key Bindings pour le mouvement*/
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
    
    /*Boucle de jeu : applique le mouvement du joueur local et envoie au réseau*/
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
    
    /*Envoie la position du joueur local au réseau*/
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
    
    /*Définit le client réseau pour l'envoi des mouvements (côté client)*/
    public void setNetworkClient(Client client) {
        this.networkClient = client;
    }
    
    /*Définit le serveur pour le broadcast direct (côté hôte)*/
    public void setHostServer(GameServer server) {
        this.hostServer = server;
    }
    
    /*Retourne un joueur par son ID*/
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
        
        // Fond noir pour les bandes latérales (letterbox)
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        // Calculer l'échelle pour remplir le panel en gardant le ratio
        double scaleX = (double) getWidth() / Protocol.ARENA_WIDTH;
        double scaleY = (double) getHeight() / Protocol.ARENA_HEIGHT;
        double scale = Math.min(scaleX, scaleY);
        
        // Centrer l'arène dans le panel
        double offsetX = (getWidth() - Protocol.ARENA_WIDTH * scale) / 2;
        double offsetY = (getHeight() - Protocol.ARENA_HEIGHT * scale) / 2;
        
        // Appliquer la transformation (tout le dessin sera en coordonnées logiques)
        g2d.translate(offsetX, offsetY);
        g2d.scale(scale, scale);
        
        // Fond de l'arène
        g2d.setColor(ARENA_BG);
        g2d.fillRect(0, 0, Protocol.ARENA_WIDTH, Protocol.ARENA_HEIGHT);
        
        // Bordure de l'arène
        g2d.setColor(new Color(60, 60, 60));
        g2d.drawRect(0, 0, Protocol.ARENA_WIDTH - 1, Protocol.ARENA_HEIGHT - 1);
        
        // Dessiner la grille
        drawGrid(g2d);
        
        // Dessiner la zone de capture
        drawCaptureZone(g2d);
        
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
        
        // Afficher le tableau des scores
        drawScoreboard(g2d);
        
        // Afficher le message de victoire
        drawVictoryMessage(g2d);
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

    /*Dessine la zone de capture avec bordure, remplissage et barre de progression*/
    private void drawCaptureZone(Graphics2D g2d) {
        if (captureZone == null || !captureZone.isActive()) return;
        
        int zx = captureZone.getX();
        int zy = captureZone.getY();
        int zw = captureZone.getWidth();
        int zh = captureZone.getHeight();
        
        // Remplissage semi-transparent
        g2d.setColor(ZONE_COLOR);
        g2d.fillRect(zx, zy, zw, zh);
        
        // Bordure pulsée (effet visuel)
        long pulse = System.currentTimeMillis() % 1000;
        int alpha = (int)(150 + 105 * Math.sin(pulse * 2 * Math.PI / 1000));
        g2d.setColor(new Color(255, 215, 0, alpha));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRect(zx, zy, zw, zh);
        g2d.setStroke(new BasicStroke(1));
        
        // Label "ZONE" au-dessus
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.setColor(ZONE_BORDER_COLOR);
        FontMetrics fm = g2d.getFontMetrics();
        String zoneLabel = "⚑ CAPTURE ZONE";
        int labelX = zx + (zw - fm.stringWidth(zoneLabel)) / 2;
        g2d.drawString(zoneLabel, labelX, zy - 8);
        
        // Barre de progression
        if (captureZone.getCaptureProgress() > 0) {
            int barWidth = zw - 20;
            int barHeight = 8;
            int barX = zx + 10;
            int barY = zy + zh + 5;
            
            // Fond de la barre
            g2d.setColor(new Color(60, 60, 60));
            g2d.fillRect(barX, barY, barWidth, barHeight);
            
            // Progression
            int progressWidth = (int)(barWidth * captureZone.getCaptureProgress());
            g2d.setColor(ZONE_PROGRESS_COLOR);
            g2d.fillRect(barX, barY, progressWidth, barHeight);
            
            // Bordure de la barre
            g2d.setColor(Color.WHITE);
            g2d.drawRect(barX, barY, barWidth, barHeight);
            
            // Nom du joueur qui capture + pourcentage
            String capturingText = captureZone.getCapturingPlayerName() + " : " 
                + (int)(captureZone.getCaptureProgress() * 100) + "%";
            g2d.setFont(new Font("Arial", Font.BOLD, 11));
            fm = g2d.getFontMetrics();
            int textX = zx + (zw - fm.stringWidth(capturingText)) / 2;
            g2d.setColor(Color.WHITE);
            g2d.drawString(capturingText, textX, barY + barHeight + 14);
        }
    }

    /*Affiche le message de victoire (capture de zone) pendant 4 secondes*/
    private void drawVictoryMessage(Graphics2D g2d) {
        // Victoire finale (prioritaire)
        if (gameWonMessage != null) {
            drawGameWonOverlay(g2d);
            return;
        }
        
        if (captureVictoryMessage == null) return;
        
        long elapsed = System.currentTimeMillis() - victoryMessageTime;
        if (elapsed > 4000) {
            captureVictoryMessage = null;
            return;
        }
        
        // Fond semi-transparent
        g2d.setColor(new Color(0, 0, 0, 150));
        int boxW = 400;
        int boxH = 60;
        int boxX = (Protocol.ARENA_WIDTH - boxW) / 2;
        int boxY = Protocol.ARENA_HEIGHT / 2 - boxH / 2;
        g2d.fillRoundRect(boxX, boxY, boxW, boxH, 15, 15);
        
        // Bordure or
        g2d.setColor(ZONE_BORDER_COLOR);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(boxX, boxY, boxW, boxH, 15, 15);
        g2d.setStroke(new BasicStroke(1));
        
        // Texte
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.setColor(ZONE_BORDER_COLOR);
        String text = "\u2B50 " + captureVictoryMessage + " +1 point !";
        int textX = boxX + (boxW - fm.stringWidth(text)) / 2;
        int textY = boxY + boxH / 2 + fm.getAscent() / 2;
        g2d.drawString(text, textX, textY);
        
        repaint(); // Continuer à redessiner pour l'animation
    }
    
    /*dessine l'overlay de victoire finale*/
    private void drawGameWonOverlay(Graphics2D g2d) {
        // Fond sombre
        g2d.setColor(new Color(0, 0, 0, 200));
        g2d.fillRect(0, 0, Protocol.ARENA_WIDTH, Protocol.ARENA_HEIGHT);
        
        // Boîte de victoire
        int boxW = 500;
        int boxH = 120;
        int boxX = (Protocol.ARENA_WIDTH - boxW) / 2;
        int boxY = Protocol.ARENA_HEIGHT / 2 - boxH / 2;
        
        g2d.setColor(new Color(30, 30, 30));
        g2d.fillRoundRect(boxX, boxY, boxW, boxH, 20, 20);
        
        // Bordure dorée
        g2d.setColor(ZONE_BORDER_COLOR);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(boxX, boxY, boxW, boxH, 20, 20);
        g2d.setStroke(new BasicStroke(1));
        
        // Titre
        g2d.setFont(new Font("Arial", Font.BOLD, 28));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.setColor(ZONE_BORDER_COLOR);
        String title = "\uD83C\uDFC6 VICTOIRE !";
        int titleX = boxX + (boxW - fm.stringWidth(title)) / 2;
        g2d.drawString(title, titleX, boxY + 45);
        
        // Message
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        fm = g2d.getFontMetrics();
        g2d.setColor(Color.WHITE);
        int msgX = boxX + (boxW - fm.stringWidth(gameWonMessage)) / 2;
        g2d.drawString(gameWonMessage, msgX, boxY + 80);
        
        // Sous-texte
        g2d.setFont(new Font("Arial", Font.ITALIC, 13));
        fm = g2d.getFontMetrics();
        g2d.setColor(new Color(180, 180, 180));
        String sub = "Retour au menu dans quelques secondes...";
        int subX = boxX + (boxW - fm.stringWidth(sub)) / 2;
        g2d.drawString(sub, subX, boxY + 105);
    }
    
    /*dessine le tableau des scores en haut à gauche*/
    private void drawScoreboard(Graphics2D g2d) {
        if (playerScores.isEmpty()) return;
        
        int sbX = 10;
        int sbY = 10;
        int sbWidth = 180;
        int lineHeight = 18;
        int headerHeight = 25;
        int sbHeight = headerHeight + playerScores.size() * lineHeight + 25; // +25 pour le score cible
        
        // Fond semi-transparent
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillRoundRect(sbX, sbY, sbWidth, sbHeight, 10, 10);
        
        // Bordure
        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRoundRect(sbX, sbY, sbWidth, sbHeight, 10, 10);
        
        // Titre
        g2d.setFont(new Font("Arial", Font.BOLD, 13));
        g2d.setColor(ZONE_BORDER_COLOR);
        g2d.drawString("SCORES", sbX + 10, sbY + 18);
        
        // Score cible
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        g2d.setColor(new Color(180, 180, 180));
        g2d.drawString("Objectif: " + Protocol.SCORE_TO_WIN + " pts", sbX + 80, sbY + 18);
        
        // Ligne de séparation
        g2d.setColor(new Color(80, 80, 80));
        g2d.drawLine(sbX + 5, sbY + headerHeight, sbX + sbWidth - 5, sbY + headerHeight);
        
        // Joueurs et scores
        int yOffset = sbY + headerHeight + lineHeight;
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        
        for (Map.Entry<Integer, int[]> entry : playerScores.entrySet()) {
            int id = entry.getKey();
            int score = entry.getValue()[0];
            String name = playerNames.getOrDefault(id, "???");
            
            // Couleur selon si c'est le joueur local
            if (id == localPlayerId) {
                g2d.setColor(LOCAL_PLAYER_COLOR);
            } else {
                g2d.setColor(Color.WHITE);
            }
            
            g2d.drawString(name, sbX + 10, yOffset);
            g2d.drawString(String.valueOf(score), sbX + sbWidth - 30, yOffset);
            
            yOffset += lineHeight;
        }
    }

    /*Met à jour la zone de capture affichée*/
    public void setCaptureZone(CaptureZone zone) {
        this.captureZone = zone;
        repaint();
    }

    /*Retourne la zone de capture actuelle*/
    public CaptureZone getCaptureZone() {
        return captureZone;
    }

    /*Affiche le message de victoire de capture*/
    public void showCaptureVictory(String playerName) {
        this.captureVictoryMessage = playerName;
        this.victoryMessageTime = System.currentTimeMillis();
        repaint();
    }
    
    /*Affiche la victoire finale et déclenche le retour au menu après un délai*/
    public void showGameWon(String playerName, int score) {
        this.gameWonMessage = playerName + " gagne avec " + score + " points !";
        repaint();
        
        // Retour au menu après 5 secondes
        Timer returnTimer = new Timer(5000, e -> {
            if (onGameWon != null) {
                onGameWon.run();
            }
        });
        returnTimer.setRepeats(false);
        returnTimer.start();
    }
    
    /*Définit le callback appelé quand la partie est gagnée (retour au menu)*/
    public void setOnGameWon(Runnable callback) {
        this.onGameWon = callback;
    }
    
    /*Met à jour les scores depuis les données du message réseau*/
    public void updateScoresFromMessage(String[] parts) {
        // parts[0] = SCORE_UPD, puis triplets id|pseudo|score
        playerScores.clear();
        playerNames.clear();
        for (int i = 1; i + 2 < parts.length; i += 3) {
            try {
                int id = Integer.parseInt(parts[i]);
                String name = parts[i + 1];
                int score = Integer.parseInt(parts[i + 2]);
                playerScores.put(id, new int[]{score});
                playerNames.put(id, name);
            } catch (NumberFormatException e) {
                // Ignorer les entrées malformées
            }
        }
        repaint();
    }
    
    /*Met à jour les scores depuis le GameServer (côté hôte)*/
    public void updateScores(GameServer server) {
        Map<Integer, int[]> newScores = new LinkedHashMap<>();
        Map<Integer, String> newNames = new LinkedHashMap<>();
        for (Client c : new java.util.ArrayList<>(server.getclients())) {
            Joueur j = c.getJoueur();
            if (j != null) {
                newScores.put(j.getid(), new int[]{j.getScore()});
                newNames.put(j.getid(), j.getPseudo());
            }
        }
        playerScores = newScores;
        playerNames = newNames;
        repaint();
    }

    /*Ajoute ou met à jour un joueur dans l'arène depuis un Client*/
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

    /*Ajoute un joueur directement dans l'arène*/
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
    
    /*Définit l'ID du joueur local (pour le mettre en évidence)*/
    public void setLocalPlayerId(int id) {
        this.localPlayerId = id;
        System.out.println("✓ ID joueur local: " + id);
        repaint();
    }
    
    /*Retourne l'ID du joueur local*/
    public int getLocalPlayerId() {
        return localPlayerId;
    }

    /*Met à jour la position d'un joueur*/
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

    /*Retire un joueur de l'arène*/
    public void removeJoueur(int id) {
        synchronized (joueurs) {
            joueurs.removeIf(j -> j.getid() == id);
            playerScores.remove(id);
            playerNames.remove(id);
            System.out.println("✓ Joueur retiré: ID " + id);
        }
        repaint();
    }
    
    /*Retourne la liste des joueurs (pour debug)*/
    public Vector<Joueur> getJoueurs() {
        return joueurs;
    }
}