package com.musicmod.voice;

import com.musicmod.MusicMod;
import com.musicmod.audio.MusicPlayer;
import com.musicmod.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.vosk.*;
import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceCommandManager {
    private static final String MODEL_PATH = "models/vosk-model-small-es-0.42";
    private static final int SAMPLE_RATE = 16000;

    private Recognizer recognizer;
    private TargetDataLine micLine;
    private Thread listeningThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private boolean modelLoaded = false;
    private final List<VoiceCommand> commands = new ArrayList<>();

    public VoiceCommandManager() {
        registerDefaultCommands();
    }

    private void registerDefaultCommands() {
        commands.add(new VoiceCommand("reproducir (.*)|poner (.*)|play (.*)", cmd -> {
            String text = cmd.getFullText();
            String query = text.replaceAll("^(reproducir|poner|play)\\s+", "").trim();
            if (!query.isEmpty()) triggerSpotifySearch(query);
        }));
        commands.add(new VoiceCommand("pausa|parar|stop", cmd -> {
            MusicMod.getInstance().getMusicPlayer().pause();
            sendFeedback("§e[Música] Pausado");
        }));
        commands.add(new VoiceCommand("siguiente|next|skip", cmd -> {
            MusicMod.getInstance().getMusicPlayer().next();
            sendFeedback("§e[Música] Siguiente canción");
        }));
        commands.add(new VoiceCommand("anterior|prev|back", cmd -> {
            MusicMod.getInstance().getMusicPlayer().previous();
            sendFeedback("§e[Música] Canción anterior");
        }));
        commands.add(new VoiceCommand("sube volumen|volumen arriba|más volumen", cmd -> {
            ModConfig config = MusicMod.getInstance().getConfig();
            config.volume = Math.min(1.0f, config.volume + 0.1f);
            MusicMod.getInstance().getMusicPlayer().setVolume(config.volume);
            config.save();
            sendFeedback("§a[Música] Volumen: " + (int)(config.volume * 100) + "%");
        }));
        commands.add(new VoiceCommand("baja volumen|volumen abajo|menos volumen", cmd -> {
            ModConfig config = MusicMod.getInstance().getConfig();
            config.volume = Math.max(0.0f, config.volume - 0.1f);
            MusicMod.getInstance().getMusicPlayer().setVolume(config.volume);
            config.save();
            sendFeedback("§a[Música] Volumen: " + (int)(config.volume * 100) + "%");
        }));
        commands.add(new VoiceCommand("aleatorio|shuffle|random", cmd -> {
            ModConfig config = MusicMod.getInstance().getConfig();
            config.shuffle = !config.shuffle;
            config.save();
            sendFeedback("§e[Música] Shuffle: " + (config.shuffle ? "ON" : "OFF"));
        }));
        commands.add(new VoiceCommand("inicia voto|votación|comienza voto", cmd -> {
            MusicMod.getInstance().getMusicPlayer().startTwitchVote();
            sendFeedback("§a[Música] Votación iniciada! Usa !vote # en Twitch");
        }));
        commands.add(new VoiceCommand("abre menú|abre panel|music controller|menú música", cmd -> {
            MinecraftClient.getInstance().setScreen(
                new com.musicmod.gui.MusicControllerScreen());
        }));
        commands.add(new VoiceCommand("spotify login|conectar spotify", cmd -> {
            var auth = MusicMod.getInstance().getSpotifyAuthManager();
            if (!auth.isAuthenticated()) {
                auth.login();
            } else {
                sendFeedback("§a[Música] Spotify ya está conectado");
            }
        }));
        commands.add(new VoiceCommand("spotify play|spotify reproduce", cmd -> {
            MusicMod.getInstance().getSpotifyPlayer().play();
        }));
        commands.add(new VoiceCommand("spotify pausa|spotify stop", cmd -> {
            MusicMod.getInstance().getSpotifyPlayer().pause();
        }));
        commands.add(new VoiceCommand("spotify siguiente|spotify next", cmd -> {
            MusicMod.getInstance().getSpotifyPlayer().nextTrack();
        }));
    }

    public boolean loadModel() {
        try {
            if (!new File(MODEL_PATH).exists()) {
                MusicMod.LOGGER.warn("Vosk model not found at {}. Voice commands disabled.", MODEL_PATH);
                sendFeedback("§c[Música] Modelo de voz no encontrado en: " + MODEL_PATH);
                sendFeedback("§7Descárgalo de: https://alphacephei.com/vosk/models");
                return false;
            }
            recognizer = new Recognizer(new Model(MODEL_PATH), SAMPLE_RATE);
            modelLoaded = true;
            MusicMod.LOGGER.info("Vosk model loaded from {}", MODEL_PATH);
            return true;
        } catch (Exception e) {
            MusicMod.LOGGER.error("Failed to load Vosk model", e);
            sendFeedback("§c[Música] Error al cargar modelo de voz: " + e.getMessage());
            return false;
        }
    }

    public boolean startListening() {
        if (listening.get()) return true;
        if (!modelLoaded && !loadModel()) return false;

        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                sendFeedback("§c[Música] Micrófono no disponible");
                return false;
            }
            micLine = (TargetDataLine) AudioSystem.getLine(info);
            micLine.open(format);
            micLine.start();

            listening.set(true);
            running.set(true);

            listeningThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (running.get()) {
                    int bytesRead = micLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String result = recognizer.getResult();
                        processResult(result);
                    }
                }
            }, "Voice-Listener");
            listeningThread.setDaemon(true);
            listeningThread.start();

            sendFeedback("§a[Música] Escuchando comandos de voz...");
            MusicMod.LOGGER.info("Voice listening started");
            return true;
        } catch (LineUnavailableException e) {
            MusicMod.LOGGER.error("Microphone unavailable", e);
            sendFeedback("§c[Música] Micrófono no disponible: " + e.getMessage());
            return false;
        }
    }

    public void stopListening() {
        running.set(false);
        listening.set(false);
        if (micLine != null) {
            micLine.stop();
            micLine.close();
            micLine = null;
        }
        if (listeningThread != null) {
            listeningThread.interrupt();
            listeningThread = null;
        }
        sendFeedback("§7[Música] Escucha de voz desactivada");
    }

    private void processResult(String jsonResult) {
        try {
            String cleaned = jsonResult.replaceAll("\\{.*?\"text\"\\s*:\\s*\"(.*?)\".*", "$1").trim();
            if (cleaned.isEmpty()) return;

            String text = cleaned.toLowerCase(Locale.ROOT);
            MusicMod.LOGGER.info("Voice heard: {}", text);

            for (VoiceCommand cmd : commands) {
                if (cmd.matches(text)) {
                    cmd.execute(text);
                    if (MusicMod.getInstance().getConfig().voice.showRecognizedText) {
                        sendFeedback("§7[Voz] §f\"" + text + "\"");
                    }
                    return;
                }
            }

            if (MusicMod.getInstance().getConfig().spotify.enabled &&
                MusicMod.getInstance().getSpotifyAuthManager().isAuthenticated()) {
                if (text.length() > 3) {
                    triggerSpotifySearch(text);
                }
            }
        } catch (Exception e) {
            MusicMod.LOGGER.warn("Failed to process voice result", e);
        }
    }

    private void triggerSpotifySearch(String query) {
        var spotify = MusicMod.getInstance().getSpotifyPlayer();
        if (spotify != null) {
            spotify.searchAndPlay(query);
        }
    }

    private void sendFeedback(String msg) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(msg), false);
        }
    }

    public boolean isListening() { return listening.get(); }
    public boolean isModelLoaded() { return modelLoaded; }

    public static class VoiceCommand {
        private final Pattern pattern;
        private final String rawPattern;
        private final CommandAction action;
        private String fullText;

        public VoiceCommand(String regex, CommandAction action) {
            this.rawPattern = regex;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.action = action;
        }

        public boolean matches(String text) {
            return pattern.matcher(text).find();
        }

        public void execute(String text) {
            this.fullText = text;
            action.execute(this);
        }

        public String getFullText() { return fullText; }
        public String getRawPattern() { return rawPattern; }
    }

    @FunctionalInterface
    public interface CommandAction {
        void execute(VoiceCommand cmd);
    }
}
