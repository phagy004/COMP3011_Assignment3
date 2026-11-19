package comp3011.assignment1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;

public class ShutdownControllerTests {

    @Test
    public void testShutdownResponse() {

        //Create a fake application context so the real server is not stopped during the test
        ConfigurableApplicationContext applicationContext =
                mock(ConfigurableApplicationContext.class);

        //Create the shutdown controller using the fake context
        ShutdownController controller =
                new ShutdownController(
                        applicationContext
                );

        //Send the first shutdown request
        ResponseEntity<Map<String, Object>> response =
                controller.shutdownServer();

        //Check that the first request returns HTTP 202
        assertEquals(
                202,
                response.getStatusCode().value()
        );

        //Get the response body
        Map<String, Object> body =
                response.getBody();

        //Check that a response body was returned
        assertNotNull(body);

        //Check that the correct shutdown message is returned
        assertEquals(
                "Graceful shutdown requested.",
                body.get("message")
        );
    }

    @Test
    public void testRepeatedShutdownResponse() {

        //Create a fake application context for the test
        ConfigurableApplicationContext applicationContext =
                mock(ConfigurableApplicationContext.class);

        //Create a new controller
        ShutdownController controller =
                new ShutdownController(
                        applicationContext
                );

        //Send the first request to start shutdown
        controller.shutdownServer();

        //Send another request while shutdown is already in progress
        ResponseEntity<Map<String, Object>> response =
                controller.shutdownServer();

        //Check that the second request returns HTTP 409
        assertEquals(
                409,
                response.getStatusCode().value()
        );

        //Get the error response body
        Map<String, Object> body =
                response.getBody();

        //Check that the response body exists
        assertNotNull(body);

        //Check the error status
        assertEquals(
                409,
                body.get("status")
        );

        //Check the error name
        assertEquals(
                "Conflict",
                body.get("error")
        );

        //Check the error message
        assertEquals(
                "Graceful shutdown is already in progress.",
                body.get("message")
        );

        //Check that the correct API path is returned
        assertEquals(
                "/api/v1/admin/shutdown",
                body.get("path")
        );

        //Check that the response also contains a timestamp
        assertNotNull(
                body.get("timestamp")
        );
    }
}