package comp3011.assignment1;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GlobalStatsController {

    //Service used to get the current token totals
    private final StatisticsService statisticsService;

    public GlobalStatsController(
            StatisticsService statisticsService) {

        //Save the service so the controller can use it
        this.statisticsService =
                statisticsService;
    }

    //Return the total token usage since the server started
    @GetMapping("/api/v1/global/stats")
    public ResponseEntity<Map<String, Long>> getGlobalStats() {

        //Create the response that will be returned as JSON
        Map<String, Long> response =
                new HashMap<>();

        //Get the current total number of input tokens
        response.put(
                "inputTokens",
                statisticsService.getInputTokens()
        );

        //Get the current total number of output tokens
        response.put(
                "outputTokens",
                statisticsService.getOutputTokens()
        );

        //Return HTTP 200 with the statistics
        return ResponseEntity
                .ok(response);
    }
}