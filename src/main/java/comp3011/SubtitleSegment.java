package comp3011;

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

    public boolean isVisibleAt(
            double timeSeconds) {

        return timeSeconds >= startSeconds
                && timeSeconds <= endSeconds;
    }
}