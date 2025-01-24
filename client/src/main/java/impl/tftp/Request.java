package impl.tftp;


public class Request {
    public final TftpEncoderDecoder.Opcodes opcode;
    public final String filename;

    public Request(TftpEncoderDecoder.Opcodes opcode, String filename) {
        this.opcode = opcode;
        this.filename = filename;
    }
}
