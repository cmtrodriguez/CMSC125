package typeshi;

import java.util.List;
import java.util.Random;

public class WordGenerator {

    private final List<String> passages = List.of(
            "JavaFX is a powerful framework for building modern Java GUIs.",
            "TypeShi challenges your speed and accuracy against the computer.",
            "Multithreading allows simultaneous operations like timer and AI typing.",
            "Real-time feedback helps players correct mistakes immediately.",
            "Programming requires patience, practice, and problem-solving skills."
    );

    private final Random random = new Random();

    public String getRandomPassage() {
        int index = random.nextInt(passages.size());
        return passages.get(index);
    }
}
