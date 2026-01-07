package typeshi;

import java.util.List;
import java.util.Random;

public class WordGenerator {

    // EASY: Short, simple sentences with basic vocabulary
    private final List<String> easyPassages = List.of(
            "Java is fun to learn and easy to read.",
            "Typing fast takes time and practice.",
            "Every mistake is a chance to improve.",
            "Focus on accuracy before speed.",
            "Small steps lead to big progress.",
            "Keep your eyes on the screen.",
            "Practice typing every single day.",
            "Good habits make good programmers.",
            "Stay calm and type with rhythm.",
            "Learning to code is a useful skill."
    );

    // MEDIUM: Moderate length with some technical terms
    private final List<String> mediumPassages = List.of(
            "JavaFX is a powerful framework for building modern Java GUIs.",
            "TypeShi challenges your speed and accuracy against the computer.",
            "Multithreading allows simultaneous operations like timer and AI typing.",
            "Real-time feedback helps players correct mistakes immediately.",
            "Programming requires patience, practice, and problem-solving skills."
    );

    // HARD: Long, complex sentences with advanced vocabulary and punctuation
    private final List<String> hardPassages = List.of(
            "Concurrency bugs are notoriously difficult to reproduce, diagnose, and fix—especially under real-world timing conditions.",
            "While optimizing premature code is discouraged, ignoring performance implications entirely can lead to catastrophic bottlenecks.",
            "The quick brown fox jumps over the lazy dog; meanwhile, developers debug race conditions at 3:47 a.m.",
            "Typing accuracy suffers when cognitive load increases, punctuation multiplies, and syntax errors silently accumulate.",
            "A well-architected system balances scalability, maintainability, extensibility, and readability without overengineering.",
            "Misplaced semicolons, off-by-one errors, and incorrect assumptions are responsible for countless hours of debugging.",
            "Asynchronous event-driven architectures demand careful coordination between callbacks, threads, and shared resources.",
            "Readable code is written for humans first, compilers second, and future maintainers—who may be you.",
            "Edge cases appear precisely where developers least expect them, often during demos, interviews, or production releases.",
            "Software development is the art of transforming vague ideas into precise instructions that computers relentlessly follow."
    );

    private final Random random = new Random();

    /**
     * Get a random passage based on difficulty level.
     * @param mode 1 = Easy, 2 = Medium, 3 = Hard
     * @return A random passage for the specified difficulty
     */
    public String getRandomPassage(int mode) {
        List<String> selectedList;
        switch (mode) {
            case 1:  // Easy
                selectedList = easyPassages;
                break;
            case 2:  // Medium
                selectedList = mediumPassages;
                break;
            case 3:  // Hard
                selectedList = hardPassages;
                break;
            default:
                selectedList = mediumPassages;  // Default to medium
        }
        int index = random.nextInt(selectedList.size());
        return selectedList.get(index);
    }

    /**
     * Backward compatibility: defaults to medium difficulty.
     * @return A random medium-difficulty passage
     */
    public String getRandomPassage() {
        return getRandomPassage(2);  // Default to medium
    }
}