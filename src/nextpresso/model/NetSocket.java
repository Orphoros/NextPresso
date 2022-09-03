package nextpresso.model;

import nextpresso.tools.NPPReader;

import java.io.*;
import java.net.SocketException;

/**
 * Generic socket
 */
public class NetSocket {
    protected PrintWriter writer;
    protected BufferedReader reader;
    protected OutputStream outputStream;
    protected InputStream inputStream;
    protected final java.net.Socket socket;

    /**
     * Creates a new NetSocket based on an existing Java Socket
     * @param socket java.net.Socket
     */
    public NetSocket(java.net.Socket socket) {
        this.socket = socket;
        try {
            writer = new PrintWriter(socket.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new NetSocket from scratch
     * @param ip IP address
     * @param port Port number to use
     */
    public NetSocket(String ip, int port) throws IOException {
        this(new java.net.Socket(ip, port));
    }

    /**
     * Send a message
     * @param msg Message to send
     */
    public void sendMessage(String msg) {
        writer.print(msg);
        writer.flush();
    }

    /**
     * Send bytes
     * @param sourceStream InputStream containing the bytes to send
     */
    public void sendBytes(InputStream sourceStream){transferBytes(sourceStream,outputStream,-1);}

    /**
     * Copy bytes from input to a new output
     * @param destination OutputStream where the bytes will be copied to
     */
    public void copyBytesFromInput(OutputStream destination){receiveBytes(destination,-1);}

    /**
     * Read bytes
     * @param destinationStream OutputStream where the bytes should be read in
     * @param length How many bytes to read (typically the file length)
     */
    public void receiveBytes(OutputStream destinationStream, long length){transferBytes(inputStream,destinationStream,length);}

    /**
     * Transfer bytes from an InputStream to an OutputStream
     * @param input InputStream where bytes will be read in
     * @param output OutputStream where bytes should be transferred to
     * @param length Number of bytes to copy (typically the file length)
     */
    private void transferBytes(InputStream input, OutputStream output, long length){
        try {
            byte[] byteBuffer = new byte[8192]; //Initial buffer to use
            int copyLength;
            while (length !=0 && (copyLength = input.read(byteBuffer)) > 0){
                length-=copyLength;
                output.write(byteBuffer, 0, copyLength);
            }
        }
        catch (SocketException ignored) {} //No action needs to be taken if the socket unexpectedly closes
        catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Read a generic incoming message
     * @return Read message in String format
     */
    public String getIncomingMessage() throws IOException {
        return NPPReader.readMessage(reader);
    }

    /**
     * Read an NPP incoming message
     * @return Read NPP message in String format
     */
    public String getIncomingNPPMessage() throws IOException {
        return NPPReader.readNPPMessage(reader,-1);
    }

    /**
     * Terminate connection
     */
    public void closeConnection() throws IOException {
        writer.close();
        socket.close();
    }

    /**
     * Check if a socket is closed
     * @return True - socket is closed
     */
    public boolean isClosed() {
        return socket.isClosed();
    }

    /**
     * Check if there are messages to be read
     * @return True - there are messages to be read
     */
    public boolean isMessageIncoming() throws IOException {
        return reader.ready();
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }
}
