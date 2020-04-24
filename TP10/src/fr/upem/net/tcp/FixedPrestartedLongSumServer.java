package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.logging.Logger;

public class FixedPrestartedLongSumServer {
    private final static Logger logger = Logger.getLogger(FixedPrestartedLongSumServer.class.getName());

    private final ServerSocketChannel ssc;
    private final int maxClient;
    private ArrayList<Thread> threads;

    public FixedPrestartedLongSumServer(int port, int maxClient) throws IOException {
        if (port <= 0 || maxClient <= 0) {
            throw new IllegalArgumentException("Port number and maxClient must be positive");
        }
        ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));
        this.maxClient = maxClient;
        threads = new ArrayList<>(maxClient);

        logger.info(this.getClass().getName() + " started on port " + port
                + " with maxClient fixed to " + maxClient);
    }

    private void run() {
        while (! Thread.interrupted()) {
            SocketChannel client = null;
            try {
                client = ssc.accept();
                logger.info("Connection accepted from client : " + client.getRemoteAddress());
                serve(client);
            } catch (IOException e) {
                logger.info("Connection terminated with client by IOException");
            } finally {
                silentlyClose(client);
            }
        }
        System.out.println("Thread " + Thread.currentThread() + " has been stopped !!!!!");
    }

    public void launch() throws IOException {
        for (int i = 0 ; i < maxClient ; ++i) {
            var t = new Thread(this::run);
            t.start();
            threads.add(t);
        }
    }

    private int readNbOp(ByteBuffer bb) {
        bb.flip();
        var nbOp = bb.getInt();
        if (nbOp <= 0) {
            return -1;
        }
        return nbOp;
    }

    private long getAndComputeSum(ByteBuffer bb, int nbOp) {
        bb.flip();
        long sum = 0;
        for (int i = 0; i < nbOp; ++i) {
            sum += bb.getLong();
        }
        return sum;
    }

    private void serve(SocketChannel sc) throws IOException {
        while (! Thread.interrupted()) {
            var buffer = ByteBuffer.allocate(Integer.BYTES);
            if (!readFully(sc, buffer)) {
                logger.info("Client closed the connection.");
                return;
            }
            try {
                var nbOp = -1;
                if ((nbOp = readNbOp(buffer)) == -1) {
                    logger.warning("Client sent an invalid number of operands");
                    return;
                }
                buffer = ByteBuffer.allocate(nbOp * Long.BYTES);
                if (! readFully(sc, buffer)) {
                    logger.info("Client closed the connection.");
                    return;
                }
                var sum = getAndComputeSum(buffer, nbOp);
                var sendBuff = ByteBuffer.allocate(Long.BYTES);
                sendBuff.putLong(sum);
                sc.write(sendBuff.flip());
            } catch (BufferUnderflowException e) {
                logger.warning("Client does not respect the LongSum protocol");
                return;
            }
        }
    }

    /**
     * Close a SocketChannel while ignoring IOException
     *
     * @param sc : client socket channel to close
     */
    private void silentlyClose(SocketChannel sc) {
        if (sc != null) {
            try {
                sc.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

    static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
        while(bb.hasRemaining()) {
            if (sc.read(bb) == -1) {
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void usage() {
        System.out.println("**usage**: java FixedPrestartedLongSumServer.java <port_number> <max_client");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        FixedPrestartedLongSumServer server = new FixedPrestartedLongSumServer(
                Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
        try {
            for (var t : server.threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            logger.info("Thread has been interrupted");
        }
    }
}
