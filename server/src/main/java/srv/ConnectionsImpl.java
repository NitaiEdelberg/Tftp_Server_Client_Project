package srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {
    ConcurrentHashMap<Integer, ConnectionHandler<T>> connections = new ConcurrentHashMap<>();

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        System.out.println("Adding to hashmap");
        connections.putIfAbsent((Integer)connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        System.out.println("Sending msg to " + connectionId);
        if(connections.containsKey(connectionId)){
            connections.get(connectionId).send(msg);
            return true;
        }
        System.out.println("Doesn't exists");
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
