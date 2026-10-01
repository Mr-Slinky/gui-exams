package exam;

import exam.internal.FractionWall;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Plays three lists on the fraction wall, one after another in the same window: a list with every kind of mistake,
 * a list with no fractions in it at all, and the correct list. Press Enter in the console to move on to the next
 * list.
 */
public class Showcase {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    public static void main(String[] args) {
        // For n = 6 the correct list is 1/2, 1/3, 2/3, 1/4, 3/4, 1/5, 2/5, 3/5, 4/5, 1/6, 5/6
        List<String> everyMistake = Arrays.asList(
                "2/3", "1/2", "1/3", "1/4", "3/4", "1/5", "2/5", "4/5", "1/6",  // correct; 3/5 and 5/6 are missing
                "2/4", "4/6",                                                      // can be simplified
                "1/3",                                                             // repeated
                "3/3", "7/5",                                                      // one whole, more than one whole
                "0/5", "-1/6",                                                     // zero, less than zero
                "1/1", "2/7",                                                      // denominator off the wall
                "abc", "1 / 4", "0.5", "[1/5, 2/5]", null, "1/0");                 // not fractions at all

        List<String> noFractions = Arrays.asList("half", "one third", "two thirds", "0.25", "[1/4, 3/4]");

        Scanner in = new Scanner(System.in);

        System.out.println("1 of 3: every mistake a list can make, for n = 6. Press Enter for the next list.");
        FractionWall.show(6, everyMistake);
        in.nextLine();

        System.out.println("2 of 3: a list with no fractions in it. Press Enter for the next list.");
        FractionWall.show(6, noFractions);
        in.nextLine();

        System.out.println("3 of 3: the correct list for n = 10. Close the window to finish.");
        FractionWall.show(10, SimplifiedFractions.simpFrac(10));
    }

}
