package typeshi;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class UIComponents {

    public BorderPane rootPane;

    public VBox playerArea;
    public VBox computerArea;

    public TextFlow targetTextFlow;       // Player target passage
    public TextFlow computerTextFlow;     // Computer typing display
    public TextField inputField;          // Player input
    public ProgressBar playerProgress;
    public ProgressBar computerProgress;
    public Label playerScoreLabel;
    public Label computerScoreLabel;
    public Label timerLabel;
    public VBox logBox;

    private GameController controller;

    public UIComponents() {
        rootPane = new BorderPane();
        rootPane.setPadding(new Insets(15));
        rootPane.setStyle("-fx-background-color: linear-gradient(to bottom, #1f1c2c, #928dab);");

        setupTop();
        setupCenter();
        setupBottom();
    }

    private void setupTop() {
        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(10));

        Label title = new Label("TypeShi");
        title.setFont(Font.font("Consolas", 40));
        title.setTextFill(Color.LIMEGREEN);

        timerLabel = new Label("00:00");
        timerLabel.setFont(Font.font("Consolas", 24));
        timerLabel.setTextFill(Color.WHITE);

        topBar.getChildren().addAll(title, timerLabel);
        rootPane.setTop(topBar);
    }

    private void setupCenter() {
        HBox center = new HBox(25);
        center.setPadding(new Insets(10));

        // ===== PLAYER AREA =====
        playerArea = new VBox(15);
        playerArea.setAlignment(Pos.TOP_CENTER);

        Label playerLabel = new Label("You");
        playerLabel.setFont(Font.font("Consolas", 20));
        playerLabel.setTextFill(Color.LIMEGREEN);

        targetTextFlow = new TextFlow();
        targetTextFlow.setPrefHeight(200);
        targetTextFlow.setMaxWidth(600);
        targetTextFlow.setStyle("-fx-background-color: #1b1b2f; -fx-padding: 10; -fx-background-radius: 10;");

        inputField = new TextField();
        inputField.setPromptText("Type here...");
        inputField.setFont(Font.font("Consolas", 18));
        inputField.setStyle("-fx-background-radius: 10; -fx-padding: 8;");

        playerProgress = new ProgressBar(0);
        playerProgress.setPrefWidth(600);

        playerScoreLabel = new Label("Score: 0 | Errors: 0");
        playerScoreLabel.setFont(Font.font("Consolas", 16));
        playerScoreLabel.setTextFill(Color.WHITE);

        playerArea.getChildren().addAll(playerLabel, targetTextFlow, inputField, playerProgress, playerScoreLabel);

        // ===== COMPUTER AREA =====
        computerArea = new VBox(15);
        computerArea.setAlignment(Pos.TOP_CENTER);

        Label compLabel = new Label("Computer");
        compLabel.setFont(Font.font("Consolas", 20));
        compLabel.setTextFill(Color.RED);

        computerTextFlow = new TextFlow();
        computerTextFlow.setPrefHeight(200);
        computerTextFlow.setMaxWidth(600);
        computerTextFlow.setStyle("-fx-background-color: #2b1b1b; -fx-padding: 10; -fx-background-radius: 10;");

        computerProgress = new ProgressBar(0);
        computerProgress.setPrefWidth(600);

        computerScoreLabel = new Label("Score: 0 | Errors: 0");
        computerScoreLabel.setFont(Font.font("Consolas", 16));
        computerScoreLabel.setTextFill(Color.WHITE);

        logBox = new VBox(5);
        logBox.setPrefHeight(100);
        ScrollPane scrollPane = new ScrollPane(logBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1b1b2f; -fx-background-radius: 10;");

        computerArea.getChildren().addAll(compLabel, computerTextFlow, computerProgress, computerScoreLabel, scrollPane);

        center.getChildren().addAll(playerArea, computerArea);
        rootPane.setCenter(center);
    }

    private void setupBottom() {
        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(10));
        Label instructions = new Label("Type the text above. Correct letters = green, wrong letters = red. Watch the computer type!");
        instructions.setFont(Font.font("Consolas", 14));
        instructions.setTextFill(Color.WHITE);
        bottom.getChildren().add(instructions);
        rootPane.setBottom(bottom);
    }

    public void setController(GameController controller) {
        this.controller = controller;
    }
}
