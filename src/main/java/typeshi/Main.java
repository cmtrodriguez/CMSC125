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

        // NEW: Multiplayer
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

        dialog.showAndWait().ifPresent(choice -> {

            // --- Choose difficulty + seconds ONLY if host ---
            final int[] chosenSeconds = {20};
            final int[] chosenAIDifficulty = {5}; // your AI difficulty scale (1..9 etc)
            final int[] chosenMode = {1};         // 1=Easy,2=Medium,3=Hard (fading mode)

            if ("Host".equals(choice)) {
                ChoiceDialog<String> diffDialog = new ChoiceDialog<>("Easy", "Easy", "Medium", "Hard");
                diffDialog.setTitle("Host Settings");
                diffDialog.setHeaderText("Select difficulty for BOTH players:");
                diffDialog.setContentText("Difficulty:");
                diffDialog.initOwner(primaryStage);

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

                secDialog.showAndWait().ifPresent(s -> {
                    try {
                        int v = Integer.parseInt(s.trim());
                        chosenSeconds[0] = Math.max(5, v);
                    } catch (Exception ignored) {
                        chosenSeconds[0] = 20;
                    }
                });
            }

            // --- Create game screen ---
            UIComponents ui = new UIComponents();
            GameController controller = new GameController(ui);
            ui.setController(controller);
            controller.setOnReturnToMenu(this::showHomeScreen);

            scene.setRoot(ui.rootPane);

            if ("Host".equals(choice)) {
                // Host sets the mode (fading logic) + aiDifficulty variable used as "difficulty"
                controller.setMode(chosenMode[0]);
                controller.startHostMultiplayer(5000, chosenSeconds[0], chosenAIDifficulty[0], chosenMode[0]);
            } else {
                // Joiner does NOT choose difficulty/time; it will receive CFG from host
                controller.setMode(1);

                TextInputDialog ipInput = new TextInputDialog("127.0.0.1");
                ipInput.setTitle("Join Game");
                ipInput.setHeaderText("Enter Host IP");
                ipInput.setContentText("IP Address:");
                ipInput.initOwner(primaryStage);

                ipInput.showAndWait().ifPresent(ip -> controller.startJoinMultiplayer(ip.trim(), 5000));
            }
        });
    }

    private void showDifficultyScreen() {
        DifficultyScreen diff = new DifficultyScreen();

        diff.getBackButton().setOnAction(e -> showHomeScreen());

        diff.getEasyButton().setOnAction(e -> showRound(1, 1, 1));
        diff.getMediumButton().setOnAction(e -> showRound(1, 2, 6));
        diff.getHardButton().setOnAction(e -> showRound(1, 3, 9));

        scene.setRoot(diff.getRoot());
    }

    private void showRound(int round, int mode, int aiDifficulty) {
        Alert roundPopup = new Alert(Alert.AlertType.INFORMATION);
        roundPopup.setTitle("Round " + round);
        roundPopup.setHeaderText("Get Ready for Round " + round + "!");
        roundPopup.setContentText("The typing challenge will begin shortly.");
        roundPopup.showAndWait();

        UIComponents ui = new UIComponents();
        GameController controller = new GameController(ui);
        controller.setMode(mode);
        ui.setController(controller);

        controller.setOnReturnToMenu(this::showHomeScreen);

        scene.setRoot(ui.rootPane);

        controller.startGameWithCountdown(20, aiDifficulty);
    }

    public static void main(String[] args) {
        launch();
    }
}
