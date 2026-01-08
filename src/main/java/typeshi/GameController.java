package typeshi;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.control.Alert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * game controller - singleplayer & multiplayer logic
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

    private int playerCumulativeErrors = 0;
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

    // NEW: separate fade index for computer side
    private int computerFadeIndex = 0;

    // NEW: handle the scheduled HARD fade task so we can cancel/reschedule it
    private ScheduledFuture<?> hardFadeFuture = null;

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

    // rounds support
    private int totalRounds = 1;
    private int currentRound = 1;
    // UI controls placed near pause button
    private Label roundLabelNode = null;
    private Button backToHomeButton = null;

    // countdown state so Pause works even while the 3-2-1 overlay runs
    private boolean countdownActive = false;
    private Timeline countdownTimeline = null;

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

    public void setTotalRounds(int total) {
        this.totalRounds = Math.max(1, total);
        this.currentRound = 1;
        // update visible label if already present
        if (roundLabelNode != null) {
            Platform.runLater(() -> roundLabelNode.setText("Round " + currentRound + " / " + totalRounds));
        }
    }

    private void setupGameUI() {
        // listen for typing
        ui.inputField.textProperty().addListener((obs, oldVal, newVal) -> onPlayerType());

        // consume enter
        ui.inputField.setOnAction(e -> {
            e.consume();
            onPlayerType();
        });

        // reset passages
        passageSequence.clear();
        playerPassageIndex = 0;
        computerPassageIndex = 0;

        String first = getOrCreatePassageAt(0);
        playerPassage = first;
        computerPassage = first;

        fadeIndex = 0;
        computerFadeIndex = 0;
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

        lastOpponentPosition = 0;
        lastOpponentErrors = 0;

        // prepare pause button + round label
        if (ui.pauseButton != null) {
            ui.pauseButton.setDisable(false);
            ui.pauseButton.setVisible(true);
            ui.pauseButton.setOnAction(e -> togglePause());

            // Attempt to insert round label and back button into the same HBox as pauseButton
            Node parent = ui.pauseButton.getParent();
            if (parent instanceof HBox) {
                HBox topBox = (HBox) parent;

                if (roundLabelNode == null) {
                    roundLabelNode = new Label("Round " + currentRound + " / " + totalRounds);
                    roundLabelNode.setStyle("-fx-text-fill: white; -fx-font-family: Consolas;");
                }
                if (!topBox.getChildren().contains(roundLabelNode)) {
                    topBox.getChildren().add(topBox.getChildren().indexOf(ui.pauseButton), roundLabelNode);
                }

                // (removed side "back" button - handled via pause modal controls)
            }
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
        // remember duration
        this.initialDurationSeconds = durationSeconds;

        // update round label
        if (roundLabelNode != null) {
            Platform.runLater(() -> roundLabelNode.setText("Round " + currentRound + " / " + totalRounds));
        }

        countdownActive = true;

        // block typing
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

        // Use field so pause can stop the countdown
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0), e -> countdownText.setText("3")),
                new KeyFrame(Duration.seconds(1), e -> countdownText.setText("2")),
                new KeyFrame(Duration.seconds(2), e -> countdownText.setText("1")),
                new KeyFrame(Duration.seconds(3), e -> countdownText.setText("GO!")),
                new KeyFrame(Duration.seconds(3.5), e -> {
                    // remove overlay: restore original center node
                    ui.rootPane.setCenter(originalCenter);
                    ui.inputField.setDisable(false);
                    ui.inputField.requestFocus();
                    countdownActive = false;
                    startGame(durationSeconds, difficulty);
                })
        );
        countdownTimeline.play();
    }

    // New helper: show "Round X Done. Next Round!" briefly between rounds
    private void showRoundDoneOverlay(Runnable onFinished) {
        if (ui == null || ui.rootPane == null) { onFinished.run(); return; }

        javafx.scene.Node originalCenter = ui.rootPane.getCenter();

        Rectangle dim = new Rectangle();
        Label text = new Label("Round " + (currentRound - 1) + " Done.  Next Round!");
        text.setTextFill(Color.WHITE);
        text.setFont(Font.font("Consolas", 36));

        StackPane overlay = new StackPane(dim, text);
        overlay.setAlignment(Pos.CENTER);
        dim.widthProperty().bind(overlay.widthProperty());
        dim.heightProperty().bind(overlay.heightProperty());
        dim.setFill(new Color(0, 0, 0, 0.35));

        StackPane centerStack = new StackPane(originalCenter, overlay);
        ui.rootPane.setCenter(centerStack);

        countdownActive = true;
        PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(e -> {
            // remove overlay and continue to countdown for next round
            ui.rootPane.setCenter(originalCenter);
            countdownActive = false;
            onFinished.run();
        });
        pause.play();
    }

    // -------------------- START GAME (TIMER + AI/NETWORK) --------------------
    public void startGame(int durationSeconds, int difficulty) {
        if (running) return;
        running = true;

        remainingSeconds = durationSeconds;
        currentDifficulty = difficulty;

        // reset scores at match start only
        if (currentRound == 1) scoreManager.reset();

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
            Platform.runLater(() -> {
                int minutes = remainingSeconds / 60;
                int seconds = remainingSeconds % 60;
                ui.timerLabel.setText(String.format("%02d:%02d", minutes, seconds));
            });
            if (remainingSeconds <= 0) endGame();

        }, 1, 1, TimeUnit.SECONDS);

        // HARD: fade words at a fixed interval (both player AND computer)
        if (mode == 3) {
            fadeIndex = 0;
            computerFadeIndex = 0;
            // cancel previously-scheduled hard fade (if any)
            if (hardFadeFuture != null) {
                hardFadeFuture.cancel(false);
                hardFadeFuture = null;
            }
            // start with a short grace so the first passage is visible (no immediate fading)
            hardFadeFuture = backgroundPool.scheduleAtFixedRate(
                    () -> { if (!paused) Platform.runLater(() -> { fadeNextWord(); fadeNextComputerWord(); }); },
                    3, 3, TimeUnit.SECONDS
            );
        }
    }

    // -------------------- START NEW PASSAGE (PLAYER ONLY) --------------------
    private void startPlayerPassage() {
        playerPassageIndex++; // human moves ahead in shared sequence
        playerPassage = getOrCreatePassageAt(playerPassageIndex);
        fadeIndex = 0;

        playerCumulativeErrors = 0;

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
        // If HARD mode: ensure subsequent passages start fading immediately after first completion
        // (playerFinishedCount is incremented before this method is called)
        if (mode == 3 && playerFinishedCount >= 1 && backgroundPool != null) {
            // Cancel any delayed starter and schedule an immediate repeating fade
            if (hardFadeFuture != null) {
                hardFadeFuture.cancel(false);
            }
            hardFadeFuture = backgroundPool.scheduleAtFixedRate(
                    () -> { if (!paused) Platform.runLater(() -> { fadeNextWord(); fadeNextComputerWord(); }); },
                    0, 3, TimeUnit.SECONDS
            );
            // Apply one immediate fade step so effects are visible right away
            Platform.runLater(() -> { fadeNextWord(); fadeNextComputerWord(); });
        }
    }

    // -------------------- START NEW PASSAGE (COMPUTER ONLY) --------------------
    private void startComputerPassage() {
        computerPassageIndex++; // AI moves ahead in shared sequence
        computerPassage = getOrCreatePassageAt(computerPassageIndex);
        computerPassageDone = false;

        computerFadeIndex = 0;

        ui.computerTextFlow.getChildren().clear();
        for (char c : computerPassage.toCharArray()) {
            Text t = new Text(String.valueOf(c));
            t.setFill(Color.GRAY);
            t.setStyle("-fx-font-family: Consolas; -fx-font-size: 18;");
            ui.computerTextFlow.getChildren().add(t);
        }

        ui.computerProgress.setProgress(0);

        // Do not force immediate fade here; fading is scheduled by startGame or when a passage has been completed.
    }

    // -------------------- PLAYER INPUT --------------------
    private void onPlayerType() {
        // ignore when not running or adjusting
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
        int prevCorrectCount = lastCorrectCount;
        int deltaCorrect = correctCount - prevCorrectCount;
        if (deltaCorrect > 0) {
            scoreManager.awardPlayer(deltaCorrect);
        }
        lastCorrectCount = correctCount;

        cappedLen = Math.min(typedRaw.length(), children.size());

        // Determine the number of newly typed letters
        int prevTypedLen = prevCorrectCount; // track last correct prefix length (use previous value)
        int typedLen = typed.length();
        int newErrors = 0;

        // Only check new characters that were just typed
        for (int i = prevTypedLen; i < typedLen; i++) {
            if (i >= playerPassage.length()) break; // safety
            char typedChar = typed.charAt(i);
            char targetChar = playerPassage.charAt(i);

            if (typedChar != targetChar) {
                newErrors++;
                // Mark this character RED immediately in TextFlow
                Text t = (Text) children.get(i);
                t.setFill(Color.RED);
            }
        }

        // Increment cumulative errors only by the newly typed wrong letters
        playerCumulativeErrors += newErrors;
        scoreManager.setPlayerErrors(playerCumulativeErrors);

        scoreManager.setPlayerProgress(progress);
        ui.playerScoreLabel.setText(scoreManager.playerSummary());

        // MULTIPLAYER: send progress to remote on every change
        if (multiplayer && networkOpponent != null) {
            networkOpponent.sendProgress(correctCount, newErrors);
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

        // Inside onPlayerType() ...
        if (typed.equals(playerPassage)) {
            playerFinishedCount++;

            Platform.runLater(() ->
                    ui.logBox.getChildren().add(
                            new Label("You finished a passage! (" + playerFinishedCount + ")")
                    )
            );

            if (multiplayer) {
                if (networkOpponent != null) networkOpponent.sendFinished();

                adjustingInput = true;
                Platform.runLater(() -> {
                    startPlayerPassage();
                    adjustingInput = false;
                });
                return;
            }

            // Singleplayer logic (already handles looping)
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

    // hard: fade (hide) the next word from the targetTextFlow
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

    // hard: fade next computer word
    private void fadeNextComputerWord() {
        var children = ui.computerTextFlow.getChildren();
        if (children.isEmpty()) return;

        while (computerFadeIndex < children.size()) {
            Text t = (Text) children.get(computerFadeIndex);
            if (!t.getFill().equals(Color.TRANSPARENT)) break;
            computerFadeIndex++;
        }
        if (computerFadeIndex >= children.size()) return;

        while (computerFadeIndex < children.size()) {
            Text t = (Text) children.get(computerFadeIndex);
            t.setFill(Color.TRANSPARENT);
            char ch = t.getText().charAt(0);
            computerFadeIndex++;
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

            // don't award if last character was hidden by HARD fading
            boolean wasHidden = false;
            if (position > 0 && position - 1 < total) {
                Text lastChar = (Text) ui.computerTextFlow.getChildren().get(position - 1);
                wasHidden = lastChar.getFill().equals(Color.TRANSPARENT);
            }
            if (lastWasCorrect && !wasHidden) scoreManager.awardComputer(1);

            ui.computerScoreLabel.setText(scoreManager.computerSummary());

            // update coloring + caret
            for (int i = 0; i < total; i++) {
                Text t = (Text) ui.computerTextFlow.getChildren().get(i);
                t.setUnderline(i == position);

                if (i < position - 1) {
                    t.setFill(Color.LIMEGREEN);
                } else if (i == position - 1) {
                    if (lastWasCorrect && !wasHidden) {
                        t.setFill(Color.LIMEGREEN);
                    } else {
                        // medium/hard: delayed red feedback
                        if (mode == 2 || mode == 3) {
                            if (!t.getFill().equals(Color.TRANSPARENT)) {
                                t.setFill(Color.WHITE);
                                final int index = i;
                                Timeline delay = new Timeline(new KeyFrame(Duration.millis(500), e -> {
                                    if (index < ui.computerTextFlow.getChildren().size()) {
                                        Text delayedText = (Text) ui.computerTextFlow.getChildren().get(index);
                                        if (!delayedText.getFill().equals(Color.LIMEGREEN) &&
                                                !delayedText.getFill().equals(Color.TRANSPARENT)) {
                                            delayedText.setFill(Color.RED);
                                        }
                                    }
                                }));
                                delay.play();
                            } else {
                                // hidden char: indicate struggle in HARD
                                if (mode == 3) shakeScreen();
                            }
                        } else {
                            if (!t.getFill().equals(Color.TRANSPARENT)) {
                                t.setFill(Color.RED);
                            }
                        }

                        // hard: shake on mistakes or hitting hidden char
                        if (mode == 3 && (!lastWasCorrect || wasHidden)) {
                            shakeScreen();
                        }
                    }
                } else {
                    t.setFill(Color.GRAY);
                }
            }
        });
    }

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

    public void onComputerFinished() {
        Platform.runLater(() -> {
            if (!running || remainingSeconds <= 0) return;

            if (multiplayer) {

                computerFinishedCount++;

                ui.logBox.getChildren().add(new Label("Opponent finished the passage."));
                startComputerPassage();

                // Reset opponent progress tracking
                lastOpponentPosition = 0;
                lastOpponentErrors = 0;

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
        if (!running && !countdownActive && !multiplayer) return;
        if (paused) resumeGame();
        else pauseGame();
    }

    private void pauseGame() {
        paused = true;
        if (ui != null && ui.pauseButton != null) ui.pauseButton.setText("▶ Resume");
        Platform.runLater(this::showPauseOverlay);
        if (ui != null) ui.inputField.setDisable(true);
        // Pause countdown (if active)
        if (countdownActive && countdownTimeline != null) countdownTimeline.pause();
    }

    private void resumeGame() {
        paused = false;
        if (ui != null && ui.pauseButton != null) ui.pauseButton.setText("⏸ Pause");
        Platform.runLater(this::hidePauseOverlay);
        if (ui != null) ui.inputField.setDisable(false);
        // Resume countdown (if active)
        if (countdownActive && countdownTimeline != null) countdownTimeline.play();
    }

    private void restartRound() {
        if (multiplayer) {
            returnToHomeFromPause();
            return;
        }

        if (backgroundPool != null) {
            backgroundPool.shutdownNow();
            backgroundPool = null;
        }
        if (hardFadeFuture != null) { hardFadeFuture.cancel(false); hardFadeFuture = null; }

        running = false;
        paused = false;

        Platform.runLater(() -> {
            hidePauseOverlay();
            if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);

            setupGameUI();
            startGameWithCountdown(initialDurationSeconds, currentDifficulty);
        });
    }

    void returnToHomeFromPause() {
        if (backgroundPool != null) backgroundPool.shutdownNow();
        if (hardFadeFuture != null) { hardFadeFuture.cancel(false); hardFadeFuture = null; }

        running = false;
        paused = false;

        // ✅ SEND INTENT FIRST
        try {
            if (networkOpponent != null) {
                networkOpponent.sendDisconnect();
            }
        } catch (Exception ignored) {}

        // ✅ THEN CLOSE SOCKETS
        try {
            if (networkOpponent != null) {
                networkOpponent.stop();
                networkOpponent = null;
            }
        } catch (Exception ignored) {}

        multiplayer = false;
        isHost = false;

        Platform.runLater(() -> {
            hidePauseOverlay();
            if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);
            if (onReturnToMenu != null) onReturnToMenu.run();
        });
    }

    /**
     * Called when the opponent disconnects unexpectedly.
     * Treats the multiplayer match as finished and returns to menu.
     */
    public void onOpponentDisconnected() {
        if (!multiplayer) return;

        Platform.runLater(() -> {
            try {
                String message = isHost
                        ? "Opponent disconnected."
                        : "Host ended the game.";

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Multiplayer Ended");
                alert.setHeaderText(null);
                alert.setContentText(message);

                if (ui != null && ui.rootPane != null && ui.rootPane.getScene() != null) {
                    alert.initOwner(ui.rootPane.getScene().getWindow());
                }

                try {
                    var css = getClass().getResource("/typeshi/styles.css");
                    if (css != null) {
                        alert.getDialogPane().getStylesheets().add(css.toExternalForm());
                    }
                } catch (Exception ignored) {}

                alert.showAndWait();

                // cleanup AFTER user sees message
                returnToHomeFromPause();

            } catch (Exception ignored) {}
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
        exit.setOnAction(e -> {
            // hide the pause modal and show a single confirmation dialog
            try { if (modal != null) modal.hide(); } catch (Exception ignored) {}
            showExitConfirm(modal);
        });
        exit.setOnMouseEntered(e -> exit.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff4b2b, #ff416c);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));
        exit.setOnMouseExited(e -> exit.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff416c, #ff4b2b);"
                        + " -fx-text-fill: white; -fx-background-radius: 10;"
        ));

        if (multiplayer) {
            // in multiplayer: avoid duplicate "back to menu" — use restart ("back to menu") and don't show home
            menu.getChildren().addAll(resume, restart, exit);
        } else {
            menu.getChildren().addAll(resume, restart, home, exit);
        }
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
        if (hardFadeFuture != null) { hardFadeFuture.cancel(false); hardFadeFuture = null; }

        // stop network loop if any
        try { if (networkOpponent != null) networkOpponent.stop(); } catch (Exception ignored) {}

        int playerScore = scoreManager.getPlayerScore();
        int computerScore = scoreManager.getComputerScore();
        int playerErrors = scoreManager.getPlayerErrors();
        int computerErrors = scoreManager.getComputerErrors();

        // If singleplayer match has more rounds, start next round after a short "round done" overlay
        if (!multiplayer && currentRound < totalRounds) {
            currentRound++;
            Platform.runLater(() -> {
                hidePauseOverlay();
                if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);

                // Prepare fresh passages for next round (preserve cumulative scores)
                passageSequence.clear();
                playerPassageIndex = 0;
                computerPassageIndex = 0;
                String first = getOrCreatePassageAt(0);
                playerPassage = first;
                computerPassage = first;
                fadeIndex = 0;
                computerFadeIndex = 0;
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

                // show a small "Round Done" overlay, then start next round countdown
                ui.logBox.getChildren().add(new Label("Round " + (currentRound - 1) + " complete"));
                showRoundDoneOverlay(() -> startGameWithCountdown(initialDurationSeconds, currentDifficulty));
            });
            return;
        }

        // else show final victory screen (existing behavior)
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

    // Ensure multiplayer lobby shows the back button in case parent HBox isn't found
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

        // ensure pause is available in lobby
        if (ui.pauseButton != null) {
            ui.pauseButton.setVisible(true);
            ui.pauseButton.setDisable(false);
        }
         // (side back button removed)
	}

	// confirm exit modal; if cancelled, re-show the pause modal passed as argument.
	private void showExitConfirm(Stage pauseModal) {
		Window owner = (pauseModal != null) ? pauseModal : (ui != null && ui.rootPane != null && ui.rootPane.getScene() != null ? ui.rootPane.getScene().getWindow() : null);

		Stage confirm = new Stage();
		if (owner != null) confirm.initOwner(owner);
		confirm.initModality(Modality.WINDOW_MODAL);
		confirm.initStyle(StageStyle.TRANSPARENT);

		StackPane root = new StackPane();
		root.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
		root.setPrefSize((owner != null ? owner.getWidth() : 400) * 0.6, (owner != null ? owner.getHeight() : 200) * 0.25);

		VBox box = new VBox(12);
		box.setMaxWidth(400);
		box.setAlignment(Pos.CENTER);
		box.setStyle("-fx-background-color: transparent; -fx-padding: 12;");

		Label msg = new Label("Are you sure you want to exit?");
		msg.setFont(Font.font("Consolas", 18));
		msg.setTextFill(Color.WHITE);

		HBox buttons = new HBox(10);
		buttons.setAlignment(Pos.CENTER);

		Button cancel = new Button("Back");
		cancel.getStyleClass().addAll("button", "primary", "modal-button");
		cancel.setFont(Font.font("Consolas", 16));
		cancel.setOnAction(ev -> {
			try { confirm.close(); } catch (Exception ignored) {}
			// restore the pause modal so it becomes the only visible popup
			try { if (pauseModal != null) pauseModal.show(); } catch (Exception ignored) {}
		});

		Button confirmExit = new Button("Exit Game");
		confirmExit.getStyleClass().addAll("button", "danger", "modal-button");
		confirmExit.setFont(Font.font("Consolas", 16));
		confirmExit.setOnAction(ev -> {
			try { if (networkOpponent != null) networkOpponent.stop(); } catch (Exception ignored) {}
			Platform.exit();
			System.exit(0);
		});

		confirmExit.setOnMouseEntered(ev -> confirmExit.setStyle(
				"-fx-background-color: linear-gradient(to right, #ff4b2b, #ff416c); -fx-text-fill: white; -fx-background-radius: 10;"
		));
		confirmExit.setOnMouseExited(ev -> confirmExit.setStyle(
				"-fx-background-color: linear-gradient(to right, #ff416c, #ff4b2b); -fx-text-fill: white; -fx-background-radius: 10;"
		));

		buttons.getChildren().addAll(cancel, confirmExit);
		box.getChildren().addAll(msg, buttons);
		root.getChildren().add(box);
		StackPane.setAlignment(box, Pos.CENTER);

		Scene scene = new Scene(root);
		try {
			var css = getClass().getResource("/typeshi/styles.css");
			if (css != null) scene.getStylesheets().add(css.toExternalForm());
		} catch (Exception ex) { ex.printStackTrace(); }
		scene.setFill(Color.TRANSPARENT);

		confirm.setScene(scene);
		confirm.setOnShown(evt -> centerModalOverOwner(confirm, owner));
		confirm.show();

		FadeTransition ft = new FadeTransition(Duration.millis(180), box);
		box.setOpacity(0);
		ft.setFromValue(0);
		ft.setToValue(1);
		ft.play();
	}

	// confirm leave match; returns home on confirm, cancels otherwise
    private void showLeaveConfirm(Window owner) {
        Stage confirm = new Stage();
        if (owner != null) confirm.initOwner(owner);
        confirm.initModality(Modality.WINDOW_MODAL);
        confirm.initStyle(StageStyle.TRANSPARENT);

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        VBox box = new VBox(12);
        box.setMaxWidth(420);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: transparent; -fx-padding: 12;");

        Label msg = new Label("are you sure you want to leave the match?");
        msg.setFont(Font.font("Consolas", 16));
        msg.setTextFill(Color.WHITE);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);

        Button cancel = new Button("back");
        cancel.getStyleClass().addAll("button", "primary", "modal-button");
        cancel.setFont(Font.font("Consolas", 14));
        cancel.setOnAction(e -> { try { confirm.close(); } catch (Exception ignored) {} });

        Button leave = new Button("leave");
        leave.getStyleClass().addAll("button", "danger", "modal-button");
        leave.setFont(Font.font("Consolas", 14));
        leave.setOnAction(e -> {
            try { if (networkOpponent != null) networkOpponent.stop(); } catch (Exception ignored) {}
            try { confirm.close(); } catch (Exception ignored) {}
            // return to home
            returnToHomeFromPause();
        });

        buttons.getChildren().addAll(cancel, leave);
        box.getChildren().addAll(msg, buttons);
        root.getChildren().add(box);
        StackPane.setAlignment(box, Pos.CENTER);

        Scene s = new Scene(root);
        try { var css = getClass().getResource("/typeshi/styles.css"); if (css != null) s.getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}
        s.setFill(Color.TRANSPARENT);

        confirm.setScene(s);
        confirm.setOnShown(e -> centerModalOverOwner(confirm, owner));
        confirm.show();
        FadeTransition ft = new FadeTransition(Duration.millis(180), box);
        box.setOpacity(0);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void setPassageFromNetwork(String passage) {
        while (passageSequence.size() <= 0) {
            passageSequence.add(wordGenerator.getRandomPassage());
        }
        playerPassage = passage;
        computerPassage = passage;
        playerPassageIndex = 0;
        computerPassageIndex = 0;
        fadeIndex = 0;
        computerFadeIndex = 0;
        computerPassageDone = false;

        Platform.runLater(() -> {
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
        });
    }

    // host a multiplayer match (called from Main)
    public void startHostMultiplayer(int port, int roundSeconds, int difficulty, int mode) {
        multiplayer = true;
        isHost = true;
        multiplayerPort = port;
        multiplayerRoundSeconds = roundSeconds;
        currentDifficulty = difficulty;
        this.mode = mode;

        Platform.runLater(() -> {
            prepareMultiplayerLobbyUI("Waiting for opponent...");
            ui.logBox.getChildren().add(new Label("Hosting on port " + multiplayerPort));
        });

        new Thread(() -> {
            try {
                mpServer = new MultiplayerServer(multiplayerPort); // blocks until client connects
                Platform.runLater(() -> ui.logBox.getChildren().add(new Label("Client connected!")));

                // generate & share passage + cfg
                String text = wordGenerator.getRandomPassage();
                Platform.runLater(() -> setPassageFromNetwork(text));
                mpServer.send("TEXT:" + text);
                mpServer.send("CFG:" + multiplayerRoundSeconds + ":" + currentDifficulty + ":" + this.mode);

                // start receiver loop for opponent updates
                networkOpponent = new NetworkOpponent(this, mpServer, null, true);
                new Thread(networkOpponent).start();

                // signal start
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

    // join a hosted match (called from Main)
    public void startJoinMultiplayer(String ip, int port) {
        multiplayer = true;
        isHost = false;
        multiplayerPort = port;

        Platform.runLater(() -> prepareMultiplayerLobbyUI("Connecting to host..."));

        new Thread(() -> {
            try {
                mpClient = new MultiplayerClient(ip, multiplayerPort);
                try { mpClient.send("READY"); } catch (Exception ignored) {}

                // wait for TEXT, CFG, START
                while (true) {
                    String msg = mpClient.receive();
                    if (msg == null) throw new RuntimeException("Disconnected");
                    if (msg.startsWith("TEXT:")) {
                        String text = msg.substring(5);
                        Platform.runLater(() -> setPassageFromNetwork(text));
                    } else if (msg.startsWith("CFG:")) {
                        String[] p = msg.split(":");
                        multiplayerRoundSeconds = Integer.parseInt(p[1]);
                        currentDifficulty = Integer.parseInt(p[2]);
                        this.mode = Integer.parseInt(p[3]);
                    } else if (msg.equals("START")) {
                        break;
                    }
                }

                // start receiver loop
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
}
