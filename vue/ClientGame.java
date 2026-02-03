package vue;

import java.awt.*;
import javax.swing.*;

import modele.client.Joueur;
import modele.common.Protocol;
import modele.server.GameServer;

public class ClientGame extends JPanel {
    private MainFrame parentFrame;
    private GameServer gameServer;
    private Joueur joueurHost;
    
    // Composants UI
    private Arena arenaPanel;
    private JPanel topPanel;
    private JLabel lblConnectionStatus;
    private JLabel lblPlayerInfo;
    private JButton btnDisconnect;

    public ClientGame(MainFrame parentFrame, Joueur joueurHost, GameServer server) {
        this.parentFrame = parentFrame;
        this.joueurHost = joueurHost;
        this.gameServer = server;
        
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        // Barre supérieure : Info de connexion
        topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        // Centre : Arène
        arenaPanel = new Arena(server);
        add(arenaPanel, BorderLayout.CENTER);

        // TODO: Se connecter au serveur
        connectToServer();
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(40, 40, 40));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Info de connexion (gauche)
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        infoPanel.setBackground(panel.getBackground());

        lblConnectionStatus = new JLabel("Connecté");
        lblConnectionStatus.setFont(new Font("Arial", Font.BOLD, 13));
        lblConnectionStatus.setForeground(new Color(50, 205, 50));

        lblPlayerInfo = new JLabel("Joueur: " + joueurHost.getPseudo() + " | Serveur: " + Protocol.DEFAULT_SERVER_HOST + ":" + Protocol.SERVER_PORT);
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
        // TODO: Initialiser la connexion au serveur
        System.out.println("Connexion au serveur...");
        System.out.println("Pseudo: " + joueurHost.getPseudo());
        System.out.println("Serveur: " + Protocol.DEFAULT_SERVER_HOST + ":" + Protocol.SERVER_PORT);
    }

    private void disconnect() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Êtes-vous sûr de vouloir vous déconnecter ?",
            "Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // TODO: Déconnexion propre du serveur
            System.out.println("Déconnexion...");
            
            parentFrame.getContentPane().removeAll();
            parentFrame.add(new MainPanel(parentFrame), BorderLayout.CENTER);
            parentFrame.revalidate();
            parentFrame.repaint();
        }
    }

    // Méthodes publiques pour mettre à jour l'interface depuis le client réseau
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
}