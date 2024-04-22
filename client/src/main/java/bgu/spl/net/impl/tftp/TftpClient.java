package bgu.spl.net.impl.tftp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import bgu.spl.net.impl.tftp.Serializer.Opcodes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TftpClient {
    private static volatile AtomicBoolean isConnected = new AtomicBoolean(true);
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
        BlockingQueue<Opcodes> requestQueue = new LinkedBlockingQueue<>();

        Thread userInputThread = new Thread(new UserInputThread(requestQueue, writeQueue));
        Thread processingThread = new Thread(new ProcessingThread(requestQueue, in, writeQueue));

        userInputThread.start();
        processingThread.start();

        while (isConnected.get()) {
            byte[] request;
            try {
                request = writeQueue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
            System.out.println("Sending: " + Arrays.toString(request));

            try {
                out.write(request);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

    }

    static class UserInputThread implements Runnable {
        private BlockingQueue<Opcodes> requestQueue;
        private BlockingQueue<byte[]> writeQueue;

        public UserInputThread(BlockingQueue<Opcodes> requestQueue, BlockingQueue<byte[]> writeQueue) {
            this.writeQueue = writeQueue;
            this.requestQueue = requestQueue;
        }

        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            while (isConnected.get()) {
                System.out.print("< ");
                String userInput = scanner.nextLine();
                byte[] bytes;
                try {
                    bytes = serialize(userInput);
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                writeQueue.add(bytes);
            }
        }

        private byte[] serialize(String request) {
            Opcodes opcode;
            String[] tokenized = request.split(" ");
            try {
                opcode = Opcodes.valueOf(tokenized[0]);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                throw e;
            }

            List<byte[]> parts = new ArrayList<>();
            parts.add(encode(opcode));

            switch (opcode) {
                case LOGRQ:
                case DELRQ:
                case WRQ:
                case RRQ:
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
            requestQueue.add(opcode);
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

        private byte[] encode(Opcodes opcode) {
            return new byte[]{0, (byte) opcode.getValue()};
        }

        private byte[] encode(String str) {
            byte[] bytes = new byte[str.length() + 1];
            System.arraycopy(str.getBytes(), 0, bytes, 0, str.getBytes(StandardCharsets.UTF_8).length);
            return bytes;
        }
    }

    static class ProcessingThread implements Runnable {
        private BufferedInputStream sockReader;
        private BlockingQueue<Opcodes> requestQueue;
        private BlockingQueue<byte[]> writeQueue;
        private static final int EOF = -1;

        public ProcessingThread(BlockingQueue<Opcodes> requestQueue, BufferedInputStream sockReader, BlockingQueue<byte[]> writeQueue) {
            this.sockReader = sockReader;
            this.requestQueue = requestQueue;
            this.writeQueue = writeQueue;
        }

        public static int twoBytes2Int (byte byte1,byte byte2) {
            return ((byte1 & 0xff) << 8) | (byte2 & 0xff);
        }

        public static int twoBytes2Int(byte[] bytes) {
            return twoBytes2Int(bytes[0], bytes[1]);
        }

        @Override
        public void run() {
            System.out.println("reading messages from server");
            short opcode;
            try {
                opcode = readShort();
            } catch (IOException e) {
                opcode = EOF;
            }
            while (opcode != EOF && isConnected.get()) {
                try {
                    switch (Opcodes.values()[opcode - 1]) {
                        case ACK:
                            short blockNum = readShort();
                            System.out.println("[ACK] Block: " + blockNum);
                            requestQueue.poll();
                            break;
                        case DATA:
                            handle_data();
                            break;
                        case BCAST:
                            boolean isAdded = read(1)[0] != 0;
                            String filename = readString();
                            System.out.println("[BCAST] Filename: " + filename + " isAdded: " + isAdded);
                            break;
                        case ERROR:
                            short errorCode = readShort();
                            String errorMsg = readString();
                            System.err.println("[Error] "+ errorCode + ": " + errorMsg);
                            requestQueue.poll();
                            break;
                    }
                    opcode = readShort();
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }

            }
            isConnected.set(false);
        }

        private void handle_data() throws IOException {
            short dataSize = readShort();
            short blockNumber = readShort();
            byte[] data = read(dataSize);
            System.out.println("Block: " + blockNumber + " " + Arrays.toString(data));
            Opcodes opcode = requestQueue.peek();
            switch (Objects.requireNonNull(opcode)) {
                case RRQ:
                    // TODO write to file
                    ack(blockNumber);
                    if (dataSize < 512) {
                        requestQueue.poll();
                    }
                    break;
                case DIRQ:
                    String str = new String(data, StandardCharsets.UTF_8);
                    for (String filepath : str.split("\0")) {
                        System.out.println(filepath);
                    }
                    ack(blockNumber);
                    requestQueue.poll();
                    break;
            }
        }

        private void ack(short blockNumber) {
            ByteBuffer msg = ByteBuffer.allocate(4);
            msg.put(Serializer.shortToByteArray(Opcodes.ACK.getValue()));
            msg.put(Serializer.shortToByteArray(blockNumber));
            writeQueue.add(msg.array());
        }

        private byte[] read(int bytesToRead) throws IOException {
            byte[] bytes = new byte[bytesToRead];

            for (int i =0; i < bytesToRead; i++) {
                bytes[i] = (byte) sockReader.read();
            }

            return bytes;
        }
        private short readShort() throws IOException {
            return (short)twoBytes2Int(read(2));
        }
        private String readString() throws IOException {
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = sockReader.read()) != 0) {
                sb.append((char) b);
            }
            return sb.toString();
        }
    }
}
