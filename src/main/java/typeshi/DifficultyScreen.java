package typeshi;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class DifficultyScreen {

    private StackPane root;
    private Button easyButton;
    private Button mediumButton;
    private Button hardButton;
    private Button backButton;

    private final Spinner<Integer> roundsSpinner;

    public DifficultyScreen() {
        root = new StackPane();
        // Keep the same background as HomeScreen
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #141E30, #243B55);");

        VBox menuBox = new VBox(20);
        menuBox.setAlignment(Pos.CENTER);

        // Panel title (consistent with app)
        Label title = new Label("Select Difficulty");
        title.getStyleClass().add("panel-title");

        // Optional small subtitle to match branding
        Label subtitle = new Label("TypeShi");
        subtitle.setFont(Font.font("Consolas", 20));
        subtitle.setTextFill(Color.web("#d3d3d3"));

        // Create buttons (use CSS classes rather than inline styles)
        easyButton = createButton("Easy");
        mediumButton = createButton("Medium");
        hardButton = createButton("Hard");
        backButton = createButton("Back");
        backButton.getStyleClass().add("danger");

        // Unify button widths
        double btnWidth = 160;
        easyButton.setPrefWidth(btnWidth);
        mediumButton.setPrefWidth(btnWidth);
        hardButton.setPrefWidth(btnWidth);

        // Instruction labels
        Label easyInstr = new Label("Easy: immediate red feedback; no auto-backspace.");
        easyInstr.getStyleClass().add("subtle");
        easyInstr.setWrapText(true);
        easyInstr.setMaxWidth(btnWidth);
        easyInstr.setAlignment(Pos.CENTER);

        Label mediumInstr = new Label("Medium: delayed red feedback; correct words auto-backspace.");
        mediumInstr.getStyleClass().add("subtle");
        mediumInstr.setWrapText(true);
        mediumInstr.setMaxWidth(btnWidth);
        mediumInstr.setAlignment(Pos.CENTER);

        Label hardInstr = new Label("Hard: delayed feedback + auto-backspace + fading words; screen shakes on errors.");
        hardInstr.getStyleClass().add("subtle");
        hardInstr.setWrapText(true);
        hardInstr.setMaxWidth(btnWidth);
        hardInstr.setAlignment(Pos.CENTER);

        // Each button + its instruction in a card-like VBox
        VBox easyBox = new VBox(8, easyButton, easyInstr);
        easyBox.setAlignment(Pos.CENTER);
        easyBox.getStyleClass().addAll("difficulty-card", "easy");
        easyBox.setPrefWidth(200);

        VBox mediumBox = new VBox(8, mediumButton, mediumInstr);
        mediumBox.setAlignment(Pos.CENTER);
        mediumBox.getStyleClass().addAll("difficulty-card", "medium");
        mediumBox.setPrefWidth(200);

        VBox hardBox = new VBox(8, hardButton, hardInstr);
        hardBox.setAlignment(Pos.CENTER);
        hardBox.getStyleClass().addAll("difficulty-card", "hard");
        hardBox.setPrefWidth(200);

        HBox buttonsRow = new HBox(24);
        buttonsRow.setAlignment(Pos.CENTER);
        buttonsRow.getChildren().addAll(easyBox, mediumBox, hardBox);

        // Rounds spinner
        roundsSpinner = new Spinner<>();
        roundsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 1));
        roundsSpinner.setEditable(true);
        roundsSpinner.getStyleClass().add("difficulty-spinner");

        HBox roundsBox = new HBox(new Label("Rounds:"), roundsSpinner);
        roundsBox.setAlignment(Pos.CENTER);
        roundsBox.setSpacing(10);

        menuBox.getChildren().addAll(title, subtitle, buttonsRow, roundsBox, backButton);
        root.getChildren().add(menuBox);
    }

    // Use CSS classes so appearance is consistent with HomeScreen
    private Button createButton(String text) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Consolas", 24));
        btn.setDefaultButton(false);
        // Rely on CSS .button and .button.primary rules
        btn.getStyleClass().add("primary");
        return btn;
    }

    public StackPane getRoot() { return root; }
    public Button getEasyButton() { return easyButton; }
    public Button getMediumButton() { return mediumButton; }
    public Button getHardButton() { return hardButton; }
    public Button getBackButton() { return backButton; }
    public Spinner<Integer> getRoundsSpinner() { return roundsSpinner; }
}