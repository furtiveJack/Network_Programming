package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientUpperCaseUDPFile {

        private static final Charset UTF8 = StandardCharsets.UTF_8;
        private static final int BUFFER_SIZE = 1024;
        private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPFile.class.getName());

        private static void usage() {
            System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
        }

        public static void main(String[] args) throws IOException, InterruptedException {
            if (args.length !=5) {
                usage();
                return;
            }

            String inFilename = args[0];
            String outFilename = args[1];
            int timeout = Integer.parseInt(args[2]);
            String host=args[3];
            int port = Integer.parseInt(args[4]);
            SocketAddress server = new InetSocketAddress(host,port);
            DatagramChannel dc = DatagramChannel.open();
            dc.bind(null);
            BlockingQueue<String> queue = new SynchronousQueue<>();

            // ---- Producer thread : get responses from server and put them in the queue ------------------------------
            Thread listener = new Thread( () -> {
                ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
                while (! Thread.interrupted()) {
                    try {
                        buff.clear();
                        dc.receive(buff);
                        buff.flip();
                        queue.put(UTF8.decode(buff).toString());
                    }
                    catch( InterruptedException | AsynchronousCloseException e) {
                        logger.info("Listener thread stopped");
                        return;
                    }
                    catch ( IOException e) {
                        logger.severe("Unexpected stop of listener thread");
                        return;
                    }
                }
            });
            listener.start();

            // ---- Consumer thread : Send lines to the server and poll answers from the queue -------------------------
            //Read all lines of inFilename opened in UTF-8
            List<String> lines= Files.readAllLines(Paths.get(inFilename), UTF8);
            ArrayList<String> upperCaseLines = new ArrayList<>();

            logger.info("Start sending lines for upper-casing");
            for (String line: lines) {
                String upperCased;
                do {
                    dc.send(UTF8.encode(line), server);
                    upperCased = queue.poll(1000, TimeUnit.MILLISECONDS);
                } while (upperCased == null);
                upperCaseLines.add(upperCased);
            }
            logger.info("Lines have been sent. Start writing upper-cased lines to file " + outFilename);
            // Write upperCaseLines to outFilename in UTF-8 ------------------------------------------------------------
            Files.write(Paths.get(outFilename),upperCaseLines, UTF8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            logger.info("Finished writing lines.\nExiting program");
            dc.close();
            listener.interrupt();
        }
    }