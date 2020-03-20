package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

    private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final List<String> lines;
    private final int nbLines;
    private final String[] upperCaseLines; //
    private final int timeout;
    private final String outFilename;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final SafeBitSet received;         // BitSet marking received requests

    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
    }

    private ClientIdUpperCaseUDPBurst(List<String> lines, int timeout, InetSocketAddress serverAddress, String outFilename) throws IOException {
        this.lines = lines;
        this.nbLines = lines.size();
        this.timeout = timeout;
        this.outFilename = outFilename;
        this.serverAddress = serverAddress;
        this.dc = DatagramChannel.open();
        dc.bind(null);
        this.received = new SafeBitSet(nbLines);
        this.upperCaseLines = new String[nbLines];
    }

    private void senderThreadRun() {
        long lastSend = 0L;
        ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
        while (!Thread.interrupted()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSend >= timeout) {
                for (int i = 0; i < nbLines; ++i) {
                    if (! received.get(i)) {
                        buff.clear();
                        buff.putLong(i);
                        String data = lines.get(i);
                        buff.put(UTF8.encode(data));
                        try {
                            buff.flip();
                            dc.send(buff, serverAddress);
                            lastSend = System.currentTimeMillis();
                            logger.info(">>> Sent line with id " + i + " and msg :" + data);
                        } catch (AsynchronousCloseException e) {
                            logger.info("Sender Thread stopped");
                        } catch (IOException e) {
                            logger.severe("Unexpected stop of the sender thread");
                        }
                    }
                }
            }
        }
    }

    private void launch() throws IOException {
        Thread senderThread = new Thread(this::senderThreadRun);
        senderThread.start();

        ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
        while (received.getCardinality() < nbLines) {
            buff.clear();
            dc.receive(buff);
            buff.flip();
            int id = (int) buff.getLong();
            String msg = UTF8.decode(buff).toString();
            System.out.println(">>> Received answer with id " + id + " and message : " + msg);
            received.set(id);
            upperCaseLines[id] = msg;
        }
        dc.close();
        senderThread.interrupt();

        Files.write(Paths.get(outFilename), Arrays.asList(upperCaseLines), UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 5) {
            usage();
            return;
        }

        String inFilename = args[0];
        String outFilename = args[1];
        int timeout = Integer.parseInt(args[2]);
        String host = args[3];
        int port = Integer.parseInt(args[4]);
        InetSocketAddress serverAddress = new InetSocketAddress(host, port);

        //Read all lines of inFilename opened in UTF-8
        List<String> lines = Files.readAllLines(Paths.get(inFilename), UTF8);
        //Create client with the parameters and launch it
        ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines, timeout, serverAddress, outFilename);
        client.launch();
    }
}


