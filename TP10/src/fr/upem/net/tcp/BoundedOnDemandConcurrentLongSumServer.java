package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BoundedOnDemandConcurrentLongSumServer {
    private static final Logger logger = Logger.getLogger(BoundedOnDemandConcurrentLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final ServerSocketChannel serverSocketChannel;
    private final Semaphore semaphore;

    public BoundedOnDemandConcurrentLongSumServer(int port, int maxClient) throws IOException {
        if (port < 0 || maxClient <= 0) {
            throw new IllegalArgumentException("Port and maxClient must be positive");
        }
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        semaphore = new Semaphore(maxClient);
        logger.info(this.getClass().getName() + " started on port " + port
                + " with maxClient fixed to " + maxClient);
    }

    public void launch() throws IOException {
        while (! Thread.interrupted()) {
            SocketChannel client = serverSocketChannel.accept();
            new Thread(() -> {
                try {
                    semaphore.acquire();
                    logger.info("Connection accepted from " + client.getRemoteAddress());
                    serve(client);
                    semaphore.release();
                } catch (IOException ioe) {
                    logger.log(Level.INFO, "Connection ended with client by IOException", ioe.getCause());
                } catch (InterruptedException ie) {
                    logger.log(Level.INFO, "Connection terminated with client by InterruptedException", ie.getCause());
                } finally {
                    silentlyClose(client);
                }
            }).start();
        }
    }

    private void serve(SocketChannel sc) throws IOException {
        while (! Thread.interrupted()) {
            var buffer = ByteBuffer.allocate(Integer.BYTES);
            System.out.println("serving  client");
            if (!readFully(sc, buffer)) {
                logger.info("Client closed the connection.");
                return;
            }
            try {
                buffer.flip();
                var nbOp = buffer.getInt();
                if (nbOp <= 0) {
                    logger.warning("Client sent an invalid number of operands");
                    return;
                }
                buffer = ByteBuffer.allocate(nbOp * Long.BYTES);
                if (! readFully(sc, buffer)) {
                    logger.info("Client closed the connection.");
                    return;
                }
                buffer.flip();
                long sum = 0;
                for (int i = 0; i < nbOp; ++i) {
                    sum += buffer.getLong();
                }
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
            System.out.println("Reading...");
            if (sc.read(bb) == -1) {
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void usage() {
        System.out.println("**usage**: java OnDemandConcurrentLongSumServer.java <port_number> <max_client");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        BoundedOnDemandConcurrentLongSumServer server = new BoundedOnDemandConcurrentLongSumServer(
                Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
    }
}
