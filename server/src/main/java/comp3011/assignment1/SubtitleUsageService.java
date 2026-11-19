package comp3011.assignment1;

import java.util.concurrent.atomic.DoubleAdder;

import org.springframework.stereotype.Service;

@Service
public class SubtitleUsageService {

    private final DoubleAdder usage = new DoubleAdder();

    public void addUsage(double seconds) {
        if (seconds > 0) {
            usage.add(seconds);
        }
    }

    public double getUsage() {
        return usage.sum();
    }
}