package typeshi;

import java.util.Random;

/**
 * Computer opponent that advances through a passage over time
 *
 * This implementation is non-blocking: it uses tick-based updates (the existing ScheduledExecutorService calls run periodically) and the AI decides whether to produce a character based on elapsed time and a configurable delay distribution
 * This avoids Thread.sleep() inside run
 */
public class ComputerOpponent implements Runnable {

    private final String passage;
    private final GameController controller;
    private final ComputerOpponentConfig config;

    private int position = 0;
    private int errors = 0;
    private volatile boolean running = true;
    private final Random random;

    // Timing state in nanoseconds
    private long lastTickNanos = 0;
    private long remainingDelayNanos = 0;

    /**
     * Backwards compatible constructor preserving the old signature
     * Legacy integer difficulty is mapped to a {@link ComputerOpponentConfig}
     */
    public ComputerOpponent(String passage, GameController controller, int difficulty) {
        this(passage, controller, ComputerOpponentConfig.fromLegacyDifficulty(difficulty), new Random());
    }

    /**
     * Primary constructor with explicit configuration and seedable Random
     * Useful for deterministic unit tests
     */
    public ComputerOpponent(String passage, GameController controller, ComputerOpponentConfig config, Random random) {
        this.passage = passage;
        this.controller = controller;
        this.config = config;
        this.random = random == null ? new Random() : random;
        // Sample initial delay
        this.remainingDelayNanos = sampleNextDelayNanos();
    }

    @Override
    public void run() {
        if (!running) return;

        long now = System.nanoTime();
        if (lastTickNanos == 0) lastTickNanos = now;
        long elapsed = now - lastTickNanos;
        lastTickNanos = now;

        remainingDelayNanos -= elapsed;

        // Emit one or more keystrokes if enough time has passed to catch up
        while (running && remainingDelayNanos <= 0) {
            advanceOneChar();
            if (!running) return;

            // Schedule next delay and allow multiple steps to catch up
            remainingDelayNanos += sampleNextDelayNanos();
        }
    }

    private void advanceOneChar() {
        if (position >= passage.length()) {
            // Return if already at end of passage
            return;
        }

        boolean makeError = random.nextDouble() < config.getErrorRate();
        if (makeError) {
            errors++;
        }

        position++;

        // Notify controller and indicate whether last typed char was correct; GameController updates UI on the JavaFX thread
        controller.updateComputerTyping(position, errors, !makeError);

        // If finished, notify controller once
        if (position >= passage.length()) {
            running = false;
            controller.onComputerFinished();
        }
    }

    private long sampleNextDelayNanos() {
        double mean = config.getMeanDelayMs();
        double jitter = config.getJitterMs();
        // Sample from normal distribution around mean with provided jitter and clamp to minimum 20 ms
        double val = mean + (random.nextGaussian() * (jitter / 2.0));
        double ms = Math.max(20.0, val);
        return (long) (ms * 1_000_000L);
    }

    public void stop() {
        running = false;
    }

    // Testing helper methods
    int getPosition() { return position; }
    int getErrors() { return errors; }
    boolean isRunning() { return running; }
}
