package typeshi;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;

public class VictoryScreen {

    private final StackPane root = new StackPane();
    private MediaPlayer mediaPlayer;
    private final Runnable onBack;

    public VictoryScreen(int playerScore,
                         int computerScore,
                         int playerErrors,
                         int computerErrors,
                         Runnable onBack,
                         boolean isMultiplayer) {

        this.onBack = onBack;

        int playerFinal = playerScore - playerErrors;
        int computerFinal = computerScore - computerErrors;

        boolean playerWon = playerFinal > computerFinal;

        // Prevent Enter from triggering buttons
        root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                e.consume();
            }
        });

        // ---------- BACKGROUND ----------
        ImageView bgView = null;
        try {
            String gifPath = playerWon ? "/images/victory.gif" : "/images/defeat.gif";
            var url = getClass().getResource(gifPath);
            if (url != null) {
                Image bgImage = new Image(url.toExternalForm());
                bgView = new ImageView(bgImage);
                bgView.setPreserveRatio(false);
                bgView.fitWidthProperty().bind(root.widthProperty());
                bgView.fitHeightProperty().bind(root.heightProperty());
            }
        } catch (Exception ignored) {}

        String opponentLabel = isMultiplayer ? "Opponent" : "Computer";

        String winnerText;
        if (playerFinal > computerFinal) {
            winnerText = "YOU WIN!";
        } else if (playerFinal < computerFinal) {
            winnerText = isMultiplayer ? "OPPONENT WINS!" : "COMPUTER WINS!";
        } else {
            winnerText = "DRAW!";
        }

        Label timesUp = new Label("TIME'S UP");
        timesUp.getStyleClass().add("panel-title");

        Label winnerLabel = new Label(winnerText);
        winnerLabel.getStyleClass().add("victory-winner");

        // 🔹Show both raw and final score
        Label youStats = new Label(
                String.format(
                        "You: %d points, %d errors  →  Final: %d",
                        playerScore, playerErrors, playerFinal
                )
        );

        Label oppStats = new Label(
                String.format(
                        "%s: %d points, %d errors  →  Final: %d",
                        opponentLabel, computerScore, computerErrors, computerFinal
                )
        );

        youStats.getStyleClass().add("stats");
        oppStats.getStyleClass().add("stats");

        Button backButton = new Button("Back to Menu");
        backButton.getStyleClass().addAll("button", "primary");
        backButton.setOnAction(e -> {
            if (mediaPlayer != null) mediaPlayer.stop();
            if (onBack != null) {
                onBack.run();
            } else if (root.getScene() != null) {
                root.getScene().setRoot(new HomeScreen().getRoot());
            }
        });

        VBox card = new VBox(12, timesUp, winnerLabel, youStats, oppStats, backButton);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30));
        card.setMaxWidth(620);
        card.setStyle(
                "-fx-background-color: rgba(0,0,0,0.35);" +
                        "-fx-background-radius: 20;"
        );

        if (bgView != null) {
            root.getChildren().addAll(bgView, card);
        } else {
            root.setStyle("-fx-background-color: #111;");
            root.getChildren().add(card);
        }

        StackPane.setAlignment(card, Pos.CENTER);
        playVictoryMusic(winnerText);
    }

    public VictoryScreen(int playerScore,
                         int computerScore,
                         int playerErrors,
                         int computerErrors) {
        this(playerScore, computerScore, playerErrors, computerErrors, null, false);
    }

    public VictoryScreen(int playerScore,
                         int computerScore,
                         int playerErrors,
                         int computerErrors,
                         Runnable onBack) {
        this(playerScore, computerScore, playerErrors, computerErrors, onBack, false);
    }

    private void playVictoryMusic(String winnerText) {
        try {
            String file = winnerText.contains("YOU") ? "audio/victory.mp3" : "audio/defeat.mp3";
            var url = getClass().getResource("/" + file);
            if (url == null) return;

            mediaPlayer = new MediaPlayer(new Media(url.toExternalForm()));
            mediaPlayer.setVolume(0.6);
            mediaPlayer.play();
        } catch (Exception ignored) {}
    }

    public Parent getRoot() {
        return root;
    }
}
