package modele.client;

import java.io.*;
import java.net.*;
import java.util.Vector;

import modele.server.*;

public class Client implements Runnable{
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private GameServer server;
    private Joueur joueur;
    
    public Client(){

    }

    /*Setters */
    public void setSocket(Socket socket) {
        this.socket = socket;
        try {
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void setServer(GameServer server) {
        this.server = server;
    }
    
    public void setJoueur(Joueur joueur) {
        this.joueur = joueur;
    }

    /*Getters */
    public Joueur getJoueur() {
        return joueur;
    }

    public Socket getSocket() {
        return socket;
    }

    public GameServer getServer() {
        return server;
    }

    @Override
    public void run() {
        try {
            String message;

            while ((message = in.readLine()) != null) {
                //On met a jour les informations du joueur en fonction du message recu
                readMessage(message);

                Vector<Client> clients=server.getclients();

                for (Client client : clients) {
                    if (client != this) {
                        client.send(message);
                    }
                }
            }
            
        } catch (IOException e) {
            System.out.println("Client déconnecté");
        } finally {
            close();
        }
    }
    
    public void send(String message) {
        if (out != null) {
            out.println(message);
            out.flush();
        }
    }

    public void readMessage(String message){
        try {
            //Format: action|id_joueur|pseudo|x|y
            String[] parts=message.split("\\|");
            String action=parts[0];

            Joueur joueur=new Joueur();
            joueur.setid(Integer.parseInt(parts[1]));
            joueur.setPseudo(parts[2]);
            joueur.setX(Integer.parseInt(parts[3]));
            joueur.setY(Integer.parseInt(parts[4]));

            Vector<Client> clients=server.getclients();

            if(action.equals("UPDATE")){
                for (Client client : clients) {
                    if (client.getJoueur().getid() == joueur.getid()) {
                        client.setJoueur(joueur);
                        break;
                    }
                }
            }else if(action.equals("DISCONNECT")){
                for (Client client : clients) {
                    if (client.getJoueur().getid() == joueur.getid()) {
                        server.getclients().remove(client);
                        break;
                    }
                }
            }

            

        } catch (Exception e) {
            // TODO: handle exception
        }
    }
    
    // public void sendPlayerData(Joueur data) {
    //     try {
    //         out.writeInt(data.id_joueur);
    //         out.writeInt(data.x);
    //         out.writeInt(data.y);
    //         out.writeUTF(data.pseudo);
    //         out.flush();
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //     }
    // }
    
    // private Joueur receivePlayerData() throws IOException {
    //     Joueur data = new Joueur();
    //     data.id_joueur = in.readInt();
    //     data.x = in.readInt();
    //     data.y = in.readInt();
    //     data.pseudo = in.readUTF();
    //     return data;
    // }
    
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            server.getclients().remove(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
