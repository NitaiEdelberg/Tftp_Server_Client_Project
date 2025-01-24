package impl.tftp;

import api.MessageEncoderDecoder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class TftpClient {
    private static final AtomicBoolean isConnected = new AtomicBoolean(true);

    public static void main(String[] args) {
        Socket sock;
        BufferedInputStream in;
        BufferedOutputStream out;
        try {
            sock = new Socket(args[0], Integer.parseInt(args[1]));
            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        BlockingQueue<byte[]> writeQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Request> requestQueue = new LinkedBlockingQueue<>();
        TftpProtocol protocol = new TftpProtocol(requestQueue, writeQueue, isConnected);
        MessageEncoderDecoder<byte[]> encoderDecoder = new TftpEncoderDecoder();

        Thread userInputThread = new Thread(new UserInputThread(requestQueue, writeQueue, isConnected));
        Thread processingThread = new Thread(new ProcessingThread(in, protocol, encoderDecoder));

        userInputThread.start();
        processingThread.start();

        System.out.println("Connected to server!");

        while (isConnected.get()) {
            byte[] message;
            try {
                message = writeQueue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                continue;
            }

            if (message == null) {
                continue;
            }
            System.out.println("Sending: " + Arrays.toString(message));

            try {
                out.write(message);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        userInputThread.interrupt();
    }

}
