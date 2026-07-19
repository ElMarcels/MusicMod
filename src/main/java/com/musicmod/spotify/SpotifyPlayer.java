package com.musicmod.spotify;

import com.musicmod.MusicMod;
import com.musicmod.audio.MusicPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentTrack;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.requests.data.player.*;

import java.util.concurrent.CompletableFuture;

public class SpotifyPlayer {
    private final SpotifyAuthManager auth;
    private String activeDeviceId;
    private SpotifyTrackInfo currentTrack;
    private boolean playing = false;
    private int volumePercent = 50;

    public SpotifyPlayer(SpotifyAuthManager auth) {
        this.auth = auth;
    }

    public CompletableFuture<Boolean> play() {
        return ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            StartResumeUsersPlaybackRequest request = api.startResumeUsersPlayback().build();
            request.execute();
            playing = true;
            fetchCurrentTrack();
            return true;
        });
    }

    public CompletableFuture<Boolean> pause() {
        return ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            PauseUsersPlaybackRequest request = api.pauseUsersPlayback().build();
            request.execute();
            playing = false;
            return true;
        });
    }

    public CompletableFuture<Boolean> nextTrack() {
        return ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            SkipUsersPlaybackToNextTrackRequest request = api.skipUsersPlaybackToNextTrack().build();
            request.execute();
            fetchCurrentTrack();
            return true;
        });
    }

    public CompletableFuture<Boolean> previousTrack() {
        return ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            SkipUsersPlaybackToPreviousTrackRequest request = api.skipUsersPlaybackToPreviousTrack().build();
            request.execute();
            fetchCurrentTrack();
            return true;
        });
    }

    public CompletableFuture<Boolean> setVolume(int percent) {
        return ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            SetVolumeForUsersPlaybackRequest request = api.setVolumeForUsersPlayback(percent).build();
            request.execute();
            volumePercent = percent;
            return true;
        });
    }

    public CompletableFuture<Boolean> searchAndPlay(String query) {
        return ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            var searchRequest = api.searchItem(query, "track").limit(1).build();
            var searchResult = searchRequest.execute();
            if (searchResult.getTracks().getItems().length == 0) {
                sendMessage("§e[Música] No se encontró: " + query);
                return false;
            }
            String trackUri = searchResult.getTracks().getItems()[0].getUri();
            StartResumeUsersPlaybackRequest request = api.startResumeUsersPlayback()
                .uris(new String[]{trackUri}).build();
            request.execute();
            playing = true;
            fetchCurrentTrack();
            sendMessage("§a[Música] Reproduciendo: §f" + searchResult.getTracks().getItems()[0].getName());
            return true;
        });
    }

    public CompletableFuture<Boolean> playPlaylist(String playlistName) {
        return ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            var searchRequest = api.searchItem(playlistName, "playlist").limit(1).build();
            var searchResult = searchRequest.execute();
            if (searchResult.getPlaylists().getItems().length == 0) {
                sendMessage("§e[Música] No se encontró playlist: " + playlistName);
                return false;
            }
            String playlistUri = searchResult.getPlaylists().getItems()[0].getUri();
            StartResumeUsersPlaybackRequest request = api.startResumeUsersPlayback()
                .contextUri(playlistUri).build();
            request.execute();
            playing = true;
            fetchCurrentTrack();
            sendMessage("§a[Música] Reproduciendo playlist: §f" + searchResult.getPlaylists().getItems()[0].getName());
            return true;
        });
    }

    public void fetchCurrentTrack() {
        try {
            SpotifyApi api = auth.getApi();
            GetUsersCurrentlyPlayingTrackRequest request = api.getUsersCurrentlyPlayingTrack().build();
            CurrentTrack track = request.execute();

            if (track != null && track.getItem() != null) {
                String artists = String.join(", ", track.getItem().getArtists());
                currentTrack = new SpotifyTrackInfo(
                    track.getItem().getName(),
                    artists,
                    track.getItem().getAlbum().getName(),
                    track.getItem().getDurationMs(),
                    track.getItem().getExternalUrls().get("spotify"),
                    track.getItem().getAlbum().getImages().length > 0
                        ? track.getItem().getAlbum().getImages()[0].getUrl() : null
                );
                playing = track.getIs_playing();

                if (MusicMod.getInstance().getConfig().showHud && MinecraftClient.getInstance().player != null) {
                    sendMessage("§a♫ " + currentTrack.name + " §7- " + currentTrack.artists);
                }
            }
        } catch (Exception e) {
            MusicMod.LOGGER.warn("Failed to fetch current Spotify track", e);
        }
    }

    public CompletableFuture<Boolean> listDevices() {
        return ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            var request = api.getUsersAvailableDevices().build();
            Device[] devices = request.execute();
            if (devices.length == 0) {
                sendMessage("§c[Música] No hay dispositivos activos. Abre Spotify en algún dispositivo.");
                return false;
            }
            sendMessage("§a[Música] Dispositivos disponibles:");
            for (Device d : devices) {
                String active = d.getIs_active() ? " §a(activo)" : "";
                sendMessage(" §7- " + d.getName() + active);
                if (d.getIs_active()) activeDeviceId = d.getId();
            }
            return true;
        });
    }

    private CompletableFuture<Boolean> ensureToken(SpotifyAction action) {
        if (!auth.isAuthenticated()) {
            sendMessage("§c[Música] No autenticado. Usa el botón 'Login Spotify' en la GUI.");
            return CompletableFuture.completedFuture(false);
        }
        try {
            action.execute();
            return CompletableFuture.completedFuture(true);
        } catch (se.michaelthelin.spotify.exceptions.SpotifyWebApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                if (auth.refreshTokenIfNeeded()) {
                    try {
                        action.execute();
                        return CompletableFuture.completedFuture(true);
                    } catch (Exception e2) {
                        MusicMod.LOGGER.error("Spotify action failed after token refresh", e2);
                    }
                }
            }
            MusicMod.LOGGER.error("Spotify action failed", e);
            sendMessage("§c[Música] Error de Spotify: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        } catch (Exception e) {
            MusicMod.LOGGER.error("Spotify action failed", e);
            sendMessage("§c[Música] Error: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    private void sendMessage(String msg) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(msg), false);
        }
    }

    public boolean isPlaying() { return playing; }
    public SpotifyTrackInfo getCurrentTrack() { return currentTrack; }
    public int getVolumePercent() { return volumePercent; }

    @FunctionalInterface
    private interface SpotifyAction {
        void execute() throws Exception;
    }

    public static class SpotifyTrackInfo {
        public final String name;
        public final String artists;
        public final String album;
        public final int durationMs;
        public final String url;
        public final String imageUrl;

        public SpotifyTrackInfo(String name, String artists, String album, int durationMs, String url, String imageUrl) {
            this.name = name;
            this.artists = artists;
            this.album = album;
            this.durationMs = durationMs;
            this.url = url;
            this.imageUrl = imageUrl;
        }
    }
}
