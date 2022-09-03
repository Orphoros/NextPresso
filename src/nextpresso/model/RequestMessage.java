package nextpresso.model;

/**
 * An NPP message that is a type of Request
 */
public class RequestMessage extends Message{
    /**
     * Create a new Request type message that came from an NPP server
     * @param builder NextPressoMessageBuilder
     */
    public RequestMessage(NextPressoMessageBuilder builder) {
        super(builder);
    }

    /**
     * Create a new Error type message that came from the legacy server
     * @param builder LegacyMessageBuilder
     */
    public RequestMessage(LegacyMessageBuilder builder) {
        super(builder);
    }

    /**
     * Used for logging to the console
     */
    @Override
    public String toString() {
        return switch (headerProtocol){
            case REQUEST_BROADCAST -> "[SEND]: Broadcast message sent successfully";
            case REQUEST_LOGIN -> "[SEND]: Login request sent successfully";
            case REQUEST_LOGOUT -> "[SEND]: Logout request sent successfully";
            case REQUEST_CREATE_GROUP -> "[SEND]: Group create request sent successfully";
            case REQUEST_JOIN_GROUP -> "[SEND]: Group join request sent successfully";
            case REQUEST_PRIVATE_MESSAGE -> "[SEND]: Private message sent successfully";
            case REQUEST_GROUP_MESSAGE -> "[SEND]: Group message sent successfully";
            case REQUEST_SEND_FILE -> "[SEND]: File transfer request sent successfully";
            case REQUEST_RECEIVE_FILE -> "[SEND]: File confirmation request sent successfully";
            case REQUEST_LEAVE_GROUP -> "[SEND]: Group leave request sent successfully";
            case REQUEST_LIST_GROUPS -> "[SEND]: Group listing request sent successfully";
            default -> "[SEND_ERROR]: An undefined request was sent (" + headerProtocol + ")";
        };
    }
}
