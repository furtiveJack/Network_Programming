package fr.upem.net.udp;

import java.nio.channels.AsynchronousCloseException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;

public class ServerIdUpperCaseUDP {

	private static final Logger logger = Logger.getLogger(ServerIdUpperCaseUDP.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;
    private final DatagramChannel dc;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);

    public ServerIdUpperCaseUDP(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        logger.info("ServerBetterUpperCaseUDP started on port " + port);
    }

    public void serve() {
        try {
            while (!Thread.interrupted()) {
                buff.clear();
                InetSocketAddress exp = (InetSocketAddress) dc.receive(buff);
                logger.info("Received " + buff.position() + " bytes from " + exp.toString());
                buff.flip();

                long id = buff.getLong();
                String data = UTF8.decode(buff).toString();
                String upperCaseData = data.toUpperCase();
                logger.info("Decoded data : ID = " + id + "\nData = " + data);

                ByteBuffer buffSend = ByteBuffer.allocate(BUFFER_SIZE);
                buffSend.putLong(id);
                buffSend.put(UTF8.encode(upperCaseData)).flip();
                dc.send(buffSend, exp);
            }
        } catch (AsynchronousCloseException e) {
            logger.log(Level.INFO, "Server has been stopped");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unexpected stop of the server");
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerIdUpperCaseUDP port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerIdUpperCaseUDP server;
        int port = Integer.parseInt(args[0]);
        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }
        try {
            server = new ServerIdUpperCaseUDP(port);
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
        server.serve();
        server.dc.close();
    }
}
