package comp3011.assignment1;

import java.io.IOException;
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

    private final SubtitleTranscriptionService subtitleService;

    public SubtitleController(SubtitleTranscriptionService subtitleService) {
        this.subtitleService = subtitleService;
    }

    @PostMapping("/subtitles")
    public ResponseEntity<List<SubtitleItem>> getSubtitles(
            @RequestParam("file") MultipartFile file) throws IOException {

        List<SubtitleItem> subtitles =
                subtitleService.transcribe(file);

        return ResponseEntity.ok(subtitles);
    }
}