package nextpresso.model;

/**
 * An NPP message that is a type of Error
 */
public class ErrorMessage extends Message{
    /**
     * Create a new Error type message that came from an NPP server
     * @param builder NextPressoMessageBuilder
     */
    public ErrorMessage(NextPressoMessageBuilder builder) {
        super(builder);
    }

    /**
     * Create a new Error type message that came from the legacy server
     * @param builder LegacyMessageBuilder
     */
    public ErrorMessage(LegacyMessageBuilder builder) {
        super(builder);
    }

    /**
     * For console logging
     */
    @Override
    public String toString() {
        return switch (headerProtocol){
            case ERROR_NOT_LOGGED_IN -> "[ERROR]: User is not logged in";
            case ERROR_USER_ALREADY_LOGGED_IN -> "[ERROR]: User with the given username is already logged in";
            case ERROR_MALFORMED_PACKET -> "[ERROR]: The NextPresso.server received a malformed message";
            case ERROR_MANDATORY_DATA_NOT_FOUND -> "[ERROR]: Mandatory data is missing from either the header or the body";
            case ERROR_UNEXPECTED -> "[ERROR]: Received an unexpected message";
            case ERROR_INVALID_DATA_FORMAT -> "[ERROR]: Received a message with an invalid format";
            case ERROR_UNAUTHORIZED -> "[ERROR]: Unauthorized!";
            case ERROR_INTERNAL_ERROR -> "[ERROR]: An internal error happened!";
            case ERROR_NOT_ALLOWED -> "[ERROR]: Action is not allowed";
            case ERROR_NOT_FOUND -> "[ERROR]: Resource is not found";
            case ERROR_TIMEOUT -> "[ERROR]: System timed out";
            default -> "[ERROR]: An undefined error occurred (" + headerProtocol + ")";
        };
    }
}
