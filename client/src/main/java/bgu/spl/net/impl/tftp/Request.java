package bgu.spl.net.impl.tftp;

public class Request {
    public final Serializer.Opcodes opcode;
    public final String filename;

    public Request(Serializer.Opcodes opcode, String filename) {
        this.opcode = opcode;
        this.filename = filename;
    }
}
