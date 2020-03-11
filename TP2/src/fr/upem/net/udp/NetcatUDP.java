package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;



public class NetcatUDP {

    public static final int BUFFER_SIZE = 1024;

    private static void usage(){
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException {
        if (args.length!=3){
            usage();
            return;
        }

        InetSocketAddress server = new InetSocketAddress(args[0],Integer.parseInt(args[1]));
        Charset cs = Charset.forName(args[2]);
        ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);

        DatagramChannel dc = DatagramChannel.open();
        dc.bind(null);
        try (Scanner scan = new Scanner(System.in);){
            while(scan.hasNextLine()){
                String line = scan.nextLine();
                // Send Data
                dc.send(cs.encode(line), server);

                bb.clear();
                // Receive data
                InetSocketAddress exp = (InetSocketAddress) dc.receive(bb);
                bb.flip();
                int size = bb.remaining();
                CharBuffer response = cs.decode(bb);
                System.out.println("Received " + size + " bytes from " + exp.getHostString());
                System.out.println("String : " + response.toString());
            }
        }
        dc.close();
    }
}
