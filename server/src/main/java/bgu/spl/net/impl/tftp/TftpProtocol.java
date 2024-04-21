package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private boolean shouldTerminate;
    private int connectionId;
    private Connections<byte[]> connections;
    private boolean loggedIn;
    private byte[] newFile;
    private final byte[] ACKSuccess = {0, 4, 0, 0};
    private String userName;
    private LinkedList<byte[]> packets;
    private int blockNum = -1;
    private File currentFile = null;
    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.loggedIn = false;
        this.newFile = null;
        packets = new LinkedList<>();
        //throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    void sendError(String error, int errorCode) {
        byte[] errorBytes = new byte[error.getBytes(StandardCharsets.UTF_8).length + 4];
        errorBytes[0] = 0;
        errorBytes[1] = 5;
        errorBytes[2] = shortToByteArray((short) errorCode)[0];
        errorBytes[3] = shortToByteArray((short) errorCode)[1];
        System.arraycopy(error.getBytes(StandardCharsets.UTF_8), 0, errorBytes, 4, error.getBytes(StandardCharsets.UTF_8).length);
        connections.send(connectionId, errorBytes);

    }

    @Override
    public void process(byte[] message) {
        // TODO implement this
        int opcase = TftpEncoderDecoder.twoBytes2Int(message[0], message[1]);
        if (opcase == 7) { // LOGRQ
            if (loggedIn) {
                sendError("Already logged in", 7);
                return;
            }
            String userName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
            if (TftpServer.onlineUsers.contains(userName)) {
                // TODO: return error message #7
                sendError("Login username already connected", 7);
            } else {
                // TODO: login and return ack packet #0 (sucsses)
                TftpServer.onlineUsers.add(userName);
                TftpServer.onlineUsersId.put(userName, connectionId);
                connections.send(connectionId, ACKSuccess);
                loggedIn = true;
                this.userName = userName;
            }
            return;
        }
        if (opcase == 10 && !loggedIn) {
            sendError("User not logged in", 0);
            return;
        }
        if (!loggedIn) {
            sendError("User not logged in", 6);
            return;
        }

        if(currentFile != null && blockNum != -1 && opcase != 3){
            File temp = currentFile;
            currentFile.renameTo(new File(TftpServer.directory + "\\" + currentFile.getName()));
            broadcast(currentFile.getName(), false);
            temp.delete();
            currentFile = null;
            blockNum = -1;
        }
        if (opcase == 8) {// DELRQ
            String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
            Path path = Paths.get(TftpServer.directory, fileName);
            if (!Files.exists(path)) { //TftpServer.filesHashMap.remove(fileName) == null
                sendError("DELRQ of non-existing file", 1);
            } else {
                try {
                    Files.delete(path);
                    broadcast(fileName, true);
                    connections.send(connectionId, ACKSuccess);
                } catch (IOException e) {
                    // for tests
                    System.out.println("Unable to delete file");
                }
            }
        } else if (opcase == 1) {// RRQ
            String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
            Path path = Paths.get(TftpServer.directory, fileName);
            if (Files.exists(path)) { //TftpServer.filesHashMap.remove(fileName) == null
                // TODO: return DATA packets (of 512 bytes) with file value
                try {
                    byte[] fullFile = Files.readAllBytes(path);
                    byte[] opcode = {0, 3};
                    List<byte[]> chunks = splitByteArray(fullFile);
                    short blockCounter = 1;
                    for (byte[] packet : chunks) {
                        ByteBuffer msg = ByteBuffer.allocate(packet.length + 6);
                        msg.put(opcode);
                        msg.put(shortToByteArray((short) packet.length));
                        msg.put(shortToByteArray(blockCounter));
                        msg.put(packet);
                        packets.addLast(msg.array());
                        blockCounter = (short) (blockCounter + 1);
                    }
                    if (packets.size() != 0) {
                        connections.send(connectionId, packets.removeFirst());
                    } else {
                        ByteBuffer msg = ByteBuffer.allocate(6);
                        msg.put(opcode);
                        msg.put(shortToByteArray((short) 0));
                        msg.put(shortToByteArray((short) 0));
                        connections.send(connectionId, msg.array());

                    }
                    blockNum = 1;
                } catch (IOException e) {
                    sendError("Fail to download", 0);
                }
            } else {
                // TODO: return error message #1
                sendError("RRQ of non-existing file", 1);
                // remember to give an exception to customer FIRST if he already has the file!!!!!
            }
        } else if (opcase == 2) {// WRQ
            String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
            Path path = Paths.get(TftpServer.directory, fileName);
            if (Files.exists(path)) { //TftpServer.filesHashMap.remove(fileName) == null
                sendError("File already exists", 5);
                currentFile = null;
            } else {
                Path path1 = Paths.get(TftpServer.temp_directory, fileName);
                try {
                    currentFile = Files.createFile(path1).toFile();
                    blockNum = 1;
                    connections.send(connectionId, ACKSuccess);
                } catch (IOException e) {
                    System.out.println("Error: " + e.getMessage());
                    sendError(e.getMessage(), 0);
                    currentFile = null;
                }
            }
        } else if (opcase == 6) { // DIRQ
            StringBuilder files = new StringBuilder();
            System.out.println("Working Directory = " + System.getProperty("user.dir"));

            File directory = new File(TftpServer.directory);
            if (directory.exists() && directory.isDirectory()) {
                File[] fileList = directory.listFiles();
                if (fileList != null && fileList.length > 0) {
                    for (File file : fileList) {
                        // Check if the file object is a file (not a directory)
                        if (file.isFile()) {
                            files.append(file.getName()).append("0");
                        }
                    }
                } else {
                    System.out.println("The directory is empty.");
                }
                byte[] DIRQBytes = new byte[files.toString().getBytes().length + 1 + 6];
                byte[] data = {0, 3};
                byte[] block = {0, 1};
                System.arraycopy(data, 0, DIRQBytes, 0, 2);
                System.arraycopy(shortToByteArray((short) files.toString().getBytes().length), 0, DIRQBytes, 2, 2);
                System.arraycopy(block, 0, DIRQBytes, 4, 2);
                System.arraycopy(files.toString().getBytes(), 0, DIRQBytes, 6, files.toString().getBytes().length);
                connections.send(connectionId, DIRQBytes);
            } else {
                System.out.println("Directory does not exist or is not a directory.");
            }
        } else if (opcase == 3) { // DATA
            int  num = TftpEncoderDecoder.twoBytes2Int(message[4], message[5]);
            if(blockNum != num) {
                sendError("Wrong block number", 0);
            } else if (currentFile == null) {
                sendError("No files to upload", 0);
            } else {
                blockNum++;
                try {
                    byte[] data = new byte[message.length-6];
                    System.arraycopy(message, 6, data, 0, data.length);
                    FileOutputStream output = new FileOutputStream(currentFile, true);
                    try {
                        output.write(data);
                    } finally {
                        output.close();
                    }
                    byte[] ack = {0, 4, shortToByteArray((short)(blockNum-1))[0], shortToByteArray((short)(blockNum-1))[1]};
                    connections.send(connectionId, ack);
                    if(data.length != 512){
                        File temp = currentFile;
                        currentFile.renameTo(new File(TftpServer.directory + "\\" + currentFile.getName()));
                        broadcast(currentFile.getName(), false);
                        temp.delete();
                        currentFile = null;
                        blockNum = -1;
                    }
                } catch (IOException e) {
                    System.out.println("Check here for error");
                }
            }
        } else if (opcase == 4) //ACK
        {
            //blockNum here
            if (!packets.isEmpty()) {
                connections.send(connectionId, packets.removeFirst());
            }
            int blockNum = TftpEncoderDecoder.twoBytes2Int(message[2], message[3]);
            System.out.println("ACK " + blockNum);
        } else if (opcase == 9) { //BCAST
            int action = message[2];
            String ret;
            if (action == 0) {
                ret = "del";
            } else {
                ret = "add";
            }
            byte[] fileName = Arrays.copyOfRange(message, 3, message.length - 2);
            String name = new String(fileName, StandardCharsets.UTF_8);
            System.out.println("BCAST" + ret + name);
        } else if (opcase == 5) { //ERROR
            byte[] errMsg = Arrays.copyOfRange(message, 4, message.length - 2);
            String err = new String(errMsg, StandardCharsets.UTF_8);
            System.out.println("ERROR" + TftpEncoderDecoder.twoBytes2Int(message[2], message[3]) + err);
        } else if (opcase == 10) { //Disc
            System.out.println("Closing");
            connections.send(connectionId, ACKSuccess);
            connections.disconnect(connectionId);
            TftpServer.onlineUsers.remove(userName);
            TftpServer.onlineUsersId.remove(userName);
            shouldTerminate = true;

        } else {
            System.out.println("Unknown opcode: " + opcase);
        }
    }

    private static List<byte[]> splitByteArray(byte[] input) {
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;

        while (offset < input.length) {
            int length = Math.min(512, input.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(input, offset, chunk, 0, length);
            chunks.add(chunk);
            offset += length;
        }
        return chunks;
    }

    public static byte[] shortToByteArray(short yourShort) {
        byte[] result = new byte[2];
        result[0] = (byte) (yourShort >> 8); // Shift right by 8 bits
        result[1] = (byte) yourShort; // No additional shift needed for the lower 8 bits
        return result;
    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        return shouldTerminate;
        //throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }
    private void broadcast(String filename, boolean isDeleted){
        byte[] packet = new byte[filename.getBytes().length + 4];
        packet[0] = 0;
        packet[1] = 9;
        if(isDeleted) packet[2] = 0;
        else packet[2] = 1;
        System.arraycopy(filename.getBytes(), 0, packet, 3, filename.getBytes().length);
        for(String id: TftpServer.onlineUsers){
            connections.send(TftpServer.onlineUsersId.get(id), packet);
        }
    }
}
