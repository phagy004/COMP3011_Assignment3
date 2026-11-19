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

    //Stores the current subtitle state.
    //This value is initially controlled by the -S command line option.
    private boolean subtitleEnabled;

    //Keeps a reference to the subtitle processor.
    //This allows subtitles to be enabled or disabled while the video is playing.
    private final SubtitleFrameProcessor subtitleProcessor;

    //Handles audio extraction and communication with the subtitle web server.
    private final SubtitleService subtitleService = new SubtitleService();

    public VideoPlayerController(
            boolean audioEnabled,
            boolean subtitleEnabled,
            List<FrameProcessor> frameProcessors,
            BiConsumer<Integer, Integer> videoSizeChangedHandler) {

        this.view = new VideoPlayerView();
        this.videoSizeChangedHandler = videoSizeChangedHandler;

        //Use the command line subtitle option as the starting state.
        this.subtitleEnabled = subtitleEnabled;

        SubtitleFrameProcessor foundSubtitleProcessor = null;

        //If -S was used, CommandLineController has already added a
        //SubtitleFrameProcessor to the normal frame processing pipeline.
        for (FrameProcessor processor : frameProcessors) {
            if (processor instanceof SubtitleFrameProcessor) {
                foundSubtitleProcessor = (SubtitleFrameProcessor) processor;
                break;
            }
        }

        //Subtitles must also be switchable when -S was not used.
        //Therefore a disabled subtitle processor is still added to the pipeline.
        if (foundSubtitleProcessor == null) {
            foundSubtitleProcessor = new SubtitleFrameProcessor();
            foundSubtitleProcessor.setEnabled(false);
            frameProcessors.add(foundSubtitleProcessor);
        }

        subtitleProcessor = foundSubtitleProcessor;

        //Make sure the processor and the subtitle button begin in the same state.
        subtitleProcessor.setEnabled(subtitleEnabled);

        model = new VideoPlayerModel(
                audioEnabled,
                frameProcessors,
                this::onVideoSizeChanged,
                this::onFrameReady,
                this::onStatusChanged,
                this::onPlaybackStateChanged,
                this::onAudioOutputStateChanged);

        //Connect the player controls to their controller actions.
        view.getStartOverButton().setOnAction(_ -> startOver());
        view.getBackFiveButton().setOnAction(_ -> backFiveSeconds());
        view.getPausePlayButton().setOnAction(_ -> togglePause());
        view.getForwardFiveButton().setOnAction(_ -> forwardFiveSeconds());
        view.getStopButton().setOnAction(_ -> stopAndExit());
        view.getAudioButton().setOnAction(_ -> toggleAudioOutput());
        view.getSubtitleButton().setOnAction(_ -> toggleSubtitles());

        //Show the initial audio and subtitle states in the UI.
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
                //The S shortcut has the same behaviour as pressing the CC button.
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

        //Clear subtitle data from any previously opened media file.
        subtitleProcessor.setSubtitles(List.of());

        //Start playback immediately.
        //Subtitle transcription happens separately so a slow network request
        //does not stop the user from watching the video.
        model.play(file);

        //Audio extraction, server communication and transcription all happen
        //asynchronously. The processor receives the subtitle list when ready.
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

    //Used by both the CC button and the S keyboard shortcut.
    //Disabling subtitles does not stop playback or delete the subtitle data.
    private void toggleSubtitles() {
        subtitleEnabled = !subtitleEnabled;

        //The processor immediately stops or resumes drawing subtitles.
        subtitleProcessor.setEnabled(subtitleEnabled);

        //Change the CC icon so the user can see the current state.
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