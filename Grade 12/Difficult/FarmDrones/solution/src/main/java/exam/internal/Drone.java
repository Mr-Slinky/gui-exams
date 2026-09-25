package exam.fun_exam.internal;

import java.time.LocalDate;

/**
 * You found the blueprint for every drone on the farm. Nice bit of exploring.
 * <p>
 * Your job is to write a class that says {@code extends Drone} and overrides every method in here. Have a look
 * at the method bodies: each one throws an {@code UnsupportedOperationException}. Had these methods been
 * {@code abstract}, your IDE would demand all of them the moment you typed {@code extends Drone}. Throwing
 * instead lets you build your class one method at a time. Any method you have yet to write fails loudly the
 * moment something calls it, and the error message tells you which one.
 * <p>
 * Locations work like the seats in a cinema: a column letter followed by a row number, such as {@code "F12"}.
 * Row 0 is where the docking bays are, so a drone parked in the bay above column C starts at {@code "C0"}.
 *
 * <h2 id="usage">Usage</h2>
 * Your manager can ask a drone what a flight would cost before sending it anywhere:
 *
 * <pre>{@code
 * Drone d = drones[i];
 * double cost = d.calculateBatteryDrain("F12");  // battery % the flight to F12 would use
 * String line = d.deliverPayload("F12", 25);     // "Falcon;F12;25.0"
 * }</pre>
 * <p>
 * Here is the catch. {@link #deliverPayload(String, double)} is a very obedient drone: it flies wherever it is
 * told, whether or not it has the water or the battery for the trip. Checking that first is up to you.
 */
public abstract class Drone {

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    /**
     * @return the name of this drone, such as {@code "Falcon"}
     */
    public String getName() {
        throw new UnsupportedOperationException("Drone::getName() has not yet been implemented");
    }

    /**
     * Returns the water this drone is carrying. A handy fact: one litre of water weighs one kilogram, so this
     * one number is both the litres on board and the payload weight in kilograms.
     *
     * @return the litres of water on board, where 0 is an empty drone
     */
    public double getPayload() {
        throw new UnsupportedOperationException("Drone::getPayload() has not yet been implemented");
    }

    /**
     * Returns the charge left in the battery as a percentage. Every drone leaves its bay at 100. Send one on a
     * flight it cannot finish, and you will see this number drop below 0.
     *
     * @return the battery percentage
     */
    public double getBatteryLife() {
        throw new UnsupportedOperationException("Drone::getBatteryLife() has not yet been implemented");
    }

    /**
     * Returns the date this drone was bought. Older drones drain their batteries faster, so the age you work
     * out from this date matters.
     *
     * @return the purchase date
     */
    public LocalDate getPurchaseDate() {
        throw new UnsupportedOperationException("Drone::getPurchaseDate() has not yet been implemented");
    }

    /**
     * Returns the colour of this drone as a hex string, such as {@code "#FF6A00"}. The display paints your
     * drone in this colour, so pick carefully when you get to choose.
     *
     * @return the colour as a hex string
     */
    public String getColourHex() {
        throw new UnsupportedOperationException("Drone::getColourHex() has not yet been implemented");
    }

    /**
     * @return the cell this drone is at, such as {@code "C0"} while docked or {@code "F12"} after a delivery
     */
    public String getLocation() {
        throw new UnsupportedOperationException("Drone::getLocation() has not yet been implemented");
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * Works out how much battery a flight from where the drone is now to {@code location} would use. Think of
     * it as asking for a quote: the drone stays exactly where it is, with the same water and the same battery.
     *
     * @param location the destination cell, such as {@code "F12"}
     *
     * @return the battery percentage the flight would use
     */
    public double calculateBatteryDrain(String location) {
        throw new UnsupportedOperationException("Drone::calculateBatteryDrain(String) has not yet been implemented");
    }

    /**
     * Flies this drone to {@code location} and releases {@code payload} litres of water there. The drone never
     * argues. It makes the trip, and then:
     *
     * <ul>
     *   <li>the battery drops by {@link #calculateBatteryDrain(String)} for the flight, worked out before
     *   the drone
     *       moves</li>
     *   <li>the water on board drops by {@code payload}</li>
     *   <li>the location becomes {@code location}</li>
     * </ul>
     * <p>
     * Send it on a job it cannot manage, and the battery or the water goes below 0. The animation shows exactly
     * where that went wrong.
     *
     * <pre>
     * Falcon at C0, delivers 25 litres to F12  ->  returns "Falcon;F12;25.0"
     * </pre>
     *
     * @param location the destination cell, such as {@code "F12"}
     * @param payload  the litres of water to release at {@code location}
     *
     * @return a line of the form {@code name;location;litres}, such as {@code "Falcon;F12;25.0"}
     */
    public String deliverPayload(String location, double payload) {
        throw new UnsupportedOperationException("Drone::deliverPayload(String, double) has not yet been implemented");
    }

}
