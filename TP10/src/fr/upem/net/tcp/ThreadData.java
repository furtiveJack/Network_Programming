package fr.upem.net.tcp;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class ThreadData {
    private final Object lock = new Object();
    private SocketChannel client;
    private long lastAction;

    public ThreadData() {
        lastAction = System.currentTimeMillis();
    }

    public void setSocketChannel(SocketChannel client) {
        Objects.requireNonNull(client);
        synchronized (lock) {
            this.client = client;
            this.lastAction = System.currentTimeMillis();
        }
    }

    public void tick() {
        synchronized (lock) {
            lastAction = System.currentTimeMillis();
        }
    }

    public boolean closeIfInactive(long timeout) {
        synchronized (lock) {
            if (this.client == null) {
                return false;
            }
            if (System.currentTimeMillis() - lastAction >= timeout) {
                System.out.println("*** Closing client ***");
                close();
                return true;
            }
            return false;
        }
    }

    public void close() {
        synchronized (lock) {
            try {
                if (this.client != null) {
                    this.client.close();
                    this.client = null;
                }
            } catch (IOException ioe) {
                // Do nothing
            }
        }
    }


}
