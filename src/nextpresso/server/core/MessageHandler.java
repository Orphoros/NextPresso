package nextpresso.server.core;

import nextpresso.tools.ApiProtocol;
import nextpresso.model.Message;
import nextpresso.model.NextPressoException;
import nextpresso.model.NextPressoMessageBuilder;
import nextpresso.server.data.UserCredentials;
import nextpresso.server.tools.PBKDF2Validator;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Incoming NextPresso message handler for handling client messages
 */
public class MessageHandler {
    private final MessageManager socket;
    private final Map<String, MessageManager> connectedUsers;
    private final Map<String, FileManager> transferUsers;
    private final Map<String, Map<String,Long>> groups;
    private final Map<String, String> userPublicKeys; //Username, Base64Key
    private String currentUser;

    /**
     * Create a new client message handler
     * @param socket Socket of the server
     * @param connectedUsers List of connected users and their server sockets
     * @param groups List of groups
     * @param transferUsers List of the users' file sockets
     * @param userPublicKeys List of the users' public RSA keys
     */
    public MessageHandler(MessageManager socket, Map<String, MessageManager> connectedUsers, Map<String, Map<String, Long>> groups, Map<String, FileManager> transferUsers, Map<String, String> userPublicKeys) {
        this.socket = socket;
        this.connectedUsers = connectedUsers;
        this.transferUsers = transferUsers;
        this.groups = groups;
        this.userPublicKeys = userPublicKeys;
        currentUser = null;
    }

    /**
     * Parses the message type and calls the appropriate method to handle the message
     * @param incomingMessage The NextPresso message that the client sent
     * @return NextPresso binary message in a raw, string format that then can be used by the NextPresso message builder class. If the message could not be handled, an automatic error response message will be returned
     * @throws NoSuchAlgorithmException Thrown if the system does not support MD5 hashing
     * @throws InvalidKeySpecException Thrown if the system does not support the cryptographic algorithms used for password hashing
     * @throws NextPressoException Thrown if something went wrong with the message handling
     */
    public String handleMessage(Message incomingMessage) throws NoSuchAlgorithmException, InvalidKeySpecException, NextPressoException {
        //Message handling for requests that do not need user logging in
        if (incomingMessage.getHeaderCode() == ApiProtocol.REQUEST_LOGIN) return loginUser(incomingMessage);
        if (incomingMessage.getHeaderCode() == ApiProtocol.HEARTBEAT_RESPONSE) return confirmHeartBeat();

        //Message handling for requests that need user login
        if (currentUser == null) return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_LOGGED_IN,"You need to log in first!").buildProtocolString();

        return switch (incomingMessage.getHeaderCode()){
            case REQUEST_BROADCAST -> preformBroadcast(incomingMessage);
            case REQUEST_LIST_USERS -> listUsers();
            case REQUEST_LOGOUT -> handleLogout(incomingMessage);
            case REQUEST_CREATE_GROUP -> createGroup(incomingMessage);
            case REQUEST_LIST_GROUPS -> listGroups();
            case REQUEST_JOIN_GROUP -> joinGroup(incomingMessage);
            case REQUEST_LEAVE_GROUP -> leaveGroup(incomingMessage);
            case REQUEST_PRIVATE_MESSAGE -> sendDM(incomingMessage);
            case REQUEST_GROUP_MESSAGE -> sendGroupMsg(incomingMessage);
            case REQUEST_SEND_FILE -> sendFileRequest(incomingMessage);
            case REQUEST_RECEIVE_FILE -> receiveFileRequest(incomingMessage);
            case REQUEST_SUBMIT_KEY -> submitPublicKey(incomingMessage);
            case REQUEST_GET_KEY -> getPublicKey(incomingMessage);
            case ENCRYPTION_SET_KEY -> forwardEncryptionSetup(incomingMessage);
            default -> new NextPressoMessageBuilder(ApiProtocol.ERROR_UNEXPECTED,"Cannot handle the received message!").buildProtocolString();
        };
    }

    //============================[Methods to handle messages]============================

    /**
     * Handle message to send group message to members of a group
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String sendGroupMsg(Message incomingMessage) {
        if(!incomingMessage.getHeaderRecords().containsKey("groupname")) return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND,"Could not find groupname to send group message to!").buildProtocolString();

        String targetGroup = incomingMessage.getHeaderRecords().get("groupname");
        if(!groups.containsKey(targetGroup)) return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND,"Group not found!").buildProtocolString();
        if(!groups.get(targetGroup).containsKey(currentUser)) return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND,"You are not in this group!").buildProtocolString();

        groups.get(targetGroup).put(currentUser,System.currentTimeMillis()); //Reset inactivity timer

        groups.get(targetGroup).forEach((user,lastActivity) ->{
            if (!user.equals(currentUser)) {
                try {
                    connectedUsers.get(user).addExchangeMessage(
                            new NextPressoMessageBuilder(ApiProtocol.MESSAGE_CHAT,incomingMessage.getPayload())
                                    .sender(currentUser)
                                    .groupname(targetGroup)
                                    .authenticated(socket.isConnectionAuthenticated()));
                } catch (NextPressoException e) {
                    System.err.println("[" + e.title + "] " + e.getMessage());
                    System.err.println("> Could not send group message");
                }
            }
        });

        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_GROUP_MESSAGE,incomingMessage.getPayload()).buildProtocolString();

    }

    /**
     * Handle message to send a private message to a user.
     * Also handles encrypted private messages
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String sendDM(Message incomingMessage) {
        try {
            if (!incomingMessage.getHeaderRecords().containsKey("username"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Could not find username to send direct message to!").buildProtocolString();

            String messageToSend = incomingMessage.getPayload();
            String messageTargetUser = incomingMessage.getHeaderRecords().get("username");

            boolean encrypted;
            if (!incomingMessage.getHeaderRecords().containsKey("encrypted")) encrypted = false;
            else encrypted = incomingMessage.getHeaderRecords().get("encrypted").equals("true");

            if (!connectedUsers.containsKey(messageTargetUser))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND, "Message target user not found!").buildProtocolString();

            connectedUsers.get(messageTargetUser).addExchangeMessage(new NextPressoMessageBuilder(ApiProtocol.MESSAGE_CHAT, messageToSend).sender(currentUser).authenticated(socket.isConnectionAuthenticated()).encrypted(encrypted));

            System.out.println("> Sent DM from '" + currentUser + "' to '" + messageTargetUser + "'!");
            return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_PRIVATE_MESSAGE, incomingMessage.getPayload()).buildProtocolString();
        }catch (NextPressoException e){
            System.err.println("[" + e.title + "] " + e.getMessage());
            System.err.println("> Could not send direct message");
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_INTERNAL_ERROR, "Could not send the direct message!").buildProtocolString();
        }
    }

    /**
     * Handle message to leave a group
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String leaveGroup(Message incomingMessage) {
        if(!incomingMessage.getHeaderRecords().containsKey("groupname")) return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND,"Group to leave is not specified!").buildProtocolString();
        String targetGroup = incomingMessage.getHeaderRecords().get("groupname");
        if(!groups.containsKey(targetGroup)) return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND,"Could not find group to leave!").buildProtocolString();
        if(!groups.get(targetGroup).containsKey(currentUser)) return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND,"You are not in this group!").buildProtocolString();

        groups.get(targetGroup).remove(currentUser);
        System.out.println("> Removed '" + currentUser + "' from group '" + targetGroup + "' based on user request!");

        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_LEAVE_GROUP,targetGroup).buildProtocolString();
    }

    /**
     * Handle message to join a group
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String joinGroup(Message incomingMessage) {
        if(!incomingMessage.getHeaderRecords().containsKey("groupname")) return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND,"Group to join is not specified!").buildProtocolString();
        String targetGroup = incomingMessage.getHeaderRecords().get("groupname");

        if(!groups.containsKey(targetGroup)) return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND,"Requested group not found!").buildProtocolString();

        groups.get(targetGroup).put(currentUser,System.currentTimeMillis()); //Start inactivity timer

        //Notify other users in group of the new member
        groups.get(targetGroup).forEach((user,lastActivity) -> {
                    if (!user.equals(currentUser)) {
                        try {
                            connectedUsers.get(user).addExchangeMessage(
                                    new NextPressoMessageBuilder(ApiProtocol.MESSAGE_SERVER_GROUP_NEW_USER)
                                            .username(currentUser)
                                            .authenticated(socket.isConnectionAuthenticated())
                                            .groupname(targetGroup));
                        } catch (NextPressoException e) {
                            System.err.println("[" + e.title + "] " + e.getMessage());
                            System.err.println("> Could not send group message");
                        }
                    }
                });

        System.out.println("> Added user '" + currentUser + "' to group '" + targetGroup + "'!");

        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_JOIN_GROUP,targetGroup).buildProtocolString();
    }

    /**
     * Handle message to list groups
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String listGroups() {
        if(groups.isEmpty()) return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_LIST_GROUPS).buildProtocolString();

        StringBuilder output = new StringBuilder();
        groups.forEach((k,v) -> { //Build list format
            output.append("{");
            output.append(k);
            output.append(",");
            output.append(v.containsKey(currentUser) ? "1" : "0");
            output.append("},");
        });
        output.deleteCharAt(output.length()-1); //Delete the very last comma that is unnecessary
        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_LIST_GROUPS,output.toString()).buildProtocolString();
    }

    /**
     * Handle message to create a new group that will also put the creator in the group
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String createGroup(Message incomingMessage) {
        if(!incomingMessage.getHeaderRecords().containsKey("groupname")) return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND,"Group to create is not specified!").buildProtocolString();
        String targetGroup = incomingMessage.getHeaderRecords().get("groupname");

        if(groups.containsKey(targetGroup)) return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_ALLOWED,"Requested group already exists!").buildProtocolString();

        HashMap<String, Long> userActivityMap = new HashMap<>();
        userActivityMap.put(currentUser,System.currentTimeMillis());
        groups.put(targetGroup, userActivityMap);

        System.out.println("> Created group '" + targetGroup + "' and added '" + currentUser + "' to it!");

        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_CREATE_GROUP,targetGroup).buildProtocolString();
    }

    /**
     * Handle message to log in a user
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String loginUser(Message incomingMessage) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if(currentUser != null) return new NextPressoMessageBuilder(ApiProtocol.ERROR_UNEXPECTED,"Already logged in!").buildProtocolString();
        if(!incomingMessage.getHeaderRecords().containsKey("username")) return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND,"Username to log in is not specified!").buildProtocolString();
        String username = incomingMessage.getHeaderRecords().get("username");
        if(username.length() < 3)return new NextPressoMessageBuilder(ApiProtocol.ERROR_INVALID_DATA_FORMAT,"Username is too short!").buildProtocolString();
        if(connectedUsers.containsKey(username))return new NextPressoMessageBuilder(ApiProtocol.ERROR_USER_ALREADY_LOGGED_IN,"User is already logged in!").buildProtocolString();

        //User wants to be authenticated (optional)
        if(incomingMessage.getHeaderRecords().containsKey("password")){
            String givenPassword = incomingMessage.getHeaderRecords().get("password");

            if(!UserCredentials.dataSet.containsKey(username))return new NextPressoMessageBuilder(ApiProtocol.ERROR_UNAUTHORIZED,"Username or password is incorrect!").buildProtocolString();

            String storedHash = UserCredentials.dataSet.get(username);

            if(!PBKDF2Validator.validateHash(givenPassword,storedHash))return new NextPressoMessageBuilder(ApiProtocol.ERROR_UNAUTHORIZED,"Username or password is incorrect!").buildProtocolString();

            socket.makeConnectionAuthorized();
            System.out.println("> Successfully authenticated user '" + username + "'!");
        }

        connectedUsers.put(username,socket);
        currentUser = username;
        System.out.println("> Logged in user '" + currentUser + "'!");
        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_LOGIN,currentUser).authenticated(socket.isConnectionAuthenticated()).buildProtocolString();
    }

    /**
     * Handle message to list connected users
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String listUsers(){
        StringBuilder output = new StringBuilder();
        connectedUsers.forEach((username, socket) -> { //Build list format
            output.append("{");
            output.append(username);
            output.append(",");
            output.append(socket.isConnectionAuthenticated() ? "1" : "0");
            output.append("},");
        });
        output.deleteCharAt(output.length()-1); //Remove last comma
        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_LIST_USERS,output.toString()).buildProtocolString();
    }

    /**
     * Handle message to log out a user
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String handleLogout(Message incomingMessage){
        socket.userConnected = false;
        System.out.println("> Logged out user '" + currentUser + "'!");
        connectedUsers.remove(currentUser);
        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_LOGOUT, incomingMessage.getPayload()).buildProtocolString();
    }

    /**
     * Handle message to send a message to every connected user
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String preformBroadcast(Message incomingMessage) {
        try {
            for (MessageManager socket : connectedUsers.values()) {
                if (socket != this.socket) { //Add message to other users who is not the current this user
                    socket.addExchangeMessage(new NextPressoMessageBuilder(ApiProtocol.MESSAGE_CHAT, incomingMessage.getPayload()).sender(currentUser).authenticated(this.socket.isConnectionAuthenticated()));
                }
            }
            System.out.println("> Broadcast from user '" + currentUser + "' is sent to everyone!");
            return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_BROADCAST, incomingMessage.getPayload()).buildProtocolString();
        }catch (NextPressoException e){
            System.err.println("[" + e.title + "] " + e.getMessage());
            System.err.println("> Could not perform a broadcast!");
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_INTERNAL_ERROR, "Could not perform broadcast!").buildProtocolString();
        }
    }

    /**
     * Handle message to forward a file request message to another user
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String sendFileRequest(Message incomingMessage){
        try {
            if (!incomingMessage.getHeaderRecords().containsKey("username"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Could not find username to send file to!").buildProtocolString();
            if (!incomingMessage.getHeaderRecords().containsKey("filename"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Could not find filename!").buildProtocolString();
            if (!incomingMessage.getHeaderRecords().containsKey("checksum"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Could not find file checksum!").buildProtocolString();

            String filename = incomingMessage.getHeaderRecords().get("filename");
            String checksum = incomingMessage.getHeaderRecords().get("checksum");
            String transferTarget = incomingMessage.getHeaderRecords().get("username");
            long fileLength = Long.parseLong(incomingMessage.getHeaderRecords().get("filelength"));

            if (!connectedUsers.containsKey(transferTarget))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND, "Transfer target user not found!").buildProtocolString();

            connectedUsers.get(transferTarget).addExchangeMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_SEND_FILE).sender(currentUser).username(transferTarget).filename(filename).checksum(checksum).filelength(fileLength));

            System.out.println("> Sent file transfer request from '" + currentUser + "' to '" + transferTarget + "'!");
            return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_SEND_FILE, filename).buildProtocolString();
        }catch (NextPressoException e){
            System.err.println("[" + e.title + "] " + e.getMessage());
            System.err.println("> Could not send file transfer request");
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_MALFORMED_PACKET, e.getMessage()).buildProtocolString();
        }catch (NumberFormatException e){
            System.err.println("[ Number format exception ] " + e.getMessage());
            System.err.println("> Could not send file transfer request");
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_MALFORMED_PACKET, "Count not interpret file length as a number!").buildProtocolString();
        }
    }

    /**
     * Handle message to forward a file receive request message to another user
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String receiveFileRequest(Message incomingMessage){
        try {
            if (!incomingMessage.getHeaderRecords().containsKey("username"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Could not find username of the file sender!").buildProtocolString();
            if (!incomingMessage.getHeaderRecords().containsKey("filename"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Could not find filename!").buildProtocolString();
            if (!incomingMessage.getHeaderRecords().containsKey("accepted"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Could not find file acceptance choice!").buildProtocolString();

            String filename = incomingMessage.getHeaderRecords().get("filename");
            String transferSource = incomingMessage.getHeaderRecords().get("username");
            boolean isAccepted = Boolean.parseBoolean(incomingMessage.getHeaderRecords().get("accepted"));

            if (!connectedUsers.containsKey(transferSource))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND, "Transfer source user not found!").buildProtocolString();
            if (isAccepted) {
                transferUsers.put(currentUser,null);
                transferUsers.put(transferSource,null);
            }

            connectedUsers.get(transferSource).addExchangeMessage(new NextPressoMessageBuilder(ApiProtocol.REQUEST_RECEIVE_FILE).sender(currentUser).username(transferSource).filename(filename).accepted(isAccepted));

            System.out.println("> Sent file receive request from '" + currentUser + "' to '" + transferSource + "'!");
            return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_RECEIVE_FILE, filename).buildProtocolString();
        }catch (NextPressoException e){
            System.err.println("[" + e.title + "] " + e.getMessage());
            System.err.println("> Could not send file receive request");
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_INTERNAL_ERROR, "Could not send file receive request!").buildProtocolString();
        }
    }

    /**
     * Store public key of a user in the server
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String submitPublicKey(Message incomingMessage){
        if (incomingMessage.getPayload().isBlank())
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "No key provided in body!").buildProtocolString();
        try{
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(incomingMessage.getPayload()));
            PublicKey pubK = keyFactory.generatePublic(publicKeySpec);
            Cipher encrypt = Cipher.getInstance("RSA");
            encrypt.init(Cipher.ENCRYPT_MODE,pubK);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_INVALID_DATA_FORMAT, "Provided data is not a valid X.509 encoded RSA Public Key!").buildProtocolString();
        }
        userPublicKeys.put(currentUser,incomingMessage.getPayload());
        return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_SUBMIT_KEY, incomingMessage.getPayload()).buildProtocolString();
    }

    /**
     * Send stored public key of a user to another user
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String getPublicKey(Message incomingMessage){
        try {
            if (!incomingMessage.getHeaderRecords().containsKey("username"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "No target username specified!").buildProtocolString();
            if (!userPublicKeys.containsKey(incomingMessage.getHeaderRecords().get("username"))) {
                return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_GET_KEY).username(incomingMessage.getHeaderRecords().get("username")).buildProtocolString();
            }
            return new NextPressoMessageBuilder(ApiProtocol.ACKNOWLEDGE_GET_KEY, userPublicKeys.get(incomingMessage.getHeaderRecords().get("username"))).username(incomingMessage.getHeaderRecords().get("username")).buildProtocolString();
        } catch (NextPressoException e){
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_INTERNAL_ERROR, "Unknown parsing error").buildProtocolString();
        }
    }

    /**
     * Forward encryption setup procedure to another user
     * @param incomingMessage Message that holds the request
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String forwardEncryptionSetup(Message incomingMessage) {
        try {
            if (!incomingMessage.getHeaderRecords().containsKey("username"))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Could not find username to send direct message to!").buildProtocolString();

            String messageToSend = incomingMessage.getPayload();
            String messageTargetUser = incomingMessage.getHeaderRecords().get("username");

            if (!connectedUsers.containsKey(messageTargetUser))
                return new NextPressoMessageBuilder(ApiProtocol.ERROR_NOT_FOUND, "Message target user not found!").buildProtocolString();

            connectedUsers.get(messageTargetUser).addExchangeMessage(new NextPressoMessageBuilder(ApiProtocol.ENCRYPTION_SET_KEY, messageToSend).sender(currentUser).username(messageTargetUser));
            return new NextPressoMessageBuilder(ApiProtocol.ENCRYPTION_KEY_FORWARDED, messageToSend).username(messageTargetUser).buildProtocolString();
        } catch (NextPressoException e){
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_INTERNAL_ERROR, "Unknown parsing error").buildProtocolString();
        }
    }

    /**
     * Handle user's heartbeat confirmation
     * @return The NPP string message answer that should be sent out through the socket
     */
    private String confirmHeartBeat(){
        socket.setHeartbeatConfirmationBool(true);
        return null;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void removeCurrentUser(){
        Map<String, Map<String,Long>> groupsCheck = new HashMap<>(groups);
        groupsCheck.forEach((group,userActivityMap) -> {
            Map<String,Long> tempUserActivityMap = new HashMap<>(userActivityMap);
            if(tempUserActivityMap.containsKey(currentUser)) groups.get(group).remove(currentUser);
        });
        connectedUsers.remove(currentUser);
        transferUsers.remove(currentUser);
        userPublicKeys.remove(currentUser);
    }
}
