package vue;

import java.awt.*;
import javax.swing.*;

import modele.client.Client;
import modele.client.Joueur;
import modele.common.Protocol;
import modele.server.GameServer;
import service.ServerService;

public class ClientGame extends JPanel {
    private MainFrame parentFrame;
    private Joueur joueurLocal;
    private Client client;
    
    // Composants UI
    private Arena arenaPanel;
    private JPanel topPanel;
    private JLabel lblConnectionStatus;
    private JLabel lblPlayerInfo;
    private JButton btnDisconnect;

    public ClientGame(MainFrame parentFrame, Joueur joueurLocal, GameServer server) {
        this.parentFrame = parentFrame;
        this.joueurLocal = joueurLocal;
        
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        // Barre supérieure : Info de connexion
        topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        // Centre : Arène (créée vide au début)
        arenaPanel = new Arena(new GameServer()); // Arène vide temporaire
        add(arenaPanel, BorderLayout.CENTER);

        // Se connecter au serveur
        connectToServer();
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(40, 40, 40));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Info de connexion (gauche)
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        infoPanel.setBackground(panel.getBackground());

        lblConnectionStatus = new JLabel("Connexion...");
        lblConnectionStatus.setFont(new Font("Arial", Font.BOLD, 13));
        lblConnectionStatus.setForeground(new Color(255, 165, 0)); // Orange pour "en cours"

        lblPlayerInfo = new JLabel("Joueur: " + joueurLocal.getPseudo() + " | Serveur: " + Protocol.DEFAULT_SERVER_HOST + ":" + Protocol.SERVER_PORT);
        lblPlayerInfo.setFont(new Font("Arial", Font.PLAIN, 12));
        lblPlayerInfo.setForeground(new Color(200, 200, 200));

        infoPanel.add(lblConnectionStatus);
        infoPanel.add(new JSeparator(SwingConstants.VERTICAL));
        infoPanel.add(lblPlayerInfo);

        // Bouton déconnexion (droite)
        btnDisconnect = new JButton("Se déconnecter");
        btnDisconnect.setFocusPainted(false);
        btnDisconnect.setFont(new Font("Arial", Font.PLAIN, 12));
        btnDisconnect.setBackground(new Color(220, 20, 60));
        btnDisconnect.setForeground(Color.WHITE);
        btnDisconnect.setOpaque(true);
        btnDisconnect.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        btnDisconnect.addActionListener(e -> disconnect());

        panel.add(infoPanel, BorderLayout.CENTER);
        panel.add(btnDisconnect, BorderLayout.EAST);

        return panel;
    }

    private void connectToServer() {
        // Lancer la connexion dans un thread séparé pour ne pas bloquer l'UI
        new Thread(() -> {
            try {
                System.out.println("=== CONNEXION AU SERVEUR ===");
                System.out.println("Pseudo: " + joueurLocal.getPseudo());
                System.out.println("Serveur: " + Protocol.DEFAULT_SERVER_HOST + ":" + Protocol.SERVER_PORT);
                
                // Se connecter au serveur
                client = ServerService.connectToServer(
                    Protocol.DEFAULT_SERVER_HOST, 
                    Protocol.SERVER_PORT, 
                    joueurLocal, 
                    arenaPanel
                );
                
                if (client != null) {
                    // Connexion réussie
                    SwingUtilities.invokeLater(() -> {
                        updateConnectionStatus(true);
                        
                        // Ajouter le joueur local à l'arène
                        arenaPanel.addJoueur(joueurLocal);
                        arenaPanel.setLocalPlayerId(joueurLocal.getid());
                        
                        // Connecter l'arène au réseau pour envoyer les mouvements
                        arenaPanel.setNetworkClient(client);
                        
                        // Timer de rafraîchissement périodique de l'arène
                        javax.swing.Timer refreshTimer = new javax.swing.Timer(100, ev -> {
                            arenaPanel.repaint();
                        });
                        refreshTimer.start();
                        
                        // Callback victoire finale : retour au menu
                        arenaPanel.setOnGameWon(() -> {
                            if (client != null) {
                                client.stop();
                                client.close();
                            }
                            parentFrame.getContentPane().removeAll();
                            parentFrame.add(new MainPanel(parentFrame), BorderLayout.CENTER);
                            parentFrame.revalidate();
                            parentFrame.repaint();
                        });
                    });
                    
                    // Callback si le serveur s'arrête
                    client.setOnDisconnected(() -> {
                        JOptionPane.showMessageDialog(ClientGame.this,
                            "Vous avez été déconnecté du serveur.\n",
                            "Déconnexion",
                            JOptionPane.WARNING_MESSAGE);
                        parentFrame.getContentPane().removeAll();
                        parentFrame.add(new MainPanel(parentFrame), BorderLayout.CENTER);
                        parentFrame.revalidate();
                        parentFrame.repaint();
                    });
                    
                    // Démarrer le thread d'écoute du client
                    new Thread(client).start();
                    
                    System.out.println("✓ Client connecté et prêt!");
                    
                } else {
                    // Échec de connexion (serveur plein ou erreur réseau)
                    SwingUtilities.invokeLater(() -> {
                        String errorMsg = ServerService.lastConnectionError != null ?
                            ServerService.lastConnectionError :
                            "Impossible de se connecter au serveur.\nVérifiez que le serveur est bien démarré.";
                        
                        JOptionPane.showMessageDialog(ClientGame.this,
                            errorMsg,
                            "Erreur de connexion",
                            JOptionPane.ERROR_MESSAGE);
                        
                        // Retour au menu principal
                        parentFrame.getContentPane().removeAll();
                        parentFrame.add(new MainPanel(parentFrame), BorderLayout.CENTER);
                        parentFrame.revalidate();
                        parentFrame.repaint();
                    });
                }
                
            } catch (Exception e) {
                System.err.println("✗ Erreur lors de la connexion: " + e.getMessage());
                e.printStackTrace();
                
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(ClientGame.this,
                        "Erreur: " + e.getMessage(),
                        "Erreur de connexion",
                        JOptionPane.ERROR_MESSAGE);
                    
                    // Retour au menu principal
                    parentFrame.getContentPane().removeAll();
                    parentFrame.add(new MainPanel(parentFrame), BorderLayout.CENTER);
                    parentFrame.revalidate();
                    parentFrame.repaint();
                });
            }
        }).start();
    }

    private void disconnect() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Êtes-vous sûr de vouloir vous déconnecter ?",
            "Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            System.out.println("=== DÉCONNEXION ===");
            
            // Arrêter le client
            if (client != null) {
                client.stop();
                
                // Fermer la connexion
                client.close();
            }
            
            // Retour au menu principal
            parentFrame.getContentPane().removeAll();
            parentFrame.add(new MainPanel(parentFrame), BorderLayout.CENTER);
            parentFrame.revalidate();
            parentFrame.repaint();
        }
    }

    /*Méthodes publiques pour mettre à jour l'interface depuis le client réseau*/
    public void updateConnectionStatus(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                lblConnectionStatus.setText("Connecté");
                lblConnectionStatus.setForeground(new Color(50, 205, 50));
            } else {
                lblConnectionStatus.setText("Déconnecté");
                lblConnectionStatus.setForeground(new Color(220, 20, 60));
            }
        });
    }

    public Arena getArenaPanel() {
        return arenaPanel;
    }
    
    public Joueur getJoueurLocal() {
        return joueurLocal;
    }
    
    public Client getClient() {
        return client;
    }
}