package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {

    private enum State {DONE, WAITING_LOGIN, WAITING_TEXT, ERROR};

    private final StringReader reader = new StringReader();
    private State state = State.WAITING_LOGIN;
    private Message message = new Message();

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        else if (state == State.WAITING_LOGIN) {
            var status = reader.process(bb);
            if (status == ProcessStatus.DONE) {
                message.setLogin(reader.get());
                message.setLoginSize(reader.getStringSize());
                reader.reset();
                state = State.WAITING_TEXT;
                return ProcessStatus.REFILL;
            }
            else {
                return status;
            }
        }
        else { // state == WAITING_TEXT
            var status = reader.process(bb);
            if (status == ProcessStatus.DONE) {
                message.setMessage(reader.get());
                message.setMessageSize(reader.getStringSize());
                reader.reset();
                state = State.DONE;
                return ProcessStatus.DONE;
            }
            else {
                return status;
            }
        }
    }

    @Override
    public Message get() {
        if (state != State.DONE) {
            throw new IllegalArgumentException();
        }
        return message;
    }

    @Override
    public void reset() {
        state = State.WAITING_LOGIN;
        reader.reset();
    }
}
