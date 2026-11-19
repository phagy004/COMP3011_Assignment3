package comp3011;

import java.io.File;
import java.util.List;

public class SubtitleService {

    public List<SubtitleSegment> getSubtitles(File videoFile) {
        //temporary data until the web server is connected
        return List.of(
                new SubtitleSegment("This is a subtitle test", 2.0, 5.0),
                new SubtitleSegment("The subtitle should change here", 6.0, 9.0),
                new SubtitleSegment("Press S to turn subtitles off", 10.0, 14.0)
        );
    }
}