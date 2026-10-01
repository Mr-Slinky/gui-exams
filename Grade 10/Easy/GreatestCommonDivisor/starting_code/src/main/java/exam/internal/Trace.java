package exam.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The trace table for one call to your method.
 * <p>
 * A trace table has a column for each variable, a column for each test in a condition, and a row for each pass
 * through your code. The first row shows the values the parameters start with. After that, a cell is filled in
 * whenever a variable is given a value or a test is made. A new row starts each time your code goes back up to an
 * earlier line, which is what a loop does when it goes round again.
 *
 * <pre>
 * gcd(12, 18), counting down from small:
 *
 *        small  big   i   i > 1   big % i == 0   small % i == 0
 * start   12    18
 *   1                12     T          F
 *   2                11     T          F
 *   ...
 *   7                 6     T          T               T          ->  returns 6
 * </pre>
 *
 * The table is built as a list of steps, one for each cell, in the order your code filled them in. The window plays
 * those steps back one at a time.
 * <p>
 * A method that only passes its parameters on to another method gets no columns of its own, so the table shows the
 * method that does the work.
 */
final class Trace {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    static final int MAX_ROWS = 40;

    /**
     * Turns the events of one call into the steps of a trace table.
     *
     * @param events  everything the traced code reported, in order
     * @param result  the value the call returned, or {@code null} if it failed
     * @param failure the reason the call failed, or {@code null} if it returned
     *
     * @return the finished trace table
     */
    static Trace build(List<Probe.Event> events, Object result, Throwable failure) {
        return new Trace(events, result, failure);
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    /**
     * One column of the table.
     *
     * @param heading     the name of a variable, or the text of a test or a whole condition
     * @param isCondition {@code true} for a column that shows the answer to a test or a condition
     */
    record Column(String heading, boolean isCondition) {}

    /**
     * One cell being filled in.
     *
     * @param line   the line of the source file that filled the cell, or -1 for the value a parameter starts with
     * @param row    the index of the row, where row 0 is the first
     * @param column the index in {@link #columns()} of the column
     * @param value  the text written in the cell
     */
    record Step(int line, int row, int column, String value) {}

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final List<Column> columns = new ArrayList<>();
    private final List<Step>   steps   = new ArrayList<>();
    private final Object       result;
    private final Throwable    failure;

    private int     rowCount;
    private int     startSteps;
    private int     lastLine   = -1;
    private int     returnLine = -1;
    private boolean isCutShort;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Trace(List<Probe.Event> events, Object result, Throwable failure) {
        this.result  = result;
        this.failure = failure;

        // A method that never fills a cell only passes values along, so its parameters stay out of the table
        var working = new HashSet<String>();
        var stack   = new ArrayDeque<String>();
        for (Probe.Event event : events) {
            switch (event) {
                case Probe.Enter enter -> stack.push(enter.method());
                case Probe.Exit _ -> stack.poll();
                case Probe.Set _, Probe.Answer _ -> working.add(stack.peek());
                default -> { }
            }
        }

        var keys    = new HashMap<String, Integer>();   // what a column stands for -> column index
        var owners  = new ArrayList<String>();          // the method each column belongs to
        var calls   = new ArrayDeque<Call>();
        var builder = new RowBuilder();

        for (Probe.Event event : events) {
            switch (event) {
                case Probe.Enter enter -> calls.push(new Call(enter.method()));
                case Probe.Exit _ -> {
                    var call = calls.poll();
                    if (call != null && working.contains(call.method)) {
                        returnLine = call.line;
                    }
                }
                case Probe.Line line -> {
                    lastLine = line.line();
                    var call = calls.peek();
                    if (call != null) {
                        // Going back up to an earlier line means a loop has gone round, so the next cell starts a row
                        if (line.line() < call.line) {
                            builder.breakBeforeNext = true;
                        }
                        call.line = line.line();
                    }
                }
                case Probe.Param param -> {
                    var call = calls.peek();
                    if (working.isEmpty() || working.contains(call.method)) {
                        int column = findColumn(keys, owners, call.method, param.name(), param.name(), false);
                        builder.put(column, param.value(), true, -1);
                    }
                }
                case Probe.Set set -> {
                    var call   = calls.peek();
                    int column = findColumn(keys, owners, call.method, set.name(), set.name(), false);
                    builder.put(column, set.value(), false, call.line);
                }
                case Probe.Answer answer -> fillTest(keys, owners, calls.peek(), builder, answer);
            }
        }

        // Two methods can each have a variable called i, so a repeated name is shown with its method in front
        var headings = columns.stream().map(Column::heading).toList();
        for (int c = 0; c < columns.size(); c++) {
            var column = columns.get(c);
            if (!column.isCondition() && headings.indexOf(column.heading()) != headings.lastIndexOf(column.heading())) {
                columns.set(c, new Column(owners.get(c) + "." + column.heading(), false));
            }
        }
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    /**
     * @return the columns, in the order your code first used them
     */
    List<Column> columns() {
        return columns;
    }

    /**
     * @return every cell that was filled in, in order, starting with the values the parameters start with
     */
    List<Step> steps() {
        return steps;
    }

    /**
     * @return the number of steps at the front of {@link #steps()} that fill in the start row
     */
    int startSteps() {
        return startSteps;
    }

    int rowCount() {
        return rowCount;
    }

    /**
     * @return the value the call returned, or {@code null} if it failed
     */
    Object result() {
        return result;
    }

    /**
     * @return the reason the call failed, or {@code null} if it returned
     */
    Throwable failure() {
        return failure;
    }

    /**
     * @return the last line of the source file that ran, or -1 if the traced code reported none
     */
    int lastLine() {
        return lastLine;
    }

    /**
     * @return the line your method returned from, or the last line that ran if it failed
     */
    int returnLine() {
        return failure == null && returnLine > 0 ? returnLine : lastLine;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * @return {@code true} if row 0 shows the values the parameters start with
     */
    boolean hasStartRow() {
        return startSteps > 0;
    }

    /**
     * @return the number of rows the code filled in after the start row
     */
    int passCount() {
        return hasStartRow() ? rowCount - 1 : rowCount;
    }

    /**
     * @return {@code true} if the table has a column for at least one condition
     */
    boolean hasConditions() {
        return columns.stream().anyMatch(Column::isCondition);
    }

    /**
     * @return {@code true} if the table stopped at {@link #MAX_ROWS} rows while the code was still running
     */
    boolean isCutShort() {
        return isCutShort;
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    // Fills the cell for one test, then the cell for its whole condition once the tests so far settle it
    private void fillTest(Map<String, Integer> keys, List<String> owners, Call call, RowBuilder builder, Probe.Answer answer) {
        var tested    = Probe.testOf(answer.id());
        var condition = tested.condition();
        var where     = "line " + tested.line() + ": ";

        // The columns of a condition are made together, so its tests sit side by side with the whole condition last
        int tests = condition.tests().size();
        for (int t = 0; t < tests; t++) {
            findColumn(keys, owners, call.method, where + condition.text() + " #" + t, condition.tests().get(t).text(), true);
        }
        int wholeColumn = tests < 2 ? -1 : findColumn(keys, owners, call.method, where + condition.text(), condition.text(), true);

        int column = findColumn(keys, owners, call.method, where + condition.text() + " #" + tested.index(), tested.test().text(), true);
        builder.put(column, formatAnswer(answer.value()), false, tested.line());

        if (tests < 2) {
            return;
        }
        // The first test of a condition always runs first, so it marks the start of a fresh pass through the condition
        if (tested.index() == 0 || call.condition != condition) {
            call.answers.clear();
            call.condition = condition;
        }
        call.answers.put(tested.index(), answer.value());

        Boolean whole = condition.evaluate(call.answers);
        if (whole != null) {
            builder.put(wholeColumn, formatAnswer(whole), false, tested.line());
            call.answers.clear();
        }
    }

    private int findColumn(Map<String, Integer> keys, List<String> owners, String method, String key, String heading, boolean isCondition) {
        return keys.computeIfAbsent(method + "\n" + isCondition + "\n" + key, _ -> {
            columns.add(new Column(heading, isCondition));
            owners.add(method);
            return columns.size() - 1;
        });
    }

    private static String formatAnswer(boolean answer) {
        return answer ? "T" : "F";
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\
    // One method that is running: the line it last reported, and the condition it is part-way through
    private static final class Call {

        private final String                method;
        private final Map<Integer, Boolean> answers = new HashMap<>();

        private int       line = -1;
        private Condition condition;

        Call(String method) {
            this.method = method;
        }
    }

    // Decides which row each cell belongs in
    private final class RowBuilder {

        private final Map<Integer, String> cells = new LinkedHashMap<>();   // the cells of the newest row

        private boolean isStart;
        private boolean breakBeforeNext;

        void put(int column, String value, boolean isParameter, int line) {
            boolean startsRow = rowCount == 0
                                || breakBeforeNext
                                || cells.containsKey(column)
                                || (isStart && !isParameter);
            if (startsRow) {
                if (rowCount >= MAX_ROWS) {
                    isCutShort = true;
                    return;
                }
                cells.clear();
                isStart = isParameter && rowCount == 0;
                rowCount++;
            }
            breakBeforeNext = false;
            cells.put(column, value);
            if (isStart) {
                startSteps++;
            }
            steps.add(new Step(line, rowCount - 1, column, value));
        }
    }

}
