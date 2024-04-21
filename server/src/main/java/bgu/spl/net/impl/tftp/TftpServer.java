package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static bgu.spl.net.srv.Server.threadPerClient;

public class TftpServer {
    //TODO: Implement this
    public static LinkedBlockingQueue<String> onlineUsers = new LinkedBlockingQueue<>();
    public static ConcurrentHashMap<String, Integer> onlineUsersId = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<byte[], byte[]> filesHashMap = new ConcurrentHashMap<>();
    public static Connections<Byte> connections = new ConnectionsImpl<>();
    //
    public static String directory = "Files";
    public static String temp_directory = "temp_files";
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
