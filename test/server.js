// The PORT and SHOULD_PING parameters can be changed for testing purposes.
const PORT = 1337
const SHOULD_PING = true

const VERSION = '1.3'

let net = require('net')
let clients = []
const CMD_CONN = 'CONN'
const CMD_BCST = 'BCST'
const CMD_PONG = 'PONG'
const CMD_QUIT = 'QUIT'
const STAT_CONNECTED = 'CONNECTED'

let server = net.createServer(function(socket) {
    socket.setEncoding('utf8');
    clients.push(socket)
	sendToClient(socket, `INFO Welcome to the server ${VERSION}`)
    socket.on('data', function(data) {
        let client = this
        if(client.pending_bytes === undefined) client.pending_bytes = ""

        for (let i = 0; i < data.length; i++) {
            client.pending_bytes += data[i]
            if(data[i] == '\n'){
                processMessage(client, client.pending_bytes);
                client.pending_bytes = ""
            }
        }
    })
    socket.on('close', function() {
        console.log('Connection closed')
        clients = clients.filter(c => c !== this)
        stats()
    })
    socket.on('error', function(err) { 
        console.log("Socket error " + err)
    })
})

function processMessage(client, input){
    let command = parseCommand(input)

    console.log(`<< [${client.username}] ${command.type} ${command.payload}`)
    switch (client.status) {
        case undefined:
            // User needs to provide a valid username first.
            if (command.type !== CMD_CONN) {
                sendToClient(client, 'ER03 Please log in first')
            } else if (!validUsernameFormat(command.payload)) {
                sendToClient(client, 'ER02 Username has an invalid format (only characters, numbers and underscores are allowed)')
            } else if (userExists(command.payload)) {
                sendToClient(client, 'ER01 User already logged in')
            } else {
                // Everything works out.
                client.username = command.payload
                client.status = STAT_CONNECTED
                client.receivedPong = false;
                sendToClient(client, `OK ${command.payload}`)
                stats()
                if (SHOULD_PING) {
                    // Start heartbeat.
                    heartbeat(client)
                }
            }
            break;
        case STAT_CONNECTED:
            // User has provided a username.
            switch(command.type) {
                case CMD_QUIT:
                    sendToClient(client, 'OK Goodbye')
                    client.destroy()
                    break;
                case CMD_BCST:
                    let others = clients.filter(c => c.status === STAT_CONNECTED && c !== client)
                    for (const other of others) {
                        sendToClient(other, `${CMD_BCST} ${client.username} ${command.payload}`)
                    }
                    sendToClient(client, `OK ${CMD_BCST} ${command.payload}`)
                    break;
                case CMD_PONG:
                    client.receivedPong = true
                    break;
                default:
                    // Any other command we cannot process.
                    sendToClient(client, 'ER00 Unknown command')
                    break;
            }
            break;
    }
}

function parseCommand(command) {
    let clean = command.trim()
    let parts = clean.split(' ')
    return {
        type: parts[0],
        payload: clean.substr(parts[0].length + 1)
    }
}

function validUsernameFormat(username) {
    let pattern = '^[a-zA-Z0-9_]{3,14}$'
    return !!username.match(pattern)
}

function userExists(username) {
    let client = clients.find(c => c.username == username)
    return client !== undefined
}

function sendToClient(client, message) {
    if (clients.includes(client)) {
        console.log(`>> [${client.username}] ${message}`)
        client.write(`${message}\n`)
    } else {
        console.log(`Skipped send (${message}): client not active any more`)
    }
}

function heartbeat(client) {
    console.log(`~~ [${client.username}] Heartbeat initiated`)
    setTimeout(function () {
        client.receivedPong = false
        sendToClient(client, 'PING')
        setTimeout(function () {
            if (client.receivedPong) {
                console.log(`~~ [${client.username}] Heartbeat expired - SUCCESS`)
                heartbeat(client)
            } else {
                console.log(`~~ [${client.username}] Heartbeat expired - FAILED`)
                sendToClient(client, 'DCSN')
                client.destroy()
            }
        }, 3 * 1000)
    }, 10 * 1000)
}

function stats() {
    console.log(`Total number of clients: ${clients.length}`)
    let connected = clients.filter(c => c.status == STAT_CONNECTED) 
    console.log(`Number of connected clients: ${connected.length}`)

}

console.log(`Starting server version ${VERSION}.`)
console.log(`Press 'control-C' to quit the server.`)
server.listen(PORT, '127.0.0.1')
