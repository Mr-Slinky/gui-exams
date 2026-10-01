package exam.internal;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The source file your class was compiled from.
 * <p>
 * The window shows your code beside the trace table and highlights the line that is running, so it needs the
 * {@code .java} file as well as the compiled class. It looks for the file in the folder your program runs from and
 * in every folder inside that one, which is where an IDE keeps it.
 *
 * <pre>
 * exam.EuclidGCD  ->  .../exam/EuclidGCD.java
 * </pre>
 */
final class SourceCode {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    private static final int         MAX_DEPTH = 12;
    private static final Set<String> SKIPPED   = Set.of("target", "out", "build", "bin", "node_modules");

    /**
     * Looks for the source file of a class.
     *
     * @param type the compiled class
     *
     * @return the source file, with no lines in it when the file cannot be found
     */
    static SourceCode find(Class<?> type) {
        String name     = type.getName();
        int    dot      = name.lastIndexOf('.');
        String simple   = name.substring(dot + 1);
        String fileName = (simple.contains("$") ? simple.substring(0, simple.indexOf('$')) : simple) + ".java";
        Path   wanted   = Path.of(dot < 0 ? "" : name.substring(0, dot).replace('.', '/'), fileName);

        Path   root  = Path.of("").toAbsolutePath();
        Path[] found = new Path[1];
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String folder = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    boolean skip  = !dir.equals(root) && (folder.startsWith(".") || SKIPPED.contains(folder));
                    return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.endsWith(wanted)) {
                        found[0] = file;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException ex) {
                    return FileVisitResult.CONTINUE;
                }
            });
            if (found[0] != null) {
                return new SourceCode(fileName, Files.readAllLines(found[0]));
            }
        } catch (IOException ex) {
            // An unreadable folder or file leaves the window without source, which it reports on screen
        }
        return new SourceCode(fileName, List.of());
    }

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final String       fileName;
    private final List<String> lines;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private SourceCode(String fileName, List<String> lines) {
        this.fileName = fileName;
        this.lines    = lines;
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    String fileName() {
        return fileName;
    }

    /**
     * @return the number of lines in the file, which is 0 when the file was not found
     */
    int lineCount() {
        return lines.size();
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * @return {@code true} if the source file was found and read
     */
    boolean isFound() {
        return !lines.isEmpty();
    }

    /**
     * @param number a line number, where the first line of the file is 1
     *
     * @return the text of that line, or an empty string for a number outside the file
     */
    String line(int number) {
        return number >= 1 && number <= lines.size() ? lines.get(number - 1) : "";
    }

    /**
     * @param number a line number, where the first line of the file is 1
     *
     * @return the conditions written on that line, from left to right
     */
    List<Condition> conditionsOn(int number) {
        return Condition.parseLine(line(number));
    }

}
