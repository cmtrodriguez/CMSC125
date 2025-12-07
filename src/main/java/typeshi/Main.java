package typeshi;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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

        // Create the scene once here
        scene = new Scene(loading.getRoot(), 800, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("TypeShi");
        primaryStage.show();

        // Auto-load 3 seconds then show home
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> showHomeScreen());
        pause.play();
    }

    // Home screen uses same scene, just swaps root
    private void showHomeScreen() {
        HomeScreen home = new HomeScreen();

        home.getPlayButton().setOnAction(e -> showDifficultyScreen());

        scene.setRoot(home.getRoot());   // no new Scene, fullscreen preserved
    }

    // Difficulty screen with same scene
    private void showDifficultyScreen() {
        DifficultyScreen diff = new DifficultyScreen();

        diff.getBackButton().setOnAction(e -> showHomeScreen());

        diff.getEasyButton().setOnAction(e -> showRound(1, 1, 1));   // mode 1, AI difficulty 3
        diff.getMediumButton().setOnAction(e -> showRound(1, 2, 6)); // mode 2, AI difficulty 6
        diff.getHardButton().setOnAction(e -> showRound(1, 3, 9));   // mode 3, AI difficulty 9

        scene.setRoot(diff.getRoot());   // no new Scene
    }

    // Start round with selected mode + AI difficulty
    private void showRound(int round, int mode, int aiDifficulty) {
        Alert roundPopup = new Alert(Alert.AlertType.INFORMATION);
        roundPopup.setTitle("Round " + round);
        roundPopup.setHeaderText("Get Ready for Round " + round + "!");
        roundPopup.setContentText("The typing challenge will begin shortly.");
        roundPopup.showAndWait();

        UIComponents ui = new UIComponents();
        GameController controller = new GameController(ui);
        controller.setMode(mode);           // 1=Easy, 2=Medium, 3=Hard
        ui.setController(controller);

        scene.setRoot(ui.rootPane);        // keep same Scene, size, fullscreen

        controller.startGameWithCountdown(20, aiDifficulty); // 60 seconds, adjust as you like
    }

    public static void main(String[] args) {
        launch();
    }
}
