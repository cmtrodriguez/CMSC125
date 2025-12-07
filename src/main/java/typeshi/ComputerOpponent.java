package typeshi;

import java.util.Random;

public class ComputerOpponent implements Runnable {

    private final String passage;
    private final GameController controller;
    private final int difficulty; // 1-10, higher = faster & more accurate

    private int position = 0;
    private int errors = 0;
    private boolean running = true;
    private final Random random = new Random();

    public ComputerOpponent(String passage, GameController controller, int difficulty) {
        this.passage = passage;
        this.controller = controller;
        this.difficulty = difficulty;
    }

    @Override
    public void run() {
        // If we have been stopped, do nothing
        if (!running) {
            return;
        }

        // Finished this passage once: notify controller exactly once
        if (position >= passage.length()) {
            controller.onComputerFinished();
            running = false;   // stop further work; future ticks just return above
            return;
        }

        // Determine if AI makes a mistake
        boolean makeError = random.nextInt(10) >= difficulty; // higher difficulty = less errors
        if (makeError) errors++;

        position++;

        // Update controller for UI
        controller.updateComputerProgress(position, errors);

        // Variable typing speed based on difficulty
        try {
            int baseSpeed = 250 - (difficulty * 15); // Easy ~235ms, Hard ~115ms
            Thread.sleep(baseSpeed + random.nextInt(50));
        } catch (InterruptedException e) {
            running = false;
        }
    }


    public void stop() {
        running = false;
    }
}
