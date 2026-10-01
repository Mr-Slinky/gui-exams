package exam.internal;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens a window that draws your fractions on a fraction wall.
 * <p>
 * A fraction wall stacks one table for each denominator from 2 to {@code n}: halves at the top, then thirds, down to
 * the {@code n}ths. Each table has as many columns as its denominator. Every fraction in your list gets a row of its
 * own in its table and fills as many columns as its numerator, so 3/4 fills three of the four columns. Every table
 * is the same width, so two fractions of the same size end at the same point.
 *
 * <h2 id="usage">Usage</h2>
 * Pass the window the same {@code n} you pass to your method, together with the list your method returns:
 *
 * <pre>{@code
 * int n = 7;
 * FractionWall.show(n, simpFrac(n));
 * }</pre>
 *
 * The wall is drawn for an {@code n} from 2 to 10. Any other {@code n} opens the window with a message instead.
 *
 * <h2 id="reading">Reading the wall</h2>
 * Before it starts, the wall shows an empty outline for every fraction your list should contain. It then plays your
 * list one fraction at a time, from the smallest denominator to the largest, so your list may be in any order.
 *
 * <ul>
 *   <li>A correct fraction fills its outline in green.</li>
 *   <li>A fraction that can be simplified, such as 2/4, fills a red row of its own. It then slides up and lands on
 *   the fraction it simplifies to. The two are the same size, so they line up exactly.</li>
 *   <li>A fraction that appears twice slides onto its twin in the same way.</li>
 *   <li>A fraction of one whole or more, such as 2/2 or 5/3, fills its row in red and spills past the edge.</li>
 *   <li>A fraction of zero or less, such as 0/3, stays empty and shakes.</li>
 *   <li>A denominator outside 2 to {@code n}, such as in 3/11 or 1/1, gets a red table of its own below the
 *   wall.</li>
 *   <li>Text that is not a fraction, such as {@code "abc"} or {@code "1 / 2"}, appears in red in the list on the
 *   right.</li>
 * </ul>
 *
 * Once your list has played, every outline that is still empty turns amber, because those fractions are missing from
 * your list. A banner then says whether you passed. You pass when every outline is filled and nothing is red. Click
 * anywhere to watch it again.
 */
public final class FractionWall {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    private static JFrame frame;  // touched only on the event thread

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private FractionWall() {
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * Opens the window and starts playing your list. A second call while the window is open plays the new list in
     * the same window.
     *
     * @param n         the largest denominator on the wall, from 2 to 10
     * @param fractions the fractions to draw, each written as {@code numerator/denominator}, such as {@code "3/4"}
     */
    public static void show(int n, List<String> fractions) {
        // Copied now, so a later change to the caller's list leaves the replay as it was
        List<String> copy = fractions == null ? null : new ArrayList<>(fractions);
        EventQueue.invokeLater(() -> {
            boolean opening = frame == null;
            if (opening) {
                frame = new JFrame("Fraction Wall");
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            }
            frame.setContentPane(new Display(n, copy));
            frame.pack();
            if (opening) {
                frame.setLocationRelativeTo(null);
            }
            frame.setVisible(true);
        });
    }

}
