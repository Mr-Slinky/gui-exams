package exam.internal;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * The part of the manager that was written for you.
 * <p>
 * Your {@code DroneManager} extends this class, so everything here runs before your own constructor does. Two
 * things happen behind the scenes:
 *
 * <ul>
 *   <li>The constructor reads {@code drones.txt} into a set of reference drones. The replay flies those, which is
 *   why a wrong battery formula in your code shows up on screen.</li>
 *   <li>{@link #launch()} opens the window. It waits for you to call it, so your constructor has finished
 *   loading every drone before anything gets drawn.</li>
 * </ul>
 *
 * <h2 id="usage">Usage</h2>
 * Build your manager first, then launch it:
 *
 * <pre>{@code
 * DroneManager m = new DroneManager();  // your constructor reads drones.txt
 * m.launch();                           // the window opens and the replay starts
 * }</pre>
 */
public class Manager {

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final ReferenceDrone[] referenceDrones;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    public Manager() {
        referenceDrones = readReferenceDrones("drones.txt");
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * Opens the window. Call this once your manager has been built.
     */
    public final void launch() {
        EventQueue.invokeLater(() -> new App(this).setVisible(true));
    }

    /**
     * Describes every drone in your fleet, one line per drone. This is the method you write in
     * {@code DroneManager}, using each drone's own {@code toString()}. The display reads these lines to draw your
     * drones in their bays, so a line it cannot read shows up as a missing drone.
     * <p>
     * Until you write it, this version returns an empty string, and the bays stay empty.
     *
     * @return one {@code name<tab>colourHex<tab>location<tab>payload} line per drone
     */
    @Override
    public String toString() {
        return "";
    }

    /**
     * Assigns every job in {@code jobs.txt} to a drone and returns the script the replay plays out. This is the
     * method you write in {@code DroneManager}.
     * <p>
     * Until you write it, this version returns an empty script. Your drones then sit in their bays and the replay
     * waits, so you can run the program and check your drones long before you reach the last question.
     *
     * @return one line per assigned job, each of the form {@code name;location;litres}
     */
    public String processJobs() {
        return "";
    }

    /**
     * Returns fresh copies of the reference drones, each docked in its bay at full battery with its full payload.
     * A new call gives a new set, so every replay starts from the beginning.
     *
     * @return one reference drone per line of {@code drones.txt}, in file order
     */
    ReferenceDrone[] copyReferenceDrones() {
        ReferenceDrone[] copies = new ReferenceDrone[referenceDrones.length];
        for (int i = 0; i < copies.length; i++) {
            copies[i] = referenceDrones[i].copy();
        }
        return copies;
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private static ReferenceDrone[] readReferenceDrones(String fileName) {
        ReferenceDrone[] drones = new ReferenceDrone[0];
        try (Scanner in = new Scanner(new File(fileName))) {
            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.isBlank()) {
                    continue;
                }
                ReferenceDrone[] grown = new ReferenceDrone[drones.length + 1];
                System.arraycopy(drones, 0, grown, 0, drones.length);
                grown[drones.length] = ReferenceDrone.parse(line);
                drones = grown;
            }
        } catch (FileNotFoundException ex) {
            throw new IllegalStateException(String.format("Could not find '%s' in the working directory %s", fileName, new File("").getAbsolutePath()), ex);
        }
        return drones;
    }

}
