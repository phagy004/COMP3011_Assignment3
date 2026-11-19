package comp3011;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class VideoPlayerController {

    private final VideoPlayerModel model;
    private final VideoPlayerView view;
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;
    private boolean subtitleEnabled;
    private final SubtitleFrameProcessor subtitleProcessor;
    private final SubtitleService subtitleService = new SubtitleService();

    public VideoPlayerController(
            boolean audioEnabled,
            boolean subtitleEnabled,
            List<FrameProcessor> frameProcessors,
            BiConsumer<Integer, Integer> videoSizeChangedHandler) {

        this.view = new VideoPlayerView();
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        this.subtitleEnabled = subtitleEnabled;

        SubtitleFrameProcessor foundSubtitleProcessor = null;

        for (FrameProcessor processor : frameProcessors) {
            if (processor instanceof SubtitleFrameProcessor) {
                foundSubtitleProcessor = (SubtitleFrameProcessor) processor;
                break;
            }
        }

        if (foundSubtitleProcessor == null) {
            foundSubtitleProcessor = new SubtitleFrameProcessor();
            foundSubtitleProcessor.setEnabled(false);
            frameProcessors.add(foundSubtitleProcessor);
        }

        subtitleProcessor = foundSubtitleProcessor;
        subtitleProcessor.setEnabled(subtitleEnabled);

        model = new VideoPlayerModel(
                audioEnabled,
                frameProcessors,
                this::onVideoSizeChanged,
                this::onFrameReady,
                this::onStatusChanged,
                this::onPlaybackStateChanged,
                this::onAudioOutputStateChanged);

        view.getStartOverButton().setOnAction(_ -> startOver());
        view.getBackFiveButton().setOnAction(_ -> backFiveSeconds());
        view.getPausePlayButton().setOnAction(_ -> togglePause());
        view.getForwardFiveButton().setOnAction(_ -> forwardFiveSeconds());
        view.getStopButton().setOnAction(_ -> stopAndExit());
        view.getAudioButton().setOnAction(_ -> toggleAudioOutput());
        view.getSubtitleButton().setOnAction(_ -> toggleSubtitles());

        view.showAudioEnabled(model.isAudioOutputEnabled());
        view.showSubtitleEnabled(subtitleEnabled);
    }

    public void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stopAndExit();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE) {
                togglePause();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT) {
                backFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                forwardFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.HOME) {
                startOver();
                event.consume();
            } else if (event.getCode() == KeyCode.A) {
                toggleAudioOutput();
                event.consume();
            } else if (event.getCode() == KeyCode.S) {
                toggleSubtitles();
                event.consume();
            }
        });
    }

    public VideoPlayerView getView() {
        return view;
    }

    public boolean isSubtitleEnabled() {
        return subtitleEnabled;
    }

    public void play(File file) {
        view.setPlaybackControlsDisabled(false);
        subtitleProcessor.setSubtitles(List.of());

        model.play(file);

        subtitleService.getSubtitles(file)
                .thenAccept(subtitleProcessor::setSubtitles);
    }

    public void shutdown() {
        model.shutdown();
    }

    private void startOver() {
        model.startOver();
    }

    private void backFiveSeconds() {
        model.backFiveSeconds();
    }

    private void togglePause() {
        model.togglePause();
    }

    private void forwardFiveSeconds() {
        model.forwardFiveSeconds();
    }

    private void toggleAudioOutput() {
        model.toggleAudioOutput();
    }

    private void toggleSubtitles() {
        subtitleEnabled = !subtitleEnabled;
        subtitleProcessor.setEnabled(subtitleEnabled);
        view.showSubtitleEnabled(subtitleEnabled);
    }

    private void stopAndExit() {
        model.stopPlayback();
        javafx.application.Platform.exit();
    }

    private void onVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    private void onFrameReady(Image image) {
        view.setImage(image);
    }

    private void onStatusChanged(String status) {
        view.setStatus(status);
    }

    private void onPlaybackStateChanged(boolean playing, boolean paused) {
        if (playing && !paused) {
            view.showPauseAction();
        } else {
            view.showPlayAction();
        }
    }

    private void onAudioOutputStateChanged(boolean audioOutputEnabled) {
        view.showAudioEnabled(audioOutputEnabled);
    }
}