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
        // TODO: implement this
        if (bytes.size() < 2){
            bytes.add(nextByte);
            opcase = findCase(0);
        } else {// check if we need to check expected massege length
            if (opcase == 7 || opcase == 8 || opcase == 1 || opcase == 2) { // LOGRQ || DELRQ || RRQ || WRQ
                if (nextByte == ZERO) {
                    bytes.add(nextByte);
                    return convertToArrayAndClean();
                }
                bytes.add(nextByte);
            } else if (opcase == 6) { // DIRQ
                return convertToArrayAndClean();
            } else if (opcase == 3) { //DATA
                if (bytes.size() < 4) {
                    bytes.add(nextByte);
                } else if (bytes.size() == 4) {
                    packetSize = findCase(2) + 6;
                    if (bytes.size() < packetSize) {
                        bytes.add(nextByte);
                    } else {
                        return convertToArrayAndClean();
                    }
                }
            } else if (opcase == 4) {// ACK
                if (bytes.size() < 4) {
                    bytes.add(nextByte);
                } else {
                    return convertToArrayAndClean();
                }
            } else if (opcase == 9) {// BCAST
                if (nextByte == ZERO && bytes.size() != 2) {
                    bytes.add(nextByte);
                    return convertToArrayAndClean();
                } else {
                    bytes.add(nextByte);
                }
            } else if (opcase == 5) {// ERROR
                if (nextByte == ZERO && bytes.size() > 3) {
                    bytes.add(nextByte);
                    return convertToArrayAndClean();
                } else {
                    bytes.add(nextByte);
                }
            }
            else if(opcase == 10) {// Disc
                return convertToArrayAndClean();
            } else {
                throw new IllegalArgumentException("opcode not legal"); // for testing
            }
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        //TODO: implement this
        opcase = findCase(0);
        if (opcase == 3 || opcase == 4 || opcase == 6 || opcase == 10) {
            return message;
        } else if (opcase != -1){
            byte[] res = Arrays.copyOf(message, message.length + 1);
            res[message.length] = ZERO;
            return res;
        }
        return null;
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
        for (int i = 0; i < ret.length; i++) {
            ret[i] = bytes.get(i);
        }
        bytes.clear();
        return ret;
    }
}