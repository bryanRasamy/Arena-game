package modele.common;

public class CaptureZone {
    private int x;
    private int y;
    private int width;
    private int height;
    private int capturingPlayerId;   // ID du joueur en train de capturer (-1 si aucun)
    private String capturingPlayerName; // Pseudo du joueur en train de capturer
    private double captureProgress;  // Progression de 0.0 à 1.0
    private boolean captured;        // Zone entièrement capturée
    private boolean active;          // Zone actuellement active

    public CaptureZone() {
        this.capturingPlayerId = -1;
        this.capturingPlayerName = "";
        this.captureProgress = 0.0;
        this.captured = false;
        this.active = false;
    }

    public CaptureZone(int x, int y, int width, int height) {
        this();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.active = true;
    }

    /*Vérifie si un joueur (rectangle) est dans la zone*/
    public boolean containsPlayer(int playerX, int playerY, int playerSize) {
        // Le centre du joueur doit être dans la zone
        int playerCenterX = playerX + playerSize / 2;
        int playerCenterY = playerY + playerSize / 2;
        return playerCenterX >= x && playerCenterX <= x + width
            && playerCenterY >= y && playerCenterY <= y + height;
    }

    /*Setters*/
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setCapturingPlayerId(int id) { this.capturingPlayerId = id; }
    public void setCapturingPlayerName(String name) { this.capturingPlayerName = name; }
    public void setCaptureProgress(double progress) { this.captureProgress = progress; }
    public void setCaptured(boolean captured) { this.captured = captured; }
    public void setActive(boolean active) { this.active = active; }

    /*Getters*/
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getCapturingPlayerId() { return capturingPlayerId; }
    public String getCapturingPlayerName() { return capturingPlayerName; }
    public double getCaptureProgress() { return captureProgress; }
    public boolean isCaptured() { return captured; }
    public boolean isActive() { return active; }

    /*Réinitialise la progression de capture*/
    public void resetCapture() {
        this.capturingPlayerId = -1;
        this.capturingPlayerName = "";
        this.captureProgress = 0.0;
        this.captured = false;
    }
}
