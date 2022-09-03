package nextpresso.client.UI;

import nextpresso.model.Message;
import nextpresso.model.NetSocket;
import nextpresso.model.NextPressoException;
import nextpresso.model.NextPressoMessageBuilder;
import nextpresso.tools.MD5Hashing;
import nextpresso.tools.ApiProtocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

/**
 * Runnable to communicate with the file socket
 */
public class FileTransfer implements Runnable{
    private final String fileName, fileHash, remoteUser, currentUser, sourceFilePath;
    private final NetSocket socket;
    private final long fileLength;
    private final static String DOWNLOAD_DIALOG_BOX_TITLE = "File Download";
    private final static String UPLOAD_DIALOG_BOX_TITLE = "File Upload";

    /**
     * Used by the receiver
     */
    public FileTransfer(NetSocket socket, String fileName, String fileHash, long fileLength, String remoteUser, String currentUser) {
        this(socket, fileName, fileHash, fileLength, remoteUser, currentUser, null);
    }

    /**
     * Used by the sender
     */
    public FileTransfer(NetSocket socket, String fileName, String fileHash, String remoteUser, String currentUser, String sourceFilePath) {
        this(socket, fileName, fileHash, 0L, remoteUser, currentUser, sourceFilePath);
    }

    private FileTransfer(NetSocket socket, String fileName, String fileHash, long fileLength, String remoteUser, String currentUser, String sourceFilePath){
        this.socket = socket;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.fileLength = fileLength;
        this.remoteUser = remoteUser;
        this.currentUser = currentUser;
        this.sourceFilePath = sourceFilePath;
    }

    @Override
    public void run() {
        try {
            socket.sendMessage(new NextPressoMessageBuilder(ApiProtocol.FILE_AUTHENTICATION).current(currentUser).remote(remoteUser).buildProtocolString());
            waitForPartner();
            System.out.println("[FILE]: Transferring...");
            if (sourceFilePath != null) sendFile();
            else receiveFile();
        } catch (IOException | NoSuchAlgorithmException | NextPressoException | TimeoutException e) {
            ShowDialog.errorDialog(e,"Transfer Error");
        }
    }

    /**
     * Handle receiving file
     */
    private void receiveFile() throws NoSuchAlgorithmException, IOException {
        File target = new File(System.getProperty("user.home") + "/Downloads/" + fileName);
        readData(target);
        if (!fileHash.equals(MD5Hashing.getHash(target.getPath()))) {
            ShowDialog.warningDialog("File '" + fileName + "' could not be verified after downloading. File deleted", DOWNLOAD_DIALOG_BOX_TITLE);
            if (!target.delete()) ShowDialog.errorDialog("Could not delete file '"+fileName+"'! Please check the file and its location and try again manually.", DOWNLOAD_DIALOG_BOX_TITLE);
        }
        ShowDialog.infoDialog("File '" + fileName + "' has been successfully received!", DOWNLOAD_DIALOG_BOX_TITLE);
    }

    /**
     * Handle sending file
     */
    private void sendFile() {
        File source = new File(sourceFilePath);
        sendData(source);
        ShowDialog.infoDialog("File '" + fileName + "' has been successfully sent!", UPLOAD_DIALOG_BOX_TITLE);
    }

    /**
     * Handle wait timeout for the partner
     */
    private void waitForPartner() throws IOException, NextPressoException, TimeoutException {
        boolean ready= false;
        long startTime = System.currentTimeMillis();
        while (!ready){
            System.out.println("[FILE]: Waiting for partner...");
            if (System.currentTimeMillis() - startTime >= 5000) throw new TimeoutException("Partner timeout");
            String responseString = socket.getIncomingMessage();
            if (responseString == null) continue;
            Message response = new NextPressoMessageBuilder(responseString).buildMessage();
            if (response.getHeaderCode() == ApiProtocol.FILE_TRANSFER_READY) ready = true;
        }
    }

    /**
     * Read bytes into a file
     * @param data File in which the data will be downloaded
     */
    private void readData(File data){
        try {
            data.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(data);
            socket.receiveBytes(outputStream, fileLength);
            outputStream.flush();
            socket.closeConnection();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Send file byte by byte
     * @param data File to send
     */
    private void sendData(File data){
        try {
            FileInputStream inputStream = new FileInputStream(data);
            socket.sendBytes(inputStream);
            inputStream.close();
            socket.closeConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
