package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientBetterUpperCaseUDP {

	private static final Logger logger = Logger.getLogger(ClientBetterUpperCaseUDP.class.getName());
	private static final int MAX_PACKET_SIZE = 1024;

	private static Charset ASCII_CHARSET = StandardCharsets.US_ASCII; //Charset.forName("US-ASCII");

	/**
	 * Creates and returns a String message represented by the ByteBuffer buffer,
	 * encoded in the following representation:
	 * - the size (as a Big Indian int) of a charsetName encoded in ASCII<br/>
	 * - the bytes encoding this charsetName in ASCII<br/>
	 * - the bytes encoding the message in this charset.<br/>
	 * The accepted ByteBuffer buffer must be in <strong>write mode</strong>
	 * (i.e. need to be flipped before to be used).
	 *
	 * @param buffer a ByteBuffer containing the representation of an encoded String message
	 * @return the String represented by buffer, or nothing if the buffer cannot be decoded
	 */
	public static Optional<String> decodeMessage(ByteBuffer buffer) {
		Objects.requireNonNull(buffer);
		buffer.flip();

		int originalLimit = buffer.limit();
		int charsetNbBytes = 0;
		Charset ascii = StandardCharsets.US_ASCII;
		Charset cs;

		// Decoding the size of the charset name
		try {
			charsetNbBytes = buffer.getInt();
		} catch (BufferOverflowException | BufferUnderflowException e) {
			return Optional.empty();
		}
		if (charsetNbBytes <= 0 || charsetNbBytes >= buffer.capacity()) {
			return Optional.empty();
		}
		// Updating buffer work-zone
		buffer.limit(buffer.position() + charsetNbBytes);
		// Decoding charset name
		try {
			cs = Charset.forName(ascii.decode(buffer).toString());
		} catch (UnsupportedCharsetException e) {
			return Optional.empty();
		}
		// Updating buffer work-zone
		buffer.limit(originalLimit);
		// Decoding and returning data
		return Optional.of(cs.decode(buffer).toString());
	}

	/**
	 * Creates and returns a new ByteBuffer containing the encoded representation 
	 * of the String <code>msg</code> using the charset <code>charsetName</code> 
	 * in the following format:
	 * - the size (as a Big Indian int) of the charsetName encoded in ASCII<br/>
	 * - the bytes encoding this charsetName in ASCII<br/>
	 * - the bytes encoding the String msg in this charset.<br/>
	 * The returned ByteBuffer is in <strong>write mode</strong> (i.e. need to 
	 * be flipped before to be used).
	 * If the buffer is larger than MAX_PACKET_SIZE bytes, then returns Optional.empty.
	 *
	 * @param msg the String to encode
	 * @param charsetName the name of the Charset to encode the String msg
	 * @return a newly allocated ByteBuffer containing the representation of msg,
	 *         or Optional.empty if the buffer would be larger than 1024
	 */
	public static Optional<ByteBuffer> encodeMessage(String msg, String charsetName) {
		Objects.requireNonNull(msg);
		Objects.requireNonNull(charsetName);

		Charset cs = Charset.forName(charsetName);
		Charset ascii = StandardCharsets.US_ASCII;
		int	csByteSize = charsetName.length();
		ByteBuffer msgBuff = cs.encode(msg);
		ByteBuffer csBuff = ascii.encode(charsetName);

		if ((Integer.BYTES + msgBuff.remaining() + csBuff.remaining()) > MAX_PACKET_SIZE) {
			return Optional.empty();
		}
		ByteBuffer data = ByteBuffer.allocate(MAX_PACKET_SIZE);

		data.putInt(csByteSize);
		data.put(csBuff);
		data.put(msgBuff);

		return Optional.of(data);
	}

	public static void usage() {
		System.out.println("Usage : ClientBetterUpperCaseUDP host port charsetName");
	}

	public static void main(String[] args) throws IOException {


		// check and retrieve parameters
		if (args.length != 3) {
			usage();
			return;
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		String charsetName = args[2];

		SocketAddress dest = new InetSocketAddress(host, port);
		// buff to receive messages
		ByteBuffer buff = ByteBuffer.allocateDirect(MAX_PACKET_SIZE);

		try(Scanner scan = new Scanner(System.in);
				DatagramChannel dc = DatagramChannel.open()){
			while (scan.hasNextLine()) {
				String line = scan.nextLine();
				Optional<ByteBuffer> enc = encodeMessage(line, charsetName);
				if (enc.isEmpty()) {
					System.out.println("Line is too long to be sent using the protocol BetterUpperCase");
					continue;
				}
				ByteBuffer packet = enc.get();
				packet.flip();
				dc.send(packet, dest);
				buff.clear();
				dc.receive(buff);
				Optional<String> res = decodeMessage(buff);
				if (res.isPresent()) {
					System.out.println("Received: "+res.get());
				} else {
					System.out.println("Received an invalid paquet");
				}
			}
		}
	}

}