package exam.fun_exam;

import exam.fun_exam.internal.Drone;
import exam.fun_exam.internal.Manager;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * `_` to be replaced with Noun when exam questions are settled.
 *
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
    // Overload methods defined in manager. These will be invoked by Display::paintComponent

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
        try (Scanner in = new Scanner(new File("jobs_easy.txt"))) {
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
