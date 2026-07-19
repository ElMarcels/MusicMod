package com.musicmod.audio;

import com.musicmod.MusicMod;
import com.musicmod.config.ModConfig;
import com.musicmod.config.ModConfig.SongEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.*;

public class MusicPlayer {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private SoundInstance currentSound;
    private SongEntry currentSong;
    private boolean playing = false;
    private int currentIndex = -1;
    private final Random random = new Random();
    private List<SongEntry> shuffledPlaylist = new ArrayList<>();

    private String source = "local";

    private final List<VoteEntry> votes = new ArrayList<>();
    private long voteEndTime = 0;
    private boolean voteActive = false;

    public void play(SongEntry song) {
        stop();
        if (!MusicMod.getInstance().getConfig().enabled) return;
        source = "local";

        currentSong = song;
        Identifier soundId = Identifier.of(song.soundId);
        SoundEvent soundEvent = SoundEvent.of(soundId);
        currentSound = PositionedSoundInstance.music(soundEvent);
        SoundManager soundManager = client.getSoundManager();
        if (soundManager != null) {
            soundManager.play(currentSound);
        }
        playing = true;
        MusicMod.LOGGER.info("Now playing: {}", song.name);
    }

    public void playSpotify() {
        stop();
        source = "spotify";
        playing = true;
        MusicMod.getInstance().getSpotifyPlayer().play();
    }

    public void stop() {
        if (source.equals("spotify")) {
            MusicMod.getInstance().getSpotifyPlayer().pause();
        }
        SoundManager soundManager = client.getSoundManager();
        if (currentSound != null && soundManager != null) {
            soundManager.stop(currentSound);
        }
        playing = false;
        currentSound = null;
    }

    public void pause() {
        if (source.equals("spotify")) {
            MusicMod.getInstance().getSpotifyPlayer().pause();
            playing = false;
            return;
        }
        SoundManager soundManager = client.getSoundManager();
        if (playing && currentSound != null && soundManager != null) {
            soundManager.stop(currentSound);
            playing = false;
        }
    }

    public void resume() {
        if (source.equals("spotify")) {
            MusicMod.getInstance().getSpotifyPlayer().play();
            playing = true;
            return;
        }
        if (!playing && currentSong != null) {
            play(currentSong);
        }
    }

    public void next() {
        if (source.equals("spotify")) {
            MusicMod.getInstance().getSpotifyPlayer().nextTrack();
            return;
        }
        ModConfig config = MusicMod.getInstance().getConfig();
        List<SongEntry> playlist = config.playlist;
        if (playlist.isEmpty()) return;

        if (config.shuffle) {
            if (shuffledPlaylist.isEmpty()) {
                shuffledPlaylist = new ArrayList<>(playlist);
                Collections.shuffle(shuffledPlaylist, random);
            }
            play(shuffledPlaylist.remove(0));
        } else {
            currentIndex = (currentIndex + 1) % playlist.size();
            play(playlist.get(currentIndex));
        }
    }

    public void previous() {
        if (source.equals("spotify")) {
            MusicMod.getInstance().getSpotifyPlayer().previousTrack();
            return;
        }
        ModConfig config = MusicMod.getInstance().getConfig();
        List<SongEntry> playlist = config.playlist;
        if (playlist.isEmpty()) return;

        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        play(playlist.get(currentIndex));
    }

    public void setVolume(float volume) {
        ModConfig config = MusicMod.getInstance().getConfig();
        config.volume = volume;
        if (source.equals("spotify")) {
            MusicMod.getInstance().getSpotifyPlayer().setVolume((int)(volume * 100));
        }
        SoundManager soundManager = client.getSoundManager();
        if (currentSound != null && soundManager != null) {
            soundManager.updateSoundVolume(SoundCategory.MUSIC, volume);
        }
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public void startVote(int durationSeconds) {
        votes.clear();
        voteActive = true;
        voteEndTime = System.currentTimeMillis() + durationSeconds * 1000L;
        MusicMod.LOGGER.info("Vote started! Duration: {}s", durationSeconds);
    }

    public void castVote(String voter, int songIndex) {
        if (!voteActive) return;
        if (System.currentTimeMillis() > voteEndTime) {
            endVote();
            return;
        }
        Optional<VoteEntry> existing = votes.stream()
            .filter(v -> v.voter.equals(voter))
            .findFirst();
        if (existing.isPresent()) {
            existing.get().songIndex = songIndex;
        } else {
            votes.add(new VoteEntry(voter, songIndex));
        }
    }

    public SongEntry endVote() {
        voteActive = false;
        if (votes.isEmpty()) {
            ModConfig config = MusicMod.getInstance().getConfig();
            if (!config.playlist.isEmpty()) {
                return config.playlist.get(random.nextInt(config.playlist.size()));
            }
            return null;
        }
        Map<Integer, Long> count = new HashMap<>();
        for (VoteEntry v : votes) {
            count.merge(v.songIndex, 1L, Long::sum);
        }
        int winnerIndex = count.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(0);
        ModConfig config = MusicMod.getInstance().getConfig();
        if (winnerIndex >= 0 && winnerIndex < config.playlist.size()) {
            SongEntry winner = config.playlist.get(winnerIndex);
            MusicMod.LOGGER.info("Vote ended! Winner: {} ({} votes)", winner.name, count.get(winnerIndex));
            return winner;
        }
        return null;
    }

    public void startTwitchVote() {
        int duration = MusicMod.getInstance().getConfig().voteDuration;
        startVote(duration);
    }

    public boolean isVoteActive() { return voteActive; }
    public long getVoteEndTime() { return voteEndTime; }
    public long getVoteTimeLeft() { return Math.max(0, voteEndTime - System.currentTimeMillis()) / 1000; }
    public List<VoteEntry> getVotes() { return votes; }
    public boolean isPlaying() { return playing; }
    public SongEntry getCurrentSong() { return currentSong; }
    public int getCurrentIndex() { return currentIndex; }

    public static class VoteEntry {
        public String voter;
        public int songIndex;
        public VoteEntry(String voter, int songIndex) {
            this.voter = voter;
            this.songIndex = songIndex;
        }
    }
}
