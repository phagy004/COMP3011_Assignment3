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
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SubtitleService {

    private static final String SUBTITLE_URL =
            "http://localhost:8080/api/v1/subtitles";

    private static final int MAX_RETRIES = 3;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AudioExtractor audioExtractor = new AudioExtractor();

    public CompletableFuture<List<SubtitleSegment>> getSubtitles(File videoFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return audioExtractor.extract(videoFile);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }).thenCompose(audioFile -> sendWithRetry(audioFile, 0))
          .exceptionally(error -> {
              System.out.println(
                      "Could not load subtitles: "
                      + error.getMessage());

              return List.of();
          });
    }

    private CompletableFuture<List<SubtitleSegment>> sendWithRetry(
            File audioFile,
            int attempt) {

        return sendAudio(audioFile)
                .exceptionallyCompose(error -> {

                    if (attempt >= MAX_RETRIES) {
                        System.out.println("Subtitle server unavailable.");
                        return CompletableFuture.completedFuture(List.of());
                    }

                    System.out.println(
                            "Subtitle request failed. Retrying...");

                    return CompletableFuture
                            .runAsync(
                                    () -> {},
                                    CompletableFuture.delayedExecutor(
                                            2,
                                            TimeUnit.SECONDS))
                            .thenCompose(ignore ->
                                    sendWithRetry(
                                            audioFile,
                                            attempt + 1));
                });
    }

    private CompletableFuture<List<SubtitleSegment>> sendAudio(File audioFile) {
        try {
            String boundary = "SubtitleBoundary";

            String header =
                    "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\""
                    + audioFile.getName() + "\"\r\n"
                    + "Content-Type: audio/mpeg\r\n\r\n";

            String footer =
                    "\r\n--" + boundary + "--\r\n";

            HttpRequest.BodyPublisher body =
                    HttpRequest.BodyPublishers.concat(
                            HttpRequest.BodyPublishers.ofByteArray(
                                    header.getBytes(StandardCharsets.UTF_8)),
                            HttpRequest.BodyPublishers.ofFile(
                                    audioFile.toPath()),
                            HttpRequest.BodyPublishers.ofByteArray(
                                    footer.getBytes(StandardCharsets.UTF_8)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUBTITLE_URL))
                    .header(
                            "Content-Type",
                            "multipart/form-data; boundary=" + boundary)
                    .POST(body)
                    .build();

            return client.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() != 200) {
                            return CompletableFuture.failedFuture(
                                    new RuntimeException(
                                            "Server returned "
                                            + response.statusCode()));
                        }

                        return CompletableFuture.completedFuture(
                                readResponse(response.body()));
                    });

        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private List<SubtitleSegment> readResponse(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            List<SubtitleSegment> subtitles = new ArrayList<>();

            for (JsonNode item : json) {
                String text = item.path("text").asText();
                double start = item.path("start").asDouble();
                double end = item.path("end").asDouble();

                subtitles.add(
                        new SubtitleSegment(
                                text,
                                start,
                                end));
            }

            return subtitles;

        } catch (Exception error) {
            throw new RuntimeException(
                    "Could not read subtitle response",
                    error);
        }
    }
}