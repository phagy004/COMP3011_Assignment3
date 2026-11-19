package comp3011;

import org.bytedeco.javacv.Frame;

public class SubtitleFrameProcessor extends FrameProcessor {

    private boolean enabled = true;

    public SubtitleFrameProcessor() {
        super();
    }

    @Override
    public void process(
            Frame frame,
            InfoFrame info)
            throws Exception {

        //subtitle rendering will be added later
        if (!enabled) {
            return;
        }
    }

    public void setEnabled(
            boolean enabled) {

        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}