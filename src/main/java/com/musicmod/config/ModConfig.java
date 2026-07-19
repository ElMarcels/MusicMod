package com.musicmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.musicmod.MusicMod;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("musicmod.json");

    public boolean enabled = true;
    public float volume = 0.5f;
    public boolean loop = false;
    public boolean shuffle = false;
    public boolean showHud = true;
    public boolean twitchIntegration = false;
    public String twitchChannel = "";
    public int voteDuration = 30;
    public List<SongEntry> playlist = new ArrayList<>();
    public SpotifyConfig spotify = new SpotifyConfig();
    public VoiceConfig voice = new VoiceConfig();

    public ModConfig() {
        playlist.add(new SongEntry("Cat", "musicmod:cat"));
        playlist.add(new SongEntry("Blocks", "musicmod:blocks"));
        playlist.add(new SongEntry("Chirp", "musicmod:chirp"));
        playlist.add(new SongEntry("Far", "musicmod:far"));
        playlist.add(new SongEntry("Mall", "musicmod:mall"));
        playlist.add(new SongEntry("Mellohi", "musicmod:mellohi"));
        playlist.add(new SongEntry("Stal", "musicmod:stal"));
        playlist.add(new SongEntry("Strad", "musicmod:strad"));
        playlist.add(new SongEntry("Ward", "musicmod:ward"));
        playlist.add(new SongEntry("11", "musicmod:eleven"));
        playlist.add(new SongEntry("Wait", "musicmod:wait"));
        playlist.add(new SongEntry("Pigstep", "musicmod:pigstep"));
        playlist.add(new SongEntry("Relic", "musicmod:relic"));
        playlist.add(new SongEntry("Precipice", "musicmod:precipice"));
    }

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                return GSON.fromJson(Files.readString(CONFIG_PATH), ModConfig.class);
            } catch (IOException e) {
                MusicMod.LOGGER.error("Failed to load config", e);
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            MusicMod.LOGGER.error("Failed to save config", e);
        }
    }

    public static class SongEntry {
        public String name;
        public String soundId;

        public SongEntry() {}
        public SongEntry(String name, String soundId) {
            this.name = name;
            this.soundId = soundId;
        }
    }

    public static class SpotifyConfig {
        public boolean enabled = false;
        public String accessToken = "";
        public String refreshToken = "";
        public int volumePercent = 50;
    }

    public static class VoiceConfig {
        public boolean enabled = false;
        public boolean continuousListening = false;
        public float confidenceThreshold = 0.3f;
        public String modelLanguage = "es";
        public boolean showRecognizedText = true;
    }
}
