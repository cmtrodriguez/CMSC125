package typeshi;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;

public class DifficultyScreen {

    private StackPane root;
    private Button easyButton;
    private Button mediumButton;
    private Button hardButton;
    private Button backButton;

    public DifficultyScreen() {
        root = new StackPane();
        // Same background as HomeScreen
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #141E30, #243B55);");

        VBox menuBox = new VBox(20);
        menuBox.setAlignment(Pos.CENTER);

        // Title styled like HomeScreen
        javafx.scene.control.Label title = new javafx.scene.control.Label("TypeShi");
        title.setFont(Font.font("Consolas", 60));
        title.setTextFill(Color.LIMEGREEN);

        DropShadow shadow = new DropShadow();
        shadow.setOffsetX(3);
        shadow.setOffsetY(3);
        shadow.setColor(Color.BLACK);
        title.setEffect(shadow);

        // Difficulty buttons using same style as HomeScreen buttons
        easyButton = createButton("Easy");
        mediumButton = createButton("Medium");
        hardButton = createButton("Hard");
        backButton = createButton("Back");
        // make back button a danger/red style to differentiate it
        backButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff416c, #ff4b2b);" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 20;" +
                        "-fx-padding: 10 40 10 40;"
        );
        backButton.setOnMouseEntered(e -> backButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff4b2b, #ff416c);" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 20;" +
                        "-fx-padding: 10 40 10 40;"
        ));
        backButton.setOnMouseExited(e -> backButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #ff416c, #ff4b2b);" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 20;" +
                        "-fx-padding: 10 40 10 40;"
        ));

        menuBox.getChildren().addAll(title, easyButton, mediumButton, hardButton, backButton);
        root.getChildren().add(menuBox);
    }

    // Same styling as HomeScreen.createButton
    private Button createButton(String text) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Consolas", 24));
        // Prevent this button from being treated as the scene's default button
        btn.setDefaultButton(false);
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

    public StackPane getRoot() { return root; }
    public Button getEasyButton() { return easyButton; }
    public Button getMediumButton() { return mediumButton; }
    public Button getHardButton() { return hardButton; }
    public Button getBackButton() { return backButton; }
}
