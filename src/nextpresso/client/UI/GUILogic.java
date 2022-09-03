package nextpresso.client.UI;

import nextpresso.client.core.ClientAPI;
import nextpresso.model.ErrorMessage;
import nextpresso.model.Message;
import nextpresso.model.NetSocket;
import nextpresso.model.NextPressoException;
import nextpresso.tools.MD5Hashing;
import nextpresso.tools.ApiProtocol;
import nextpresso.tools.CryptoTools;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Timer;
import java.util.*;

import static nextpresso.client.core.Helpers.getMessageKeyValuePairs;

/**
 * Functionality of the GUI components
 */
public class GUILogic {
    private final MainPane gui;
    private Boolean authenticated;
    private ClientAPI api;
    private TimerTask guiWatchTask;
    private final HashMap<String,Boolean> allGroups, allUsers;
    private final HashMap<String, String> outgoingTransfers; //Username, Path
    private final Map<String,String> publicKeys; //Username, RSA key
    private final HashMap<String, Map.Entry<String, String>> sessionKeys; //Username, <AES Key in base64, AES IV in base64>
    private final String privateKey,publicKey;
    private int filePort;
    private String serverURL,username, iv;

    public GUILogic() {
        this.gui = new MainPane(this);
        this.allUsers = new HashMap<>();
        this.allGroups = new HashMap<>();
        this.outgoingTransfers = new HashMap<>();
        this.sessionKeys = new HashMap<>();
        this.publicKeys = Collections.synchronizedMap(new HashMap<>());
        KeyPair keys = CryptoTools.generateRSAKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    }

    /**
     * Handles logging in to the server
     */
    public void handleConnect() {
        try {
            try {
                String[] response = ShowDialog.showLoginDialog();
                if (response != null) {
                    gui.chatView.setText("");
                    handleSocketConnection(response);
                    api.requestLoginUser(response[3], response[4]);
                    handleLogon();
                    finalizeConnection(response);
                }
            } catch (IllegalStateException | NextPressoException e) {
                api.stopSocket();
                renderException(e);
            }
        } catch (IOException e) {
            renderException(e);
        }
    }

    private void handleSocketConnection(String[] response) throws IOException {
        api = new ClientAPI(response[0], Integer.parseInt(response[1]));
        filePort = Integer.parseInt(response[2]);
        serverURL = response[0];
        Message[] cache = api.getMessageCache();
        while (cache.length == 0) cache = api.getMessageCache();
        gui.chatView.append(cache[0].toString() + "\n");
    }

    private void handleLogon() throws IllegalStateException{
        while (true) {
            Message[] cache = api.getMessageCache();
            if (cache.length > 0) {
                for (Message message : cache) {
                    if (message.getHeaderCode() == ApiProtocol.ACKNOWLEDGE_LOGIN) {
                        authenticated = message.getHeaderRecords().containsKey("authenticated") && message.getHeaderRecords().get("authenticated").equals("true");
                        return;
                    }
                    if (message instanceof ErrorMessage) throw new IllegalStateException(message.toString());
                }
            }
        }
    }

    private void finalizeConnection(String[] response){
        username = response[3];
        String displayedUsername = authenticated ? "*" + response[3] : response[3];
        gui.frontendUpdateOnConnection(displayedUsername, api.isLegacyConnection());
        checkServerMessages();
        if (!api.isLegacyConnection()) uploadRSA();
    }

    /**
     * Parse and handle messages coming from the server
     */
    private void checkServerMessages(){
        //Start message check-and-print thread
        Thread messageHandlerThread = new Thread(() -> {
            while (api.getConnectionStatus()[1]) {
                Message[] newMessages = api.getMessageCache();
                if (newMessages.length > 0) {
                    for (Message m : newMessages) {
                        switch (m.getHeaderCode()){
                            case ACKNOWLEDGE_LIST_USERS -> retrieveUsers(m.getPayload());
                            case ACKNOWLEDGE_LIST_GROUPS -> retrieveGroups(m.getPayload());
                            case ACKNOWLEDGE_JOIN_GROUP -> ShowDialog.infoDialog("Joined group: "+m.getPayload(),"Joined group");
                            case ACKNOWLEDGE_CREATE_GROUP -> ShowDialog.infoDialog("Created and joined group: "+m.getPayload(), "Created group");
                            case ACKNOWLEDGE_SEND_FILE -> ShowDialog.infoDialog("Sent request to transfer file: "+m.getPayload(), "File upload");
                            case REQUEST_SEND_FILE -> handleFileSendRequest(m);
                            case REQUEST_RECEIVE_FILE -> handleFileTransferAccept(m);
                            case ACKNOWLEDGE_GET_KEY -> publicKeys.put(m.getHeaderRecords().get("username"),m.getPayload().isBlank() ? null : m.getPayload());
                            case ENCRYPTION_SET_KEY -> handleNewSessionKey(m);
                            case MESSAGE_SERVER_INFO, MESSAGE_CHAT, MESSAGE_SERVER_GROUP_NEW_USER -> gui.chatView.append(handleDisplayableMessage(m) + "\n");
                            default -> System.out.println(m);
                        }
                    }
                    gui.chatView.setCaretPosition(gui.chatView.getDocument().getLength());
                }
                try {
                    Thread.sleep(100); //Sleep to prevent CPU overload(?)
                } catch (InterruptedException ignored) {}
            }
            if (api.getConnectionStatus()[0]) {
                gui.displayError("Disconnected - Server timed out");
                gui.updateUIOnDisconnect();
                populateGUIGroups(new ArrayList<>());
                populateGUIUsers(new ArrayList<>());
            }
        }, "MessagePrinter");
        messageHandlerThread.start();
        if (!api.isLegacyConnection()) watchUsersAndGroups();
    }

    private void uploadRSA(){
        api.submitPublicKey(publicKey);
    }

    /**
     * Refreshes the local database of the user and group list.
     * Every second the database will request the server to send the listing
     */
    private void watchUsersAndGroups(){
        java.util.Timer timer = new Timer();
        guiWatchTask = new TimerTask() {
            @Override
            public void run() {
                requestListUsers();
                requestListGroups();
            }
        };
        timer.schedule(guiWatchTask, 0,3000);
    }

    /**
     * Process (and decrypt) a message to be displayed to the client.
     * @param incomingMessage A message object of the incoming message
     * @return The string to be displayed in the UI
     */
    private String handleDisplayableMessage(Message incomingMessage){
        if (incomingMessage.getHeaderRecords().containsKey("encrypted") && incomingMessage.getHeaderRecords().get("encrypted").equals("true")){
            if (!sessionKeys.containsKey(incomingMessage.getHeaderRecords().get("sender")))return "! INVALID ENCRYPTED MESSAGE, CHECK WITH SENDER !";
            String aesKey = sessionKeys.get(incomingMessage.getHeaderRecords().get("sender")).getKey();
            String aesIV = sessionKeys.get(incomingMessage.getHeaderRecords().get("sender")).getValue();
            String message = CryptoTools.decryptAESString(aesKey,aesIV,incomingMessage.getPayload());
            StringBuilder builder = new StringBuilder();
            builder.append("[ENCRYPTED] ");
            if(incomingMessage.getHeaderRecords().get("authenticated").equals("true")) builder.append('*');
            builder.append(incomingMessage.getHeaderRecords().get("sender"));
            builder.append(" says: ");
            builder.append(message);
            return builder.toString();
        }
        return incomingMessage.toString();
    }

    /**
     * Perform a disconnect (logout)
     */
    public void handleDisconnect(){
        try {
            api.requestLogoutUser();
            if (guiWatchTask != null) guiWatchTask.cancel();
            gui.statusIndicator.setBackground(new Color(85, 85, 85));
            gui.statusMessage.setText("Disconnected");
            gui.updateUIOnDisconnect();
            ArrayList<String> notCon = new ArrayList<>();
            notCon.add("Not connected");
            gui.groups = notCon;
            gui.users = notCon;
        } catch (IOException e) {
            ShowDialog.errorDialog(e,"Network Error");
        }
    }

    /**
     * Update tab info on switch, used by listener
     */
    public void handleTabSwitch(){
        if (gui.chatInputSelector.getSelectedIndex() == 1) requestListGroups();
        else if (gui.chatInputSelector.getSelectedIndex() == 2) requestListUsers();
    }

    private void requestListUsers(){
        try {
            api.requestListUsers();
        } catch (IOException e) {
            ShowDialog.errorDialog(e,"Communication Error");
        }
    }

    /**
     * Refresh the user list
     * @param userList Up-to-date list of server users
     */
    private void retrieveUsers(String userList){
        HashMap<String, Boolean> tmpList = new HashMap<>(getMessageKeyValuePairs(userList));
        tmpList.remove(username);
        if(tmpList.isEmpty()) populateGUIUsers(new ArrayList<>());
        if(tmpList.equals(allUsers)) return;
        for (String user : allUsers.keySet()){
            if (!tmpList.containsKey(user)){
                allUsers.remove(user);
                publicKeys.remove(user);
                sessionKeys.remove(user);
            }
        }
        for (String user : tmpList.keySet()){
            if (!allUsers.containsKey(user)) allUsers.put(user,tmpList.get(user));
        }
        ArrayList<String> listOfUsers = new ArrayList<>();
        for(Map.Entry<String,Boolean> e : allUsers.entrySet()){
            String displayedUsername = e.getValue() ? ("*" + e.getKey()) : e.getKey();
            listOfUsers.add(displayedUsername);
        }
        populateGUIUsers(listOfUsers);
    }

    private void populateGUIUsers(ArrayList<String> updatedList){
        String userDropdownHint = "-- Select a user --";
        gui.users.clear();
        gui.users.add(userDropdownHint);
        if (updatedList.size() == 0) gui.users.add("No users connected");
        else gui.users.addAll(updatedList);
        gui.userSelector.setModel(new DefaultComboBoxModel<>(gui.users.toArray(new String[0])));
    }

    public void requestListGroups(){
        try {
            api.requestListGroups();
        } catch (IOException e) {
            ShowDialog.errorDialog(e,"Communication Error");
        }
    }

    /**
     * Refresh the group list
     * @param groupList Up-to-date list of server groups
     */
    private void retrieveGroups(String groupList){
        HashMap<String, Boolean> tmpList = new HashMap<>(getMessageKeyValuePairs(groupList));
        if (tmpList.isEmpty()) populateGUIGroups(new ArrayList<>());
        if(tmpList.equals(allGroups)) return;
        allGroups.clear();
        allGroups.putAll(tmpList);
        ArrayList<String> listOfJoinedGroups = new ArrayList<>();

        for(Map.Entry<String,Boolean> e : allGroups.entrySet()){
            if(e.getValue()) listOfJoinedGroups.add(e.getKey());
        }
        populateGUIGroups(listOfJoinedGroups);
    }

    private void populateGUIGroups(ArrayList<String> outGroups){
        String groupDropdownHint = "-- Select a group --";
        gui.groups.clear();
        gui.groups.add(groupDropdownHint);
        if (outGroups.size() == 0) gui.groups.add("No groups available");
        else gui.groups.addAll(outGroups);
        gui.groupSelector.setModel(new DefaultComboBoxModel<>(gui.groups.toArray(new String[0])));
    }

    /**
     * Show GUI to leave or join a group
     * @param isLeave True - leave a group
     */
    public void leaveJoinGroup(boolean isLeave){
        ArrayList<String> availableGroups = new ArrayList<>();
        availableGroups.add("-- Select a group --");
        for (Map.Entry<String,Boolean> group : allGroups.entrySet()) {
            if (!isLeave && !group.getValue() && !group.getKey().isBlank()) availableGroups.add(group.getKey());
            else if (isLeave && group.getValue()) availableGroups.add(group.getKey());
        }
        if (availableGroups.size() == 1) availableGroups.add("No groups available");
        String selection = ShowDialog.leaveJoinDialog(availableGroups.toArray(new String[0]),isLeave);
        if (selection == null || selection.equals("-- Select a group --") || selection.equals("No groups available") || selection.equals("Not connected")) return;
        try {
            if (!isLeave) api.requestJoinGroup(selection);
            else api.requestLeaveGroup(selection);
        } catch (IOException | NextPressoException e) {
            ShowDialog.errorDialog(e,"Error "+(isLeave ? "leaving" : "joining")+" group");
        }
    }

    public void createGroup(){
        try {
            String groupname = ShowDialog.groupCreateDialog();
            if (groupname == null || groupname.isBlank()) return;
            api.requestCreateGroup(groupname);
        } catch (IOException | NextPressoException e) {
            ShowDialog.errorDialog(e,"Error creating group");
        }
    }

    public void sendBroadcastMessage(){
        String message = gui.broadcastMessage.getText();
        if (message.isBlank()) return;
        try {
            api.requestMessageBroadcast(message);
            gui.chatView.append("You said: "+message+"\n");
        } catch (IOException e) {
            ShowDialog.errorDialog(e,"Broadcast Message Error");
        }
        gui.broadcastMessage.setText("");
    }

    public void sendGroupMessage(){
        String message = gui.groupMessage.getText();
        if (message.isBlank()) return;
        String selection = (String) gui.groupSelector.getSelectedItem();
        if (selection == null || selection.equals("-- Select a group --") || selection.equals("No groups available") || selection.equals("Not connected")) return;
        try {
            api.requestGroupMessage(selection, message);
            gui.chatView.append("You ("+selection+") said: "+message+"\n");
        } catch (IOException | NextPressoException e) {
            ShowDialog.errorDialog(e,"Group Message Error");
        }
        gui.groupMessage.setText("");
    }

    public void sendDirectMessage(){
        String message = gui.directMessage.getText();
        if (message.isBlank()) return;
        String selection = (String) gui.userSelector.getSelectedItem();
        if (selection == null || selection.equals("-- Select a user --") || selection.equals("No users connected") || selection.equals("Not connected")) return;
        selection = selection.replace("*","");

        try {
            if (gui.encryptCheckbox.isSelected())sendEncryptedDirectMessage(selection, message);
            else api.requestDirectMessage(selection, message, false);
            gui.chatView.append("You said: "+message+"\n");
        } catch (IOException | NextPressoException | IllegalStateException e) {
            ShowDialog.errorDialog(e,"Direct Message Error");
        } finally {
            gui.directMessage.setText("");
        }
    }

    private void sendEncryptedDirectMessage(String target, String message) throws NextPressoException, IOException, IllegalStateException {
        if (!sessionKeys.containsKey(target))configureEncryption(target);
        Map.Entry<String, String> aesPair = sessionKeys.get(target);
        String encryptedMessage = CryptoTools.encryptAESString(aesPair.getKey(),aesPair.getValue(),message);
        api.requestDirectMessage(target,encryptedMessage,true);
    }

    /**
     * Configure message encryption with another client
     * @param target The target of the encrypted channel
     * @throws NextPressoException Thrown if an issue occurs when communicating with the server
     * @throws IllegalStateException Thrown if the other party has no encryption support
     */
    private void configureEncryption(String target) throws NextPressoException, IllegalStateException {
        readPubKey(target);
        if (iv == null) iv = Base64.getEncoder().encodeToString(CryptoTools.generateIv().getIV());
        String sessionKey = Base64.getEncoder().encodeToString(CryptoTools.generateAESKey().getEncoded());
        sessionKeys.put(target, new AbstractMap.SimpleEntry<>(sessionKey,iv));

        String encryptedSession = CryptoTools.encryptRSAString(publicKeys.get(target),sessionKey);
        String encryptedIV = CryptoTools.encryptRSAString(publicKeys.get(target),iv);
        api.sendSessionKey(target,encryptedSession,encryptedIV);

        publicKeys.remove(target);
    }

    /**
     * Get public key of a user
     * @param target Username from whom the public key should be retrieved
     */
    private void readPubKey(String target) throws NextPressoException {
        api.getPublicKey(target);
        long startTime = System.currentTimeMillis();
        while (!publicKeys.containsKey(target) && System.currentTimeMillis()-startTime < 5000) {} //Wait for the key
        if (publicKeys.get(target) == null) {
            publicKeys.remove(target);
            throw new IllegalStateException("Message target does not support encryption. Please uncheck the \"Encrypt\" checkbox to message them.");
        }
    }

    /**
     * Handle GUI file upload logic
     */
    public void sendFileUploadRequest(){
        File selectedFile = ShowDialog.uploadDialog();
        String targetUser = (String) gui.userSelector.getSelectedItem();
        if (selectedFile == null) return;
        if (targetUser == null || targetUser.equals("-- Select a user --") || targetUser.equals("No users connected") || targetUser.equals("Not connected")) {
            ShowDialog.infoDialog("Select a user to send file", "File upload");
            return;
        }
        String fileName = selectedFile.getName();
        try {
            api.requestSendFile(targetUser.replace("*",""),fileName,MD5Hashing.getHash(selectedFile.getPath()),selectedFile.length());
            outgoingTransfers.put(targetUser.replace("*",""),selectedFile.getPath());
        } catch (Exception e){
            ShowDialog.errorDialog(e,"Error Transferring File");
        }
    }

    /**
     * Manage file request from another user
     * @param incomingMessage Message that holds the file request
     */
    private void handleFileSendRequest(Message incomingMessage) {
        try {
            if (!ShowDialog.fileAcceptanceDialog(incomingMessage.getHeaderRecords().get("filename"), incomingMessage.getHeaderRecords().get("sender"))) {
                api.requestFileAcceptance(incomingMessage.getHeaderRecords().get("sender"), incomingMessage.getHeaderRecords().get("filename"), false);
                return;
            }
            api.requestFileAcceptance(incomingMessage.getHeaderRecords().get("sender"), incomingMessage.getHeaderRecords().get("filename"), true);
            Thread transfer = new Thread(new FileTransfer(new NetSocket(serverURL,filePort),
                    incomingMessage.getHeaderRecords().get("filename"),
                    incomingMessage.getHeaderRecords().get("checksum"),
                    Long.parseLong(incomingMessage.getHeaderRecords().get("filelength")),
                    incomingMessage.getHeaderRecords().get("sender"),
                    username));
            transfer.start();
        } catch (IOException | NextPressoException e) {
            ShowDialog.errorDialog(e,"Error Transferring File");
        }
    }

    /**
     * Manage file transfer acceptance.
     * @param incomingMessage Message that hold the info if file transfer is accepter or not
     */
    private void handleFileTransferAccept(Message incomingMessage){
        try{
            if (incomingMessage.getHeaderRecords().containsKey("accepted")) {
                if (incomingMessage.getHeaderRecords().get("accepted").equals("true")) {
                    readFile(incomingMessage);
                } else ShowDialog.warningDialog("User '"+incomingMessage.getHeaderRecords().get("sender")+"' rejected the file '"+incomingMessage.getHeaderRecords().get("filename")+"'", "File Upload");
            }
        }
        catch (IOException | NoSuchAlgorithmException e) {
            ShowDialog.errorDialog(e,"Error Transferring files");
        }
    }

    /**
     * Start the process of reading a file from the server
     * @param incomingMessage Message with file transfer setup data
     */
    private void readFile(Message incomingMessage) throws IOException, NoSuchAlgorithmException {
        Thread fileReaderThread = new Thread(new FileTransfer(new NetSocket(serverURL, filePort),
                incomingMessage.getHeaderRecords().get("filename"),
                MD5Hashing.getHash(outgoingTransfers.get(incomingMessage.getHeaderRecords().get("sender"))),
                incomingMessage.getHeaderRecords().get("sender"),
                username,
                outgoingTransfers.get(incomingMessage.getHeaderRecords().get("sender"))),"FileReaderThread");
        fileReaderThread.start();
    }

    /**
     * Get session key from another user
     * @param incomingMessage Message holding the session key
     */
    private void handleNewSessionKey(Message incomingMessage){
        String key = CryptoTools.decryptRSAString(privateKey,incomingMessage.getPayload().split(",")[0]);
        String iv = CryptoTools.decryptRSAString(privateKey,incomingMessage.getPayload().split(",")[1]);
        sessionKeys.put(incomingMessage.getHeaderRecords().get("sender"),new AbstractMap.SimpleEntry<>(key,iv));
    }

    /**
     * Display an error on the footer of the GUI
     * @param e Exception to display
     */
    private void renderException(Exception e){
        String message = e.getMessage();
        message = message.replace("[ERROR]: ","");
        gui.displayError("Error - " + message);
    }
}
