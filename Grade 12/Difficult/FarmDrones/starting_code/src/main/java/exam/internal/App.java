package exam.internal;

import javax.swing.*;
import java.awt.*;

/**
 * The window your drones appear in.
 * <p>
 * The window contains a single {@link Display}, sizes itself to fit that display and opens in the centre of the
 * screen. Closing the window ends your program.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-01
 * @since 1.0.0
 */
public class App extends JFrame {

    public App(Manager manager) throws HeadlessException {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(new Display(manager));
        pack();
        setLocationRelativeTo(null);
    }

}
