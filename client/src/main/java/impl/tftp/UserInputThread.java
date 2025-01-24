package impl.tftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;


public class UserInputThread implements Runnable {
    private BlockingQueue<Request> requestQueue;
    private BlockingQueue<byte[]> writeQueue;
    private final AtomicBoolean isConnected;


    public UserInputThread(BlockingQueue<Request> requestQueue, BlockingQueue<byte[]> writeQueue, AtomicBoolean isConnected) {
        this.writeQueue = writeQueue;
        this.requestQueue = requestQueue;
        this.isConnected = isConnected;
    }

    @Override
    public void run() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (isConnected.get()) {
            try {
                while (!reader.ready()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            } catch (IOException e) {
                return;
            }
            String userInput;
            try {
                userInput = reader.readLine();
            } catch (IOException e) {
                continue;
            }

            byte[] bytes;
            try {
                bytes = serialize(userInput);
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid command");
                continue;
            } catch (Exception e) {
                System.out.println(e.getMessage());
                continue;
            }

            writeQueue.add(bytes);
        }
    }

    private byte[] serialize(String request) throws Exception {
        TftpEncoderDecoder.Opcodes opcode;
        String[] tokenized = request.split(" ", 2);
        opcode = TftpEncoderDecoder.Opcodes.valueOf(tokenized[0]);

        List<byte[]> parts = new ArrayList<>();
        parts.add(encode(opcode));
        switch (opcode) {
            case RRQ:
                if (Files.exists(Paths.get(tokenized[1]))) {
                    throw new Exception("File already exists");
                }
                parts.add(encode(tokenized[1]));
                break;
            case WRQ:
                if (!Files.exists(Paths.get(tokenized[1]))) {
                    throw new Exception("File does not exists");
                }
                parts.add(encode(tokenized[1]));
                break;
            case LOGRQ:
            case DELRQ:
                parts.add(encode(tokenized[1]));
                break;
            case DIRQ:
            case DISC:
                // request has no other data
                break;
            default:
                throw new IllegalArgumentException("Invalid command");
        }
        // only add once we are sure that we parsed the request correctly
        if (opcode == TftpEncoderDecoder.Opcodes.RRQ || opcode == TftpEncoderDecoder.Opcodes.WRQ) {
            requestQueue.add(new Request(opcode, tokenized[1]));
        } else {
            requestQueue.add(new Request(opcode, null));
        }

        return flattenList(parts);
    }

    private static byte[] flattenList(List<byte[]> list) {
        int totalLength = 0;
        for (byte[] array : list) {
            totalLength += array.length;
        }

        byte[] flattenedArray = new byte[totalLength];
        int offset = 0;
        for (byte[] array : list) {
            System.arraycopy(array, 0, flattenedArray, offset, array.length);
            offset += array.length;
        }
        return flattenedArray;
    }

    private byte[] encode(TftpEncoderDecoder.Opcodes opcode) {
        return new byte[]{0, (byte) opcode.value};
    }

    private byte[] encode(String str) {
        byte[] bytes = new byte[str.length() + 1];
        System.arraycopy(str.getBytes(), 0, bytes, 0, str.getBytes(StandardCharsets.UTF_8).length);
        return bytes;
    }
}
