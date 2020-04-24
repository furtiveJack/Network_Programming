package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Logger;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {
    private final static Logger logger = Logger.getLogger("ServerTimeout");
    private final static long TIMEOUT = 2000;
    private final static int MAX_THREADS = 10;

    private final ServerSocketChannel ssc;
    private final ArrayList<Thread> threads;
    private final ArrayList<ThreadData> threadsData;
    private final int maxClient;
    //private final HashMap<Thread, ThreadData> threadsMap;
    private Thread clientKiller;
    private Thread console;

    public FixedPrestartedConcurrentLongSumServerWithTimeout(int port, int maxClient) throws IOException {
        if (port <= 0 || maxClient <= 0) {
            throw new IllegalArgumentException("Port number and maxClient must be positive");
        }
        if (maxClient > MAX_THREADS) {
            throw new IllegalArgumentException("Number of clients must be less than " + MAX_THREADS);
        }
        ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));
        this.maxClient = maxClient;
        threads = new ArrayList<>(maxClient);
        threadsData = new ArrayList<>(maxClient);
        //threadsMap = new HashMap<>();
        logger.info("*** Server started on port " + port
                + " with maxClient fixed to " + maxClient);
    }

    /**
     * Runnable method used all the threads that are connected to clients.
     * This method tries to accept a client that is in pending connexion.
     * Once the connexion is open, allows the client to ask the server a sum to compute.
     */
    private void run() {
        var data = threadsData.get(threads.indexOf(Thread.currentThread()));
        while (! Thread.interrupted()) {
            SocketChannel client;
            try {
                client = ssc.accept();
                logger.info("*** Connection accepted from client : " + client.getRemoteAddress() + " ***");
                data.setSocketChannel(client);
                serve(client, data);
            } catch (AsynchronousCloseException e) {
                data.close();
            } catch (IOException e) {
                logger.info("*** " + Thread.currentThread().getName() + " : Connection terminated" +
                                " with client by IOException ***");
            } finally {
                data.close();
            }
        }
        data.close();
//        logger.info("*** Worker" + Thread.currentThread().getName() + " has been interrupted ***");
    }

    /**
     * Runnable method used by the clientKiller thread. Every TIMEOUT ms, this method checks if one of the
     * client thread is connected to an inactive client. If so, the connexion with this client is closed.
     */
    private void killClients()  {
        while (! Thread.interrupted()) {
            try {
                Thread.sleep(TIMEOUT);
            } catch (InterruptedException e) {
//                logger.info("*** clientKiller thread has been interrupted *** ");
                return;
            }

            for (var i = 0 ; i < maxClient ; ++i) {
                var data = threadsData.get(i);
                var t = threads.get(i);
                if (data.closeIfInactive(TIMEOUT)) {
                    logger.info("*** " + t.getName() + " client has been closed due to timeout ***");
                }
            }
        }
//        logger.info("*** clientKiller thread has been interrupted *** ");
    }

    /**
     * Runnable method used by the console thread. This method reads commands as string from stdin, and perform
     * the corresponding action (if the command is implemented).
     * Possible commands are :
     *  - INFO : return the number of clients currently being served.
     *  - SHUTDOWN : close all the clients connexions.
     *  - SHUTDOWNNOW : close all the clients connexions (even if they were currently being served),
     *                  and shutdown the server.
     */
    private void readCommands() {
        var input = new Scanner(System.in);
        while (!Thread.interrupted()) {
            var cmd = input.nextLine();
            switch (cmd) {
                case "INFO":
                    System.out.println(countConnectedClients() + " clients are currently being served");
                    break;
                case "SHUTDOWN":
                    System.out.println("Shutdown " + shutdownNonWorkingThreads() + " threads that were not serving clients");
                    break;
                case "SHUTDOWNNOW":
                    shutdownServer();
                    break;
                default:
                    System.out.println("ERR : Unknown command.");
                    break;
            }
        }
        input.close();
//        logger.info("***  Console thread has been interrupted ***");
    }

    /**
     * Launch the server
     */
    public void launch() {
        for (int i = 0 ; i < maxClient ; ++i) {
            var t = new Thread(this::run);
            threads.add(t);
            threadsData.add(new ThreadData());
        }
        threads.forEach(Thread::start);
        clientKiller = new Thread(this::killClients);
        clientKiller.start();
        console = new Thread(this::readCommands);
        console.start();
        logger.info("*** Server has been launched ***");
    }

    private int countConnectedClients() {
        int count = 0;
        for (var i = 0 ; i < maxClient ; ++i) {
            if (threadsData.get(i).isClientConnected()) {
                count++;
            }
        }
        return count;
    }

    private void shutdownServer() {
        threads.forEach(Thread::interrupt);
        clientKiller.interrupt();
        console.interrupt();
        try {
            ssc.close();
        } catch (IOException e) {
            //
        }
    }

    private int shutdownNonWorkingThreads() {
        int count = 0;
        for (var i = 0 ; i < maxClient ; ++i) {
            var t = threads.get(i);
            if (! threadsData.get(i).isClientConnected()) {
                t.interrupt();
                count++;
            }
        }
        return count;
    }

    /**
     * Return an int read from the ByteBuffer. The value read represents the number of operands to compute that will be send
     * to the server by a client, so this number must be positive.
     * @param bb : the ByteBuffer to read.
     * @return the int read, or -1
     * @throws BufferUnderflowException if the read-zone of the buffer does not contain an integer (4 bytes) to read.
     */
    private int readNbOp(ByteBuffer bb) throws BufferUnderflowException {
        bb.flip();
        var nbOp = bb.getInt();
        if (nbOp <= 0) {
            return -1;
        }
        return nbOp;
    }

    /**
     * Read nbOp long from the ByteBuffer given, and compute their sum.
     * @param bb : the ByteBuffer to read
     * @param nbOp : the number of operands to read
     * @return the sum of all the operands read
     * @throws BufferUnderflowException if the read-zone of the buffer is smaller than LONG.BYTES.
     */
    private long getAndComputeSum(ByteBuffer bb, int nbOp) throws BufferUnderflowException {
        bb.flip();
        long sum = 0;
        for (int i = 0; i < nbOp; ++i) {
            sum += bb.getLong();
        }
        return sum;
    }

    /**
     * Serves a client accordingly to the LongSum Protocol.
     * @param sc : the socket channel connected to a client.
     * @param client : the data associated to a client to manage timeouts.
     * @throws IOException : if in I/O error occurs while reading or writing to the SocketChannel.
     */
    private void serve(SocketChannel sc, ThreadData client) throws IOException {
        while (!Thread.interrupted()) {
            var buffer = ByteBuffer.allocate(Integer.BYTES);
            if (!readFully(sc, buffer)) {
                logger.info("*** Client closed the connection. ***");
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
                    logger.info("*** Client closed the connection. ***");
                    client.close();
                    return;
                }
                var sum = getAndComputeSum(buffer, nbOp);
                client.tick();
                var sendBuff = ByteBuffer.allocate(Long.BYTES);
                sendBuff.putLong(sum);
                sc.write(sendBuff.flip());
            } catch (BufferUnderflowException e) {
                logger.warning("*** Client does not respect the LongSum protocol ***");
                return;
            }
        }
    }

    private static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
        while(bb.hasRemaining()) {
            if (sc.read(bb) == -1) {
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
            server.console.join();
            for (var t : server.threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            logger.info("Thread has been interrupted");
        }
        for (var i = 0 ; i < server.maxClient ; ++i) {
            var t = server.threads.get(i);
            var data = server.threadsData.get(i);
            if (data.isClientConnected()) {
                logger.info("\nThere is still a connected client\n");
            }
        }
        System.out.println("Server has been shutdown");
    }
}
