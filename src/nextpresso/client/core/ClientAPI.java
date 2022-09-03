package nextpresso.client.core;

import nextpresso.model.*;
import nextpresso.tools.ApiProtocol;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * API to communicate with the socket and the user
 */
public class ClientAPI {
    private final NetSocket socket;
    /**
     * Holds queued in messages that are read from the socket
     */
    private final ConcurrentLinkedQueue<Message> messageCache;
    /**
     * Thread that keeps reading messages from the socket
     */
    private Thread serverMessageReader;

    /**
     * Flag to indicate if login in is successful
     */
    private boolean loggedIn;
    /**
     * Flag to indicate if the socket is connected to the legacy server
     */
    private boolean legacyConnection;

    /**
     * Create a new Client API
     * @param ip IP address of the server
     * @param port Port number of the server
     * @throws IOException If the connection could not be established
     */
    public ClientAPI(String ip, int port) throws IOException {
        socket = new NetSocket(ip, port);
        messageCache = new ConcurrentLinkedQueue<>();
        loggedIn = true;
        legacyConnection = false;
        readIncomingMessages();
    }

    /**
     * Buffer incoming messages from the server into a queue and handle socket close and errors
     */
    private void readIncomingMessages() {
        serverMessageReader = new Thread(() -> {
            try {
                handleIncomingMessages();
            } catch (IOException | InterruptedException e) {
                switch (e.getMessage()){
                    case "Connection reset", "sleep interrupted" -> {}
                    case "Socket closed" -> loggedIn = false;
                    default -> {
                        System.err.println("> An error has occurred in the message watcher thread!");
                        System.err.println("> " + e.getMessage());
                    }
                }
            } catch (NextPressoException e) {
                System.err.println("> Error happened in incoming message reading lambda function!");
                System.err.println("[" + e.title + "] " + e.getMessage());
            }
        }, "MessageWatcher");
        serverMessageReader.start();
    }

    /**
     * Convert incoming messages from the socket to processable objects and store them in the cache.
     * This function automatically handles heartbeat requests
     */
    private void handleIncomingMessages() throws IOException, NextPressoException, InterruptedException {
        while (true){
            String incomingStringMessage = socket.getIncomingMessage();
            if (incomingStringMessage == null) break; //Socket closed
            Message incomingMessage = null;
            if (!legacyConnection) {
                try {
                    //Attempt to interpret message as NPP
                    incomingMessage = new NextPressoMessageBuilder(incomingStringMessage).buildMessage();
                } catch (NextPressoException e){
                    //If the interpretation fails, try legacy
                    legacyConnection = true;
                }
            }
            if (legacyConnection) incomingMessage = new LegacyMessageBuilder(incomingStringMessage).buildMessage();
            if (incomingMessage!=null) {
                if (incomingMessage.getHeaderCode() == ApiProtocol.HEARTBEAT_REQUEST) handleHeartbeatRequest();
                else messageCache.add(incomingMessage);
            }
            Thread.sleep(100); //Sleep to prevent CPU overload
        }
    }

    /**
     * Respond to heartbeats
     */
    private void handleHeartbeatRequest() {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.HEARTBEAT_RESPONSE).buildProtocolString());
        else socket.sendMessage(new LegacyMessageBuilder(ApiProtocol.HEARTBEAT_RESPONSE,null).buildProtocolString());
    }

    /**
     * Request list of groups
     * @throws IOException If the request could not be sent
     */
    public void requestListGroups() throws IOException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_LIST_GROUPS).buildProtocolString());
    }

    /**
     * Request to join a group
     * @param groupname Name of the group to join
     * @throws IOException If the request could not be sent
     * @throws NextPressoException If the group name is invalid
     */
    public void requestJoinGroup(String groupname) throws IOException, NextPressoException{
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_JOIN_GROUP).groupname(groupname).buildProtocolString());
    }

    /**
     * Request to create a group
     * @param groupname Name of the group to create
     * @throws IOException If the request could not be sent
     * @throws NextPressoException If the group name is invalid
     */
    public void requestCreateGroup(String groupname) throws IOException, NextPressoException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_CREATE_GROUP).groupname(groupname).buildProtocolString());
    }

    /**
     * Request to leave a group
     * @param groupname Name of the group to leave
     * @throws IOException If the request could not be sent
     * @throws NextPressoException If the group name is invalid
     */
    public void requestLeaveGroup(String groupname) throws IOException, NextPressoException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_LEAVE_GROUP).groupname(groupname).buildProtocolString());
    }

    /**
     * Request to send a group message
     * @param targetGroup Name of the group
     * @param message Message to send
     * @throws IOException If the request could not be sent
     * @throws NextPressoException If the target group and/or message is invalid
     */
    public void requestGroupMessage(String targetGroup, String message) throws IOException, NextPressoException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_GROUP_MESSAGE,message).groupname(targetGroup).buildProtocolString());
    }

    /**
     * Request to send a direct message to another user
     * @param targetUsername Username of the message target
     * @param message Message to send
     * @throws IOException If the request could not be sent
     * @throws NextPressoException If the username and/or message is invalid
     */
    public void requestDirectMessage(String targetUsername, String message, boolean isEncrypted) throws IOException, NextPressoException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_PRIVATE_MESSAGE,message).username(targetUsername).encrypted(isEncrypted).buildProtocolString());
    }

    /**
     * Request user login. Password is optional
     * @param username Username to log in
     * @param password Raw password that belongs to the username
     * @throws IOException If the request could not be sent
     * @throws NextPressoException If the username and/or password has an invalid format
     */
    public void requestLoginUser(String username, String password) throws IOException, NextPressoException {
        if (!legacyConnection) {
            NextPressoMessageBuilder builder;
            if (password == null || password.equals(""))
                builder = new NextPressoMessageBuilder(ApiProtocol.REQUEST_LOGIN).username(username);
            else
                builder = new NextPressoMessageBuilder(ApiProtocol.REQUEST_LOGIN).username(username).password(password);
            socket.sendMessage(builder.buildProtocolString());
        } else {
            socket.sendMessage(new LegacyMessageBuilder(ApiProtocol.REQUEST_LOGIN, username).buildProtocolString());
        }
    }

    /**
     * Request user logout. Success of this request will result in closing the socket
     * @throws IOException If the request could not be sent
     */
    public void requestLogoutUser() throws IOException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_LOGOUT).buildProtocolString());
        else socket.sendMessage(new LegacyMessageBuilder(ApiProtocol.REQUEST_LOGOUT,null).buildProtocolString());
        loggedIn = false;
        socket.closeConnection();
        serverMessageReader.interrupt();
    }

    /**
     * Request message broadcast. The message will be sent to every user
     * @param message Message to send
     * @throws IOException If the request could not be sent
     */
    public void requestMessageBroadcast(String message) throws IOException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_BROADCAST, message).buildProtocolString());
        else socket.sendMessage(new LegacyMessageBuilder(ApiProtocol.REQUEST_BROADCAST, message).buildProtocolString());
    }

    /**
     * Request user listing
     * @throws IOException If the request could not be sent
     */
    public void requestListUsers() throws IOException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_LIST_USERS).buildProtocolString());
    }

    /**
     * Request to send a file to another user
     * @param targetUser Username to whom the file should be sent
     * @param filename Name of the file to send
     * @param hash Hash fingerprint of the file to send
     * @throws NextPressoException If a parameter is invalid or has an incorrect format
     */
    public void requestSendFile(String targetUser, String filename, String hash, long filelength) throws NextPressoException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_SEND_FILE).username(targetUser).filename(filename).checksum(hash).filelength(filelength).buildProtocolString());
    }

    /**
     * Request file acceptance. This is used to signal whether the request file to send is accepted or rejected
     * @param targetUser Username of the file's sender
     * @param filename Name of the file that is accepted/rejected
     * @param isAccepted True - file is accepted to receive
     * @throws IOException If the request could not be sent
     * @throws NextPressoException If a parameter is invalid or has an incorrect format
     */
    public void requestFileAcceptance(String targetUser, String filename, boolean isAccepted) throws IOException, NextPressoException{
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_RECEIVE_FILE).username(targetUser).filename(filename).accepted(isAccepted).buildProtocolString());
    }

    /**
     * Send public key to the server
     * @param b64PublicKey Public key in base64 format
     */
    public void submitPublicKey(String b64PublicKey){
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_SUBMIT_KEY,b64PublicKey).buildProtocolString());
    }

    /**
     * Request public key for a user from the server
     * @param username Username of the public keyholder
     * @throws NextPressoException If a parameter is invalid or has an incorrect format
     */
    public void getPublicKey(String username) throws NextPressoException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_GET_KEY).username(username).buildProtocolString());
    }

    /**
     * Send session key to a user
     * @param username Username to who the session key should be sent
     * @param sessionAES Session key
     * @param sessionIV Session IV
     * @throws NextPressoException If a parameter is invalid or has an incorrect format
     */
    public void sendSessionKey(String username, String sessionAES, String sessionIV) throws NextPressoException {
        if (!legacyConnection) socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.ENCRYPTION_SET_KEY,sessionAES+","+sessionIV).username(username).buildProtocolString());
    }

    /**
     * Close the connection
     * @throws IOException If the connection could not be closed properly
     */
    public void stopSocket() throws IOException{
        socket.closeConnection();
    }

    /**
     * Get message cache
     * This method is used to retrieve all the incoming queued messages
     * @return An array of buffered messages
     */
    public Message[] getMessageCache() {
        int size = messageCache.size();
        Message[] cacheResponse = new Message[size];
        if (size != 0) {
            for (int i = 0; i < size; i++) {
                cacheResponse[i] = messageCache.poll();
            }
        }
        return cacheResponse;
    }

    /**
     * Check connection status
     * @return [isLoggedIn, isConnectionAlive]
     */
    public boolean[] getConnectionStatus(){
        return new boolean[]{loggedIn,serverMessageReader.isAlive()};
    }

    /**
     * Check if the API is connected to the legacy server
     * @return True - legacy server is connected
     */
    public boolean isLegacyConnection() {
        return legacyConnection;
    }
}
