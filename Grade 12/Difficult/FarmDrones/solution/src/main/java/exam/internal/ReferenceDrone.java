package exam.fun_exam.internal;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

/**
 * The drone the replay trusts.
 * <p>
 * When your script plays out, the display never asks your drones how much battery a flight used. It asks one of
 * these instead. Each {@code ReferenceDrone} is built from the same line of {@code drones.txt} as yours, and it
 * runs the battery formula from the paper. So if your formula has a slip in it, your manager makes its decisions
 * on the wrong numbers, while the replay flies the drone on the right ones. That is the moment you watch a drone
 * you were sure about fall out of the sky.
 * <p>
 * A line of {@code drones.txt} is read as {@code name,payload,purchaseDate,colour,bay}, for example:
 *
 * <pre>
 * Turing,5,2026/01/01,1238512,A0  ->  Turing, 5 litres, bought 1 January 2026, colour #12E5F0, docked at A0
 * </pre>
 */
final class ReferenceDrone extends Drone {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private static final double            CELL_METRES = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * Builds a fully charged drone from one line of {@code drones.txt}.
     *
     * @param line a line such as {@code "Turing,5,2026/01/01,1238512,A0"}
     *
     * @return a drone docked in its bay at 100% battery
     */
    static ReferenceDrone parse(String line) {
        String[] data = line.split(",");
        return new ReferenceDrone(data[0],
                                  Double.parseDouble(data[1]),
                                  LocalDate.parse(data[2], DATE_FORMAT),
                                  String.format("#%06X", Integer.parseInt(data[3])),
                                  data[4],
                                  100);
    }

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final String    name;
    private final LocalDate purchaseDate;
    private final String    colourHex;
    private       double    payload;
    private       String    location;
    private       double    batteryLife;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private ReferenceDrone(String name, double payload, LocalDate purchaseDate, String colourHex, String location,
                           double batteryLife) {
        this.name         = name;
        this.payload      = payload;
        this.purchaseDate = purchaseDate;
        this.colourHex    = colourHex;
        this.location     = location;
        this.batteryLife  = batteryLife;
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getPayload() {
        return payload;
    }

    @Override
    public double getBatteryLife() {
        return batteryLife;
    }

    @Override
    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    @Override
    public String getColourHex() {
        return colourHex;
    }

    @Override
    public String getLocation() {
        return location;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    @Override
    public double calculateBatteryDrain(String location) {
        int age = Period.between(purchaseDate, TODAY).getYears();

        double ageFactor;
        if (age < 2) {
            ageFactor = 1.0;
        } else if (age <= 4) {
            ageFactor = 1.25;
        } else {
            ageFactor = 1.5;
        }

        return (distanceTo(location) / 100) * ageFactor * (1 + payload / 20);
    }

    @Override
    public String deliverPayload(String location, double payload) {
        batteryLife  -= calculateBatteryDrain(location);
        this.payload -= payload;
        this.location = location;
        return String.format("%s;%s;%s", name, location, payload);
    }

    /**
     * Returns a fresh copy of this drone with the same state, so the replay can run a script from the start as
     * often as it likes.
     *
     * @return an independent copy of this drone
     */
    ReferenceDrone copy() {
        return new ReferenceDrone(name, payload, purchaseDate, colourHex, location, batteryLife);
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private double distanceTo(String destination) {
        double dx = (destination.charAt(0) - location.charAt(0)) * CELL_METRES;
        double dy = (Integer.parseInt(destination.substring(1)) - Integer.parseInt(location.substring(1))) * CELL_METRES;
        return Math.sqrt(dx * dx + dy * dy);
    }

}
