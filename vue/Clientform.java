package vue;

import java.awt.*;
import javax.swing.*;

import modele.client.Joueur;
import modele.common.Protocol;
import modele.server.GameServer;

public class Clientform extends JPanel {
    private MainFrame parentFrame;
    private JTextField txtPseudo;
    private JComboBox<String> comboServers;
    private JButton btnRefresh;
    private JButton btnConnect;
    private JButton btnBack;

    public Clientform(MainFrame parentFrame) {
        this.parentFrame = parentFrame;
        
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        // Titre
        JLabel title = new JLabel("REJOINDRE UNE SESSION", JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(30, 10, 30, 10));
        add(title, BorderLayout.NORTH);

        // Formulaire central
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        formPanel.setBackground(getBackground());
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Pseudo
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(createLabel("Pseudo :"), gbc);
        
        gbc.gridx = 1;
        txtPseudo = createTextField("Joueur2");
        formPanel.add(txtPseudo, gbc);

        // Liste des serveurs disponibles
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(createLabel("Serveurs disponibles :"), gbc);
        
        gbc.gridx = 1;
        JPanel serverPanel = new JPanel(new BorderLayout(5, 0));
        serverPanel.setBackground(getBackground());
        
        comboServers = new JComboBox<>();
        comboServers.setFont(new Font("Arial", Font.PLAIN, 14));
        
        // Ajouter des serveurs d'exemple (TODO: remplacer par scan réseau)
        comboServers.addItem("127.0.0.1:4018 - Serveur Local");
        comboServers.addItem("192.168.1.100:4018 - Serveur de test");
        
        btnRefresh = new JButton("🔄");
        btnRefresh.setFocusPainted(false);
        btnRefresh.setBackground(new Color(70, 130, 180));
        btnRefresh.setForeground(Color.WHITE);
        btnRefresh.setPreferredSize(new Dimension(40, 25));
        btnRefresh.addActionListener(e -> refreshServerList());
        
        serverPanel.add(comboServers, BorderLayout.CENTER);
        serverPanel.add(btnRefresh, BorderLayout.EAST);
        formPanel.add(serverPanel, gbc);

        // Info
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JLabel info = new JLabel("<html><center>Cliquez sur 🔄 pour actualiser<br>la liste des serveurs</center></html>", 
                                  JLabel.CENTER);
        info.setFont(new Font("Arial", Font.ITALIC, 11));
        info.setForeground(new Color(150, 150, 150));
        formPanel.add(info, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Boutons en bas
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        buttonPanel.setBackground(getBackground());

        btnBack = createButton("Retour", new Color(100, 100, 100));
        btnConnect = createButton("Se connecter", new Color(34, 139, 34));

        btnBack.addActionListener(e -> goBack());
        btnConnect.addActionListener(e -> connectToServer());

        buttonPanel.add(btnBack);
        buttonPanel.add(btnConnect);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setForeground(Color.WHITE);
        return label;
    }

    private JTextField createTextField(String defaultValue) {
        JTextField field = new JTextField(defaultValue, 20);
        field.setFont(new Font("Arial", Font.PLAIN, 14));
        return field;
    }

    private JButton createButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Arial", Font.PLAIN, 14));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        return btn;
    }

    private void goBack() {
        parentFrame.getContentPane().removeAll();
        parentFrame.add(new MainPanel(parentFrame), BorderLayout.CENTER);
        parentFrame.revalidate();
        parentFrame.repaint();
    }

    private void refreshServerList() {
        // TODO: Implémenter le scan des serveurs disponibles sur le réseau
        // Pour l'instant, on simule juste un refresh
        comboServers.removeAllItems();
        comboServers.addItem("127.0.0.1:4018 - Serveur Local");
        comboServers.addItem("192.168.1.100:4018 - Partie de test");
        comboServers.addItem("10.111.236.81:4018 - Serveur distant");
        
        JOptionPane.showMessageDialog(this, 
            "Liste des serveurs actualisée !", 
            "Actualisation", 
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void connectToServer() {
        String pseudo = txtPseudo.getText().trim();
        String selectedServer = (String) comboServers.getSelectedItem();

        // Validation
        if (pseudo.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Veuillez entrer un pseudo !", 
                "Erreur", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (selectedServer == null) {
            JOptionPane.showMessageDialog(this, 
                "Veuillez sélectionner un serveur !", 
                "Erreur", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Extraire IP et Port du format "IP:PORT - Description"
        String serverAddress = selectedServer.split(" - ")[0];
        String[] parts = serverAddress.split(":");
        String serverIP = parts[0];
        int serverPort = Integer.parseInt(parts[1]);

        Protocol.DEFAULT_SERVER_HOST=serverIP;

        GameServer server=new GameServer();

        Joueur joueur=new Joueur();
        joueur.setPseudo(pseudo);
        joueur.setX((int)(Math.random() * 1000) - 200 + 1);
        joueur.setY((int)(Math.random() * 1000) - 400 + 1);

        // Lancer l'affichage client
        parentFrame.getContentPane().removeAll();
        parentFrame.add(new ClientGame(parentFrame, joueur, server), BorderLayout.CENTER);
        parentFrame.revalidate();
        parentFrame.repaint();
    }

    // Getters pour tests
    public String getPseudo() { return txtPseudo.getText().trim(); }
    public String getSelectedServer() { return (String) comboServers.getSelectedItem(); }
}