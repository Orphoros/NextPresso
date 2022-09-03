# NextPresso Protocol -- NPP/1.1

> Created by Orphoros and SarenDev, 2021-2022

## Abstract
The NextPresso Protocol (NPP) is a binary-based application-level protocol for sharing text, files, and other media via many systems simultaneously. The primary purpose of NPP is to share information between many clients using a server and to establish communication between a server and a client. The primary aim of the NPP is to be used in a chat system. This specification defines the protocol referred to as "NPP/1.1".

---

## _(1)_ Purpose
NPP is designed to be used in a chat system where users communicate via a server. 

Connected clients to a server can use NPP to send text messages or share files. For example, a client using NPP/1.1 may send text messages to a specific user of their choosing, to a group of users, or broadcast messages to users connected to the server. Additionally, a user can send files to a specific user of their choosing.

 With NPP, a server can also send messages (for instance, errors) to specific users or every connected user. This way, communication can be established between users and the server.

## _(2)_ Protocol structure
### _(2.1)_ Type
NPP/1.1 sends `String` encoded data over the network using a binary protocol. All protocol data, except for control bytes (see 2.2 - Layout), is sent as a String. Some non-String data needs to be converted to a String for transmission.

- To send boolean values, use `true` and `false`.
- To send numbers, send the ASCII numbers and not bytes.

For file data transmission, NPP/1.1 is used to initiate the transfer; however, the transmission of the file data itself is not encoded under the NPP/1.1 spec and is transferred in raw binary.

### _(2.2)_ Layout
NPP uses a header-body structure. A header and a body must be present. First, the entire NPP message must begin with the byte `0x01` (the ASCII character for 'start of heading'). The header is ended by the `0x1F` (unit separator) byte. Data between these two bytes are header data. The body begins after the unit separator byte. The body and thus the entire NPP message ends with the (end of transmission) byte `0x04`. A valid NPP message must begin with `0x01`. Then it must contain one unit separator byte to mark the end of the header and the beginning of the body. Lastly, the message must end with the `0x04` byte. Data between the start and end bytes is considered a single NPP/1.1 message; all data not surrounded by these bytes are ignored.

An example of this core structure would look like as follows:
```
0x01[header]0x1F[body]0x04
```

Throughout an NPP message, none of the following characters can be used inside the body and the header data:
- The ASCII 'start of heading' character (byte 0x01)
- The ASCII 'unit separator' character (byte 0x1F)
- The ASCII 'end of transmission' character (byte 0x04)
These characters are restricted because their bytes are used to mark the structure of an NPP message and can only be present in the order defined above.

Furthermore, none of the following characters can be used in custom defined header data (key-value pairs):
- The equal (`=`) character
- The forward slash (`/`) character

The header consists of key-value pairs (see next section) connected by an equal character, and multiple key-value pairs are separated by forward slashes. Hence, the "`=`" and the "`/`" characters are not allowed to be used in key and value names in the header! These characters may be used, however, in the body.

Spaces can be used freely throughout any part of an NPP message. However, NPP considers spaces as part of the String for the key or value when spaces are used! For example, writing "` test = hello `" will result in a key of "` test `" (with spaces before and after "test") and a value of "` hello `" (with spaces before and after "hello").

Every other character that is not mentioned above can be used throughout the header's key and value data and the body. NPP/1.1 thus also supports multiline messages in its body.

#### _(2.2.1)_ Header Layout
As defined in section 2.2, the header must be placed between the `0x01` and the `0x1F` byte. The header can be further subdivided into smaller portions, called sections. Sections can be separated by forward slashes (`/`).

An example of header sections would look like as follows:
```
0x01[section-1]/[section-2]/[section-2]/...0x1F
```

The header specified in NPP/1.1 has one mandatory section that must be present. The first section, under index 1, must contain the header code.

The table below describes the mandatory section. The indexes start from 1, where index 1 refers to the first section of the header.

| Section index | Definition  | Description              |
| ------------- | ----------- | ------------------------ |
| 1             | Header code | Defines the message type |

Every extra section after these is considered optional. 

A section in the header separated by a forward slash (`/`) is a key-value pair, except the first section contains the header code byte(s). So, first, the key must be defined, followed by an equal sign, and then the key's value.

Example:
```
sender=bob
```
A section must have exactly one equal sign, a unique, non-empty key that has not yet been defined in the header, and a value that is not empty.

An example for a header that contains multiple optional header key-value pairs is as follows:
```
0x01[HEADER-CODE]/name=Bob/age=30/isMale=true0x1F
```
Please note that every number that begins with `0x` is a hexadecimal value and should not be interpreted as a literal String! A forward slash is only required in the header if another section follows a section.

#### _(2.2.1)_ Body Layout
The body must be defined after the header is terminated by the `0x1F` byte. Then, the body is ended by the `0x04` byte. With this byte, the protocol message is also ended.

## _(2.3)_ Header codes
The header codes define the type of message in NPP. Header codes are represented by integer numbers and displayed as hexadecimal numbers. The standard header code set of NPP/1.1 is one byte long. NPP also supports extended bytes if the first byte cannot cover any new options, should the protocol need expansion.

The header code is a mandatory section in the header. The header code must be defined right after the `0x01` (starter) byte. Unlike the optional header sections, the first section that holds the header code is not a key-value pair section. Hence, the first section just holds byte(s) that identify the type of message transmitted.

The header code acts as a message type identifier. The two hexadecimal nibbles of the standard header code set (one byte long) are two separate identifiers. The first nibble identifies the primary message type, and the second nibble identifies the sub-category of the message (if applicable).

The table below sums up the valid first nubble hexadecimal numbers and their second nubble subset identifier if applicable.

#### _(2.3.1)_ First nibble

The 'socket' section of the table below describes which of the two sockets (message/file) should these messages be sent.

| Code (1st nibble) | Definition  | Second nibble        | Socket                 | Definition                                                                                                                                 |
| ----------------- | ----------- | -------------------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| 1                 | Acknowledge | `Command codes`      | Message socket         | A "confirmation" message as a response for requests                                                                                        |
| 2                 | Error       | `Error codes`        | Message socket         | Message to indicate failure                                                                                                                |
| 3                 | Message     | `Message codes`      | Message socket         | Message that holds information messages in the body that is sent explicitly by the server                                                  |
| 4                 | Request     | `Command codes`      | Message socket         | Request action for the other party (server) that must be confirmed by an acknowledge message if succeeded or by an error message if failed |
| 5                 | File        | `File codes`         | File socket            | A message that holds a file to send                                                                                                        |
| 6                 | Encryption  | `Encryption codes`   | Message socket         | A message for configuring encryption between two clients                                                                                   |
| E                 | Extended    | *Unused*, always `0` | Message or file socket | The following bytes after the first one will tell the message type                                                                         |
| F                 | Heartbeat   | `Heartbeat codes`    | Message socket         | Messages used to test if the other party is still connected and alive                                                                      |

Please note that the second nibble always depends on the first nibble. For example, if the second nibble is 1, that could mean a completely different type if the first nibble is a 1 or a 2! The second nibble row in the table above refers to nibble sets that can be used for the first nibble. See section 2.3.2 for more information!

***1* - Acknowledge nibble:**

If the first nibble of the header code is a `1`, that means the message is an acknowledge (confirmation) message to a request message. An acknowledge message must only be sent to respond to a request message to indicate success and provide any requested information.

***2* - Error nibble:**

Messages whose first nibble is a `2` are error messages. These messages indicate to the other party (most commonly the server to a client) that an error has occurred. The error message should be sent as a response to request messages if the request could not be processed (instead of sending an acknowledge message that indicates success). These messages indicate exceptions that the receiving party needs to handle. These errors may be errors that can be simply displayed to the users to notify them or handled internally. The second nibble tells the exact type of error. The second nibble for the first error nibble is later expanded under error codes at the second nibble definition.

***3* - Message nibble:**

The message nibble (`3`) marks a message directly displayed to the user. These messages hold messages from the server (such as welcome messages, status updates, or notifications) and messages from other users that the server directed to a client holds the chat message. These messages are only sent by the server. When clients want to send messages to another client, they request the server transfer their messages. If the server accepts the request, these chat messages will be sent to the target in this message type. Hence, to indicate the message's original sender, the header has to contain the sender's information.

The server also uses this message type to send information, such as status updates, to the connected clients. Whenever the server sends a message, the 2nd nibble will always be `0` to indicate that the message is from the server.

***4* - Request nibble:**

A message whose first nibble in the header code is a `4` marks a request message. These messages are used to request another party for action. Requests can be sent by both a client and the server. Once a party receives a request message, they should respond by either an acknowledge message (to complete the request or confirm that their request is received) or an error message (to tell the sender that the request could not be processed). Next, the request sender should wait for a response. If no response is given in seconds, the request is timed out.

***5* - File nibble:**

NPP messages with a `5` as the first nibble in their header code should only be sent in a file socket! These NPP messages are used explicitly to establish a new file socket connection from both the file sender and the file receiver client to the server.

***6* - Encryption nibble:**

Messages marked with the first nibble of `6` are treated as messages to configure client-to-client encryption. The server does not act on the request messages and is directly forwarded to their intended recipient. Each request message has a response message that the server can send to the request sender when successfully forwarded.

***E* - Expansion nibble:**

The `E` hex value for the first nibble is reserved for the extended header codes. 0xE is chosen since `F` is already used for heartbeats and `E` stands for Extended. If the first nibble is `E`, then the second nibble is always a 0 as it is unused. This arrangement is required because the following byte(s) after the first byte will identify the message type and the sub-category if applicable. 

*NOTE:* The expansion nibble is currently unused in NPP/1.1. It is reserved for future use.

***F* - Heartbeat nibble:**

The `F` hex first nibble identifier is reserved for the heartbeat operation. This transmission type exists because a connection that uses NPP/1.1 must always use a heartbeat operation to check if the connection is alive and should be kept alive on both sides. Since the heartbeat must always be present, the very last possible hex value, F, for the first nibble is assigned to the heartbeat. The second nibble that can follow this first nibble is fixed.

**Important note:**

The first nibble can never be 0. This restriction exists because if the protocol header code needs expansion and multiple new bytes are required, the first nibble can never be nullable to make sure that, for example, 1 is always acknowledged. Thus, 0x0001, where the first byte is nullified, is translated back to 1 (acknowledge), which should not happen as only the first byte can contain the message type. Likewise, the second nibble can only be a message identifier if the first nibble is set to 0xE0 to mark the purpose of the second nibble.

#### _(2.3.2)_ Second nibble

The second nibble in the first byte (in the standard header code set) always depends on the first nibble, as the second nibble only makes sense with its first nibble. Thus, each first nibble has its own second nibble set. However, several first nibble types may reuse the same second nibble set in some cases. Because of this, the possible second nibbles are grouped into sets that the first nibbles can use. What second nibble set in combination with a first nibble can be used is defined in the table in the previous chapter (2.3.1 - First nibble). The message might expect a particular body and header data based on the second nibble. 

If a value is marked with an asterisk (`*`) in the table, the value is mandatory. Every field under the "Header section keys" row describes what key(s) must be present in the header where values must be added. For example, if the header section keys row defines a key such as "*name" that means, in the header, the `name=Bob` section must be present, where `Bob` is the value that is pared to the key "name" (of course, the value "Bob" is user-defined).

---

**Command codes**

---*When the first nibble is a 4 (For requests)*---

| Code (2nd nibble) | Definition          | Header section keys                                 | Body       | *Notes*                                                                                                                                                                                                                                                                                  |
| ----------------- | ------------------- | --------------------------------------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1                 | Log in              | *username, password                                 | -          | *The header needs the user's credentials to log in. Passwords are optional for credential authentication.*                                                                                                                                                                               |
| 2                 | Log out             | -                                                   | -          | -                                                                                                                                                                                                                                                                                        |
| 3                 | Broadcast           | -                                                   | *message   | -                                                                                                                                                                                                                                                                                        |
| 4                 | List users          | -                                                   | -          | -                                                                                                                                                                                                                                                                                        |
| 5                 | List groups         | -                                                   | -          | -                                                                                                                                                                                                                                                                                        |
| 6                 | Join group          | *groupname                                          | -          | *Existing group to join is defined in the header*                                                                                                                                                                                                                                        |
| 7                 | Create group        | *groupname                                          | -          | *Group-name to create is defined in the header*                                                                                                                                                                                                                                          |
| 8                 | Leave group         | *groupname                                          | -          | *Existing group to leave is defined in the header*                                                                                                                                                                                                                                       |
| 9                 | Private message     | *username, encrypted                                | *message   | *Username in the header is the target user. Encrypted is `true` if the message is encrypted or `false` if it is not; this header is optional, and if missing, is equivalent to a `false` value*                                                                                          |
| A                 | Group message       | *groupname                                          | *message   | *Username in the header is the target user*                                                                                                                                                                                                                                              |
| B                 | Send file           | *username, *filename, *checksum, *filelengh, sender | -          | *The username is the target, the filename is the name of the file to send, including extension (`test.txt`), the checksum holds the file's MD5 hash, and filelength holds the number of bytes in the file to send. The sender is only defined by the server when forwarding the request* |
| C                 | Receive file        | *username, *filename, *accepted, sender             | -          | *Username is the file sender, the filename is the name of the file to accept, accepted is either "`true`" or "`false"`. By sending false, the file is denied. The sender is only defined by the server when forwarding the request*                                                      |
| D                 | Submit public key   | -                                                   | *publicKey | *PublicKey must contain an RSA Public key that the current user wants to use to establish encrypted connection*                                                                                                                                                                          |
| E                 | Retrieve public key | *username                                           | -          | *Username must contain the name of the user whose RSA Public key the sender wants to retrieve*                                                                                                                                                                                           |

---*When the first nibble is a 1 (For acknowledges)*---
| Code (2nd nibble) | Definition          | Header section keys | Body                 | *Notes*                                                                                                                                                                                                                                                                    |
| ----------------- | ------------------- | ------------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1                 | Log in              | *authenticated      | *username            | *The body must contain the logged-in username. Authenticated is 'true' if the user is logged in with a password. Otherwise, it is 'false'*                                                                                                                                 |
| 2                 | Log out             | -                   | *username            | *The body must contain the logged-out username.*                                                                                                                                                                                                                           |
| 3                 | Broadcast           | -                   | *message             | *The body must contain the requested broadcast message.*                                                                                                                                                                                                                   |
| 4                 | List users          | -                   | *list of users       | *The body contains entries of `{}` separated by a comma (`,`). Each curly bracket pair must first contain the username, a comma, a `0` to mark the user is not authenticated, or a `1` to mark that they are. Example: `{bob,1},{jack,0}`*                                 |
| 5                 | List groups         | -                   | *list of group names | *The body contains entries of `{}` separated by a comma (`,`). Each curly bracket pair must contain first the group name, then a comma, and finally either a `0` to mark the current user is not in the group or a `1` to mark that they are. Example: `{uni,0},{home,1}`* |
| 6                 | Join group          | -                   | *groupname           | *Successfully joined group name in the body*                                                                                                                                                                                                                               |
| 7                 | Create group        | -                   | *groupname           | *Successfully created group name in the body*                                                                                                                                                                                                                              |
| 8                 | Leave group         | -                   | *groupname           | *Successfully left group name in the body*                                                                                                                                                                                                                                 |
| 9                 | Private message     | -                   | *message             | *Successfully sent message in the body.*                                                                                                                                                                                                                                   |
| A                 | Group message       | -                   | *message             | *Successfully sent message in the body.*                                                                                                                                                                                                                                   |
| B                 | Send file           | -                   | *filename            | *The filename is the name of the file including extension (`test.txt`) that is acknowledged*                                                                                                                                                                               |
| C                 | Receive file        | -                   | *filename            | *The filename is the name of the file including extension (`test.txt`) that is acknowledged*                                                                                                                                                                               |
| D                 | Submit public key   | -                   | *publicKey           | *PublicKey must contain an RSA Public key that the sender wants to use to establish an encrypted connection*                                                                                                                                                               |
| E                 | Retrieve public key | *username           | *publicKey           | *Username must contain the user's name whose RSA Public key the sender wants to retrieve. PublicKey must contain an RSA Public key of the requested user*                                                                                                                  |

---

**Message codes**

---*When sending a message from the server to a client (For messages)*--

| Code (2nd nibble) | Definition                    | Header section keys                   | Body                          | *Notes*                                                                                                                                                                                                                                                                  |
| ----------------- | ----------------------------- | ------------------------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 0                 | Server generic info message   | -                                     | *information                  | *The body needs to contain the information that the server wants to share.*                                                                                                                                                                                              |
| 1                 | Server group new-user message | *username, *authenticated, *groupname | -                             | *The header records must contain the newly joined user's username, and whether they are authenticated (true) or not (false) in the authenticated record alongside with the groupname which the new user joined.*                                                         |
| 2                 | Client message                | *sender, *authenticated, *encrypted   | *Chat message from the client | *Sender holds the username of the message requester. Authenticated is `true` if the user has logged in with a password; else, it is always `false`. Encrypted is `true` if the message is encrypted or `false` if it is not. The body holds the requested chat message.* |

The `0` for the second nibble marks messages as generic server messages. It can be used, for example, for broadcasting server updates or welcoming newly joined users.

The `1` for the second nibble marks the messages as a message that should only be sent by the server for the members of a specific group to inform them that a new user has joined.

The `2` for the second nibble marks the message as a chat message from another user.

---

**Error codes**

---*When the first nibble is a 2 (For errors)*---

| Code (2nd nibble) | Definition                | Header section keys | Body                      | Use-case                                                                                                                                                                   |
| ----------------- | ------------------------- | ------------------- | ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F                 | Malformed packet          | -                   | Descriptive error message | The packet has an invalid format. The structure of the header or the body is incorrect. Also, the error could mean that the header or the body contains invalid characters |
| 1                 | user is already logged in | -                   | Descriptive error message | Username to log in is already logged in                                                                                                                                    |
| 2                 | Invalid data format       | -                   | Descriptive error message | The data given in the header as a value or in the body has an incorrect format                                                                                             |
| 3                 | Not logged in             | -                   | Descriptive error message | One party tries to communicate with the other party when not logged in with a username                                                                                     |
| 4                 | Not found                 | -                   | Descriptive error message | Resource target, such as a targeted group or username, is not found either because it does not exist or because it is out of reach                                         |
| 5                 | Mandatory data not found  | -                   | Descriptive error message | A header code defines that the message needs mandatory header data or body data but is not found                                                                           |
| 6                 | Internal error            | -                   | Descriptive error message | An error happened during the runtime of the software that is related to internal operations                                                                                |
| 7                 | Unauthorized              | -                   | Descriptive error message | The username or the password does not match or does not exist in the registry                                                                                              |
| 8                 | Unexpected                | -                   | Descriptive error message | Received an unexpected message. This error is transmitted if the server cannot process a message (more details below)                                                      |
| 9                 | Not allowed               | -                   | Descriptive error message | An action was requested that cannot be completed                                                                                                                           |
| A                 | Timeout                   | -                   | Descriptive error message | An action did not occur, or a condition was not met in a certain amount of time                                                                                            |

A malformed packet (`F`) error message can be used to indicate that there is an error with the structure of the message. In the NPP/1.1, this is a generic error that indicates that something is wrong with the message. For example, this error can be a structural problem, an invalid character in a key, an unknown header code, or simply an unintelligible message that cannot be interpreted.

Error `8` and `F` seem similar, but these two errors must be sent in different scenarios. Error `F` implies that the message parser could not process the message data, or invalid characters were found that 'corrupt' the structure of the message. However, error `8` applies that the message has a correct format and does not contain corrupted data, but the receiving party cannot process the message correctly. For example, the receiving end received a header code that is not recognized.

---

**Heartbeat codes**

---*When the first nibble is an F (For heartbeats)*---

| Code (2nd nibble) | Definition           | Header section keys | Body |
| ----------------- | -------------------- | ------------------- | ---- |
| 1                 | Request by server    | -                   | -    |
| 2                 | Response from client | -                   | -    |

Heartbeat codes are used to keep the connection alive and signal that both ends are still alive and active. A heartbeat request is always sent by the server. The client then must send a heartbeat response to the server before the server will time out the client and close the connection. A client may not send heartbeat requests to a server.

---

**File codes**

---*When the first nibble is a 5 (For file transfer)*--

| Code (2nd nibble) | Definition              | Header section keys | Body | *Notes*                                                                                                                                                 |
| ----------------- | ----------------------- | ------------------- | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0                 | Authenticate connection | *current, *remote   | -    | *Sent by the client to the server. The header needs to contain the username of the current user (current) and the username of the remote user (remote)* |
| 1                 | Await partner           | -                   | -    | *Sent by the server to a client when only one transfer part established a socket*                                                                       |
| 2                 | Ready to transfer       | -                   | -    | *This message is sent by the server to both clients (receiver and sender) when both transfer parties established sockets with the file service*         |

---

**Encryption codes**

---*When the first nibble is a 6 (For encryption)*--

| Code (2nd nibble) | Definition             | Header section keys | Body                    | *Notes*                                                                                                                                                                                                                                                                                                                                                                      |
| ----------------- | ---------------------- | ------------------- | ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0                 | Set session code       | *username, sender   | *sessionKey, *sessionIV | *Sent by the client to the server. The header needs to contain the username of the target user, with whom the current user wants to establish an encrypted channel. The server fills in the sender. The body must contain an AES session key, followed by a comma (`,`), and the session key IV. All values must be encrypted with the target user's Public RSA Key.*        |
| 1                 | Session code forwarded | *username           | *sessionKey, *sessionIV | *Sent by the server to the sender client. The header needs to contain the username of the target user, with whom the current user wants to establish an encrypted channel. The server fills in the sender. The body must contain an AES session key, followed by a comma (`,`), and the session key IV. All values must be encrypted with the target user's Public RSA Key.* |

---

## _(3)_ Use-cases

With different header codes, messages can be sent for different purposes. This chapter will describe what message type (message header) to use in specific scenarios.

- **Acknowledge message**: These messages should be sent once a party receives a request message from another. An acknowledge message serves as a response message for a request message. Users and servers can potentially send these messages. An acknowledge message must only be sent as a response to a request (marking the request successful)
- **Error message:** An error message is used to inform the other parties if something goes wrong. These errors should be handled on the receiving side. These errors could be triggered by data that the other side sends. Error messages may be sent as a response to request messages to mark failure.
- **Message:** Information message sent by the server.
- **Request message:** A request message can be sent by either a connected client or a server. These messages are used to request something from another party. A request message should be answered by an acknowledge message or by an error message. If none of these happen in a given time, that means the request was lost.
- **Heartbeat message:** These messages are request-response messages. A server uses these messages to check if a client is still active. In most common cases, a server sends a heartbeat request message, and within *x* seconds, the client needs to respond, or the server will break the connection. 
- **File message:** These messages are sent in separate file sockets to establish a new file transfer connection between two clients through the server.

### _(3.1)_ Chain of messages

Whenever a client or a server receives a message, a particular message is expected back in specific scenarios, and in other cases, no message is expected back at all. This chapter will cover these scenarios.

#### _(3.1.1)_ Connecting

Whenever a client joins an NPP message server, the server should greet the user with a welcome message.

| Header Code | Header Records | Body                                                  |
| ----------- | -------------- | ----------------------------------------------------- |
| 0x30        | -              | Welcome to Latte, a NextPresso (NPP/1.1) chat server! |

The same applies when a client joins an NPP file server.

| Header Code | Header Records | Body                                    |
| ----------- | -------------- | --------------------------------------- |
| 0x30        | -              | Connected to "Latte" file transfer port |

#### _(3.2.1)_ Request-response

Most actions using NPP messages are request-response based.

A request message is used to request another party (usually a server) to act, such as to forward a message to a connected user. Then, an acknowledge message is used to confirm the success of the request. Finally, if the request fails, an error message with the cause of the problem is the response.

Whenever a client (most commonly a server) receives a `request message` that is sent by the other party, one of the following two messages should be sent as a response:

- Error message (if something failed or went wrong. Refer to the possible error messages on what error to send!)
- Acknowledge message (the second nibble remaining the same, but the first nibble is changed from the request nibble to acknowledge nibble) if the request was successfully processed

If a client wants to request another client for something and not the server (for instance, by file transfer), the server will forward the request message to the target client. However, the server must always send back an acknowledgment if the requested transfer was successful.

A response message should only be sent to reply to a request message! Suppose a party receives a response message but is not sending a request message to the sender. In that case, the receiver should respond by sending an error message with the header `0x28` (Unexpected message error).


#### _(3.2.1.1)_ Logging in

When a user tries to communicate with an NPP server to send messages to other connected clients, one must first log in. This action is required because when a client first connects to an NPP server, the client will be marked as a guest user. Therefore, the user cannot perform any action other than log in and respond to heartbeat requests to keep the connection alive.

A user can log in two ways:

- Log in with a username only (with a unique username that is not already logged in)
- Log in (authenticate) with a username and a password

Once a user is successfully logged in or authenticated, the user will no longer be marked as quest and will have access to NPP server features. Logging in with a password will mark the user as authenticated to other users.

If the user sends only a username to an NPP server, the server will only log in the user. If the user also provides the optional "password" header (with the raw password), the server will authenticate the user. If authentication fails, the server will send back an error message and not authenticate or log in the client with only the username as the operation is aborted.

The flow of logic for logging in:

1. Client sends:
  
| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0x41        | username=Emily | -    |

2. Server responds:

| Header Code | Header Records      | Body  |
| ----------- | ------------------- | ----- |
| 0x11        | authenticated=false | Emily |

The flow of logic for authenticating:

1. Client sends:

| Header Code | Header Records               | Body |
| ----------- | ---------------------------- | ---- |
| 0x41        | username=Emily/password=1234 | -    |

2. Server responds:

| Header Code | Header Records     | Body  |
| ----------- | ------------------ | ----- |
| 0x11        | authenticated=true | Emily |

The server may respond with the following errors header codes if the request fails:
`0x22, 0x28, 0x21, 0x25`

#### _(3.2.1.2)_ Sending a direct message

When a client wants to send a chat message to another client, the message should be sent by the following steps:

1. Client sends:
  
| Header Code | Header Records | Body             |
| ----------- | -------------- | ---------------- |
| 0x49        | username=Emily | message to Emily |

2. Server sends back to Emily:
  
| Header Code | Header Records | Body             |
| ----------- | -------------- | ---------------- |
| 0x19        | -              | message to Emily |

3. Server sends a direct message:
  
| Header Code | Header Records                   | Body             |
| ----------- | -------------------------------- | ---------------- |
| 0x32        | authenticated=false/sender=Emily | message to Emily |

The server may respond with the following errors header codes if the request fails:
`0x24, 0x25`

#### _(3.2.1.3)_ Sending a broadcast message

When a client wants to send a chat message to all clients, the message should be sent by the following steps:

1. Client sends:
  
| Header Code | Header Records | Body                |
| ----------- | -------------- | ------------------- |
| 0x43        | -              | message to everyone |

2. Server sends back to Emily:
  
| Header Code | Header Records                   | Body                |
| ----------- | -------------------------------- | ------------------- |
| 0x13        | authenticated=false/sender=Emily | message to everyone |

#### _(3.2.1.4)_ Request user listing

1. Client sends:
  
| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0x44        | -              | -    |

2. Server sends back:
  
| Header Code | Header Records | Body                       |
| ----------- | -------------- | -------------------------- |
| 0x14        | -              | {Bob,1},{Jack,0},{Alice,1} |

#### _(3.2.1.5)_ Request group listing

1. Client sends:
  
| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0x45        | -              | -    |

2. Server sends back:
  
| Header Code | Header Records | Body                                |
| ----------- | -------------- | ----------------------------------- |
| 0x15        | -              | {HomeGroup,0},{Study,1},{Friends,0} |


#### _(3.2.1.6)_ Create group

1. Client sends:
  
| Header Code | Header Records        | Body |
| ----------- | --------------------- | ---- |
| 0x47        | groupname=NameOfGroup | -    |

2. Server sends back:
  
| Header Code | Header Records | Body        |
| ----------- | -------------- | ----------- |
| 0x17        | -              | NameOfGroup |

The server may respond with the following errors header codes if the request fails:
`0x29, 0x25`

#### _(3.2.1.7)_ Join a group

1. Client sends:
  
| Header Code | Header Records        | Body |
| ----------- | --------------------- | ---- |
| 0x46        | groupname=NameOfGroup | -    |

2. Server sends back:
  
| Header Code | Header Records | Body        |
| ----------- | -------------- | ----------- |
| 0x16        | -              | NameOfGroup |

3. Server sends to all group members:
  
| Header Code | Header Records                                           | Body |
| ----------- | -------------------------------------------------------- | ---- |
| 0x31        | authenticated=false/groupname=NameOfGroup/username=Emily | -    |

The server may respond with the following errors header codes if the request fails:
`0x24, 0x25`

#### _(3.2.1.8)_ Send group message

1. Client sends:
  
| Header Code | Header Records        | Body          |
| ----------- | --------------------- | ------------- |
| 0x4A        | groupname=NameOfGroup | group message |

2. Server sends back:
  
| Header Code | Header Records | Body          |
| ----------- | -------------- | ------------- |
| 0x1A        | -              | group message |

3. Server sends to group members:
  
| Header Code | Header Records                                         | Body          |
| ----------- | ------------------------------------------------------ | ------------- |
| 0x32        | authenticated=false/sender=Emily/groupname=NameOfGroup | group message |

The server may respond with the following errors header codes if the request fails:
`0x25,0x24`

#### _(3.2.1.9)_ Leave group

1. Client sends:
  
| Header Code | Header Records        | Body |
| ----------- | --------------------- | ---- |
| 0x48        | groupname=NameOfGroup | -    |

2. Server sends back:
  
| Header Code | Header Records | Body        |
| ----------- | -------------- | ----------- |
| 0x18        | -              | NameOfGroup |

The server may respond with the following errors header codes if the request fails:
`0x25`

#### _(3.2.1.10)_ Logout

1. Client sends:
  
| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0x42        | -              | -    |

2. Server sends back:
  
| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0x12        | -              | -    |

#### _(3.2.1.11)_ Heartbeat

Once a client is connected to a server (or to any party), in every *x* seconds, the server should verify that the other side is still active. Therefore, software that uses NPP/1.1 is required to implement a heartbeat handler. Clients need to respond to heartbeat requests, and servers need to verify clients' status by heartbeat requests.

After connecting to a server, the server should wait some seconds first before initiating the heartbeat sequence. Once the sequence is begun, the following chain of the logic happens:

1. Server sends:
  
| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0xF1        | -              | -    |

2. Client sends back:
  
| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0xF2        | -              | -    |

If the client does not respond in *x* seconds, the server will shut down the connection.

#### _(3.1.5)_ Sending files

Sending a file from one user to another happens via a centralized server that runs NPP. Both clients (the one who wants to send a file and the client who should receive a file) need to be logged in to the server. Before a logged-in user can send a file to another user, the target user needs to accept (or deny) the file request.

The following logic is used in performing a transfer (in this example, the sender is "Sender" and the recipient is "Receiver")

1. Sender sends to server:
  
| Header Code | Header Records                                                          | Body |
| ----------- | ----------------------------------------------------------------------- | ---- |
| 0x4B        | username=Receiver/filename=test.txt/checksum=387a6b2c32/filelength=3827 | -    |

2. Server sends to Sender:

| Header Code | Header Records | Body     |
| ----------- | -------------- | -------- |
| 0x1B        | -              | test.txt |

3. Server sends to Receiver:

| Header Code | Header Records                                                                        | Body     |
| ----------- | ------------------------------------------------------------------------------------- | -------- |
| 0x4B        | filename=test.txt/sender=Sender/filelength=3827/checksum=387a6b2c32/username=Receiver | test.txt |

4. Receiver sends to Server:

| Header Code | Header Records                                  | Body     |
| ----------- | ----------------------------------------------- | -------- |
| 0x4C        | username=Sender/filename=test.txt/accepted=true | test.txt |

5. Server sends to Receiver:

| Header Code | Header Records | Body     |
| ----------- | -------------- | -------- |
| 0x1C        | -              | test.txt |

6. Server sends to Sender:

| Header Code | Header Records                                                  | Body |
| ----------- | --------------------------------------------------------------- | ---- |
| 0x4C        | filename=test.txt/sender=Receiver/accepted=true/username=Sender | -    |

7. Sender opens a new file socket

8. Sender sends a message to the server via File Socket

| Header Code | Header Records                 | Body |
| ----------- | ------------------------------ | ---- |
| 0x50        | current=Sender/remote=Receiver | -    |

9. Server sends to Sender via File Socket

| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0x51        | -              | -    |

10. Receiver opens a new file socket

11. Receiver sends a message to the server via File Socket

| Header Code | Header Records                 | Body |
| ----------- | ------------------------------ | ---- |
| 0x50        | current=Receiver/remote=Sender | -    |

12. Server sends to Receiver via File Socket

| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0x51        | -              | -    |

13. Server sends to Receiver and Sender via File Socket

| Header Code | Header Records | Body |
| ----------- | -------------- | ---- |
| 0x52        | -              | -    |

14. Sender sends the file
15. Receiver reads bytes until the end of the file length and performs a checksum compare

The server may respond with the following errors header codes if the request fails:
`0x25, 0x24, 0x28, 0x2F, 0x2A`
---


#### _(3.1.6)_ Encryption

NPP message encryption is implemented for client-to-client direct messages. The message text is encrypted with a symmetric AES key exchanged in an encrypted form using an asymmetric RSA key pair during the first time two clients want to use encrypted communication. While both parties generate and submit their own RSA key pairs, only the party that wants to initiate an encrypted communication generates the AES key. All encrypted data and keys are stored in the standard Bas64 character set. In this example, the communication initiator is called "Sender," and the other party is called "Receiver."

1. Client generates and submits a 1024 bit RSA public key:

| Header Code | Header Records | Body                                                                                                                                                                                                                     |
| ----------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 0x4D        | -              | MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDBAIMNvqKBVRgQoN8b+6tqhXQbsCwWld4FIWY6fQGfNuFBg6ufoBtbN/EhYUBlmutk+AIeHHSeCfc4UVJAKrJtzkwthEOvWIkevmZt5ypK75q7eha6hrWd59iKBq6le9Yfe9ybcMXGckVbkgNp/PiR63xYbjEZXgt2SyY2JiypdwIDAQAB |

2. Server acknowledges key submission:
  
| Header Code | Header Records | Body                                                                                                                                                                                                                     |
| ----------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 0x1D        | -              | MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDBAIMNvqKBVRgQoN8b+6tqhXQbsCwWld4FIWY6fQGfNuFBg6ufoBtbN/EhYUBlmutk+AIeHHSeCfc4UVJAKrJtzkwthEOvWIkevmZt5ypK75q7eha6hrWd59iKBq6le9Yfe9ybcMXGckVbkgNp/PiR63xYbjEZXgt2SyY2JiypdwIDAQAB |

3. "Sender" requests "Receiver" public key:

| Header Code | Header Records    | Body |
| ----------- | ----------------- | ---- |
| 0x4E        | username=Receiver | -    |

4. Server returns "Receiver" public key:

| Header Code | Header Records    | Body                                                                                                                                                                                                                     |
| ----------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 0x1E        | username=Receiver | MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDBAIMNvqKBVRgQoN8b+6tqhXQbsCwWld4FIWY6fQGfNuFBg6ufoBtbN/EhYUBlmutk+AIeHHSeCfc4UVJAKrJtzkwthEOvWIkevmZt5ypK75q7eha6hrWd59iKBq6le9Yfe9ybcMXGckVbkgNp/PiR63xYbjEZXgt2SyY2JiypdwIDAQAB |

5. "Sender" generates a 128 bit RSA key and IV and sends it to "Receiver":

| Header Code | Header Records    | Body                                              |
| ----------- | ----------------- | ------------------------------------------------- |
| 0x1E        | username=Receiver | z0qTCLEdm8M35AAoh73AVg==,lZNQBRglGSaw2v6+u0lfOg== |

6. Server confirms session code forward

| Header Code | Header Records    | Body                                              |
| ----------- | ----------------- | ------------------------------------------------- |
| 0x1E        | username=Receiver | z0qTCLEdm8M35AAoh73AVg==,lZNQBRglGSaw2v6+u0lfOg== |

7. "Receiver" stores the key.

8. Both clients can begin messaging with the specified key:

| Header Code | Header Records                   | Body                     |
| ----------- | -------------------------------- | ------------------------ |
| 0x49        | username=Receiver/encrypted=true | Kg5SnDUNAw6Kxch/c9xYxw== |

The server may respond with the following errors header codes if the request fails:
`0x25, 0x22, 0x24`

### _(3.2)_ Examples

In this section, specific messages are given in their raw text format, alongside a message breakdown table to explain the different parts of the message. Please note that String values that start with `0x` followed by numbers should be interpreted as hex integers rather than literal String values!

### 1 - Send a group message request to the server from a client computer when logged in with a username

**`0x014A/groupname=School0x1FTest Message0x04`**

| Raw byte 1           | Raw byte 2 - First nibble | Raw byte 2 - Second nibble | Target group | Raw byte 3        | Body         | Raw byte 4              |
| -------------------- | ------------------------- | -------------------------- | ------------ | ----------------- | ------------ | ----------------------- |
| Message start (0x01) | 4 (Acknowledge)           | A (Group message)          | School       | Header end (0x1F) | Test message | Transmission end (0x04) |