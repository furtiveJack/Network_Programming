package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {
    private enum State {DONE, WAITING_SIZE, WAITING_TEXT, ERROR};

    private final static int BUFFER_SIZE = 1024;
    private final static Charset UTF8 = StandardCharsets.UTF_8;
    private final ByteBuffer internalbb = ByteBuffer.allocate(BUFFER_SIZE); // write-mode
    private final IntReader sizeReader = new IntReader();

    private State state = State.WAITING_SIZE;
    private String text;
    private int dataSize;

    private Reader.ProcessStatus readStringSize(ByteBuffer bb) {
        var status = sizeReader.process(bb);
        if (status == ProcessStatus.DONE) {
            dataSize = sizeReader.get();
            sizeReader.reset();
            state = State.WAITING_TEXT;
        }
        return status;
    }

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if (state == State.WAITING_SIZE) {
            var status = readStringSize(bb);
            if (status == ProcessStatus.REFILL || status == ProcessStatus.ERROR) {
                return status;
            }
            if (dataSize > BUFFER_SIZE || dataSize < 0) {
                return ProcessStatus.ERROR;
            }
            internalbb.limit(dataSize);
        }
        bb.flip();
        try {
            if (bb.remaining() <= internalbb.remaining()) {
                internalbb.put(bb);
            }
            else {
                var oldLimit = bb.limit();
                bb.limit(internalbb.remaining());
                internalbb.put(bb);
                bb.limit(oldLimit);
            }
        } finally {
            bb.compact();
        }
        if (internalbb.hasRemaining()) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        internalbb.flip();
        text = UTF8.decode(internalbb).toString();
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalArgumentException();
        }
        return text;
    }

    public int getStringSize() {
        if (state != State.DONE) {
            throw new IllegalArgumentException();
        }
        return dataSize;
    }

    @Override
    public void reset() {
        state = State.WAITING_SIZE;
        internalbb.clear();
        sizeReader.reset();
    }
}
