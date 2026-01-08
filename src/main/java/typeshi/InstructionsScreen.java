package typeshi;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Simple instructions/help screen.
 */
public class InstructionsScreen {

    private final StackPane root = new StackPane();

    public InstructionsScreen(Runnable onBack) {
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #141E30, #243B55);");

        VBox box = new VBox(16);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(30));

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setMaxWidth(720);

        Label title = new Label("Instructions");
        title.setStyle("-fx-text-fill: white; -fx-font-family: Consolas; -fx-font-size: 22;");

        Text body = new Text(
                "Overview:\n" +
                "- Type the given passage as quickly and accurately as you can.\n" +
                "- Score is awarded for each correct character; errors are counted separately.\n\n" +
                "Per-difficulty features:\n" +
                "Easy: Immediate feedback (wrong chars turn red) and no auto-backspace.\n" +
                "Medium: Mistakes are delayed (briefly shown white then turn red) and correctly typed words auto-backspace once.\n" +
                "Hard: Words progressively fade (hidden) and mistakes cause a brief screen shake; delayed feedback and auto-backspace apply.\n\n" +
                "Multiplayer:\n" +
                "- Host chooses passage, round length, difficulty, and rounds. Joiner syncs to host.\n\n" +
                "Rounds & Pause:\n" +
                "- You can set number of rounds; between rounds a short 'Round X Done' message appears, then a 3-2-1 countdown.\n" +
                "- Pause works at any time (including during countdowns)."
        );
        body.setFill(javafx.scene.paint.Color.WHITE);
        body.setFont(Font.font("Consolas", 13));
        body.setWrappingWidth(720);

        Button back = new Button("Back");
        back.getStyleClass().addAll("button", "primary");
        back.setOnAction(e -> {
            if (onBack != null) onBack.run();
        });

        card.getChildren().addAll(title, body, back);
        box.getChildren().add(card);
        root.getChildren().add(box);
    }

    public Parent getRoot() { return root; }
}