package com.jetpackjung.lan;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LAN lobby relay. The host is the local Android phone running this server.
 * Nine remote Android clients may join, making ten seats including the host.
 * Gameplay snapshots and actions are relayed between the host WebView and joining phones.
 */
public final class LanServer extends WebSocketServer {
    public static final int PORT = 28765;
    private final Map<WebSocket, String> peers = new ConcurrentHashMap<>();
    private final Map<WebSocket, String> peerIds = new ConcurrentHashMap<>();
    private final Map<WebSocket, Long> rateWindowStart = new ConcurrentHashMap<>();
    private final Map<WebSocket, Integer> rateWindowCount = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<String> hostPackets = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile String roomCode = "";

    public LanServer() { super(new InetSocketAddress("0.0.0.0", PORT)); }
    public void setRoomCode(String code) { roomCode = code; }
    public String getRoomCode() { return roomCode; }
    public int playerCount() { return peers.size() + 1; } // include host
    public String pollHostPacket() { return hostPackets.poll(); }
    public void sendToPeers(String message) { broadcast(message); }

    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Wait for a join packet before admitting the socket.
        conn.send("{\"type\":\"hello\",\"maxPlayers\":10}");
    }

    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = peers.remove(conn);
        String peerId = peerIds.remove(conn);
        rateWindowStart.remove(conn);
        rateWindowCount.remove(conn);
        if (name != null) {
            String left = "{\"type\":\"peer_left\",\"id\":\"" + safe(peerId == null ? "" : peerId) + "\",\"name\":\"" + safe(name) + "\"}";
            // The host is not a WebSocket peer, so explicitly queue disconnects
            // for its WebView bridge as well as notifying the remaining clients.
            if (hostPackets.size() < 512) hostPackets.offer(left);
            broadcast("{\"type\":\"peer_count\",\"players\":" + playerCount() + "}");
            broadcast(left);
        }
    }

    @Override public void onMessage(WebSocket conn, String message) {
        if (message == null || message.length() > 8192) {
            conn.close(1009, "Message too large");
            return;
        }
        if (!peers.containsKey(conn)) {
            String code = field(message, "code");
            String name = field(message, "name");
            if (!"join".equals(field(message, "type")) || !roomCode.equalsIgnoreCase(code)) {
                conn.send("{\"type\":\"error\",\"message\":\"Invalid room code\"}");
                conn.close(1008, "Invalid room code");
                return;
            }
            if (peers.size() >= 9) {
                conn.send("{\"type\":\"error\",\"message\":\"Room full (10 players maximum)\"}");
                conn.close(1008, "Room full");
                return;
            }
            if (name == null || name.trim().isEmpty()) name = "Player";
            name = name.trim().replaceAll("[\\p{Cntrl}]", "");
            if (name.isEmpty()) name = "Player";
            name = name.substring(0, Math.min(12, name.length()));
            peers.put(conn, name);
            conn.send("{\"type\":\"welcome\",\"room\":\"" + roomCode + "\",\"name\":\"" + safe(name) + "\",\"maxPlayers\":10,\"players\":" + playerCount() + "}");
            broadcastExcept(conn, "{\"type\":\"peer_joined\",\"name\":\"" + safe(name) + "\",\"players\":" + playerCount() + "}");
            broadcast("{\"type\":\"peer_count\",\"players\":" + playerCount() + "}");
            return;
        }
        // Bound traffic from each phone so a stuck client cannot flood the host.
        long now = System.nanoTime();
        Long window = rateWindowStart.get(conn);
        if (window == null || now - window >= 1_000_000_000L) {
            rateWindowStart.put(conn, now);
            rateWindowCount.put(conn, 1);
        } else {
            int count = rateWindowCount.merge(conn, 1, Integer::sum);
            if (count > 45) {
                conn.close(1008, "Packet rate exceeded");
                return;
            }
        }

        // Relay admitted client packets to the other phones and queue them for the host game.
        String packetType = field(message, "type");
        if ("state".equals(packetType)) {
            String id = field(message, "id");
            if (id != null && id.length() <= 40) peerIds.put(conn, id);
        }
        if (("state".equals(packetType) || "action".equals(packetType) || "match_state".equals(packetType) || "match_end".equals(packetType)) && hostPackets.size() < 512) {
            hostPackets.offer(message);
        }
        broadcastExcept(conn, message);
    }

    @Override public void onError(WebSocket conn, Exception ex) { }
    @Override public void onStart() { }

    private void broadcast(String message) {
        for (WebSocket peer : peers.keySet()) if (peer.isOpen()) peer.send(message);
    }
    private void broadcastExcept(WebSocket source, String message) {
        for (WebSocket peer : peers.keySet()) if (peer != source && peer.isOpen()) peer.send(message);
    }
    private String field(String json, String key) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(json);
            if (m.find()) return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception ignored) { }
        return null;
    }
    private String safe(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
