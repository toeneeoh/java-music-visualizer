import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Dimension;

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
        JMenuItem uploadItem = new JMenuItem("Open");

        // visualizer
        VisualizerPanel visualizerPanel = new VisualizerPanel();
        frame.add(visualizerPanel, BorderLayout.CENTER);

        // control panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // play / pause button
        ImageIcon pauseIcon = new ImageIcon("res/pause.png");
        ImageIcon playIcon  = new ImageIcon("res/play.png");
        JButton playPauseButton = new JButton(playIcon);
        playPauseButton.setPreferredSize(new Dimension(30, 30));
        playPauseButton.setFocusable(false);
        controlPanel.add(playPauseButton);

        playPauseButton.addActionListener(e -> {
            if (audioPlayer.isPaused) {
                audioPlayer.resume();
            } else {
                audioPlayer.pause();
            }
        });

        // seek bar
        JSlider seekBar = new JSlider(0, 100, 0);
        controlPanel.add(seekBar);

        // bottom panel
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());

        // song label
        JLabel songLabel = new JLabel("Now playing: Nothing");
        bottomPanel.add(songLabel, BorderLayout.SOUTH);

        // assemble menu
        fileMenu.add(uploadItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        // south container holds both control panel and bottom panel vertically
        JPanel southContainer = new JPanel();
        southContainer.setLayout(new BoxLayout(southContainer, BoxLayout.Y_AXIS));
        southContainer.add(controlPanel);
        southContainer.add(bottomPanel);

        frame.add(southContainer, BorderLayout.SOUTH);

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
            if (isSeekingInternally) return;

            if (seekBar.getValueIsAdjusting()) {
                if (!seeking) {
                    seeking = true;
                    audioPlayer.pause(); // pause only once at drag start
                }
            } else {
                float percent = seekBar.getValue() / 100f;
                audioPlayer.seek(percent);
                seeking = false;
            }
        });

        // timer
        Timer progressTimer = new Timer(100, e -> {
            if (!seeking && !audioPlayer.isPaused) {
                float progress = audioPlayer.getProgress();
                isSeekingInternally = true;
                seekBar.setValue((int)(progress * 100));
                isSeekingInternally = false;
            }
            // set play button icon based on audio player
            playPauseButton.setIcon(audioPlayer.isPaused ? playIcon : pauseIcon);
            if (audioPlayer.isFinished) {
                isSeekingInternally = true;
                seekBar.setValue(0);
                isSeekingInternally = false;
                playPauseButton.setIcon(playIcon);
            }
        });
        progressTimer.start();

        seekBar.setEnabled(true);
    }

    public static void main(String args[]) {
        new Window();
    }

}