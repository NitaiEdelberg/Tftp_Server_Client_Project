package bgu.spl.net.srv;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {
    ConcurrentHashMap<Integer, ConnectionHandler<T>> connections;

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        connections.putIfAbsent((Integer)connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if(connections.containsKey((Integer)connectionId)){
            connections.get((Integer)connectionId).send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        try {
            if(connections.containsKey((Integer)connectionId)) {
                ConnectionHandler<T> handler = connections.remove((Integer) connectionId);
                handler.close();
            }
        } catch (Exception ignore) {
        }
    }
}
