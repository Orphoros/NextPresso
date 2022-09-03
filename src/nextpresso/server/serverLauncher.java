package nextpresso.server;

import nextpresso.server.core.FileService;
import nextpresso.server.core.MessageService;

import java.io.IOException;

public class serverLauncher {
    public static void main(String[] args) throws IOException {
        System.out.println("<<< Server \"Latte\" started >>>");
        //Initiate the file handler server
        FileService fileServer = new FileService(7331);
        Thread fileThread = new Thread(fileServer);

        //Initiate the message handler server
        MessageService latteMessage = new MessageService(1337,fileServer);
        Thread messageThread = new Thread(latteMessage);

        //Start the services
        messageThread.start();
        fileThread.start();
    }
}
