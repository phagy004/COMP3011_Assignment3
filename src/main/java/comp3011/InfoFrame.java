package comp3011;

public record InfoFrame(
        int frameNumber,
        long timestampUs,
        long playbackTimestampUs) {
}