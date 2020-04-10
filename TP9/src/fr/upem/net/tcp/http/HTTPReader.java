package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.regex.PatternSyntaxException;


public class HTTPReader {

    private final Charset ASCII_CHARSET = StandardCharsets.US_ASCII;
    private final SocketChannel sc;
    private final ByteBuffer buff;

    public HTTPReader(SocketChannel sc, ByteBuffer buff) {
        this.sc = sc;
        this.buff = buff;
    }

    /**
     * @return The ASCII string terminated by CRLF without the CRLF
     * <p>
     * The method assume that buff is in write mode and leave it in write-mode
     * The method never reads from the socket as long as the buffer is not empty
     * @throws IOException HTTPException if the connection is closed before a line could be read
     */
    public String readLineCRLF() throws IOException {
        StringBuilder builder = new StringBuilder();
        buff.flip();
        boolean lastCR = false;
        boolean finished = false;
        while (true) {
            while (buff.hasRemaining()) {
                var current = (char) buff.get();
                if (lastCR && current == '\n') {
                    finished = true;
                    break;
                }
                lastCR = current == '\r';
            }
            var tmp = buff.duplicate();
            tmp.flip();
            builder.append(StandardCharsets.US_ASCII.decode(tmp));
            if (finished) {
                break;
            }
            buff.clear();
            if (sc.read(buff) == -1) {
                throw new HTTPException("Server closed connection before end of line");
            }
            buff.flip();
        }
        buff.compact();
        builder.setLength(builder.length() - 2); // delete termintating CRLF
        return builder.toString();
    }

    /**
     * @return The HTTPHeader object corresponding to the header read
     * @throws IOException HTTPException if the connection is closed before a header could be read
     *                     if the header is ill-formed
     */
    public HTTPHeader readHeader() throws IOException {
        String firstLine = readLineCRLF();
        var headerMap = new HashMap<String, String>();
        if (firstLine.isEmpty()) {
            throw new HTTPException("Bad HTTP header");
        }
        var line = readLineCRLF();
        while (! line.isEmpty()) {
            try {
                var headerLine = line.split(":", 2);
                var key = headerLine[0];
                var value = headerLine[1];
                headerMap.compute(key, (k, v) -> (v == null) ? value : v.concat("; ").concat(value));
            } catch (PatternSyntaxException e) {
                throw new HTTPException("HTTP header is ill-formed");
            }
            line = readLineCRLF();
        }
        return HTTPHeader.create(firstLine, headerMap);
    }

    /**
     * @param size : number of bytes to read
     * @return a ByteBuffer in write-mode containing size bytes read on the socket
     * @throws IOException HTTPException if the connection is closed before all bytes could be read
     */
    public ByteBuffer readBytes(int size) throws IOException {
        var readBuff = ByteBuffer.allocate(size * Byte.BYTES);
        buff.flip();
        while (readBuff.hasRemaining()) {       // while size bytes haven't been read
            if (buff.hasRemaining()) {          // read bytes from buffer if possible
                readBuff.put(buff.get());
            }
            else {                              // read from socketChannel if there's no more bytes
                buff.clear();                   // to read in the buffer
                if (sc.read(buff) == -1) {
                    throw new IllegalArgumentException("Server closed connection before end of response");
                }
                buff.flip();
            }
        }
        buff.compact();
        return readBuff;
    }

    /**
     * @return a ByteBuffer in write-mode containing a content read in chunks mode
     * @throws IOException HTTPException if the connection is closed before the end of the chunks
     *                     if chunks are ill-formed
     */
    public ByteBuffer readChunks() throws IOException {
        int chunkSize = Integer.parseInt(readLineCRLF(), 16);
        System.out.println("chunksize : " + chunkSize);
        var chunks = ByteBuffer.allocate(1024);
        chunks.flip();
        while (chunkSize > 0) {
            while (chunks.remaining() < chunkSize) {
                var tmp = ByteBuffer.allocate(chunks.capacity() * 2);
                chunks.flip();
                tmp.put(chunks);
                chunks = tmp;
            }
            var chunk = readBytes(chunkSize);
            chunks.put(chunk.flip());
            if (readLineCRLF().length() != 0) { // remove ending \r\n from last chunk
                throw new HTTPException("Chunk is ill-formed");
            }
            chunkSize = Integer.parseInt(readLineCRLF(), 16);
            System.out.println("chunksize : " + chunkSize);
        }
        System.out.println(StandardCharsets.UTF_8.decode(chunks.flip()));
        return chunks;
    }

    public static void main(String[] args) throws IOException {
        Charset charsetASCII = StandardCharsets.US_ASCII;
        String request = "GET / HTTP/1.1\r\n"
                + "Host: www.w3.org\r\n"
                + "\r\n";
        SocketChannel sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        sc.write(charsetASCII.encode(request));
        ByteBuffer bb = ByteBuffer.allocate(50);
        HTTPReader reader = new HTTPReader(sc, bb);
        System.out.println(reader.readLineCRLF());
        System.out.println(reader.readLineCRLF());
        System.out.println(reader.readLineCRLF());
        sc.close();

        System.out.println("-------------------------");
        bb = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        reader = new HTTPReader(sc, bb);
        sc.write(charsetASCII.encode(request));
        System.out.println(reader.readHeader());
        sc.close();


        System.out.println("-------------------------");
        bb = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        reader = new HTTPReader(sc, bb);
        sc.write(charsetASCII.encode(request));
        HTTPHeader header = reader.readHeader();
        System.out.println(header);
        ByteBuffer content = reader.readBytes(header.getContentLength());
        content.flip();
        System.out.println(header.getCharset().decode(content));
        sc.close();


        System.out.println("-------------------------");
        bb = ByteBuffer.allocate(50);
        request = "GET / HTTP/1.1\r\n"
                + "Host: www.u-pem.fr\r\n"
                + "\r\n";
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
        reader = new HTTPReader(sc, bb);
        sc.write(charsetASCII.encode(request));
        header = reader.readHeader();
        System.out.println(header);
        content = reader.readChunks();
        content.flip();
        System.out.println(header.getCharset().decode(content));
        sc.close();
    }
}
