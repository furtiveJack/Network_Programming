package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Message {
    private final static Charset UTF8 = StandardCharsets.UTF_8;
    private String login;
    private int loginSize;
    private String text;
    private int textSize;

    public ByteBuffer getLoginBytes() {
        return UTF8.encode(login);
    }

    public ByteBuffer getMessageBytes() {
        return UTF8.encode(text);
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setLoginSize(int loginSize) {
        this.loginSize = loginSize;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
    }

    public String getLogin() {
        return login;

    }
    public int getLoginSize() {
        return loginSize;
    }

    public String getText() {
        return text;
    }

    public int getTextSize() {
        return textSize;
    }
}
