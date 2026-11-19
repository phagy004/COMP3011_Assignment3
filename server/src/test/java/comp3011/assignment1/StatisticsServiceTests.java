package comp3011.assignment1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

public class StatisticsServiceTests {

    @Test
    public void testConcurrentTokenUpdates() throws Exception {

        //Create the statistics service for this test
        StatisticsService statisticsService =
                new StatisticsService();

        //Use 20 threads to update the token values at the same time
        ExecutorService executor =
                Executors.newFixedThreadPool(20);

        //Store each task so the test can wait for all of them to finish
        List<Future<?>> results =
                new ArrayList<>();

        //Run 200 updates to test the service with concurrent access
        for (int i = 0; i < 200; i++) {

            Future<?> result =
                    executor.submit(() -> {

                        //Each task adds the same sample token values
                        statisticsService.addTokens(
                                10,
                                5
                        );
                    });

            //Save the task result
            results.add(result);
        }

        //Wait until every task has finished
        for (Future<?> result : results) {

            result.get();
        }

        //Stop the thread pool after all tasks are complete
        executor.shutdown();

        //200 updates with 10 input tokens should give 2000
        assertEquals(
                2000,
                statisticsService.getInputTokens()
        );

        //200 updates with 5 output tokens should give 1000
        assertEquals(
                1000,
                statisticsService.getOutputTokens()
        );
    }
}