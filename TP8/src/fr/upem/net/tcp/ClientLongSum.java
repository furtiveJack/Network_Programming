package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ClientLongSum {

    private static final int BUFFER_SIZE = 1024;

    private static ArrayList<Long> randomLongList(int size) {
        Random rng = new Random();
        ArrayList<Long> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(rng.nextLong());
        }
        return list;
    }

    private static boolean checkSum(List<Long> list, long response) {
        long sum = 0;
        for (long l : list)
            sum += l;
        return sum == response;
    }

    /**
     * Fill the workspace of the Bytebuffer with bytes read from sc.
     *
     * @param sc
     * @param bb
     * @return false if read returned -1 at some point and true otherwise
     * @throws IOException
     */
    static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
        while (bb.hasRemaining()) {
            if (sc.read(bb) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Write all the longs in list in BigEndian on the server
     * and read the long sent by the server and returns it
     * <p>
     * returns Optional.empty if the protocol is not followed by the server but no IOException is thrown
     *
     * @param sc
     * @param list
     * @return
     * @throws IOException
     */
    private static Optional<Long> requestSumForList(SocketChannel sc, List<Long> list) throws IOException {
        int nbOp = list.size();
        var sendBuff = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * nbOp);
        var recBuff = ByteBuffer.allocate(Long.BYTES);
        sendBuff.putInt(nbOp);
        list.forEach(sendBuff::putLong);
        sendBuff.flip();
        sc.write(sendBuff);
        if (!readFully(sc, recBuff)) {
            return Optional.empty();
        }
        recBuff.flip();
        return Optional.of(recBuff.getLong());
    }

    public static void main(String[] args) throws IOException {
        InetSocketAddress server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        try (SocketChannel sc = SocketChannel.open(server)) {
            for (int i = 0; i < 5; i++) {
                ArrayList<Long> list = randomLongList(50);

                Optional<Long> l = requestSumForList(sc, list);
                if (l.isEmpty()) {
                    System.err.println("Connection with server lost.");
                    return;
                }
                if (!checkSum(list, l.get())) {
                    System.err.println("Oups! Something wrong happens!");
                }
            }
            System.err.println("Everything seems ok");
        }
    }
}
