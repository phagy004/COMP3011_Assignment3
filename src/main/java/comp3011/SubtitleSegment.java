package comp3011;

//Represents one subtitle received by the desktop video player.
//Each subtitle contains its text and the time range when it should appear.
public class SubtitleSegment {

    private final String text;
    private final double startSeconds;
    private final double endSeconds;

    public SubtitleSegment(
            String text,
            double startSeconds,
            double endSeconds) {

        this.text = text;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
    }

    public String getText() {
        return text;
    }

    public double getStartSeconds() {
        return startSeconds;
    }

    public double getEndSeconds() {
        return endSeconds;
    }

    //Checks whether this subtitle should be visible at the current
    //video playback position.
    public boolean isVisibleAt(double timeSeconds) {
        return timeSeconds >= startSeconds
                && timeSeconds <= endSeconds;
    }
}