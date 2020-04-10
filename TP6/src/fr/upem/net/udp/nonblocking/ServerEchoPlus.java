package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerEchoPlus {
    private final static Logger logger = Logger.getLogger(ServerEchoPlus.class.getName());
    private final static int BUFFER_SIZE = 1024;

    private final Selector selector;
    private final DatagramChannel dc;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress exp;
    private int port;

    public ServerEchoPlus(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(this.port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
    }

    public void serve() throws IOException {
        logger.info("Server started on port " + this.port);
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw new IOException(tunneled);
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void doRead(SelectionKey key) throws IOException {
        buff.clear();
        exp = dc.receive(buff);
        if (exp == null) {
            return;
        }
        buff.flip();
        incBuffer();
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {

        dc.send(buff, exp);
        if (buff.hasRemaining()) {
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    private void incBuffer() {
        byte[] inc = new byte[BUFFER_SIZE];
        int size, i = 0;
        while (buff.hasRemaining()) {
            inc[i] = (byte) ((buff.get() + 1) % 255);
            ++i;
        }
        size = i;
        buff.clear();
        for (i = 0 ; i < size ; ++i) {
            buff.put(inc[i]);
        }
        buff.flip();
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoPlus <port>");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        var server = new ServerEchoPlus(Integer.parseInt(args[0]));
        server.serve();
    }
}
