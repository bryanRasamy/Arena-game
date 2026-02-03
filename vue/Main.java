package vue;

import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {

        // Lancer l'interface graphique (EDT)
        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });

    }
}
