package vue;

import java.awt.*;
import javax.swing.*;
import modele.client.*;
import modele.server.GameServer;
import java.util.Vector;

public class Arena extends JPanel {
    
    // Dimensions de l'arène (fixées dans Protocol)
    private static final int ARENA_WIDTH = 800;
    private static final int ARENA_HEIGHT = 600;
    private static final int PLAYER_SIZE = 20;
    public static final float PLAYER_SPEED = 5.0f;
    
    // Liste des joueurs à afficher
    private Vector<Joueur> joueurs;
    
    // ID du joueur local (pour le mettre en évidence)
    private int localPlayerId = -1;
    
    // Couleurs
    private static final Color ARENA_BG = new Color(20, 20, 20);
    private static final Color GRID_COLOR = new Color(40, 40, 40);
    private static final Color LOCAL_PLAYER_COLOR = new Color(70, 130, 180);
    private static final Color OTHER_PLAYER_COLOR = new Color(220, 100, 50);

    public Arena(GameServer gameServer) {
        joueurs = new Vector<>();
        
        setPreferredSize(new Dimension(ARENA_WIDTH, ARENA_HEIGHT));
        setBackground(ARENA_BG);
        setFocusable(true);

        Vector<Client> clients = gameServer.getclients();
         
        for (Client client : clients) {
            addPlayer(client);
        }
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
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(GRID_COLOR);
        int gridSize = 50;
        
        // Lignes verticales
        for (int x = 0; x < ARENA_WIDTH; x += gridSize) {
            g2d.drawLine(x, 0, x, ARENA_HEIGHT);
        }
        
        // Lignes horizontales
        for (int y = 0; y < ARENA_HEIGHT; y += gridSize) {
            g2d.drawLine(0, y, ARENA_WIDTH, y);
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
        g2d.fillRect(joueur.getX(), joueur.getY(), PLAYER_SIZE, PLAYER_SIZE);
        
        // Bordure
        g2d.setColor(Color.WHITE);
        g2d.drawRect(joueur.getX(), joueur.getY(), PLAYER_SIZE, PLAYER_SIZE);
        
        // Afficher le pseudo au-dessus
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(joueur.getPseudo());
        int textX = joueur.getX() + (PLAYER_SIZE - textWidth) / 2;
        int textY = joueur.getY() - 5;
        
        // Ombre du texte
        g2d.setColor(Color.BLACK);
        g2d.drawString(joueur.getPseudo(), textX + 1, textY + 1);
        
        // Texte
        g2d.setColor(Color.WHITE);
        g2d.drawString(joueur.getPseudo(), textX, textY);
    }

    private void drawInstructions(Graphics2D g2d) {
        g2d.setColor(new Color(150, 150, 150));
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        String instructions = "Utilisez les flèches ou ZQSD pour vous déplacer";
        g2d.drawString(instructions, 10, ARENA_HEIGHT - 10);
    }

    
    /*Ajoute ou met à jour un joueur dans l'arène*/
    public void addPlayer(Client client) {
        synchronized (joueurs) {
            Joueur joueur = client.getJoueur();

            joueurs.add(joueur);
            if (joueur.getIsHost()) {
                localPlayerId = joueur.getid();
            }
        }
        repaint();
    }
    
    /*Met à jour la position d'un joueur*/
    // public void updatePlayerPosition(int id, int x, int y) {
    //     synchronized (players) {
    //         PlayerDisplay player = players.get(id);
    //         if (player != null) {
    //             player.x = x;
    //             player.y = y;
    //         }
    //     }
    //     repaint();
    // }
    
    // /*Retire un joueur de l'arène*/
    // public void removePlayer(int id) {
    //     synchronized (players) {
    //         players.remove(id);
    //     }
    //     repaint();
    // }
    
    // /*Met à jour tous les joueurs d'un coup (pour les mises à jour du serveur)*/
    // public void updateAllPlayers(Map<Integer, PlayerDisplay> newPlayers) {
    //     synchronized (players) {
    //         players.clear();
    //         players.putAll(newPlayers);
    //     }
    //     repaint();
    // }
    
    // /*Efface tous les joueurs*/
    // public void clearPlayers() {
    //     synchronized (players) {
    //         players.clear();
    //     }
    //     repaint();
    // }
}