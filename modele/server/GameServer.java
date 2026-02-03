package modele.server;

import java.net.*;
import java.util.Vector;
import modele.client.*;

public class GameServer {
    ServerSocket serverSocket;
    Socket socket;
    int nombre_joueurs;
    Vector<Client> clients;

    /*Constructeur*/
    public GameServer(){
        clients=new Vector<>();
    }

    /*Setters*/
    public void setServeurSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public void setNombre_joueurs(int nombre_joueurs) {
        this.nombre_joueurs = nombre_joueurs;
    }

    public void setclients(Vector<Client> clients) {
        this.clients = clients;
    }
    
    /*Getters*/
    public ServerSocket getServeurSocket() {
        return serverSocket;
    }

    public Socket getSocket() {
        return socket;
    }

    public int getNombre_joueurs() {
        return nombre_joueurs;
    }

    public Vector<Client> getclients() {
        return clients;
    }
}