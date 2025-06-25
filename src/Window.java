import java.awt.BorderLayout;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Window {
    private boolean seeking = false;
    private AudioPlayer audioPlayer = new AudioPlayer();
    private boolean isSeekingInternally = false;

    public Window() {
        JFrame frame = new JFrame("Music Visualizer");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 350);
        frame.setLayout(new BorderLayout());
        frame.setLocationRelativeTo(null);

        // menu bar
        JMenuBar menuBar = new JMenuBar();

        // file menu
        JMenu fileMenu = new JMenu("File");

        // upload button
        JMenuItem uploadItem = new JMenuItem("Upload");

        // center placeholder
        frame.add(new VisualizerPanel(), BorderLayout.CENTER);

        // bottom panel

        // bottom panel
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());

        // seek bar
        JSlider seekBar = new JSlider(0, 100, 0);
        bottomPanel.add(seekBar, BorderLayout.NORTH);

        // song label
        JLabel songLabel = new JLabel("Now playing: Nothing");
        bottomPanel.add(songLabel, BorderLayout.SOUTH);

        // assemble menu
        fileMenu.add(uploadItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // make frame visible
        frame.setVisible(true);

        // upload listener
        uploadItem.addActionListener((ActionEvent e) -> {
            JFileChooser fileChooser = new JFileChooser();

            // restrict file types to audio
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Audio Files (*.mp3, *.wav)", "mp3", "wav"
            );
            fileChooser.setFileFilter(filter);

            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                String path = selectedFile.getAbsolutePath();
                String name = selectedFile.getName();
                audioPlayer.play(path, name, songLabel::setText);
            }
        });

        // user-initiated seeking
        seekBar.addChangeListener(e -> {
            if (isSeekingInternally) return; // Ignore programmatic changes
            if (seekBar.getValueIsAdjusting()) {
                if (!seeking) {
                    seeking = true;
                    audioPlayer.pause(); // Pause only once at drag start
                }
            } else {
                float percent = seekBar.getValue() / 100f;
                audioPlayer.seek(percent);
                seeking = false;
            }
        });

        // timer
        Timer progressTimer = new Timer(200, e -> {
            if (!seeking) {
                float progress = audioPlayer.getProgress();
                isSeekingInternally = true;
                seekBar.setValue((int)(progress * 100));
                isSeekingInternally = false;
            }
        });
        progressTimer.start();

        seekBar.setEnabled(true);
    }

    public static void main(String args[]) {
        new Window();
    }

}