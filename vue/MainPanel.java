package vue;

import java.awt.*;
import javax.swing.*;

public class MainPanel extends JPanel {
    private MainFrame menu;
    private JButton btnJoin;
    private JButton btnCreate;
    private JButton btnLeave;

    public MainPanel(MainFrame menu) {
        this.menu=menu;

        setPreferredSize(new Dimension(250, 0));
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 30));

        // ===== Titre =====
        JLabel title = new JLabel("MENU PRINCIPAL", JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        add(title, BorderLayout.NORTH);

        // ===== Zone boutons =====
        JPanel controller = new JPanel();
        controller.setBackground(getBackground());
        controller.setLayout(new BoxLayout(controller, BoxLayout.Y_AXIS));
        controller.setBorder(BorderFactory.createEmptyBorder(50, 20, 50, 20));

        btnJoin = createButton("Rejoindre une session");
        btnCreate = createButton("Créer une session");
        btnLeave = createButton("Quitter");

        // Action pour créer une session
        btnCreate.addActionListener(e -> openHostForm());
        btnJoin.addActionListener(e -> openClientForm());
        btnLeave.addActionListener(e -> quit());

        controller.add(btnCreate);
        controller.add(Box.createRigidArea(new Dimension(0, 15))); // espace vertical
        controller.add(btnJoin);
        controller.add(Box.createRigidArea(new Dimension(0, 15))); // espace vertical
        controller.add(btnLeave);

        // Centrer verticalement
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(getBackground());
        centerWrapper.add(controller);
        add(centerWrapper, BorderLayout.CENTER);
    }

    private JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Arial", Font.PLAIN, 14));
        btn.setBackground(new Color(70, 130, 180));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return btn;
    }

    public JButton getBtnJoin() { return btnJoin; }
    public JButton getBtnCreate() { return btnCreate; }
    public JButton getBtnLeave() { return btnLeave; }

    private void openHostForm() {
        menu.getContentPane().removeAll();
        menu.add(new Hostform(menu), BorderLayout.CENTER);
        menu.revalidate();
        menu.repaint();
    }

    private void openClientForm() {
        menu.getContentPane().removeAll();
        menu.add(new Clientform(menu), BorderLayout.CENTER);
        menu.revalidate();
        menu.repaint();
    }

    private void quit() {
        System.exit(0);
    }
}