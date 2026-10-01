package exam;

import exam.internal.FractionWall;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every simplified fraction between 0 and 1 whose denominator is at most {@code n}, and shows the list on a
 * fraction wall.
 */
public class SimplifiedFractions {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    public static void main(String[] args) {
        int n = 10;
        FractionWall.show(n, simpFrac(n));
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * Lists every simplified fraction between 0 and 1 whose denominator is from 2 to {@code n}.
     *
     * <pre>
     * simpFrac(4)  ->  ["1/2", "1/3", "2/3", "1/4", "3/4"]
     * </pre>
     *
     * @param n the largest denominator, from 2 to 10
     *
     * @return one {@code numerator/denominator} string per fraction, smallest denominator first
     */
    public static List<String> simpFrac(int n) {
        List<String> fractions = new ArrayList<>();
        for (int denominator = 2; denominator <= n; denominator++) {
            for (int numerator = 1; numerator < denominator; numerator++) {
                if (isSimplified(numerator, denominator)) {
                    fractions.add(numerator + "/" + denominator);
                }
            }
        }
        return fractions;
    }

    /**
     * Tests whether a fraction is in its simplest form.
     *
     * <pre>
     * isSimplified(3, 4)  ->  true
     * isSimplified(2, 4)  ->  false (2 divides both)
     * </pre>
     *
     * @param numerator   the number above the line, 1 or more
     * @param denominator the number below the line
     *
     * @return {@code true} if no whole number above 1 divides both the numerator and the denominator
     */
    public static boolean isSimplified(int numerator, int denominator) {
        for (int divisor = 2; divisor <= numerator; divisor++) {
            if (numerator % divisor == 0 && denominator % divisor == 0) {
                return false;
            }
        }
        return true;
    }

}
