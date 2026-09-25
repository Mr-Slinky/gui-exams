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
    Moderate/
    Difficult/
Grade 11/
    ...
Grade 12/
    Easy/
    Moderate/
    Difficult/
        FarmDrones/
```

| Grade | Difficulty | Paper                                                         | Topic                       |
|-------|------------|---------------------------------------------------------------|-----------------------------|
| 12    | Difficult  | [Farm Drones](Grade%2012/Difficult/FarmDrones/QuestionPaper.pdf) | Object-oriented programming |

## Inside a paper

Every paper folder has the same four parts:

| Part                | Contents                                                                      |
|---------------------|-------------------------------------------------------------------------------|
| `QuestionPaper.pdf` | The question paper the learner works from                                     |
| `data_files/`       | The text files the learner's program reads                                    |
| `starting_code/`    | The `exam.internal` package, which draws the window and checks the learner's work |
| `solution/`         | A worked answer, alongside the same `exam.internal` package                   |

The learner writes their classes in the `exam` package, next to `exam.internal`. The question paper
tells them to leave every file in `exam.internal` as it is. Every class in that package has
documentation written for the learner, so reading it is part of the exam.

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

## Requirements

The provided code uses Java records, so a learner needs Java 16 or later. The window uses Swing,
which ships with the JDK, so the exams need no other libraries and no build tool.
