package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

public class TftpServer {
    //TODO: Implement this
    public static ConcurrentHashMap<Integer, byte[]> users = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<byte[], byte[]> filesHashMap = new ConcurrentHashMap<>();
    public static String directory = "Files";
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
