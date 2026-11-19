package comp3011.assignment1;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

//Provides the global subtitle transcription usage through the REST API.
@RestController
public class SubtitleUsageController {

    private final SubtitleUsageService usageService;

    //Spring injects the shared usage service into this controller.
    public SubtitleUsageController(
            SubtitleUsageService usageService) {

        this.usageService = usageService;
    }

    //Assignment 3 requires the usage endpoint to return:
    //{ "usage": seconds }
    @GetMapping("/api/v1/global/usage")
    public Map<String, Double> getUsage() {
        return Map.of(
                "usage",
                usageService.getUsage());
    }
}