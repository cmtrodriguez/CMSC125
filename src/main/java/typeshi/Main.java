package typeshi;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class Main extends Application {

    private Stage primaryStage;
    private Scene scene;       // one shared scene

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        showLoadingScreen();
    }

    private void showLoadingScreen() {
        LoadingScreen loading = new LoadingScreen();

        scene = new Scene(loading.getRoot(), 800, 500);
        try {
            var css = getClass().getResource("/typeshi/styles.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ex) {
            ex.printStackTrace();

        }

        primaryStage.setScene(scene);
        primaryStage.setTitle("TypeShi");
        primaryStage.show();

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> showHomeScreen());
        pause.play();
    }

    private void showHomeScreen() {
        HomeScreen home = new HomeScreen();

        home.getPlayButton().setOnAction(e -> showDifficultyScreen());

        // multiplayer
        home.getMultiplayerButton().setOnAction(e -> handleMultiplayerSelection());

        home.getSettingsButton().setOnAction(e -> {
            SettingsScreen s = new SettingsScreen(() -> showHomeScreen());
            scene.setRoot(s.getRoot());
        });

        home.getInstructionsButton().setOnAction(e -> {
            InstructionsScreen instr = new InstructionsScreen(() -> showHomeScreen());
            scene.setRoot(instr.getRoot());
        });

        scene.setRoot(home.getRoot());
    }

    private void handleMultiplayerSelection() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("Host", "Host", "Join");
        dialog.setTitle("Multiplayer");
        dialog.setHeaderText("Choose how you want to play:");
        dialog.setContentText("Mode:");
        dialog.initOwner(primaryStage);

        // dialog styling
        var css = getClass().getResource("/typeshi/styles.css");
        try {
            if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}
        dialog.getDialogPane().setStyle("-fx-background-color: linear-gradient(to bottom, #2b2b2b, #1f1f1f); -fx-text-fill: white;");

        // header label dark
        dialog.setOnShown(e -> {
            var headerLabel = dialog.getDialogPane().lookup(".header-panel .label");
            if (headerLabel != null) headerLabel.setStyle("-fx-text-fill: black;");
        });

        dialog.showAndWait().ifPresent(choice -> {
            // --- Choose difficulty + seconds ONLY if host ---
            final int[] chosenSeconds = {20};
            final int[] chosenAIDifficulty = {5}; // your AI difficulty scale (1..9 etc)
            final int[] chosenMode = {1};         // 1=Easy,2=Medium,3=Hard (fading mode)
            final int[] chosenRounds = {1};

            if ("Host".equals(choice)) {
                ChoiceDialog<String> diffDialog = new ChoiceDialog<>("Easy", "Easy", "Medium", "Hard");
                diffDialog.setTitle("Host Settings");
                diffDialog.setHeaderText("Select difficulty for BOTH players:");
                diffDialog.setContentText("Difficulty:");
                diffDialog.initOwner(primaryStage);
                try { if (css != null) diffDialog.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}
                diffDialog.getDialogPane().setStyle("-fx-background-color: linear-gradient(to bottom, #2b2b2b, #1f1f1f); -fx-text-fill: white;");

                diffDialog.setOnShown(ev -> {
                    var lbl = diffDialog.getDialogPane().lookup(".header-panel .label");
                    if (lbl != null) lbl.setStyle("-fx-text-fill: black;");
                });

                diffDialog.showAndWait().ifPresent(diff -> {
                    if ("Easy".equals(diff)) {
                        chosenMode[0] = 1;
                        chosenAIDifficulty[0] = 1;
                    } else if ("Medium".equals(diff)) {
                        chosenMode[0] = 2;
                        chosenAIDifficulty[0] = 6;
                    } else {
                        chosenMode[0] = 3;
                        chosenAIDifficulty[0] = 9;
                    }
                });

                TextInputDialog secDialog = new TextInputDialog("20");
                secDialog.setTitle("Host Settings");
                secDialog.setHeaderText("Round duration (seconds):");
                secDialog.setContentText("Seconds:");
                secDialog.initOwner(primaryStage);
                try { if (css != null) secDialog.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}
                secDialog.getDialogPane().setStyle("-fx-background-color: linear-gradient(to bottom, #2b2b2b, #1f1f1f); -fx-text-fill: white;");

                secDialog.setOnShown(ev -> {
                    var lbl = secDialog.getDialogPane().lookup(".header-panel .label");
                    if (lbl != null) lbl.setStyle("-fx-text-fill: black;");
                });
                secDialog.showAndWait().ifPresent(s -> {
                    try {
                        int v = Integer.parseInt(s.trim());
                        chosenSeconds[0] = Math.max(5, v);
                    } catch (Exception ignored) {
                        chosenSeconds[0] = 20;
                    }
                });

                // Ask host how many rounds
                TextInputDialog roundsDialog = new TextInputDialog("1");
                roundsDialog.setTitle("Host Settings");
                roundsDialog.setHeaderText("Number of rounds (best of):");
                roundsDialog.setContentText("Rounds:");
                roundsDialog.initOwner(primaryStage);
                try { if (css != null) roundsDialog.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}
                roundsDialog.getDialogPane().setStyle("-fx-background-color: linear-gradient(to bottom, #2b2b2b, #1f1f1f); -fx-text-fill: white;");

                roundsDialog.setOnShown(ev -> {
                    var lbl = roundsDialog.getDialogPane().lookup(".header-panel .label");
                    if (lbl != null) lbl.setStyle("-fx-text-fill: black;");
                });
                roundsDialog.showAndWait().ifPresent(r -> {
                    try { chosenRounds[0] = Math.max(1, Integer.parseInt(r.trim())); } catch (Exception ignored) { chosenRounds[0] = 1; }
                });
            }

            // create game screen
            UIComponents ui = new UIComponents();
            GameController controller = new GameController(ui);
            ui.setController(controller);
            controller.setOnReturnToMenu(this::showHomeScreen);

            scene.setRoot(ui.rootPane);

            if ("Host".equals(choice)) {
                // host settings applied
                controller.setMode(chosenMode[0]);
                controller.setTotalRounds(chosenRounds[0]);
                controller.startHostMultiplayer(5000, chosenSeconds[0], chosenAIDifficulty[0], chosenMode[0]);
            } else {
                // joiner setup
                controller.setMode(1);
                controller.setTotalRounds(1);

                TextInputDialog ipInput = new TextInputDialog("127.0.0.1");
                ipInput.setTitle("Join Game");
                ipInput.setHeaderText("Enter Host IP");
                ipInput.setContentText("IP Address:");
                ipInput.initOwner(primaryStage);
                try { if (css != null) ipInput.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}
                ipInput.getDialogPane().setStyle("-fx-background-color: linear-gradient(to bottom, #2b2b2b, #1f1f1f); -fx-text-fill: white;");

                ipInput.setOnShown(ev -> {
                    var lbl = ipInput.getDialogPane().lookup(".header-panel .label");
                    if (lbl != null) lbl.setStyle("-fx-text-fill: black;");
                });
                ipInput.showAndWait().ifPresent(ip -> controller.startJoinMultiplayer(ip.trim(), 5000));
            }
        });
    }

    // difficulty screen
    private void showDifficultyScreen() {
        DifficultyScreen diff = new DifficultyScreen();

        diff.getBackButton().setOnAction(e -> showHomeScreen());

        diff.getEasyButton().setOnAction(e -> showRound(1, 1, 1, diff.getRoundsSpinner().getValue()));
        diff.getMediumButton().setOnAction(e -> showRound(1, 2, 6, diff.getRoundsSpinner().getValue()));
        diff.getHardButton().setOnAction(e -> showRound(1, 3, 9, diff.getRoundsSpinner().getValue()));

        scene.setRoot(diff.getRoot());
    }

    // round popup
    private void showRound(int round, int mode, int aiDifficulty, int totalRounds) {
        Alert roundPopup = new Alert(Alert.AlertType.INFORMATION);
        roundPopup.setTitle("Round " + round);
        roundPopup.setHeaderText("Get Ready for Round " + round + "!");
        roundPopup.setContentText("The typing challenge will begin shortly.");

        // dialog styling
        var css = getClass().getResource("/typeshi/styles.css");
        try { if (css != null) roundPopup.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}
        roundPopup.getDialogPane().setStyle("-fx-background-color: linear-gradient(to bottom, #2b2b2b, #1f1f1f); -fx-text-fill: white;");

        // ensure header/content dark
        roundPopup.setOnShown(e -> {
            var pane = roundPopup.getDialogPane();

            var hdrNode = pane.lookup(".header-panel .label");
            if (hdrNode instanceof Label) {
                ((Label) hdrNode).setStyle("-fx-text-fill: black; -fx-font-family: Consolas; -fx-font-size: 18;");
            }

            var contentNode = pane.lookup(".content .label");
            if (contentNode instanceof Label) {
                ((Label) contentNode).setStyle("-fx-text-fill: black; -fx-font-family: Consolas; -fx-font-size: 14;");
            }
        });
        roundPopup.showAndWait();

        UIComponents ui = new UIComponents();
        GameController controller = new GameController(ui);
        controller.setMode(mode);
        controller.setTotalRounds(totalRounds);
        ui.setController(controller);

        controller.setOnReturnToMenu(this::showHomeScreen);

        scene.setRoot(ui.rootPane);

        controller.startGameWithCountdown(60, aiDifficulty);
    }

    public static void main(String[] args) {
        launch();
    }
}
