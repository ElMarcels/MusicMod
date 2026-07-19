package com.musicmod.spotify;

import com.musicmod.MusicMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.google.gson.JsonArray;

import java.util.Arrays;
import java.util.stream.Collectors;

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
            auth.getApi().startResumeUsersPlayback().build().execute();
            playing = true;
            fetchCurrentTrack();
            return null;
        });
    }

    public void pause() {
        ensureToken(() -> {
            auth.getApi().pauseUsersPlayback().build().execute();
            playing = false;
            return null;
        });
    }

    public void nextTrack() {
        ensureToken(() -> {
            auth.getApi().skipUsersPlaybackToNextTrack().build().execute();
            fetchCurrentTrack();
            return null;
        });
    }

    public void previousTrack() {
        ensureToken(() -> {
            auth.getApi().skipUsersPlaybackToPreviousTrack().build().execute();
            fetchCurrentTrack();
            return null;
        });
    }

    public void setVolume(int percent) {
        ensureToken(() -> {
            auth.getApi().setVolumeForUsersPlayback(percent).build().execute();
            volumePercent = percent;
            return null;
        });
    }

    @SuppressWarnings("deprecation")
    public void searchAndPlay(String query) {
        ensureToken(() -> {
            var search = auth.getApi().searchItem(query, "track").limit(1).build().execute();
            if (search.getTracks().getItems().length == 0) {
                sendMessage("§e[Música] No se encontró: " + query);
                return null;
            }
            Track trackItem = search.getTracks().getItems()[0];
            JsonArray uris = new JsonArray();
            uris.add(trackItem.getUri());
            auth.getApi().startResumeUsersPlayback().uris(uris).build().execute();
            playing = true;
            sendMessage("§a[Música] Reproduciendo: §f" + trackItem.getName());
            fetchCurrentTrack();
            return null;
        });
    }

    public void playPlaylist(String playlistName) {
        ensureToken(() -> {
            var search = auth.getApi().searchItem(playlistName, "playlist").limit(1).build().execute();
            if (search.getPlaylists().getItems().length == 0) {
                sendMessage("§e[Música] No se encontró playlist: " + playlistName);
                return null;
            }
            String pu = search.getPlaylists().getItems()[0].getUri();
            auth.getApi().startResumeUsersPlayback().context_uri(pu).build().execute();
            playing = true;
            sendMessage("§a[Música] Reproduciendo playlist: " + search.getPlaylists().getItems()[0].getName());
            fetchCurrentTrack();
            return null;
        });
    }

    public void fetchCurrentTrack() {
        try {
            CurrentlyPlaying track = auth.getApi().getUsersCurrentlyPlayingTrack().build().execute();
            if (track != null && track.getItem() != null) {
                IPlaylistItem item = track.getItem();
                Track trackItem = (Track) item;
                String artists = Arrays.stream(trackItem.getArtists())
                    .map(ArtistSimplified::getName)
                    .collect(Collectors.joining(", "));
                currentTrack = new SpotifyTrackInfo(
                    trackItem.getName(),
                    artists,
                    trackItem.getAlbum().getName(),
                    trackItem.getDurationMs(),
                    trackItem.getExternalUrls().get("spotify"),
                    trackItem.getAlbum().getImages().length > 0
                            ? trackItem.getAlbum().getImages()[0].getUrl() : null
                );
                playing = track.getIs_playing();
            }
        } catch (Exception e) {
            MusicMod.LOGGER.warn("Failed to fetch current Spotify track", e);
        }
    }

    public void listDevices() {
        ensureToken(() -> {
            Device[] devices = auth.getApi().getUsersAvailableDevices().build().execute();
            if (devices.length == 0) {
                sendMessage("§c[Música] No hay dispositivos activos. Abre Spotify en algún dispositivo.");
                return null;
            }
            sendMessage("§a[Música] Dispositivos disponibles:");
            for (Device d : devices) {
                String active = d.getIs_active() ? " §a(activo)" : "";
                sendMessage(" §7- " + d.getName() + active);
                if (d.getIs_active()) activeDeviceId = d.getId();
            }
            return null;
        });
    }

    private void ensureToken(java.util.concurrent.Callable<Void> action) {
        if (!auth.isAuthenticated()) {
            sendMessage("§c[Música] No autenticado.");
            return;
        }
        try {
            action.call();
        } catch (Exception e) {
            if (e instanceof se.michaelthelin.spotify.exceptions.SpotifyWebApiException) {
                if (e.getMessage() != null && e.getMessage().contains("401")) {
                    if (auth.refreshTokenIfNeeded()) {
                        try { action.call(); return; } catch (Exception ignored) {}
                    }
                }
            }
            MusicMod.LOGGER.error("Spotify error", e);
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
