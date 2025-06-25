import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * VisualizerPanel is a Swing component that displays a real-time visualization of audio
 * retrieved from PCMBuffer
 * 
 * Periodically reads the latest PCM sample buffer, performs an in-place FFT,
 * calculates magnitude bands, and draws vertical bars.
 */
public class VisualizerPanel extends JPanel {
    private static final int FFT_SIZE = 1024;
    private static final int BANDS = 64;
    private static final int FRAME_INTERVAL_MS = 16; // ~60 fps
    private static final int VISUAL_DELAY_MS = 500;
    private static final float SMOOTHING_FACTOR = 0.5f;
    private static final float MIN_CLAMP = 0.02f;
    private static final float MAX_CLAMP = 1.0f;

    private float[] magnitudes = new float[BANDS];
    private final LinkedList<float[]> fftDelayQueue = new LinkedList<>();

    public VisualizerPanel() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                float[] samples = PCMBuffer.getSamples();
                if (samples.length >= FFT_SIZE) {
                    float[] window = new float[FFT_SIZE];
                    System.arraycopy(samples, 0, window, 0, FFT_SIZE);

                    float[] real = new float[FFT_SIZE];
                    float[] imag = new float[FFT_SIZE];
                    System.arraycopy(window, 0, real, 0, FFT_SIZE);

                    fft(real, imag);

                    float[] bandMagnitudes = new float[BANDS];
                    for (int i = 0; i < BANDS; i++) {
                        float sum = 0f;
                        int start = i * (FFT_SIZE / 2) / BANDS;
                        int end = (i + 1) * (FFT_SIZE / 2) / BANDS;
                        for (int j = start; j < end; j++) {
                            float mag = (float) Math.sqrt(real[j] * real[j] + imag[j] * imag[j]);
                            sum += mag;
                        }
                        float avg = sum / (end - start);

                        // clamp magnitude
                        avg = Math.max(MIN_CLAMP, Math.min(MAX_CLAMP, avg));
                        bandMagnitudes[i] = avg;
                    }

                    // add to delay queue
                    fftDelayQueue.addLast(bandMagnitudes);
                    int maxQueueSize = VISUAL_DELAY_MS / FRAME_INTERVAL_MS;
                    if (fftDelayQueue.size() > maxQueueSize) {
                        float[] delayed = fftDelayQueue.removeFirst();
                        for (int i = 0; i < BANDS; i++) {
                            // smooth magnitude
                            magnitudes[i] = magnitudes[i] * SMOOTHING_FACTOR + delayed[i] * (1f - SMOOTHING_FACTOR);
                        }
                    }
                }

                repaint();
            }
        }, 0, FRAME_INTERVAL_MS);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight() - 10;
        int barWidth = width / BANDS;

        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, width, height);

        g2.setColor(Color.GREEN);
        for (int i = 0; i < BANDS; i++) {
            int barHeight = (int)(magnitudes[i] * height);
            g2.fillRect(i * barWidth, height - barHeight, barWidth - 2, barHeight);
        }
    }

    /**
     * In-place Cooley-Tukey Radix-2 FFT (Fast Fourier Transform).
     */
    private void fft(float[] real, float[] imag) {
        int n = real.length;

        for (int j = 1, i = 0; j < n - 1; j++) {
            int bit = n >> 1;
            for (; i >= bit; bit >>= 1) i -= bit;
            i += bit;
            if (j < i) {
                float temp = real[j];
                real[j] = real[i];
                real[i] = temp;
                temp = imag[j];
                imag[j] = imag[i];
                imag[i] = temp;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            float angle = (float)(-2 * Math.PI / len);
            float wlenR = (float)Math.cos(angle);
            float wlenI = (float)Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float wR = 1, wI = 0;
                for (int j = 0; j < len / 2; j++) {
                    int u = i + j;
                    int v = i + j + len / 2;
                    float tR = real[v] * wR - imag[v] * wI;
                    float tI = real[v] * wI + imag[v] * wR;
                    real[v] = real[u] - tR;
                    imag[v] = imag[u] - tI;
                    real[u] += tR;
                    imag[u] += tI;
                    float nextWR = wR * wlenR - wI * wlenI;
                    wI = wR * wlenI + wI * wlenR;
                    wR = nextWR;
                }
            }
        }
    }
}
