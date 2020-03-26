package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.logging.Logger;

public class ServerEchoNonBlocking {

    private static final Logger logger = Logger.getLogger(ServerEchoNonBlocking.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress exp;
    private int port;

    public ServerEchoNonBlocking(int port) throws IOException {
        this.port=port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
   }


    public void serve() throws IOException {
        logger.info("ServerEcho started on port "+port);
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try{
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
        buff.flip(); // on flip ici pour etre sûr de le faire qu'a la reception
        key.interestOps(SelectionKey.OP_WRITE);
	 }

    private void doWrite(SelectionKey key) throws IOException {
        dc.send(buff, exp);
        if (buff.hasRemaining()) { // si l'envoi a raté
   		    return;
        }
   		key.interestOps(SelectionKey.OP_READ);
	}

    public static void usage() {
        System.out.println("Usage : ServerEcho port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerEchoNonBlocking server= new ServerEchoNonBlocking(Integer.parseInt(args[0]));
        server.serve();
    }




}
