package comp3011;

import java.util.List;

import org.bytedeco.javacv.Frame;

public class SubtitleFrameProcessor extends FrameProcessor {

    //can be changed from the JavaFX thread while video is playing
    private volatile boolean enabled = true;

    //current subtitle data returned from the server
    private volatile List<SubtitleSegment> subtitles =
            List.of();

    public SubtitleFrameProcessor() {
        super();
    }

    @Override
    public void process(
            Frame frame,
            InfoFrame info)
            throws Exception {

        if (!enabled) {
            return;
        }

        //find the subtitle for the current frame time
        SubtitleSegment subtitle =
                findSubtitle(
                        info.timestampUs()
                );

        if (subtitle == null) {
            return;
        }

        //subtitle drawing will be added next
    }

    public void setEnabled(
            boolean enabled) {

        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSubtitles(
            List<SubtitleSegment> subtitles) {

        //copy the list so it cannot change while a frame is using it
        this.subtitles =
                List.copyOf(
                        subtitles
                );
    }

    public SubtitleSegment findSubtitle(
            long timestampUs) {

        double timeSeconds =
                timestampUs / 1_000_000.0;

        for (SubtitleSegment subtitle :
                subtitles) {

            if (subtitle.isVisibleAt(
                    timeSeconds
            )) {

                return subtitle;
            }
        }

        return null;
    }
}