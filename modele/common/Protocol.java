package modele.common;

public class Protocol {
    
    /*RÉSEAU*/
    public static int SERVER_PORT = 4018;
    public static String DEFAULT_SERVER_HOST = "none"; // IP par défaut
    public static final int SOCKET_TIMEOUT_MS = 5000; // Timeout pour détection déconnexion
    public static final int MAX_PACKET_SIZE = 1024;
    
    /*JEU */
    // Boucle de jeu
    public static final int TICK_RATE = 10; // Mises à jour par seconde (10 Hz)
    public static final int TICK_DELAY_MS = 1000 / TICK_RATE;
    
    // Arène
    public static final int ARENA_WIDTH = 800;
    public static final int ARENA_HEIGHT = 600;
    public static final int PLAYER_SIZE = 20;
    public static final float PLAYER_SPEED = 5.0f;
    
    /*TYPES DE MESSAGES*/
    // Client -> Serveur
    public static final String MSG_CONNECT = "CONNECT";      // Connexion initiale
    public static final String MSG_INPUT = "INPUT";          // Entrée joueur (direction)
    public static final String MSG_DISCONNECT = "DISCONNECT"; // Déconnexion propre
    public static final String MSG_PING = "PING";            // Vérification latence
    
    // Serveur -> Client
    public static final String MSG_WELCOME = "WELCOME";      // Confirmation connexion + ID
    public static final String MSG_GAME_STATE = "STATE";     // État complet du jeu
    public static final String MSG_PLAYER_JOINED = "JOINED"; // Nouveau joueur
    public static final String MSG_PLAYER_LEFT = "LEFT";     // Joueur parti
    public static final String MSG_PONG = "PONG";            // Réponse ping
    public static final String MSG_ERROR = "ERROR";          // Erreur
    
    /*DIRECTIONS*/
    public static final String DIR_UP = "UP";
    public static final String DIR_DOWN = "DOWN";
    public static final String DIR_LEFT = "LEFT";
    public static final String DIR_RIGHT = "RIGHT";
    public static final String DIR_NONE = "NONE";
    
    /*SÉPARATEURS*/
    public static final String SEPARATOR = "|"; // Séparateur entre champs
    public static final String END_MESSAGE = "\n"; // Fin de message
    
    /*UTILITAIRES*/
    public static long getTickDelayNanos() {
        return TICK_DELAY_MS * 1_000_000L;
    }
    
    public static String buildMessage(String type, String... data) {
        StringBuilder msg = new StringBuilder(type);
        for (String field : data) {
            msg.append(SEPARATOR).append(field);
        }
        msg.append(END_MESSAGE);
        return msg.toString();
    }
    
    public static String[] parseMessage(String message) {
        message = message.trim();
        return message.split("\\" + SEPARATOR);
    }
}