package nextpresso.model;

import nextpresso.tools.ApiProtocol;

import java.util.HashMap;

/**
 * Build NextPresso messages
 */
public class NextPressoMessageBuilder {
    protected final ApiProtocol headerCode;
    protected final String body;
    protected final HashMap<String, String> headerRecords;

    private final char HEADING_START = (char) ApiProtocol.PROTOCOL_DATA_START.code;
    private final char HEADING_END = (char) ApiProtocol.PROTOCOL_DATA_HEADER_SEPARATOR.code;
    private final char BLOCK_END = (char)ApiProtocol.PROTOCOL_DATA_END.code;

    /**
     * Create a new NextPresso builder from scratch using a header code and a body
     * @param headerCode Header code of the message to identify the message type
     * @param body The body of the message
     */
    public NextPressoMessageBuilder(ApiProtocol headerCode, String body) {
        this.headerCode = headerCode;
        this.headerRecords = new HashMap<>();
        this.body = body == null ? "" : body;
    }

    /**
     * Create a new NextPresso builder from scratch without a body using a header code
     * @param headerCode Header code of the message to identify the message type
     */
    public NextPressoMessageBuilder(ApiProtocol headerCode) {
        this(headerCode,"");
    }

    /**
     * Create a new NextPresso builder from an existing, raw, byte message in a string format.
     * This will parse the raw message and then individual data can be retrieved from it.
     * @param messageFromServer Raw NPP string message
     * @throws NextPressoException If the message cannot be parsed
     */
    public NextPressoMessageBuilder(String messageFromServer) throws NextPressoException {
        this.headerRecords = new HashMap<>();
        if(messageFromServer != null) {
            if(messageFromServer.chars().filter(c -> c == HEADING_START).count() == 0) throw new NextPressoException("Communication Error","Received message has an incorrect format!");
            if(messageFromServer.chars().filter(c -> c == HEADING_END).count() == 0) throw new NextPressoException("Communication Error","Received message has an incorrect format!");
            if(messageFromServer.chars().filter(c -> c == BLOCK_END).count() == 0) throw new NextPressoException("Communication Error","Received message has an incorrect format!");

            int headingStart = messageFromServer.indexOf(HEADING_START);
            int headingEnd = messageFromServer.indexOf(HEADING_END);
            int bodyEnd = messageFromServer.indexOf(BLOCK_END);

            String rawHeaderData = messageFromServer.substring(headingStart+1, headingEnd);
            String rawBodyData = messageFromServer.substring(headingEnd+1, bodyEnd);

            String[] headerData = rawHeaderData.split("/");

            if(headerData[0].equals(""))throw new NextPressoException("Communication Error","Received a message without a type identifier!");

            try {
                headerCode = ApiProtocol.parseString(headerData[0]);
            }catch (NumberFormatException e){
                throw new NextPressoException("Communication Error","Received a message without a type identifier!");
            }

            if(headerCode == ApiProtocol.ERROR_MALFORMED_PACKET) throw new NextPressoException("Communication Error","Received a message with an unknown type identifier!");

            populateHeaderRecords(headerData);
            body = rawBodyData;
        }else {
            headerCode = null;
            body = null;
        }

    }

    private void populateHeaderRecords(String[] headerData) throws NextPressoException {
        for(int i = 1; i < headerData.length; i++){
            if(!headerData[i].contains("=")) throw new NextPressoException("Communication Error","Found a header section data in message that is not properly formatted!");
            String[] dataPair = headerData[i].split("=");
            if(dataPair.length != 2) throw new NextPressoException("Communication Error","Header section data in message doesn't contain a proper key-value pair!");
            if(dataPair[0].equals("") || dataPair[1].equals("")) throw new NextPressoException("Communication Error","A header section record is missing the key!");
            headerRecords.put(dataPair[0],dataPair[1]);
        }
    }

    public NextPressoMessageBuilder sender (String sender) throws NextPressoException {
        if(sender == null || sender.equals("")) throw new NextPressoException("Input Error", "Username for sender is not defined!");
        if(containsInvalidCharacters(sender)) throw new NextPressoException("Input Error", "Sender contains invalid characters!");
        headerRecords.put("sender",sender);
        return this;
    }

    public NextPressoMessageBuilder username(String username) throws NextPressoException {
        if(username == null || username.equals("")) throw new NextPressoException("Input Error", "Username for sender is not defined!");
        if(containsInvalidCharacters(username)) throw new NextPressoException("Input Error", "Sender contains invalid characters!");
        headerRecords.put("username",username);
        return this;
    }

    /**
     * Raw password format, not the hashed one
     */
    public NextPressoMessageBuilder password(String pw) throws NextPressoException {
        if(pw == null || pw.equals("")) throw new NextPressoException("Input Error", "Password is not defined!");
        if(containsInvalidCharacters(pw)) throw new NextPressoException("Input Error", "Password contains invalid characters!");
        headerRecords.put("password",pw);
        return this;
    }

    public NextPressoMessageBuilder authenticated(boolean isAuthenticated) {
        headerRecords.put("authenticated",isAuthenticated ? "true" : "false");
        return this;
    }

    public NextPressoMessageBuilder encrypted(boolean isEncrypted) {
        headerRecords.put("encrypted",isEncrypted ? "true" : "false");
        return this;
    }

    public NextPressoMessageBuilder groupname(String groupname) throws NextPressoException {
        if(groupname == null || groupname.equals("")) throw new NextPressoException("Input Error", "Groupname is not defined!");
        if(containsInvalidCharacters(groupname)) throw new NextPressoException("Input Error", "Groupname contains invalid characters!");
        headerRecords.put("groupname",groupname);
        return this;
    }

    public NextPressoMessageBuilder filename(String filename) throws NextPressoException {
        if(filename == null || filename.equals("")) throw new NextPressoException("Input Error", "Filename is not defined!");
        if(containsInvalidCharacters(filename)) throw new NextPressoException("Input Error", "Filename contains invalid characters!");
        headerRecords.put("filename",filename);
        return this;
    }

    public NextPressoMessageBuilder filelength(long filelength)  {
        headerRecords.put("filelength",String.valueOf(filelength));
        return this;
    }

    /**
     * MD5 checksum
     */
    public NextPressoMessageBuilder checksum(String checksum) throws NextPressoException {
        if(checksum == null || checksum.equals("")) throw new NextPressoException("Input Error", "Checksum is not defined!");
        if(containsInvalidCharacters(checksum)) throw new NextPressoException("Input Error", "Checksum contains invalid characters!");
        if (!checksum.matches("^[a-f0-9]{32}$")) throw new NextPressoException("Input Error", "Checksum is not in an MD5 format!");
        headerRecords.put("checksum",checksum);
        return this;
    }

    public NextPressoMessageBuilder accepted(boolean accepted) throws NextPressoException {
        headerRecords.put("accepted",String.valueOf(accepted));
        return this;
    }

    public NextPressoMessageBuilder current(String username) throws NextPressoException {
        if(username == null || username.equals("")) throw new NextPressoException("Input Error", "Username for current user is not defined!");
        if(containsInvalidCharacters(username)) throw new NextPressoException("Input Error", "Current username contains invalid characters!");
        headerRecords.put("current",username);
        return this;
    }

    public NextPressoMessageBuilder remote(String username) throws NextPressoException {
        if(username == null || username.equals("")) throw new NextPressoException("Input Error", "Username for remote is not defined!");
        if(containsInvalidCharacters(username)) throw new NextPressoException("Input Error", "Remote username contains invalid characters!");
        headerRecords.put("remote",username);
        return this;
    }

    /**
     * Check if an input for header values contains invalid characters
     * @param input User input for a header value
     * @return True if the input contains invalid characters
     */
    private boolean containsInvalidCharacters(String input){
        return input.contains(Character.toString(HEADING_END)) ||
                input.contains(Character.toString(HEADING_START)) ||
                input.contains(Character.toString(BLOCK_END)) ||
                input.contains("/") ||
                input.contains("=");
    }

    /**
     * Transforms the stored data into a raw, binary string message that can be sent through the network
     * @return String encoded NextPresso message
     */
    public String buildProtocolString(){
        if(headerCode != null) {
            StringBuilder b = new StringBuilder();
            b.append(HEADING_START).append(headerCode.code);

            headerRecords.forEach((k,v) -> b.append('/').append(k).append("=").append(v));

            b.append(HEADING_END).append(body).append(BLOCK_END);

            return b.toString();
        } else return null;
    }

    /**
     * Transforms the stored data into a NextPresso message object
     * @return NextPresso message object
     */
    public Message buildMessage(){
        if(headerCode != null) {
            int firstNibble = headerCode.code >> 4;
            return switch (firstNibble) {
                case 0x1 -> new AcknowledgeMessage(this);
                case 0x3 -> new Message(this);
                case 0x4 -> new RequestMessage(this);
                case 0x6 -> new EncryptionMessage(this);
                case 0xF -> new HeartBeatMessage(this);
                default -> new ErrorMessage(this);
            };
        }else return null;
    }
}
