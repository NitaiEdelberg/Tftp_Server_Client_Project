package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private boolean shouldTeminate;
    private int connectionId;
    private Connections<byte[]> connections;
    private boolean loggedIn;
    private byte[] newFile;
    private final byte[] ACKsucsses = "0400".getBytes(StandardCharsets.UTF_8);

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        this.shouldTeminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.loggedIn = false;
        this.newFile = null;
        //throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    @Override
    public void process(byte[] message) {
        // TODO implement this
        if (TftpEncoderDecoder.opcase == 7) { // LOGRQ
            byte[] userName = Arrays.copyOfRange(message, 2, message.length - 1);
            int userId = System.identityHashCode(userName);
            if (TftpServer.users.containsKey(userId)) {
                // TODO: return error message #7
                String error = "0507Login username already connected";
                connections.send(connectionId, error.getBytes(StandardCharsets.UTF_8));
            } else {
                // TODO: login and return ack packet #0 (sucsses)
                TftpServer.users.put(userId, userName);
                connections.send(connectionId, ACKsucsses);
            }
        }
        if (loggedIn) {
            if (TftpEncoderDecoder.opcase == 8) {// DELRQ
                byte[] fileNameBytes = Arrays.copyOfRange(message, 2, message.length - 1);
                String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                Path path = Paths.get(TftpServer.directory, fileName);
                if (!Files.exists(path)) { //TftpServer.filesHashMap.remove(fileName) == null
                    // TODO: return error message #1
                    String error = "0501DELRQ of non-existing file";
                    connections.send(connectionId, error.getBytes(StandardCharsets.UTF_8));
                } else {
                    // TODO: delete file and return ack packet #0
                    try {
                        Files.delete(path);
                        connections.send(connectionId, ACKsucsses);
                    } catch (IOException e) {
                        // for tests
                        System.out.println("Unable to delete file");
                    }


                }
            } else if (TftpEncoderDecoder.opcase == 1) {// RRQ
                byte[] fileNameBytes = Arrays.copyOfRange(message, 2, message.length - 1);
                String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                Path path = Paths.get(TftpServer.directory, fileName);
                if (Files.exists(path)) { //TftpServer.filesHashMap.remove(fileName) == null
                    // TODO: return DATA packets (of 512 bytes) with file value
                    try {
                        byte[] fullFile = Files.readAllBytes(path);
                        byte[] opcode = "03".getBytes(StandardCharsets.UTF_8);
                        List<byte[]> chunks = splitByteArray(fullFile, 512);
                        short blockCounter = 0;
                        for (byte[] packet : chunks) {
                            blockCounter = (short) (blockCounter + 1);
                            ByteBuffer msg = ByteBuffer.allocate(packet.length + 6);
                            msg.put(opcode);
                            msg.put(shortToByteArray((short) packet.length));
                            msg.put(shortToByteArray(blockCounter));
                            msg.put(packet);
                            connections.send(connectionId, msg.array());
                        }
                    } catch (IOException e) {
                        String error = "0500Fail to download";
                        connections.send(connectionId, error.getBytes(StandardCharsets.UTF_8));
                    }


                }
            } else {
                // TODO: return error message #1
                String err = "0501RRQ of non-existing file";
                connections.send(connectionId, err.getBytes(StandardCharsets.UTF_8));
                // remember to give an exception to customer FIRST if he already has the file!!!!!
            }
        } else if (TftpEncoderDecoder.opcase == 2) {// WRQ
            byte[] fileName = Arrays.copyOfRange(message, 2, message.length - 1);
            if (TftpServer.filesHashMap.containsKey(fileName)) {
                // TODO: return error message #5
            } else {
                newFile = fileName;
                byte[] empty = new byte[0];
                TftpServer.filesHashMap.put(fileName, empty);
                // TODO: return ack packet #0
                // remember to give an exception to customer FIRST if he  dosn't already has the file!!!!!
            }
        } else if (TftpEncoderDecoder.opcase == 6) { // DIRQ
            if (TftpServer.filesHashMap.isEmpty()) {
                // TODO: return error message #0
            } else {
                // TODO: return all file names in DATA packets
            }
        } else if (TftpEncoderDecoder.opcase == 3) { // DATA
            byte[] raw = Arrays.copyOfRange(message, 6, message.length - 1);
            byte[] oldDATA = TftpServer.filesHashMap.get(newFile);
            int combinedLength = oldDATA.length + raw.length;
            byte[] combined = new byte[combinedLength];
            System.arraycopy(oldDATA, 0, combined, 0, oldDATA.length);
            System.arraycopy(raw, 0, combined, oldDATA.length, raw.length);
            if (TftpEncoderDecoder.packetSize == 512) {
                TftpServer.filesHashMap.replace(newFile, oldDATA, combined);
            } else {
                newFile = null;
                // TODO: save 'combined' to 'Files' folder
            }
            // TODO: return ack packet with DATA block #
        } else if (TftpEncoderDecoder.opcase == 4) //ACK
        {
            int blockNum = TftpEncoderDecoder.twoBytes2Int(message[2], message[3]);
            System.out.println("ACK" + blockNum);
        } else if (TftpEncoderDecoder.opcase == 9) { //BCAST
            Byte d = message[2];
            int action = d.intValue();
            String ret;
            if (action == 0) {
                ret = "del";
            } else {
                ret = "add";
            }
            byte[] fileName = Arrays.copyOfRange(message, 3, message.length - 2);
            String name = new String(fileName, StandardCharsets.UTF_8);
            System.out.println("BCAST" + ret + name);
        } else if (TftpEncoderDecoder.opcase == 5) { //ERROR
            byte[] errMsg = Arrays.copyOfRange(message, 4, message.length - 2);
            String err = new String(errMsg, StandardCharsets.UTF_8);
            System.out.println("ERROR" + TftpEncoderDecoder.twoBytes2Int(message[2], message[3]) + err);
        } else if (TftpEncoderDecoder.opcase == 10) { //Disc
            shouldTeminate = true;
//                if (shouldTerminate()){
//                    // TODO: terminate session and return ARK #0
//                } else {
//                    // TODO: if needed
//                }
        } else {
            // TODO: return error message #6 (not logged in)
        }

    }

    private static List<byte[]> splitByteArray(byte[] input, int paketSize) {
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;

        while (offset < input.length) {
            int length = Math.min(paketSize, input.length - offset);
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
        this.connections.disconnect(this.connectionId);
        TftpServer.users.remove(this.connectionId);
        return shouldTeminate;
        //throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }


}
