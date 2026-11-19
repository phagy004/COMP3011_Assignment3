package comp3011;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SubtitleService {

    private static final String SUBTITLE_URL = "http://localhost:8080/api/v1/subtitles";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<List<SubtitleSegment>> getSubtitles(File videoFile) {
        try {
            String boundary = "SubtitleBoundary";

            String header =
                    "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\""
                    + videoFile.getName() + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n";

            String footer = "\r\n--" + boundary + "--\r\n";

            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(header.getBytes(StandardCharsets.UTF_8)),
                    HttpRequest.BodyPublishers.ofFile(videoFile.toPath()),
                    HttpRequest.BodyPublishers.ofByteArray(footer.getBytes(StandardCharsets.UTF_8))
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUBTITLE_URL))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(body)
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::readResponse)
                    .exceptionally(error -> {
                        System.out.println("Could not load subtitles: " + error.getMessage());
                        return List.of();
                    });

        } catch (Exception error) {
            System.out.println("Could not load subtitles: " + error.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private List<SubtitleSegment> readResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            System.out.println("Subtitle server returned " + response.statusCode());
            return List.of();
        }

        try {
            JsonNode json = objectMapper.readTree(response.body());
            List<SubtitleSegment> subtitles = new ArrayList<>();

            for (JsonNode item : json) {
                String text = item.get("text").asText();
                double start = item.get("start").asDouble();
                double end = item.get("end").asDouble();

                subtitles.add(new SubtitleSegment(text, start, end));
            }

            return subtitles;
        } catch (Exception error) {
            System.out.println("Could not read subtitle response: " + error.getMessage());
            return List.of();
        }
    }
}