package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.logging.Logger;

public class ClientEOS {

    public static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;
    public static final int BUFFER_SIZE = 1024;
    public static final Logger logger = Logger.getLogger(ClientEOS.class.getName());

    /**
     * This method:
     *  - connect to server
     *  - writes the bytes corresponding to request in UTF8
     *  - closes the write-channel to the server
     *  - stores the bufferSize first bytes of server response
     *  - return the corresponding string in UTF8
     *
     * @param request
     * @param server
     * @param bufferSize
     * @return the UTF8 string corresponding to bufferSize first bytes of server response
     * @throws IOException
     */
    public static String getFixedSizeResponse(String request, SocketAddress server, int bufferSize) throws IOException {
    	Objects.requireNonNull(request);
    	Objects.requireNonNull(server);
    	if (bufferSize <= 0) throw new IllegalArgumentException("Buffer size must be positive");
    	try (var sc = SocketChannel.open(server)) {
    		sc.write(UTF8_CHARSET.encode(request));
    		sc.shutdownOutput();
    		var buff = ByteBuffer.allocate(bufferSize);
    		if (! readFully(sc, buff)) {
				logger.warning("Connection closed by server");
			}
    		buff.flip();
    		return UTF8_CHARSET.decode(buff).toString();
		}
	}
    // 1st version
	/*
    public static String getFixedSizeResponse(String request, SocketAddress server, int bufferSize) throws IOException {
		Objects.requireNonNull(request);
		Objects.requireNonNull(server);
		try (var sc = SocketChannel.open(server)) {
			sc.write(UTF8_CHARSET.encode(request));
			sc.shutdownOutput();
			var buff = ByteBuffer.allocate(bufferSize);
			while (buff.hasRemaining()) {
				int read = sc.read(buff);
				if (read == -1) {
					logger.warning("Connection closed by server");
					break;
				}
			}
			buff.flip();
			return UTF8_CHARSET.decode(buff).toString();
		}
    }*/

    /**
  	 * This method:
	   *  - connect to server
	   *  - writes the bytes corresponding to request in UTF8
	   *  - closes the write-channel to the server
	   *  - reads and stores all bytes from server until read-channel is closed
	   *  - return the corresponding string in UTF8
	   *
	   * @param request
	   * @param server
	   * @return the UTF8 string corresponding the full response of the server
	   * @throws IOException
     */
	public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {
		Objects.requireNonNull(request);
		Objects.requireNonNull(server);
		try (var sc = SocketChannel.open(server)) {
			var buff = ByteBuffer.allocate(BUFFER_SIZE);
			sc.write(UTF8_CHARSET.encode(request));
			sc.shutdownOutput();
			while (readFully(sc, buff)) {
				var tmp = ByteBuffer.allocate(buff.capacity() * 2);
				buff.rewind();
				tmp.put(buff);
				buff = tmp;
			}
			buff.flip();
			return UTF8_CHARSET.decode(buff).toString();
		}
	}


    // 1st version
/*    public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {
		Objects.requireNonNull(request);
		Objects.requireNonNull(server);
    	try (var sc = SocketChannel.open(server)) {
			var buff = ByteBuffer.allocate(BUFFER_SIZE);
			sc.write(UTF8_CHARSET.encode(request));
			sc.shutdownOutput();
			while (sc.read(buff) != -1) {
				if (! buff.hasRemaining()) {
					var tmp = ByteBuffer.allocate(buff.capacity() * 2);
					buff.rewind();
					tmp.put(buff);
					buff = tmp;
				}
			}
			buff.flip();
			return UTF8_CHARSET.decode(buff).toString();
		}
    }
*/

   /**
	  * Fill the workspace of the Bytebuffer with bytes read from sc.
	  *
	  * @param sc
	  * @param bb
	  * @return false if read returned -1 at some point and true otherwise
	  * @throws IOException
	  */
  	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
		while (bb.hasRemaining()) {
			if (sc.read(bb) == -1) {
				return false;
			}
		}
		return true;
  	}

    public static void main(String[] args) throws IOException {
		  InetSocketAddress google = new InetSocketAddress("www.google.fr", 80);
		  System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n",
		  		google,	512));
		  System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n",
		        google));
    }
}
