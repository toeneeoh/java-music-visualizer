import javazoom.jl.decoder.Bitstream;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.function.Consumer;

public class AudioPlayer {
    private enum Type { NONE, MP3, WAV }

    private Type currentType = Type.NONE;
    private String currentPath = null;
    private String fileName = null;
    private Consumer<String> statusReporter;

    // MP3-specific
    private AdvancedPlayer mp3Player;
    private Thread mp3Thread;
    private int mp3PausedFrame = 0;
    private boolean mp3Paused = false;
    private boolean mp3Stopped = false;
    private long mp3StartTimeMillis = 0; // time playback starts
    private int mp3LastFrame = 0;
    private int mp3TotalFrames = 0;
    private final int MP3_FRAME_DURATION_MS = 26;

    // WAV-specific
    private Clip wavClip;
    private long wavPausedPos = 0;
    private boolean wavPaused = false;
    private long wavLength = 0; // length of wav in microseconds

    public float getProgress() {
        return switch (currentType) {
            case MP3 -> mp3TotalFrames > 0 && !mp3Stopped
                ? Math.min(getCurrentFrame() / (float) mp3TotalFrames, 1f)
                : 0f;
            case WAV -> wavClip != null && wavClip.isOpen() && wavLength > 0
                ? wavClip.getMicrosecondPosition() / (float) wavLength
                : 0f;
            default -> 0f;
        };
    }

    private int getCurrentFrame() {
        if (mp3Paused) return mp3PausedFrame;
        return mp3PausedFrame + (int)((System.currentTimeMillis() - mp3StartTimeMillis) / MP3_FRAME_DURATION_MS);
    }

    public void play(String path, String name, Consumer<String> statusReporter) {
        stop(); // stop current playback

        this.statusReporter = statusReporter;
        this.currentPath = path;
        this.fileName = name;

        if (path.endsWith(".mp3")) {
            currentType = Type.MP3;
            mp3TotalFrames = 0; // reset total frames
            playMp3();
        } else if (path.endsWith(".wav")) {
            currentType = Type.WAV;
            playWav(0);
        } else {
            statusReporter.accept("Error: Unsupported file type.");
        }
    }

    public void pause() {
        switch (currentType) {
            case MP3 -> pauseMp3();
            case WAV -> pauseWav();
            default -> statusReporter.accept("Nothing is playing.");
        }
    }

    public void resume() {
        switch (currentType) {
            case MP3 -> resumeMp3();
            case WAV -> resumeWav();
            default -> statusReporter.accept("Nothing is playing.");
        }
    }

    public void stop() {
        switch (currentType) {
            case MP3 -> stopMp3();
            case WAV -> stopWav();
            default -> {
                if (statusReporter != null)
                    statusReporter.accept("Nothing is playing.");
            }
        }
        currentType = Type.NONE;
        currentPath = null;
        fileName = null;
    }

    // ========== MP3 HANDLING ==========

    private void playMp3() {
        mp3Paused = false;
        mp3Stopped = false;

        mp3Thread = new Thread(() -> {
            try {
                // Estimate total frame count first
                if (mp3TotalFrames == 0)
                    mp3TotalFrames = estimateFrameCount(currentPath);
                System.out.println("total frames: " + mp3TotalFrames);

                FileInputStream fis = new FileInputStream(currentPath);
                mp3Player = new AdvancedPlayer(fis, new JavaSoundAudioDevice());

                mp3LastFrame = mp3PausedFrame;

                mp3Player.setPlayBackListener(new PlaybackListener() {
                    @Override
                    public void playbackFinished(PlaybackEvent evt) {
                        if (!mp3Paused && !mp3Stopped) {
                            mp3PausedFrame = 0;
                            statusReporter.accept("Finished playing: " + fileName);
                            mp3Stopped = true;
                        } else if (mp3Paused) {
                            mp3PausedFrame += evt.getFrame();
                        }
                        mp3LastFrame += evt.getFrame();
                    }
                });

                statusReporter.accept("Now playing: " + fileName);
                mp3StartTimeMillis = System.currentTimeMillis();
                mp3Player.play(mp3PausedFrame, Integer.MAX_VALUE);

            } catch (Exception e) {
                e.printStackTrace();
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                statusReporter.accept("Error: " + message);
            }
        });

        mp3Thread.start();
    }

    // hacky way to get mp3 length
    private int estimateFrameCount(String filePath) {
        FileInputStream fis = null;
        Bitstream bs = null;

        try {
            fis = new FileInputStream(filePath);
            bs = new Bitstream(fis);

            int frames = 0;
            while (bs.readFrame() != null) {
                frames++;
                bs.closeFrame();
            }
            return frames;

        } catch (Exception e) {
            return 0;
        } finally {
            try {
                if (bs != null) bs.close();
            } catch (Exception ignored) {}

            try {
                if (fis != null) fis.close();
            } catch (Exception ignored) {}
        }
    }

    // main method for seek bar
    public void seek(float percent) {
        switch (currentType) {
            case MP3 -> seekMp3(percent);
            case WAV -> seekWav(percent);
            default -> {
                if (statusReporter != null)
                    statusReporter.accept("Error: Nothing to seek.");
            }
        }
    }

    private void seekMp3(float percent) {
        if (mp3TotalFrames == 0) return;

        mp3PausedFrame = (int)(mp3TotalFrames * percent);
        resume();
    }

    private void seekWav(float percent) {
        if (wavClip == null || wavLength == 0) return;

        long target = (long)(wavLength * percent);
        wavPausedPos = target;
        resume();
    }

    private void pauseMp3() {
        if (mp3Player != null) {
            mp3Paused = true;
            mp3Player.close(); // triggers playbackFinished()
            statusReporter.accept("Paused " + fileName);
        }
    }

    private void resumeMp3() {
        if (mp3Paused && currentPath != null) {
            statusReporter.accept("Resumed " + fileName);
            playMp3();
        }
    }

    private void stopMp3() {
        mp3Stopped = true;
        mp3PausedFrame = 0;
        if (mp3Player != null) {
            mp3Player.close();
        }
    }

    // ========== WAV HANDLING ==========

    private void playWav(long time) {
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(new File(currentPath));
            wavClip = AudioSystem.getClip();
            wavClip.open(stream);
            wavLength = wavClip.getMicrosecondLength();

            wavClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP && !wavPaused) {
                    statusReporter.accept("Finished playing: " + fileName);
                    wavPaused = true; // technically paused if finished
                    wavClip.close();
                }
            });

            wavPaused = false;
            wavPausedPos = time;
            wavClip.setMicrosecondPosition(time);
            wavClip.start();

            statusReporter.accept("Now playing: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            statusReporter.accept("Error: " + message);
        }
    }

    private void pauseWav() {
        if (wavClip != null && wavClip.isRunning()) {
            wavPausedPos = wavClip.getMicrosecondPosition();
            wavClip.stop();
            wavPaused = true;
            statusReporter.accept("Paused " + fileName);
        }
    }

    private void resumeWav() {
        if (wavClip != null && wavPaused) {
            if (wavClip.isOpen()) {
                wavClip.setMicrosecondPosition(wavPausedPos);
                wavClip.start();
            } else {
                playWav(wavPausedPos);
            }
            wavPaused = false;
            statusReporter.accept("Resumed " + fileName);
        }
    }

    private void stopWav() {
        wavPaused = false;
        wavPausedPos = 0;
        if (wavClip != null && wavClip.isRunning()) {
            wavClip.stop();
            wavClip.close();
        }
    }
}