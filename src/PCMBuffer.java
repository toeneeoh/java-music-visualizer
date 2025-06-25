/**
 * PCMBuffer is a singleton utility class that holds a fixed-length buffer of audio sample data
 * as a float array
 */
public class PCMBuffer {
    private static final int FFT_SIZE = 1024;
    private static final float[] buffer = new float[FFT_SIZE];

    public static synchronized void addSamples(float[] samples) {
        int len = Math.min(samples.length, FFT_SIZE);
        System.arraycopy(samples, 0, buffer, 0, len);
        for (int i = len; i < FFT_SIZE; i++) {
            buffer[i] = 0;
        }
    }

    public static synchronized float[] getSamples() {
        return buffer.clone();
    }
}
