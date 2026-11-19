package comp3011.assignment1;

//Simple data object returned by the subtitle REST endpoint.
//Spring automatically serialises each record into JSON.
public record SubtitleItem(
        String text,
        double start,
        double end) {
}