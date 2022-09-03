package nextpresso.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A reader for NextPresso messages
 */
public class NPPReader {

    /**
     * Read data from the buffer in the form of a message.
     * This method will attempt to determine the incoming message type (NPP/1.1 or legacy) and process accordingly
     * @param reader The buffer to read from
     * @return The interpreted string
     * @throws IOException If the reading the buffer encountered a problem
     */
    public static String readMessage(BufferedReader reader) throws IOException {
        int startByte = reader.read();
        //0x20 means that it is not a unicode control character that is used by NPP
        if (startByte >= 0x20) return readLegacy(reader,startByte);
        return readNPPMessage(reader, startByte);
    }

    /**
     * Read data from the buffer as a NextPresso 1.1 message.
     * The reader will start reading a message once it encounters a message start byte (0x01).
     * From that byte (inclusive) the reader will keep reading and buffering incoming bytes till it encounters
     * a message end byte (0x04).
     * If the reader does not detect byte 0x04 after detecting byte 0x01 in 4 seconds, the reader will time out.
     * @param reader The buffer to read from
     * @param firstByte The fist byte read if the function is called by {@link #readMessage(BufferedReader)}. Otherwise, use -1
     * @return The string of the raw NextPresso message
     * @throws IOException If the reading the buffer encountered a problem
     */
    public static String readNPPMessage(BufferedReader reader, int firstByte) throws IOException {
        ArrayList<Character> inputBytes = new ArrayList<>();
        int lastByte = 0;
        if (firstByte == -1) firstByte = reader.read();
        if (firstByte != ApiProtocol.PROTOCOL_DATA_START.code) return null; //Message is corrupted
        inputBytes.add((char) firstByte);
        long start = System.currentTimeMillis();
        while (reader.ready() && lastByte != ApiProtocol.PROTOCOL_DATA_END.code && System.currentTimeMillis() - start < 4000){
            lastByte = reader.read();
            inputBytes.add((char) lastByte);
        }
        return inputBytes.toString()
                .substring(1, 3 * inputBytes.size() - 1)
                .replaceAll(", ", "");
    }

    /**
     * Reads data from the buffer as a legacy message.
     * The reader will read all bytes until a Line Feed 0x0A or NULL 0x00 byte
     * @param reader The buffer to read from
     * @param firstByte The fist byte read if the function is called by {@link #readMessage(BufferedReader)}. Otherwise, use -1
     * @return The string of the raw legacy message
     * @throws IOException If the reading the buffer encountered a problem
     */
    private static String readLegacy(BufferedReader reader, int firstByte) throws IOException {
        ArrayList<Character> inputBytes = new ArrayList<>();
        inputBytes.add((char) firstByte);
        int lastByte;
        while ((lastByte = reader.read()) != 0x00){
            if (lastByte == 0x0A) break;
            inputBytes.add((char) lastByte);
        }
        String output = inputBytes.toString();
        //Regex is used to match legacy protocol pattern
        if (output.split("^[A-Z]{2}\\w+ [\\w\\p{Punct}\\s]+").length == 0) return null; //Message is corrupted
        return output.split("^[A-Z]{2}\\w+ [\\w\\p{Punct}\\s]+")[0]
                .substring(1, 3 * inputBytes.size() - 1)
                .replaceAll(", ", "");
    }
}
