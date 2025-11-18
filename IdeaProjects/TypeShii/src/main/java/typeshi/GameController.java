package typeshi;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameController {

    private final UIComponents ui;
    private final WordGenerator wordGenerator;

    private ComputerOpponent computer;
    private ScheduledExecutorService backgroundPool;

    private int remainingSeconds;
    private boolean running = false;

    public GameController(UIComponents ui) {
        this.ui = ui;
        this.wordGenerator = new WordGenerator();
        setupGameUI();
    }

    private void setupGameUI() {
        ui.inputField.textProperty().addListener((obs, oldVal, newVal) -> onPlayerType());
        startNewPassage();
    }

    // -------------------- START NEW PASSAGE --------------------
    private void startNewPassage() {
        String passage = wordGenerator.getRandomPassage();

        // Setup player targetTextFlow
        ui.targetTextFlow.getChildren().clear();
        for (char c : passage.toCharArray()) {
            Text t = new Text(String.valueOf(c));
            t.setFill(Color.WHITE);
            t.setStyle("-fx-font-family: Consolas; -fx-font-size: 18;");
            ui.targetTextFlow.getChildren().add(t);
        }

        // Setup computer textFlow
        ui.computerTextFlow.getChildren().clear();
        for (char c : passage.toCharArray()) {
            Text t = new Text(String.valueOf(c));
            t.setFill(Color.GRAY); // initially gray
            t.setStyle("-fx-font-family: Consolas; -fx-font-size: 18;");
            ui.computerTextFlow.getChildren().add(t);
        }

        ui.inputField.clear();
        ui.playerProgress.setProgress(0);
        ui.computerProgress.setProgress(0);
    }

    // -------------------- START GAME --------------------
    public void startGame(int durationSeconds, int difficulty) {
        if (running) return;
        running = true;
        remainingSeconds = durationSeconds;

        // Scheduled thread pool for AI
        backgroundPool = Executors.newScheduledThreadPool(2);

        String passage = getCurrentPassage();
        computer = new ComputerOpponent(passage, this, difficulty);
        backgroundPool.scheduleAtFixedRate(computer, 0, 100, TimeUnit.MILLISECONDS);

        // Countdown timer
        backgroundPool.scheduleAtFixedRate(() -> {
            remainingSeconds--;
            Platform.runLater(() -> ui.timerLabel.setText(String.format("00:%02d", remainingSeconds)));
            if (remainingSeconds <= 0) endGame();
        }, 1, 1, TimeUnit.SECONDS);
    }

    // -------------------- PLAYER INPUT --------------------
    private void onPlayerType() {
        if (!running) return;
        String typed = ui.inputField.getText();
        var children = ui.targetTextFlow.getChildren();

        int correctCount = 0;

        for (int i = 0; i < children.size(); i++) {
            Text t = (Text) children.get(i);
            if (i < typed.length()) {
                if (typed.charAt(i) == t.getText().charAt(0)) {
                    t.setFill(Color.LIMEGREEN);
                    correctCount++;
                } else {
                    t.setFill(Color.RED);
                }
            } else {
                t.setFill(Color.WHITE);
            }
        }

        double progress = (double) correctCount / children.size();
        ui.playerProgress.setProgress(progress);
        ui.playerScoreLabel.setText("Score: " + correctCount + " | Errors: " + (typed.length() - correctCount));

        // Player finished
        if (typed.equals(getCurrentPassage())) {
            ui.logBox.getChildren().add(new javafx.scene.control.Label("You finished the passage!"));
            startNewPassage();

            // Reset computer for new passage
            if (computer != null) computer.stop();
            computer = new ComputerOpponent(getCurrentPassage(), this, 5);
            backgroundPool.scheduleAtFixedRate(computer, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    // -------------------- COMPUTER UPDATES --------------------
    public void updateComputerProgress(int position, int errors) {
        Platform.runLater(() -> {
            double progress = (double) position / ui.computerTextFlow.getChildren().size();
            ui.computerProgress.setProgress(progress);
            ui.computerScoreLabel.setText("Score: " + position + " | Errors: " + errors);

            // Update computer text coloring
            for (int i = 0; i < ui.computerTextFlow.getChildren().size(); i++) {
                Text t = (Text) ui.computerTextFlow.getChildren().get(i);
                if (i < position) t.setFill(Color.LIMEGREEN);
                else t.setFill(Color.GRAY);
            }
        });
    }

    public void onComputerFinished() {
        Platform.runLater(() -> ui.logBox.getChildren().add(new javafx.scene.control.Label("Computer finished a passage.")));
    }

    // -------------------- END GAME --------------------
    private void endGame() {
        running = false;
        if (backgroundPool != null) backgroundPool.shutdownNow();
        Platform.runLater(() -> ui.logBox.getChildren().add(new javafx.scene.control.Label("Time's up! Round finished.")));
    }

    private String getCurrentPassage() {
        StringBuilder sb = new StringBuilder();
        for (var node : ui.targetTextFlow.getChildren()) {
            sb.append(((Text) node).getText());
        }
        return sb.toString();
    }
}
