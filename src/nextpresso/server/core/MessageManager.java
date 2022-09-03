package nextpresso.server.core;

import nextpresso.tools.ApiProtocol;
import nextpresso.model.Message;
import nextpresso.model.NextPressoException;
import nextpresso.model.NextPressoMessageBuilder;
import nextpresso.model.NetSocket;

import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Class that manages a message socket
 */
public class MessageManager implements Runnable {
    private ScheduledFuture<?> heartbeatHandler;
    private boolean isHeartbeatConfirmed;
    protected boolean userConnected;
    private final ConcurrentLinkedQueue<NextPressoMessageBuilder> pendingExchangeMessages;
    private final MessageHandler handler;
    private boolean isAuthenticated;
    private final NetSocket socket;

    /**
     * Create a new NPP message manager
     * @param socket User's socket
     * @param connectedUsers List of users and their managers
     * @param groups List of groups with their name and their user list alongside their activity indicator
     * @param transferUsers List of the 2 users who want to initiate file transfer
     * @param userPublicKeys List of stored user public keys
     */
    public MessageManager(NetSocket socket, Map<String, MessageManager> connectedUsers, Map<String, Map<String,Long>> groups, Map<String, FileManager> transferUsers, Map<String, String> userPublicKeys) {
        this.socket = socket;
        this.isHeartbeatConfirmed = false;
        this.userConnected = true;
        this.pendingExchangeMessages = new ConcurrentLinkedQueue<>();
        this.handler = new MessageHandler(this,connectedUsers, groups, transferUsers, userPublicKeys);
        this.isAuthenticated = false;
    }

    /**
     * Main logic that handles incoming requests from the socket and sends data out to the socket when needed
     */
    @Override
    public void run() {
        try {
            try {
                //Send welcome message on connection
                socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.MESSAGE_SERVER_INFO, "Welcome to Latte, a NextPresso (NPP/1.1) chat server!").buildProtocolString());
                //Start heartbeat
                runHeartBeatSequence();
                do {
                    if (socket.isClosed()) break;
                    //Send queued messages to this client from other clients
                    sendExchangedMessages();

                    if (socket.isMessageIncoming()) {
                        //Read incoming messages
                        String incomingMessage = socket.getIncomingNPPMessage();
                        if (incomingMessage == null) continue;
                        //Handle read messages
                        String response = handler.handleMessage(new NextPressoMessageBuilder(incomingMessage).buildMessage());
                        if (response != null) {
                            //Send response back if there is any
                            socket.sendMessage(response);
                        }
                    }
                } while (userConnected);

            //Error handling
            } catch (SocketException e) {
                System.out.println("> Heartbeat sequence ended for '" + (handler.getCurrentUser() == null ? "<GUEST>" : handler.getCurrentUser()) + "'");
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                e.printStackTrace();
            } catch (NextPressoException e) {
                socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.ERROR_MALFORMED_PACKET,e.getMessage()).buildProtocolString());
            } finally {
                heartbeatHandler.cancel(true);
                socket.closeConnection();
                System.out.println("> Connection with username '" +(handler.getCurrentUser() == null ? "<GUEST>" : handler.getCurrentUser()) + "' has been closed");
                handler.removeCurrentUser();
            }
        }catch (IOException e){
            System.err.println("> Could not properly communicate with user '" + handler.getCurrentUser() + "' to handle exception!");
        }
    }

    /**
     * Send messages (direct messages, group messages) requested by other clients to this client
     */
    private void sendExchangedMessages(){
        int size = pendingExchangeMessages.size();
        NextPressoMessageBuilder[] pendingMessageCache = new NextPressoMessageBuilder[size];
        if (size != 0) {
            for (int i = 0; i < size; i++) {
                pendingMessageCache[i] = pendingExchangeMessages.poll();
            }
        }
        for (NextPressoMessageBuilder builder : pendingMessageCache){
            Message tempMessage = builder.buildMessage();
            if (tempMessage.getHeaderRecords().get("target") == null || tempMessage.getHeaderRecords().get("target").equals(handler.getCurrentUser())){
                socket.sendMessage(builder.buildProtocolString());
            }
        }
    }

    /**
     * Handle heartbeats
     */
    protected void runHeartBeatSequence(){
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable heartBeat = () -> {
            setHeartbeatConfirmationBool(false);
            /*
            The string "<GUEST>" is being logged when the username is null. This can occur when the connection with a client is established,
            the client is responding to heartbeats but is not logged in.
            */
            System.out.println("~~ Heartbeat initiated for user '" + (handler.getCurrentUser() == null ? "<GUEST>" : handler.getCurrentUser()) + "'");
            NextPressoMessageBuilder heartBeatBuilder = new NextPressoMessageBuilder(ApiProtocol.HEARTBEAT_REQUEST);
            socket.sendMessage(heartBeatBuilder.buildProtocolString());
            long start = System.currentTimeMillis();
            long end = start + 3000; //3 seconds timeout
            while(System.currentTimeMillis() < end) {
                if(isHeartbeatConfirmed()) {
                    System.out.println("~~ Heartbeat confirmed for user '" + (handler.getCurrentUser() == null ? "<GUEST>" : handler.getCurrentUser()) + "'");
                    break;
                }
            }
            if(!isHeartbeatConfirmed()) {
                System.out.println("~~ Heartbeat failed for user '" + (handler.getCurrentUser() == null ? "<GUEST>" : handler.getCurrentUser()) + "'");
                try {
                    socket.closeConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        //Heartbeat is first called after 5 seconds
        //Heartbeat will then initiate itself randomly between 5 and 15 seconds
        heartbeatHandler = scheduler.scheduleAtFixedRate(heartBeat, 5,(long)(Math.random() * (15 - 5 + 1) + 5) , TimeUnit.SECONDS);
    }

    protected synchronized void setHeartbeatConfirmationBool(boolean isHeartbeatConfirmed){
        this.isHeartbeatConfirmed = isHeartbeatConfirmed;
    }

    private synchronized boolean isHeartbeatConfirmed(){
        return isHeartbeatConfirmed;
    }

    protected void addExchangeMessage(NextPressoMessageBuilder messageBuilder){
        this.pendingExchangeMessages.add(messageBuilder);
    }

    protected synchronized boolean isConnectionAuthenticated(){
        return this.isAuthenticated;
    }

    protected synchronized void makeConnectionAuthorized(){
        this.isAuthenticated = true;
    }
}
