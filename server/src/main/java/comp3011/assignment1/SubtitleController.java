package comp3011.assignment1;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class SubtitleController {

    @PostMapping("/subtitles")
    public ResponseEntity<List<SubtitleItem>> getSubtitles(
            @RequestParam("file") MultipartFile file) {

        //temporary response for testing
        List<SubtitleItem> subtitles = List.of(
                new SubtitleItem("This came from the server", 2.0, 5.0),
                new SubtitleItem("The subtitle endpoint is working", 6.0, 9.0),
                new SubtitleItem("Next we connect Whisper", 10.0, 14.0)
        );

        return ResponseEntity.ok(subtitles);
    }
}