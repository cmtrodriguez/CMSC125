package typeshi;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.control.Separator;
import javafx.scene.text.Font;

import java.util.prefs.Preferences;

/**
 * Simple settings screen for the game. Values are not yet persisted to disk.
 */
public class SettingsScreen {

    private final StackPane root = new StackPane();

    private ChoiceBox<String> defaultDifficultyChoice;
    private Button backButton;

    public SettingsScreen(Runnable onBack) {
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #141E30, #243B55);");

        VBox outer = new VBox();
        outer.setAlignment(Pos.CENTER);
        outer.setPadding(new Insets(30));

        VBox card = new VBox(12);
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("card");
        card.setMaxWidth(640);

        Label title = new Label("Settings");
        title.getStyleClass().add("panel-title");

        Label subtitle = new Label("Adjust audio levels, toggle music and effects, and choose your default difficulty.");
        subtitle.getStyleClass().addAll("subtle", "settings-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(520);

        Separator sep = new Separator();

        VBox content = new VBox(14);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(520);

        // Volume
        Label volumeLabel = new Label("Master Volume");
        volumeLabel.getStyleClass().add("subtle");
        Slider volumeSlider = new Slider(0, 100, 70);
        volumeSlider.setShowTickLabels(true);
        volumeSlider.setShowTickMarks(true);
        volumeSlider.setBlockIncrement(5);
        volumeSlider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(volumeSlider, Priority.ALWAYS);
        Label volVal = new Label(String.valueOf((int) volumeSlider.getValue()));
        volVal.getStyleClass().add("subtle");
        HBox volValRow = new HBox(volVal);
        volValRow.setAlignment(Pos.CENTER_RIGHT);
        volumeSlider.valueProperty().addListener((obs, o, n) -> volVal.setText(String.valueOf(n.intValue())));

        // Audio toggles
        Label audioLabel = new Label("Audio");
        audioLabel.getStyleClass().add("subtle");
        VBox toggles = new VBox(8);
        toggles.setAlignment(Pos.CENTER_LEFT);
        CheckBox musicCheck = new CheckBox("Music");
        CheckBox sfxCheck = new CheckBox("Sound Effects");
        musicCheck.getStyleClass().add("subtle");
        sfxCheck.getStyleClass().add("subtle");
        toggles.getChildren().addAll(musicCheck, sfxCheck);

        // Difficulty
        Label diffLabel = new Label("Default Difficulty");
        diffLabel.getStyleClass().add("subtle");
        defaultDifficultyChoice = new ChoiceBox<>();
        defaultDifficultyChoice.getItems().addAll("Easy", "Medium", "Hard");
        defaultDifficultyChoice.setMaxWidth(Double.MAX_VALUE);
        defaultDifficultyChoice.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;");

        // Load saved preferences
        Preferences prefs = Preferences.userNodeForPackage(SettingsScreen.class);
        volumeSlider.setValue(prefs.getInt("volume", 70));
        musicCheck.setSelected(prefs.getBoolean("music", true));
        sfxCheck.setSelected(prefs.getBoolean("sfx", true));
        defaultDifficultyChoice.setValue(prefs.get("difficulty", "Medium"));

        // Footer buttons
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("settings-footer");

        Button reset = new Button("Reset Defaults");
        reset.getStyleClass().add("subtle");
        reset.setOnAction(e -> {
            volumeSlider.setValue(70);
            musicCheck.setSelected(true);
            sfxCheck.setSelected(true);
            defaultDifficultyChoice.setValue("Medium");
        });

        Button save = new Button("Save");
        save.getStyleClass().addAll("button", "primary");
        save.setOnAction(e -> {
        });

        backButton = new Button("Back");
        backButton.getStyleClass().addAll("button", "danger");
        backButton.setOnAction(e -> {
            if (onBack != null) onBack.run();
        });

        footer.getChildren().addAll(reset, save, backButton);

        content.getChildren().addAll(volumeLabel, volumeSlider, volValRow, audioLabel, toggles, diffLabel, defaultDifficultyChoice);
        card.getChildren().addAll(title, subtitle, sep, content, footer);
        outer.getChildren().add(card);
        root.getChildren().add(outer);
    }

    public Parent getRoot() { return root; }
}
