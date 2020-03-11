package fr.upem.net.buffers;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ReadFileWithEncoding {
    private final static int BUFFER_SIZE = 1024;

    private static void usage(){
        System.out.println("Usage: ReadFileWithEncoding charset filename");
    }

    private static String stringFromFile(Charset cs, Path path) throws IOException {

        try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ) ) {
            long size = fc.size();
            ByteBuffer bb = ByteBuffer.allocate((int) size);
            while ( bb.hasRemaining() ) {
                fc.read(bb);
            }
            bb.flip();
            return cs.decode(bb).toString();
        } catch ( IOException e ) {
            System.out.println("Unable to open file " + path.toString());
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length!=2){
            usage();
            return;
        }
        Charset cs=Charset.forName(args[0]);
        Path path=Paths.get(args[1]);
        System.out.print(stringFromFile(cs,path));
    }


}
