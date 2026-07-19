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
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;

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

    private void drawLocalTab() {
        int leftCol = guiLeft + 10;
        int rightCol = guiLeft + 160;

        songList = new SongListWidget(
            width - 40, 90,
            guiTop + 45, guiTop + 130, 18);
        songList.setX(leftCol);
        songList.setWidth(GUI_WIDTH - 180);
        addSelectableChild(songList);

        addDrawableChild(ButtonWidget.builder(
            Text.literal("▶ Play"),
            btn -> {
                if (player.getSource().equals("spotify")) { player.setSource("local"); player.stop(); }
                if (player.isPlaying()) { player.pause(); btn.setMessage(Text.literal("▶ Play")); }
                else {
                    SongEntry selected = songList.getSelected();
                    if (selected != null) player.play(selected);
                    else if (!config.playlist.isEmpty()) player.next();
                    btn.setMessage(Text.literal("⏸ Pause"));
                }
            }
        ).dimensions(leftCol, guiTop + 135, 60, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("⏭"), btn -> player.next()).dimensions(leftCol + 63, guiTop + 135, 24, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏮"), btn -> player.previous()).dimensions(leftCol + 90, guiTop + 135, 24, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), btn -> player.stop()).dimensions(leftCol + 117, guiTop + 135, 24, 18).build());

        addDrawableChild(new SliderWidget(leftCol, guiTop + 158, 140, 18,
            Text.literal("Vol: " + (int)(config.volume * 100) + "%"), config.volume) {
            @Override protected void updateMessage() { setMessage(Text.literal("Vol: " + (int)(this.value * 100) + "%")); }
            @Override protected void applyValue() { config.volume = (float)value; player.setVolume(config.volume); config.save(); }
        });

        addDrawableChild(ButtonWidget.builder(Text.literal(config.shuffle ? "🔀 ON" : "🔀 OFF"), btn -> { config.shuffle = !config.shuffle; config.save(); init(); }).dimensions(leftCol, guiTop + 180, 45, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(config.loop ? "🔁 ON" : "🔁 OFF"), btn -> { config.loop = !config.loop; config.save(); init(); }).dimensions(leftCol + 48, guiTop + 180, 45, 18).build());

        addDrawableChild(new TextWidget(rightCol, guiTop + 45, 80, 10, Text.literal("§7Twitch:"), textRenderer));
        twitchChannelField = new TextFieldWidget(textRenderer, rightCol, guiTop + 57, 85, 16, Text.literal("Canal"));
        twitchChannelField.setText(config.twitchChannel);
        addDrawableChild(twitchChannelField);
        twitchConnectButton = addDrawableChild(ButtonWidget.builder(Text.literal(MusicMod.getInstance().getVoteListener().isConnected() ? "✓ Con" : "Conectar"), btn -> {
            String ch = twitchChannelField.getText();
            if (!ch.isBlank()) { config.twitchChannel = ch; config.twitchIntegration = true; config.save(); MusicMod.getInstance().getVoteListener().connect(ch); }
        }).dimensions(rightCol + 88, guiTop + 56, 70, 17).build());

        addDrawableChild(new TextWidget(rightCol, guiTop + 78, 80, 10, Text.literal("§7Votación:"), textRenderer));
        voteDurationField = new TextFieldWidget(textRenderer, rightCol, guiTop + 90, 40, 16, Text.literal("Seg"));
        voteDurationField.setText(String.valueOf(config.voteDuration));
        voteDurationField.setTextPredicate(s -> s.matches("\\d{0,3}"));
        addDrawableChild(voteDurationField);
        addDrawableChild(ButtonWidget.builder(Text.literal("🗳 Votar"), btn -> {
            try { config.voteDuration = Integer.parseInt(voteDurationField.getText()); } catch (NumberFormatException ignored) {}
            player.startVote(config.voteDuration);
        }).dimensions(rightCol + 44, guiTop + 89, 55, 17).build());
    }

    private void drawSpotifyTab() {
        var auth = MusicMod.getInstance().getSpotifyAuthManager();
        var sp = MusicMod.getInstance().getSpotifyPlayer();

        addDrawableChild(ButtonWidget.builder(Text.literal(auth.isAuthenticated() ? "✓ Spotify Conectado" : "🔗 Login Spotify"), btn -> {
            if (!auth.isAuthenticated()) auth.login(); else { auth.logout(); init(); }
        }).dimensions(guiLeft + 10, guiTop + 45, 145, 20).build());

        SpotifyConfig sc = config.spotify;
        addDrawableChild(ButtonWidget.builder(Text.literal(sc.enabled ? "Integración: ON" : "Integración: OFF"), btn -> { sc.enabled = !sc.enabled; config.save(); init(); }).dimensions(guiLeft + 160, guiTop + 45, 90, 20).build());

        if (!auth.isAuthenticated()) return;

        SpotifyTrackInfo track = sp.getCurrentTrack();
        if (track != null) {
            addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 72, GUI_WIDTH - 20, 12,
                Text.literal("§lNow:§r " + track.name + " §7- " + track.artists), textRenderer));
        } else {
            addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 72, GUI_WIDTH - 20, 12,
                Text.literal("§7Sin reproducción activa"), textRenderer));
        }

        addDrawableChild(ButtonWidget.builder(Text.literal(sp.isPlaying() ? "⏸ Pausar" : "▶ Reproducir"), btn -> {
            if (player.getSource().equals("spotify") && player.isPlaying()) sp.pause(); else player.playSpotify(); init();
        }).dimensions(guiLeft + 10, guiTop + 90, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("⏭ Sig"), btn -> { sp.nextTrack(); init(); }).dimensions(guiLeft + 85, guiTop + 90, 50, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏮ Ant"), btn -> { sp.previousTrack(); init(); }).dimensions(guiLeft + 140, guiTop + 90, 50, 20).build());

        addDrawableChild(new SliderWidget(guiLeft + 10, guiTop + 115, 140, 18,
            Text.literal("Vol: " + sp.getVolumePercent() + "%"), sp.getVolumePercent() / 100.0) {
            @Override protected void updateMessage() { setMessage(Text.literal("Vol: " + (int)(this.value * 100) + "%")); }
            @Override protected void applyValue() { sp.setVolume((int)(this.value * 100)); }
        });

        addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 140, 80, 10, Text.literal("§7Buscar:"), textRenderer));
        spotifySearchField = new TextFieldWidget(textRenderer, guiLeft + 10, guiTop + 152, 140, 16, Text.literal("Nombre o artista..."));
        addDrawableChild(spotifySearchField);
        addDrawableChild(ButtonWidget.builder(Text.literal("🔍"), btn -> { String q = spotifySearchField.getText(); if (!q.isBlank()) sp.searchAndPlay(q); }).dimensions(guiLeft + 155, guiTop + 151, 25, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("▶ Playlist"), btn -> { String q = spotifySearchField.getText(); if (!q.isBlank()) sp.playPlaylist(q); }).dimensions(guiLeft + 10, guiTop + 175, 80, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("📱 Disp."), btn -> sp.listDevices()).dimensions(guiLeft + 95, guiTop + 175, 70, 18).build());
    }

    private void drawVoiceTab() {
        var voice = MusicMod.getInstance().getVoiceManager();
        VoiceConfig vc = config.voice;

        addDrawableChild(ButtonWidget.builder(Text.literal(voice.isListening() ? "🔴 Desactivar Voz" : "🎤 Activar Voz"), btn -> {
            if (voice.isListening()) voice.stopListening(); else voice.startListening();
            init();
        }).dimensions(guiLeft + 10, guiTop + 45, 130, 20).build());

        String status = !voice.isModelLoaded() ? "§cModelo no encontrado" : voice.isListening() ? "§a✓ Escuchando..." : "§7Inactivo";
        addDrawableChild(new TextWidget(guiLeft + 145, guiTop + 47, 150, 10, Text.literal(status), textRenderer));

        addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 68, 150, 10, Text.literal("§7Tecla rápida: V"), textRenderer));
        addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 82, 150, 10, Text.literal("§7Modelo: vosk-model-small-es-0.42"), textRenderer));

        addDrawableChild(ButtonWidget.builder(Text.literal(vc.continuousListening ? "Contínua: ON" : "Contínua: OFF"), btn -> { vc.continuousListening = !vc.continuousListening; config.save(); init(); }).dimensions(guiLeft + 10, guiTop + 97, 110, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(vc.showRecognizedText ? "Texto: ON" : "Texto: OFF"), btn -> { vc.showRecognizedText = !vc.showRecognizedText; config.save(); init(); }).dimensions(guiLeft + 125, guiTop + 97, 80, 16).build());

        addDrawableChild(new TextWidget(guiLeft + 10, guiTop + 120, 100, 10, Text.literal("§lComandos disponibles:"), textRenderer));
        String[] cmds = {
            "§ereproducir/play + nombre", "§epausa/stop", "§esiguiente/next", "§esube/baja volumen",
            "§ealeatorio/shuffle", "§einicia voto", "§eabre menú", "§7Otros -> busca en Spotify"
        };
        int y = 133;
        for (String c : cmds) { addDrawableChild(new TextWidget(guiLeft + 10, guiTop + y, 200, 9, Text.literal(c), textRenderer)); y += 10; }

        addDrawableChild(ButtonWidget.builder(Text.literal("📥 Descargar modelo Vosk"), btn -> MinecraftClient.getInstance().player.sendMessage(
            net.minecraft.text.Text.literal("§aDescarga en: https://alphacephei.com/vosk/models"))).dimensions(guiLeft + 10, guiTop + 220, 150, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xC0101010);
        context.drawBorder(guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, 0xFF444444);

        context.drawText(textRenderer, Text.literal("§lMusic Controller"), guiLeft + 8, guiTop + 6, 0xFFFFFF, true);

        if (currentTab == Tab.LOCAL) {
            SongEntry current = player.getCurrentSong();
            String now = player.getSource().equals("spotify") ? "§b♫ Spotify conectado" : (current != null ? "§aNow: " + current.name : "§7No song playing");
            context.drawText(textRenderer, Text.literal(now), guiLeft + 8, guiTop + GUI_HEIGHT - 16, 0xFFFFFF, true);
            if (player.isVoteActive()) {
                String vote = "§eVote: " + player.getVoteTimeLeft() + "s";
                context.drawText(textRenderer, Text.literal(vote), guiLeft + GUI_WIDTH / 2 - 30, guiTop + GUI_HEIGHT - 16, 0xFFFF00, true);
            }
        }

        if (currentTab == Tab.LOCAL && songList != null) songList.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    private class SongListWidget extends AlwaysSelectedEntryListWidget<SongEntryWidget> {
        private final List<SongEntry> entries;

        public SongListWidget(int width, int height, int top, int bottom, int itemHeight) {
            super(MinecraftClient.getInstance(), width, height, top, bottom);
            this.entries = config.playlist;
            for (int i = 0; i < entries.size(); i++) {
                addEntry(new SongEntryWidget(entries.get(i), i));
            }
        }

        public SongEntry getSelected() {
            SongEntryWidget w = getSelectedOrNull();
            return w != null ? w.song : null;
        }

        @Override
        public int getRowWidth() { return width / 2 - 10; }
        @Override
        protected int getScrollbarX() { return getX() + width / 2 - 6; }
    }

    private class SongEntryWidget extends AlwaysSelectedEntryListWidget.Entry<SongEntryWidget> {
        private final SongEntry song;
        private final int idx;

        SongEntryWidget(SongEntry song, int idx) {
            this.song = song;
            this.idx = idx;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            String text = (idx + 1) + ". " + song.name;
            context.drawText(textRenderer, text, x + 5, y + (entryHeight - 8) / 2, hovered ? 0xFFFFAA : 0xFFFFFF, false);
        }

        @Override
        public Text getNarration() {
            return Text.literal(song.name);
        }
    }
}
