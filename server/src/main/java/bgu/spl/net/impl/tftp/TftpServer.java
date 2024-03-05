package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

public class TftpServer {
    //TODO: Implement this
    public static ConcurrentHashMap<byte[], byte[]> users = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<byte[], byte[]> files = new ConcurrentHashMap<>();
    public static void main(String[] args) {
        if(args.length == 2) { //opcode short
            Server.threadPerClient(
                    Integer.parseInt(args[1]), //port
                    () -> new TftpProtocol(), //protocol factory
                    TftpEncoderDecoder()::new //message encoder decoder factory
            ).serve();
        }
    }
}
