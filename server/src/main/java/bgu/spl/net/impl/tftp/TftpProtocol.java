package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.*;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private boolean shouldTeminate;
    private int connectionId;
    private Connections<byte[]> connections;
    public static BlockingQueue responseQ;
    private boolean loggedIn;
    private byte[] newFile;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        this.shouldTeminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.responseQ  = new LinkedBlockingQueue();
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
            } else {
                TftpServer.users.put(userId, userName);
                ConnectionHandler ch = new BlockingConnectionHandler(new Socket(),Bidi)
                connections.connect(userId, );
                loggedIn = true;
                // TODO: return ack packet #0 (sucsses)
            }
        }
        if (loggedIn) {
            if (TftpEncoderDecoder.opcase == 8) {// DELRQ
                byte[] fileName = Arrays.copyOfRange(message, 2, message.length - 1);
                if (TftpServer.files.remove(fileName) == null) {
                    // TODO: return error message #1
                } else {
                    // TODO: return ack packet #0
                }
            } else if (TftpEncoderDecoder.opcase == 1) {// RRQ
                byte[] fileName = Arrays.copyOfRange(message, 2, message.length - 1);
                if (TftpServer.files.containsKey(fileName)) {
                    // TODO: return DATA packets (of 512 bytes) with file value
                } else {
                    // TODO: return error message #1
                    // remember to give an exception to customer FIRST if he already has the file!!!!!
                }
            } else if (TftpEncoderDecoder.opcase == 2) {// WRQ
                byte[] fileName = Arrays.copyOfRange(message, 2, message.length - 1);
                if (TftpServer.files.containsKey(fileName)) {
                    // TODO: return error message #5
                } else {
                    newFile = fileName;
                    byte[] empty = new byte[0];
                    TftpServer.files.put(fileName,empty);
                    // TODO: return ack packet #0
                    // remember to give an exception to customer FIRST if he  dosn't already has the file!!!!!
                }
            } else if (TftpEncoderDecoder.opcase == 6) { // DIRQ
                if (TftpServer.files.isEmpty()) {
                    // TODO: return error message #0
                } else {
                    // TODO: return all file names in DATA packets
                }
            } else if (TftpEncoderDecoder.opcase == 3) { // DATA
                byte[] raw = Arrays.copyOfRange(message, 6, message.length - 1);
                byte[] oldDATA = TftpServer.files.get(newFile);
                int combinedLength = oldDATA.length + raw.length;
                byte[] combined = new byte[combinedLength];
                System.arraycopy(oldDATA,0,combined,0,oldDATA.length);
                System.arraycopy(raw,0,combined,oldDATA.length,raw.length);
                if (TftpEncoderDecoder.packetSize == 512){
                    TftpServer.files.replace(newFile,oldDATA,combined);
                } else {
                    newFile = null;
                    // TODO: save 'combined' to 'Files' folder
                }
                // TODO: return ack packet with DATA block #
            }
            else if(TftpEncoderDecoder.opcase == 4) //ACK
            {
                int blockNum = TftpEncoderDecoder.twoBytes2Int(message[2],message[3]);
                System.out.println("ACK" + blockNum);
            } else if (TftpEncoderDecoder.opcase == 9) { //BCAST
                Byte d = message[2];
                int action = d.intValue();
                String ret;
                if(action == 0) {ret = "del";}
                else {ret = "add";}
                byte[] fileName = Arrays.copyOfRange(message,3,message.length - 2);
                String name = new String(fileName, StandardCharsets.UTF_8);
                System.out.println("BCAST" + ret + name);
            }
            else if(TftpEncoderDecoder.opcase == 5) { //ERROR
                byte[] errMsg = Arrays.copyOfRange(message,4,message.length - 2);
                String err = new String(errMsg,StandardCharsets.UTF_8);
                System.out.println("ERROR" + TftpEncoderDecoder.twoBytes2Int(message[2],message[3]) + err);
            }
            else if(TftpEncoderDecoder.opcase == 10) { //Disc
                shouldTeminate = true;
//                if (shouldTerminate()){
//                    // TODO: terminate session and return ARK #0
//                } else {
//                    // TODO: if needed
//                }
            }

        } else {
            // TODO: return error message #6 (not logged in)
        }
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
