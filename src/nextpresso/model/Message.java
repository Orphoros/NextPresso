package nextpresso.model;

import nextpresso.tools.ApiProtocol;

import java.util.HashMap;

/**
 * A generic exchange message
 */
public class Message {
    protected final ApiProtocol headerProtocol;
    protected final String payload;
    protected final HashMap<String, String> headerRecords;

    /**
     * Create a new generic message that came from an NPP server
     * @param builder NextPressoMessageBuilder
     */
    public Message(NextPressoMessageBuilder builder) {
        this.headerProtocol = builder.headerCode;
        this.payload = builder.body;
        this.headerRecords = builder.headerRecords;
    }

    /**
     * Create a new generic message that came from the legacy server
     * @param builder LegacyMessageBuilder
     */
    public Message(LegacyMessageBuilder builder){
        this.headerProtocol = builder.header;
        this.payload = builder.body;
        this.headerRecords = builder.headerRecords;
    }

    /**
     * Get the header code type of the message
     * @return ApiProtocol ENUM of the header code
     */
    public ApiProtocol getHeaderCode() {
        return headerProtocol;
    }

    /**
     * Get the payload contents of the message
     * @return The payload in String format
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Get the list of key-value pair records from the header
     * @return A HashMap of header records
     */
    public HashMap<String, String> getHeaderRecords() {
        return new HashMap<>(headerRecords);
    }

    /**
     * Used for printing to the GUI
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        switch (headerProtocol){
            case MESSAGE_CHAT -> {
                if(headerRecords.containsKey("authenticated") && headerRecords.get("authenticated").equals("true")) builder.append('*');
                builder.append(headerRecords.get("sender"));
                if (headerRecords.containsKey("groupname")) builder.append(" (").append(headerRecords.get("groupname")).append(")");
                builder.append(" says: ");
                builder.append(payload);
                return builder.toString();
            }
            case MESSAGE_SERVER_INFO -> {
                builder.append("SERVER says: ");
                builder.append(payload);
                return builder.toString();
            }
            case MESSAGE_SERVER_GROUP_NEW_USER -> {
                builder.append("SERVER (");
                builder.append(headerRecords.get("groupname"));
                builder.append(") says: ");
                if(headerRecords.get("authenticated").equals("true")) builder.append("*");
                builder.append(headerRecords.get("username"));
                builder.append(" has joined this group!");
                builder.append(payload);
                return builder.toString();
            }
            default -> {
                builder.append("[WARNING] Unparseable message: ");
                builder.append(payload);
                return builder.toString();
            }
        }
    }
}
