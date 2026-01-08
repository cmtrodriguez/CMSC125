package typeshi;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ComputerOpponentTest {

    @Test
    public void testFastAccurateOpponentFinishes() throws Exception {
        String passage = "hello world"; // 11 chars
        // fast mean and tiny jitter, no errors
        ComputerOpponentConfig config = new ComputerOpponentConfig(10.0, 1.0, 0.0);

        ComputerOpponent ai = new ComputerOpponent(passage, new DummyController(), config, new java.util.Random(42));

        ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
        pool.scheduleAtFixedRate(ai, 0, 10, TimeUnit.MILLISECONDS);

        // wait up to 2 seconds for completion
        long start = System.currentTimeMillis();
        while (ai.isRunning() && (System.currentTimeMillis() - start) < 2000) {
            Thread.sleep(20);
        }

        pool.shutdownNow();

        assertFalse(ai.isRunning(), "Computer should have finished");
        assertEquals(passage.length(), ai.getPosition(), "Position should match passage length");
        assertEquals(0, ai.getErrors(), "No errors expected");
    }

    // Minimal controller stub used for tests
    static class DummyController extends GameController {
        DummyController() {
            super(null);
        }

        @Override
        public void updateComputerTyping(int position, int errors, boolean lastWasCorrect) {
            // no-op for tests (headless)
        }

        @Override
        public void onComputerFinished() {
            // no-op for tests
        }
    }
}