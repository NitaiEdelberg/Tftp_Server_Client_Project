package tftp;

import srv.Connections;
import srv.ConnectionsImpl;

import java.util.concurrent.ConcurrentHashMap;

import static srv.Server.threadPerClient;

public class TftpServer {
    public static ConcurrentHashMap<String, Integer> onlineUsersId = new ConcurrentHashMap<>();
    public static Connections<Byte> connections = new ConnectionsImpl<>();

    public static String directory = "Files";
    public static String temp_directory = "src/temp_files";
    public static void main(String[] args) {
        if(args.length == 1) { //opcode short
            threadPerClient(
                    Integer.parseInt(args[0]), //port
                    TftpProtocol::new, //protocol factory
                    TftpEncoderDecoder::new //message encoder decoder factory
            ).serve();
        }
    }
}
