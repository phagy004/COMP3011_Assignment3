package comp3011.assignment1;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubtitleUsageController {

    private final SubtitleUsageService usageService;

    public SubtitleUsageController(SubtitleUsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/api/v1/global/usage")
    public Map<String, Double> getUsage() {
        return Map.of("usage", usageService.getUsage());
    }
}