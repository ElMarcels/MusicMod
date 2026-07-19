package com.musicmod.spotify;

import com.musicmod.MusicMod;
import com.musicmod.audio.MusicPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.player.*;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;

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

    public void play() {
        ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            StartResumeUsersPlaybackRequest request = api.startResumeUsersPlayback().build();
            request.execute();
            playing = true;
            fetchCurrentTrack();
        });
    }

    public void pause() {
        ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            PauseUsersPlaybackRequest request = api.pauseUsersPlayback().build();
            request.execute();
            playing = false;
        });
    }

    public void nextTrack() {
        ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            SkipUsersPlaybackToNextTrackRequest request = api.skipUsersPlaybackToNextTrack().build();
            request.execute();
            fetchCurrentTrack();
        });
    }

    public void previousTrack() {
        ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            SkipUsersPlaybackToPreviousTrackRequest request = api.skipUsersPlaybackToPreviousTrack().build();
            request.execute();
            fetchCurrentTrack();
        });
    }

    public void setVolume(int percent) {
        ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            SetVolumeForUsersPlaybackRequest request = api.setVolumeForUsersPlayback(percent).build();
            request.execute();
            volumePercent = percent;
        });
    }

    public void searchAndPlay(String query) {
        ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            var searchRequest = api.searchItem(query, "track").limit(1).build();
            var searchResult = searchRequest.execute();
            if (searchResult.getTracks().getItems().length == 0) {
                sendMessage("§e[Música] No se encontró: " + query);
                return;
            }
            Track trackItem = searchResult.getTracks().getItems()[0];
            String trackUri = trackItem.getUri();
            StartResumeUsersPlaybackRequest request = api.startResumeUsersPlayback()
                .uris(trackUri).build();
            request.execute();
            playing = true;
            fetchCurrentTrack();
            sendMessage("§a[Música] Reproduciendo: §f" + trackItem.getName());
        });
    }

    public void playPlaylist(String playlistName) {
        ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            var searchRequest = api.searchItem(playlistName, "playlist").limit(1).build();
            var searchResult = searchRequest.execute();
            if (searchResult.getPlaylists().getItems().length == 0) {
                sendMessage("§e[Música] No se encontró playlist: " + playlistName);
                return;
            }
            String playlistUri = searchResult.getPlaylists().getItems()[0].getUri();
            StartResumeUsersPlaybackRequest request = api.startResumeUsersPlayback()
                .contextUri(playlistUri).build();
            request.execute();
            playing = true;
            fetchCurrentTrack();
            sendMessage("§a[Música] Reproduciendo playlist: §f" + searchResult.getPlaylists().getItems()[0].getName());
        });
    }

    public void fetchCurrentTrack() {
        try {
            SpotifyApi api = auth.getApi();
            var request = api.getUsersCurrentlyPlayingTrack().build();
            CurrentlyPlaying track = request.execute();

            if (track != null && track.getItem() != null) {
                IPlaylistItem item = track.getItem();
                Track trackItem = (Track) item;
                String artists = String.join(", ", trackItem.getArtists());
                currentTrack = new SpotifyTrackInfo(
                    trackItem.getName(),
                    artists,
                    trackItem.getAlbum().getName(),
                    trackItem.getDurationMs(),
                    trackItem.getExternalUrls().get("spotify"),
                    trackItem.getAlbum().getImages().length > 0
                        ? trackItem.getAlbum().getImages()[0].getUrl() : null
                );
                playing = track.getIsPlaying();

                if (MusicMod.getInstance().getConfig().showHud && MinecraftClient.getInstance().player != null) {
                    sendMessage("§a♫ " + currentTrack.name + " §7- " + currentTrack.artists);
                }
            }
        } catch (Exception e) {
            MusicMod.LOGGER.warn("Failed to fetch current Spotify track", e);
        }
    }

    public void listDevices() {
        ensureToken(() -> {
            SpotifyApi api = auth.getApi();
            var request = api.getUsersAvailableDevices().build();
            Device[] devices = request.execute();
            if (devices.length == 0) {
                sendMessage("§c[Música] No hay dispositivos activos. Abre Spotify en algún dispositivo.");
                return;
            }
            sendMessage("§a[Música] Dispositivos disponibles:");
            for (Device d : devices) {
                String active = d.getIsActive() ? " §a(activo)" : "";
                sendMessage(" §7- " + d.getName() + active);
                if (d.getIsActive()) activeDeviceId = d.getId();
            }
        });
    }

    private void ensureToken(SpotifyAction action) {
        if (!auth.isAuthenticated()) {
            sendMessage("§c[Música] No autenticado. Usa el botón 'Login Spotify' en la GUI.");
            return;
        }
        try {
            action.execute();
        } catch (se.michaelthelin.spotify.exceptions.SpotifyWebApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                if (auth.refreshTokenIfNeeded()) {
                    try {
                        action.execute();
                        return;
                    } catch (Exception e2) {
                        MusicMod.LOGGER.error("Spotify action failed after token refresh", e2);
                    }
                }
            }
            MusicMod.LOGGER.error("Spotify action failed", e);
            sendMessage("§c[Música] Error de Spotify: " + e.getMessage());
        } catch (Exception e) {
            MusicMod.LOGGER.error("Spotify action failed", e);
            sendMessage("§c[Música] Error: " + e.getMessage());
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
