package fr.upem.net.udp.nonblocking;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPOneByOne {

    private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;

    private enum State {SENDING, RECEIVING, FINISHED};

    private final List<String> lines;
    private final List<String> upperCaseLines = new ArrayList<>();
    private final int timeout;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final Selector selector;
    private final SelectionKey uniqueKey;
    private int currentId;
    private long lastSend;
    private long lastReceive;

    // TODO add new fields 

    private State state;

    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }

    public ClientIdUpperCaseUDPOneByOne(List<String> lines, int timeout, InetSocketAddress serverAddress) throws IOException {
        this.lines = lines;
        this.timeout = timeout;
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
        ClientIdUpperCaseUDPOneByOne client = new ClientIdUpperCaseUDPOneByOne(lines, timeout, serverAddress);
        List<String> upperCaseLines = client.launch();
        Files.write(Paths.get(outFilename), upperCaseLines, UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

    }

    
    private List<String> launch() throws IOException, InterruptedException {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        while (!isFinished()) {
            System.out.println("State : " + state);
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
        return upperCaseLines;
    }

    /**
     * Updates the interestOps on key based on state of the context
     *
     * @return the timeout for the next select (0 means no timeout)
     */

    private int updateInterestOps() {
        if (state == State.RECEIVING) {
            if (System.currentTimeMillis() - lastSend >= timeout) {
                System.out.println("No response in " + timeout + "ms, resending");
                uniqueKey.interestOps(SelectionKey.OP_WRITE);
                //state = State.SENDING;
                return 0;
            }
            uniqueKey.interestOps(SelectionKey.OP_READ);
            return (int) (timeout - (lastReceive - lastSend));
        }
        uniqueKey.interestOps(SelectionKey.OP_WRITE);
        return 0;
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
        System.out.println("Reading data");
        var exp = dc.receive(buff);
        if (exp == null || ! exp.equals(serverAddress)) {
            return;
        }
        lastReceive = System.currentTimeMillis();
        buff.flip();
        long id = buff.getLong(); //checker la taille
        if (id == currentId) {
            CharBuffer data = UTF8.decode(buff);
            System.out.println("Received : " + id + " - " + data.toString());
            upperCaseLines.add(data.toString());
            currentId++;
            if (id >= lines.size() - 1) {
                state = State.FINISHED;
            }
            else {
                state = State.SENDING;
            }
            return;
        }
        System.out.println("Received answer with wrong id: " + id + " (expected : " + currentId + ")");
    }

    /**
    * Tries to send the packets
    *
    * @throws IOException if I/O error occurs while sending data
    */
    private void doWrite() throws IOException {
        ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
        buff.putLong(currentId);
        buff.put(UTF8.encode(lines.get(currentId)));
        System.out.println("Sending: " + currentId + " - " + lines.get(currentId));
        buff.flip();
        dc.send(buff, serverAddress);
        if (buff.hasRemaining()) {
            return;
        }
        lastSend = System.currentTimeMillis();
        System.out.println("Sent data ! ");
        state = State.RECEIVING;
    }
}







