# Server_Project

## Overview
The Server_Project implements an extended version of the Trivial File Transfer Protocol (TFTP), facilitating multi-client interactions including file uploads, downloads, and system-wide file status notifications. This Java-based application supports multiple simultaneous client connections and incorporates advanced functionalities like file transfer, multi-casting, and command execution using a binary communication protocol.

## How to run
First, make sure that you have maven installed.
To start the server:
In the terminal run: mvn clean compile
Then add arguments 7777 : representing the port of the communication.
Then run the main function in the TftpServer class.

To start the client:
In the terminal run: mvn clean compile
Then add arguments: 127.0.0.0 7777 : representing ip and port
Then run the main function in the TftpClient class.

Now you have both the Server and the Client running and you can start sending requests.

## Key Components

### Server
The server component is built on a Thread-Per-Client (TPC) model, allowing efficient handling of multiple client communications concurrently. It integrates interfaces for managing connections and broadcasting messages, enabling interactions such as file uploads, downloads, and notifications of file status changes (additions or deletions).

### Client
Each client operates through two main threads: a keyboard thread for sending commands and a listening thread for handling server responses. The client supports various commands for interacting with the server, including logging in, file manipulation, and querying server directories.

### Commands
- **LOGRQ**: Logs a user into the server using a specified username.
- **DELRQ**: Requests deletion of a specified file from the server.
- **RRQ**: Downloads a file from the server to the client's working directory.
- **WRQ**: Uploads a file from the client's working directory to the server.
- **DIRQ**: Retrieves a list of all files currently stored on the server.
- **DISC**: Disconnects the client from the server and closes the application.

### Internal Procedures
These procedures are crucial for the server-client communication but are not triggered directly through client commands:
- **DATA**: Transmits a block of the file between the server and the client.
- **ACK**: Acknowledges the receipt of packets.
- **BCAST**: Broadcasts the addition or deletion of files to all connected clients.
- **ERROR**: Communicates error messages related to file transfers and command executions.


## Copyright Notice
© 2024 by Nitai Edelberg and Ido Toker. All rights reserved.

This project is a part of academic work at Ben Gurion University. Unauthorized use, copying, or distribution is prohibited.
