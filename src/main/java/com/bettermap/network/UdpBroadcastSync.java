package com.bettermap.network;

import com.bettermap.config.BetterMapConfig;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class UdpBroadcastSync {
    private final PlayerSyncManager syncManager;
    private final BetterMapConfig config;
    private DatagramSocket socket;
    private Thread listenerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public UdpBroadcastSync(PlayerSyncManager syncManager, BetterMapConfig config) {
        this.syncManager = syncManager;
        this.config = config;
    }

    public synchronized void start() {
        if (running.get()) return;
        running.set(true);

        try {
            int port = config.getLanPort();
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);

            listenerThread = new Thread(this::listenLoop, "BetterMap-UDP-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
        } catch (Exception e) {
            running.set(false);
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        listenerThread = null;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void listenLoop() {
        byte[] buffer = new byte[1024];
        while (running.get() && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handlePacket(msg);
            } catch (Exception e) {
                if (!running.get()) break;
            }
        }
    }

    private void handlePacket(String msg) {
        // Format: BM_LAN|room|uuid|name|x|y|z|yaw|pitch|dim|health
        if (!msg.startsWith("BM_LAN|")) return;

        try {
            String[] parts = msg.split("\\|");
            if (parts.length < 11) return;

            String room = parts[1];
            if (!room.equals(config.getRoomKey())) return;

            UUID uuid = UUID.fromString(parts[2]);
            if (uuid.equals(syncManager.getLocalPlayerUuid())) return;

            String name = parts[3];
            double x = Double.parseDouble(parts[4]);
            double y = Double.parseDouble(parts[5]);
            double z = Double.parseDouble(parts[6]);
            float yaw = Float.parseFloat(parts[7]);
            float pitch = Float.parseFloat(parts[8]);
            String dim = parts[9];
            float health = Float.parseFloat(parts[10]);

            syncManager.updateRemotePlayer(uuid, name, x, y, z, yaw, pitch, dim, health);
        } catch (Exception ignored) {}
    }

    public void sendPosition(UUID uuid, String name, double x, double y, double z, float yaw, float pitch, String dimension, float health) {
        if (!running.get() || socket == null || socket.isClosed()) return;

        try {
            String payload = String.format("BM_LAN|%s|%s|%s|%.2f|%.2f|%.2f|%.1f|%.1f|%s|%.1f",
                    config.getRoomKey(),
                    uuid.toString(),
                    name,
                    x, y, z, yaw, pitch, dimension, health);

            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, config.getLanPort());
            socket.send(packet);
        } catch (Exception ignored) {}
    }
}
