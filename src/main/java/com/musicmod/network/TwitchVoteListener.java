package com.musicmod.network;

import com.musicmod.MusicMod;
import com.musicmod.audio.MusicPlayer;
import com.musicmod.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class TwitchVoteListener {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private String channel = "";
    private int reconnectAttempts = 0;

    public void connect(String channelName) {
        if (channelName == null || channelName.isBlank()) return;
        this.channel = channelName.toLowerCase();
        ModConfig config = MusicMod.getInstance().getConfig();
        if (!config.twitchIntegration) return;

        CompletableFuture.runAsync(() -> {
            try {
                socket = new Socket("irc.chat.twitch.tv", 6667);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("CAP REQ :twitch.tv/tags");
                out.println("PASS oauth:anonymous");
                out.println("NICK justinfan12345");
                out.println("JOIN #" + channel);

                connected = true;
                reconnectAttempts = 0;
                MusicMod.LOGGER.info("Connected to Twitch chat: #{}", channel);

                String line;
                while (connected && (line = in.readLine()) != null) {
                    handleMessage(line);
                }
            } catch (IOException e) {
                MusicMod.LOGGER.warn("Twitch connection failed (attempt {}): {}", reconnectAttempts, e.getMessage());
                connected = false;
            }
        });
    }

    private void handleMessage(String line) {
        if (line.startsWith("PING")) {
            out.println("PONG " + line.substring(5));
            return;
        }
        if (!MusicMod.getInstance().getMusicPlayer().isVoteActive()) return;

        String[] parts = line.split("!");
        if (parts.length < 2) return;
        String username = parts[0].substring(1);

        int msgIndex = line.indexOf("PRIVMSG");
        if (msgIndex == -1) return;
        String message = line.substring(line.indexOf(":", msgIndex) + 1).trim().toLowerCase();

        ModConfig config = MusicMod.getInstance().getConfig();
        if (message.startsWith("!votar") || message.startsWith("!vote")) {
            String[] msgParts = message.split(" ");
            if (msgParts.length >= 2) {
                try {
                    int songIndex = Integer.parseInt(msgParts[1]) - 1;
                    if (songIndex >= 0 && songIndex < config.playlist.size()) {
                        MusicMod.getInstance().getMusicPlayer().castVote(username, songIndex);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            MusicMod.LOGGER.error("Error disconnecting from Twitch", e);
        }
    }

    public boolean isConnected() { return connected; }
    public String getChannel() { return channel; }
}
