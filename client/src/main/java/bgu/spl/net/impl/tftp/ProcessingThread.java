package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.ConnectionHandler;
import bgu.spl.net.api.MessageEncoderDecoder;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static bgu.spl.net.impl.tftp.TftpEncoderDecoder.bytesToShort;

public class ProcessingThread implements Runnable {
    private BufferedInputStream sockReader;
    private MessageEncoderDecoder<byte[]> encoderDecoder;
    private TftpProtocol protocol;

    public ProcessingThread(BufferedInputStream sockReader, TftpProtocol protocol, MessageEncoderDecoder<byte[]> messageEncoderDecoder) {
        this.sockReader = sockReader;
        this.encoderDecoder = messageEncoderDecoder;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        int read;
        try {
            while (!protocol.shouldTerminate() && (read = sockReader.read()) >= 0) {
                byte[] message = encoderDecoder.decodeNextByte((byte) read);
                if (message != null) {
                    protocol.process(message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            protocol.terminate();
        }
    }
}
