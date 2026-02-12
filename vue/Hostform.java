package vue;

import java.awt.*;

import javax.swing.*;
import modele.common.*;
import modele.server.*;
import modele.client.*;
import service.*;

public class Hostform extends JPanel {
    private MainFrame parentFrame;
    private JTextField txtPseudo;
    private JSpinner spinMaxPlayers;
    private JSpinner spinScoreTarget;
    private JButton btnStart;
    private JButton btnBack;

    public Hostform(MainFrame parentFrame) {
        this.parentFrame = parentFrame;
        
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        // Titre
        JLabel title = new JLabel("CRÉER UNE SESSION", JLabel.CENTER);
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
        txtPseudo = createTextField("Joueur1");
        formPanel.add(txtPseudo, gbc);
        
        // Nombre max de joueurs
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(createLabel("Nombre max de joueurs :"), gbc);
        
        gbc.gridx = 1;
        SpinnerNumberModel spinModel = new SpinnerNumberModel(4, 2, 10, 1);
        spinMaxPlayers = new JSpinner(spinModel);
        spinMaxPlayers.setFont(new Font("Arial", Font.PLAIN, 14));
        ((JSpinner.DefaultEditor) spinMaxPlayers.getEditor()).getTextField().setEditable(false);
        formPanel.add(spinMaxPlayers, gbc);

        // Score cible
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(createLabel("Score pour gagner :"), gbc);
        
        gbc.gridx = 1;
        SpinnerNumberModel scoreModel = new SpinnerNumberModel(Protocol.SCORE_TO_WIN, 1, 100, 1);
        spinScoreTarget = new JSpinner(scoreModel);
        spinScoreTarget.setFont(new Font("Arial", Font.PLAIN, 14));
        ((JSpinner.DefaultEditor) spinScoreTarget.getEditor()).getTextField().setEditable(false);
        formPanel.add(spinScoreTarget, gbc);

        // Info port (non modifiable)
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        JLabel portInfo = new JLabel("Port utilisé : " + Protocol.SERVER_PORT + " (par défaut)", JLabel.CENTER);
        portInfo.setFont(new Font("Arial", Font.ITALIC, 12));
        portInfo.setForeground(new Color(150, 150, 150));
        formPanel.add(portInfo, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Boutons en bas
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        buttonPanel.setBackground(getBackground());

        btnBack = createButton("Retour", new Color(100, 100, 100));
        btnStart = createButton("Démarrer le serveur", new Color(70, 130, 180));

        btnBack.addActionListener(e -> goBack());
        btnStart.addActionListener(e -> startServer());

        buttonPanel.add(btnBack);
        buttonPanel.add(btnStart);

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

    private void startServer() {
        // Utiliser l'IP physique (WiFi/Ethernet), pas localhost
        Protocol.DEFAULT_SERVER_HOST = ServerService.getLocalPhysicalIP();
        System.out.println("IP du serveur: " + Protocol.DEFAULT_SERVER_HOST);
        
        String pseudo = txtPseudo.getText().trim();
        
        int maxPlayers = (int) spinMaxPlayers.getValue();
        int scoreTarget = (int) spinScoreTarget.getValue();

        // Validation
        if (pseudo.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Veuillez entrer un pseudo !", 
                "Erreur", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Appliquer le score cible
        Protocol.SCORE_TO_WIN = scoreTarget;

        // Création et démarrage du serveur
        GameServer server = ServerService.createServer();
        server.setNombre_joueurs(maxPlayers);

        Joueur joueurHost = new Joueur();
        joueurHost.setPseudo(pseudo);
        joueurHost.setIsHost(true);
        // La position sera assignée par ServerService.startGameServer
        
        // Lancer l'affichage hôte
        parentFrame.getContentPane().removeAll();
        parentFrame.add(new HostGame(parentFrame, joueurHost, server), BorderLayout.CENTER);
        parentFrame.revalidate();
        parentFrame.repaint();
    }

    // Getters pour tests
    public String getPseudo() { return txtPseudo.getText().trim(); }
    public int getMaxPlayers() { return (int) spinMaxPlayers.getValue(); }
}