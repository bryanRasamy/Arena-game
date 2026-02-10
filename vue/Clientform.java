package vue;

import java.awt.*;
import javax.swing.*;
import modele.client.*;
import java.util.List;
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
        comboServers.addItem("Recherche en cours...");
        comboServers.setEnabled(false);
        
        btnRefresh = new JButton("Actualiser");
        btnRefresh.setFocusPainted(false);
        btnRefresh.setBackground(new Color(70, 130, 180));
        btnRefresh.setForeground(Color.WHITE);
        btnRefresh.addActionListener(e -> refreshServerList());
        
        
        serverPanel.add(comboServers, BorderLayout.CENTER);
        serverPanel.add(btnRefresh, BorderLayout.EAST);
        formPanel.add(serverPanel, gbc);

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

        refreshServerList();
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
        // Désactiver pendant la recherche
        comboServers.setEnabled(false);
        btnRefresh.setEnabled(false);
        btnConnect.setEnabled(false);
        
        comboServers.removeAllItems();
        comboServers.addItem("Recherche en cours...");
        
        new Thread(() -> {
            List<String> listeIPs = Client.discoverStreamers(3000);
            
            SwingUtilities.invokeLater(() -> {
                comboServers.removeAllItems();
                
                if (listeIPs.isEmpty()) {
                    comboServers.addItem("Aucun serveur trouvé");
                    System.out.println("Aucun serveur découvert");
                } else {
                    for (String ip : listeIPs) {
                        comboServers.addItem(ip.trim());
                        System.out.println("Serveur trouvé : " + ip);
                    }
                    btnConnect.setEnabled(true);
                }
                
                comboServers.setEnabled(true);
                btnRefresh.setEnabled(true);
            });
        }).start();
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

        // Extraire IP et Port (format "IP:PORT" ou "IP" seul)
        String serverAddress = selectedServer.trim();
        String serverIP;
        int serverPort;
        if (serverAddress.contains(":")) {
            String[] parts = serverAddress.split(":");
            serverIP = parts[0];
            serverPort = Integer.parseInt(parts[1]);
        } else {
            serverIP = serverAddress;
            serverPort = Protocol.SERVER_PORT; // Port par défaut
        }

        Protocol.DEFAULT_SERVER_HOST = serverIP;
        Protocol.SERVER_PORT = serverPort;

        GameServer server = new GameServer();

        Joueur joueur = new Joueur();
        joueur.setPseudo(pseudo);

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