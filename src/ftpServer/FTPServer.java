package ftpServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FTPServer {
    private final static String dir= "F:\\Server";

    public static void main(String[] args){
        try {
            ServerSocket serverSocket = new ServerSocket(1025);
            while(true){
                Socket client = serverSocket.accept();
                new ClientThread(client,dir).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
