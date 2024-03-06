package bgu.spl.net.srv;

import java.io.IOException;

public class ConnectionsImpl<T> implements Connections<T> {
    int connectionId;
    ConnectionHandler<T> handler;

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        this.connectionId = connectionId;
        this.handler = handler;
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if (this.connectionId != connectionId) {
            return false;
        } else {
            handler.send(msg);
            return true;
        }
    }

    @Override
    public void disconnect(int connectionId) {
        try {
            handler.close();
        } catch (IOException ignore) {
        }
    }
}
