package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder
    private final Byte ZERO = new Byte("0");
    private List <Byte> bytes = new ArrayList<>();
    private int opcase = -1;
    private int packetSize = 0;
    private boolean wasDeleted;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        // TODO: implement this
        if (bytes.size() < 2){
            bytes.add(nextByte);
            opcase = findCase(0);
        } else {// check if we need to check expected massege length
            if (opcase == 7 || opcase == 8 || opcase == 1 || opcase == 2) { // case
                if (nextByte == ZERO) {
                    bytes.add(nextByte);
                    return convertToArrayAndClean();
                }
                bytes.add(nextByte);
            } else if (opcase == 6) {
                return convertToArrayAndClean();
            } else if (opcase == 3) {
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
            } else if (opcase == 4) {
                if (bytes.size() < 4) {
                    bytes.add(nextByte);
                } else {
                    return convertToArrayAndClean();
                }
            } else if (opcase == 9) {
                if (nextByte == ZERO && bytes.size() != 2) {
                    bytes.add(nextByte);
                    return convertToArrayAndClean();
                } else {
                    bytes.add(nextByte);
                }
            } else if (opcase == 5) {
                if (nextByte == ZERO && bytes.size() > 3) {
                    bytes.add(nextByte);
                    return convertToArrayAndClean();
                } else {
                    bytes.add(nextByte);
                }
            }
            else if(opcase == 10) {
                return convertToArrayAndClean();
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
            return bytes.get(i).intValue() + bytes.get(i+1).intValue();
        }
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