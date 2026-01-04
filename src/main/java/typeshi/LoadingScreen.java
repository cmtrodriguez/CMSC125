package typeshi;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class LoadingScreen {

    private final StackPane root;

    public LoadingScreen() {
        root = new StackPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #0f2027, #203a43, #2c5364);");

        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);

        Label title = new Label("TypeShi");
        title.setFont(Font.font("Consolas", 48));
        title.setTextFill(Color.LIMEGREEN);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(80, 80);
        progress.setStyle("-fx-progress-color: limegreen;");

        Label loadingText = new Label("Loading...");
        loadingText.setFont(Font.font("Consolas", 20));
        loadingText.setTextFill(Color.WHITE);

        box.getChildren().addAll(title, progress, loadingText);
        root.getChildren().add(box);
    }

    public StackPane getRoot() {
        return root;
    }
}
