package com.musicmod.gui;

import com.musicmod.MusicMod;
import com.musicmod.audio.MusicPlayer;
import com.musicmod.config.ModConfig.SongEntry;
import com.musicmod.spotify.SpotifyPlayer.SpotifyTrackInfo;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class HudOverlay implements HudRenderCallback {
    private static final int PADDING = 4;

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (!MusicMod.getInstance().getConfig().showHud) return;
        if (!MusicMod.getInstance().getConfig().enabled) return;

        MusicPlayer player = MusicMod.getInstance().getMusicPlayer();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        int right = client.getWindow().getScaledWidth() - PADDING;
        int y = PADDING;
        boolean hasContent = false;

        // Spotify info
        if (player.getSource().equals("spotify") && player.isPlaying()) {
            var spotify = MusicMod.getInstance().getSpotifyPlayer();
            if (spotify != null) {
                SpotifyTrackInfo track = spotify.getCurrentTrack();
                if (track != null) {
                    String text = "♫ " + track.name + " - " + track.artists;
                    int width = client.textRenderer.getWidth(text);
                    context.fill(right - width - 4, y - 1, right + 2, y + 10, 0x80000000);
                    context.drawText(client.textRenderer, text, right - width, y + 1, 0x1DB954, false);
                    y += 12;
                    hasContent = true;
                } else {
                    String text = "♫ Spotify reproduciendo...";
                    int width = client.textRenderer.getWidth(text);
                    context.fill(right - width - 4, y - 1, right + 2, y + 10, 0x80000000);
                    context.drawText(client.textRenderer, text, right - width, y + 1, 0x1DB954, false);
                    y += 12;
                    hasContent = true;
                }
            }
        }

        // Local music info
        if (!player.getSource().equals("spotify") && player.isPlaying() && player.getCurrentSong() != null) {
            SongEntry current = player.getCurrentSong();
            String text = "♫ " + current.name;
            int width = client.textRenderer.getWidth(text);
            context.fill(right - width - 4, y - 1, right + 2, y + 10, 0x80000000);
            context.drawText(client.textRenderer, text, right - width, y + 1, 0xFFFFFF, false);
            y += 12;
            hasContent = true;
        }

        // Vote status
        if (player.isVoteActive()) {
            String voteText = "🗳 Vote: " + player.getVoteTimeLeft() + "s";
            int width = client.textRenderer.getWidth(voteText);
            context.fill(right - width - 4, y - 1, right + 2, y + 10, 0x80000000);
            context.drawText(client.textRenderer, voteText, right - width, y + 1, 0xFFFFAA, false);
            y += 12;
            hasContent = true;
        }

        // Voice listening indicator
        var voice = MusicMod.getInstance().getVoiceManager();
        if (voice != null && voice.isListening()) {
            String vText = "🎤 Escuchando...";
            int width = client.textRenderer.getWidth(vText);
            context.fill(right - width - 4, y - 1, right + 2, y + 10, 0x80000000);
            context.drawText(client.textRenderer, vText, right - width, y + 1, 0x00FFAA, false);
            hasContent = true;
        }

        // Spotify connection status
        var auth = MusicMod.getInstance().getSpotifyAuthManager();
        if (auth != null && auth.isAuthenticated() && !hasContent) {
            String sText = "♫ Spotify conectado";
            int width = client.textRenderer.getWidth(sText);
            context.fill(right - width - 4, y - 1, right + 2, y + 10, 0x80000000);
            context.drawText(client.textRenderer, sText, right - width, y + 1, 0x1DB954, false);
        }
    }
}
