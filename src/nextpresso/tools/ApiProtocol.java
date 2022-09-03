package nextpresso.tools;

/**
 * Enum to hold all the NextPresso 1.1 protocol header codes defined in the spec
 */
public enum ApiProtocol {
    ERROR_MALFORMED_PACKET(0x2F),
    ERROR_USER_ALREADY_LOGGED_IN(0x21),
    ERROR_NOT_LOGGED_IN(0x23),
    ERROR_UNEXPECTED(0x28),
    ERROR_NOT_FOUND(0x24),
    ERROR_NOT_ALLOWED(0x29),
    ERROR_MANDATORY_DATA_NOT_FOUND(0x25),
    ERROR_INVALID_DATA_FORMAT(0x22),
    ERROR_UNAUTHORIZED(0x27),
    ERROR_INTERNAL_ERROR(0x26),
    ERROR_TIMEOUT(0x2A),

    MESSAGE_SERVER_INFO(0x30),
    MESSAGE_SERVER_GROUP_NEW_USER(0x31),
    MESSAGE_CHAT(0x32),

    REQUEST_LOGIN(0x41),
    REQUEST_LOGOUT(0x42),
    REQUEST_BROADCAST(0x43),
    REQUEST_LIST_USERS(0x44),
    REQUEST_LIST_GROUPS(0x45),
    REQUEST_JOIN_GROUP(0x46),
    REQUEST_CREATE_GROUP(0x47),
    REQUEST_LEAVE_GROUP(0x48),
    REQUEST_PRIVATE_MESSAGE(0x49),
    REQUEST_GROUP_MESSAGE(0x4A),
    REQUEST_SEND_FILE(0x4B),
    REQUEST_RECEIVE_FILE(0x4C),
    REQUEST_SUBMIT_KEY(0x4D),
    REQUEST_GET_KEY(0x4E),

    ACKNOWLEDGE_LOGIN(0x11),
    ACKNOWLEDGE_LOGOUT(0x12),
    ACKNOWLEDGE_BROADCAST(0x13),
    ACKNOWLEDGE_LIST_USERS(0x14),
    ACKNOWLEDGE_LIST_GROUPS(0x15),
    ACKNOWLEDGE_JOIN_GROUP(0x16),
    ACKNOWLEDGE_CREATE_GROUP(0x17),
    ACKNOWLEDGE_LEAVE_GROUP(0x18),
    ACKNOWLEDGE_PRIVATE_MESSAGE(0x19),
    ACKNOWLEDGE_GROUP_MESSAGE(0x1A),
    ACKNOWLEDGE_SEND_FILE(0x1B),
    ACKNOWLEDGE_RECEIVE_FILE(0x1C),
    ACKNOWLEDGE_SUBMIT_KEY(0x1D),
    ACKNOWLEDGE_GET_KEY(0x1E),

    FILE_AUTHENTICATION(0x50),
    FILE_AWAIT_PARTNER(0x51),
    FILE_TRANSFER_READY(0x52),

    ENCRYPTION_SET_KEY(0x60),
    ENCRYPTION_KEY_FORWARDED(0x61),

    HEARTBEAT_REQUEST(0xF1),
    HEARTBEAT_RESPONSE(0xF2),

    PROTOCOL_DATA_START(0x01),
    PROTOCOL_DATA_END(0x04),
    PROTOCOL_DATA_HEADER_SEPARATOR(0x1F);

    public final int code;

    ApiProtocol(int code) {
        this.code = code;
    }

    /**
     * Parses a string code to an ApiProtocol enum
     * @param code Integer header code encoded in a string
     * @return The found ApiProtocol enum. If not found, returns a ERROR_MALFORMED_PACKET enum
     */
    public static ApiProtocol parseString(String code){
        for (var v: ApiProtocol.values()){
            if (v.code == Integer.parseInt(code)){
                return v;
            }
        }
        return ERROR_MALFORMED_PACKET;
    }
}