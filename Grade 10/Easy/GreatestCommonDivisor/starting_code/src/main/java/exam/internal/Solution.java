package exam.internal;

import javax.swing.*;
import java.awt.*;

/**
 * The class your answer extends.
 * <p>
 * Write a class that says {@code extends Solution} and override {@link #gcd(int, int)}. Until you do, the version
 * here throws an {@code UnsupportedOperationException}, and the window waits for you.
 * <p>
 * {@link #launch()} opens the window. The window calls your {@code gcd} with several pairs of numbers and draws a
 * trace table for each call: a column for each of your variables, and a new row each time your loop goes round.
 *
 * <h2 id="usage">Usage</h2>
 * Make an object of your class and launch it from your {@code main} method:
 *
 * <pre>{@code
 * new EuclidGCD().launch();  // the window opens and the trace tables start to fill in
 * }</pre>
 */
public abstract class Solution {

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * Finds the greatest common divisor of two numbers, which is the largest number that divides both of them
     * exactly. This is the method you write.
     *
     * <pre>
     * gcd(12, 18)  ->  6
     * gcd(7, 20)   ->  1
     * </pre>
     *
     * @param small the smaller of the two numbers, 1 or more
     * @param big   the larger of the two numbers
     *
     * @return the largest number that divides both {@code small} and {@code big} exactly
     */
    public int gcd(int small, int big) {
        throw new UnsupportedOperationException("gcd has not been implemented");
    }

    /**
     * Opens the window and plays the trace table for each call to your {@code gcd}.
     */
    public final void launch() {
        Class<? extends Solution> type = getClass();
        EventQueue.invokeLater(() -> {
            var frame = new JFrame("Trace Table");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setContentPane(new Display(type));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

}
