package nextpresso.model;

/**
 * An NPP message that is a type of HeartBeat
 */
public class HeartBeatMessage extends Message{
    /**
     * Create a new HeartBeat type message using that came from an NPP server
     * @param builder NextPressoMessageBuilder
     */
    public HeartBeatMessage(NextPressoMessageBuilder builder) {
        super(builder);
    }

    /**
     * Create a new HeartBeat type message using that came from the legacy server
     * @param builder LegacyMessageBuilder
     */
    public HeartBeatMessage(LegacyMessageBuilder builder) {
        super(builder);
    }

    /**
     * Used for console logging
     */
    @Override
    public String toString() {
        return switch (headerProtocol){
            case HEARTBEAT_REQUEST -> "[HEARTBEAT]: Request";
            case HEARTBEAT_RESPONSE -> "[HEARTBEAT]: Response to request";
            default -> "[HEARTBEAT]: Unknown protocol!";
        };
    }
}
