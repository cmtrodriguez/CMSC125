package typeshi;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * GameController
 * - Singleplayer: runs ComputerOpponent on a scheduler (your existing logic).
 * - Multiplayer: does NOT run ComputerOpponent; instead, sends PROGRESS/FINISHED and updates
 *   the "computer" side UI from incoming network messages (via updateComputerProgress + onComputerFinished).
 */
public class GameController {

    private final UIComponents ui;
    private final WordGenerator wordGenerator;

    private ComputerOpponent computer;
    private ScheduledExecutorService backgroundPool;

    private int remainingSeconds;
    private boolean running = false;
    private int currentDifficulty = 5;

    private boolean paused = false; // pause state
    private StackPane pauseOverlay; // legacy overlay (not used when modal stage exists)
    private Stage pauseStage = null; // modal stage for pause dialog
    private int initialDurationSeconds = 0; // preserve initial duration for restart

    // ===== MULTIPLAYER =====
    private boolean multiplayer = false;
    private boolean isHost = false;
    private NetworkOpponent networkOpponent;
    private MultiplayerServer mpServer;
    private MultiplayerClient mpClient;

    private int lastOpponentPosition = 0;
    private int lastOpponentErrors = 0;

    private int multiplayerRoundSeconds = 20;
    private int multiplayerPort = 5000;

    // scoring
    private final ScoreManager scoreManager = new ScoreManager();
    private int lastCorrectCount = 0;

    // Difficulty mode: 1 = Easy, 2 = Medium, 3 = Hard
    private int mode = 1;

    // For HARD fading (player side)
    private int fadeIndex = 0;

    // Guard flag so programmatic text changes do not recurse badly
    private boolean adjustingInput = false;

    // Track which character positions have been auto-backspaced (Medium/Hard)
    private final java.util.Set<Integer> backspacedPositions = new java.util.HashSet<>();

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

    private Runnable onReturnToMenu = null;

    public GameController(UIComponents ui) {
        this.ui = ui;
        this.wordGenerator = new WordGenerator();

        // allow tests to pass null for UI to avoid JavaFX initialization
        if (this.ui != null) {
            setupGameUI();
        }
    }

    // Call this from outside before startGame: 1=Easy, 2=Medium, 3=Hard
    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setOnReturnToMenu(Runnable onReturnToMenu) {
        this.onReturnToMenu = onReturnToMenu;
    }

    private void setupGameUI() {
        ui.inputField.textProperty().addListener((obs, oldVal, newVal) -> onPlayerType());

        // Prevent Enter key from activating unrelated default buttons: consume the TextField action
        ui.inputField.setOnAction(e -> {
            e.consume();
            onPlayerType();
        });

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

        // build "computer" TextFlow
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

        lastOpponentPosition = 0;
        lastOpponentErrors = 0;

        // prepare pause control
        if (ui.pauseButton != null) {
            ui.pauseButton.setDisable(false);
            ui.pauseButton.setVisible(true);
            ui.pauseButton.setOnAction(e -> togglePause());
        }
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
        // remember duration so we can restart later
        this.initialDurationSeconds = durationSeconds;

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
                new KeyFrame(Duration.seconds(0), e -> countdownText.setText("3")),
                new KeyFrame(Duration.seconds(1), e -> countdownText.setText("2")),
                new KeyFrame(Duration.seconds(2), e -> countdownText.setText("1")),
                new KeyFrame(Duration.seconds(3), e -> countdownText.setText("GO!")),
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

    // -------------------- START GAME (TIMER + AI/NETWORK) --------------------
    public void startGame(int durationSeconds, int difficulty) {
        if (running) return;
        running = true;

        remainingSeconds = durationSeconds;
        currentDifficulty = difficulty;

        scoreManager.reset();
        playerFinishedCount = 0;
        computerFinishedCount = 0;
        lastCorrectCount = 0;

        lastOpponentPosition = 0;
        lastOpponentErrors = 0;

        ui.playerScoreLabel.setText(scoreManager.playerSummary());
        ui.computerScoreLabel.setText(scoreManager.computerSummary());

        // Scheduled thread pool for timer (+ optional fade). AI only when NOT multiplayer.
        backgroundPool = Executors.newScheduledThreadPool(3);

        if (!multiplayer) {
            computer = new ComputerOpponent(computerPassage, this, currentDifficulty);
            backgroundPool.scheduleAtFixedRate(() -> {
                if (!paused && computer != null) computer.run();
            }, 0, 100, TimeUnit.MILLISECONDS);
        } else {
            computer = null; // opponent is remote
        }

        // Countdown timer (pause-aware)
        backgroundPool.scheduleAtFixedRate(() -> {
            if (paused) return;
            remainingSeconds--;
            Platform.runLater(() ->
                    ui.timerLabel.setText(String.format("00:%02d", remainingSeconds)));
            if (remainingSeconds <= 0) endGame();
        }, 1, 1, TimeUnit.SECONDS);

        // HARD: fade words at a fixed interval
        if (mode == 3) {
            fadeIndex = 0;
            backgroundPool.scheduleAtFixedRate(
                    () -> { if (!paused) Platform.runLater(this::fadeNextWord); },
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

    public void prepareMultiplayerLobbyUI(String statusText) {
        if (ui == null) return;

        // change labels (requires UIComponents fields; see Fix 2)
        if (ui.opponentTitleLabel != null) ui.opponentTitleLabel.setText("Player");
        if (ui.bottomInstructionLabel != null) ui.bottomInstructionLabel.setText(statusText);

        ui.inputField.clear();
        ui.inputField.setDisable(true);

        ui.targetTextFlow.getChildren().clear();
        ui.computerTextFlow.getChildren().clear();

        ui.playerProgress.setProgress(0);
        ui.computerProgress.setProgress(0);

        ui.timerLabel.setText("00:00");
        ui.playerScoreLabel.setText("Score: 0 | Errors: 0");
        ui.computerScoreLabel.setText("Score: 0 | Errors: 0");

        ui.logBox.getChildren().clear();
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

        String typedRaw = ui.inputField.getText();
        var children = ui.targetTextFlow.getChildren();

        // cap for safety if user pastes extra characters
        int cappedLen = Math.min(typedRaw.length(), children.size());
        String typed = typedRaw.substring(0, cappedLen);

        int correctCount = 0;
        boolean hasNewError = false; // Track if user just made a NEW mistake (for screen shake)

        for (int i = 0; i < children.size(); i++) {
            Text t = (Text) children.get(i);
            if (i < cappedLen) {
                char targetChar = t.getText().charAt(0);
                if (typedRaw.charAt(i) == targetChar) {
                    correctCount++;
                    if (!t.getFill().equals(Color.TRANSPARENT)) {
                        t.setFill(Color.LIMEGREEN);
                    }
                } else {
                    // Detect NEW error for screen shake (was white, now wrong)
                    if (mode == 3 && t.getFill().equals(Color.WHITE)) {
                        hasNewError = true;
                    }

                    // MEDIUM & HARD: Delayed red feedback (invisible errors)
                    if (mode == 2 || mode == 3) {
                        // Keep white for 0.5 seconds, then turn red
                        if (!t.getFill().equals(Color.TRANSPARENT)) {
                            t.setFill(Color.WHITE);
                            final int index = i;
                            Timeline delay = new Timeline(new KeyFrame(Duration.millis(500), e -> {
                                if (index < children.size()) {
                                    Text delayedText = (Text) children.get(index);
                                    if (!delayedText.getFill().equals(Color.LIMEGREEN) &&
                                            !delayedText.getFill().equals(Color.TRANSPARENT)) {
                                        delayedText.setFill(Color.RED);
                                    }
                                }
                            }));
                            delay.play();
                        }
                    } else {
                        // EASY: immediate red feedback
                        if (!t.getFill().equals(Color.TRANSPARENT)) {
                            t.setFill(Color.RED);
                        }
                    }
                }
            } else {
                if (!t.getFill().equals(Color.TRANSPARENT)) {
                    t.setFill(Color.WHITE);
                }
            }
        }

        // HARD: Screen shake on wrong letter
        if (mode == 3 && hasNewError) {
            shakeScreen();
        }

        double progress = children.isEmpty() ? 0.0 : (double) correctCount / children.size();
        ui.playerProgress.setProgress(progress);

        // how many *new* correct chars since last keystroke
        int deltaCorrect = correctCount - lastCorrectCount;
        if (deltaCorrect > 0) {
            scoreManager.awardPlayer(deltaCorrect);
        }
        lastCorrectCount = correctCount;

        int errors = Math.max(0, cappedLen - correctCount); // errors in this passage
        scoreManager.setPlayerErrors(errors);
        scoreManager.setPlayerProgress(progress);
        ui.playerScoreLabel.setText(scoreManager.playerSummary());

        // MULTIPLAYER: send progress to remote on every change
        if (multiplayer && networkOpponent != null) {
            networkOpponent.sendProgress(correctCount, errors);
        }

        // MEDIUM & HARD: Auto-backspace after completing a correct word (ONCE per position only)
        if ((mode == 2 || mode == 3) && typedRaw.length() > 0 && typedRaw.charAt(typedRaw.length() - 1) == ' ') {
            // Check if the word just completed was entirely correct
            int wordStart = typedRaw.lastIndexOf(' ', typedRaw.length() - 2) + 1;
            boolean wordCorrect = true;
            for (int i = wordStart; i < typedRaw.length() - 1; i++) {
                if (i >= playerPassage.length() || typedRaw.charAt(i) != playerPassage.charAt(i)) {
                    wordCorrect = false;
                    break;
                }
            }

            // Only backspace if: word is correct AND this position hasn't been backspaced before
            int backspacePosition = typedRaw.length() - 2; // The character to be removed
            if (wordCorrect && typedRaw.length() > 1 && !backspacedPositions.contains(backspacePosition)) {
                backspacedPositions.add(backspacePosition); // Mark this position as backspaced
                adjustingInput = true;
                Timeline backspaceDelay = new Timeline(new KeyFrame(Duration.millis(100), e -> {
                    String current = ui.inputField.getText();
                    if (current.length() > 1) {
                        // Remove one character before the space
                        String newText = current.substring(0, current.length() - 2) + " ";
                        ui.inputField.setText(newText);
                        ui.inputField.positionCaret(newText.length());
                    }
                    adjustingInput = false;
                }));
                backspaceDelay.play();
            }
        }

        // Player finished their passage (use capped input for reliable detection)
        if (typed.equals(playerPassage)) {
            playerFinishedCount++;

            Platform.runLater(() ->
                    ui.logBox.getChildren().add(
                            new Label("You finished a passage! (" + playerFinishedCount + ")")
                    )
            );

            // MULTIPLAYER: finishing ends the round (race style)
            if (multiplayer) {
                if (networkOpponent != null) networkOpponent.sendFinished();
                endGame();
                return;
            }

            // SINGLEPLAYER: defer passage transition to avoid TextField conflicts
            // Set flag to prevent recursive onPlayerType calls during transition
            adjustingInput = true;
            Platform.runLater(() -> {
                startPlayerPassage();
                adjustingInput = false;
            });
        }
    }


    // HARD: Screen shake effect when player makes an error
    private void shakeScreen() {
        if (ui == null || ui.rootPane == null) return;

        // Store original position
        double originalX = ui.rootPane.getTranslateX();

        // Create shake animation
        Timeline shake = new Timeline(
                new KeyFrame(Duration.millis(0), e -> ui.rootPane.setTranslateX(originalX - 5)),
                new KeyFrame(Duration.millis(50), e -> ui.rootPane.setTranslateX(originalX + 5)),
                new KeyFrame(Duration.millis(100), e -> ui.rootPane.setTranslateX(originalX - 3)),
                new KeyFrame(Duration.millis(150), e -> ui.rootPane.setTranslateX(originalX + 3)),
                new KeyFrame(Duration.millis(200), e -> ui.rootPane.setTranslateX(originalX))
        );
        shake.play();
    }

    // Helper: did the player just finish a correct word? (space-ended words only)
    @SuppressWarnings("unused")
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

    // -------------------- COMPUTER / OPPONENT UPDATES --------------------
    /**
     * Called by:
     * - AI path (singleplayer): ComputerOpponent (indirectly)
     * - Multiplayer path: NetworkOpponent receives "PROGRESS:pos:errors"
     *
     * In multiplayer, position is treated as absolute correct characters typed by remote.
     */
    public void updateComputerProgress(int position, int errors) {
        if (multiplayer) {
            updateOpponentFromNetwork(position, errors);
            return;
        }
        // Backwards compatible: treat last typed char as correct
        updateComputerTyping(position, errors, true);
    }

    /**
     * AI-only UI update.
     * lastWasCorrect indicates whether the most recently typed character was correct.
     */
    public void updateComputerTyping(int position, int errors, boolean lastWasCorrect) {
        if (ui == null) return; // allow headless tests to run without JavaFX

        Platform.runLater(() -> {
            int total = ui.computerTextFlow.getChildren().size();
            double progress = total == 0 ? 0.0 : (double) position / total;
            ui.computerProgress.setProgress(progress);

            scoreManager.setComputerProgress(progress);
            scoreManager.setComputerErrors(errors);
            if (lastWasCorrect) scoreManager.awardComputer(1);

            ui.computerScoreLabel.setText(scoreManager.computerSummary());

            // Update computer text coloring & caret (underline next char)
            for (int i = 0; i < total; i++) {
                Text t = (Text) ui.computerTextFlow.getChildren().get(i);
                t.setUnderline(i == position);

                if (i < position - 1) {
                    t.setFill(Color.LIMEGREEN);
                } else if (i == position - 1) {
                    t.setFill(lastWasCorrect ? Color.LIMEGREEN : Color.RED);
                } else {
                    t.setFill(Color.GRAY);
                }
            }
        });
    }

    /**
     * Multiplayer-only: update opponent UI + scoring from absolute position/errors sent over network.
     */
    private void updateOpponentFromNetwork(int position, int errors) {
        if (ui == null) return;

        Platform.runLater(() -> {
            int total = ui.computerTextFlow.getChildren().size();
            int pos = Math.max(0, Math.min(position, total));

            double progress = total == 0 ? 0.0 : (double) pos / total;
            ui.computerProgress.setProgress(progress);

            int delta = pos - lastOpponentPosition;
            if (delta > 0) scoreManager.awardComputer(delta);

            scoreManager.setComputerProgress(progress);
            scoreManager.setComputerErrors(Math.max(0, errors));
            ui.computerScoreLabel.setText(scoreManager.computerSummary());

            // simple coloring (green for correct positions)
            for (int i = 0; i < total; i++) {
                Text t = (Text) ui.computerTextFlow.getChildren().get(i);
                t.setUnderline(i == pos);
                if (i < pos) t.setFill(Color.LIMEGREEN);
                else t.setFill(Color.GRAY);
            }

            lastOpponentPosition = pos;
            lastOpponentErrors = Math.max(0, errors);
        });
    }

    /**
     * Called by:
     * - AI path: when ComputerOpponent finishes a passage
     * - Multiplayer path: NetworkOpponent receives "FINISHED"
     *
     * In multiplayer, FINISHED ends the round.
     */
    public void onComputerFinished() {
        Platform.runLater(() -> {
            if (!running || remainingSeconds <= 0) return;

            if (multiplayer) {
                ui.logBox.getChildren().add(new Label("Opponent finished the passage."));
                endGame();
                return;
            }

            // SINGLEPLAYER behavior (your existing logic)
            if (computerPassageDone) return;
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

            if (backgroundPool != null) {
                backgroundPool.scheduleAtFixedRate(
                        () -> { if (!paused && computer != null) computer.run(); },
                        0, 100, TimeUnit.MILLISECONDS
                );
            }
        });
    }

    // -------------------- PAUSE / RESUME / RESTART --------------------
    private void togglePause() {
        if (!running) return;
        if (paused) resumeGame();
        else pauseGame();
    }

    private void pauseGame() {
        paused = true;
        if (ui != null && ui.pauseButton != null) ui.pauseButton.setText("▶ Resume");
        Platform.runLater(this::showPauseOverlay);
        if (ui != null) ui.inputField.setDisable(true);
    }

    private void resumeGame() {
        paused = false;
        if (ui != null && ui.pauseButton != null) ui.pauseButton.setText("⏸ Pause");
        Platform.runLater(this::hidePauseOverlay);
        if (ui != null) ui.inputField.setDisable(false);
    }

    private void restartRound() {
        // Restarting mid-multiplayer without resync will desync both players.
        // Safest behavior: return to menu.
        if (multiplayer) {
            returnToHomeFromPause();
            return;
        }

        if (backgroundPool != null) {
            backgroundPool.shutdownNow();
            backgroundPool = null;
        }
        running = false;
        paused = false;

        Platform.runLater(() -> {
            hidePauseOverlay();
            if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);

            setupGameUI();
            startGameWithCountdown(initialDurationSeconds, currentDifficulty);
        });
    }

    private void returnToHomeFromPause() {
        if (backgroundPool != null) backgroundPool.shutdownNow();
        running = false;
        paused = false;

        // stop network loop if any
        try { if (networkOpponent != null) networkOpponent.stop(); } catch (Exception ignored) {}

        Platform.runLater(() -> {
            hidePauseOverlay();
            if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);
            if (onReturnToMenu != null) onReturnToMenu.run();
        });
    }

    private void showPauseOverlay() {
        if (ui == null || ui.rootPane == null || ui.rootPane.getScene() == null) return;

        if (pauseStage != null) {
            try { pauseStage.close(); } catch (Exception ignored) {}
            pauseStage = null;
        }

        Window owner = ui.rootPane.getScene().getWindow();
        Stage modal = new Stage();
        modal.initOwner(owner);
        modal.initModality(Modality.WINDOW_MODAL);
        modal.initStyle(StageStyle.TRANSPARENT);

        StackPane modalRoot = new StackPane();
        modalRoot.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        modalRoot.setPrefSize(owner.getWidth(), owner.getHeight());

        VBox menu = new VBox(12);
        menu.setMaxWidth(320);
        menu.setPrefWidth(320);
        menu.setAlignment(Pos.CENTER);
        menu.setStyle("-fx-background-color: transparent; -fx-padding: 8;");

        Button resume = new Button("Resume");
        resume.setDefaultButton(false);
        resume.getStyleClass().addAll("button", "primary", "modal-button");
        resume.setMaxWidth(Double.MAX_VALUE);
        resume.setPrefHeight(36);
        resume.setFont(Font.font("Consolas", 22));
        resume.setOnAction(e -> {
            modal.close();
            resumeGame();
        });
        resume.setOnMouseEntered(e -> resume.setStyle(
                "-fx-background-color: linear-gradient(to right, #96c93d, #00b09b);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));
        resume.setOnMouseExited(e -> resume.setStyle(
                "-fx-background-color: linear-gradient(to right, #00b09b, #96c93d);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));

        Button restart = new Button(multiplayer ? "Back to Menu" : "Start Again");
        restart.setDefaultButton(false);
        restart.getStyleClass().addAll("button", "primary", "modal-button");
        restart.setMaxWidth(Double.MAX_VALUE);
        restart.setPrefHeight(36);
        restart.setFont(Font.font("Consolas", 22));
        restart.setOnAction(e -> {
            modal.close();
            restartRound();
        });
        restart.setOnMouseEntered(e -> restart.setStyle(
                "-fx-background-color: linear-gradient(to right, #96c93d, #00b09b);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));
        restart.setOnMouseExited(e -> restart.setStyle(
                "-fx-background-color: linear-gradient(to right, #00b09b, #96c93d);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));

        Button home = new Button("Back to Menu");
        home.setDefaultButton(false);
        home.getStyleClass().addAll("button", "primary", "modal-button");
        home.setMaxWidth(Double.MAX_VALUE);
        home.setPrefHeight(36);
        home.setFont(Font.font("Consolas", 22));
        home.setOnAction(e -> {
            modal.close();
            returnToHomeFromPause();
        });
        home.setOnMouseEntered(e -> home.setStyle(
                "-fx-background-color: linear-gradient(to right, #96c93d, #00b09b);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));
        home.setOnMouseExited(e -> home.setStyle(
                "-fx-background-color: linear-gradient(to right, #00b09b, #96c93d);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));

        Button exit = new Button("Exit Game");
        exit.setDefaultButton(false);
        exit.getStyleClass().addAll("button", "danger", "modal-button");
        exit.setMaxWidth(Double.MAX_VALUE);
        exit.setPrefHeight(36);
        exit.setFont(Font.font("Consolas", 22));
        exit.setOnAction(e -> System.exit(0));
        exit.setOnMouseEntered(e -> exit.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff4b2b, #ff416c);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));
        exit.setOnMouseExited(e -> exit.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff416c, #ff4b2b);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));

        menu.getChildren().addAll(resume, restart, home, exit);
        modalRoot.getChildren().add(menu);
        StackPane.setAlignment(menu, Pos.CENTER);

        modalRoot.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) e.consume();
        });

        Scene modalScene = new Scene(modalRoot);
        try {
            var css = getClass().getResource("/typeshi/styles.css");
            if (css != null) modalScene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        modalScene.setFill(Color.TRANSPARENT);
        modal.setScene(modalScene);

        modal.setOnShown(evt -> centerModalOverOwner(modal, owner));
        owner.widthProperty().addListener((o, a, b) -> centerModalOverOwner(modal, owner));
        owner.heightProperty().addListener((o, a, b) -> centerModalOverOwner(modal, owner));

        pauseStage = modal;
        modal.show();

        menu.requestFocus();

        FadeTransition ft = new FadeTransition(Duration.millis(180), menu);
        menu.setOpacity(0);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void centerModalOverOwner(Stage modal, Window owner) {
        if (modal == null || owner == null) return;

        double ownerX = owner.getX();
        double ownerY = owner.getY();
        double ownerW = owner.getWidth();
        double ownerH = owner.getHeight();

        double modalW = modal.getWidth();
        double modalH = modal.getHeight();

        if (modalW <= 0 || modalH <= 0) {
            modal.centerOnScreen();
            return;
        }

        double x = ownerX + (ownerW - modalW) / 2.0;
        double y = ownerY + (ownerH - modalH) / 2.0;
        modal.setX(x);
        modal.setY(y);
    }

    private void hidePauseOverlay() {
        try {
            if (pauseStage != null) {
                pauseStage.close();
                pauseStage = null;
                return;
            }
        } catch (Exception ignored) {}

        if (ui == null || ui.rootPane == null || pauseOverlay == null) return;
        ui.rootPane.getChildren().remove(pauseOverlay);
        pauseOverlay = null;
    }

    // -------------------- END GAME --------------------
    private void endGame() {
        if (!running) return;
        running = false;

        if (backgroundPool != null) backgroundPool.shutdownNow();

        // stop network loop if any
        try { if (networkOpponent != null) networkOpponent.stop(); } catch (Exception ignored) {}

        int playerScore = scoreManager.getPlayerScore();
        int computerScore = scoreManager.getComputerScore();
        int playerErrors = scoreManager.getPlayerErrors();
        int computerErrors = scoreManager.getComputerErrors();

        Platform.runLater(() -> {
            hidePauseOverlay();
            if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);

            VictoryScreen victory = new VictoryScreen(
                    playerScore,
                    computerScore,
                    playerErrors,
                    computerErrors,
                    onReturnToMenu,
                    multiplayer
            );

            ui.rootPane.getScene().setRoot(victory.getRoot());
        });
    }

    // -------------------- MULTIPLAYER SYNC HELPERS --------------------
    private void setPassageFromNetwork(String passage) {
        // reset shared sequence to exactly this one passage
        passageSequence.clear();
        passageSequence.add(passage);

        playerPassageIndex = 0;
        computerPassageIndex = 0;

        playerPassage = passage;
        computerPassage = passage;

        fadeIndex = 0;
        computerPassageDone = false;

        ui.targetTextFlow.getChildren().clear();
        for (char c : playerPassage.toCharArray()) {
            Text t = new Text(String.valueOf(c));
            t.setFill(Color.WHITE);
            t.setStyle("-fx-font-family: Consolas; -fx-font-size: 18;");
            ui.targetTextFlow.getChildren().add(t);
        }

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
        lastOpponentPosition = 0;
        lastOpponentErrors = 0;
    }

    public void startHostMultiplayer(int port, int roundSeconds, int difficulty, int mode) {
        multiplayer = true;
        isHost = true;

        multiplayerPort = port;
        multiplayerRoundSeconds = roundSeconds;
        currentDifficulty = difficulty;
        this.mode = mode;

        // Show lobby immediately
        Platform.runLater(() -> prepareMultiplayerLobbyUI("Waiting for opponent..."));
        Platform.runLater(() -> ui.logBox.getChildren().add(new Label("Hosting on port " + multiplayerPort)));

        new Thread(() -> {
            try {
                // blocks on accept()
                mpServer = new MultiplayerServer(multiplayerPort);
                Platform.runLater(() -> ui.logBox.getChildren().add(new Label("Client connected!")));

                // NEW: generate a fresh passage when both are connected
                String text = wordGenerator.getRandomPassage();

                // apply it locally (host UI)
                Platform.runLater(() -> setPassageFromNetwork(text));

                // send to client
                mpServer.send("TEXT:" + text);

                // Send config: seconds, difficulty, mode
                mpServer.send("CFG:" + multiplayerRoundSeconds + ":" + currentDifficulty + ":" + this.mode);

                // Start receiver loop for opponent updates
                networkOpponent = new NetworkOpponent(this, mpServer, null, true);
                new Thread(networkOpponent).start();

                // Now start
                mpServer.send("START");

                Platform.runLater(() -> {
                    ui.inputField.setDisable(false);
                    ui.inputField.requestFocus();
                    startGameWithCountdown(multiplayerRoundSeconds, currentDifficulty);
                });


            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> ui.bottomInstructionLabel.setText("Host error: " + e.getMessage()));
            }
        }).start();
    }

    public void startJoinMultiplayer(String ip, int port) {
        multiplayer = true;
        isHost = false;

        multiplayerPort = port;

        Platform.runLater(() -> prepareMultiplayerLobbyUI("Connecting to host..."));

        new Thread(() -> {
            try {
                mpClient = new MultiplayerClient(ip, multiplayerPort);

                // Optional handshake
                try { mpClient.send("READY"); } catch (Exception ignored) {}

                // Read TEXT + CFG + START here (single reader thread).
                while (true) {
                    String msg = mpClient.receive();
                    if (msg == null) throw new RuntimeException("Disconnected");

                    if (msg.startsWith("TEXT:")) {
                        String text = msg.substring(5);
                        Platform.runLater(() -> setPassageFromNetwork(text));

                    } else if (msg.startsWith("CFG:")) {
                        // CFG:seconds:difficulty:mode
                        String[] p = msg.split(":");
                        multiplayerRoundSeconds = Integer.parseInt(p[1]);
                        currentDifficulty = Integer.parseInt(p[2]);
                        this.mode = Integer.parseInt(p[3]);

                    } else if (msg.equals("START")) {
                        break;
                    }
                }

                // Start receiver loop for opponent updates
                networkOpponent = new NetworkOpponent(this, null, mpClient, false);
                new Thread(networkOpponent).start();

                Platform.runLater(() -> {
                    ui.inputField.setDisable(false);
                    ui.inputField.requestFocus();
                    startGameWithCountdown(multiplayerRoundSeconds, currentDifficulty);
                });


            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> ui.bottomInstructionLabel.setText("Join failed: " + e.getMessage()));
            }
        }).start();
    }


    private String getCurrentPassage() {
        return playerPassage;
    }
}
