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
    private final Runnable onBack; // optional callback when Back button is pressed

    /**
     * Main constructor allowing an onBack callback from callers.
     */
    public VictoryScreen(int playerScore,
                         int computerScore,
                         int playerErrors,
                         int computerErrors,
                         Runnable onBack,
                         boolean isMultiplayer) {
        this.onBack = onBack;

        // Prevent Enter from activating buttons unintentionally on the victory screen
        root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                e.consume();
            }
        });

        // Determine if player won or lost
        boolean playerWon = playerScore > computerScore;

        // ---------- BACKGROUND GIF (win or loss specific) ----------
        // Victory: src/main/resources/images/victory.gif
        // Defeat:  src/main/resources/images/defeat.gif
        ImageView bgView = null;
        try {
            String gifPath = playerWon ? "/images/victory.gif" : "/images/defeat.gif";
            var url = getClass().getResource(gifPath);
            if (url != null) {
                Image bgImage = new Image(url.toExternalForm());
                bgView = new ImageView(bgImage);
                bgView.setPreserveRatio(false); // stretch to fill window
                bgView.fitWidthProperty().bind(root.widthProperty());
                bgView.fitHeightProperty().bind(root.heightProperty());
            } else {
                System.err.println("Background image not found: " + gifPath + " — using fallback background");
                // fallback background style applied below if no bgView
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            bgView = null;
        }

        // ---------- TEXT / UI OVERLAY ----------
        String opponentLabel = isMultiplayer ? "Opponent" : "Computer";

        String winnerText;
        if (playerScore > computerScore) {
            winnerText = "YOU WIN!";
        } else if (playerScore < computerScore) {
            winnerText = isMultiplayer ? "OPPONENT WINS!" : "COMPUTER WINS!";
        } else {
            winnerText = "DRAW!";
        }

        Label timesUp = new Label("TIME'S UP");
        timesUp.getStyleClass().add("panel-title");

        Label winnerLabel = new Label(winnerText);
        winnerLabel.getStyleClass().add("victory-winner");

        Label youStats = new Label(
                String.format("You: %d points, %d errors", playerScore, playerErrors)
        );
        youStats.getStyleClass().add("stats");

        Label compStats = new Label(
                String.format("%s: %d points, %d errors", opponentLabel, computerScore, computerErrors)
        );
        compStats.getStyleClass().add("stats");

        Button backButton = new Button("Back to Menu");
        backButton.setDefaultButton(false);
        backButton.getStyleClass().addAll("button", "primary");
        backButton.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
            if (this.onBack != null) {
                try { this.onBack.run(); } catch (Exception ex) { ex.printStackTrace(); }
            } else {
                try {
                    // Replace the current scene root with the Home screen so the player returns to menu
                    if (root.getScene() != null) {
                        root.getScene().setRoot(new HomeScreen().getRoot());
                    } else {
                        // Fallback: hide window if scene is unexpectedly null
                        root.getScene().getWindow().hide();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // In case of any error, hide the window as a last resort
                    if (root.getScene() != null) root.getScene().getWindow().hide();
                }
            }
        });

        VBox overlay = new VBox(10, timesUp, winnerLabel, youStats, compStats, backButton);
        overlay.setAlignment(Pos.TOP_CENTER);
        overlay.setPadding(new Insets(40, 20, 20, 20));
        overlay.setMaxWidth(500);
        overlay.setStyle(
                "-fx-background-color: rgba(0,0,0,0.35); " +
                        "-fx-background-radius: 20;"
        );

        VBox centerCard = new VBox(12, timesUp, winnerLabel, youStats, compStats, backButton);
        centerCard.setAlignment(Pos.CENTER);
        centerCard.getStyleClass().add("card");
        centerCard.setMaxWidth(620);

        if (bgView != null) {
            root.getChildren().addAll(bgView, centerCard);
        } else {
            // apply a simple fallback background so the overlay is visible
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #111111, #2b2b2b);");
            root.getChildren().add(centerCard);
        }

        StackPane.setAlignment(centerCard, Pos.CENTER);

        // Play audio (if available) — kept in separate try/catch within method
        playVictoryMusic(winnerText);
    }

    /**
     * Backwards-compatible constructor that does not provide a callback.
     */
    public VictoryScreen(int playerScore,
                         int computerScore,
                         int playerErrors,
                         int computerErrors) {
        this(playerScore, computerScore, playerErrors, computerErrors, null, false);
    }

    /**
     * Constructor with callback but no multiplayer flag (defaults to singleplayer).
     */
    public VictoryScreen(int playerScore,
                         int computerScore,
                         int playerErrors,
                         int computerErrors,
                         Runnable onBack) {
        this(playerScore, computerScore, playerErrors, computerErrors, onBack, false);
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