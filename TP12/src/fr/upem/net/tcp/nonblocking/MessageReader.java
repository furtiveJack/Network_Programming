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
        if (state == State.WAITING_LOGIN) {
            var status = reader.process(bb);
            if (status != ProcessStatus.DONE) {
                return status;
            }
            message.setLogin(reader.get());
            message.setLoginSize(reader.getStringSize());
            reader.reset();
//            System.out.println("Parsed login : " + message.getLogin() + " SIZE : " + message.getLoginSize());
            state = State.WAITING_TEXT;
        }
        if (state == State.WAITING_TEXT) {
            var status = reader.process(bb);
            if (status != ProcessStatus.DONE) {
                return status;
            }
            message.setText(reader.get());
            message.setTextSize(reader.getStringSize());
            reader.reset();
//            System.out.println("Parsed text : " + message.getText() + " SIZE : " + message.getTextSize());
            state = State.DONE;
        }
        if (state == State.DONE) {
            return ProcessStatus.DONE;
        }
        return ProcessStatus.ERROR; // this shouldn't happen
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
