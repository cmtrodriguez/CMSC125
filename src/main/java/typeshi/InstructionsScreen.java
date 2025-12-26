package typeshi;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

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

        Label title = new Label("How to Play");
        title.getStyleClass().add("panel-title");

        Label p1 = new Label("• Type the text shown in the left pane as accurately and quickly as possible.");
        p1.getStyleClass().addAll("subtle");
        p1.setWrapText(true);
        Label p2 = new Label("• Correct letters turn green; incorrect letters turn red.");
        p2.getStyleClass().addAll("subtle");
        p2.setWrapText(true);
        Label p3 = new Label("• The computer opponent types in real time; aim to type more accurate characters than the computer.");
        p3.getStyleClass().addAll("subtle");
        p3.setWrapText(true);
        Label p4 = new Label("• Use the settings to control volume and default difficulty.");
        p4.getStyleClass().addAll("subtle");
        p4.setWrapText(true);
        Label p5 = new Label("• Press Back to return to the main menu.");
        p5.getStyleClass().addAll("subtle");

        Button back = new Button("Back");
        back.setDefaultButton(false);
        back.getStyleClass().addAll("button", "primary");
        back.setOnAction(e -> {
            if (onBack != null) onBack.run();
        });

        card.getChildren().addAll(title, p1, p2, p3, p4, p5, back);
        box.getChildren().add(card);
        root.getChildren().add(box);
    }

    public Parent getRoot() { return root; }
}