package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {
    private final static Logger logger = Logger.getLogger(FixedPrestartedConcurrentLongSumServerWithTimeout.class.getName());
    private final static int BUFFER_SIZE = 1024;
    private final static long TIMEOUT = 2000;

    private final ServerSocketChannel ssc;
    private final ArrayList<Thread> threads;
    private final int maxClient;
    private final HashMap<Thread, ThreadData> threadsMap;
    private Thread clientKiller;

    public FixedPrestartedConcurrentLongSumServerWithTimeout(int port, int maxClient) throws IOException {
        if (port <= 0 || maxClient <= 0) {
            throw new IllegalArgumentException("Port number and maxClient must be positive");
        }
        ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));
        this.maxClient = maxClient;
        threads = new ArrayList<>(maxClient);
        threadsMap = new HashMap<>();
        logger.info("Server started on port " + port
                + " with maxClient fixed to " + maxClient);
    }

    private void run() {
        var data = threadsMap.get(Thread.currentThread());
        while (! Thread.interrupted()) {
            SocketChannel client = null;
            try {
                client = ssc.accept();
                logger.info("Connection accepted from client : " + client.getRemoteAddress());
                data.setSocketChannel(client);
                serve(client, data);
            } catch (IOException e) {
                logger.info("Connection terminated with client by IOException");
            } finally {
                data.close();
            }
        }
        System.out.println("Thread " + Thread.currentThread() + " has been stopped !!!!!");
    }

    private void killClient()  {
        while (! Thread.interrupted()) {
            try {
                Thread.sleep(TIMEOUT);
            } catch (InterruptedException e) {
                logger.warning("*** clientKiller thread has been interrupted");
            }
            System.out.println("*** Checking which client will be killed ***");
            for (var t : threads) {
                var data = threadsMap.get(t);
                if (data.closeIfInactive(TIMEOUT)) {
                    logger.info("*** Thread " + t.getName() + " client has been closed***");
                }
            }
        }
    }

    public void launch() throws IOException {
        for (int i = 0 ; i < maxClient ; ++i) {
            var t = new Thread(this::run);
            threads.add(t);
            threadsMap.put(t, new ThreadData());
            t.start();
        }
        clientKiller = new Thread(this::killClient);
        clientKiller.start();
        logger.info("*** Server has been launched ***");
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

    private void serve(SocketChannel sc, ThreadData client) throws IOException {
        while (!Thread.interrupted()) {
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
                client.tick();
                buffer = ByteBuffer.allocate(nbOp * Long.BYTES);
                if (! readFully(sc, buffer)) {
                    logger.info("Client closed the connection.");
                    client.close();
                    return;
                }
                var sum = getAndComputeSum(buffer, nbOp);
                client.tick();
                var sendBuff = ByteBuffer.allocate(Long.BYTES);
                sendBuff.putLong(sum);
                sc.write(sendBuff.flip());
            } catch (BufferUnderflowException e) {
                logger.warning("Client does not respect the LongSum protocol");
                return;
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
        System.out.println("**usage**: java FixedPrestartedConcurrentLongSumServerWithTimeout.java <port_number> <max_client");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        FixedPrestartedConcurrentLongSumServerWithTimeout server = new FixedPrestartedConcurrentLongSumServerWithTimeout(
                Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
        try {
            server.clientKiller.join();
            for (var t : server.threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            logger.info("Thread has been interrupted");
        }
    }
}
