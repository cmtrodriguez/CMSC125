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
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
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

    private boolean paused = false; // pause state
    private StackPane pauseOverlay; // deprecated: previously used overlay
    private Stage pauseStage = null; // modal stage for pause dialog
    private int initialDurationSeconds = 0; // preserve initial duration for restart



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
        // allow tests to pass null for UI to avoid JavaFX initialization
        if (this.ui != null) {
            setupGameUI();
        }
    }

    // Call this from outside before startGame: 1=Easy, 2=Medium, 3=Hard
    public void setMode(int mode) {
        this.mode = mode;
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
    private Runnable onReturnToMenu = null;

    public void setOnReturnToMenu(Runnable onReturnToMenu) {
        this.onReturnToMenu = onReturnToMenu;
    }

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
        // schedule a wrapper so we can pause the AI without cancelling the executor
        backgroundPool.scheduleAtFixedRate(() -> {
            if (!paused && computer != null) computer.run();
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Countdown timer (pause-aware)
        backgroundPool.scheduleAtFixedRate(() -> {
            if (paused) return;
            remainingSeconds--;
            Platform.runLater(() ->
                    ui.timerLabel.setText(String.format("00:%02d", remainingSeconds)));
            if (remainingSeconds <= 0) endGame();
        }, 1, 1, TimeUnit.SECONDS);

        // HARD: fade words at a fixed interval (e.g., every 3 seconds)
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

        // NOTE: previous behavior removed trailing spaces after words in MEDIUM mode.
        // That prevented users from typing normal spaces; remove that behavior so spaces work normally.
        // (No auto-trim.)

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
        // Backwards compatible: treat last typed char as correct
        updateComputerTyping(position, errors, true);
    }

    /**
     * Update the UI to reflect a new typing event from the computer opponent.
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
                // underline the next character (acts as a caret); no underline if finished
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
            backgroundPool.scheduleAtFixedRate(() -> { if (!paused && computer != null) computer.run(); }, 0, 100, TimeUnit.MILLISECONDS);
        });
    }

    // -------------------- PAUSE / RESUME / RESTART --------------------
    private void togglePause() {
        if (!running) return;
        if (paused) resumeGame(); else pauseGame();
    }

    private void pauseGame() {
        paused = true;
        // update pause button label to show resume affordance
        if (ui != null && ui.pauseButton != null) ui.pauseButton.setText("▶ Resume");
        // show overlay with controls
        Platform.runLater(this::showPauseOverlay);
        // disable player input while paused
        if (ui != null) ui.inputField.setDisable(true);
    }

    private void resumeGame() {
        paused = false;
        // restore pause button label
        if (ui != null && ui.pauseButton != null) ui.pauseButton.setText("⏸ Pause");
        Platform.runLater(this::hidePauseOverlay);
        if (ui != null) ui.inputField.setDisable(false);
    }

    private void restartRound() {
        // Stop current background tasks and prepare for a fresh round
        if (backgroundPool != null) {
            backgroundPool.shutdownNow();
            backgroundPool = null;
        }
        running = false;
        paused = false;

        // Update UI and immediately start the countdown for a new round
        Platform.runLater(() -> {
            // Close any pause modal/stage and disable pause button until the new round starts
            hidePauseOverlay();
            if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);

            // Reset UI for a fresh round (clears passages and progress)
            setupGameUI();

            // Start the countdown which will start the new round when complete
            startGameWithCountdown(initialDurationSeconds, currentDifficulty);
        });
    }

    private void returnToHomeFromPause() {
        if (backgroundPool != null) backgroundPool.shutdownNow();
        running = false;
        paused = false;
        Platform.runLater(() -> {
            hidePauseOverlay();
            if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);

            if (onReturnToMenu != null) onReturnToMenu.run();
        });
    }

    private void showPauseOverlay() {
        // Use a modal stage to ensure the dialog is centered over the app window
        if (ui == null || ui.rootPane == null || ui.rootPane.getScene() == null) return;

        // Close any previous modal
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
        menu.setAlignment(javafx.geometry.Pos.CENTER);
        // minimal/transparent background so only buttons appear over the dim backdrop
        menu.setStyle("-fx-background-color: transparent; -fx-padding: 8;");

        Button resume = new Button("Resume");
        resume.setDefaultButton(false);
        resume.getStyleClass().addAll("button", "primary", "modal-button");
        resume.setMaxWidth(Double.MAX_VALUE);
        resume.setPrefHeight(36);
        resume.setFont(javafx.scene.text.Font.font("Consolas", 22));
        resume.setOnAction(e -> {
            modal.close();
            resumeGame();
        });
        resume.setOnMouseEntered(e -> resume.setStyle("-fx-background-color: linear-gradient(to right, #96c93d, #00b09b); -fx-text-fill: white; -fx-background-radius: 10;"));
        resume.setOnMouseExited(e -> resume.setStyle("-fx-background-color: linear-gradient(to right, #00b09b, #96c93d); -fx-text-fill: white; -fx-background-radius: 10;"));

        Button restart = new Button("Start Again");
        restart.setDefaultButton(false);
        restart.getStyleClass().addAll("button", "primary", "modal-button");
        restart.setMaxWidth(Double.MAX_VALUE);
        restart.setPrefHeight(36);
        restart.setFont(javafx.scene.text.Font.font("Consolas", 22));
        restart.setOnAction(e -> {
            modal.close();
            restartRound();
        });
        restart.setOnMouseEntered(e -> restart.setStyle("-fx-background-color: linear-gradient(to right, #96c93d, #00b09b); -fx-text-fill: white; -fx-background-radius: 10;"));
        restart.setOnMouseExited(e -> restart.setStyle("-fx-background-color: linear-gradient(to right, #00b09b, #96c93d); -fx-text-fill: white; -fx-background-radius: 10;"));

        Button home = new Button("Back to Menu");
        home.setDefaultButton(false);
        home.getStyleClass().addAll("button", "primary", "modal-button");
        home.setMaxWidth(Double.MAX_VALUE);
        home.setPrefHeight(36);
        home.setFont(javafx.scene.text.Font.font("Consolas", 22));
        home.setOnAction(e -> {
            modal.close();
            returnToHomeFromPause();
        });
        home.setOnMouseEntered(e -> home.setStyle("-fx-background-color: linear-gradient(to right, #96c93d, #00b09b); -fx-text-fill: white; -fx-background-radius: 10;"));
        home.setOnMouseExited(e -> home.setStyle("-fx-background-color: linear-gradient(to right, #00b09b, #96c93d); -fx-text-fill: white; -fx-background-radius: 10;"));

        Button exit = new Button("Exit Game");
        exit.setDefaultButton(false);
        exit.getStyleClass().addAll("button", "danger", "modal-button");
        exit.setMaxWidth(Double.MAX_VALUE);
        exit.setPrefHeight(36);
        exit.setFont(javafx.scene.text.Font.font("Consolas", 22));
        exit.setOnAction(e -> System.exit(0));
        exit.setOnMouseEntered(e -> exit.setStyle("-fx-background-color: linear-gradient(to right, #ff4b2b, #ff416c); -fx-text-fill: white; -fx-background-radius: 10;"));
        exit.setOnMouseExited(e -> exit.setStyle("-fx-background-color: linear-gradient(to right, #ff416c, #ff4b2b); -fx-text-fill: white; -fx-background-radius: 10;"));

        menu.getChildren().addAll(resume, restart, home, exit);
        modalRoot.getChildren().add(menu);
        StackPane.setAlignment(menu, javafx.geometry.Pos.CENTER);

        // Consume Enter events on the modal so pressing Enter doesn't activate other buttons on owner
        modalRoot.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) e.consume();
        });

        Scene modalScene = new Scene(modalRoot);
        // Attach app stylesheet so modal buttons match HomeScreen styles
        try {
            var css = getClass().getResource("/typeshi/styles.css");
            if (css != null) modalScene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        modalScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        modal.setScene(modalScene);

        // keep modal centered over owner when shown/resized
        modal.setOnShown(evt -> centerModalOverOwner(modal, owner));
        owner.widthProperty().addListener((o, a, b) -> centerModalOverOwner(modal, owner));
        owner.heightProperty().addListener((o, a, b) -> centerModalOverOwner(modal, owner));

        pauseStage = modal;
        modal.show();

        // initial focus on the menu
        menu.requestFocus();

        // small fade-in for polish
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), menu);
        menu.setOpacity(0);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    // Helper to center modal Stage over owner window
    private void centerModalOverOwner(Stage modal, Window owner) {
        if (modal == null || owner == null) return;
        // center over owner
        double ownerX = owner.getX();
        double ownerY = owner.getY();
        double ownerW = owner.getWidth();
        double ownerH = owner.getHeight();

        double modalW = modal.getWidth();
        double modalH = modal.getHeight();
        // If modal has not computed size yet, rely on centerOnScreen fallback
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
        // Close modal stage if present, otherwise remove the in-root overlay (legacy)
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
        running = false;
        if (backgroundPool != null) backgroundPool.shutdownNow();

        // capture final stats
        int playerScore = scoreManager.getPlayerScore();
        int computerScore = scoreManager.getComputerScore();
        int playerErrors = scoreManager.getPlayerErrors();
        int computerErrors = scoreManager.getComputerErrors();

        // ensure pause UI is cleared and pause button disabled
        Platform.runLater(() -> {
            hidePauseOverlay();
            if (ui != null && ui.pauseButton != null) ui.pauseButton.setDisable(true);

            VictoryScreen victory = new VictoryScreen(
                    playerScore,
                    computerScore,
                    playerErrors,
                    computerErrors,
                    onReturnToMenu
            );

            // swap the current game UI for the victory screen, keeping the same Scene
            ui.rootPane.getScene().setRoot(victory.getRoot());
        });
    }


    private String getCurrentPassage() {
        return playerPassage;
    }
}
