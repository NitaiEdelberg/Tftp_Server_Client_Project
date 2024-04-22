package bgu.spl.net.impl.tftp;

public class Serializer {
    public enum Opcodes {
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

        private final short value;

        Opcodes(short value) {
            this.value = value;
        }

        public short getValue() {
            return value;
        }
    }

    public static byte[] shortToByteArray(short yourShort) {
        byte[] result = new byte[2];
        result[0] = (byte) (yourShort >> 8); // Shift right by 8 bits
        result[1] = (byte) yourShort; // No additional shift needed for the lower 8 bits
        return result;
    }
}
