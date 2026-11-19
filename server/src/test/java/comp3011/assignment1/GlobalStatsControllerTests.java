package comp3011.assignment1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class GlobalStatsControllerTests {

    @Test
    public void testGlobalStatsResponse() {

        //Create the statistics service used by the controller
        StatisticsService statisticsService =
                new StatisticsService();

        //Add some sample token values before checking the response
        statisticsService.addTokens(
                100,
                20
        );

        //Create the controller with the statistics service
        GlobalStatsController controller =
                new GlobalStatsController(
                        statisticsService
                );

        //Call the global statistics endpoint method
        ResponseEntity<Map<String, Long>> response =
                controller.getGlobalStats();

        //Check that the request returns HTTP 200
        assertEquals(
                200,
                response.getStatusCode().value()
        );

        //Get the response body
        Map<String, Long> body =
                response.getBody();

        //Check that a response body was returned
        assertNotNull(body);

        //Check that the input token total is correct
        assertEquals(
                100L,
                body.get("inputTokens")
        );

        //Check that the output token total is correct
        assertEquals(
                20L,
                body.get("outputTokens")
        );
    }
}