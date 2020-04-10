package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Objects;
import java.util.logging.Logger;

public class ServerEchoMultiPort {
    private final static int BUFFER_SIZE = 1024;
    private final static Logger logger = Logger.getLogger(ServerEchoMultiPort.class.getName());

    static class Context {
        private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
        private InetSocketAddress exp;
        private SelectionKey key;
        private DatagramChannel dc;

        public Context(SelectionKey key) {
            Objects.requireNonNull(key);
            this.dc = (DatagramChannel) key.channel();
            this.key = key;
        }

        public void doRead() throws  IOException {
            buff.clear();
            exp = (InetSocketAddress) dc.receive(buff);
            buff.flip();
            if (exp != null) {
                key.interestOps(SelectionKey.OP_WRITE);
            }
            else {
                logger.warning("Selector gave a bad hint (OP_WRITE)");
            }
        }

        public void doWrite() throws IOException {
            dc.send(buff, exp);
            if (! buff.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ);
            }
            else {
                logger.warning("Selector gave a bad hint (OP_READ)");
            }
        }
    }

    private final Selector selector;
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
            SelectionKey key = dc.register(selector, SelectionKey.OP_READ);
            key.attach(new Context(key));
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
               Context ctx = (Context) key.attachment();
               ctx.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                Context ctx = (Context) key.attachment();
                ctx.doRead();
            }
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
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
