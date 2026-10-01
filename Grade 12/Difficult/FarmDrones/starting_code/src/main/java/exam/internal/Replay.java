package exam.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Works out, ahead of time, everything that happens when your script plays.
 * <p>
 * Before a single frame is drawn, the replay reads your script line by line and flies the matching reference drone
 * for each one. It works out where every drone is at every moment, which fields get water and when, and which of
 * your lines went wrong and why. The display then only has to ask "where is everything at 3.2 seconds?" and draw
 * the answer.
 * <p>
 * Your script plays one line at a time, in the order you wrote it. Each flight starts once the one before it has
 * finished, so you can follow exactly which line sent which drone where:
 *
 * <pre>
 * Turing;F4;10.0   ->  Turing flies from its bay to F4 and waters it
 * Lovelace;B2;5.0  ->  once Turing has finished spraying, Lovelace takes off
 * Turing;C7;5.0    ->  then Turing flies on from F4 to C7
 * </pre>
 *
 * A line the replay cannot fly, such as one naming a drone that does not exist, still gets a short turn of its
 * own, so its red mark appears in the right place in the list.
 */
final class Replay {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    static final double START_DELAY_SECONDS = 1.0;
    static final double CELLS_PER_SECOND    = 3.0;
    static final double MIN_FLIGHT_SECONDS  = 0.4;
    static final double SPRAY_SECONDS       = 0.8;
    static final double FALL_SECONDS        = 1.0;
    static final double REJECT_SECONDS      = 0.6;

    // Floating-point slack, so a battery that lands on exactly 0 counts as a finished flight
    private static final double  EPSILON = 1e-9;
    private static final Pattern CELL    = Pattern.compile("[A-Z][0-9]+");

    /**
     * Plays a script against a set of drones and jobs. The drones are flown as the script instructs, so pass fresh
     * copies.
     *
     * @param script one {@code name;location;litres} line per delivery
     * @param drones the drones to fly, docked and fully charged
     * @param jobs   the fields that need water
     *
     * @return the finished replay, ready to be drawn at any moment in time
     */
    static Replay run(String script, ReferenceDrone[] drones, Job[] jobs) {
        return new Replay(script, drones, jobs);
    }

    /**
     * Reads a jobs file, where each line is a cell and the litres that cell needs.
     *
     * <pre>
     * F12;25  ->  the field at F12 needs 25 litres
     * </pre>
     *
     * @param fileName the jobs file to read
     *
     * @return one job per non-blank line, in file order
     *
     * @throws IllegalStateException if the file does not exist
     */
    static Job[] readJobs(String fileName) {
        List<Job> list = new ArrayList<>();
        try (Scanner in = new Scanner(new File(fileName))) {
            while (in.hasNextLine()) {
                String line = in.nextLine().strip();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts    = line.split(";");
                String   location = parts[0].strip();
                list.add(new Job(location, col(location), row(location), Double.parseDouble(parts[1].strip())));
            }
        } catch (FileNotFoundException ex) {
            throw new IllegalStateException(String.format("Could not find '%s' in the working directory %s", fileName, new File("").getAbsolutePath()), ex);
        }
        return list.toArray(new Job[0]);
    }

    /**
     * @param cell a cell such as {@code "F12"}
     *
     * @return the column index, where column A is 0
     */
    static int col(String cell) {
        return cell.charAt(0) - 'A';
    }

    /**
     * @param cell a cell such as {@code "F12"}
     *
     * @return the row number, where the docking bays are row 0
     */
    static int row(String cell) {
        return Integer.parseInt(cell.substring(1));
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    /**
     * A field that needs water.
     *
     * @param location the cell, such as {@code "F12"}
     * @param col      the column index of the cell
     * @param row      the row number of the cell
     * @param litres   the litres the field needs
     */
    record Job(String location, int col, int row, double litres) {}

    /**
     * What became of one line of your script.
     */
    enum Status {
        DELIVERED,
        SHORT_OF_WATER,
        WASTED,
        CRASHED,
        DRONE_DOWN,
        UNKNOWN_DRONE,
        UNREADABLE
    }

    /**
     * One line of your script, the moment its outcome became known, and what that outcome was.
     *
     * @param time   seconds after the replay starts
     * @param line   the line exactly as your script wrote it
     * @param status the outcome
     * @param detail an explanation for the outcome, empty for a clean delivery
     */
    record LogEntry(double time, String line, Status status, String detail) {}

    /**
     * What a drone is doing at a particular moment.
     */
    enum Phase {
        DOCKED,
        FLYING,
        SPRAYING,
        HOVERING,
        FALLING,
        WRECKED
    }

    /**
     * Where a drone is at a particular moment, and what it is doing there.
     *
     * @param col      the column, with fractions while flying
     * @param row      the row, with fractions while flying
     * @param battery  the battery percentage
     * @param payload  the litres on board
     * @param phase    what the drone is doing
     * @param progress how far through the current phase the drone is, from 0 to 1
     */
    record DroneState(double col, double row, double battery, double payload, Phase phase, double progress) {}

    // A single flight: take-off, travel, then either a spray or a crash
    private record Flight(double start, double arrive, double end,
                          double fromCol, double fromRow, double toCol, double toRow,
                          boolean crashed,
                          double batteryBefore, double batteryAfter,
                          double payloadBefore, double payloadAfter) {}

    // Water reaching a field, spread over the spray
    private record WaterEvent(double start, double end, int job, double litres) {}

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final ReferenceDrone[]   drones;
    private final Job[]              jobs;
    private final List<List<Flight>> flights     = new ArrayList<>();
    private final List<WaterEvent>   waterEvents = new ArrayList<>();
    private final List<LogEntry>     log         = new ArrayList<>();

    private final double[] startCol;
    private final double[] startRow;
    private final double[] startBattery;
    private final double[] startPayload;
    private final double[] finalWater;

    private final int scriptLineCount;
    private       double clock = START_DELAY_SECONDS;  // when the next line of the script starts
    private       double endTime;
    private       int    maxCol;
    private       int    maxRow;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Replay(String script, ReferenceDrone[] drones, Job[] jobs) {
        this.drones = drones;
        this.jobs   = jobs;

        int n = drones.length;
        startCol     = new double[n];
        startRow     = new double[n];
        startBattery = new double[n];
        startPayload = new double[n];
        for (int d = 0; d < n; d++) {
            startCol[d]     = col(drones[d].getLocation());
            startRow[d]     = row(drones[d].getLocation());
            startBattery[d] = drones[d].getBatteryLife();
            startPayload[d] = drones[d].getPayload();
            flights.add(new ArrayList<>());
            maxCol = Math.max(maxCol, (int) startCol[d]);
        }
        for (Job job : jobs) {
            maxCol = Math.max(maxCol, job.col());
            maxRow = Math.max(maxRow, job.row());
        }

        boolean[] down    = new boolean[n];
        double[]  watered = new double[jobs.length];

        int lines = 0;
        for (String raw : script.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            lines++;
            playLine(line, down, watered);
        }
        scriptLineCount = lines;
        finalWater      = watered;
        endTime         = clock;
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    int droneCount() {
        return drones.length;
    }

    String name(int drone) {
        return drones[drone].getName();
    }

    String colourHex(int drone) {
        return drones[drone].getColourHex();
    }

    double startCol(int drone) {
        return startCol[drone];
    }

    double startRow(int drone) {
        return startRow[drone];
    }

    double startPayload(int drone) {
        return startPayload[drone];
    }

    Job[] jobs() {
        return jobs;
    }

    int scriptLineCount() {
        return scriptLineCount;
    }

    /**
     * @return the moment the last drone finishes, in seconds after the replay starts
     */
    double endTime() {
        return endTime;
    }

    /**
     * @return the highest column index any drone, job or flight reaches
     */
    int maxCol() {
        return maxCol;
    }

    /**
     * @return the highest row number any job or flight reaches
     */
    int maxRow() {
        return maxRow;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * @return {@code true} if every field has received all the water it needs
     */
    boolean passed() {
        return dryCount() == 0;
    }

    /**
     * @return the number of fields still short of water once every drone has finished
     */
    int dryCount() {
        int dry = 0;
        for (int j = 0; j < jobs.length; j++) {
            if (finalWater[j] < jobs[j].litres() - EPSILON) {
                dry++;
            }
        }
        return dry;
    }

    /**
     * @param job  the index of the job in {@link #jobs()}
     * @param time seconds after the replay starts
     *
     * @return the litres that field has received so far, rising smoothly while a drone sprays it
     */
    double wateredAt(int job, double time) {
        double total = 0;
        for (WaterEvent event : waterEvents) {
            if (event.job() != job || time <= event.start()) {
                continue;
            }
            double fraction = Math.min(1, (time - event.start()) / (event.end() - event.start()));
            total += event.litres() * fraction;
        }
        return total;
    }

    /**
     * @param time seconds after the replay starts
     *
     * @return every log entry whose outcome is known by {@code time}, earliest first
     */
    List<LogEntry> logUntil(double time) {
        List<LogEntry> visible = new ArrayList<>();
        for (LogEntry entry : log) {
            if (entry.time() > time) {
                break;
            }
            visible.add(entry);
        }
        return visible;
    }

    /**
     * Works out where a drone is and what it is doing at a given moment.
     *
     * @param drone the index of the drone
     * @param time  seconds after the replay starts
     *
     * @return the state of the drone at {@code time}
     */
    DroneState stateAt(int drone, double time) {
        double col     = startCol[drone];
        double row     = startRow[drone];
        double battery = startBattery[drone];
        double payload = startPayload[drone];
        Phase  phase   = Phase.DOCKED;

        for (Flight f : flights.get(drone)) {
            if (time < f.start()) {
                return new DroneState(col, row, battery, payload, phase, 0);
            }
            if (time < f.arrive()) {
                double p = (time - f.start()) / (f.arrive() - f.start());
                double e = ease(p);
                return new DroneState(lerp(f.fromCol(), f.toCol(), e), lerp(f.fromRow(), f.toRow(), e),
                                      lerp(f.batteryBefore(), f.batteryAfter(), p), f.payloadBefore(), Phase.FLYING, p);
            }
            if (time < f.end()) {
                double p = (time - f.arrive()) / (f.end() - f.arrive());
                if (f.crashed()) {
                    return new DroneState(f.toCol(), f.toRow(), 0, f.payloadBefore(), Phase.FALLING, p);
                }
                return new DroneState(f.toCol(), f.toRow(), f.batteryAfter(),
                                      lerp(f.payloadBefore(), f.payloadAfter(), p), Phase.SPRAYING, p);
            }
            col     = f.toCol();
            row     = f.toRow();
            battery = f.batteryAfter();
            payload = f.payloadAfter();
            phase   = f.crashed() ? Phase.WRECKED : Phase.HOVERING;
        }
        return new DroneState(col, row, battery, payload, phase, 0);
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private void playLine(String line, boolean[] down, double[] watered) {
        String[] parts = line.split(";");
        if (parts.length != 3) {
            reject(line, Status.UNREADABLE, "expected name;location;litres");
            return;
        }

        int drone = indexOf(parts[0].strip());
        if (drone < 0) {
            reject(line, Status.UNKNOWN_DRONE, String.format("no drone is called '%s'", parts[0].strip()));
            return;
        }

        String location = parts[1].strip();
        if (!CELL.matcher(location).matches()) {
            reject(line, Status.UNREADABLE, String.format("'%s' is not a grid cell", location));
            return;
        }

        double litres;
        try {
            litres = Double.parseDouble(parts[2].strip());
        } catch (NumberFormatException ex) {
            reject(line, Status.UNREADABLE, String.format("'%s' is not a number of litres", parts[2].strip()));
            return;
        }

        if (down[drone]) {
            reject(line, Status.DRONE_DOWN, String.format("%s has already crashed", drones[drone].getName()));
            return;
        }

        fly(drone, location, litres, line, down, watered);
    }

    // Logs a line that never flies, and gives it a short turn so it appears in script order
    private void reject(String line, Status status, String detail) {
        addLog(clock, line, status, detail);
        clock += REJECT_SECONDS;
    }

    private void fly(int drone, String location, double litres, String line, boolean[] down, double[] watered) {
        ReferenceDrone rd = drones[drone];

        double fromCol  = col(rd.getLocation());
        double fromRow  = row(rd.getLocation());
        double toCol    = col(location);
        double toRow    = row(location);
        double duration = Math.max(MIN_FLIGHT_SECONDS, Math.hypot(toCol - fromCol, toRow - fromRow) / CELLS_PER_SECOND);
        double start    = clock;

        maxCol = Math.max(maxCol, (int) toCol);
        maxRow = Math.max(maxRow, (int) toRow);

        double drain         = rd.calculateBatteryDrain(location);
        double batteryBefore = rd.getBatteryLife();
        double payloadBefore = rd.getPayload();

        if (batteryBefore - drain < -EPSILON) {
            // The battery empties partway, at the fraction of the trip it could pay for
            double reached = drain <= 0 ? 0 : clamp(batteryBefore / drain, 0.0, 1.0);
            double arrive  = start + reached * duration;

            flights.get(drone).add(new Flight(start, arrive, arrive + FALL_SECONDS,
                                              fromCol, fromRow, lerp(fromCol, toCol, reached), lerp(fromRow, toRow, reached),
                                              true, batteryBefore, 0, payloadBefore, payloadBefore));
            addLog(arrive, line, Status.CRASHED, String.format("battery ran out %.0f%% of the way to %s", reached * 100, location));

            down[drone] = true;
            clock       = arrive + FALL_SECONDS;
            return;
        }

        double released = Math.min(litres, Math.max(0, payloadBefore));
        rd.deliverPayload(location, litres);

        double arrive = start + duration;
        double end    = arrive + SPRAY_SECONDS;
        flights.get(drone).add(new Flight(start, arrive, end, fromCol, fromRow, toCol, toRow, false,
                                          batteryBefore, Math.max(0, rd.getBatteryLife()),
                                          payloadBefore, Math.max(0, rd.getPayload())));
        clock = end;

        int job = findOpenJob(location, litres, watered);
        if (job < 0) {
            addLog(arrive, line, Status.WASTED, String.format("no dry field at %s needs %s L", location, formatLitres(litres)));
            return;
        }

        if (released > EPSILON) {
            watered[job] += released;
            waterEvents.add(new WaterEvent(arrive, end, job, released));
        }

        if (released < litres - EPSILON) {
            addLog(arrive, line, Status.SHORT_OF_WATER, String.format("only %s L on board, the field needs %s L", formatLitres(released), formatLitres(litres)));
        } else {
            addLog(arrive, line, Status.DELIVERED, "");
        }
    }

    private int findOpenJob(String location, double litres, double[] watered) {
        for (int j = 0; j < jobs.length; j++) {
            Job job = jobs[j];
            if (job.location().equals(location)
                && Math.abs(job.litres() - litres) < EPSILON
                && watered[j] < job.litres() - EPSILON) {
                return j;
            }
        }
        return -1;
    }

    private int indexOf(String name) {
        for (int d = 0; d < drones.length; d++) {
            if (drones[d].getName().equals(name)) {
                return d;
            }
        }
        return -1;
    }

    private void addLog(double time, String line, Status status, String detail) {
        log.add(new LogEntry(time, line, status, detail));
    }

    static String formatLitres(double litres) {
        return litres == Math.rint(litres) ? String.format("%.0f", litres) : String.format("%.1f", litres);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    private static double ease(double p) {
        return p * p * (3 - 2 * p);
    }

}
