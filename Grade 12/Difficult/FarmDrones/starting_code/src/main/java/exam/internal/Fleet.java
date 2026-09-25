package exam.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads your {@code DroneManager.toString()} and turns each line back into a drone the display can draw.
 * <p>
 * Every line has to be four values separated by tabs: the name, the colour as a hex string, the location, and the
 * litres on board.
 *
 * <pre>
 * Turing\t#12E5F0\tA0\t5.0  ->  a cyan drone called Turing, docked at A0, carrying 5 litres
 * </pre>
 *
 * Each line is also checked against the drone of the same name in {@code drones.txt}. A line that reads fine but
 * disagrees with the file, say a colour that came out as {@code #0012E5} instead of {@code #12E5F0}, is flagged so
 * you can go looking for the slip in your constructor.
 */
final class Fleet {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    private static final double  EPSILON = 1e-9;
    private static final Pattern HEX     = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Pattern CELL    = Pattern.compile("[A-Z][0-9]+");

    /**
     * Reads every non-blank line of a {@code toString()} result.
     *
     * @param text      the text your {@code DroneManager.toString()} returned
     * @param reference the drones from {@code drones.txt}, used to check each line
     *
     * @return one entry per non-blank line, in the order the lines appear
     */
    static List<Entry> parse(String text, ReferenceDrone[] reference) {
        List<Entry> entries = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            if (raw.isBlank()) {
                continue;
            }
            entries.add(parseLine(raw.strip(), reference));
        }
        return entries;
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    /**
     * How well one line of your {@code toString()} came through.
     */
    enum Status {
        /** The line reads correctly and matches {@code drones.txt}. */
        OK,
        /** The line reads correctly but disagrees with {@code drones.txt}. */
        MISMATCH,
        /** The line cannot be read, so its drone cannot be drawn. */
        UNREADABLE
    }

    /**
     * One line of your {@code toString()}, and the drone it describes.
     *
     * @param line      the line exactly as your code wrote it
     * @param status    how well the line came through
     * @param detail    what went wrong, empty when the status is {@link Status#OK}
     * @param name      the drone's name, or {@code null} if the line is unreadable
     * @param colourHex the drone's colour, or {@code null} if the line is unreadable
     * @param col       the column index of the drone's location
     * @param row       the row number of the drone's location
     * @param payload   the litres on board
     */
    record Entry(String line, Status status, String detail, String name, String colourHex, int col, int row, double payload) {

        /**
         * @return {@code true} if the display can draw this drone
         */
        boolean isDrawable() {
            return status != Status.UNREADABLE;
        }
    }

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Fleet() {
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private static Entry parseLine(String line, ReferenceDrone[] reference) {
        String[] parts = line.split("\t");
        if (parts.length != 4) {
            return unreadable(line, String.format("expected 4 tab-separated values, found %d", parts.length));
        }

        String name      = parts[0].strip();
        String colourHex = parts[1].strip();
        String location  = parts[2].strip();
        if (!HEX.matcher(colourHex).matches()) {
            return unreadable(line, String.format("'%s' is not a hex colour like #12E5F0", colourHex));
        }
        if (!CELL.matcher(location).matches()) {
            return unreadable(line, String.format("'%s' is not a grid cell", location));
        }

        double payload;
        try {
            payload = Double.parseDouble(parts[3].strip());
        } catch (NumberFormatException ex) {
            return unreadable(line, String.format("'%s' is not a number of litres", parts[3].strip()));
        }

        String mismatch = compare(name, colourHex, location, payload, reference);
        Status status   = mismatch.isEmpty() ? Status.OK : Status.MISMATCH;
        return new Entry(line, status, mismatch, name, colourHex, Replay.col(location), Replay.row(location), payload);
    }

    // Lists every way the line disagrees with drones.txt, or returns an empty string when it agrees
    private static String compare(String name, String colourHex, String location, double payload, ReferenceDrone[] reference) {
        ReferenceDrone match = null;
        for (ReferenceDrone drone : reference) {
            if (drone.getName().equals(name)) {
                match = drone;
            }
        }
        if (match == null) {
            return String.format("drones.txt has no drone called '%s'", name);
        }

        List<String> problems = new ArrayList<>();
        if (!match.getColourHex().equalsIgnoreCase(colourHex)) {
            problems.add(String.format("colour should be %s", match.getColourHex()));
        }
        if (!match.getLocation().equals(location)) {
            problems.add(String.format("location should be %s", match.getLocation()));
        }
        if (Math.abs(match.getPayload() - payload) > EPSILON) {
            problems.add(String.format("payload should be %s", match.getPayload()));
        }
        return String.join("; ", problems);
    }

    private static Entry unreadable(String line, String detail) {
        return new Entry(line, Status.UNREADABLE, detail, null, null, 0, 0, 0);
    }

}
