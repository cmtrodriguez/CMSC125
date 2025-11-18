package typeshi;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class Main extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        showLoadingScreen();
    }

    private void showLoadingScreen() {
        LoadingScreen loading = new LoadingScreen();

        Scene scene = new Scene(loading.getRoot(), 800, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("TypeShi");
        primaryStage.show();

        // Auto-load 3 seconds then show home
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> showHomeScreen());
        pause.play();
    }

    private void showHomeScreen() {
        HomeScreen home = new HomeScreen();

        // Setup buttons
        home.getPlayButton().setOnAction(e -> showRound(1));

        Scene scene = new Scene(home.getRoot(), 800, 500);
        primaryStage.setScene(scene);
    }

    private void showRound(int round) {
        Alert roundPopup = new Alert(Alert.AlertType.INFORMATION);
        roundPopup.setTitle("Round " + round);
        roundPopup.setHeaderText("Get Ready for Round " + round + "!");
        roundPopup.setContentText("The typing challenge will begin shortly.");
        roundPopup.showAndWait();

        // Start the actual game UI
        UIComponents ui = new UIComponents();
        GameController controller = new GameController(ui);
        ui.setController(controller);

        Scene scene = new Scene(ui.rootPane, 1000, 500);
        primaryStage.setScene(scene);
    }

    public static void main(String[] args) {
        launch();
    }
}
