package exam;

import exam.internal.Drone;
import exam.internal.Manager;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * The worked answer to Questions 2 and 5 of the Farm Drones paper.
 * <p>
 * The constructor reads up to ten {@link FarmDrone} objects from {@code drones.txt}.
 * {@link #assignJob(String, double)} gives a field to the first drone in the array that has the battery and the
 * water for it. {@link #processJobs()} calls it once for each line of {@code jobs.txt} and joins the results into
 * the script the display replays.
 *
 * <h2 id="usage">Usage</h2>
 * {@link DroneUI} builds the manager and then launches it:
 *
 * <pre>{@code
 * DroneManager m = new DroneManager();  // reads drones.txt
 * m.launch();                           // opens the window, which calls toString() and processJobs()
 * }</pre>
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-01
 * @since 1.0.0
 */
public class DroneManager extends Manager {

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private Drone[] drones = new Drone[10];
    private int     n      = 0;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    public DroneManager() {
        try (Scanner in = new Scanner(new File("drones.txt"))) {
            while (in.hasNextLine()) {
                String[] data = in.nextLine().split(",");

                String name         = data[0];
                double payload      = Double.parseDouble(data[1]);
                String purchaseDate = data[2];
                int    colour       = Integer.parseInt(data[3]);
                String location     = data[4];

                drones[n++] = new FarmDrone(name, payload, purchaseDate, colour, location);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    // toString() and processJobs() override Manager, and Display calls each of them once when the window opens

    @Override
    public String toString() {
        String str = "";
        for (int i = 0; i < n; i++) {
            str += drones[i].toString() + "\n";
        }
        return str;
    }

    public String processJobs() {
        StringBuilder str = new StringBuilder();
        try (Scanner in = new Scanner(new File("jobs.txt"))) {
            while (in.hasNextLine()) {
                String[] data = in.nextLine().split(";");
                String location = data[0];
                double payload  = Double.parseDouble(data[1]);

                String line = assignJob(location, payload);
                if (!line.isEmpty()) {
                    str.append(line).append("\n");
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return str.toString();
    }

    public String assignJob(String location, double payload) {
        for (int i = 0; i < n; i++) {
            Drone d = drones[i];

            // First fit: the first drone that can make the trip takes the job
            if (canTravel(d, location, payload)) {
                return d.deliverPayload(location, payload);
            }
        }

        // No drone can take this job, so it adds no line to the script
        return "";
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private boolean canTravel(Drone d, String location, double payload) {
        boolean enoughPower = d.getBatteryLife() - d.calculateBatteryDrain(location) >= 0;
        boolean enoughPayload = d.getPayload() >= payload;

        return enoughPower && enoughPayload;
    }

}
