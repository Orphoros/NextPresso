package nextpresso.server.core;

import nextpresso.model.NetSocket;
import nextpresso.tools.ApiProtocol;
import nextpresso.model.NextPressoException;
import nextpresso.model.NextPressoMessageBuilder;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * Service to handle all connections that are NPP message based
 */
public class MessageService implements Runnable{
    private final Map<String, MessageManager> connectedUsers;
    private final Map<String, Map<String,Long>> groups; //Each group name holds a map of usernames and their last activity
    private final Map<String, String> userPublicKeys; //Username, Base64Key

    private final ServerSocket messageSocketIntro;
    private final Map<String, FileManager> transferUsers;

    /**
     * Create a new Message Service for the server
     * @param messagePort Port of the service to listen on
     * @param fileServer Port of the file service
     */
    public MessageService(int messagePort, FileService fileServer) throws IOException {
        messageSocketIntro = new ServerSocket(messagePort);
        this.transferUsers = fileServer.transferUsers;
        connectedUsers = Collections.synchronizedMap(new HashMap<>());
        groups = Collections.synchronizedMap(new HashMap<>());
        userPublicKeys = Collections.synchronizedMap(new HashMap<>());

        System.out.println("<<< Server \"Latte\" now listens for messages on port " + messagePort + " >>>");
    }

    /**
     * Create a new thread with a new message manager for each new incoming connection
     */
    public void run() {
        watchGroupInactivity();
        int threadID = 0;
        while (true) {
            Socket socket = null;
            try {
                //For each new connection create a new socket
                socket = messageSocketIntro.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(socket != null) {
                Thread socketThread = new Thread(new MessageManager(new NetSocket(socket), connectedUsers, groups, transferUsers, userPublicKeys), "ConnectionThread-" + threadID);
                socketThread.start();
                threadID++;
            }
        }
    }

    /**
     * Kick a user from a group if user is idle for more than 2 minutes
     */
    private void watchGroupInactivity(){
        Timer timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Map<String, Map<String, Long>> tempGroups = new HashMap<>(groups);
                tempGroups.forEach((groupname,userActivityMap) -> {
                    Map<String, Long> tempUserAct = new HashMap<>(userActivityMap);
                    tempUserAct.forEach((username, lastActivity) -> {
                        if(System.currentTimeMillis() - lastActivity > 120000 ){ //2 minutes inactivity
                            groups.get(groupname).remove(username);
                            try {
                                //Inform user about being kicked
                                connectedUsers.get(username).addExchangeMessage(new NextPressoMessageBuilder(ApiProtocol.MESSAGE_SERVER_INFO,"You have been kicked from group '" + groupname + "' due to inactivity!").sender("SERVER"));
                            } catch (NextPressoException e) {
                                System.err.println("> Could not notify user about being kicked from a group!");
                            }finally {
                                System.out.println("> Removed '" + username + "' from group '" + groupname + "' due to inactivity!");
                            }
                        }
                    });
                });
            }
        };
        //First run after 2 minute, then repeat every second
        timer.schedule(task, 120000,1000);
    }
}
