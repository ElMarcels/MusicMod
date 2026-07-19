package com.musicmod.gui;

import com.musicmod.MusicMod;
import com.musicmod.audio.MusicPlayer;
import com.musicmod.config.ModConfig;
import com.musicmod.config.ModConfig.SongEntry;
import com.musicmod.config.ModConfig.SpotifyConfig;
import com.musicmod.config.ModConfig.VoiceConfig;
import com.musicmod.spotify.SpotifyPlayer.SpotifyTrackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MusicControllerScreen extends Screen {
    private static final int GUI_WIDTH = 340;
    private static final int GUI_HEIGHT = 250;

    private int guiLeft, guiTop;
    private final MusicPlayer player = MusicMod.getInstance().getMusicPlayer();
    private final ModConfig config = MusicMod.getInstance().getConfig();
    private Tab currentTab = Tab.LOCAL;
    private SongListWidget songList;
    private TextFieldWidget voteDurationField;
    private TextFieldWidget twitchChannelField;
    private TextFieldWidget spotifySearchField;
    private ButtonWidget voteButton;
    private ButtonWidget twitchConnectButton;
    private ButtonWidget spotifyLoginButton;
    private ButtonWidget voiceToggleButton;
    private ButtonWidget spotifyPlayPauseButton;
    private ButtonWidget spotifyNextButton;
    private ButtonWidget spotifyPrevButton;

    private enum Tab { LOCAL, SPOTIFY, VOICE }

    public MusicControllerScreen() {
        super(Text.literal("Music Controller"));
    }

    @Override
    protected void init() {
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;

        clearChildren();
        drawTabs();

        switch (currentTab) {
            case LOCAL -> drawLocalTab();
            case SPOTIFY -> drawSpotifyTab();
            case VOICE -> drawVoiceTab();
        }

        // Close button
        addDrawableChild(ButtonWidget.builder(
            Text.literal("✕"),
            btn -> close()
        ).dimensions(guiLeft + GUI_WIDTH - 22, guiTop + 3, 18, 18).build());
    }

    private void drawTabs() {
        int tabWidth = (GUI_WIDTH - 20) / 3;
        int tabY = guiTop + 22;
        String[] tabNames = {"Local", "Spotify", "Voz"};
        for (int i = 0; i < 3; i++) {
            final Tab tab = Tab.values()[i];
            boolean active = currentTab == tab;
            int x = guiLeft + 10 + i * tabWidth;
            addDrawableChild(ButtonWidget.builder(
                Text.literal((active ? "§l" : "") + tabNames[i]),
                btn -> { currentTab = tab; init(); }
            ).dimensions(x, tabY, tabWidth - 2, 18).build());
        }
    }

    // ============== LOCAL TAB ==============
    private void drawLocalTab() {
        int leftCol = guiLeft + 10;
        int rightCol = guiLeft + 160;

        // Song list
        songList = new SongListWidget(
            mc, GUI_WIDTH - 180, 90,
            guiTop + 45, guiTop + 130,
            18, leftCol, guiTop + 45
        );
        addSelectableChild(songList);

        // Play/Pause
        addDrawableChild(ButtonWidget.builder(
            Text.literal("▶ Play"),
            btn -> {
                if (player.getSource().equals("spotify")) {
                    player.setSource("local");
                    player.stop();
                }
                if (player.isPlaying()) {
                    player.pause();
                    btn.setMessage(Text.literal("▶ Play"));
                } else {
                    SongEntry selected = songList.getSelectedOrNull();
                    if (selected != null) player.play(selected);
                    else if (!config.playlist.isEmpty()) player.next();
                    btn.setMessage(Text.literal("⏸ Pause"));
                }
            }
        ).dimensions(leftCol, guiTop + 135, 60, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("⏭"), btn -> player.next())
            .dimensions(leftCol + 63, guiTop + 135, 24, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏮"), btn -> player.previous())
            .dimensions(leftCol + 90, guiTop + 135, 24, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), btn -> player.stop())
            .dimensions(leftCol + 117, guiTop + 135, 24, 18).build());

        // Volume slider
        addDrawableChild(new SliderWidget(
            leftCol, guiTop + 158, 140, 18,
            Text.literal("Vol: " + (int)(config.volume * 100) + "%"),
            config.volume
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Vol: " + (int)(this.value * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                config.volume = (float) this.value;
                player.setVolume(config.volume);
                config.save();
            }
        });

        // Shuffle / Loop
        addDrawableChild(ButtonWidget.builder(
            Text.literal(config.shuffle ? "🔀 ON" : "🔀 OFF"),
            btn -> { config.shuffle = !config.shuffle; config.save(); init(); }
        ).dimensions(leftCol, guiTop + 180, 45, 18).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal(config.loop ? "🔁 ON" : "🔁 OFF"),
            btn -> { config.loop = !config.loop; config.save(); init(); }
        ).dimensions(leftCol + 48, guiTop + 180, 45, 18).build());

        // Twitch section
        TextWidget twitchLabel = new TextWidget(rightCol, guiTop + 45, 80, 10,
            Text.literal("§7Twitch:"), textRenderer);
        addDrawableChild(twitchLabel);

        twitchChannelField = new TextFieldWidget(textRenderer, rightCol, guiTop + 57, 85, 16, Text.literal("Canal"));
        twitchChannelField.setText(config.twitchChannel);
        addDrawableChild(twitchChannelField);

        twitchConnectButton = addDrawableChild(ButtonWidget.builder(
            Text.literal(MusicMod.getInstance().getVoteListener().isConnected() ? "✓ Conectado" : "Conectar"),
            btn -> {
                String ch = twitchChannelField.getText();
                if (!ch.isBlank()) {
                    config.twitchChannel = ch;
                    config.twitchIntegration = true;
                    config.save();
                    MusicMod.getInstance().getVoteListener().connect(ch);
                }
            }
        ).dimensions(rightCol + 88, guiTop + 56, 80, 17).build());

        // Vote
        TextWidget voteLabel = new TextWidget(rightCol, guiTop + 78, 80, 10,
            Text.literal("§7Votación:"), textRenderer);
        addDrawableChild(voteLabel);

        voteDurationField = new TextFieldWidget(textRenderer, rightCol, guiTop + 90, 40, 16,
            Text.literal("Seg"));
        voteDurationField.setText(String.valueOf(config.voteDuration));
        voteDurationField.setTextPredicate(s -> s.matches("\\d{0,3}"));
        addDrawableChild(voteDurationField);

        voteButton = addDrawableChild(ButtonWidget.builder(
            Text.literal("🗳 Votar"),
            btn -> {
                try { config.voteDuration = Integer.parseInt(voteDurationField.getText()); }
                catch (NumberFormatException ignored) {}
                player.startVote(config.voteDuration);
            }
        ).dimensions(rightCol + 44, guiTop + 89, 55, 17).build());
    }

    // ============== SPOTIFY TAB ==============
    private void drawSpotifyTab() {
        int cx = guiLeft + GUI_WIDTH / 2;
        var auth = MusicMod.getInstance().getSpotifyAuthManager();
        var sp = MusicMod.getInstance().getSpotifyPlayer();

        // Login button
        spotifyLoginButton = addDrawableChild(ButtonWidget.builder(
            Text.literal(auth.isAuthenticated() ? "✓ Spotify Conectado" : "🔗 Login Spotify"),
            btn -> {
                if (!auth.isAuthenticated()) {
                    auth.login().thenAccept(success -> {
                        if (success) init();
                    });
                } else {
                    auth.logout();
                    init();
                }
            }
        ).dimensions(guiLeft + 10, guiTop + 45, 140, 20).build());

        // Spotify integration toggle
        SpotifyConfig sc = config.spotify;
        addDrawableChild(ButtonWidget.builder(
            Text.literal(sc.enabled ? "Integración: ON" : "Integración: OFF"),
            btn -> { sc.enabled = !sc.enabled; config.save(); init(); }
        ).dimensions(guiLeft + 155, guiTop + 45, 90, 20).build());

        if (!auth.isAuthenticated()) return;

        // Now playing info
        SpotifyTrackInfo track = sp.getCurrentTrack();
        if (track != null) {
            Text trackText = Text.literal("§lNow:§r " + track.name + " §7- " + track.artists);
            TextWidget trackWidget = new TextWidget(guiLeft + 10, guiTop + 72, GUI_WIDTH - 20, 12,
                trackText, textRenderer);
            addDrawableChild(trackWidget);
        } else {
            addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 72, GUI_WIDTH - 20, 12,
                Text.literal("§7Sin reproducción activa"), textRenderer));
        }

        // Playback controls
        spotifyPlayPauseButton = addDrawableChild(ButtonWidget.builder(
            Text.literal(sp.isPlaying() ? "⏸ Pausar" : "▶ Reproducir"),
            btn -> {
                if (player.getSource().equals("spotify") && player.isPlaying()) {
                    sp.pause();
                } else {
                    player.playSpotify();
                }
                init();
            }
        ).dimensions(guiLeft + 10, guiTop + 90, 70, 20).build());

        spotifyNextButton = addDrawableChild(ButtonWidget.builder(
            Text.literal("⏭ Siguiente"),
            btn -> { sp.nextTrack(); init(); }
        ).dimensions(guiLeft + 85, guiTop + 90, 65, 20).build());

        spotifyPrevButton = addDrawableChild(ButtonWidget.builder(
            Text.literal("⏮ Anterior"),
            btn -> { sp.previousTrack(); init(); }
        ).dimensions(guiLeft + 155, guiTop + 90, 65, 20).build());

        // Volume
        addDrawableChild(new SliderWidget(
            guiLeft + 10, guiTop + 115, 140, 18,
            Text.literal("Vol: " + sp.getVolumePercent() + "%"),
            sp.getVolumePercent() / 100.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Vol: " + (int)(this.value * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                sp.setVolume((int)(this.value * 100));
            }
        });

        // Search
        TextWidget searchLabel = new TextWidget(guiLeft + 10, guiTop + 140, 80, 10,
            Text.literal("§7Buscar canción:"), textRenderer);
        addDrawableChild(searchLabel);

        spotifySearchField = new TextFieldWidget(textRenderer, guiLeft + 10, guiTop + 153, 140, 16,
            Text.literal("Nombre o artista..."));
        addDrawableChild(spotifySearchField);

        addDrawableChild(ButtonWidget.builder(
            Text.literal("🔍 Buscar"),
            btn -> {
                String q = spotifySearchField.getText();
                if (!q.isBlank()) {
                    sp.searchAndPlay(q);
                }
            }
        ).dimensions(guiLeft + 155, guiTop + 152, 60, 18).build());

        // Playlist search
        addDrawableChild(ButtonWidget.builder(
            Text.literal("▶ Playlist..."),
            btn -> {
                String q = spotifySearchField.getText();
                if (!q.isBlank()) {
                    sp.playPlaylist(q);
                }
            }
        ).dimensions(guiLeft + 10, guiTop + 175, 100, 18).build());

        // List devices
        addDrawableChild(ButtonWidget.builder(
            Text.literal("📱 Dispositivos"),
            btn -> sp.listDevices()
        ).dimensions(guiLeft + 115, guiTop + 175, 100, 18).build());
    }

    // ============== VOICE TAB ==============
    private void drawVoiceTab() {
        var voice = MusicMod.getInstance().getVoiceManager();
        VoiceConfig vc = config.voice;

        voiceToggleButton = addDrawableChild(ButtonWidget.builder(
            Text.literal(voice.isListening() ? "🔴 Desactivar Voz" : "🎤 Activar Voz"),
            btn -> {
                if (voice.isListening()) {
                    voice.stopListening();
                } else {
                    voice.startListening();
                }
                init();
            }
        ).dimensions(guiLeft + 10, guiTop + 45, 130, 20).build());

        // Status indicator
        String status;
        if (!voice.isModelLoaded()) {
            status = "§cModelo no cargado";
        } else if (voice.isListening()) {
            status = "§a✓ Escuchando...";
        } else {
            status = "§7Inactivo";
        }
        addDrawableChild(new TextWidget(guiLeft + 145, guiTop + 47, 100, 10,
            Text.literal(status), textRenderer));

        // Voice toggle keybind hint
        addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 68, GUI_WIDTH - 20, 10,
            Text.literal("§7Tecla rápida: V"), textRenderer));

        // Model info
        addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 85, GUI_WIDTH - 20, 10,
            Text.literal("§7Modelo: vosk-model-small-es-0.42"), textRenderer));

        // Continuous listening toggle
        addDrawableChild(ButtonWidget.builder(
            Text.literal(vc.continuousListening ? "Escucha contínua: ON" : "Escucha contínua: OFF"),
            btn -> { vc.continuousListening = !vc.continuousListening; config.save(); init(); }
        ).dimensions(guiLeft + 10, guiTop + 100, 140, 18).build());

        // Show recognized text
        addDrawableChild(ButtonWidget.builder(
            Text.literal(vc.showRecognizedText ? "Mostrar texto: ON" : "Mostrar texto: OFF"),
            btn -> { vc.showRecognizedText = !vc.showRecognizedText; config.save(); init(); }
        ).dimensions(guiLeft + 155, guiTop + 100, 140, 18).build());

        // Comandos disponibles
        TextWidget cmdLabel = new TextWidget(guiLeft + 10, guiTop + 125, 100, 10,
            Text.literal("§lComandos disponibles:"), textRenderer);
        addDrawableChild(cmdLabel);

        String[] cmds = {
            "§e\"reproducir/poner/play\" + nombre",
            "§e\"pausa/parar/stop\"",
            "§e\"siguiente/next/skip\"",
            "§e\"sube/baja volumen\"",
            "§e\"aleatorio/shuffle\"",
            "§e\"inicia voto/votación\"",
            "§e\"abre menú/music controller\"",
            "§7Sin comando: busca en Spotify"
        };
        int y = guiTop + 138;
        for (String c : cmds) {
            addDrawableChild(new TextWidget(guiLeft + 10, y, GUI_WIDTH - 20, 10,
                Text.literal(c), textRenderer));
            y += 10;
        }

        // Download model link
        addDrawableChild(ButtonWidget.builder(
            Text.literal("📥 Descargar modelo Vosk"),
            btn -> MinecraftClient.getInstance().player.sendMessage(
                net.minecraft.text.Text.literal("§aDescarga el modelo en: https://alphacephei.com/vosk/models"))
        ).dimensions(guiLeft + 10, guiTop + 220, 150, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xC0101010);
        context.drawBorder(guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, 0xFF444444);

        // Title
        context.drawText(textRenderer, Text.literal("§lMusic Controller"), guiLeft + 8, guiTop + 6, 0xFFFFFF, true);

        // Now playing (local)
        if (currentTab == Tab.LOCAL) {
            SongEntry current = player.getCurrentSong();
            String now = player.getSource().equals("spotify")
                ? "§b♫ Spotify conectado"
                : (current != null ? "§aNow: " + current.name : "§7No song playing");
            context.drawText(textRenderer, Text.literal(now), guiLeft + 8, guiTop + GUI_HEIGHT - 16, 0xFFFFFF, true);

            if (player.isVoteActive()) {
                String vote = "§eVote: " + player.getVoteTimeLeft() + "s";
                context.drawText(textRenderer, Text.literal(vote),
                    guiLeft + GUI_WIDTH / 2 - 30, guiTop + GUI_HEIGHT - 16, 0xFFFF00, true);
            }
        }

        if (currentTab == Tab.LOCAL && songList != null) {
            songList.render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    // ============== SONG LIST WIDGETS ==============
    private static class SongListWidget extends ElementListWidget<SongEntryWidget> {
        private final List<SongEntry> entries;

        public SongListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight,
                              int x, int y) {
            super(client, width, height, y, itemHeight);
            this.entries = MusicMod.getInstance().getConfig().playlist;
            setX(x);
            for (int i = 0; i < entries.size(); i++) {
                addEntry(new SongEntryWidget(entries.get(i), i));
            }
        }

        public SongEntry getSelectedOrNull() {
            SongEntryWidget w = getSelectedOrNull();
            return w != null ? w.song : null;
        }

        @Override
        public int getRowWidth() { return width - 10; }
        @Override
        protected int getScrollbarPositionX() { return getX() + width - 6; }
    }

    private static class SongEntryWidget extends ElementListWidget.Entry<SongEntryWidget> {
        private final SongEntry song;
        private final int index;

        SongEntryWidget(SongEntry song, int index) {
            this.song = song;
            this.index = index;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {
            String text = (this.index + 1) + ". " + song.name;
            context.drawText(MinecraftClient.getInstance().textRenderer, text,
                x + 5, y + (entryHeight - 8) / 2, hovered ? 0xFFFFAA : 0xFFFFFF, false);
        }
    }
}
