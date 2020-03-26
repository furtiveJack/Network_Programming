package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.logging.Logger;

public class ServerEchoMultiPort {
    private final static int BUFFER_SIZE = 1024;
    private final static Logger logger = Logger.getLogger(ServerEchoMultiPort.class.getName());

    static class Context {
        final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
        SocketAddress exp;
        int port;

        public Context(int port) {
            this.port = port;
        }
    }

    private final Selector selector;
    private final HashMap<Integer, DatagramChannel> dcMap = new HashMap<>();
    private final int beginPort, endPort;

    public ServerEchoMultiPort(int beginPort, int endPort) throws IOException {
        if (beginPort <= 0 || endPort <= 0 || endPort <= beginPort) {
            throw new IllegalArgumentException("Incorrect range of ports");
        }
        this.beginPort = beginPort;
        this.endPort = endPort;
        selector = Selector.open();
        for (int port = beginPort ; port <= endPort ; ++port) {
            DatagramChannel dc = DatagramChannel.open();
            dc.bind(new InetSocketAddress(port));
            dc.configureBlocking(false);
            dc.register(selector, SelectionKey.OP_READ, new Context(port));
            dcMap.put(port, dc);
        }
    }

    public void serve() throws IOException {
        logger.info("Server started on ports from " + beginPort + " to " + endPort);
        while (! Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw new IOException(tunneled);
            }
        }
    }

    private void treatKey(SelectionKey key){
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void doRead(SelectionKey key) throws IOException {
        var context = (Context) key.attachment();
        context.buff.clear();
        context.exp = dcMap.get(context.port).receive(context.buff);
        if (context.exp == null) {
            return;
        }
        context.buff.flip();
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        var context = (Context) key.attachment();
        dcMap.get(context.port).send(context.buff, context.exp);
        if (context.buff.hasRemaining()) {
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoMultiPort <beginning_port> <ending_port>");
    }
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        ServerEchoMultiPort server = new ServerEchoMultiPort(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.serve();
    }


}
