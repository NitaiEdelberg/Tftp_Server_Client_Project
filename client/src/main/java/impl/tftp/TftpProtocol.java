package impl.tftp;

import api.BidiMessagingProtocol;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static impl.tftp.TftpEncoderDecoder.createData;
import static impl.tftp.TftpEncoderDecoder.shortToByteArray;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    public static final int OPCODE_LENGTH = 2;
    public static final int DATA_HEADER_LENGTH = OPCODE_LENGTH + 2 + 2;
    public static final int MAX_DATA_LENGTH = 512;

    private BlockingQueue<Request> requestQueue;
    private BlockingQueue<byte[]> writeQueue;
    private AtomicBoolean isConnected;
    private LinkedList<byte[]> packets;
    private short blockNum;

    public TftpProtocol(BlockingQueue<Request> requestQueue, BlockingQueue<byte[]> writeQueue, AtomicBoolean isConnected) {
        this.requestQueue = requestQueue;
        this.writeQueue = writeQueue;
        this.isConnected = isConnected;

        this.packets = new LinkedList<>();
        this.blockNum = 0;
    }

    @Override
    public boolean shouldTerminate() {
        return !isConnected.get();
    }

    @Override
    public void process(byte[] message) {
        System.out.println(Arrays.toString(message));
        TftpEncoderDecoder.Opcodes opCode;
        int requestOpCode = TftpEncoderDecoder.bytesToShort(message[0], message[1]);

        try {
            opCode = TftpEncoderDecoder.Opcodes.values()[requestOpCode];
        } catch (IndexOutOfBoundsException ignore) {
            System.out.println("Unknown opcode: " + requestOpCode);
            return;
        }

        try {
            switch (opCode) {
                case ACK:
                    handleACK(message);
                    break;
                case DATA:
                    handleDATA(message);
                    break;
                case BCAST:
                    handleBCAST(message);
                    break;
                case ERROR:
                    handleERROR(message);
                    break;
                default:
                    System.out.println("Unsupported opcode: " + opCode);
                    break;

            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

    }

    private void handleERROR(byte[] message) {
        byte[] errMsg = Arrays.copyOfRange(message, 4, message.length - 1);
        String err = new String(errMsg, StandardCharsets.UTF_8);
        System.err.println("ERROR " + TftpEncoderDecoder.bytesToShort(message[2], message[3])+ " " + err);
        endRequest();
    }

    private void handleBCAST(byte[] message) {
        int action = message[2];
        String name = new String(Arrays.copyOfRange(message, 3, message.length - 1), StandardCharsets.UTF_8);
        System.out.println("BCAST " + (action == 0 ? "del" : "add") + " " + name);
    }

    private void handleACK(byte[] message) throws Exception {
        int blockNum = TftpEncoderDecoder.bytesToShort(message[2], message[3]);
        System.out.println("ACK " + blockNum);

        Request request = requestQueue.peek();
        if (request.opcode != TftpEncoderDecoder.Opcodes.WRQ) {
            if (request.opcode == TftpEncoderDecoder.Opcodes.DISC) {
                isConnected.set(false);
            }
            endRequest();
            return;
        }

        // only in case of WRQ
        if (blockNum == 0) {
            Path path = Paths.get(request.filename);

            byte[] fullFile = Files.readAllBytes(path);
            List<byte[]> chunks = splitByteArray(fullFile);
            short blockCounter = 1;

            for (byte[] packet : chunks) {
                packets.addLast(createData(blockCounter, packet));
                blockCounter = (short) (blockCounter + 1);
            }

            if (fullFile.length % MAX_DATA_LENGTH == 0) {
                packets.addLast(createData(blockCounter, new byte[0]));
            }
        }

        if (packets.isEmpty()) {
            System.out.println("WRQ " + request.filename + " complete");
            endRequest();
        } else {
            writeQueue.add(packets.removeFirst());
        }
    }


    private void handleDATA(byte[] message) throws IOException {
        blockNum++;
        short dataSize = TftpEncoderDecoder.bytesToShort(message[2], message[3]);
        short blockNumber = TftpEncoderDecoder.bytesToShort(message[4], message[5]);

        if (blockNum != blockNumber) {
            System.err.println("Wrong block number received for data");
            endRequest();
            return;
        }

        byte[] data = new byte[message.length - DATA_HEADER_LENGTH];
        System.arraycopy(message, DATA_HEADER_LENGTH, data, 0, data.length);

        System.out.println("Block: " + blockNumber + " " + Arrays.toString(data));
        Request request = requestQueue.peek();
        switch (Objects.requireNonNull(request.opcode)) {
            case RRQ:
                try (FileOutputStream output = new FileOutputStream(request.filename, true)) {
                    output.write(data);
                }
                sendACK(blockNumber);
                if (dataSize < MAX_DATA_LENGTH) {
                    endRequest();
                    System.out.println("RRQ " + request.filename + " complete");
                }
                break;
            case DIRQ:
                packets.addLast(data);
                sendACK(blockNumber);

                if (dataSize < MAX_DATA_LENGTH) {
                    String str = packets.stream()
                            .map(b -> new String(b, StandardCharsets.UTF_8))
                            .collect(Collectors.joining());
                    for (String filepath : str.split("\0")) {
                        System.out.println(filepath);
                    }
                    endRequest();
                }
                break;
        }
    }

    private void endRequest() {
        requestQueue.remove();
        blockNum = 0;
        packets.clear();
    }

    private void sendACK(short blockNumber) {
        ByteBuffer msg = ByteBuffer.allocate(OPCODE_LENGTH + 2);
        msg.put(shortToByteArray(TftpEncoderDecoder.Opcodes.ACK.value));
        msg.put(shortToByteArray(blockNumber));
        writeQueue.add(msg.array());
    }

    private static List<byte[]> splitByteArray(byte[] input) {
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;

        while (offset < input.length) {
            int length = Math.min(MAX_DATA_LENGTH, input.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(input, offset, chunk, 0, length);
            chunks.add(chunk);
            offset += length;
        }
        return chunks;
    }

    public void terminate() {
        isConnected.set(false);
    }
}



