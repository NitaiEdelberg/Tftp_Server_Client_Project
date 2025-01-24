package impl.tftp;

import api.MessageEncoderDecoder;

import java.nio.ByteBuffer;
import java.util.*;


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    public static final int OPCODE_LENGTH = 2;
    public static final int DATA_HEADER_LENGTH = OPCODE_LENGTH + 2 + 2;

    public static short opcode = Opcodes.NO_CASE.value;
    public static short expectedPacketSize = -1;
    private final Byte ZERO = new Byte("0");
    private List<Byte> bytes = new ArrayList<>();

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        bytes.add(nextByte);
        if (bytes.size() == OPCODE_LENGTH) {
            opcode = findCase(0);
        }

        if (opcode == Opcodes.NO_CASE.value) {
            return null;
        }

        Opcodes opCase;
        try {
            opCase = Opcodes.values()[opcode];
        } catch (ArrayIndexOutOfBoundsException ignored) {
            System.out.println("opcode " + opcode + " not legal");
            return null;
        }

        switch (opCase) {
            case LOGRQ:
            case DELRQ:
            case RRQ:
            case WRQ:
                if (nextByte == 0) {
                    return convertToArrayAndClean();
                }
                break;
            case DIRQ:
            case DISC:
                return convertToArrayAndClean();
            case DATA:
                if (bytes.size() == OPCODE_LENGTH + 2) {
                    expectedPacketSize = (short) (DATA_HEADER_LENGTH + findCase(OPCODE_LENGTH));
                } else if (bytes.size() >= DATA_HEADER_LENGTH && bytes.size() == expectedPacketSize) {
                    return convertToArrayAndClean();
                }
                break;
            case ACK:
                if (bytes.size() == OPCODE_LENGTH + 2) {
                    return convertToArrayAndClean();
                }
                break;
            case BCAST:
                if (nextByte == ZERO && bytes.size() > OPCODE_LENGTH + 1) {
                    return convertToArrayAndClean();
                }
                break;
            case ERROR:
                if (nextByte == ZERO && bytes.size() > OPCODE_LENGTH + 2) {
                    return convertToArrayAndClean();
                }
                break;
            default:
                System.out.println("opcode " + opcode + " not legal");
                break;
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        System.out.println("Sending: " + Arrays.toString(message));
        return message;
    }

    private short findCase(int i) {
        if (bytes.size() < OPCODE_LENGTH && bytes.get(0) != 0) {
            return -1;
        } else {
            return bytesToShort(bytes.get(i), bytes.get(i + 1));
        }
    }

    public static short bytesToShort(byte byte1, byte byte2) {
        return (short) (((byte1 & 0xff) << 8) | (byte2 & 0xff));
    }

    public static byte[] shortToByteArray(short yourShort) {
        byte[] result = new byte[2];
        result[0] = (byte) (yourShort >> 8); // Shift right by 8 bits
        result[1] = (byte) yourShort; // No additional shift needed for the lower 8 bits
        return result;
    }

    private byte[] convertToArrayAndClean() {
        byte[] ret = new byte[bytes.size()];
        int i = 0;
        for (byte b : bytes) {
            ret[i] = b;
            i++;
        }
        bytes.clear();
        opcode = Opcodes.NO_CASE.value;
        return ret;
    }

    public enum Opcodes {
        NO_CASE((short) -1),
        RRQ((short) 1),
        WRQ((short) 2),
        DATA((short) 3),
        ACK((short) 4),
        ERROR((short) 5),
        DIRQ((short) 6),
        LOGRQ((short) 7),
        DELRQ((short) 8),
        BCAST((short) 9),
        DISC((short) 10);

        public final short value;

        Opcodes(short value) {
            this.value = value;
        }
    }

    public static byte[] createData(short blockNumber, byte[] data) {
        ByteBuffer msg = ByteBuffer.allocate(DATA_HEADER_LENGTH + data.length);
        msg.putShort(TftpEncoderDecoder.Opcodes.DATA.value);
        msg.putShort((short) data.length);
        msg.putShort(blockNumber);
        msg.put(data);
        return msg.array();
    }
}