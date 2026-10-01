package exam;

import exam.internal.Solution;

public class EuclidGCD extends Solution {

    public static void main(String[] args) {
        int big   = 33;
        int small = 22;

//        System.out.println(gcd_best(small, big));
        System.out.println(gcd_worst(small, big));

        new EuclidGCD().launch();
    }

    @Override
    public int gcd(int small, int big) {
        return gcd_worst(small, big);
    }

    public static int gcd_worst(int small, int big) {
        for (int i = small; i > 1; i--) {
            if (big % i == 0 && small % i == 0) return i;
        }

        return 1;
    }


    public static int gcd_best(int small, int big) {
        while (big != 0) {
            int temp = big;
            big   = small % big;
            small = temp;
        }

        return small;
    }

}
