package modele.common;

public class Protocol {
    
    /*RÉSEAU*/
    public static int SERVER_PORT = 4018;
    public static String DEFAULT_SERVER_HOST = "none"; // IP par défaut
    public static final int SOCKET_TIMEOUT_MS = 5000; // Timeout pour détection déconnexion
    public static final int MAX_PACKET_SIZE = 1024;
    
    /*MULTICAST*/
    public static final String MULTICAST_GROUP = "239.1.1.1";
    public static final int DISCOVERY_PORT = 9876;

    /*JEU */
    // Boucle de jeu
    public static final int TICK_RATE = 10; // Mises à jour par seconde (10 Hz)
    public static final int TICK_DELAY_MS = 1000 / TICK_RATE;
    
    // Arène
    public static final int ARENA_WIDTH = 800;
    public static final int ARENA_HEIGHT = 600;
    public static final int PLAYER_SIZE = 20;
    public static final float PLAYER_SPEED = 5.0f;
    
    // Zone de capture
    public static final int ZONE_SIZE = 100;             // Taille de la zone (carré)
    public static final int CAPTURE_TIME_SECONDS = 10;   // Temps pour capturer la zone
    public static final int ZONE_SPAWN_DELAY_MS = 3000;  // Délai avant apparition de la première zone
    public static final int ZONE_RESPAWN_DELAY_MS = 5000; // Délai avant réapparition après capture
    public static int SCORE_TO_WIN = 3;                   // Score à atteindre pour gagner (modifiable)
    
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
    public static final String MSG_SERVER_FULL = "SERVER_FULL"; // Serveur plein
    public static final String MSG_SERVER_STOP = "SERVER_STOP"; // Serveur arrêté
    public static final String MSG_ZONE_SPAWN = "ZONE_SPAWN";   // Nouvelle zone: ZONE_SPAWN|x|y|w|h
    public static final String MSG_ZONE_UPDATE = "ZONE_UPD";    // Mise à jour: ZONE_UPD|capturingId|pseudo|progress
    public static final String MSG_ZONE_CAPTURED = "ZONE_CAP";  // Capturée: ZONE_CAP|winnerId|pseudo
    public static final String MSG_ZONE_RESET = "ZONE_RST";     // Zone disparaît avant respawn
    public static final String MSG_GAME_WON = "GAME_WON";      // Victoire finale: GAME_WON|winnerId|pseudo|score
    public static final String MSG_SCORE_UPDATE = "SCORE_UPD";  // Score: SCORE_UPD|id1|pseudo1|score1|id2|pseudo2|score2|...
    
    // Bidirectionnel
    public static final String MSG_MOVE = "MOVE";            // Mouvement joueur: MOVE|id|x|y
    
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