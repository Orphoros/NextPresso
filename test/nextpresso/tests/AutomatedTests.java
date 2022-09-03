package nextpresso.tests;

import nextpresso.model.NetSocket;
import nextpresso.tools.ApiProtocol;
import nextpresso.Helper;
import nextpresso.server.core.FileService;
import nextpresso.server.core.MessageService;
import nextpresso.tools.CryptoTools;
import org.junit.jupiter.api.*;

import java.io.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class AutomatedTests {
    public static PrintWriter messageSender;
    public static BufferedReader reader;
    public static java.net.Socket socket;

    public static PrintWriter messageSender2;
    public static BufferedReader reader2;
    public static java.net.Socket socket2;

    public static PrintWriter messageSender3;
    public static BufferedReader reader3;
    public static java.net.Socket socket3;

    public static char HEADING_END = (char) ApiProtocol.PROTOCOL_DATA_HEADER_SEPARATOR.code;
    public static char BLOCK_END = (char)ApiProtocol.PROTOCOL_DATA_END.code;

    @BeforeAll
    public static void startServer() {
        Thread serverThread = null;
        Thread fileThread = null;
        try {
            FileService fileServer = new FileService(7331);
            fileThread = new Thread(fileServer,"JunitFileServerThread");
            serverThread = new Thread(new MessageService(1337, fileServer), "JunitServerThread");
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert fileThread != null;
        assert serverThread != null;

        fileThread.start();
        serverThread.start();
    }

    @BeforeEach
    public void setUpVariables() throws IOException {
        //User 1
        socket = new java.net.Socket("localhost", 1337);
        messageSender = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Helper.readServerMessage(reader); //Skip welcome message

        //User 2
        socket2 = new java.net.Socket("localhost", 1337);
        messageSender2 = new PrintWriter(socket2.getOutputStream(), true);
        reader2 = new BufferedReader(new InputStreamReader(socket2.getInputStream()));
        Helper.readServerMessage(reader2); //Skip welcome message

        //User 3
        socket3 = new java.net.Socket("localhost", 1337);
        messageSender3 = new PrintWriter(socket3.getOutputStream(), true);
        reader3 = new BufferedReader(new InputStreamReader(socket3.getInputStream()));
        Helper.readServerMessage(reader3); //Skip welcome message
    }

    @AfterEach
    public void cleanup() throws IOException {
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_LOGOUT), ""));
        messageSender.flush();
        messageSender2.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_LOGOUT), ""));
        messageSender2.flush();
        messageSender3.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_LOGOUT), ""));
        messageSender3.flush();
        socket.close();
        socket2.close();
        socket3.close();
    }

    @Test
    @DisplayName("GoodWeather - Welcome message")
    public void receiveWelcomeMessage() throws IOException {
        java.net.Socket socketWelcome = new java.net.Socket("localhost", 1337);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socketWelcome.getInputStream()));

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.MESSAGE_SERVER_INFO.code), "Welcome to Latte, a NextPresso (NPP/1.1) chat server!"),response);

        socketWelcome.close();
    }

    @Test
    @DisplayName("BadWeather - Plain text is sent")
    public void sendNonProtocolString() throws IOException {
        messageSender.println("Some random text");
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.HEARTBEAT_REQUEST.code), ""),response); //Message is ignored and the heartbeat starts
    }

    @Test
    @DisplayName("BadWeather - Not closing the message with the proper byte")
    public void sendNonClosingMessage() throws IOException {
        messageSender.println((char)ApiProtocol.PROTOCOL_DATA_START.code + "header records" + (char)ApiProtocol.PROTOCOL_DATA_HEADER_SEPARATOR.code + "body");
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_MALFORMED_PACKET.code),"Received message has an incorrect format!"),response);
    }

    @Test
    @DisplayName("BadWeather - No header code is sent")
    public void sendNoHeaderCode() throws IOException {
        messageSender.println(Helper.buildProtocolString("sender=bob", "Test body"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_MALFORMED_PACKET.code), "Received a message without a type identifier!"),response);
    }

    @Test
    @DisplayName("BadWeather - Empty header section is sent")
    public void sendHeaderWithAnEmptyHeaderSection() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LIST_GROUPS.code + "//", ""));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_NOT_LOGGED_IN.code), "You need to log in first!"),response); //The empty header section is thus simply ignored
    }

    @Test
    @DisplayName("BadWeather - Incorrect character in header")
    public void sendIncorrectHeaderCharacter() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/" + ApiProtocol.PROTOCOL_DATA_START.code, "Test body"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_MALFORMED_PACKET.code), "Found a header section data in message that is not properly formatted!"),response);
    }

    @Test
    @DisplayName("BadWeather - Value in header section is empty")
    public void sendEmptyHeaderValue() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=", "Test body"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_MALFORMED_PACKET.code), "Header section data in message doesn't contain a proper key-value pair!"),response);
    }

    @Test
    @DisplayName("BadWeather - Key in header section is empty")
    public void sendEmptyHeaderKey() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=test/=test", "Test body"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_MALFORMED_PACKET.code), "A header section record is missing the key!"),response);
    }

    @Test
    @DisplayName("BadWeather - Not specifying username properly on login")
    public void sendLoginWithoutUsernameKey() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/something=Bob", "Test body"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_MANDATORY_DATA_NOT_FOUND.code), "Username to log in is not specified!"),response);
    }

    @Test
    @DisplayName("BadWeather - Username to log in too short")
    public void sendLoginWithTooShortUsername() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=1", "Test body"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_INVALID_DATA_FORMAT.code), "Username is too short!"),response);
    }

    @Test
    @DisplayName("BadWeather - Send broadcast request without logging in")
    public void sendBroadcastWithoutLoggingIn() throws IOException {
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_BROADCAST.code), "My test broadcast message"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_NOT_LOGGED_IN.code), "You need to log in first!"),response);
    }

    @Test
    @DisplayName("BadWeather - Authorize user with incorrect credentials")
    public void tryLoginWithIncorrectCredentials() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Jack/password=IncorrectPassword1234!", ""));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_UNAUTHORIZED.code), "Username or password is incorrect!"),response);
    }

    @Test
    @DisplayName("BadWeather - Login twice")
    public void tryLoginTwice() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Max", ""));
        messageSender.flush();

        String rs = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.ACKNOWLEDGE_LOGIN.code + "/authenticated=false", "Max"),rs);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Max", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_UNEXPECTED.code), "Already logged in!"),response);
    }

    @Test
    @DisplayName("BadWeather - Send acknowledge type message without the other party awaiting one")
    public void sendAcknowledgeWhenNotExpectingIt() throws IOException {
        //First log in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=JunitTestUser", ""));
        messageSender.flush();

        String rs = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.ACKNOWLEDGE_LOGIN.code + "/authenticated=false", "JunitTestUser"),rs);

        //Send acknowledge message
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "blah"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_UNEXPECTED.code), "Cannot handle the received message!"),response);
    }

    @Test
    @DisplayName("BadWeather - Join group that does not exist")
    public void joinNonExistingGroup() throws IOException {
        //First log in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=JunitTestUser", ""));
        messageSender.flush();
        Helper.skipMessage(reader);


        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_JOIN_GROUP.code +"/groupname=NonExistingGroup", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_NOT_FOUND.code), "Requested group not found!"),response);
    }

    @Test
    @DisplayName("BadWeather - Leave group that does not exist")
    public void leaveNonExistingGroup() throws IOException {
        //First log in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=JunitTestUser", ""));
        messageSender.flush();
        Helper.skipMessage(reader);


        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LEAVE_GROUP.code +"/groupname=NonExistingGroup", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_NOT_FOUND.code), "Could not find group to leave!"),response);
    }

    @Test
    @Timeout(10000)
    @DisplayName("GoodWeather - Responding to heartbeat request")
    public void answeringHeartbeatRequest() throws IOException, InterruptedException {
        //First log in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=JunitTestUser2", ""));
        messageSender.flush();

        Helper.skipMessage(reader); //Ignore response as it is already tested in another test

        //Respond to heartbeat
        String response;
        String expectedHeartbeat = Helper.buildProtocolString(String.valueOf(ApiProtocol.HEARTBEAT_REQUEST.code),"");
        while (!Helper.readServerMessage(reader).equals(expectedHeartbeat));
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.HEARTBEAT_RESPONSE.code),""));
        messageSender.flush();

        //Await new heartbeat
        while (!(response = Helper.readServerMessage(reader)).equals(expectedHeartbeat));
        Assertions.assertEquals(expectedHeartbeat,response);
    }

    @Test
    @DisplayName("BadWeather - Not responding to heartbeat requests")
    public void ignoringHeartbeatRequest() throws IOException, InterruptedException {
        //First log in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=JunitTestUser2", ""));
        messageSender.flush();

        Helper.readServerMessage(reader); //Ignore response as it is already tested in another test

        Thread.sleep(10000); //Wait till the heartbeat times out

        Helper.readServerMessage(reader); //Ignore heartbeat request message
        
        //Send acknowledge message
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "blah"));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals("TIMEOUT",response); //Connection closed so the reader times out
    }

    @Test
    @DisplayName("GoodWeather - Send multiple NPP messages once")
    public void sendMultipleRequestsOnce() throws IOException {
        //First log in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=TEST", ""));
        messageSender.flush();
        Helper.skipMessage(reader); //Ignore input as it is already tested in another test

        messageSender.print(Helper.buildProtocolString(ApiProtocol.REQUEST_BROADCAST.code + "", "Hello"));
        messageSender.print(Helper.buildProtocolString(ApiProtocol.REQUEST_BROADCAST.code + "", "Hello1")); //Should be ignored
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_BROADCAST.code + "", "Hello2"));//Should be ignored
        messageSender.print(Helper.buildProtocolString(ApiProtocol.REQUEST_BROADCAST.code + "", "Hello3"));//Should be ignored
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "Hello"),response);
        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "Hello1"),response);
        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "Hello2"),response);
        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "Hello3"),response);
    }

    @Test
    @DisplayName("GoodWeather - Send multiline body message")
    public void sendMultilineBodyMessage() throws IOException {
        //First log in
        messageSender.print(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=TEST2", ""));
        messageSender.flush();
        Helper.skipMessage(reader); //Ignore input as it is already tested in another test

        messageSender.print(Helper.buildProtocolString(ApiProtocol.REQUEST_BROADCAST.code + "", "Hello\nThis is a test\nNew Lines"));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "Hello\nThis is a test\nNew Lines"),response);
    }

    @Test
    @DisplayName("GoodWeather - Log in")
    public void login() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Emily", ""));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.ACKNOWLEDGE_LOGIN.code + "/authenticated=false", "Emily"),response);
    }

    @Test
    @DisplayName("GoodWeather - Authenticate")
    public void authenticate() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Alice/password=PWAlice1234!", ""));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.ACKNOWLEDGE_LOGIN.code + "/authenticated=true", "Alice"),response);
    }

    @Test
    @DisplayName("GoodWeather - Broadcast")
    public void broadcast() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=BroadcastSender", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=BroadcastReceiver", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender3.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=BroadcastReceiver2", ""));
        messageSender3.flush();
        Helper.skipMessage(reader3);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_BROADCAST.code + "", "TestBroadcast1"));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "TestBroadcast1"),response);

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.MESSAGE_CHAT.code + "/authenticated=false/sender=BroadcastSender", "TestBroadcast1"),response);

        response = Helper.readServerMessage(reader3);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.MESSAGE_CHAT.code + "/authenticated=false/sender=BroadcastSender", "TestBroadcast1"),response);
    }

    @Test
    @DisplayName("GoodWeather - Authenticated Broadcast")
    public void broadcastWithAuthorizedUser() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Jack/password=PWJack1234!", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=AuthBroadcastReceiver", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender3.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=AuthBroadcastReceiver2", ""));
        messageSender3.flush();
        Helper.skipMessage(reader3);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_BROADCAST.code + "", "AuthTestBroadcast1"));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_BROADCAST.code), "AuthTestBroadcast1"),response);

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.MESSAGE_CHAT.code + "/authenticated=true/sender=Jack", "AuthTestBroadcast1"),response);

        response = Helper.readServerMessage(reader3);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.MESSAGE_CHAT.code + "/authenticated=true/sender=Jack", "AuthTestBroadcast1"),response);
    }

    @Test
    @DisplayName("GoodWeather - Direct Message")
    public void DM() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=DMSender", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=DMReceiver", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender3.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=NonDMUser", ""));
        messageSender3.flush();
        Helper.skipMessage(reader3);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_PRIVATE_MESSAGE.code + "/username=DMReceiver", "TestDM1"));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_PRIVATE_MESSAGE.code), "TestDM1"),response);

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.MESSAGE_CHAT.code + "/authenticated=false/encrypted=false/sender=DMSender", "TestDM1"),response);

        response = Helper.readServerMessage(reader3);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.HEARTBEAT_REQUEST.code),""),response); //DM is not received, thus we will only get a heartbeat request
    }

    @Test
    @DisplayName("GoodWeather - Authenticated Direct Message")
    public void authDM() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob/password=PWBob1234!", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=DMReceiver", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender3.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=NonDMUser", ""));
        messageSender3.flush();
        Helper.skipMessage(reader3);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_PRIVATE_MESSAGE.code + "/username=DMReceiver", "TestDM1"));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_PRIVATE_MESSAGE.code), "TestDM1"),response);

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.MESSAGE_CHAT.code + "/authenticated=true/encrypted=false/sender=Bob", "TestDM1"),response);

        response = Helper.readServerMessage(reader3);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.HEARTBEAT_REQUEST.code),""),response); //DM is not received, thus we will only get a heartbeat request
    }

    @Test
    @DisplayName("GoodWeather - List users")
    public void listUsers() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob/password=PWBob1234!", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=User1", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender3.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=User2", ""));
        messageSender3.flush();
        Helper.skipMessage(reader3);

        messageSender2.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_LIST_USERS.code), ""));
        messageSender2.flush();

        String response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_LIST_USERS.code), "{Bob,1},{User2,0},{User1,0}"),response);
    }

    @Test
    @DisplayName("GoodWeather - Create groups, join group, get listing and leave group")
    public void joinGroup() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=GroupCreator", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_CREATE_GROUP.code + "/groupname=TestGroup1", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_CREATE_GROUP.code + "/groupname=TestGroup2", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=User2", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender3.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=User3", ""));
        messageSender3.flush();
        Helper.skipMessage(reader3);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_JOIN_GROUP.code + "/groupname=TestGroup1", ""));
        messageSender2.flush();
        String response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_JOIN_GROUP.code), "TestGroup1"),response);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LIST_GROUPS.code + "/groupname=TestGroup1", ""));
        messageSender2.flush();
        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_LIST_GROUPS.code), "{TestGroup2,0},{TestGroup1,1}"),response);

        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.MESSAGE_SERVER_GROUP_NEW_USER.code + "/authenticated=false/groupname=TestGroup1/username=User2", ""),response);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_GROUP_MESSAGE.code + "/groupname=TestGroup1", "TestGroupMessage"));
        messageSender2.flush();
        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_GROUP_MESSAGE.code), "TestGroupMessage"),response);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LEAVE_GROUP.code+"/groupname=TestGroup1",""));
        messageSender2.flush();
        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_LEAVE_GROUP.code),"TestGroup1"),response);

        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.MESSAGE_CHAT.code + "/authenticated=false/sender=User2/groupname=TestGroup1", "TestGroupMessage"),response);

        response = Helper.readServerMessage(reader3);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.HEARTBEAT_REQUEST.code),""),response); //Message was not received as user 3 is not in the group. Thus, only the heartbeat was sent by the server
    }

    @Test
    @DisplayName("BadWeather - Create group twice")
    public void listGroups() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=GroupCreator", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_CREATE_GROUP.code + "/groupname=Grp1", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_CREATE_GROUP.code), "Grp1"),response);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_CREATE_GROUP.code + "/groupname=Grp1", ""));
        messageSender.flush();

        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_NOT_ALLOWED.code), "Requested group already exists!"),response);
    }

    @Test
    @DisplayName("GoodWeather - Log out")
    public void logOut() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=user", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_LOGOUT.code), ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_LOGOUT.code), ""),response);

        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_BROADCAST.code), "Hi"));
        messageSender.flush();

        response = Helper.readServerMessage(reader);
        Assertions.assertEquals("TIMEOUT",response);
    }

    @Test
    @DisplayName("GoodWeather - Send file transfer request")
    public void sendFileTransferRequest() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileSenderUser", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileReceiverUser", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/username=FileReceiverUser/filename=test.txt/checksum=2187f15067488bff528612492a810c42/filelength=123", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_SEND_FILE.code), "test.txt"),response);

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/filename=test.txt/sender=FileSenderUser/filelength=123/checksum=2187f15067488bff528612492a810c42/username=FileReceiverUser", ""),response);
    }

    @Test
    @DisplayName("BadWeather - File transfer request with incorrect hash format")
    public void sendFileTransferRequestIncorrectHash() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileSenderUser", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileReceiverUser", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/username=FileReceiverUser/filename=test.txt/checksum=1f2ebc/filelength=123", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_MALFORMED_PACKET.code), "Checksum is not in an MD5 format!"),response);
    }

    @Test
    @DisplayName("BadWeather - File transfer request with incorrect file length")
    public void sendFileTransferRequestIncorrectFileLength() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileSenderUser", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/username=FileReceiverUser/filename=test.txt/checksum=2187f15067488bff528612492a810c42/filelength=0x232FF", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_MALFORMED_PACKET.code), "Count not interpret file length as a number!"),response);
    }

    @Test
    @DisplayName("BadWeather - File transfer request for not existing user")
    public void sendFileTransferRequestNonExUser() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileSenderUser", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/username=anonymus/filename=test.txt/checksum=2187f15067488bff528612492a810c42/filelength=123", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_NOT_FOUND.code), "Transfer target user not found!"),response);
    }

    @Test
    @DisplayName("GoodWeather - Reject file transfer request")
    public void rejectFileTransferRequest() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileSenderUser", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileReceiverUser", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/username=FileReceiverUser/filename=test.txt/checksum=2187f15067488bff528612492a810c42/filelength=123", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_SEND_FILE.code), "test.txt"),response);

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/filename=test.txt/sender=FileSenderUser/filelength=123/checksum=2187f15067488bff528612492a810c42/username=FileReceiverUser", ""),response);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_RECEIVE_FILE.code + "/username=FileSenderUser/filename=test.txt/accepted=false", ""));
        messageSender2.flush();

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_RECEIVE_FILE.code), "test.txt"),response);

        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.REQUEST_RECEIVE_FILE.code + "/filename=test.txt/sender=FileReceiverUser/accepted=false/username=FileSenderUser", ""),response);
    }

    @Test
    @DisplayName("BadWeather - Reject file transfer request on non existing username")
    public void rejectFileTransferRequestNonExUsername() throws IOException {
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileSenderUser", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=FileReceiverUser", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/username=FileReceiverUser/filename=test.txt/checksum=2187f15067488bff528612492a810c42/filelength=123", ""));
        messageSender.flush();

        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_SEND_FILE.code), "test.txt"),response);

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/filename=test.txt/sender=FileSenderUser/filelength=123/checksum=2187f15067488bff528612492a810c42/username=FileReceiverUser", ""),response);

        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_RECEIVE_FILE.code + "/username=anonymous/filename=test.txt/accepted=false", ""));
        messageSender2.flush();

        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_NOT_FOUND.code), "Transfer source user not found!"),response);
    }

    @Test
    @DisplayName("GoodWeather - File transfer")
    public void transferFile() throws IOException {
        String file = "[This is just an example file. It is basically just a sample txt file.]";
        String hash = "e0335f76114c705a42cc5b0fc579e7aa";

        //Login users
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob", ""));
        messageSender.flush();
        Helper.skipMessage(reader);
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Jack", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        //Request file transfer
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/username=Jack/filename=test.txt/checksum="+hash+"/filelength="+file.length(), ""));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_SEND_FILE.code), "test.txt"),response);
        String response2 = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/filename=test.txt/sender=Bob/filelength="+file.length()+"/checksum="+hash+"/username=Jack", ""),response2);

        //Accept file
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_RECEIVE_FILE.code + "/username=Bob/filename=test.txt/accepted=true", ""));
        messageSender2.flush();
        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_RECEIVE_FILE.code), "test.txt"),response);
        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.REQUEST_RECEIVE_FILE.code + "/filename=test.txt/sender=Jack/accepted=true/username=Bob", ""),response);

        //Open file sockets
        NetSocket bobFileSocket = new NetSocket("localhost",7331);
        response = bobFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.MESSAGE_SERVER_INFO.code), "Connected to \"Latte\" file transfer port"),response);
        bobFileSocket.sendMessage(Helper.buildProtocolString(ApiProtocol.FILE_AUTHENTICATION.code + "/current=Bob/remote=Jack",""));
        response = bobFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.FILE_AWAIT_PARTNER.code), ""),response);
        NetSocket jackFileSocket = new NetSocket("localhost",7331);
        response = jackFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.MESSAGE_SERVER_INFO.code), "Connected to \"Latte\" file transfer port"),response);
        jackFileSocket.sendMessage(Helper.buildProtocolString(ApiProtocol.FILE_AUTHENTICATION.code + "/current=Jack/remote=Bob",""));

        //Wait for partners to be ready
        response = jackFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.FILE_AWAIT_PARTNER.code), ""),response);
        response = bobFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.FILE_TRANSFER_READY.code), ""),response);
        response = jackFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.FILE_TRANSFER_READY.code), ""),response);

        //Transfer file
        InputStream targetStream = new ByteArrayInputStream(file.getBytes());
        bobFileSocket.sendBytes(targetStream);
        OutputStream outputStream = new ByteArrayOutputStream();
        jackFileSocket.receiveBytes(outputStream,file.length());
        String receivedFile = outputStream.toString();
        Assertions.assertEquals(receivedFile,file);
    }

    @Test
    @DisplayName("BadWeather - File transfer times out")
    public void transferFileTimesOut() throws IOException {
        String file = "[This is just an example file. It is basically just a sample txt file.]";
        String hash = "e0335f76114c705a42cc5b0fc579e7aa";

        //Login users
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob", ""));
        messageSender.flush();
        Helper.skipMessage(reader);
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Jack", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        //Request file transfer
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/username=Jack/filename=test.txt/checksum="+hash+"/filelength="+file.length(), ""));
        messageSender.flush();
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_SEND_FILE.code), "test.txt"),response);
        String response2 = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.REQUEST_SEND_FILE.code + "/filename=test.txt/sender=Bob/filelength="+file.length()+"/checksum="+hash+"/username=Jack", ""),response2);

        //Accept file
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_RECEIVE_FILE.code + "/username=Bob/filename=test.txt/accepted=true", ""));
        messageSender2.flush();
        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_RECEIVE_FILE.code), "test.txt"),response);
        response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.REQUEST_RECEIVE_FILE.code + "/filename=test.txt/sender=Jack/accepted=true/username=Bob", ""),response);

        //Open file sockets
        NetSocket bobFileSocket = new NetSocket("localhost",7331);
        response = bobFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.MESSAGE_SERVER_INFO.code), "Connected to \"Latte\" file transfer port"),response);
        bobFileSocket.sendMessage(Helper.buildProtocolString(ApiProtocol.FILE_AUTHENTICATION.code + "/current=Bob/remote=Jack",""));
        response = bobFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.FILE_AWAIT_PARTNER.code), ""),response);
        NetSocket jackFileSocket = new NetSocket("localhost",7331);
        response = jackFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.MESSAGE_SERVER_INFO.code), "Connected to \"Latte\" file transfer port"),response);

        //Timeout
        response = bobFileSocket.getIncomingMessage();
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_TIMEOUT.code), "Transfer partner timed out!"),response);
    }

    @Test
    @DisplayName("GoodWeather - Submit valid public key to server")
    public void submitValidPubKey() throws IOException, NoSuchAlgorithmException {

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        //Generate public key
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        String publicKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());

        //Submission successful
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_SUBMIT_KEY.code),publicKey));
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_SUBMIT_KEY.code), publicKey),response);
    }

    @Test
    @DisplayName("BadWeather - Submit invalid public key to server")
    public void submitInvalidPubKey() throws IOException {

        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob", ""));
        messageSender.flush();
        Helper.skipMessage(reader);

        //Invalid data provided
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_SUBMIT_KEY.code),"SomethingWrong"));
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ERROR_INVALID_DATA_FORMAT.code),"Provided data is not a valid X.509 encoded RSA Public Key!"),response);
    }

    @Test
    @DisplayName("GoodWeather - Get public key of a user")
    public void getValidPublicKey() throws IOException, NoSuchAlgorithmException {
        //Log test users in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob", ""));
        messageSender.flush();
        Helper.skipMessage(reader);
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Jack", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        //Generate public key
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        String publicKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_SUBMIT_KEY.code),publicKey));
        String response = Helper.readServerMessage(reader);
        Assertions.assertEquals(Helper.buildProtocolString(String.valueOf(ApiProtocol.ACKNOWLEDGE_SUBMIT_KEY.code), publicKey),response);

        //Valid key retrieved
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_GET_KEY.code + "/username=Bob", ""));
        messageSender2.flush();
        response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.ACKNOWLEDGE_GET_KEY.code + "/username=Bob",publicKey),response);
    }

    @Test
    @DisplayName("BadWeather - Get public key of user who hasn't submitted one")
    public void getInvalidPubKey() throws IOException {
        //Log test users in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob", ""));
        messageSender.flush();
        Helper.skipMessage(reader);
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Jack", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        //No key retrieved
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_GET_KEY.code + "/username=Bob", ""));
        messageSender2.flush();
        String response = Helper.readServerMessage(reader2);
        Assertions.assertEquals(Helper.buildProtocolString(ApiProtocol.ACKNOWLEDGE_GET_KEY.code + "/username=Bob",""),response);
    }

    @Test
    @DisplayName("GoodWeather - Exchange AES key")
    public void sendValidAES() throws IOException {
        //Generate public RSA key by Bob
        KeyPair keyPair = CryptoTools.generateRSAKeyPair();
        String originalPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        //Generate AES key by Jack
        String originalAESKey = Base64.getEncoder().encodeToString(CryptoTools.generateAESKey().getEncoded());
        String originalIV = Base64.getEncoder().encodeToString(CryptoTools.generateIv().getIV());

        //Log test users in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob", ""));
        messageSender.flush();
        Helper.skipMessage(reader);
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Jack", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        //Upload Bob's RSA public key
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_SUBMIT_KEY.code),originalPublicKey));
        Helper.skipMessage(reader);

        //Key retrieved
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_GET_KEY.code + "/username=Bob", ""));
        messageSender2.flush();
        String response = Helper.readServerMessage(reader2);
        String gotPublicKey = response.substring(response.indexOf(HEADING_END)+1, response.indexOf(BLOCK_END));
        Assertions.assertEquals(originalPublicKey,gotPublicKey);

        //Send AES
        String encryptedAESKey = CryptoTools.encryptRSAString(gotPublicKey,originalAESKey);
        String encryptedIV = CryptoTools.encryptRSAString(gotPublicKey,originalIV);
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.ENCRYPTION_SET_KEY.code+"/username=Bob",encryptedAESKey+","+encryptedIV));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        //Get AES
        response = Helper.readServerMessage(reader);
        String[] aesPair = response.substring(response.indexOf(HEADING_END)+1, response.indexOf(BLOCK_END)).split(",");
        String gotAESKey = CryptoTools.decryptRSAString(privateKey,aesPair[0]);
        String gotIV = CryptoTools.decryptRSAString(privateKey,aesPair[1]);
        Assertions.assertEquals(originalAESKey,gotAESKey);
        Assertions.assertEquals(originalIV,gotIV);
    }

    @Test
    @DisplayName("GoodWeather - Send encrypted direct message")
    public void sendEncryptedDM() throws IOException {

        //Generate public RSA key by Bob
        KeyPair keyPair = CryptoTools.generateRSAKeyPair();
        String originalPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        //Generate AES key by Jack
        String originalAESKey = Base64.getEncoder().encodeToString(CryptoTools.generateAESKey().getEncoded());
        String originalIV = Base64.getEncoder().encodeToString(CryptoTools.generateIv().getIV());

        //Log test users in
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Bob", ""));
        messageSender.flush();
        Helper.skipMessage(reader);
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_LOGIN.code + "/username=Jack", ""));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        //Upload Bob's RSA public key
        messageSender.println(Helper.buildProtocolString(String.valueOf(ApiProtocol.REQUEST_SUBMIT_KEY.code),originalPublicKey));
        Helper.skipMessage(reader);

        //Key retrieved
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.REQUEST_GET_KEY.code + "/username=Bob", ""));
        messageSender2.flush();
        String response = Helper.readServerMessage(reader2);
        String gotPublicKey = response.substring(response.indexOf(HEADING_END)+1, response.indexOf(BLOCK_END));
        Assertions.assertEquals(originalPublicKey,gotPublicKey);

        //Send AES
        String encryptedAESKey = CryptoTools.encryptRSAString(gotPublicKey,originalAESKey);
        String encryptedIV = CryptoTools.encryptRSAString(gotPublicKey,originalIV);
        messageSender2.println(Helper.buildProtocolString(ApiProtocol.ENCRYPTION_SET_KEY.code+"/username=Bob",encryptedAESKey+","+encryptedIV));
        messageSender2.flush();
        Helper.skipMessage(reader2);

        //Get AES
        response = Helper.readServerMessage(reader);
        String[] aesPair = response.substring(response.indexOf(HEADING_END)+1, response.indexOf(BLOCK_END)).split(",");
        String gotAESKey = CryptoTools.decryptRSAString(privateKey,aesPair[0]);
        String gotIV = CryptoTools.decryptRSAString(privateKey,aesPair[1]);

        //Send encrypted message
        String message = "This is a test message to be encrypted.";
        messageSender.println(Helper.buildProtocolString(ApiProtocol.REQUEST_PRIVATE_MESSAGE.code+"/username=Jack/encrypted=true",CryptoTools.encryptAESString(gotAESKey,gotIV,message)));
        messageSender.flush();
        Helper.skipMessage(reader);

        //Get valid message
        response = Helper.readServerMessage(reader2);
        String gotMessage = response.substring(response.indexOf(HEADING_END)+1, response.indexOf(BLOCK_END));
        String decryptedMessage = CryptoTools.decryptAESString(gotAESKey,gotIV,gotMessage);
        Assertions.assertEquals(message,decryptedMessage);
    }
}