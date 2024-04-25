package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static bgu.spl.net.impl.tftp.TftpEncoderDecoder.createData;
import static bgu.spl.net.impl.tftp.TftpEncoderDecoder.shortToByteArray;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    public static final int OPCODE_LENGTH = 2;
    public static final int NULL_ENDING = 1;
    public static final int DATA_HEADER_LENGTH = OPCODE_LENGTH + 2 + 2;
    public static final int MAX_DATA_LENGTH = 512;
    private final byte[] ACK_SUCCESS = {0, 4, 0, 0};

    private boolean shouldTerminate;
    private int connectionId;
    private String userName;
    private Connections<byte[]> connections;
    private boolean loggedIn;
    private LinkedList<byte[]> packets;
    private short blockNum;
    private File currentFile;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.loggedIn = false;
        this.packets = new LinkedList<>();
        this.blockNum = -1;
        this.currentFile = null;
    }

    void sendError(String error, int errorCode) {
        System.out.println("Error: [" + errorCode + "] " + error);

        byte[] errorBytes = new byte[OPCODE_LENGTH + 2 + error.getBytes(StandardCharsets.UTF_8).length + NULL_ENDING];
        errorBytes[0] = 0;
        errorBytes[1] = (byte) TftpEncoderDecoder.Opcodes.ERROR.value;
        errorBytes[2] = shortToByteArray((short) errorCode)[0];
        errorBytes[3] = shortToByteArray((short) errorCode)[1];
        System.arraycopy(error.getBytes(StandardCharsets.UTF_8), 0, errorBytes, OPCODE_LENGTH + 2,
                error.getBytes(StandardCharsets.UTF_8).length);
        connections.send(connectionId, errorBytes);

    }

    @Override
    public void process(byte[] message) {
        System.out.println(Arrays.toString(message));
        TftpEncoderDecoder.Opcodes opCode;
        int requestOpCode = TftpEncoderDecoder.bytesToShort(message[0], message[1]);

        try {
            opCode = TftpEncoderDecoder.Opcodes.values()[requestOpCode];
        } catch (IndexOutOfBoundsException ignore) {
            System.out.println("Unknown opcode: " + requestOpCode);
            return;
        }

        if (!loggedIn && opCode != TftpEncoderDecoder.Opcodes.LOGRQ) {
            sendError("User not logged in", 6);
            return;
        }

        switch (opCode) {
            case LOGRQ:
                handleLOGRQ(message);
                break;
            case DELRQ:
                handleDELRQ(message);
                break;
            case RRQ:
                handleRRQ(message);
                break;
            case WRQ:
                handleWRQ(message);
                break;
            case DIRQ:
                handleDIRQ();
                break;
            case DATA:
                handleDATA(message);
                break;
            case ACK:
                handleACK(message);
                break;
            case BCAST:
                handleBCAST(message);
                break;
            case ERROR:
                handleERROR(message);
                break;
            case DISC:
                handleDISC();
                break;
        }
    }

    private void handleDISC() {
        System.out.println(userName + " is disconnecting");
        connections.send(connectionId, ACK_SUCCESS);
        connections.disconnect(connectionId);
        TftpServer.onlineUsersId.remove(this.userName);
        shouldTerminate = true;
    }

    private static void handleERROR(byte[] message) {
        byte[] errMsg = Arrays.copyOfRange(message, OPCODE_LENGTH + 2, message.length - 2);
        String err = new String(errMsg, StandardCharsets.UTF_8);
        System.err.println("ERROR" + TftpEncoderDecoder.bytesToShort(message[2], message[3]) + err);
    }

    private static void handleBCAST(byte[] message) {
        int action = message[2];
        String name = new String(Arrays.copyOfRange(message, 3, message.length - 1), StandardCharsets.UTF_8);
        System.out.println("BCAST " + (action == 0 ? "del" : "add") + " " + name);
    }

    private void handleACK(byte[] message) {
        if (!packets.isEmpty()) {
            connections.send(connectionId, packets.removeFirst());
        }
        int blockNum = TftpEncoderDecoder.bytesToShort(message[2], message[3]);
        System.out.println("ACK " + blockNum);
    }

    private void handleDELRQ(byte[] message) {
        String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
        Path path = Paths.get(TftpServer.directory, fileName);
        if (!Files.exists(path)) {
            sendError("DELRQ of non-existing file", 1);
            return;
        }

        try {
            Files.delete(path);
            broadcast(fileName, true);
            connections.send(connectionId, ACK_SUCCESS);
        } catch (IOException ignored) {
            sendError("Failed to delete file", 0);
            System.err.println("Unable to delete file");
        }
    }

    private void handleLOGRQ(byte[] message) {
        if (loggedIn) {
            sendError("Already logged in", 7);
            return;
        }

        String userName = new String(message, OPCODE_LENGTH, message.length - OPCODE_LENGTH, StandardCharsets.UTF_8);
        if (TftpServer.onlineUsersId.containsKey(userName)) {
            sendError("Login username already connected", 7);
            return;
        }

        TftpServer.onlineUsersId.put(userName, connectionId);
        connections.send(connectionId, ACK_SUCCESS);
        loggedIn = true;
        this.userName = userName;
    }

    private void handleDATA(byte[] message) {
        int num = TftpEncoderDecoder.bytesToShort(message[4], message[5]);
        if (blockNum != num) {
            sendError("Wrong block number", 0);
        } else if (currentFile == null) {
            sendError("No files to upload", 0);
        } else {
            blockNum++;
            try {
                byte[] data = new byte[message.length - DATA_HEADER_LENGTH];
                System.arraycopy(message, DATA_HEADER_LENGTH, data, 0, data.length);
                FileOutputStream output = new FileOutputStream(currentFile, true);
                try {
                    output.write(data);
                } finally {
                    output.close();
                }
                ByteBuffer ackMessage = ByteBuffer.allocate(OPCODE_LENGTH + 2);
                ackMessage.putShort(TftpEncoderDecoder.Opcodes.ACK.value);
                ackMessage.putShort((short) (blockNum - 1));
                connections.send(connectionId, ackMessage.array());

                if (data.length != MAX_DATA_LENGTH) {
                    File temp = currentFile;
                    currentFile.renameTo(new File(TftpServer.directory + "\\" + currentFile.getName()));
                    broadcast(currentFile.getName(), false);
                    temp.delete();
                    currentFile = null;
                    blockNum = -1;
                }
            } catch (IOException e) {
                sendError("failed to parse data packet", 0);
                e.printStackTrace();
                System.err.println("failed to parse data packet");
            }
        }
    }

    private void handleDIRQ() {
        StringBuilder files = new StringBuilder();
        File directory = new File(TftpServer.directory);
        if (directory.exists() && directory.isDirectory()) {
            File[] fileList = directory.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    // Check if the file object is a file (not a directory)
                    if (file.isFile()) {
                        files.append(file.getName()).append('\0');
                    }
                }
            } else {
                System.out.println("The directory is empty.");
            }

            generatePackets(files.toString().getBytes());

            if (!packets.isEmpty()) {
                connections.send(connectionId, packets.removeFirst());
            }
        } else {
            sendError("Directory does not exist or is not a directory.", 0);
            System.err.println("Directory does not exist or is not a directory.");
        }
    }

    private void handleWRQ(byte[] message) {
        String fileName;
        Path path;
        fileName = new String(message, OPCODE_LENGTH, message.length - (OPCODE_LENGTH + NULL_ENDING), StandardCharsets.UTF_8);
        path = Paths.get(TftpServer.directory, fileName);
        if (Files.exists(path)) {
            sendError("File already exists", 5);
            currentFile = null;
        } else {
            Path tempPath = Paths.get(TftpServer.temp_directory, fileName);
            try {
                currentFile = Files.createFile(tempPath).toFile();
                blockNum = 1;
                connections.send(connectionId, ACK_SUCCESS);
            } catch (IOException e) {
                sendError(e.getMessage(), 0);
                System.err.println("Error: " + e.getMessage());
                currentFile = null;
            }
        }
    }

    private void handleRRQ(byte[] message) {
        String fileName;
        Path path;
        fileName = new String(message, OPCODE_LENGTH, message.length - (OPCODE_LENGTH + NULL_ENDING), StandardCharsets.UTF_8);
        path = Paths.get(TftpServer.directory, fileName);
        if (!Files.exists(path)) {
            sendError("RRQ of non-existing file", 1);
            return;
        }

        try {
            byte[] fullFile = Files.readAllBytes(path);

            generatePackets(fullFile);

            if (!packets.isEmpty()) {
                connections.send(connectionId, packets.removeFirst());
            }
            blockNum = 1;
        } catch (IOException e) {
            sendError("Fail to download", 0);
        }
    }

    private static List<byte[]> splitByteArray(byte[] input) {
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;

        while (offset < input.length) {
            int length = Math.min(MAX_DATA_LENGTH, input.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(input, offset, chunk, 0, length);
            chunks.add(chunk);
            offset += length;
        }
        return chunks;
    }

    private void broadcast(String filename, boolean isDeleted) {
        byte[] packet = new byte[filename.getBytes().length + 4];
        packet[0] = 0;
        packet[1] = 9;
        if (isDeleted) packet[2] = 0;
        else packet[2] = 1;
        System.arraycopy(filename.getBytes(), 0, packet, 3, filename.getBytes().length);
        for (String id : TftpServer.onlineUsersId.keySet()) {
            connections.send(TftpServer.onlineUsersId.get(id), packet);
        }
    }

    private void generatePackets(byte[] data) {
        List<byte[]> chunks = splitByteArray(data);
        int i;
        for(i = 0; i < chunks.size(); i++) {
            packets.addLast(createData((short) (i + 1), chunks.get(i)));
        }

        if (data.length % MAX_DATA_LENGTH == 0) {
            packets.addLast(createData((short) (i + 1), new byte[0]));
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public int getConnectionId() {
        return connectionId;
    }

}
