package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IterativeLongSumServer {

    private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024; 
    private final ServerSocketChannel serverSocketChannel;

    public IterativeLongSumServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName()
                + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */

    public void launch() throws IOException {
        logger.info("Server started");
        while(!Thread.interrupted()) {
            SocketChannel client = serverSocketChannel.accept();
            try {
                logger.info("Connection accepted from " + client.getRemoteAddress());
                serve(client);
            } catch (IOException ioe) {
                logger.log(Level.INFO,"Connection terminated with client by IOException",ioe.getCause());
            } catch (InterruptedException ie) {
                logger.info("Server interrupted");
                break;
            } finally {
                silentlyClose(client);
            }
        }
    }

    /**
     * Treat the connection sc applying the protocol
     * All IOException are thrown
     *
     * @param sc : socket channel connected to the client
     * @throws IOException
     * @throws InterruptedException
     */
    private void serve(SocketChannel sc) throws IOException, InterruptedException{
   	    var buffer = ByteBuffer.allocate(BUFFER_SIZE);
   	    while (true) {
   	        System.out.println("serving  client");
            if (!readFully(sc, buffer)) {
                logger.info("Client closed the connection.");
                return;
            }
            try {
                System.out.println("Reading received data");
                var nbOp = buffer.getInt();
                if (nbOp <= 0) {
                    logger.warning("Client does not respect the LongSum protocol");
                    return;
                }
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
            System.out.println("reading...");
            if (sc.read(bb) == -1){
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        IterativeLongSumServer server = new IterativeLongSumServer(Integer.parseInt(args[0]));
        server.launch();
    }
}
