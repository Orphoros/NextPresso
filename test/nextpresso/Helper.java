package nextpresso;

import nextpresso.tools.ApiProtocol;
import nextpresso.tools.NPPReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;

public class Helper {

    public static String buildProtocolString(String header, String body){
        return (char) ApiProtocol.PROTOCOL_DATA_START.code + header + (char)ApiProtocol.PROTOCOL_DATA_HEADER_SEPARATOR.code + body + (char)ApiProtocol.PROTOCOL_DATA_END.code;
    }

    public static String readServerMessage(BufferedReader reader) throws IOException {
        long start = System.currentTimeMillis();
        long end = start + 3000; //3 seconds timeout
        try {
            while (System.currentTimeMillis() < end) {
                String incomingStringMessage;
                if ((incomingStringMessage = NPPReader.readMessage(reader)) != null) {
                    return incomingStringMessage;
                }
            }
            return "TIMEOUT";
        }catch (SocketException e){
            return "TIMEOUT";
        }
    }

    public static void skipMessage(BufferedReader reader) throws IOException {
        readServerMessage(reader);
    }
}
