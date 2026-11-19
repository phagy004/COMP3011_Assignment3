package comp3011.assignment1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SubtitleTranscriptionService {

    private static final String OPENAI_URL =
            "https://api.openai.com/v1/audio/transcriptions";

    private final RestClient client = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SubtitleItem> transcribe(MultipartFile file) throws IOException {
        String apiKey = getApiKey();

        MultipartBodyBuilder body = new MultipartBodyBuilder();

        body.part("file", file.getResource())
                .filename(file.getOriginalFilename());

        body.part("model", "whisper-1");
        body.part("response_format", "verbose_json");
        body.part("timestamp_granularities[]", "word");
        body.part("timestamp_granularities[]", "segment");

        String response = client.post()
                .uri(OPENAI_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(String.class);

        return readSubtitles(response);
    }

    private List<SubtitleItem> readSubtitles(String response) throws IOException {
        JsonNode json = objectMapper.readTree(response);
        JsonNode segments = json.path("segments");

        List<SubtitleItem> subtitles = new ArrayList<>();

        for (JsonNode segment : segments) {
            double noSpeechProb = segment.path("no_speech_prob").asDouble();
            double aveLogProb = segment.path("avg_logprob").asDouble();

            //skip only when both conditions are true
            if (noSpeechProb > 0.6 && aveLogProb < -1.0) {
                continue;
            }

            String text = segment.path("text").asText().trim();
            double start = segment.path("start").asDouble();
            double end = segment.path("end").asDouble();

            if (!text.isEmpty()) {
                subtitles.add(new SubtitleItem(text, start, end));
            }
        }

        return subtitles;
    }

    private String getApiKey() throws IOException {
        String key = System.getenv("OPENAI_API_KEY");

        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        Path keyFile = Path.of("key.txt");

        if (Files.exists(keyFile)) {
            return Files.readString(keyFile).trim();
        }

        throw new IllegalStateException(
                "OPENAI_API_KEY or key.txt is required");
    }
}