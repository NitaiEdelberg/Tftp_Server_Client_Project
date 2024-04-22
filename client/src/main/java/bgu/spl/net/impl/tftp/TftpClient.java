package bgu.spl.net.impl.tftp;

import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import bgu.spl.net.impl.tftp.Serializer.Opcodes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class TftpClient {
	public static void main(String[] args) {
		Socket sock;
		BufferedReader in;
		BufferedWriter out;
		try {
			sock = new Socket(args[0], Integer.parseInt(args[1]));
			in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

		// Create threads for user input and processing
		Thread userInputThread = new Thread(new UserInputThread(out, messageQueue));
		Thread processingThread = new Thread(new ProcessingThread(in, messageQueue));

		// Start the threads
		userInputThread.start();
		processingThread.start();
		// sock.close();
	}

	static class UserInputThread implements Runnable {
		private BlockingQueue<String> messageQueue;
		private BufferedWriter sockWriter;

		public UserInputThread(BufferedWriter sockWriter, BlockingQueue<String> messageQueue) {
			this.messageQueue = messageQueue;
			this.sockWriter = sockWriter;
		}

		@Override
		public void run() {
			Scanner scanner = new Scanner(System.in);
			while (true) {
				System.out.print("< ");
				String userInput = scanner.nextLine();

				try {
					sockWriter.write(userInput);
					sockWriter.newLine();
					sockWriter.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}

				try {
					// Put user input into the message queue
					messageQueue.put(userInput);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
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
		}
	}

	static class ProcessingThread implements Runnable {
		private BlockingQueue<String> messageQueue;
		private BufferedReader sockReader;

		public ProcessingThread(BufferedReader sockReader, BlockingQueue<String> messageQueue) {
			this.messageQueue = messageQueue;
			this.sockReader = sockReader;
		}

		@Override
		public void run() {
			System.out.println("reading messages from server");
			while (true) {
				System.out.println("awaiting response");
				String line;
				try {
					line = sockReader.readLine();
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}
				System.out.println("> " + line);
			}
		}
	}
}
