package exam.internal;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_char;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

/**
 * Makes a copy of your compiled class that reports every step it takes, and runs code against that copy.
 * <p>
 * The copy does everything your class does. After each step it also calls {@link Probe}: when a method starts, when
 * a variable is given a value, when a condition is tested, when a new line begins, when a loop goes round again and
 * when a method returns. Your own class and your source file stay as you wrote them.
 * <p>
 * A compiled class keeps the outcome of each test and drops the words it was written in. The copy therefore matches
 * the tests on each line of the compiled class to the conditions on the same line of the source file, in order. A
 * line where the two counts differ gets no condition columns.
 * <p>
 * Variable names come from the debugging information in the compiled class, which an IDE and Maven both include. A
 * class compiled without it shows each variable as {@code var} followed by a number.
 *
 * <h2 id="usage">Usage</h2>
 * Build the copy once, make an object from it, then record each call:
 *
 * <pre>{@code
 * Class<?> copy   = Tracer.instrument(EuclidGCD.class, SourceCode.find(EuclidGCD.class));
 * Solution traced = (Solution) copy.getDeclaredConstructor().newInstance();
 * Trace    trace  = Tracer.record(() -> traced.gcd(12, 18));  // trace.result() is 6
 * }</pre>
 */
final class Tracer {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    private static final ClassDesc PROBE = ClassDesc.of(Probe.class.getName());

    private static final MethodTypeDesc TAKES_NOTHING     = MethodTypeDesc.of(CD_void);
    private static final MethodTypeDesc TAKES_INT         = MethodTypeDesc.of(CD_void, CD_int);
    private static final MethodTypeDesc TAKES_STRING      = MethodTypeDesc.of(CD_void, CD_String);
    private static final MethodTypeDesc TAKES_TWO_STRINGS = MethodTypeDesc.of(CD_void, CD_String, CD_String);
    private static final MethodTypeDesc TAKES_TWO_INTS    = MethodTypeDesc.of(CD_void, CD_int, CD_int);
    private static final MethodTypeDesc TAKES_THREE_INTS  = MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int);

    private static final MethodTypeDesc TAKES_OBJECT_AND_INT      = MethodTypeDesc.of(CD_void, CD_Object, CD_int);
    private static final MethodTypeDesc TAKES_TWO_OBJECTS_AND_INT = MethodTypeDesc.of(CD_void, CD_Object, CD_Object, CD_int);

    /**
     * Builds the reporting copy of a class. The copy has the same name and the same superclass, so an object made
     * from it can be cast to that superclass and called as usual.
     *
     * @param type   the compiled class to copy
     * @param source the source file of the class, which supplies the text of each condition
     *
     * @return the copy, loaded by a class loader of its own
     *
     * @throws IOException if the compiled class cannot be found or read
     */
    static Class<?> instrument(Class<?> type, SourceCode source) throws IOException {
        var    name = type.getName();
        var    file = name.substring(name.lastIndexOf('.') + 1) + ".class";
        byte[] original;
        try (InputStream in = type.getResourceAsStream(file)) {
            if (in == null) {
                throw new IOException(String.format("Could not find the compiled class '%s'", file));
            }
            original = in.readAllBytes();
        }

        var    classFile = ClassFile.of();
        byte[] traced    = classFile.transformClass(classFile.parse(original), (builder, element) -> {
            if (element instanceof MethodModel method && isTraceable(method)) {
                builder.transformMethod(method, MethodTransform.transformingCode(new Instrumenter(method, source)));
            } else {
                builder.with(element);
            }
        });
        return new TracedLoader(type.getClassLoader()).define(name, traced);
    }

    /**
     * Runs a call against a reporting copy and collects everything the copy reports.
     *
     * @param call the call to make, which returns the result of the traced method
     *
     * @return the finished trace, including the result or the reason the call failed
     */
    static Trace record(Supplier<?> call) {
        Probe.reset();
        Object    result  = null;
        Throwable failure = null;
        try {
            result = call.get();
        } catch (RuntimeException | Probe.Runaway | StackOverflowError ex) {
            failure = ex;
        }
        return Trace.build(Probe.events(), result, failure);
    }

    // Constructors, static initialisers and main are left alone: none of them is part of an answer
    private static boolean isTraceable(MethodModel method) {
        var name = method.methodName().stringValue();
        return method.code().isPresent() && !name.startsWith("<") && !name.equals("main");
    }

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Tracer() {
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\
    // Defines the copy under the original name. The copy still finds every other class through the parent loader.
    private static final class TracedLoader extends ClassLoader {

        TracedLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    // Copies one method body, adding a call to Probe after each step
    private static final class Instrumenter implements CodeTransform {

        private final String                  methodName;
        private final MethodTypeDesc          methodType;
        private final boolean                 isStatic;
        private final List<LocalVariableInfo> variables;
        private final Set<Label>              bound = new HashSet<>();
        private final List<Integer>           testIds;   // the id of each conditional jump in order, or -1 for none

        private int pc;                 // the position of the next instruction in the original method
        private int pendingLine = -1;   // a line that has started but whose first instruction is still to come
        private int nextTest;           // the index in testIds of the next conditional jump

        Instrumenter(MethodModel method, SourceCode source) {
            methodName = method.methodName().stringValue();
            methodType = method.methodTypeSymbol();
            isStatic   = method.flags().has(AccessFlag.STATIC);
            variables  = method.code()
                               .flatMap(code -> code.findAttribute(Attributes.localVariableTable()))
                               .map(LocalVariableTableAttribute::localVariables)
                               .orElse(List.of());
            testIds    = matchTests(method, source);
        }

        // Pairs the conditional jumps on each line with the tests the source file writes on that line, in order
        private List<Integer> matchTests(MethodModel method, SourceCode source) {
            List<Integer> lines = new ArrayList<>();   // the source line of each conditional jump
            int line = -1;
            for (CodeElement element : method.code().orElseThrow()) {
                if (element instanceof LineNumber number) {
                    line = number.line();
                } else if (element instanceof BranchInstruction branch && isConditional(branch)) {
                    lines.add(line);
                }
            }

            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                ids.add(-1);
            }
            for (int number : new TreeSet<>(lines)) {
                List<Probe.Tested> written = new ArrayList<>();
                for (Condition condition : source.conditionsOn(number)) {
                    for (int t = 0; t < condition.tests().size(); t++) {
                        written.add(new Probe.Tested(methodName, number, condition, t));
                    }
                }
                if (written.size() != Collections.frequency(lines, number)) {
                    continue;
                }
                int next = 0;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i) == number) {
                        ids.set(i, Probe.register(written.get(next++)));
                    }
                }
            }
            return ids;
        }

        private static boolean isConditional(BranchInstruction branch) {
            return branch.opcode() != Opcode.GOTO && branch.opcode() != Opcode.GOTO_W;
        }

        // The values a jump compares are on top of the stack, so copies of them go to Probe with the id of the test
        private void reportTest(CodeBuilder builder, Opcode opcode, int id) {
            switch (opcode) {
                case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                    builder.dup2();
                    builder.loadConstant(id);
                    builder.invokestatic(PROBE, "test", TAKES_THREE_INTS);
                }
                case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                    builder.dup();
                    builder.loadConstant(id);
                    builder.invokestatic(PROBE, "test", TAKES_TWO_INTS);
                }
                case IF_ACMPEQ, IF_ACMPNE -> {
                    builder.dup2();
                    builder.loadConstant(id);
                    builder.invokestatic(PROBE, "testSame", TAKES_TWO_OBJECTS_AND_INT);
                }
                case IFNULL, IFNONNULL -> {
                    builder.dup();
                    builder.loadConstant(id);
                    builder.invokestatic(PROBE, "testNull", TAKES_OBJECT_AND_INT);
                }
                default -> { }
            }
        }

        @Override
        public void atStart(CodeBuilder builder) {
            builder.loadConstant(methodName);
            builder.invokestatic(PROBE, "enter", TAKES_STRING);

            // An instance method keeps 'this' in slot 0, so its parameters start at slot 1
            int slot = isStatic ? 0 : 1;
            for (ClassDesc parameter : methodType.parameterList()) {
                var kind = TypeKind.from(parameter);
                if (kind != TypeKind.REFERENCE) {
                    builder.loadConstant(findName(slot, 0, 0));
                    builder.loadLocal(kind, slot);
                    convertToText(builder, parameter.descriptorString());
                    builder.invokestatic(PROBE, "param", TAKES_TWO_STRINGS);
                }
                slot += kind.slotSize();
            }
        }

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            int start = pc;
            if (element instanceof Instruction instruction) {
                pc += instruction.sizeInBytes();
                reportPendingLine(builder);
            }

            switch (element) {
                case LabelTarget target -> {
                    bound.add(target.label());
                    builder.with(element);
                }
                case LineNumber line -> {
                    pendingLine = line.line();
                    builder.with(element);
                }
                case StoreInstruction store -> {
                    builder.with(element);
                    reportValue(builder, store.slot(), store.typeKind(), start);
                }
                case IncrementInstruction increment -> {
                    builder.with(element);
                    reportValue(builder, increment.slot(), TypeKind.INT, start);
                }
                case BranchInstruction branch -> {
                    if (isConditional(branch)) {
                        int id = testIds.get(nextTest++);
                        if (id >= 0) {
                            reportTest(builder, branch.opcode(), id);
                        }
                    }
                    // A jump to a label already passed goes backwards, which is a loop going round again
                    if (bound.contains(branch.target())) {
                        builder.invokestatic(PROBE, "loop", TAKES_NOTHING);
                    }
                    builder.with(element);
                }
                case ReturnInstruction result -> {
                    reportReturn(builder, result.typeKind());
                    builder.with(element);
                }
                default -> builder.with(element);
            }
        }

        // The report waits for the first instruction of the line, so that it lands after any label a loop jumps to
        private void reportPendingLine(CodeBuilder builder) {
            if (pendingLine < 0) {
                return;
            }
            builder.loadConstant(pendingLine);
            builder.invokestatic(PROBE, "line", TAKES_INT);
            pendingLine = -1;
        }

        private void reportValue(CodeBuilder builder, int slot, TypeKind kind, int start) {
            if (kind == TypeKind.REFERENCE) {
                return;
            }
            builder.loadConstant(findName(slot, start, pc));
            builder.loadLocal(kind, slot);
            convertToText(builder, findDescriptor(slot, kind, start, pc));
            builder.invokestatic(PROBE, "set", TAKES_TWO_STRINGS);
        }

        // The value to return is on top of the stack, so a copy of it is turned into text and reported
        private void reportReturn(CodeBuilder builder, TypeKind kind) {
            switch (kind) {
                case VOID -> builder.aconst_null();
                case LONG, DOUBLE -> {
                    builder.dup2();
                    convertToText(builder, methodType.returnType().descriptorString());
                }
                default -> {
                    builder.dup();
                    convertToText(builder, methodType.returnType().descriptorString());
                }
            }
            builder.invokestatic(PROBE, "exit", TAKES_STRING);
        }

        // Replaces the value on top of the stack with its text, through the String.valueOf that suits its type
        private static void convertToText(CodeBuilder builder, String descriptor) {
            ClassDesc argument = switch (descriptor.charAt(0)) {
                case 'Z' -> CD_boolean;
                case 'C' -> CD_char;
                case 'B', 'S', 'I' -> CD_int;
                case 'J' -> CD_long;
                case 'F' -> CD_float;
                case 'D' -> CD_double;
                default -> CD_Object;
            };
            builder.invokestatic(CD_String, "valueOf", MethodTypeDesc.of(CD_String, argument));
        }

        // A variable's scope opens straight after the instruction that first gives it a value, so the search covers
        // the instruction itself and the position after it
        private LocalVariableInfo findVariable(int slot, int start, int end) {
            for (LocalVariableInfo variable : variables) {
                if (variable.slot() == slot && variable.startPc() <= end && start < variable.startPc() + variable.length()) {
                    return variable;
                }
            }
            return null;
        }

        private String findName(int slot, int start, int end) {
            var variable = findVariable(slot, start, end);
            return variable == null ? "var" + slot : variable.name().stringValue();
        }

        private String findDescriptor(int slot, TypeKind kind, int start, int end) {
            var variable = findVariable(slot, start, end);
            if (variable != null) {
                return variable.type().stringValue();
            }
            return switch (kind) {
                case LONG -> "J";
                case FLOAT -> "F";
                case DOUBLE -> "D";
                default -> "I";
            };
        }
    }

}
