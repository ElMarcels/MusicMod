package com.musicmod;

import com.musicmod.audio.MusicPlayer;
import com.musicmod.config.ModConfig;
import com.musicmod.gui.MusicControllerScreen;
import com.musicmod.network.TwitchVoteListener;
import com.musicmod.spotify.SpotifyAuthManager;
import com.musicmod.spotify.SpotifyPlayer;
import com.musicmod.voice.VoiceCommandManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicMod implements ClientModInitializer {
    public static final String MOD_ID = "musicmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MusicMod instance;
    private MusicPlayer musicPlayer;
    private ModConfig config;
    private TwitchVoteListener voteListener;
    private SpotifyAuthManager spotifyAuth;
    private SpotifyPlayer spotifyPlayer;
    private VoiceCommandManager voiceManager;
    private KeyBinding openGuiKey;
    private KeyBinding voiceToggleKey;

    @Override
    public void onInitializeClient() {
        instance = this;
        config = ModConfig.load();

        musicPlayer = new MusicPlayer();
        voteListener = new TwitchVoteListener();
        spotifyAuth = new SpotifyAuthManager();
        spotifyPlayer = new SpotifyPlayer(spotifyAuth);
        voiceManager = new VoiceCommandManager();

        registerKeyBindings();
        registerTickEvents();

        LOGGER.info("MusicMod initialized with Spotify & Voice support");
    }

    private void registerKeyBindings() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.musicmod.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.musicmod"
        ));

        voiceToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.musicmod.voice_toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "category.musicmod"
        ));
    }

    private void registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.wasPressed()) {
                client.setScreen(new MusicControllerScreen());
            }
            if (voiceToggleKey.wasPressed()) {
                toggleVoice();
            }
        });
    }

    private void toggleVoice() {
        if (voiceManager.isListening()) {
            voiceManager.stopListening();
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    net.minecraft.text.Text.literal("§7[Música] Comandos de voz desactivados"), false);
            }
        } else {
            boolean started = voiceManager.startListening();
            if (!started) {
                MinecraftClient.getInstance().player.sendMessage(
                    net.minecraft.text.Text.literal("§c[Música] No se pudo iniciar escucha de voz. Verifica que el modelo esté en: models/vosk-model-small-es-0.42"), false);
            }
        }
    }

    public static MusicMod getInstance() { return instance; }
    public MusicPlayer getMusicPlayer() { return musicPlayer; }
    public ModConfig getConfig() { return config; }
    public TwitchVoteListener getVoteListener() { return voteListener; }
    public SpotifyAuthManager getSpotifyAuthManager() { return spotifyAuth; }
    public SpotifyPlayer getSpotifyPlayer() { return spotifyPlayer; }
    public VoiceCommandManager getVoiceManager() { return voiceManager; }
    public KeyBinding getOpenGuiKey() { return openGuiKey; }
    public KeyBinding getVoiceToggleKey() { return voiceToggleKey; }
}
