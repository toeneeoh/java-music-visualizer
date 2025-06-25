import javazoom.jl.decoder.Bitstream;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.function.Consumer;

/**
 * AudioPlayer is a unified audio playback handler supporting both WAV and MP3 files.
 *
 * Plays, pauses, resumes, seeks, and stops audio.
 *
 * MP3 playback is handled using JLayer (AdvancedPlayer), and WAV uses javax.sound.sampled.
 */
public class AudioPlayer {
    private enum Type { NONE, MP3, WAV }

    private Type currentType = Type.NONE;
    private String currentPath;
    private String fileName;
    private Consumer<String> statusReporter;

    public boolean isPaused = true;
    public boolean isFinished = true;

    // MP3
    private AdvancedPlayer mp3Player;
    private Thread mp3Thread;
    private int mp3PausedFrame = 0;
    private int mp3TotalFrames = 0;
    private final int MP3_FRAME_DURATION_MS = 26;
    private long mp3StartTimeMillis = 0;

    // WAV
    private Thread wavThread;
    private long wavPausedPos = 0;
    private long wavLength = 0;
    private boolean wavStopped = false;

    public float getProgress() {
        return switch (currentType) {
            case MP3 -> mp3TotalFrames > 0 ? Math.min(getCurrentFrame() / (float) mp3TotalFrames, 1f) : 0f;
            case WAV -> wavLength > 0 ? wavPausedPos / (float) wavLength : 0f;
            default -> 0f;
        };
    }

    private int getCurrentFrame() {
        if (isPaused) return mp3PausedFrame;
        return mp3PausedFrame + (int) ((System.currentTimeMillis() - mp3StartTimeMillis) / MP3_FRAME_DURATION_MS);
    }

    public void play(String path, String name, Consumer<String> reporter) {
        stop();
        this.statusReporter = reporter;
        this.currentPath = path;
        this.fileName = name;
        this.isPaused = false;
        this.isFinished = false;

        if (path.endsWith(".mp3")) {
            currentType = Type.MP3;
            mp3PausedFrame = 0;
            playMp3();
        } else if (path.endsWith(".wav")) {
            currentType = Type.WAV;
            wavPausedPos = 0;
            playWav(0);
        } else {
            reporter.accept("Error: Unsupported file type.");
            isPaused = true;
            isFinished = true;
        }
    }

    public void pause() {
        if (currentType == Type.MP3 && mp3Player != null) {
            mp3PausedFrame = getCurrentFrame();
            mp3Player.close();
            statusReporter.accept("Paused " + fileName);
        } else if (currentType == Type.WAV) {
            wavStopped = true;
            statusReporter.accept("Paused " + fileName);
        }

        isPaused = true;
    }

    public void resume() {
        if (isFinished) {
            play(currentPath, fileName, statusReporter);
        } else {
            isPaused = false;
            if (currentType == Type.MP3) {
                playMp3();
                statusReporter.accept("Resumed " + fileName);
            } else if (currentType == Type.WAV) {
                playWav(wavPausedPos);
                statusReporter.accept("Resumed " + fileName);
            }
        }
    }

    public void stop() {
        isPaused = true;
        if (currentType == Type.MP3 && mp3Player != null) {
            mp3Player.close();
        } else if (currentType == Type.WAV) {
            wavStopped = true;
            if (wavThread != null && wavThread.isAlive()) {
                try {
                    wavThread.join();
                } catch (InterruptedException ignored) {}
            }
        }
        resetState();
    }

    private void resetState() {
        isPaused = true;
        isFinished = true;
        mp3PausedFrame = 0;
        wavPausedPos = 0;
    }

    public void seek(float percent) {
        if (isFinished) return;
        switch (currentType) {
            case MP3 -> {
                if (mp3TotalFrames > 0) {
                    mp3PausedFrame = (int)(mp3TotalFrames * percent);
                    resume();
                }
            }
            case WAV -> {
                if (wavLength > 0) {
                    wavPausedPos = (long)(wavLength * percent);
                    resume();
                }
            }
            default -> {}
        }
    }

    private void playMp3() {
        try {
            if (mp3TotalFrames == 0)
                mp3TotalFrames = estimateFrameCount(currentPath);

            FileInputStream fis = new FileInputStream(currentPath);
            mp3Player = new AdvancedPlayer(fis, new VisualizingAudioDevice());

            mp3Thread = new Thread(() -> {
                mp3StartTimeMillis = System.currentTimeMillis();
                mp3Player.setPlayBackListener(new PlaybackListener() {
                    @Override
                    public void playbackFinished(PlaybackEvent evt) {
                        if (!isPaused) {
                            statusReporter.accept("Finished playing: " + fileName);
                            resetState();
                        }
                    }
                });

                try {
                    statusReporter.accept("Now playing: " + fileName);
                    mp3Player.play(mp3PausedFrame, Integer.MAX_VALUE);
                } catch (Exception e) {
                    statusReporter.accept("Error: " + e.getMessage());
                }
            });

            mp3Thread.start();
        } catch (Exception e) {
            statusReporter.accept("Error: " + e.getMessage());
        }
    }

    private int estimateFrameCount(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Bitstream bs = new Bitstream(fis);
            int frames = 0;
            while (bs.readFrame() != null) {
                frames++;
                bs.closeFrame();
            }
            return frames;
        } catch (Exception e) {
            return 0;
        }
    }

    private void playWav(long timeMicros) {
        try {
            if (wavThread != null && wavThread.isAlive()) wavThread.join();

            AudioInputStream originalStream = AudioSystem.getAudioInputStream(new File(currentPath));
            AudioFormat baseFormat = originalStream.getFormat();
            AudioFormat workingFormat = baseFormat;

            if (workingFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                workingFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
                );
                originalStream = AudioSystem.getAudioInputStream(workingFormat, originalStream);
            }

            final AudioFormat finalFormat = workingFormat;
            AudioInputStream audioStream = originalStream;
            wavLength = (long)((audioStream.getFrameLength() * 1_000_000.0) / finalFormat.getFrameRate());
            SourceDataLine line = AudioSystem.getSourceDataLine(finalFormat);
            line.open(finalFormat);

            wavStopped = false;
            wavThread = new Thread(() -> {
                try (audioStream) {
                    byte[] buffer = new byte[2048];
                    line.start();

                    if (timeMicros > 0) {
                        long skipBytes = (long)((timeMicros / 1_000_000.0) * finalFormat.getFrameRate() * finalFormat.getFrameSize());
                        audioStream.skip(skipBytes);
                    }

                    int bytesRead;
                    while (!wavStopped && !isPaused && (bytesRead = audioStream.read(buffer)) != -1) {
                        line.write(buffer, 0, bytesRead);
                        float[] samples = decodePCM(buffer, bytesRead, finalFormat);
                        PCMBuffer.addSamples(samples);
                        wavPausedPos += (long)((bytesRead / (float) finalFormat.getFrameSize()) / finalFormat.getFrameRate() * 1_000_000);
                    }

                    line.drain();
                    line.stop();
                    line.close();

                    if (!isPaused && !wavStopped) {
                        statusReporter.accept("Finished playing: " + fileName);
                        resetState();
                    }
                } catch (Exception e) {
                    statusReporter.accept("Error: " + e.getMessage());
                }
            });

            wavThread.start();
            statusReporter.accept("Now playing: " + fileName);
        } catch (Exception e) {
            statusReporter.accept("Error: " + e.getMessage());
        }
    }

    /**
     * Converts raw PCM byte data into a normalized float array of audio samples.
     *
     * Assumes 16-bit little-endian signed PCM format. Each 2-byte sample is decoded
     * into a float in the range [-1.0, 1.0]
     *
     * @param data   the raw PCM byte array
     * @param length the number of valid bytes to decode
     * @param format the AudioFormat of the input data
     * @return a float array of decoded samples in the range [-1.0, 1.0]
     */
    private float[] decodePCM(byte[] data, int length, AudioFormat format) {
        int sampleCount = length / 2;
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int low = data[2 * i] & 0xFF;
            int high = data[2 * i + 1];
            int sample = (high << 8) | low;
            if (sample > 32767) sample -= 65536;
            samples[i] = sample / 32768f;
        }
        return samples;
    }
}