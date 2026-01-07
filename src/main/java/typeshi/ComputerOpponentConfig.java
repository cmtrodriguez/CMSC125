package typeshi;

/**
 * Configuration for ComputerOpponent behavior.
 * Encapsulates typing speed (mean and jitter) and error probability.
 */
public final class ComputerOpponentConfig {
    private final double meanDelayMs;
    private final double jitterMs;
    private final double errorRate; // 0.0 - 1.0

    public ComputerOpponentConfig(double meanDelayMs, double jitterMs, double errorRate) {
        if (meanDelayMs <= 0) throw new IllegalArgumentException("meanDelayMs must be > 0");
        if (jitterMs < 0) throw new IllegalArgumentException("jitterMs must be >= 0");
        if (errorRate < 0 || errorRate > 1) throw new IllegalArgumentException("errorRate must be between 0 and 1");
        this.meanDelayMs = meanDelayMs;
        this.jitterMs = jitterMs;
        this.errorRate = errorRate;
    }

    public double getMeanDelayMs() {
        return meanDelayMs;
    }

    public double getJitterMs() {
        return jitterMs;
    }

    public double getErrorRate() {
        return errorRate;
    }

    /**
     * Helper to map legacy integer difficulty (1..10) to a reasonable config.
     */
    public static ComputerOpponentConfig fromLegacyDifficulty(int difficulty) {
        int d = Math.max(1, Math.min(10, difficulty));
        // Map difficulty -> mean delay (ms): 1 => 300ms, 10 => 80ms (faster)
        double mean = 300.0 - (d - 1) * (220.0 / 9.0);
        // Jitter smaller at higher difficulty
        double jitter = 80.0 - (d - 1) * (60.0 / 9.0);
        // error rate: difficulty 1 => 0.25, difficulty 10 => 0.01
        double error = 0.25 - (d - 1) * (0.24 / 9.0);
        return new ComputerOpponentConfig(mean, Math.max(10.0, jitter), Math.max(0.01, error));
    }
}