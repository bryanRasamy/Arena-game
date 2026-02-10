package vue;

import java.awt.*;
import java.io.IOException;

import javax.swing.*;
import javax.swing.border.*;

import modele.client.*;
import modele.server.*;
import modele.common.*;
import service.ServerService;

public class HostGame extends JPanel {
    private MainFrame parentFrame;
    private GameServer gameServer;
    private Joueur joueurHost;
    
    // Composants UI
    private Arena arenaPanel;
    private JTextArea txtPlayerList;
    private JLabel lblPlayerCount;
    private JLabel lblServerStatus;
    private JLabel lblServerInfo;
    private JButton btnStopServer;

    public HostGame(MainFrame parentFrame, Joueur joueurHost, GameServer server) {
        this.parentFrame = parentFrame;
        this.joueurHost = joueurHost;
        this.gameServer = server;
        
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        // Panel de gauche : Arène 
        arenaPanel = new Arena(new GameServer());
        arenaPanel.setHostServer(gameServer);       // Permettre à l'hôte d'envoyer les mouvements
        Client.setHostArena(arenaPanel);            // Pour que le serveur mette à jour l'arène de l'hôte
        add(arenaPanel, BorderLayout.CENTER);

        // Panel de droite : Contrôles serveur 
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.EAST);

        // Démarrage du serveur
        startGameServer(gameServer);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(280, 0));
        panel.setBackground(new Color(40, 40, 40));
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Titre
        JLabel title = new JLabel("CONTRÔLE SERVEUR", JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 16));
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // Info serveur
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(new Color(50, 50, 50));
        infoPanel.setBorder(new CompoundBorder(
            new LineBorder(new Color(70, 130, 180), 2),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        lblServerStatus = createInfoLabel("Serveur actif");
        lblServerStatus.setForeground(new Color(50, 205, 50));
        
        lblServerInfo = createInfoLabel("IP: " + Protocol.DEFAULT_SERVER_HOST + ":" + Protocol.SERVER_PORT);
        
        lblPlayerCount = createInfoLabel("Joueurs: 0/" + gameServer.getNombre_joueurs());

        infoPanel.add(lblServerStatus);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        infoPanel.add(lblServerInfo);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        infoPanel.add(lblPlayerCount);

        // Liste des joueurs
        JPanel playerListPanel = new JPanel(new BorderLayout());
        playerListPanel.setBackground(panel.getBackground());
        
        JLabel playerListTitle = new JLabel("Joueurs connectés:");
        playerListTitle.setFont(new Font("Arial", Font.BOLD, 13));
        playerListTitle.setForeground(Color.WHITE);
        playerListTitle.setBorder(BorderFactory.createEmptyBorder(15, 0, 5, 0));

        txtPlayerList = new JTextArea();
        txtPlayerList.setEditable(false);
        txtPlayerList.setBackground(new Color(50, 50, 50));
        txtPlayerList.setForeground(Color.WHITE);
        txtPlayerList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(txtPlayerList);
        scrollPane.setPreferredSize(new Dimension(260, 150));
        scrollPane.setBorder(new LineBorder(new Color(60, 60, 60)));

        playerListPanel.add(playerListTitle, BorderLayout.NORTH);
        playerListPanel.add(scrollPane, BorderLayout.CENTER);

        // Statistiques
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBackground(panel.getBackground());
        statsPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));

        JLabel statsTitle = new JLabel("Statistiques:");
        statsTitle.setFont(new Font("Arial", Font.BOLD, 13));
        statsTitle.setForeground(Color.WHITE);
        statsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblUptime = createSmallInfoLabel("Temps: 0:00");
        JLabel lblPing = createSmallInfoLabel("Ping moyen: -- ms");
        JLabel lblPackets = createSmallInfoLabel("Paquets: 0");

        statsPanel.add(statsTitle);
        statsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        statsPanel.add(lblUptime);
        statsPanel.add(lblPing);
        statsPanel.add(lblPackets);

        // Bouton arrêter
        btnStopServer = new JButton("⚠ ARRÊTER LE SERVEUR");
        btnStopServer.setFocusPainted(false);
        btnStopServer.setFont(new Font("Arial", Font.BOLD, 13));
        btnStopServer.setBackground(new Color(220, 20, 60));
        btnStopServer.setForeground(Color.WHITE);
        btnStopServer.setOpaque(true);
        btnStopServer.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        btnStopServer.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnStopServer.addActionListener(e -> stopServer());

        // Assemblage
        JPanel centerContent = new JPanel();
        centerContent.setLayout(new BoxLayout(centerContent, BoxLayout.Y_AXIS));
        centerContent.setBackground(panel.getBackground());
        centerContent.add(infoPanel);
        centerContent.add(playerListPanel);
        centerContent.add(statsPanel);

        panel.add(title, BorderLayout.NORTH);
        panel.add(centerContent, BorderLayout.CENTER);
        panel.add(btnStopServer, BorderLayout.SOUTH);

        return panel;
    }

    private JLabel createInfoLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.PLAIN, 13));
        label.setForeground(Color.WHITE);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel createSmallInfoLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.PLAIN, 11));
        label.setForeground(new Color(180, 180, 180));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void startGameServer(GameServer gameServer) {
        System.out.println("=== DÉMARRAGE DU SERVEUR ===");
        System.out.println("Hôte: " + joueurHost.getPseudo());
        System.out.println("IP: " + Protocol.DEFAULT_SERVER_HOST + ":" + Protocol.SERVER_PORT);
        System.out.println("Max joueurs: " + gameServer.getNombre_joueurs());
        
        try {
            // Passer l'arène au ServerService pour qu'il puisse y ajouter les joueurs
            ServerService.startGameServer(gameServer, joueurHost, arenaPanel);
            System.out.println("✓ Serveur démarré avec succès");
            
            // Timer pour mettre à jour l'affichage régulièrement
            Timer updateTimer = new Timer(500, e -> {
                updatePlayerListDisplay();
                updatePlayerCount(gameServer.getclients().size(), gameServer.getNombre_joueurs());
                arenaPanel.repaint();
            });
            updateTimer.start();
            
        } catch (IOException e) {
            System.err.println("✗ Erreur lors du démarrage du serveur: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Erreur lors du démarrage du serveur:\n" + e.getMessage(),
                "Erreur",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updatePlayerListDisplay() {
        StringBuilder playerList = new StringBuilder();
        for (Client client : gameServer.getclients()) {
            Joueur j = client.getJoueur();
            playerList.append(j.getPseudo());
            if (j.getIsHost()) {
                playerList.append(" (Hôte)");
            }
            playerList.append("\n");
        }
        txtPlayerList.setText(playerList.toString());
    }

    private void stopServer() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Êtes-vous sûr de vouloir arrêter le serveur ?\nTous les joueurs seront déconnectés.",
            "Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // Arrêter le serveur via le service (ferme connexions + multicast)
            ServerService.stopServer(gameServer);
            
            // Retour au menu
            parentFrame.getContentPane().removeAll();
            parentFrame.add(new MainPanel(parentFrame), BorderLayout.CENTER);
            parentFrame.revalidate();
            parentFrame.repaint();
        }
    }

    // Méthodes publiques pour mettre à jour l'interface depuis le serveur
    public void updatePlayerList(String playerListText) {
        SwingUtilities.invokeLater(() -> {
            txtPlayerList.setText(playerListText);
        });
    }

    public void updatePlayerCount(int current, int max) {
        SwingUtilities.invokeLater(() -> {
            lblPlayerCount.setText("Joueurs: " + current + "/" + max);
        });
    }

    public Arena getArenaPanel() {
        return arenaPanel;
    }
}