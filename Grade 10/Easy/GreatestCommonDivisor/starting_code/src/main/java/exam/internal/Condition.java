package exam.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A condition from your source code, split into the tests it is built from.
 * <p>
 * The condition of an {@code if}, a {@code while} or a {@code for} is one or more tests joined by {@code &&} and
 * {@code ||}. A trace table gives each test a column of its own, and a condition built from several tests gets one
 * more column for the whole of it:
 *
 * <pre>
 * if (big % i == 0 &amp;&amp; small % i == 0)
 *
 *   ->  big % i == 0
 *   ->  small % i == 0
 *   ->  big % i == 0 &amp;&amp; small % i == 0
 * </pre>
 *
 * Java stops working through a condition as soon as the answer is certain. With {@code &&}, a first test that is
 * false settles the whole condition, so the second test never runs and its cell stays empty.
 */
final class Condition {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    private static final Pattern KEYWORD = Pattern.compile("\\b(if|while|for)\\s*\\(");

    // The two-character symbols come first, so "<=" is never read as "<"
    private static final String[]   SYMBOLS   = {"==", "!=", "<=", ">=", "<", ">"};
    private static final Operator[] OPERATORS = {Operator.EQUAL, Operator.NOT_EQUAL, Operator.LESS_OR_EQUAL, Operator.GREATER_OR_EQUAL, Operator.LESS, Operator.GREATER};

    /**
     * Finds every condition on one line of source code, from left to right.
     *
     * <pre>
     * "for (int i = small; i > 1; i--) {"            ->  [i > 1]
     * "if (big % i == 0 &amp;&amp; small % i == 0) return i;"  ->  [big % i == 0 &amp;&amp; small % i == 0]
     * "int temp = big;"                              ->  []
     * </pre>
     *
     * @param line one line of a Java source file
     *
     * @return the conditions on the line, empty when it has none
     */
    static List<Condition> parseLine(String line) {
        List<Condition> conditions = new ArrayList<>();
        String  code    = stripComment(line);
        Matcher matcher = KEYWORD.matcher(code);
        while (matcher.find()) {
            int open  = matcher.end() - 1;
            int close = findClosingBracket(code, open);
            if (close < 0) {
                continue;
            }
            String inside = code.substring(open + 1, close);
            if (matcher.group(1).equals("for")) {
                // for (start; condition; step) keeps its condition between the two semicolons
                List<String> parts = splitTopLevel(inside, ";");
                inside = parts.size() == 3 ? parts.get(1) : "";
            }
            if (!inside.isBlank()) {
                conditions.add(new Condition(inside.strip()));
            }
        }
        return conditions;
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    /**
     * The comparison a test makes between the value on its left and the value on its right.
     */
    enum Operator {
        EQUAL,
        NOT_EQUAL,
        LESS,
        LESS_OR_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        /** A test with no comparison in it, such as a boolean variable, which is true when its value is not 0. */
        IS_TRUE;

        boolean test(long left, long right) {
            return switch (this) {
                case EQUAL -> left == right;
                case NOT_EQUAL -> left != right;
                case LESS -> left < right;
                case LESS_OR_EQUAL -> left <= right;
                case GREATER -> left > right;
                case GREATER_OR_EQUAL -> left >= right;
                case IS_TRUE -> left != 0;
            };
        }
    }

    /**
     * One test inside a condition.
     *
     * @param text      the test as the source code writes it, such as {@code "big % i == 0"}
     * @param operator  the comparison the test makes
     * @param isNegated {@code true} if a {@code !} in front of the test flips its answer
     */
    record Test(String text, Operator operator, boolean isNegated) {

        /**
         * Works out the answer to this test from the two values the running code compared.
         *
         * @param left  the value on the left of the comparison
         * @param right the value on the right, which is 0 for a test with no comparison in it
         *
         * @return the answer the test gives for those values
         */
        boolean evaluate(long left, long right) {
            return operator.test(left, right) != isNegated;
        }
    }

    // How the tests of a condition combine: a single test, a negation, or tests joined by && or by ||
    private sealed interface Node permits Leaf, Not, All, Any {}

    private record Leaf(int index) implements Node {}

    private record Not(Node inner) implements Node {}

    private record All(List<Node> parts) implements Node {}

    private record Any(List<Node> parts) implements Node {}

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final String     text;
    private final List<Test> tests = new ArrayList<>();
    private final Node       root;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Condition(String text) {
        this.text = text;
        this.root = parse(text);
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    /**
     * @return the whole condition as the source code writes it
     */
    String text() {
        return text;
    }

    /**
     * @return the tests the condition is built from, in the order Java works through them
     */
    List<Test> tests() {
        return tests;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * Works out the answer to the whole condition from the tests that have run so far.
     *
     * <pre>
     * a &amp;&amp; b, with a = false             ->  false (b never runs)
     * a &amp;&amp; b, with a = true              ->  null  (b is still to run)
     * a &amp;&amp; b, with a = true, b = true    ->  true
     * </pre>
     *
     * @param answers the answer of each test that has run, by its index in {@link #tests()}
     *
     * @return the answer to the condition, or {@code null} while it depends on a test that has yet to run
     */
    Boolean evaluate(Map<Integer, Boolean> answers) {
        return evaluate(root, answers);
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private static Boolean evaluate(Node node, Map<Integer, Boolean> answers) {
        return switch (node) {
            case Leaf leaf -> answers.get(leaf.index());
            case Not not -> {
                Boolean inner = evaluate(not.inner(), answers);
                yield inner == null ? null : !inner;
            }
            case All all -> {
                for (Node part : all.parts()) {
                    Boolean answer = evaluate(part, answers);
                    if (answer == null || !answer) {
                        yield answer;
                    }
                }
                yield true;
            }
            case Any any -> {
                for (Node part : any.parts()) {
                    Boolean answer = evaluate(part, answers);
                    if (answer == null || answer) {
                        yield answer;
                    }
                }
                yield false;
            }
        };
    }

    // || binds loosest, then &&, then a ! or a pair of brackets around a smaller condition
    private Node parse(String expression) {
        String trimmed = expression.strip();

        List<String> alternatives = splitTopLevel(trimmed, "||");
        if (alternatives.size() > 1) {
            return new Any(alternatives.stream().map(this::parse).toList());
        }
        List<String> requirements = splitTopLevel(trimmed, "&&");
        if (requirements.size() > 1) {
            return new All(requirements.stream().map(this::parse).toList());
        }

        boolean joinsTests = trimmed.contains("&&") || trimmed.contains("||");
        if (joinsTests && isWrappedInBrackets(trimmed)) {
            return parse(trimmed.substring(1, trimmed.length() - 1));
        }
        if (joinsTests && trimmed.startsWith("!") && isWrappedInBrackets(trimmed.substring(1).strip())) {
            String inner = trimmed.substring(1).strip();
            return new Not(parse(inner.substring(1, inner.length() - 1)));
        }

        tests.add(parseTest(trimmed));
        return new Leaf(tests.size() - 1);
    }

    private static Test parseTest(String text) {
        String  body      = text;
        boolean isNegated = false;
        while (true) {
            if (body.startsWith("!") && !body.startsWith("!=")) {
                isNegated = !isNegated;
                body      = body.substring(1).strip();
            } else if (isWrappedInBrackets(body)) {
                body = body.substring(1, body.length() - 1).strip();
            } else {
                break;
            }
        }
        return new Test(text, findOperator(body), isNegated);
    }

    // The comparison outside every pair of brackets is the one the test turns on
    private static Operator findOperator(String body) {
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                for (int op = 0; op < SYMBOLS.length; op++) {
                    if (body.startsWith(SYMBOLS[op], i)) {
                        return OPERATORS[op];
                    }
                }
            }
        }
        return Operator.IS_TRUE;
    }

    private static boolean isWrappedInBrackets(String text) {
        return text.startsWith("(") && findClosingBracket(text, 0) == text.length() - 1;
    }

    // Returns the index of the bracket that closes the one at 'open', or -1 if the line ends first
    private static int findClosingBracket(String text, int open) {
        int     depth    = 0;
        boolean inString = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString && c == '(') {
                depth++;
            } else if (!inString && c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    // Splits on a separator wherever it appears outside every pair of brackets
    private static List<String> splitTopLevel(String text, String separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && text.startsWith(separator, i)) {
                parts.add(text.substring(start, i));
                start = i + separator.length();
                i     = start - 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static String stripComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString && c == '/' && line.charAt(i + 1) == '/') {
                return line.substring(0, i);
            }
        }
        return line;
    }

}
