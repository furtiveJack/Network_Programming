package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerLongSum {

    private static class SumData {
        private final long nbOpExpected;
        private long partialSum;
        private final SafeBitSet received;

        public SumData(long nbOp) {
            if (nbOp <=  0) {
                throw new IllegalArgumentException("Number of operandes must be strictly positive");
            }
            nbOpExpected = nbOp;
            partialSum = 0;
            received = new SafeBitSet(nbOpExpected);
        }

        public SumData update(long idPosOper, long opValue) {
            if (! received.get(idPosOper)) {
                received.set(idPosOper);
                partialSum += opValue;
            }
            return this;
        }

        private boolean sumCompleted() {
            return received.getCardinality() == nbOpExpected;
        }

        public long getSum() {
            if (sumCompleted()) {
                return partialSum;
            }
            return -1;
        }
    }

    // InetSocketAddress= client
    // Long = session id for this client
    // SumData = state of the sum at a given time
    private final HashMap<InetSocketAddress, HashMap<Long, SumData>> map;
    private final DatagramChannel dc;
    private static final Logger logger = Logger.getLogger(ServerLongSum.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;
    private static final byte OP = 1;
    private static final byte ACK = 2;
    private static final byte RES = 3;
    private final ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
    //private final ByteBuffer buffSend = ByteBuffer.allocateDirect(BUFFER_SIZE);

    public ServerLongSum(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(7777));

        map = new HashMap<>();
        logger.info("ServerBetterUpperCaseUDP started on port " + port);
    }

    private void updateClientData(InetSocketAddress exp, long sessionId, long idPosOper, long totalOper, long opValue) {
        map.putIfAbsent(exp, new HashMap<>());
        map.get(exp).compute(sessionId, (k, v) ->
                (v == null)
                        ? new SumData(totalOper).update(idPosOper, opValue)
                        : v.update(idPosOper, opValue));
    }

    private void sendAck(InetSocketAddress exp, long sessionId, long idPosOper) throws IOException {
        ByteBuffer buffSend = ByteBuffer.allocate(BUFFER_SIZE);
        buffSend.clear();
        buffSend.put(ACK);
        buffSend.putLong(sessionId);
        buffSend.putLong(idPosOper);
        buffSend.flip();
        logger.info("\nSending ACK for client : " + exp.toString() + " on session : " + sessionId + " for op : " + idPosOper);
        dc.send(buffSend, exp);
    }

    private void sendRes(InetSocketAddress exp, long sessionId) throws IOException {
        long sum = map.get(exp).get(sessionId).getSum();
        if (sum != -1) {
            ByteBuffer buffSend = ByteBuffer.allocate(BUFFER_SIZE);
            buffSend.clear();
            buffSend.put(RES);
            buffSend.putLong(sessionId);
            buffSend.putLong(sum);
            buffSend.flip();
            logger.info("\n Sending RES for client : " + exp.toString() + " on session :" + sessionId
                            + " with res : " + sum);
            dc.send(buffSend, exp);
        }
    }
    private void sendResponse(InetSocketAddress exp, long sessionId, long idPosOper) throws IOException {
        sendAck(exp, sessionId, idPosOper);
        sendRes(exp, sessionId);
    }

    private void dealWithRequest(InetSocketAddress exp) throws IOException {
        buff.flip();
        byte opType = buff.get();
        if (opType != OP) {
            logger.warning("Received requests from client: " + exp.toString() + " with OPTYPE: "
                            + opType + ".\nIgnoring it");
            return; // client requests must be with OP TYPE = 1
        }
        long sessionId = buff.getLong();
        long idPosOper = buff.getLong();
        long totalOper = buff.getLong();
        long opValue   = buff.getLong();

        updateClientData(exp, sessionId, idPosOper, totalOper, opValue);
        sendResponse(exp, sessionId, idPosOper);
    }

    public void serve() throws IOException {
        while (! Thread.interrupted()) {
            buff.clear();
            InetSocketAddress exp = (InetSocketAddress) dc.receive(buff);
            logger.log(Level.INFO, "Received " + buff.position() + " bytes from " + exp.toString());
            dealWithRequest(exp);
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerIdUpperCaseUDP port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerLongSum server;
        int port = Integer.parseInt(args[0]);
        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }
        try {
            server = new ServerLongSum(port);
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
        server.serve();
    }
}
