package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPOneByOne {

    private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private final List<String> lines;
    private final List<String> upperCaseLines = new ArrayList<>(); //
    private final int timeout;
    private final String outFilename;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;

    private final BlockingQueue<Response> queue = new SynchronousQueue<>();



    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }

    private ClientIdUpperCaseUDPOneByOne(List<String> lines,int timeout,InetSocketAddress serverAddress,String outFilename) throws IOException {
        this.lines = lines;
        this.timeout = timeout;
        this.outFilename = outFilename;
        this.serverAddress = serverAddress;
        this.dc = DatagramChannel.open();
        dc.bind(null);
    }

    private void listenerThreadRun() {
        ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
        try {
            while (! Thread.interrupted()) {
                buff.clear();
                dc.receive(buff);
                buff.flip();
                queue.put(new Response(buff.getLong(), UTF8.decode(buff).toString()));
            }
        } catch (AsynchronousCloseException | InterruptedException e) {
            logger.info("Listener thread stopped");
        } catch (IOException e) {
            logger.severe("Unexpected close of the listener thread");
        }
    }


    // Structure du main
    /*
    while (true) {
        long currentTime = System.currentMillis();
        if (currentTime - lastSend >= timeout) {
            dc.send();
            lastSend = currentTime;
        }
        msg = queue.poll(timeout - (currentTime - lastSend));
        ...
     */

    private void launch() throws IOException, InterruptedException {
        Thread listenerThread = new Thread(this::listenerThreadRun);
        listenerThread.start();

        ByteBuffer request = ByteBuffer.allocate(BUFFER_SIZE);
        int id = 0;
        long lastSend, currentTime;
        boolean hasSend;
        lastSend = System.currentTimeMillis();
        for (String line : lines) {
            hasSend = false;
            System.out.println(request);
            request.clear();
            request.putLong(id);
            request.put(UTF8.encode(line));
            while (! hasSend) {
                currentTime = System.currentTimeMillis();
                if (currentTime - lastSend >= timeout) {
                    request.flip();
                    System.out.println("Resend request : " + request);
                    dc.send(request, serverAddress);
                    lastSend = currentTime;
                }

                Response response = queue.poll(timeout - (currentTime - lastSend), TimeUnit.MILLISECONDS);
                if (response != null) {
                    if (response.id == id) {
                        hasSend = true;
                        System.out.println("Got response : " + response.id + " - " + response.msg);
                        upperCaseLines.add(response.msg);
                    }
                    else {
                        System.out.println("Received response with wrong id : " + response.id);
                    }
                }
            }
            ++id;
        }

        Files.write(Paths.get(outFilename), upperCaseLines, UTF8,
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
        String host=args[3];
        int port = Integer.parseInt(args[4]);
        InetSocketAddress serverAddress = new InetSocketAddress(host,port);

        //Read all lines of inFilename opened in UTF-8
        List<String> lines= Files.readAllLines(Paths.get(inFilename),UTF8);
        //Create client with the parameters and launch it
        ClientIdUpperCaseUDPOneByOne client = new ClientIdUpperCaseUDPOneByOne(lines,timeout,serverAddress,outFilename);
        client.launch();

    }

    private static class Response {
        long id;
        String msg;

        Response(long id, String msg) {
            this.id = id;
            this.msg = msg;
        }
    }
}



