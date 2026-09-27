package exam;

import exam.internal.Drone;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

public class FarmDrone extends Drone {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private static final double CELL_WIDTH_METRES  = 100;
    private static final double CELL_HEIGHT_METRES = 100;

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private String    name;
    private String    colourHex;
    private LocalDate purchaseDate;
    private double    payload;
    private double    batteryLife;
    private String    location;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    public FarmDrone(String name, double payload, String purchaseDate, int colour, String location) {
        this.name         = name;
        this.payload      = payload;
        this.purchaseDate = LocalDate.parse(purchaseDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        this.colourHex    = String.format("#%06X", colour);
        this.batteryLife  = 100;
        this.location     = location;
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getColourHex() {
        return colourHex;
    }

    @Override
    public LocalDate getPurchaseDate() {
        return purchaseDate;
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
    public String getLocation() {
        return location;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    @Override
    public double calculateBatteryDrain(String location) {
        double distance = distanceBetween(this.location, location);
        int    ageYears = calculateAgeInYears();

        double ageFactor;
        if (ageYears < 2) {
            ageFactor = 1.0;
        } else if (ageYears <= 4) {
            ageFactor = 1.25;
        } else {
            ageFactor = 1.5;
        }

        return (distance / 100) * ageFactor * (1 + getPayload() / 20);
    }

    @Override
    public String deliverPayload(String location, double payload) {
        // Drain is measured before moving, while the water is still on board
        batteryLife  -= calculateBatteryDrain(location);
        this.payload -= payload;
        this.location = location;

        return name + ";" + location + ";" + payload;
    }

    @Override
    public String toString() {
        return name + "\t" + colourHex + "\t" + location + "\t" + payload;
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private int calculateAgeInYears() {
        return Period.between(purchaseDate, TODAY).getYears();
    }

    private double distanceBetween(String location1, String location2) {
        double x1 = (location1.charAt(0) - 'A') * CELL_WIDTH_METRES;
        double x2 = (location2.charAt(0) - 'A') * CELL_WIDTH_METRES;

        double y1 = Integer.parseInt(location1.substring(1)) * CELL_HEIGHT_METRES;
        double y2 = Integer.parseInt(location2.substring(1)) * CELL_HEIGHT_METRES;

        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

}
