package bgu.spl.net.impl.tftp;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import bgu.spl.net.impl.tftp.Serializer.Opcodes;

import java.net.Socket;
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

        Thread userInputThread = new Thread(new UserInputThread(requestQueue, writeQueue));
        Thread processingThread = new Thread(new ProcessingThread(requestQueue, in, writeQueue));

        userInputThread.start();
        processingThread.start();

        while (isConnected.get()) {
            byte[] request;
            try {
                request = writeQueue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                continue;
            }

            if (request == null) {
                continue;
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

        System.out.println("Done");
        userInputThread.interrupt();
    }

    static class UserInputThread implements Runnable {
        private BlockingQueue<Request> requestQueue;
        private BlockingQueue<byte[]> writeQueue;

        public UserInputThread(BlockingQueue<Request> requestQueue, BlockingQueue<byte[]> writeQueue) {
            this.writeQueue = writeQueue;
            this.requestQueue = requestQueue;
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
            Opcodes opcode;
            String[] tokenized = request.split(" ");
            opcode = Opcodes.valueOf(tokenized[0]);

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
            if (opcode == Opcodes.RRQ || opcode == Opcodes.WRQ) {
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
        private BlockingQueue<Request> requestQueue;
        private BlockingQueue<byte[]> writeQueue;
        private static final int EOF = -1;

        public ProcessingThread(BlockingQueue<Request> requestQueue, BufferedInputStream sockReader, BlockingQueue<byte[]> writeQueue) {
            this.sockReader = sockReader;
            this.requestQueue = requestQueue;
            this.writeQueue = writeQueue;
        }

        public static int twoBytes2Int(byte byte1, byte byte2) {
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
            while (opcode != EOF) {
                try {
                    switch (Opcodes.values()[opcode - 1]) {
                        case ACK:
                            handle_ack();
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
                            handle_error();
                            break;
                    }
                    opcode = readShort();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

            }
            isConnected.set(false);
        }

        private void handle_error() throws IOException {
            short errorCode = readShort();
            String errorMsg = readString();
            System.err.println("[Error] " + errorCode + ": " + errorMsg);
            requestQueue.poll();
        }

        private void handle_ack() throws Exception {
            short blockNum = readShort();
            System.out.println("[ACK] Block: " + blockNum);
            Request request = requestQueue.peek();
            switch (request.opcode) {
                case WRQ:
                    long fileSize = Files.size(Paths.get(request.filename));
                    if ((512 * blockNum) > fileSize) {
                        requestQueue.poll();
                        return;
                    }
                    ByteBuffer buffer = ByteBuffer.allocate((int)Math.min(512L, fileSize));
                    try (FileChannel fileChannel = FileChannel.open(Paths.get(request.filename), StandardOpenOption.READ)) {
                        fileChannel.position(512 * blockNum);
                        int bytesRead = fileChannel.read(buffer);

                        if (bytesRead == -1) {
                            throw new Exception("Failed to read file");
                        }
                    }
                    sendData(Opcodes.DATA.getValue(), (short) (blockNum + 1), buffer.array());
                    break;
                default:
                    requestQueue.poll();
                    break;
            }
        }

        private void handle_data() throws IOException {
            short dataSize = readShort();
            short blockNumber = readShort();
            byte[] data = read(dataSize);
            System.out.println("Block: " + blockNumber + " " + Arrays.toString(data));
            Request request = requestQueue.peek();
            switch (Objects.requireNonNull(request.opcode)) {
                case RRQ:
                    try (FileOutputStream output = new FileOutputStream(Files.createFile(Paths.get(request.filename)).toFile(), true)) {
                        output.write(data);
                    }

                    ack(blockNumber);
                    if (dataSize < 512) {
                        requestQueue.poll();
                        System.out.println("Complete");
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
            ByteBuffer msg = ByteBuffer.allocate(2 + 2);
            msg.put(Serializer.shortToByteArray(Opcodes.ACK.getValue()));
            msg.put(Serializer.shortToByteArray(blockNumber));
            writeQueue.add(msg.array());
        }

        public void sendData(short opcode, short blockNum, byte[] data) {
            ByteBuffer msg = ByteBuffer.allocate(2 + 2 + 2 + data.length);
            msg.put(Serializer.shortToByteArray(opcode));
            msg.put(Serializer.shortToByteArray((short) data.length));
            msg.put(Serializer.shortToByteArray(blockNum));
            msg.put(data);
            writeQueue.add(msg.array());

        }

        private byte[] read(int bytesToRead) throws IOException {
            byte[] bytes = new byte[bytesToRead];

            for (int i = 0; i < bytesToRead; i++) {
                bytes[i] = (byte) sockReader.read();
            }

            return bytes;
        }

        private short readShort() throws IOException {
            return (short) twoBytes2Int(read(2));
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
