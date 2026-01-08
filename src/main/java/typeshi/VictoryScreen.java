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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

public class VictoryScreen {

    private final StackPane root = new StackPane();
    private MediaPlayer mediaPlayer;

    public VictoryScreen(int playerScore,
                         int computerScore,
                         int playerErrors,
                         int computerErrors) {

        // ---------- BACKGROUND GIF ----------
        // Put file at: src/main/resources/images/victory.gif
        Image bgImage = new Image(
                getClass().getResource("/images/victory.gif").toExternalForm()
        );
        ImageView bgView = new ImageView(bgImage);
        bgView.setPreserveRatio(false); // stretch to fill window
        bgView.fitWidthProperty().bind(root.widthProperty());
        bgView.fitHeightProperty().bind(root.heightProperty());

        // ---------- TEXT / UI OVERLAY ----------
        String winnerText;
        if (playerScore > computerScore) {
            winnerText = "YOU WIN!";
        } else if (playerScore < computerScore) {
            winnerText = "COMPUTER WINS!";
        } else {
            winnerText = "DRAW!";
        }

        Label timesUp = new Label("TIME'S UP");
        timesUp.setFont(Font.font("Consolas", 40));
        timesUp.setTextFill(Color.GOLD);

        Label winnerLabel = new Label(winnerText);
        winnerLabel.setFont(Font.font("Consolas", 32));
        winnerLabel.setTextFill(
                playerScore >= computerScore ? Color.LIMEGREEN : Color.CRIMSON
        );

        Label youStats = new Label(
                String.format("You: %d points, %d errors", playerScore, playerErrors)
        );
        youStats.setFont(Font.font("Consolas", 18));
        youStats.setTextFill(Color.WHITE);

        Label compStats = new Label(
                String.format("Computer: %d points, %d errors", computerScore, computerErrors)
        );
        compStats.setFont(Font.font("Consolas", 18));
        compStats.setTextFill(Color.WHITE);

        Button backButton = new Button("Back to Menu");
        backButton.setFont(Font.font("Consolas", 16));
        backButton.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
            root.getScene().getWindow().hide();
        });

        VBox overlay = new VBox(10, timesUp, winnerLabel, youStats, compStats, backButton);
        overlay.setAlignment(Pos.TOP_CENTER);
        overlay.setPadding(new Insets(40, 20, 20, 20));
        overlay.setMaxWidth(500);
        overlay.setStyle(
                "-fx-background-color: rgba(0,0,0,0.35); " +
                        "-fx-background-radius: 20;"
        );

        root.getChildren().addAll(bgView, overlay);
        StackPane.setAlignment(overlay, Pos.TOP_CENTER);

        playVictoryMusic(winnerText);
    }

    private void playVictoryMusic(String winnerText) {
        try {
            String fileName = "audio/victory.mp3";
            if (!winnerText.contains("YOU")) {
                fileName = "audio/defeat.mp3";
            }

            var url = getClass().getResource("/" + fileName);
            if (url == null) {
                System.err.println("Audio file not found: " + fileName);
                return;
            }

            Media media = new Media(url.toExternalForm());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(0.6);
            mediaPlayer.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Parent getRoot() {
        return root;
    }
}
