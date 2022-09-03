package nextpresso.server.core;

import nextpresso.model.NetSocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to handle all connections that are file socket based
 */
public class FileService implements Runnable {

    protected final Map<String, FileManager> transferUsers;

    private final ServerSocket fileSocketIntro;

    /**
     * Create a new file service to handle file transports
     * @param filePort port to listen on
     */
    public FileService(int filePort) throws IOException {
        fileSocketIntro = new ServerSocket(filePort);
        transferUsers = Collections.synchronizedMap(new HashMap<>());

        System.out.println("<<< Server \"Latte\" now listens for files on port " + filePort + " >>>");
    }

    /**
     * Run the main logic of the service
     */
    public void run(){
        int threadID = 0; //used to give ID to the dynamically spawned threads
        while (true) {
            try {
                Socket socket = fileSocketIntro.accept();
                Thread socketThread = new Thread(new FileManager(new NetSocket(socket), transferUsers), "FileThread-" + threadID);
                socketThread.start();
                threadID++;
            } catch (IOException e) {
                System.err.println("> An error happen when opening a new file socket");
                System.err.println("[IO exception]: " + e.getMessage());
            }
        }
    }
}
