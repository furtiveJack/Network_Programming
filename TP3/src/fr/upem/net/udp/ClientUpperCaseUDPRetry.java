package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUpperCaseUDPRetry {

    public static final int BUFFER_SIZE = 1024;
    private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPRetry.class.getName());
    private static void usage(){
        System.out.println("Usage : ClientUpperCaseUDPTimeout host port charset");
    }

    public static void main(String[] args) throws IOException {
        if (args.length!=3){
            usage();
            return;
        }
        InetSocketAddress server = new InetSocketAddress(args[0],Integer.parseInt(args[1]));
        Charset cs = Charset.forName(args[2]);

        DatagramChannel dc = DatagramChannel.open();
        dc.bind(null);
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(128);

        Thread listener = new Thread(() -> {
            ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
            while (! Thread.interrupted()) {
                try {
                    buff.clear();
                    dc.receive(buff);
                    buff.flip();
                    queue.put(cs.decode(buff).toString());
                } catch (InterruptedException | AsynchronousCloseException e) {
                    logger.info("Listener thread stopped");
                    return;
                } catch (IOException e) {
                    logger.severe("Unexpected stop of listener thread");
                    return;
                }
            }
        });
        listener.start();

        try (Scanner scan = new Scanner(System.in)){
            while(scan.hasNextLine()){
                String line = scan.nextLine();
                // Send Data
                dc.send(cs.encode(line), server);
                // Receive data
                String response = queue.poll(1000, TimeUnit.MILLISECONDS);
                while (response == null) {
                    // Resend data
                    dc.send(cs.encode(line), server);
                    response = queue.poll(1000, TimeUnit.MILLISECONDS);
                }
                System.out.println("Received String : " + response);
            }
        } catch (IOException | InterruptedException e) {
            logger.severe("Unexpected stop of sender thread : " + e.getMessage());
            return;
        }
        dc.close();
        listener.interrupt();
    }
}
