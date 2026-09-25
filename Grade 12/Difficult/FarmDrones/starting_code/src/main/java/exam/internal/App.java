package exam.internal;

import javax.swing.*;
import java.awt.*;

public class App extends JFrame {

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private Manager manager;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    public App(Manager manager) throws HeadlessException {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(new Display(manager));
        pack();
        setLocationRelativeTo(null);
    }

}
