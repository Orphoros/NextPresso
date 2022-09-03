package nextpresso.model;

/**
 * An NPP message that is a type of Encryption
 */
public class EncryptionMessage extends Message{
    /**
     * Create a new Encryption type message using that came from an NPP server
     * @param builder NextPressoMessageBuilder
     */
    public EncryptionMessage(NextPressoMessageBuilder builder) {
        super(builder);
    }

    /**
     * Used for console logging
     */
    @Override
    public String toString() {
        return switch (headerProtocol){
            case ENCRYPTION_SET_KEY -> "[ENC]: Key is set";
            case ENCRYPTION_KEY_FORWARDED -> "[ENC]: Key forwarded";
            default -> "[ENC]: Unknown protocol!";
        };
    }
}
