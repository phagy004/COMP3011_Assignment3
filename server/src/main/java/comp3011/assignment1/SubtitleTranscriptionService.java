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
        JsonNode words = json.path("words");

        List<SubtitleItem> subtitles = new ArrayList<>();

        StringBuilder text = new StringBuilder();
        double start = -1;
        double end = 0;
        double previousEnd = -1;

        for (JsonNode word : words) {
            String value = word.path("word").asText().trim();
            double wordStart = word.path("start").asDouble();
            double wordEnd = word.path("end").asDouble();

            if (value.isEmpty()) {
                continue;
            }

            if (start < 0) {
                start = wordStart;
            }

            boolean longPause =
                    previousEnd >= 0 && wordStart - previousEnd > 0.7;

            boolean tooLong =
                    text.length() + value.length() + 1 > 84;

            boolean tooLongOnScreen =
                    wordEnd - start > 7.0;

            if (text.length() > 0 &&
                    (longPause || tooLong || tooLongOnScreen)) {

                subtitles.add(new SubtitleItem(
                        text.toString().trim(),
                        start,
                        Math.max(end, start + 1.0)
                ));

                text.setLength(0);
                start = wordStart;
            }

            if (text.length() > 0) {
                text.append(" ");
            }

            text.append(value);
            end = wordEnd;
            previousEnd = wordEnd;

            if (endsSentence(value)) {
                subtitles.add(new SubtitleItem(
                        text.toString().trim(),
                        start,
                        Math.max(end, start + 1.0)
                ));

                text.setLength(0);
                start = -1;
            }
        }

        if (text.length() > 0) {
            subtitles.add(new SubtitleItem(
                    text.toString().trim(),
                    start,
                    Math.max(end, start + 1.0)
            ));
        }

        return subtitles;
    }

    private boolean endsSentence(String word) {
        return word.endsWith(".")
                || word.endsWith("?")
                || word.endsWith("!");
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