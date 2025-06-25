import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.JavaSoundAudioDevice;

/**
 * VisualizingAudioDevice is a custom audio device used with the JLayer MP3 decoder.
 * 
 * It extends JavaSoundAudioDevice to both output MP3 audio to speakers and extract
 * PCM data for visualization
 * 
 * This device forwards the decoded PCM samples from JLayer into the PCMBuffer
 */
public class VisualizingAudioDevice extends JavaSoundAudioDevice {
    @Override
    public void open(Decoder decoder) {
        try {
            super.open(decoder);
        } catch (JavaLayerException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void writeImpl(short[] samples, int offs, int len) {
        try {
            super.writeImpl(samples, offs, len);
        } catch (JavaLayerException e) {
            e.printStackTrace();
        }

        float[] pcm = new float[len];
        for (int i = 0; i < len; i++) {
            pcm[i] = samples[offs + i] / 32768f; // normalize to [-1.0, 1.0]
        }

        PCMBuffer.addSamples(pcm);
    }
}
