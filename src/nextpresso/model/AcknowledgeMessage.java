package nextpresso.model;

/**
 * An NPP message that is a type of Acknowledge
 */
public class AcknowledgeMessage extends Message {
    /**
     * Create a new Acknowledge type message using that came from an NPP server
     * @param builder NextPressoMessageBuilder
     */
    public AcknowledgeMessage(NextPressoMessageBuilder builder) {
        super(builder);
    }

    /**
     * Create a new Acknowledge type message using that came from the legacy server
     * @param builder LegacyMessageBuilder
     */
    public AcknowledgeMessage(LegacyMessageBuilder builder) {
        super(builder);
    }

    /**
     * Used for logging to the console
     */
    @Override
    public String toString() {
        return switch (headerProtocol){
            case ACKNOWLEDGE_BROADCAST, ACKNOWLEDGE_PRIVATE_MESSAGE, ACKNOWLEDGE_GROUP_MESSAGE -> "You wrote: "+super.payload;
            case ACKNOWLEDGE_LOGIN -> "[ACK]: You successfully logged in!";
            case ACKNOWLEDGE_LOGOUT -> "[ACK]: You successfully logged out!";
            case HEARTBEAT_RESPONSE -> "[ACK]: Server is keeping the connection alive";
            case ACKNOWLEDGE_LIST_USERS -> "[ACK]: Received list of connected users";
            case ACKNOWLEDGE_LIST_GROUPS -> "[ACK]: Received list of groups";
            case ACKNOWLEDGE_JOIN_GROUP -> "[ACK]: Successfully joined group";
            case ACKNOWLEDGE_LEAVE_GROUP -> "[ACK]: Successfully left group";
            case ACKNOWLEDGE_CREATE_GROUP -> "[ACK]: Successfully created group";
            case ACKNOWLEDGE_SEND_FILE -> "[ACK]: Successfully requested file transfer";
            case ACKNOWLEDGE_RECEIVE_FILE -> "[ACK]: Successfully responded to file transfer request";
            case ACKNOWLEDGE_SUBMIT_KEY -> "[ACK]: Successfully submitted public key";
            case ACKNOWLEDGE_GET_KEY -> "[ACK]: Successfully received public key";
            default -> "[ACK_ERROR]: An undefined response was received (" + headerProtocol + ")";
        };
    }
}
