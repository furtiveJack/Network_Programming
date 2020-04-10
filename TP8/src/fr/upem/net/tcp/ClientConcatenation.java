package fr.upem.net.tcp;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Scanner;

public class ClientConcatenation {
    private final static int BUFFER_SIZE = 1024;
    private final SocketAddress serverAddress;
    private final LinkedList<String> lines;
    public static final Charset UTF8 = StandardCharsets.UTF_8;

    public ClientConcatenation(String host, int port) {
        serverAddress = new InetSocketAddress(host, port);
        lines = new LinkedList<>();
    }

    static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
        while (bb.hasRemaining()) {
            if (sc.read(bb) == -1) {
                return false;
            }
        }
        return true;
    }

    private void getInputs() {
        System.out.println("Enter lines to concat: ");
        try (var scanner = new Scanner(System.in)) {
            var line = scanner.nextLine();
            while (line.length() != 0) {
                lines.add(line);
                line = scanner.nextLine();
            }
            System.out.println("End of input");
        }
    }

    private void sendLines(SocketChannel sc) throws IOException {
        getInputs();
        var buffers = new ByteBuffer[1 + 2*lines.size()];
        buffers[0] = ByteBuffer.allocate(Integer.BYTES).putInt(lines.size()).flip(); //writing the number of strings
        var i = 1;
        for (String line : lines) {
            var buff = UTF8.encode(line);
            buffers[i] = ByteBuffer.allocate(Integer.BYTES).putInt(buff.remaining()).flip(); //writing the size of the string in UTF8
            ++i;
            buffers[i] = buff; //writing the string in UTF8
            ++i;
        }
        sc.write(buffers);
    }

    public Optional<String> requestConcat() throws IOException {
        try (var sc = SocketChannel.open(serverAddress)) {
            sendLines(sc);
            var buffSize = ByteBuffer.allocate(Integer.BYTES); //buffer to get the size of the concatenated strings
            if (! readFully(sc, buffSize)) {
                return Optional.empty();
            }
            var size = buffSize.flip().getInt();    // reading the size
            if (size <= 0) {
                return Optional.empty();
            }
            var buffString = ByteBuffer.allocate(size); // buffer to get the concatenated strings
            if (! readFully(sc, buffString)) {
                return Optional.empty();
            }
            return Optional.of(UTF8.decode(buffString).toString());
        } catch (ConnectException e) {
            System.err.println("Can't reach server : " + e.getMessage());
            return Optional.empty();
        }
    }
    private static void usage() {
        System.out.println("ClientConcatenation <host> <port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        var client = new ClientConcatenation(host, port);
        Optional<String> response = client.requestConcat();
        if (response.isEmpty()) {
            return;
        }
        System.out.println("\nGot response : " + response.get());
    }
}

