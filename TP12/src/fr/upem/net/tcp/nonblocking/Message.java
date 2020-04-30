package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Message {
    private final static Charset UTF8 = StandardCharsets.UTF_8;
    private String login;
    private int loginSize;
    private String message;
    private int messageSize;

    public ByteBuffer getLoginBytes() {
        return UTF8.encode(login);
    }

    public ByteBuffer getMessageBytes() {
        return UTF8.encode(message);
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getLoginSize() {
        return loginSize;
    }

    public void setLoginSize(int loginSize) {
        this.loginSize = loginSize;
    }

    public int getMessageSize() {
        return messageSize;
    }

    public void setMessageSize(int messageSize) {
        this.messageSize = messageSize;
    }
}
