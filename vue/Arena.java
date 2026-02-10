package vue;

import java.awt.*;
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
    
    // Couleurs
    private static final Color ARENA_BG = new Color(20, 20, 20);
    private static final Color GRID_COLOR = new Color(40, 40, 40);
    private static final Color LOCAL_PLAYER_COLOR = new Color(70, 130, 180);
    private static final Color OTHER_PLAYER_COLOR = new Color(220, 100, 50);

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
        
        System.out.println("Arena créée avec " + joueurs.size() + " joueur(s)");
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