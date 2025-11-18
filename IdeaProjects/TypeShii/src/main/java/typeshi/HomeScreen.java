package typeshi;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;

public class HomeScreen {

    private StackPane root;
    private Button playButton;
    private Button settingsButton;
    private Button exitButton;

    public HomeScreen() {
        root = new StackPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #141E30, #243B55);");

        VBox menuBox = new VBox(20);
        menuBox.setAlignment(Pos.CENTER);

        // Title
        javafx.scene.control.Label title = new javafx.scene.control.Label("TypeShi");
        title.setFont(Font.font("Consolas", 60));
        title.setTextFill(Color.LIMEGREEN);
        DropShadow shadow = new DropShadow();
        shadow.setOffsetX(3);
        shadow.setOffsetY(3);
        shadow.setColor(Color.BLACK);
        title.setEffect(shadow);

        // Play button
        playButton = createButton("Play vs Computer");

        // Settings button
        settingsButton = createButton("Settings");

        // Exit button
        exitButton = createButton("Exit");
        exitButton.setOnAction(e -> System.exit(0));

        menuBox.getChildren().addAll(title, playButton, settingsButton, exitButton);
        root.getChildren().add(menuBox);
    }

    private Button createButton(String text) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Consolas", 24));
        btn.setStyle(
                "-fx-background-color: linear-gradient(to right, #00b09b, #96c93d);" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 20;" +
                        "-fx-padding: 10 40 10 40;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: linear-gradient(to right, #96c93d, #00b09b);" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 20;" +
                        "-fx-padding: 10 40 10 40;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: linear-gradient(to right, #00b09b, #96c93d);" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 20;" +
                        "-fx-padding: 10 40 10 40;"
        ));
        return btn;
    }

    public StackPane getRoot() {
        return root;
    }

    public Button getPlayButton() {
        return playButton;
    }

    public Button getSettingsButton() {
        return settingsButton;
    }

    public Button getExitButton() {
        return exitButton;
    }
}
