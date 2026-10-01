# GUI Exams

Practical mock exams for the IEB Information Technology syllabus, written in Java for Grades 10, 11
and 12. The Grade 12 papers follow the layout of the IEB matric Paper I.

These exams aim to be more engaging and more fun than a standard paper. In a standard paper, the
learner's program prints its answers as text on the screen. Here, the same text output becomes a
script, and a window runs that script and animates it. In Farm Drones, for example, each line the
learner's program returns sends a drone flying across the farm to water a field.

The learner reads the question paper, writes the classes it asks for, and then watches the window
to see whether the program works. The window marks each part green, amber or red, and gives the
reason under anything that is wrong.

## Papers

The repository sorts papers by grade and then by difficulty:

```
Grade 10/
    Easy/
        GreatestCommonDivisor/
    Moderate/
        SimplifiedFractions/
    Difficult/
Grade 11/
    ...
Grade 12/
    Easy/
    Moderate/
    Difficult/
        FarmDrones/
```

| Grade | Difficulty | Paper                                                               | Topic                       |
|-------|------------|---------------------------------------------------------------------|-----------------------------|
| 10    | Easy       | [Greatest Common Divisor](Grade%2010/Easy/GreatestCommonDivisor)    | Loops and trace tables      |
| 10    | Moderate   | [Simplified Fractions](Grade%2010/Moderate/SimplifiedFractions)     | Loops and lists             |
| 12    | Difficult  | [Farm Drones](Grade%2012/Difficult/FarmDrones/QuestionPaper.pdf)    | Object-oriented programming |

## Inside a paper

A paper folder has up to four parts:

| Part                | Contents                                                                      |
|---------------------|-------------------------------------------------------------------------------|
| `QuestionPaper.pdf` | The question paper the learner works from                                     |
| `data_files/`       | The text files the learner's program reads                                    |
| `starting_code/`    | The `exam.internal` package, which draws the window and checks the learner's work |
| `solution/`         | The classes a worked answer adds to the `exam` package                        |

Farm Drones has all four parts. Greatest Common Divisor and Simplified Fractions each consist of the
two code folders, and the sections below set their tasks.

`starting_code/` contains the only copy of `exam.internal`. A worked answer compiles once its
classes are copied into `starting_code/`, beside that package.

The learner writes their classes in the `exam` package, next to `exam.internal`. The question paper
tells them to leave every file in `exam.internal` as it is. Every class in that package has
documentation written for the learner, so reading it is part of the exam.

## Greatest Common Divisor

Greatest Common Divisor is a Grade 10 paper built around one method. The learner writes a class
that extends `Solution` and overrides `gcd(small, big)`, which returns the largest number that
divides both parameters exactly. The learner then calls `launch()` on an object of that class.

The window calls `gcd` with five pairs of numbers that it chooses, and plays each call back as a
trace table:

- The learner's code is on the left, with the line that is running highlighted.
- The table has a column for each variable and a column for each test in a condition. One cell
  fills in per step, and a new row starts each time a loop goes round.
- Buttons under the table pause the replay, step it forwards or backwards, and change its speed.

The learner passes when all five answers are right. The window also counts the rows each call
fills, and shows that count beside the count for Euclid's algorithm. A solution that counts down
from `small` fills 35 rows over the five calls, and Euclid's algorithm fills 21.

The window shows the code and labels the condition columns from the learner's `.java` file. It
looks for that file in the folder the program runs from.

## Simplified Fractions

Simplified Fractions is a Grade 10 paper built around one method. The learner writes `simpFrac(n)`,
which returns every fraction between 0 and 1 that is in its simplest form and has a denominator
from 2 to `n`. Each fraction is a string such as `"3/4"`. The learner then passes the same `n` and
the returned list to `FractionWall.show`.

The window draws a fraction wall, with one table for each denominator from 2 to `n`. Every table is
the same width, so two fractions of the same size end at the same point. The wall first shows an
empty outline for every fraction the list should contain, and then plays the list one fraction at
a time:

- A correct fraction fills its outline in green.
- A fraction that can be simplified, such as 2/4, or one that appears twice, fills a red row and
  then slides up onto the fraction of the same size.
- Once the list has played, every outline that is still empty turns amber, because the list is
  missing that fraction.

The learner passes when every outline is filled and nothing is red. The wall draws an `n` from 2
to 10.

## Farm Drones

Farm Drones is a Grade 12 paper in five questions. A farm is a grid of fields, and a fleet of
drones waits in docking bays along the top row. The learner reads the fleet from `drones.txt` and a
list of fields that need water, then decides which drone waters which field.

The window checks the work in two stages:

1. After Question 3, it parks each drone in its bay and checks every line of the learner's
   `toString` against `drones.txt`.
2. After Question 5, it plays back the learner's deliveries as an animation. The window flies its
   own copy of every drone with the battery formula from the paper, so a wrong calculation shows
   up as a drone falling out of the sky.

The paper has no marks. The learner passes when every field has received all the water it needs,
and a banner in the window says so.

`data_files/` contains two lists of fields. The paper uses `jobs.txt`, which lists 14 fields, and
the worked answer waters all of them. `jobs_difficult.txt` lists 18 fields. Question 5 gives each
field to the first drone able to water it, and that rule leaves two of the 18 fields dry.

The window draws the fields from whichever text file the learner's `processJobs` opens. A learner
who changes that file name to `jobs_difficult.txt` therefore sees the 18 fields, with every file in
`exam.internal` left as it is.

## Requirements

A learner needs Java 25 or later. The Farm Drones window and the Greatest Common Divisor window both
read the learner's compiled class through the `java.lang.classfile` API. Every window uses Swing,
which ships with the JDK, so the exams need no other libraries and no build tool.
