package fr.upem.net.udp.nonblocking;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;

public class ClientIdUpperCaseBurst {

    private static Logger logger = Logger.getLogger(ClientIdUpperCaseBurst.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;

    private enum State {SENDING, RECEIVING, FINISHED};

    private final List<String> lines;
    private final String[] upperCaseLines;
    private final int timeout;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final Selector selector;
    private final SelectionKey uniqueKey;
    private final int nbLines;
    private final BitSet received;
    private long lastSend, lastReceived;
    private int currentId;
    private int nbReceived;

    private State state;

    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }

    public ClientIdUpperCaseBurst(List<String> lines, int timeout, InetSocketAddress serverAddress) throws IOException {
        Objects.requireNonNull(lines);
        Objects.requireNonNull(serverAddress);
        if (timeout <= 0) throw new IllegalArgumentException("Timeout should be positive");
        this.lines = lines;
        this.nbLines = lines.size();
        this.timeout = timeout;
        this.received = new BitSet(nbLines);
        this.upperCaseLines = new String[nbLines];
        this.serverAddress = serverAddress;
        this.dc = DatagramChannel.open();
        dc.configureBlocking(false);
        dc.bind(null);
        this.selector = Selector.open();
        this.uniqueKey = dc.register(selector, SelectionKey.OP_WRITE);
        this.state = State.SENDING;
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
        ClientIdUpperCaseBurst client = new ClientIdUpperCaseBurst(lines, timeout, serverAddress);
        List<String> upperCaseLines = client.launch();
        Files.write(Paths.get(outFilename), upperCaseLines, UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    
    private List<String> launch() throws IOException, InterruptedException {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        while (!isFinished()) {
            selector.select(updateInterestOps());
            for (SelectionKey key : selectedKeys) {
                if (key.isValid() && key.isWritable()) {
                    doWrite();
                }
                if (key.isValid() && key.isReadable()) {
                    doRead();
                }
            }
            selectedKeys.clear();
        }
        dc.close();
        return Arrays.asList(upperCaseLines);
    }

    /**
    * Updates the interestOps on key based on state of the context
    *
    * @return the timeout for the next select (0 means no timeout)
    */
    private int updateInterestOps() {
        var currentTime = System.currentTimeMillis();
        if (state == State.SENDING) {
            uniqueKey.interestOps(SelectionKey.OP_WRITE);
            currentId = received.nextClearBit(currentId);
            return 0;
        }
        if (state == State.RECEIVING && System.currentTimeMillis() - lastSend >= timeout) {
            uniqueKey.interestOps(SelectionKey.OP_WRITE);
            currentId = 0;
            return 0;
        }
        // state == State.RECEIVING
        uniqueKey.interestOps(SelectionKey.OP_READ);
        return (int) (timeout - (currentTime - lastSend));
    }

    private boolean isFinished() {
        return state == State.FINISHED;
    }

    /**
    * Performs the receptions of packets
    *
    * @throws IOException if I/O error occurs while receiving data
    */
    private void doRead() throws IOException {
        ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
        var exp = dc.receive(buff);
        if (exp == null || ! exp.equals(serverAddress)) { return; }
        buff.flip();
        lastReceived = System.currentTimeMillis();
        long id = buff.getLong();
        if (id >= 0 && id < nbLines && ! received.get((int) id)) {
            received.set((int) id);
            upperCaseLines[(int) id] = UTF8.decode(buff).toString();
            nbReceived++;
            System.out.println("Received line id " + id);
        }
        if (nbReceived == nbLines) {
            System.out.println("Received all lines");
            state = State.FINISHED;
        }
    }

    /**
    * Tries to send the packets
    *
    * @throws IOException if I/O error occurs while sending data
    */
    private void doWrite() throws IOException { //on devrait commencer par send, donc préparer le buff à la fin pour le prochain envoi
        ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
        int id = received.nextClearBit(currentId);
        if (id == nbLines) {
            state = State.RECEIVING;
            return;
        }
        buff.clear();
        buff.putLong(id);
        buff.put(UTF8.encode(lines.get(id)));
        buff.flip();
        dc.send(buff, serverAddress);
        if (buff.hasRemaining()) {
            return;
        }
        System.out.println("Sent line nb " + id);
        currentId = id + 1;
        lastSend = System.currentTimeMillis();

    }
}







