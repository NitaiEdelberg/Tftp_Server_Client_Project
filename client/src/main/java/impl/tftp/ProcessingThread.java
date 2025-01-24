package impl.tftp;

import api.MessageEncoderDecoder;

import java.io.BufferedInputStream;
import java.io.IOException;

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
