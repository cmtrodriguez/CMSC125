package typeshi;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.text.Font;

import java.util.Optional;

public class Main extends Application {

    private Stage primaryStage;
    private Scene scene;       // one shared scene

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        showLoadingScreen();
    }

    /* =========================================================
       Helper: style dialog buttons reliably for every dialog instance
       (kept minimal: only applies CSS class for cancel + lets FX size buttons)
       ========================================================= */
    private void styleDialogCancelLikeOk(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();

        Button cancelBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);

        if (cancelBtn != null) {
            if (!cancelBtn.getStyleClass().contains("cancel-button")) {
                cancelBtn.getStyleClass().add("cancel-button");
            }
            // do not set sizes here — let JavaFX size the buttons natively
        }

        if (okBtn != null) {
            // keep default sizing for OK (do not override)
        }
    }
    /* ========================================================= */

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

        var css = getClass().getResource("/typeshi/styles.css");
        try { if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}

        dialog.setOnShown(e -> styleDialogCancelLikeOk(dialog));

        // show and handle cancellation: if user cancels, return to home
        Optional<String> choiceOpt = dialog.showAndWait();
        if (!choiceOpt.isPresent()) {
            showHomeScreen();
            return;
        }
        String choice = choiceOpt.get();

        final int[] chosenSeconds = {20};
        final int[] chosenAIDifficulty = {5};
        final int[] chosenMode = {1};
        final int[] chosenRounds = {1};

        if ("Host".equals(choice)) {
            ChoiceDialog<String> diffDialog = new ChoiceDialog<>("Easy", "Easy", "Medium", "Hard");
            diffDialog.setTitle("Host Settings");
            diffDialog.setHeaderText("Select difficulty for BOTH players:");
            diffDialog.setContentText("Difficulty:");
            diffDialog.initOwner(primaryStage);
            try { if (css != null) diffDialog.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}

            diffDialog.setOnShown(ev -> styleDialogCancelLikeOk(diffDialog));

            Optional<String> diffOpt = diffDialog.showAndWait();
            if (!diffOpt.isPresent()) { showHomeScreen(); return; }
            String diff = diffOpt.get();

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

            TextInputDialog secDialog = new TextInputDialog("20");
            secDialog.setTitle("Host Settings");
            secDialog.setHeaderText("Round duration (seconds):");
            secDialog.setContentText("Seconds:");
            secDialog.initOwner(primaryStage);
            try { if (css != null) secDialog.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}

            secDialog.setOnShown(ev -> styleDialogCancelLikeOk(secDialog));

            Optional<String> sOpt = secDialog.showAndWait();
            if (!sOpt.isPresent()) { showHomeScreen(); return; }
            String s = sOpt.get();
            try {
                int v = Integer.parseInt(s.trim());
                chosenSeconds[0] = Math.max(5, v);
            } catch (Exception ignored) {
                chosenSeconds[0] = 20;
            }

            TextInputDialog roundsDialog = new TextInputDialog("1");
            roundsDialog.setTitle("Host Settings");
            roundsDialog.setHeaderText("Number of rounds (best of):");
            roundsDialog.setContentText("Rounds:");
            roundsDialog.initOwner(primaryStage);
            try { if (css != null) roundsDialog.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}

            roundsDialog.setOnShown(ev -> styleDialogCancelLikeOk(roundsDialog));

            Optional<String> rOpt = roundsDialog.showAndWait();
            if (!rOpt.isPresent()) { showHomeScreen(); return; }
            String r = rOpt.get();
            try { chosenRounds[0] = Math.max(1, Integer.parseInt(r.trim())); } catch (Exception ignored) { chosenRounds[0] = 1; }
        }

        // create game screen
        UIComponents ui = new UIComponents();
        GameController controller = new GameController(ui);
        ui.setController(controller);
        controller.setOnReturnToMenu(this::showHomeScreen);

        scene.setRoot(ui.rootPane);

        if ("Host".equals(choice)) {
            controller.setMode(chosenMode[0]);
            controller.setTotalRounds(chosenRounds[0]);
            controller.startHostMultiplayer(5000, chosenSeconds[0], chosenAIDifficulty[0], chosenMode[0]);
        } else {
            controller.setMode(1);
            controller.setTotalRounds(1);

            TextInputDialog ipInput = new TextInputDialog("127.0.0.1");
            ipInput.setTitle("Join Game");
            ipInput.setHeaderText("Enter Host IP");
            ipInput.setContentText("IP Address:");
            ipInput.initOwner(primaryStage);
            try { if (css != null) ipInput.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}

            ipInput.setOnShown(ev -> styleDialogCancelLikeOk(ipInput));

            Optional<String> ipOpt = ipInput.showAndWait();
            if (!ipOpt.isPresent()) {
                // user cancelled IP input -> go back home
                showHomeScreen();
                return;
            }
            controller.startJoinMultiplayer(ipOpt.get().trim(), 5000);
        }
    }

    private void showDifficultyScreen() {
        DifficultyScreen diff = new DifficultyScreen();

        diff.getBackButton().setOnAction(e -> showHomeScreen());
        diff.getEasyButton().setOnAction(e -> showRound(1, 1, 1, diff.getRoundsSpinner().getValue()));
        diff.getMediumButton().setOnAction(e -> showRound(1, 2, 6, diff.getRoundsSpinner().getValue()));
        diff.getHardButton().setOnAction(e -> showRound(1, 3, 9, diff.getRoundsSpinner().getValue()));

        scene.setRoot(diff.getRoot());
    }

    private void showRound(int round, int mode, int aiDifficulty, int totalRounds) {
        Alert roundPopup = new Alert(Alert.AlertType.INFORMATION);
        roundPopup.setTitle("Round " + round);
        roundPopup.setHeaderText("Get Ready for Round " + round + "!");
        roundPopup.setContentText("The typing challenge will begin shortly.");

        var css = getClass().getResource("/typeshi/styles.css");
        try { if (css != null) roundPopup.getDialogPane().getStylesheets().add(css.toExternalForm()); } catch (Exception ignored) {}

        roundPopup.setOnShown(e -> styleDialogCancelLikeOk(roundPopup));
        roundPopup.showAndWait();

        UIComponents ui = new UIComponents();
        GameController controller = new GameController(ui);
        controller.setMode(mode);
        controller.setTotalRounds(totalRounds);
        ui.setController(controller);

        controller.setOnReturnToMenu(this::showHomeScreen);

        scene.setRoot(ui.rootPane);

        int duration = (mode == 1) ? 120 : (mode == 2 ? 60 : 30);
        controller.startGameWithCountdown(duration, aiDifficulty);
    }

    public static void main(String[] args) {
        launch();
    }
}
