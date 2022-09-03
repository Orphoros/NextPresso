package nextpresso.model;

import nextpresso.tools.ApiProtocol;

import java.util.HashMap;

/**
 * Message builder for the legacy server to maintain backwards compatibility
 */
public class LegacyMessageBuilder {
    protected ApiProtocol header;
    protected String body;
    protected final HashMap<String, String> headerRecords;

    /**
     * Create a new Legacy message builder
     * @param headerCode NextPresso header code to identify the message type
     * @param body body of the message
     */
    public LegacyMessageBuilder(ApiProtocol headerCode, String body) {
        this.header = headerCode;
        this.body = body;
        this.headerRecords = new HashMap<>();
    }

    /**
     * Create a new Legacy message builder from an existing string message
     * @param messageFromServer Incoming message from the legacy server
     * @throws NextPressoException If the message could not be parsed
     */
    public LegacyMessageBuilder(String messageFromServer) throws NextPressoException {
        headerRecords = new HashMap<>();
        if (messageFromServer != null && !messageFromServer.isBlank()){
            header = parseMessage(messageFromServer);
        } else throw new NextPressoException("Legacy communication error", "Invalid message received");
    }

    /**
     * Parses legacy messages to NPP messages
     * @param messageFromServer Message from the legacy server
     * @return The NPP-parsed ApiProtocol header type
     * @throws NextPressoException If message is incorrect
     */
    private ApiProtocol parseMessage(String messageFromServer) throws NextPressoException {
        String type = messageFromServer.split(" ")[0];
        String payload = messageFromServer.replace(type + " ", "");

        return switch (type){
            case "OK" -> handleOK(payload);
            case "BCST" -> handleBCST(payload);
            case "PING" -> handlePING();
            case "INFO" -> handleINFO(payload);
            default -> handleERROR(type);
        };
    }

    private ApiProtocol handleERROR(String type) throws NextPressoException {
        body = null;

        return switch (type){
            case "ER00" -> header = ApiProtocol.ERROR_MALFORMED_PACKET;
            case "ER01" -> header = ApiProtocol.ERROR_USER_ALREADY_LOGGED_IN;
            case "ER02" -> header = ApiProtocol.ERROR_INVALID_DATA_FORMAT;
            case "ER03" -> header = ApiProtocol.ERROR_NOT_LOGGED_IN;
            default -> throw new NextPressoException("Legacy communication error","Unknown error message from server");
        };
    }

    private ApiProtocol handleINFO(String payload) throws NextPressoException {
        if (payload.isBlank()) throw new NextPressoException("Legacy communication error", "No message in INFO message");
        headerRecords.put("sender", "SERVER");
        body = payload;
        return header = ApiProtocol.MESSAGE_SERVER_INFO;
    }

    private ApiProtocol handlePING() {
        body = null;
        return header = ApiProtocol.HEARTBEAT_REQUEST;
    }

    private ApiProtocol handleBCST(String payload) throws NextPressoException {
        String sender = payload.split(" ")[0];
        if (sender.isBlank()) throw new NextPressoException("Legacy communication error", "No sender for incoming broadcast");
        String message = payload.replace(sender + " ","");
        if (sender.isBlank()) throw new NextPressoException("Legacy communication error", "No message in incoming broadcast");
        headerRecords.put("sender", sender);
        body = message;
        return header = ApiProtocol.MESSAGE_CHAT;
    }

    private ApiProtocol handleOK(String payload) throws NextPressoException {
        if (payload.isBlank()) throw new NextPressoException("Legacy communication error","Incomplete OK message from server");
        String acknowledgeCommand = payload.split(" ")[0];
        if (acknowledgeCommand.isBlank()) throw new NextPressoException("Legacy communication error","No command in OK message");
        switch (acknowledgeCommand) {
            case "BCST" -> {
                body = payload.replace(payload.split(" ")[0] + " ","");
                header = ApiProtocol.ACKNOWLEDGE_BROADCAST;
            }
            case "Goodbye" -> {
                header = ApiProtocol.ACKNOWLEDGE_LOGOUT;
                body = null;
            }
            default -> {
                header = ApiProtocol.ACKNOWLEDGE_LOGIN;
                body = payload;
            }
        }
        return header;
    }

    /**
     * Creates a new NextPresso message object from the legacy data
     * @return NextPresso message object
     */
    public Message buildMessage(){
        if (header != null){
            int firstNibble = header.code >> 4;
            return switch (firstNibble){
                case 0x1 -> new AcknowledgeMessage(this);
                case 0x3 -> new Message(this);
                case 0x4 -> new RequestMessage(this);
                case 0xF -> new HeartBeatMessage(this);
                default -> new ErrorMessage(this);
            };
        } else return null;
    }

    /**
     * Creates a new legacy message that matches the legacy protocol standards.
     * @return Legacy protocol message
     */
    public String buildProtocolString(){
        StringBuilder b = new StringBuilder();

        switch (header){
            case REQUEST_LOGIN -> b.append("CONN");
            case REQUEST_BROADCAST -> b.append("BCST");
            case REQUEST_LOGOUT -> b.append("QUIT");
            case HEARTBEAT_RESPONSE -> b.append("PONG");
        }

        if (body != null) b.append(" ").append(body);

        b.append("\n");
        return b.toString();
    }
}
