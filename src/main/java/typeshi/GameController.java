package typeshi;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
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
    private int currentDifficulty = 5;

    // scoring
    private final ScoreManager scoreManager = new ScoreManager();
    private int lastCorrectCount = 0;

    // Difficulty mode: 1 = Easy, 2 = Medium, 3 = Hard
    private int mode = 1;

    // For HARD fading (player side)
    private int fadeIndex = 0;

    // Guard flag so programmatic text changes do not recurse badly
    private boolean adjustingInput = false;

    // current passages for each side
    private String playerPassage;
    private String computerPassage;

    // shared ordered sequence of passages
    private final List<String> passageSequence = new ArrayList<>();
    private int playerPassageIndex = 0;
    private int computerPassageIndex = 0;

    // how many passages each has finished (for stats)
    private int playerFinishedCount = 0;
    private int computerFinishedCount = 0;

    // track if current computer passage has already been handled
    private boolean computerPassageDone = false;

    public GameController(UIComponents ui) {
        this.ui = ui;
        this.wordGenerator = new WordGenerator();
        setupGameUI();
    }

    // Call this from outside before startGame: 1=Easy, 2=Medium, 3=Hard
    public void setMode(int mode) {
        this.mode = mode;
    }

    private void setupGameUI() {
        ui.inputField.textProperty().addListener((obs, oldVal, newVal) -> onPlayerType());

        // reset shared sequence for this round
        passageSequence.clear();
        playerPassageIndex = 0;
        computerPassageIndex = 0;

        String first = getOrCreatePassageAt(0);
        playerPassage = first;
        computerPassage = first;

        fadeIndex = 0;
        computerPassageDone = false;

        // build player TextFlow
        ui.targetTextFlow.getChildren().clear();
        for (char c : playerPassage.toCharArray()) {
            Text t = new Text(String.valueOf(c));
            t.setFill(Color.WHITE);
            t.setStyle("-fx-font-family: Consolas; -fx-font-size: 18;");
            ui.targetTextFlow.getChildren().add(t);
        }

        // build computer TextFlow
        ui.computerTextFlow.getChildren().clear();
        for (char c : computerPassage.toCharArray()) {
            Text t = new Text(String.valueOf(c));
            t.setFill(Color.GRAY);
            t.setStyle("-fx-font-family: Consolas; -fx-font-size: 18;");
            ui.computerTextFlow.getChildren().add(t);
        }

        ui.inputField.clear();
        ui.playerProgress.setProgress(0);
        ui.computerProgress.setProgress(0);
        lastCorrectCount = 0;
    }

    // Generate or reuse passage at given index in shared sequence
    private String getOrCreatePassageAt(int index) {
        while (index >= passageSequence.size()) {
            passageSequence.add(wordGenerator.getRandomPassage());
        }
        return passageSequence.get(index);
    }

    // -------------------- GAME START WITH COUNTDOWN --------------------
    // Call this from Main instead of startGame(...)
    public void startGameWithCountdown(int durationSeconds, int difficulty) {
        // block typing during countdown
        ui.inputField.setDisable(true);

        // remember existing center content (player/computer HBox)
        javafx.scene.Node originalCenter = ui.rootPane.getCenter();

        // overlay + dim
        Rectangle dim = new Rectangle();
        Label countdownText = new Label("3");
        countdownText.setTextFill(Color.WHITE);
        countdownText.setFont(Font.font("Consolas", 80));

        StackPane overlay = new StackPane(dim, countdownText);
        overlay.setAlignment(Pos.CENTER);

        // stack = existing center content + overlay on top
        StackPane centerStack = new StackPane(originalCenter, overlay);
        dim.widthProperty().bind(centerStack.widthProperty());
        dim.heightProperty().bind(centerStack.heightProperty());
        dim.setFill(new Color(0, 0, 0, 0.25)); // light dim

        // temporarily replace center with stacked version
        ui.rootPane.setCenter(centerStack);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0),  e -> countdownText.setText("3")),
                new KeyFrame(Duration.seconds(1),  e -> countdownText.setText("2")),
                new KeyFrame(Duration.seconds(2),  e -> countdownText.setText("1")),
                new KeyFrame(Duration.seconds(3),  e -> countdownText.setText("GO!")),
                new KeyFrame(Duration.seconds(3.5), e -> {
                    // remove overlay: restore original center node
                    ui.rootPane.setCenter(originalCenter);
                    ui.inputField.setDisable(false);
                    ui.inputField.requestFocus();
                    startGame(durationSeconds, difficulty);
                })
        );
        timeline.play();
    }

    // -------------------- START GAME (ACTUAL TIMER + AI) --------------------
    public void startGame(int durationSeconds, int difficulty) {
        if (running) return;
        running = true;
        remainingSeconds = durationSeconds;

        currentDifficulty = difficulty;

        scoreManager.reset();
        playerFinishedCount = 0;
        computerFinishedCount = 0;
        lastCorrectCount = 0;

        ui.playerScoreLabel.setText(scoreManager.playerSummary());
        ui.computerScoreLabel.setText(scoreManager.computerSummary());

        // Scheduled thread pool for AI + timer (+ optional fade)
        backgroundPool = Executors.newScheduledThreadPool(3);

        computer = new ComputerOpponent(computerPassage, this, currentDifficulty);
        backgroundPool.scheduleAtFixedRate(computer, 0, 100, TimeUnit.MILLISECONDS);

        // Countdown timer
        backgroundPool.scheduleAtFixedRate(() -> {
            remainingSeconds--;
            Platform.runLater(() ->
                    ui.timerLabel.setText(String.format("00:%02d", remainingSeconds)));
            if (remainingSeconds <= 0) endGame();
        }, 1, 1, TimeUnit.SECONDS);

        // HARD: fade words at a fixed interval (e.g., every 3 seconds)
        if (mode == 3) {
            fadeIndex = 0;
            backgroundPool.scheduleAtFixedRate(
                    () -> Platform.runLater(this::fadeNextWord),
                    3, 3, TimeUnit.SECONDS
            );
        }
    }

    // -------------------- START NEW PASSAGE (PLAYER ONLY) --------------------
    private void startPlayerPassage() {
        playerPassageIndex++; // human moves ahead in shared sequence
        playerPassage = getOrCreatePassageAt(playerPassageIndex);
        fadeIndex = 0;

        ui.targetTextFlow.getChildren().clear();
        for (char c : playerPassage.toCharArray()) {
            Text t = new Text(String.valueOf(c));
            t.setFill(Color.WHITE);
            t.setStyle("-fx-font-family: Consolas; -fx-font-size: 18;");
            ui.targetTextFlow.getChildren().add(t);
        }

        ui.inputField.clear();
        ui.playerProgress.setProgress(0);
        lastCorrectCount = 0;
    }

    // -------------------- START NEW PASSAGE (COMPUTER ONLY) --------------------
    private void startComputerPassage() {
        computerPassageIndex++; // AI moves ahead in shared sequence
        computerPassage = getOrCreatePassageAt(computerPassageIndex);
        computerPassageDone = false;

        ui.computerTextFlow.getChildren().clear();
        for (char c : computerPassage.toCharArray()) {
            Text t = new Text(String.valueOf(c));
            t.setFill(Color.GRAY);
            t.setStyle("-fx-font-family: Consolas; -fx-font-size: 18;");
            ui.computerTextFlow.getChildren().add(t);
        }

        ui.computerProgress.setProgress(0);
    }

    // -------------------- PLAYER INPUT --------------------
    private void onPlayerType() {
        // ignore while not running or while we are programmatically changing text
        if (!running || adjustingInput) return;

        String typed = ui.inputField.getText();
        var children = ui.targetTextFlow.getChildren();

        int correctCount = 0;

        for (int i = 0; i < children.size(); i++) {
            Text t = (Text) children.get(i);

            if (i < typed.length()) {
                char targetChar = t.getText().charAt(0);
                if (typed.charAt(i) == targetChar) {
                    correctCount++;
                    if (!t.getFill().equals(Color.TRANSPARENT)) {
                        t.setFill(Color.LIMEGREEN);
                    }
                } else {
                    if (!t.getFill().equals(Color.TRANSPARENT)) {
                        t.setFill(Color.RED);
                    }
                }
            } else {
                if (!t.getFill().equals(Color.TRANSPARENT)) {
                    t.setFill(Color.WHITE);
                }
            }
        }

        double progress = (double) correctCount / children.size();
        ui.playerProgress.setProgress(progress);

        // how many *new* correct chars since last keystroke
        int deltaCorrect = correctCount - lastCorrectCount;
        if (deltaCorrect > 0) {
            scoreManager.awardPlayer(deltaCorrect);
        }
        lastCorrectCount = correctCount;

        // errors in this passage
        scoreManager.setPlayerErrors(typed.length() - correctCount);
        scoreManager.setPlayerProgress(progress);

        // show the formatted summary: "Score: X | Errors: Y"
        ui.playerScoreLabel.setText(scoreManager.playerSummary());

        // MEDIUM and HARD: after each correctly completed word, remove last letter
        if ((mode == 2 || mode == 3) && justFinishedWord(typed, playerPassage) && !typed.isEmpty()) {
            String shortened = typed.substring(0, typed.length() - 1);

            adjustingInput = true;
            Platform.runLater(() -> {
                ui.inputField.setText(shortened);
                ui.inputField.positionCaret(shortened.length());
                adjustingInput = false;
            });

            return;
        }

        // Player finished their passage
        if (typed.equals(playerPassage)) {
            playerFinishedCount++;

            adjustingInput = true;
            Platform.runLater(() -> {
                ui.logBox.getChildren().add(
                        new Label("You finished a passage! (" + playerFinishedCount + ")")
                );

                // Only player moves to next passage in shared sequence
                startPlayerPassage();

                adjustingInput = false;
            });

            return;
        }
    }

    // Helper: did the player just finish a correct word? (space-ended words only)
    private boolean justFinishedWord(String typed, String passage) {
        int len = typed.length();
        if (len == 0 || len > passage.length()) return false;

        char last = typed.charAt(len - 1);
        if (last != ' ') return false;

        for (int i = 0; i < len; i++) {
            if (typed.charAt(i) != passage.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // HARD: fade (hide) the next word from the targetTextFlow
    private void fadeNextWord() {
        var children = ui.targetTextFlow.getChildren();
        if (children.isEmpty()) return;

        while (fadeIndex < children.size()) {
            Text t = (Text) children.get(fadeIndex);
            if (!t.getFill().equals(Color.TRANSPARENT)) break;
            fadeIndex++;
        }
        if (fadeIndex >= children.size()) return;

        while (fadeIndex < children.size()) {
            Text t = (Text) children.get(fadeIndex);
            t.setFill(Color.TRANSPARENT);
            char ch = t.getText().charAt(0);
            fadeIndex++;
            if (ch == ' ') break;
        }
    }

    // -------------------- COMPUTER UPDATES --------------------
    public void updateComputerProgress(int position, int errors) {
        Platform.runLater(() -> {
            double progress = (double) position / ui.computerTextFlow.getChildren().size();
            ui.computerProgress.setProgress(progress);

            scoreManager.setComputerProgress(progress);
            scoreManager.setComputerErrors(errors);
            scoreManager.awardComputer(1);

            ui.computerScoreLabel.setText(scoreManager.computerSummary());

            // Update computer text coloring
            for (int i = 0; i < ui.computerTextFlow.getChildren().size(); i++) {
                Text t = (Text) ui.computerTextFlow.getChildren().get(i);
                if (i < position) t.setFill(Color.LIMEGREEN);
                else t.setFill(Color.GRAY);
            }
        });
    }

    public void onComputerFinished() {
        Platform.runLater(() -> {
            // ignore if round is over or this passage was already processed
            if (!running || remainingSeconds <= 0 || computerPassageDone) {
                return;
            }
            computerPassageDone = true;

            computerFinishedCount++;
            ui.logBox.getChildren().add(
                    new Label("Computer finished a passage. (" + computerFinishedCount + ")")
            );

            // Only computer moves to next passage in shared sequence
            startComputerPassage();

            // Restart AI with new passage
            if (computer != null) computer.stop();
            computer = new ComputerOpponent(computerPassage, this, currentDifficulty);
            backgroundPool.scheduleAtFixedRate(computer, 0, 100, TimeUnit.MILLISECONDS);
        });
    }

    // -------------------- END GAME --------------------
    private void endGame() {
        running = false;
        if (backgroundPool != null) backgroundPool.shutdownNow();

        // capture final stats
        int playerScore = scoreManager.getPlayerScore();
        int computerScore = scoreManager.getComputerScore();
        int playerErrors = scoreManager.getPlayerErrors();
        int computerErrors = scoreManager.getComputerErrors();

        Platform.runLater(() -> {
            VictoryScreen victory = new VictoryScreen(
                    playerScore,
                    computerScore,
                    playerErrors,
                    computerErrors
            );

            // swap the current game UI for the victory screen, keeping the same Scene
            ui.rootPane.getScene().setRoot(victory.getRoot());
        });
    }


    private String getCurrentPassage() {
        return playerPassage;
    }
}
