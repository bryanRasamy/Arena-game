package modele.client;

public class Joueur {
    private int id_joueur;
    private String pseudo;
    private int x;
    private int y;
    private boolean isHost;
    private int score;

    /*Constructeur*/
    public Joueur(){

    }

    /*Setters*/
    public void setid(int id_joueur){
        this.id_joueur = id_joueur;
    }

    public void setPseudo(String pseudo){
        this.pseudo = pseudo;
    }

    public void setX(int x){
        this.x = x;
    }

    public void setY(int y){
        this.y = y;
    }

    public void setIsHost(boolean isHost){
        this.isHost = isHost;
    }

    public void setScore(int score){
        this.score = score;
    }

    /*Getters*/
    public int getid(){
        return id_joueur;
    }

    public String getPseudo(){
        return pseudo;
    }

    public boolean getIsHost(){
        return isHost;
    }

    public int getX(){
        return x;
    }

    public int getY(){
        return y;
    }

    public int getScore(){
        return score;
    }
}
