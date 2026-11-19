package comp3011.assignment1;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

//REST controller used by the desktop video player to request subtitles.
@RestController
@RequestMapping("/api/v1")
public class SubtitleController {

    private final SubtitleTranscriptionService subtitleService;

    //Spring provides the transcription service when the controller is created.
    public SubtitleController(
            SubtitleTranscriptionService subtitleService) {

        this.subtitleService = subtitleService;
    }

    //Receives the extracted media audio from the desktop player.
    //The transcription service sends the audio to Whisper and returns
    //a list containing subtitle text and timing information.
    @PostMapping("/subtitles")
    public ResponseEntity<List<SubtitleItem>> getSubtitles(
            @RequestParam("file") MultipartFile file)
            throws IOException {

        List<SubtitleItem> subtitles =
                subtitleService.transcribe(file);

        //Spring automatically converts the subtitle list into JSON.
        return ResponseEntity.ok(subtitles);
    }
}