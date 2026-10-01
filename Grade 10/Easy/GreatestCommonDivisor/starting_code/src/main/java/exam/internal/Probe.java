package exam.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Records what your code does while it runs.
 * <p>
 * Before the window calls your method, a {@link Tracer} makes a copy of your compiled class and adds a call to this
 * class after every step: each time a method starts, a variable is given a value, a condition is tested, a new line
 * begins, a loop goes round again, or a method returns. Your own file stays exactly as you wrote it. The calls land
 * here as a list of events, and a {@link Trace} turns that list into the steps of a trace table.
 *
 * <pre>
 * int temp = big;     ->  set("temp", "18")
 * big = small % big;  ->  set("big", "12")
 * while (big != 0)    ->  test(12, id), which records the answer true
 * </pre>
 *
 * The class is public because the copy of your class calls it from outside this package.
 */
public final class Probe {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    static final int MAX_LOOPS  = 500;
    static final int MAX_EVENTS = 50_000;

    private static final List<Event>  EVENTS = new ArrayList<>();
    private static final List<Tested> TESTS  = new ArrayList<>();   // every test a traced class makes, by its id

    private static int loops;

    /**
     * Records the answer to a test that compares two whole numbers, such as {@code i > 1}.
     *
     * @param left  the value on the left of the comparison
     * @param right the value on the right of the comparison
     * @param id    the id {@link #register(Tested)} gave the test
     */
    public static void test(int left, int right, int id) {
        add(new Answer(id, TESTS.get(id).test().evaluate(left, right)));
    }

    /**
     * Records the answer to a test that compares one value with 0, such as {@code big % i == 0}.
     *
     * @param value the value compared with 0
     * @param id    the id {@link #register(Tested)} gave the test
     */
    public static void test(int value, int id) {
        test(value, 0, id);
    }

    /**
     * Records the answer to a test that asks whether two objects are the same object.
     *
     * @param left  the object on the left of the comparison
     * @param right the object on the right of the comparison
     * @param id    the id {@link #register(Tested)} gave the test
     */
    public static void testSame(Object left, Object right, int id) {
        test(left == right ? 0 : 1, 0, id);
    }

    /**
     * Records the answer to a test that compares an object with {@code null}.
     *
     * @param value the object compared with {@code null}
     * @param id    the id {@link #register(Tested)} gave the test
     */
    public static void testNull(Object value, int id) {
        test(value == null ? 0 : 1, 0, id);
    }

    /**
     * Gives a test an id, which the traced class passes back each time the test runs.
     *
     * @param tested the test and where it is written
     *
     * @return the id of the test
     */
    static int register(Tested tested) {
        TESTS.add(tested);
        return TESTS.size() - 1;
    }

    /**
     * @param id an id that {@link #register(Tested)} returned
     *
     * @return the test with that id
     */
    static Tested testOf(int id) {
        return TESTS.get(id);
    }

    /**
     * Records that a method has started.
     *
     * @param method the name of the method
     */
    public static void enter(String method) {
        add(new Enter(method));
    }

    /**
     * Records the value a parameter has when its method starts.
     *
     * @param name  the name of the parameter
     * @param value the value, as text
     */
    public static void param(String name, String value) {
        add(new Param(name, value));
    }

    /**
     * Records that a variable has been given a value.
     *
     * @param name  the name of the variable
     * @param value the new value, as text
     */
    public static void set(String name, String value) {
        add(new Set(name, value));
    }

    /**
     * Records that the code on a new line of the source file is about to run.
     *
     * @param line the line number in the source file
     */
    public static void line(int line) {
        add(new Line(line));
    }

    /**
     * Records that a loop is about to go round again.
     *
     * @throws Runaway if loops have gone round more than {@link #MAX_LOOPS} times in total
     */
    public static void loop() {
        loops++;
        if (loops > MAX_LOOPS) {
            throw new Runaway(String.format("still looping after %d passes", MAX_LOOPS));
        }
    }

    /**
     * Records that a method is about to return.
     *
     * @param value the value it returns, as text, or {@code null} for a method that returns nothing
     */
    public static void exit(String value) {
        add(new Exit(value));
    }

    static void reset() {
        EVENTS.clear();
        loops = 0;
    }

    /**
     * @return a copy of every event recorded since the last {@link #reset()}, in the order they happened
     */
    static List<Event> events() {
        return new ArrayList<>(EVENTS);
    }

    private static void add(Event event) {
        if (EVENTS.size() >= MAX_EVENTS) {
            throw new Runaway(String.format("still running after %d steps", MAX_EVENTS));
        }
        EVENTS.add(event);
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    /**
     * One thing your code did.
     */
    sealed interface Event permits Enter, Param, Set, Answer, Line, Exit {}

    record Answer(int id, boolean value) implements Event {}

    /**
     * One test of a condition, and where it is written.
     *
     * @param method    the method the test is in
     * @param line      the line of the source file the test is on
     * @param condition the condition the test belongs to
     * @param index     the position of the test within that condition, where the first test is 0
     */
    record Tested(String method, int line, Condition condition, int index) {

        Condition.Test test() {
            return condition.tests().get(index);
        }
    }

    record Enter(String method) implements Event {}

    record Param(String name, String value) implements Event {}

    record Set(String name, String value) implements Event {}

    record Line(int line) implements Event {}

    record Exit(String value) implements Event {}

    /**
     * Stops code that shows no sign of finishing. It is an {@code Error}, so a {@code catch (Exception e)} in the
     * traced code lets it through.
     */
    static final class Runaway extends Error {

        Runaway(String message) {
            super(message);
        }
    }

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Probe() {
    }

}
