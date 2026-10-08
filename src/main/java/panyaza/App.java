package panyaza;

import java.io.File;
import java.util.List;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.Group;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {
    private final ObservableList<Track> tracks = FXCollections.observableArrayList();
    private MediaPlayer mediaPlayer;
    private Track currentTrack;
    private ListView<Track> playlist;
    private Slider progress;
    private Button play;
    private Button mainPlay;
    private Button miniPlay;
    private Button previous;
    private Button rewind;
    private Button forward;
    private Button next;
    private Button volumeButton;
    private Label playbackTime;
    private Label fileName;
    private MediaView mediaView;
    private Canvas waveform;
    private StackPane audioArtwork;
    private double volumeLevel = 1.0;
    private boolean muted;

    private ImageView icon(String name, double size) {
        Image image = new Image(getClass().getResourceAsStream("/panyaza/icons/" + name));
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private Button button(String symbol, String description, String styleClass) {
        Button button = new Button(symbol);
        button.getStyleClass().add(styleClass);
        button.setAccessibleText(description);
        button.setFocusTraversable(true);
        button.setMnemonicParsing(false);
        return button;
    }

    private Button iconButton(String iconName, String description, String styleClass, double size) {
        Button button = new Button();
        button.setGraphic(icon(iconName, size));
        button.getStyleClass().add(styleClass);
        button.setAccessibleText(description);
        button.setFocusTraversable(true);
        button.setMnemonicParsing(false);
        return button;
    }

    private void seekBy(double seconds) {
        if (mediaPlayer == null) {
            return;
        }
        Duration target = mediaPlayer.getCurrentTime().add(Duration.seconds(seconds));
        Duration duration = mediaPlayer.getTotalDuration();
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) {
            mediaPlayer.seek(new Duration(Math.max(0, target.toMillis())));
        } else {
            mediaPlayer.seek(new Duration(Math.max(0,
                    Math.min(duration.toMillis(), target.toMillis()))));
        }
    }

    private void playSelectedTrack() {
        Track selected = playlist.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        playTrack(selected);
    }

    private void playTrack(Track track) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        mediaPlayer = null;
        currentTrack = null;

        try {
            MediaPlayer newPlayer = new MediaPlayer(new Media(track.file.toURI().toString()));
            mediaPlayer = newPlayer;
            currentTrack = track;
            newPlayer.setVolume(volumeLevel);
            newPlayer.setMute(muted);
            fileName.setText(track.name);
            newPlayer.setOnReady(() -> {
                progress.setMin(0);
                progress.setMax(newPlayer.getTotalDuration().toSeconds());
                updateTimeLabel(Duration.ZERO, newPlayer.getTotalDuration());
                updateTransportButtons();
            });
            newPlayer.setOnPlaying(this::updateTransportButtons);
            newPlayer.setOnPaused(this::updateTransportButtons);
            newPlayer.currentTimeProperty().addListener((observable, oldTime, currentTime) -> {
                progress.setValue(currentTime.toSeconds());
                updateTimeLabel(currentTime, newPlayer.getTotalDuration());
                drawWaveform();
            });
            newPlayer.setOnEndOfMedia(() -> {
                int nextIndex = playlist.getSelectionModel().getSelectedIndex() + 1;
                if (nextIndex < tracks.size()) {
                    playlist.getSelectionModel().select(nextIndex);
                    playSelectedTrack();
                } else {
                    updateTransportButtons();
                }
            });
            newPlayer.setOnError(() -> showMediaError(track, newPlayer));
            mediaView.setMediaPlayer(track.isVideo() ? newPlayer : null);
            audioArtwork.setVisible(!track.isVideo());
            playlist.getSelectionModel().select(track);
            newPlayer.play();
            updateTransportButtons();
        } catch (RuntimeException exception) {
            showError("Unable to play this file", exception.getMessage());
        }
    }

    private void togglePlayback() {
        Track selected = playlist.getSelectionModel().getSelectedItem();
        if (selected != null && selected != currentTrack) {
            playSelectedTrack();
            return;
        }
        if (mediaPlayer == null) {
            playSelectedTrack();
            return;
        }
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
        updateTransportButtons();
    }

    private void stopPlayback() {
        if (mediaPlayer == null) {
            return;
        }
        mediaPlayer.stop();
        mediaPlayer.seek(Duration.ZERO);
        progress.setValue(0);
        updateTimeLabel(Duration.ZERO, mediaPlayer.getTotalDuration());
        updateTransportButtons();
    }

    private void updateTransportButtons() {
        boolean playing = mediaPlayer != null
                && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
        String iconName = playing ? "pause.png" : "play.png";
        String label = playing ? "Pause" : "Play";
        play.setGraphic(icon(iconName, 20));
        play.setAccessibleText(label);
        mainPlay.setGraphic(icon(iconName, 42));
        mainPlay.setAccessibleText(label);
        miniPlay.setGraphic(icon(iconName, 16));
        miniPlay.setAccessibleText(label);
        mainPlay.setVisible(!playing);
        mainPlay.setManaged(!playing);

        boolean hasTracks = !tracks.isEmpty();
        play.setDisable(!hasTracks);
        mainPlay.setDisable(!hasTracks);
        miniPlay.setDisable(!hasTracks);
        previous.setDisable(!hasTracks);
        rewind.setDisable(!hasTracks);
        forward.setDisable(!hasTracks);
        next.setDisable(!hasTracks);
    }

    private void updateTimeLabel(Duration elapsed, Duration duration) {
        playbackTime.setText(formatTime(elapsed) + " / " + formatTime(duration));
    }

    private void drawWaveform() {
        if (waveform == null || waveform.getWidth() <= 0 || waveform.getHeight() <= 0) {
            return;
        }
        GraphicsContext graphics = waveform.getGraphicsContext2D();
        double width = waveform.getWidth();
        double height = waveform.getHeight();
        graphics.clearRect(0, 0, width, height);

        double played = progress.getMax() > 0 ? progress.getValue() / progress.getMax() : 0;
        int bars = Math.max(1, (int) (width / 5));
        for (int i = 0; i < bars; i++) {
            double wave = Math.abs(Math.sin(i * 1.71) * Math.cos(i * 0.39));
            double barHeight = 5 + wave * (height - 10);
            double x = i * width / bars;
            double y = (height - barHeight) / 2;
            graphics.setFill(i / (double) bars <= played
                    ? Color.web("#b9fff2") : Color.web("#69b9b5"));
            graphics.fillRoundRect(x, y, 2.2, barHeight, 2.2, 2.2);
        }
    }

    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) {
            return "00:00";
        }
        long seconds = Math.max(0, (long) duration.toSeconds());
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void showMediaError(Track track, MediaPlayer player) {
        String message = player.getError() == null
                ? "The selected media file could not be played. Check that its format and codecs are supported."
                : player.getError().getMessage();
        showError("Unable to play " + track.name, message);
        updateTransportButtons();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Media Player");
        alert.setHeaderText(title);
        alert.setContentText(message == null || message.isEmpty()
                ? "Please choose a supported audio file and try again."
                : message);
        alert.show();
    }

    @Override
    public void start(Stage stage) {
        Label signal = new Label("♫  Panyaza's Mediaplayer");
        signal.getStyleClass().add("status-icon");
        Label time = new Label("AUDIO  •  VIDEO");
        time.getStyleClass().add("status-time");

        BorderPane statusBar = new BorderPane();
        statusBar.setLeft(signal);
        statusBar.setCenter(time);
        statusBar.getStyleClass().add("status-bar");

        mainPlay = iconButton("play.png", "Play", "main-play", 42);
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.setSmooth(true);
        Label musicNote = new Label("♫");
        musicNote.getStyleClass().add("music-note");
        audioArtwork = new StackPane(
                new Circle(78, Color.TRANSPARENT),
                new Circle(62, Color.TRANSPARENT),
                new Circle(46, Color.TRANSPARENT),
                musicNote);
        audioArtwork.getStyleClass().add("audio-artwork");
        audioArtwork.getChildren().get(0).getStyleClass().add("artwork-ring-outer");
        audioArtwork.getChildren().get(1).getStyleClass().add("artwork-ring-middle");
        audioArtwork.getChildren().get(2).getStyleClass().add("artwork-ring-inner");
        StackPane video = new StackPane(audioArtwork, mediaView, mainPlay);
        mediaView.fitWidthProperty().bind(video.widthProperty());
        mediaView.fitHeightProperty().bind(video.heightProperty());
        video.getStyleClass().add("video");
        VBox.setVgrow(video, Priority.ALWAYS);

        waveform = new Canvas(320, 44);
        waveform.setMouseTransparent(true);
        StackPane waveformArea = new StackPane(waveform);
        waveform.widthProperty().bind(waveformArea.widthProperty());
        waveform.setHeight(44);
        waveform.widthProperty().addListener((observable, oldWidth, newWidth) -> drawWaveform());

        progress = new Slider(0, 1, 0);
        progress.setAccessibleText("Playback position");
        progress.setFocusTraversable(true);
        progress.getStyleClass().add("progress");

        miniPlay = iconButton("play.png", "Play", "small-control", 16);
        volumeButton = iconButton("volume.png", "Mute audio", "small-control", 20);
        volumeButton.getStyleClass().add("volume-control");
        playbackTime = new Label("00:00 / 00:00");
        playbackTime.getStyleClass().add("playback-time");
        HBox timelineControls = new HBox(10, miniPlay, progress, playbackTime, volumeButton);
        timelineControls.setAlignment(Pos.CENTER);
        fileName = new Label("No media selected");
        fileName.getStyleClass().add("file-name");
        fileName.setMaxWidth(Double.MAX_VALUE);
        fileName.setEllipsisString("...");
        VBox timeline = new VBox(5, fileName, waveformArea, timelineControls);
        timeline.getStyleClass().add("timeline");
        HBox.setHgrow(progress, Priority.ALWAYS);
        VBox.setVgrow(waveformArea, Priority.NEVER);

        Label playlistTitle = new Label("Playlist");
        playlistTitle.getStyleClass().add("playlist-title");
        Button addMusic = button("+ Add media", "Add music or videos to playlist", "add-music");
        Button removeTrackButton = iconButton("trash.png", "Remove selected track from playlist", "add-music", 18);
        HBox playlistHeader = new HBox(24, playlistTitle, addMusic, removeTrackButton);
        playlistHeader.setAlignment(Pos.CENTER_LEFT);

        playlist = new ListView<>(tracks);
        playlist.getStyleClass().add("playlist");
        playlist.setAccessibleText("Media playlist");
        playlist.setPlaceholder(new Label("Add music or videos to start your playlist"));
        playlist.setCellFactory(view -> new javafx.scene.control.ListCell<Track>() {
            @Override
            protected void updateItem(Track track, boolean empty) {
                super.updateItem(track, empty);
                if (empty || track == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                } else {
                    ImageView trackIcon = icon(track.isVideo() ? "play.png" : "heart.png", 16);
                    setGraphic(trackIcon);
                    setText(track.name);
                    MenuItem removeItem = new MenuItem("Remove from playlist");
                    removeItem.setOnAction(e -> removeTrack(track));
                    ContextMenu menu = new ContextMenu(removeItem);
                    setContextMenu(menu);
                }
            }
        });
        playlist.getSelectionModel().selectedItemProperty().addListener((observable, oldTrack, track) -> {
            if (track != null) {
                fileName.setText(track.name);
            }
        });
        playlist.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                playSelectedTrack();
            }
        });
        playlist.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                playSelectedTrack();
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                Track selected = playlist.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    removeTrack(selected);
                    event.consume();
                }
            }
        });
        VBox.setVgrow(playlist, Priority.ALWAYS);

        play = iconButton("play.png", "Play", "transport-control", 20);
        play.getStyleClass().add("transport-play");
        rewind = iconButton("rewind.png", "Rewind 10 seconds", "transport-control", 20);
        forward = iconButton("fast-forward.png", "Forward 10 seconds", "transport-control", 20);
        previous = iconButton("prev.png", "Play previous song", "transport-control", 20);
        next = iconButton("next.png", "Play next song", "transport-control", 20);

        HBox transport = new HBox(18, previous, rewind, play, forward, next);
        transport.setAlignment(Pos.CENTER);
        transport.getStyleClass().add("transport");

        VBox playlistSection = new VBox(12, playlistHeader, playlist, transport);
        playlistSection.setPadding(new Insets(18, 22, 20, 22));
        playlistSection.getStyleClass().add("playlist-section");
        VBox.setVgrow(playlistSection, Priority.ALWAYS);

        VBox player = new VBox(statusBar, video, timeline, playlistSection);
        player.getStyleClass().add("player");
        player.setMaxWidth(610);
        player.setMaxHeight(800);
        VBox.setVgrow(video, Priority.ALWAYS);

        StackPane root = new StackPane(player);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("background");

        Scene scene = new Scene(root, 460, 760);
        scene.getStylesheets().add(getClass().getResource("/panyaza/player.css").toExternalForm());

        Runnable togglePlayback = this::togglePlayback;
        play.setOnAction(event -> togglePlayback.run());
        mainPlay.setOnAction(event -> togglePlayback.run());
        miniPlay.setOnAction(event -> togglePlayback.run());

        addMusic.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Add media to playlist");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Audio and video files", "*.mp3", "*.wav", "*.aiff", "*.aif", "*.m4a",
                    "*.mp4", "*.m4v", "*.mov"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Audio files", "*.mp3", "*.wav", "*.aiff", "*.aif", "*.m4a"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Video files", "*.mp4", "*.m4v", "*.mov"));
            List<File> selectedFiles = chooser.showOpenMultipleDialog(stage);
            if (selectedFiles != null) {
                for (File file : selectedFiles) {
                    tracks.add(new Track(file));
                }
                if (playlist.getSelectionModel().getSelectedItem() == null && !tracks.isEmpty()) {
                    playlist.getSelectionModel().selectFirst();
                }
                updateTransportButtons();
            }
        });

        removeTrackButton.setOnAction(event -> {
            Track selected = playlist.getSelectionModel().getSelectedItem();
            if (selected != null) {
                removeTrack(selected);
            }
        });

        previous.setOnAction(event -> {
            int index = playlist.getSelectionModel().getSelectedIndex();
            if (index > 0) {
                playlist.getSelectionModel().select(index - 1);
                playSelectedTrack();
            } else if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.ZERO);
            }
        });
        next.setOnAction(event -> {
            int index = playlist.getSelectionModel().getSelectedIndex();
            if (index >= 0 && index + 1 < tracks.size()) {
                playlist.getSelectionModel().select(index + 1);
                playSelectedTrack();
            }
        });
        rewind.setOnAction(event -> seekBy(-10));
        forward.setOnAction(event -> seekBy(10));
        progress.setOnMouseReleased(event -> seekToSliderPosition());
        progress.setOnKeyReleased(event -> seekToSliderPosition());
        progress.valueProperty().addListener((observable, oldValue, newValue) -> drawWaveform());

        volumeButton.setOnAction(event -> toggleMute());

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            Node focused = scene.getFocusOwner();
            KeyCode key = event.getCode();
            boolean sliderFocused = focused instanceof Slider;
            boolean playlistFocused = playlist.isFocused();
            boolean buttonFocused = focused instanceof Button;

            if (key == KeyCode.N) {
                next.fire();
                event.consume();
            } else if (key == KeyCode.P) {
                previous.fire();
                event.consume();
            } else if (key == KeyCode.S) {
                stopPlayback();
                event.consume();
            } else if (key == KeyCode.SPACE && !buttonFocused) {
                togglePlayback();
                event.consume();
            } else if (key == KeyCode.M) {
                toggleMute();
                event.consume();
            } else if (key == KeyCode.F) {
                stage.setFullScreen(true);
                event.consume();
            } else if (key == KeyCode.ESCAPE && stage.isFullScreen()) {
                stage.setFullScreen(false);
                event.consume();
            } else if (key == KeyCode.UP && !sliderFocused && !playlistFocused) {
                adjustVolume(0.05);
                event.consume();
            } else if (key == KeyCode.DOWN && !sliderFocused && !playlistFocused) {
                adjustVolume(-0.05);
                event.consume();
            } else if ((key == KeyCode.LEFT || key == KeyCode.RIGHT)
                    && (!sliderFocused && !playlistFocused || hasSeekModifier(event))) {
                seekBy(seekAmount(event) * (key == KeyCode.LEFT ? -1 : 1));
                event.consume();
            }
        });

        stage.setTitle("Media Player");
        stage.setMinWidth(370);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (mediaPlayer != null) {
                mediaPlayer.dispose();
            }
        });
        stage.show();
        updateTransportButtons();
    }

    private void removeTrack(Track track) {
        int index = tracks.indexOf(track);
        if (index < 0) {
            return;
        }
        boolean wasCurrent = track == currentTrack;
        if (wasCurrent && mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
            currentTrack = null;
            mediaView.setMediaPlayer(null);
            audioArtwork.setVisible(true);
            fileName.setText("No media selected");
            progress.setValue(0);
            updateTimeLabel(Duration.ZERO, Duration.ZERO);
        }
        tracks.remove(track);
        if (wasCurrent) {
            if (!tracks.isEmpty()) {
                int newIndex = Math.min(index, tracks.size() - 1);
                playlist.getSelectionModel().select(newIndex);
            } else {
                playlist.getSelectionModel().clearSelection();
            }
            updateTransportButtons();
        }
    }

    private void seekToSliderPosition() {
        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.seconds(progress.getValue()));
        }
    }

    private boolean hasSeekModifier(KeyEvent event) {
        return event.isShiftDown() || event.isAltDown() || event.isControlDown();
    }

    private double seekAmount(KeyEvent event) {
        if (event.isControlDown() && event.isAltDown()) {
            return 300;
        }
        if (event.isControlDown()) {
            return 60;
        }
        if (event.isAltDown()) {
            return 10;
        }
        if (event.isShiftDown()) {
            return 3;
        }
        return 5;
    }

    private void toggleMute() {
        muted = !muted;
        if (mediaPlayer != null) {
            mediaPlayer.setMute(muted);
        }
        volumeButton.setGraphic(icon(muted ? "mute.png" : "volume.png", 20));
        volumeButton.setAccessibleText(muted ? "Unmute audio" : "Mute audio");
    }

    private void adjustVolume(double amount) {
        volumeLevel = Math.max(0, Math.min(1, volumeLevel + amount));
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volumeLevel);
        }
        if (volumeLevel == 0 && !muted) {
            muted = true;
            if (mediaPlayer != null) {
                mediaPlayer.setMute(true);
            }
        } else if (volumeLevel > 0 && muted) {
            muted = false;
            if (mediaPlayer != null) {
                mediaPlayer.setMute(false);
            }
        }
        volumeButton.setGraphic(icon(muted ? "mute.png" : "volume.png", 20));
        volumeButton.setAccessibleText(muted ? "Unmute audio" : "Mute audio");
    }

    private static final class Track {
        private final File file;
        private final String name;
        private final boolean video;

        private Track(File file) {
            this.file = file;
            this.name = file.getName();
            String lowerCaseName = name.toLowerCase(java.util.Locale.ROOT);
            this.video = lowerCaseName.endsWith(".mp4")
                    || lowerCaseName.endsWith(".m4v")
                    || lowerCaseName.endsWith(".mov");
        }

        private boolean isVideo() {
            return video;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}