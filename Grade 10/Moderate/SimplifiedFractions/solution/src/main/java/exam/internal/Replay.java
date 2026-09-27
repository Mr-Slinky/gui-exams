package exam.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out, ahead of time, everything that happens while your list plays.
 * <p>
 * Before a single frame is drawn, the replay reads every fraction in your list and decides three things for each
 * one: which table it belongs in, which row of that table it fills, and whether it is right. The display then only
 * has to ask "what is happening at 3.2 seconds?" and draw the answer.
 * <p>
 * The replay plays your fractions from the smallest denominator to the largest, and from the smallest numerator to
 * the largest within a denominator. The wall therefore fills from the top down, whatever order your list is in:
 *
 * <pre>
 * ["2/3", "1/2", "2/4", "1/3"]  plays as  1/2, 1/3, 2/3, 2/4
 * </pre>
 *
 * Text that is not a fraction plays last, and the outlines left empty after that are marked as missing.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-09-27
 * @since 2.0.0
 */
final class Replay {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    static final int MIN_N = 2;
    static final int MAX_N = 10;

    static final double START_DELAY_SECONDS = 0.8;
    static final double FILL_SECONDS        = 0.45;
    static final double PAUSE_SECONDS       = 0.15;
    static final double HOLD_SECONDS        = 0.35;
    static final double SLIDE_SECONDS       = 0.7;
    static final double LAND_SECONDS        = 0.45;
    static final double STAY_SECONDS        = 0.6;
    static final double REJECT_SECONDS      = 0.5;
    static final double REJECT_ALL_SECONDS  = 8.0;   // the longest all the unreadable strings together may take
    static final double MISSING_SECONDS     = 0.3;

    // The numerators each table ends up with once a list is right, indexed by denominator. The display draws these
    // as empty outlines before the walk starts, so a fraction left out of a list shows up as an outline left empty.
    private static final int[][] WALL = {
        {},
        {},
        {1},
        {1, 2},
        {1, 3},
        {1, 2, 3, 4},
        {1, 5},
        {1, 2, 3, 4, 5, 6},
        {1, 3, 5, 7},
        {1, 2, 4, 5, 7, 8},
        {1, 3, 7, 9}
    };

    private static final String[] TABLE_NAMES = {
        "", "Wholes", "Halves", "Thirds", "Quarters", "Fifths", "Sixths", "Sevenths", "Eighths", "Ninths", "Tenths"
    };

    /**
     * Plays a list of fractions against the wall for {@code n}.
     *
     * @param n         the largest denominator on the wall, from {@link #MIN_N} to {@link #MAX_N}
     * @param fractions the list to play, which may contain null elements
     *
     * @return the finished replay, ready to be drawn at any moment in time
     *
     * @throws IllegalArgumentException if {@code n} is outside {@link #MIN_N} to {@link #MAX_N}
     */
    static Replay run(int n, List<String> fractions) {
        if (!isValidN(n)) {
            throw new IllegalArgumentException(String.format("n must be from %d to %d, but was %d", MIN_N, MAX_N, n));
        }
        return new Replay(n, fractions);
    }

    /**
     * @param n the largest denominator a learner asked for
     *
     * @return {@code true} if the wall can be drawn for {@code n}
     */
    static boolean isValidN(int n) {
        return n >= MIN_N && n <= MAX_N;
    }

    /**
     * @param denominator a denominator of 1 or more
     *
     * @return the name of the table for that denominator, such as {@code "Quarters"} for 4
     */
    static String nameOf(int denominator) {
        if (denominator < TABLE_NAMES.length) {
            return TABLE_NAMES[denominator];
        }
        return String.format("%dths", denominator);
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    /**
     * A fraction read from a learner's list. The numerator can be zero, negative or larger than the denominator, and
     * the denominator is always 1 or more.
     *
     * @param numerator   the number above the line
     * @param denominator the number below the line
     */
    record Fraction(int numerator, int denominator) {

        // Two fractions are the same size when cross-multiplying gives the same number on both sides
        boolean isSameSizeAs(Fraction other) {
            return (long) numerator * other.denominator == (long) other.numerator * denominator;
        }

        @Override
        public String toString() {
            return numerator + "/" + denominator;
        }
    }

    /**
     * What became of one fraction in a list, or of one outline the list left empty.
     */
    enum Status {
        ON_WALL,
        NOT_SIMPLIFIED,
        REPEATED,
        WHOLE_OR_MORE,
        ZERO_OR_LESS,
        OFF_THE_WALL,
        UNREADABLE,
        MISSING
    }

    /**
     * One table of the wall. A wall table has a denominator from 2 to {@code n}; any other table holds fractions
     * whose denominator falls outside the wall, and is drawn in red below it.
     *
     * @param denominator the number of columns
     * @param onWall      {@code true} for a table of the wall itself
     * @param firstRow    the index in {@link #rows()} of the first row of this table
     * @param lastRow     the index in {@link #rows()} of the last row of this table
     */
    record Table(int denominator, boolean onWall, int firstRow, int lastRow) {}

    /**
     * One row of a table.
     *
     * @param table    the index in {@link #tables()} of the table the row belongs to
     * @param fraction the fraction the row is drawn for
     * @param outline  {@code true} for an outline the wall draws before the walk starts, {@code false} for a row
     *                 added for a wrong fraction in the list
     */
    record Row(int table, Fraction fraction, boolean outline) {}

    /**
     * One turn of the walk: a fraction from the list, or an outline the list left empty.
     *
     * @param text   the fraction exactly as the list wrote it
     * @param status the outcome
     * @param detail an explanation for the outcome, empty for a correct fraction
     * @param row    the index in {@link #rows()} the turn fills, or -1 for text that is not a fraction
     * @param target the index in {@link #rows()} the filled row slides onto, or -1 when it stays where it is
     * @param start  seconds after the replay starts
     */
    record Step(String text, Status status, String detail, int row, int target, double start) {

        boolean isCorrect() {
            return status == Status.ON_WALL;
        }

        // The moment the list on the right shows this turn
        double shownAt() {
            return status == Status.UNREADABLE || status == Status.MISSING ? start : start + FILL_SECONDS;
        }

        // Each of these runs from 0 before its part of the turn to 1 after it
        double fillProgress(double time) {
            return clamp01((time - start) / FILL_SECONDS);
        }

        double stayProgress(double time) {
            return clamp01((time - start - FILL_SECONDS) / STAY_SECONDS);
        }

        double slideProgress(double time) {
            return clamp01((time - slideStart()) / SLIDE_SECONDS);
        }

        double landProgress(double time) {
            return clamp01((time - slideStart() - SLIDE_SECONDS) / LAND_SECONDS);
        }

        double missingProgress(double time) {
            return clamp01((time - start) / (MISSING_SECONDS * 2));
        }

        private double slideStart() {
            return start + FILL_SECONDS + HOLD_SECONDS;
        }
    }

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final int          n;
    private final int          entryCount;
    private final int          readableCount;
    private final double       rejectSeconds;
    private final List<Table>  tables = new ArrayList<>();
    private final List<Row>    rows   = new ArrayList<>();
    private final List<Step>   steps  = new ArrayList<>();
    private final Set<Integer> filled = new HashSet<>();
    private final Step[]       rowSteps;
    private final double       endTime;

    private double clock = START_DELAY_SECONDS;  // when the next turn starts

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Replay(int n, List<String> fractions) {
        this.n          = n;
        this.entryCount = fractions.size();

        List<Entry> readable   = new ArrayList<>();
        List<Entry> unreadable = new ArrayList<>();
        for (String text : fractions) {
            Entry entry = parse(text);
            if (entry.fraction() == null) {
                unreadable.add(entry);
            } else {
                readable.add(entry);
            }
        }
        this.readableCount = readable.size();

        // A long list of unreadable strings plays faster, so the wall still reaches its verdict
        this.rejectSeconds = unreadable.isEmpty() ? REJECT_SECONDS : Math.min(REJECT_SECONDS, REJECT_ALL_SECONDS / unreadable.size());

        // Wall denominators first, so the wall fills from the top and any extra tables go below it
        readable.sort(Comparator.comparingInt((Entry e) -> isOnWall(e.fraction().denominator()) ? 0 : 1)
                                .thenComparingInt(e -> e.fraction().denominator())
                                .thenComparingInt(e -> e.fraction().numerator()));

        int next = 0;
        for (int d = MIN_N; d <= n; d++) {
            int table = tables.size();
            int first = rows.size();
            for (int numerator : WALL[d]) {
                rows.add(new Row(table, new Fraction(numerator, d), true));
            }
            while (next < readable.size() && readable.get(next).fraction().denominator() == d) {
                playOnWall(readable.get(next), table, first);
                next++;
            }
            tables.add(new Table(d, true, first, rows.size() - 1));
        }

        while (next < readable.size()) {
            int d     = readable.get(next).fraction().denominator();
            int table = tables.size();
            int first = rows.size();
            while (next < readable.size() && readable.get(next).fraction().denominator() == d) {
                playOffWall(readable.get(next), table);
                next++;
            }
            tables.add(new Table(d, false, first, rows.size() - 1));
        }

        for (Entry entry : unreadable) {
            addStep(entry.text(), Status.UNREADABLE, entry.problem(), -1, -1);
        }

        // An empty list means the method has yet to be written, so nothing counts as missing
        if (!fractions.isEmpty()) {
            for (int r = 0; r < rows.size(); r++) {
                Row row = rows.get(r);
                if (row.outline() && !filled.contains(r)) {
                    addStep(row.fraction().toString(), Status.MISSING, "missing from your list", r, -1);
                }
            }
        }

        rowSteps = new Step[rows.size()];
        for (Step step : steps) {
            if (step.row() >= 0) {
                rowSteps[step.row()] = step;
            }
        }
        endTime = clock;
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    int n() {
        return n;
    }

    /**
     * @return the number of elements in the list, readable or not
     */
    int entryCount() {
        return entryCount;
    }

    /**
     * @return the number of elements in the list that are written as a fraction, right or wrong
     */
    int readableCount() {
        return readableCount;
    }

    List<Table> tables() {
        return tables;
    }

    List<Row> rows() {
        return rows;
    }

    List<Step> steps() {
        return steps;
    }

    /**
     * @param row the index in {@link #rows()}
     *
     * @return the turn that fills that row, or {@code null} for an outline when the list is empty
     */
    Step stepAt(int row) {
        return rowSteps[row];
    }

    /**
     * @return the moment the last turn finishes, in seconds after the replay starts
     */
    double endTime() {
        return endTime;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * @return {@code true} if the list filled every outline and every fraction in it was correct
     */
    boolean passed() {
        return entryCount > 0 && wrongCount() == 0 && missingCount() == 0;
    }

    /**
     * @return the number of elements in the list that are wrong in any way
     */
    int wrongCount() {
        int wrong = 0;
        for (Step step : steps) {
            if (!step.isCorrect() && step.status() != Status.MISSING) {
                wrong++;
            }
        }
        return wrong;
    }

    /**
     * @return the number of outlines the list left empty
     */
    int missingCount() {
        int missing = 0;
        for (Step step : steps) {
            if (step.status() == Status.MISSING) {
                missing++;
            }
        }
        return missing;
    }

    /**
     * @param table the index in {@link #tables()}
     *
     * @return the moment the table first appears: 0 for a wall table, or the start of its first turn otherwise
     */
    double tableAppearsAt(int table) {
        if (tables.get(table).onWall()) {
            return 0;
        }
        double first = Double.MAX_VALUE;
        for (Step step : steps) {
            if (step.row() >= 0 && rows.get(step.row()).table() == table) {
                first = Math.min(first, step.start());
            }
        }
        return first;
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private boolean isOnWall(int denominator) {
        return denominator >= MIN_N && denominator <= n;
    }

    // A fraction whose denominator has a table on the wall
    private void playOnWall(Entry entry, int table, int firstRow) {
        Fraction fraction  = entry.fraction();
        int      numerator = fraction.numerator();

        if (numerator >= fraction.denominator()) {
            String detail = numerator == fraction.denominator()
                    ? String.format("%s is one whole", fraction)
                    : String.format("%s is more than one whole", fraction);
            addRow(entry, table, Status.WHOLE_OR_MORE, detail, -1);
            return;
        }
        if (numerator <= 0) {
            String detail = numerator == 0 ? String.format("%s is zero", fraction) : String.format("%s is less than zero", fraction);
            addRow(entry, table, Status.ZERO_OR_LESS, detail, -1);
            return;
        }

        int outline = findOutline(firstRow, numerator);
        if (outline < 0) {
            int same = findSameSize(fraction);
            addRow(entry, table, Status.NOT_SIMPLIFIED, String.format("%s simplifies to %s", fraction, rows.get(same).fraction()), same);
        } else if (filled.contains(outline)) {
            addRow(entry, table, Status.REPEATED, String.format("%s is already on the wall", fraction), outline);
        } else {
            filled.add(outline);
            addStep(entry.text(), Status.ON_WALL, "", outline, -1);
        }
    }

    private void playOffWall(Entry entry, int table) {
        String detail = entry.fraction().denominator() == 1
                ? "the wall starts at halves"
                : String.format("the wall stops at %s when n is %d", nameOf(n).toLowerCase(), n);
        addRow(entry, table, Status.OFF_THE_WALL, detail, -1);
    }

    // Adds a row of its own for a wrong fraction, below the outlines of its table
    private void addRow(Entry entry, int table, Status status, String detail, int target) {
        rows.add(new Row(table, entry.fraction(), false));
        addStep(entry.text(), status, detail, rows.size() - 1, target);
    }

    private void addStep(String text, Status status, String detail, int row, int target) {
        steps.add(new Step(text, status, detail, row, target, clock));
        clock += durationOf(status);
    }

    private int findOutline(int firstRow, int numerator) {
        for (int r = firstRow; r < rows.size(); r++) {
            Row row = rows.get(r);
            if (row.outline() && row.fraction().numerator() == numerator) {
                return r;
            }
        }
        return -1;
    }

    private int findSameSize(Fraction fraction) {
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            if (row.outline() && row.fraction().isSameSizeAs(fraction)) {
                return r;
            }
        }
        return -1;
    }

    private static Entry parse(String text) {
        if (text == null) {
            return new Entry("null", null, "the list contains null instead of a fraction");
        }
        if (text.contains(",") || text.contains("[") || text.contains("]")) {
            return new Entry(text, null, "add each fraction to the list on its own");
        }
        if (text.contains(".")) {
            return new Entry(text, null, "write a fraction such as 1/2 in place of a decimal");
        }
        if (text.chars().anyMatch(Character::isWhitespace)) {
            return new Entry(text, null, "write a fraction without spaces, such as 3/4");
        }
        String[] parts = text.split("/", -1);
        if (parts.length != 2) {
            return new Entry(text, null, "expected a numerator and a denominator, such as 3/4");
        }

        int numerator;
        int denominator;
        try {
            numerator   = Integer.parseInt(parts[0]);
            denominator = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return new Entry(text, null, "expected two whole numbers, such as 3/4");
        }
        if (denominator <= 0) {
            return new Entry(text, null, String.format("a table cannot have %d columns", denominator));
        }
        return new Entry(text, new Fraction(numerator, denominator), "");
    }

    private double durationOf(Status status) {
        return switch (status) {
            case ON_WALL -> FILL_SECONDS + PAUSE_SECONDS;
            case NOT_SIMPLIFIED, REPEATED -> FILL_SECONDS + HOLD_SECONDS + SLIDE_SECONDS + LAND_SECONDS;
            case WHOLE_OR_MORE, ZERO_OR_LESS, OFF_THE_WALL -> FILL_SECONDS + STAY_SECONDS;
            case UNREADABLE -> rejectSeconds;
            case MISSING -> MISSING_SECONDS;
        };
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\
    // One element of the list: the fraction it describes, or the reason it describes none
    private record Entry(String text, Fraction fraction, String problem) {}

}
