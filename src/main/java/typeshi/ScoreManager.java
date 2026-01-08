package typeshi;


public class ScoreManager {
    private double playerProgress = 0;
    private double computerProgress = 0;
    private int playerErrors = 0;
    private int computerErrors = 0;
    private int playerScore = 0;
    private int computerScore = 0;


    public void reset() {
        playerProgress = computerProgress = 0;
        playerErrors = computerErrors = 0;
        playerScore = computerScore = 0;
    }


    public void setPlayerProgress(double p) { playerProgress = p; }
    public void setComputerProgress(double p) { computerProgress = p; }
    public void setPlayerErrors(int e) { playerErrors = e; }
    public void setComputerErrors(int e) { computerErrors = e; }


    public void awardPlayer(int amount) { playerScore += amount; }
    public void awardComputer(int amount) { computerScore += amount; }


    public String playerSummary() { return String.format("Score: %d | Errors: %d", playerScore, playerErrors); }
    public String computerSummary() { return String.format("Score: %d | Errors: %d", computerScore, computerErrors); }
}