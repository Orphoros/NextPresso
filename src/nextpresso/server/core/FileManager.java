package nextpresso.server.core;

import nextpresso.tools.ApiProtocol;
import nextpresso.model.Message;
import nextpresso.model.NextPressoException;
import nextpresso.model.NextPressoMessageBuilder;
import nextpresso.model.NetSocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Manager that handles a file socket.
 */
public class FileManager implements Runnable {
    protected boolean rawByteMode = false; //Used to switch from NPP messages to byte data
    protected boolean inactive = false;
    protected String remoteUser, currentUser;
    private final Map<String, FileManager> transferUsers;
    protected NetSocket targetSocket;
    private final NetSocket socket;

    /**
     * Create a new file manager for a socket
     * @param socket Socket to manage
     * @param transferUsers List of the 2 parties (file sender and receiver) - String: username, FileManager: Manager that manager their file socket
     */
    public FileManager(NetSocket socket, Map<String, FileManager> transferUsers) {
        this.socket = socket;
        this.transferUsers = transferUsers;
        remoteUser = currentUser = null;
    }

    /**
     * Process incoming messages
     */
    @Override
    public void run() {
        try {
            try {
                socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.MESSAGE_SERVER_INFO, "Connected to \"Latte\" file transfer port").buildProtocolString());
                while (!rawByteMode) {
                    if (socket.isClosed()) break;
                    if (socket.isMessageIncoming()) {
                        String incomingMessage = socket.getIncomingMessage();
                        if (incomingMessage == null) continue;
                        String response = handleMessage(new NextPressoMessageBuilder(incomingMessage).buildMessage());
                        if (response != null) socket.sendMessage(response);
                    }
                }
                waitForPartner();
                startFileTransfer();
            } catch (NextPressoException e) {
                System.err.println("> " + e.getMessage());
                socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.ERROR_INTERNAL_ERROR,e.getMessage()).buildProtocolString());
            } catch (TimeoutException e){
                System.err.println("> " + e.getMessage());
                socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.ERROR_TIMEOUT,e.getMessage()).buildProtocolString());
            } finally {
                socket.closeConnection();
                if (transferUsers.containsKey(remoteUser) && (transferUsers.get(remoteUser) == null || transferUsers.get(remoteUser).inactive)) {
                    transferUsers.remove(remoteUser);
                    transferUsers.remove(currentUser);
                } else inactive = true;
            }
        } catch (IOException e){
            System.err.println("> Could not properly communicate with user '" + currentUser + "' to handle exception!");
        }
    }

    /**
     * Start file transfer process
     */
    private void startFileTransfer() {
        targetSocket = transferUsers.get(remoteUser).socket;
        System.out.println("> File transfer for user \""+currentUser+"\" is ready!");
        socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.FILE_TRANSFER_READY).buildProtocolString());
        while (rawByteMode) {
            socket.copyBytesFromInput(transferUsers.get(remoteUser).socket.getOutputStream());
            rawByteMode = false;
        }
    }

    /**
     * Method to wait for other partner
     * @throws TimeoutException If partner does not connect in 5 seconds
     */
    private void waitForPartner() throws TimeoutException {
        long start = System.currentTimeMillis();
        socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.FILE_AWAIT_PARTNER).buildProtocolString());
        System.out.println("~~ FILE-MNGR \""+currentUser+"\" is waiting for \""+remoteUser+"\"...");

        while (transferUsers.get(remoteUser) == null) if (System.currentTimeMillis() - start >= 5000) throw new TimeoutException("Transfer partner timed out!");
    }

    /**
     * Handle NPP messages to set up and initiate the file socket between two clients
     * @param incomingMessage Incoming message to handle
     * @return NPP string answer
     */
    private String handleMessage(Message incomingMessage){
        //Check for errors
        if (incomingMessage.getHeaderCode() != ApiProtocol.FILE_AUTHENTICATION)
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_UNEXPECTED,"File socket cannot handle the received message!").buildProtocolString();
        if (!incomingMessage.getHeaderRecords().containsKey("current"))
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Current username not specified").buildProtocolString();
        if (!incomingMessage.getHeaderRecords().containsKey("remote"))
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND, "Remote username not specified").buildProtocolString();
        //Set up the 2 sides
        remoteUser = incomingMessage.getHeaderRecords().get("remote");
        currentUser = incomingMessage.getHeaderRecords().get("current");
        //Check for connected client errors
        if (!transferUsers.containsKey(currentUser))
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_UNEXPECTED, "The current user did not start a file transfer").buildProtocolString();
        if (!transferUsers.containsKey(remoteUser))
            return new NextPressoMessageBuilder(ApiProtocol.ERROR_UNEXPECTED, "The target user did not start a file transfer").buildProtocolString();
        transferUsers.replace(currentUser,this);
        //Start reading file
        rawByteMode = true;
        System.out.println("~~ FILE-MNGR Initiated transfer socket for user \""+currentUser+"\", targeting user \""+remoteUser+"\"");
        return null;
    }
}
