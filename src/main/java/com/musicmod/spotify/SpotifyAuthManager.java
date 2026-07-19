package com.musicmod.spotify;

import com.musicmod.MusicMod;
import com.musicmod.config.ModConfig;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import org.apache.hc.core5.net.URIBuilder;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

import java.awt.*;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class SpotifyAuthManager {
    private static final String CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID";
    private static final String CLIENT_SECRET = "YOUR_SPOTIFY_CLIENT_SECRET";
    private static final URI REDIRECT_URI = SpotifyHttpManager.makeUri("http://localhost:8888/callback");
    private static final int LOCAL_PORT = 8888;

    private SpotifyApi spotifyApi;
    private HttpServer server;
    private boolean authenticated = false;
    private String accessToken;
    private String refreshToken;

    public SpotifyAuthManager() {
        ModConfig.SpotifyConfig cfg = MusicMod.getInstance().getConfig().spotify;
        this.accessToken = cfg.accessToken;
        this.refreshToken = cfg.refreshToken;
        if (accessToken != null && !accessToken.isEmpty()) {
            spotifyApi = buildApi();
            authenticated = true;
        }
    }

    private SpotifyApi buildApi() {
        return new SpotifyApi.Builder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRedirectUri(REDIRECT_URI)
            .setAccessToken(accessToken)
            .setRefreshToken(refreshToken)
            .build();
    }

    public void setTokens(String access, String refresh) {
        this.accessToken = access;
        this.refreshToken = refresh;
        this.spotifyApi = buildApi();
        this.authenticated = true;

        ModConfig.SpotifyConfig cfg = MusicMod.getInstance().getConfig().spotify;
        cfg.accessToken = access;
        cfg.refreshToken = refresh;
        MusicMod.getInstance().getConfig().save();
    }

    public CompletableFuture<Boolean> login() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (authenticated) {
            future.complete(true);
            return future;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(LOCAL_PORT), 0);
            server.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String code = "";
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2 && pair[0].equals("code")) {
                            code = pair[1];
                        }
                    }
                }

                String response;
                if (!code.isEmpty()) {
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    response = "<html><body><h1>✓ Autenticado</h1><p>Ya puedes cerrar esta pestaña y volver a Minecraft.</p></body></html>";
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }

                    exchangeClose();
                    completeAuth(code, future);
                } else {
                    response = "<html><body><h1>Error de autenticación</h1></body></html>";
                    exchange.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                    future.complete(false);
                }
            });
            server.setExecutor(null);
            server.start();

            AuthorizationCodeUriRequest uriRequest = spotifyApi != null
                ? spotifyApi.authorizationCodeUri().build()
                : buildApi().authorizationCodeUri().build();

            URI uri = uriRequest.execute();

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                Text msg = Text.literal("§a[Música] Abriendo Spotify en el navegador... ");
                Text link = Text.literal("§9§n[Haz clic aquí si no se abre]")
                    .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uri.toString()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Abrir URL manualmente"))));
                client.player.sendMessage(msg.append(link));
            }

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            }

            MusicMod.LOGGER.info("Spotify auth URL: {}", uri);
        } catch (Exception e) {
            MusicMod.LOGGER.error("Failed to start Spotify auth", e);
            future.complete(false);
        }

        return future;
    }

    private void completeAuth(String code, CompletableFuture<Boolean> future) {
        try {
            var request = buildApi().authorizationCode(code).build();
            var credentials = request.execute();

            setTokens(credentials.getAccessToken(), credentials.getRefreshToken());

            MusicMod.LOGGER.info("Spotify authentication successful");
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("§a[Música] Spotify conectado exitosamente!"), false);
            }
            future.complete(true);
        } catch (Exception e) {
            MusicMod.LOGGER.error("Spotify auth exchange failed", e);
            future.complete(false);
        }
    }

    public boolean refreshTokenIfNeeded() {
        if (spotifyApi == null || refreshToken == null) return false;
        try {
            var request = spotifyApi.authorizationCodeRefresh().build();
            var credentials = request.execute();
            setTokens(credentials.getAccessToken(), credentials.getRefreshToken());
            return true;
        } catch (Exception e) {
            MusicMod.LOGGER.warn("Failed to refresh Spotify token", e);
            authenticated = false;
            return false;
        }
    }

    private void exchangeClose() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void logout() {
        exchangeClose();
        authenticated = false;
        accessToken = null;
        refreshToken = null;
        spotifyApi = null;

        ModConfig.SpotifyConfig cfg = MusicMod.getInstance().getConfig().spotify;
        cfg.accessToken = "";
        cfg.refreshToken = "";
        MusicMod.getInstance().getConfig().save();

        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(
                Text.literal("§c[Música] Spotify desconectado"), false);
        }
    }

    public boolean isAuthenticated() { return authenticated; }
    public SpotifyApi getApi() { return spotifyApi; }
}
