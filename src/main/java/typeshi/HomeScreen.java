package typeshi;

import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;

public class HomeScreen {

    private StackPane root;
    private Button playButton;
    private Button multiplayerButton;
    private Button settingsButton;
    private Button exitButton;
    private Button instructionsButton;

    public HomeScreen() {
        root = new StackPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #141E30, #243B55);");

        // Prevent Enter key from activating buttons unintentionally on this screen
        root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) e.consume();
        });

        VBox menuBox = new VBox(20);
        menuBox.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.control.Label title = new javafx.scene.control.Label("TypeShi");
        title.setFont(Font.font("Consolas", 60));
        title.setTextFill(Color.LIMEGREEN);

        DropShadow shadow = new DropShadow();
        shadow.setOffsetX(3);
        shadow.setOffsetY(3);
        shadow.setColor(Color.BLACK);
        title.setEffect(shadow);

        playButton = createButton("Play vs Computer");

        multiplayerButton = createButton("Multiplayer");

        instructionsButton = createButton("Instructions");
        instructionsButton.setOnAction(e -> {
            // Wired in Main
        });

        settingsButton = createButton("Settings");

        exitButton = createButton("Exit");
        exitButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff416c, #ff4b2b);"
                        + " -fx-text-fill: white;"
                        + " -fx-background-radius: 20;"
                        + " -fx-padding: 10 40 10 40;"
        );
        exitButton.setOnMouseEntered(e -> exitButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff4b2b, #ff416c);"
                        + " -fx-text-fill: white;"
                        + " -fx-background-radius: 20;"
                        + " -fx-padding: 10 40 10 40;"
        ));
        exitButton.setOnMouseExited(e -> exitButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff416c, #ff4b2b);"
                        + " -fx-text-fill: white;"
                        + " -fx-background-radius: 20;"
                        + " -fx-padding: 10 40 10 40;"
        ));
        exitButton.setOnAction(e -> System.exit(0));

        menuBox.getChildren().addAll(
                title,
                playButton,
                multiplayerButton,
                instructionsButton,
                settingsButton,
                exitButton
        );

        root.getChildren().add(menuBox);
    }

    private Button createButton(String text) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Consolas", 24));
        btn.setDefaultButton(false);
        btn.setStyle(
                "-fx-background-color: linear-gradient(to right, #00b09b, #96c93d);"
                        + " -fx-text-fill: white;"
                        + " -fx-background-radius: 20;"
                        + " -fx-padding: 10 40 10 40;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: linear-gradient(to right, #96c93d, #00b09b);"
                        + " -fx-text-fill: white;"
                        + " -fx-background-radius: 20;"
                        + " -fx-padding: 10 40 10 40;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: linear-gradient(to right, #00b09b, #96c93d);"
                        + " -fx-text-fill: white;"
                        + " -fx-background-radius: 20;"
                        + " -fx-padding: 10 40 10 40;"
        ));
        return btn;
    }

    public StackPane getRoot() { return root; }
    public Button getPlayButton() { return playButton; }
    public Button getMultiplayerButton() { return multiplayerButton; }
    public Button getSettingsButton() { return settingsButton; }
    public Button getExitButton() { return exitButton; }
    public Button getInstructionsButton() { return instructionsButton; }
}
