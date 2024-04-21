package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder
    private final Byte ZERO = new Byte("0");
    private List <Byte> bytes = new ArrayList<>();
    public static int opcase = -1;
    public static int packetSize = -1;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        System.out.println(nextByte);
        System.out.println(Arrays.toString(bytes.toArray()));
        // TODO: implement this
        bytes.add(nextByte);
        if (bytes.size() == 2){
            opcase = findCase(0);
        }
        if(opcase != -1){// check if we need to check expected massege length
            if (opcase == 7 || opcase == 8 || opcase == 1 || opcase == 2) { // LOGRQ || DELRQ || RRQ || WRQ
                if (nextByte == 0) {
                    return convertToArrayAndClean();
                }
            } else if (opcase == 6) { // DIRQ
                return convertToArrayAndClean();
            } else if (opcase == 3) { //DATA
                if (bytes.size() == 4) {
                    packetSize = findCase(2) + 6;
                }
                if (bytes.size() >= 6)
                    if (bytes.size() == packetSize) {
                        return convertToArrayAndClean();
                    }
            } else if (opcase == 4) {// ACK
                if (bytes.size() == 4) {
                    return convertToArrayAndClean();
                }
            } else if (opcase == 9) {// BCAST
                if (nextByte == ZERO && bytes.size() != 2) {
                    return convertToArrayAndClean();
                }
            } else if (opcase == 5) {// ERROR
                if (nextByte == ZERO && bytes.size() > 3) {
                    return convertToArrayAndClean();
                }
            }
            else if(opcase == 10) {// Disc
                return convertToArrayAndClean();
            } else {
                System.out.println("opcode " + opcase + " not legal"); // for testing
            }
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        //TODO: implement this
        System.out.println("Sending: " + Arrays.toString(message));
        return message;
//        int opcase1 = twoBytes2Int(message[0], message[1]);
//        if (opcase1 == 3 || opcase1 == 4 || opcase1 == 6 || opcase1 == 10) {
//            return message;
//        } else if (opcase1 != -1){
//            byte[] res = Arrays.copyOf(message, message.length + 1);
//            res[message.length] = ZERO;
//            return res;
//        }
//        return null;
    }

    private int findCase(int i){
        if (bytes.size() < 2 && bytes.get(0) != 0){
            return -1;
        } else {
            return twoBytes2Int(bytes.get(i),bytes.get(i+1));
        }
    }
    public static int twoBytes2Int (byte byte1,byte byte2) {
        return ((byte1 & 0xff) << 8) | (byte2 & 0xff);
    }

    private byte[] convertToArrayAndClean(){
        byte[] ret = new byte[bytes.size()];
        int i = 0;
        for (byte b : bytes) {
            ret[i] = b;
            i++;
        }
        bytes.clear();
        opcase = -1;
        return ret;
    }
}