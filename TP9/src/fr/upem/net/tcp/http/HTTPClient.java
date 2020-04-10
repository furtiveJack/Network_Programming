package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Logger;

public class HTTPClient {
    private static final int BUFFER_SIZE = 1024;
    private static final int PORT = 80;
    private static final int MAX_REDIRECTION = 5;
    private final Charset ASCII = StandardCharsets.US_ASCII;
    private final Charset UTF8 = StandardCharsets.UTF_8;
    private final static Logger logger = Logger.getLogger(HTTPClient.class.getName());
    private final String resource;
    private InetSocketAddress server;
    private HTTPReader reader;
    private SocketChannel sc;
    private int nbRedirection;

    public HTTPClient(String address, String resource) throws IOException {
        Objects.requireNonNull(address);
        Objects.requireNonNull(resource);
        this.server = new InetSocketAddress(address, PORT);
        this.resource = resource;
        this.sc = SocketChannel.open();
        this.sc.connect(this.server);
        this.reader = new HTTPReader(sc, ByteBuffer.allocateDirect(BUFFER_SIZE));
    }

    public void sendRequest() throws IOException {
        var request = "GET " + resource + " HTTP/1.1\r\n"
                + "Host: " + server.getHostString() + ":" + server.getPort() + "\r\n"
                + "\r\n";
        sc.write(ASCII.encode(request));
    }

    private void redirect(String location) throws IOException {
        URL url = new URL(location);
        logger.info("Resource has moved to " + location + "\nRedirecting.");
        if (! url.getProtocol().equals("http")) {
            throw new HTTPException("Redirected resource can't be find using HTTP protocol.\nCanceling request");
        }
        server = new InetSocketAddress(url.getHost(), PORT);
        sc = SocketChannel.open();
        sc.connect(server);
        reader = new HTTPReader(sc, ByteBuffer.allocateDirect(BUFFER_SIZE));
        nbRedirection++;
        if (nbRedirection >= MAX_REDIRECTION) {
            throw new HTTPException("Too much redirection.\nCanceling request");
        }
    }

    public String getResponse() throws IOException {
        var header = reader.readHeader();
        var size = header.getContentLength();
        var contentType = header.getContentType();
        var charset = header.getCharset();
        System.out.println(header + "\n");
        if (header.getCode() == 301 || header.getCode() == 302) {       // dealing with redirections
            var location = header.getFields().get("location");
            if (location == null) {
                throw new HTTPException("Server said the document has moved, but has not provided the new address");
            }
            redirect(location);
            sendRequest();
            getResponse();
        }
        if (contentType == null ) {
            throw new HTTPException("Missing content-type in response header");
        }
        if (charset == null) {
            charset = UTF8;
        }
        var types = contentType.split(";");
        for (var type : types) {
            if (type.equals("text/html")) {
                ByteBuffer buff;
                if (header.isChunkedTransfer()) {
                    buff = reader.readChunks();
                }
                else {
                    if (size == -1) {
                        logger.warning("Missing size information in response header");
                        return null;
                    }
                    buff = reader.readBytes(size);
                }
                return charset.decode(buff.flip()).toString();
            }
        }
        throw new IllegalArgumentException("Response is not html");
    }

    public static void usage() {
        System.out.println("Usage : HTTPClient <host> <resource>");
    }
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
        }
        var host = args[0];
        var resource = args[1];
        var client = new HTTPClient(host, resource);
        client.sendRequest();
        try {
            var response = client.getResponse();
            System.out.println(response);
        } catch (IllegalArgumentException e) {
            logger.warning(e.getMessage());
        }

    }
}
