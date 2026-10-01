package exam.internal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The screen you watch your trace tables on.
 * <p>
 * When the window opens, the display calls your {@code gcd} once for each pair of numbers it has chosen, and a
 * {@link Tracer} records every value your variables take and every test your conditions make. It then plays each
 * call back one step at a time:
 *
 * <ul>
 *   <li>your code on the left, with the line that is running highlighted</li>
 *   <li>a trace table on the right, with a column for each variable and each test, where one cell fills in per
 *   step</li>
 *   <li>a list of the calls under your code, each marked green or red once it has played, with the number of rows it
 *   filled next to the number Euclid's algorithm fills</li>
 * </ul>
 *
 * The display chooses the numbers, and it keeps them small enough for a table to fit on the screen.
 *
 * <h2 id="controls">Controls</h2>
 * The buttons under the table control the replay, and each has a key:
 *
 * <ul>
 *   <li><b>Back</b> and <b>Next</b>, or the left and right arrow keys, move one step and pause the replay</li>
 *   <li><b>Pause</b>, or the space bar, stops the replay and starts it again</li>
 *   <li><b>Speed</b> changes how long each step stays on screen</li>
 *   <li><b>Restart</b>, or the R key, goes back to the first call</li>
 * </ul>
 *
 * Click a call in the list to play that call. Once every call has played, a banner says whether you passed. You pass
 * when every answer is right.
 */
final class Display extends JPanel {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    // Each pair is small then big. A solution that counts down from small fills at most 12 rows for any of them.
    private static final int[][] INPUTS = {{12, 18}, {9, 27}, {7, 20}, {15, 25}, {16, 24}};

    private static final int PAD          = 20;
    private static final int TITLE        = 56;
    private static final int GAP          = 16;
    private static final int LEFT_WIDTH   = 560;
    private static final int CODE_LINES   = 16;
    private static final int CODE_LINE_H  = 22;
    private static final int CODE_TOP     = TITLE - 8;
    private static final int CODE_HEIGHT  = 58 + CODE_LINES * CODE_LINE_H + 10;
    private static final int LIST_TOP     = CODE_TOP + CODE_HEIGHT + GAP;
    private static final int LIST_LINE_H  = 44;
    private static final int LIST_HEIGHT  = 62 + INPUTS.length * LIST_LINE_H + 8;
    private static final int FULL_HEIGHT  = LIST_TOP + LIST_HEIGHT + PAD;

    private static final int TABLE_X      = PAD + LEFT_WIDTH + PAD;
    private static final int TABLE_WIDTH  = 880;
    private static final int LABEL_WIDTH  = 64;
    private static final int MIN_COLUMN   = 64;
    private static final int MAX_COLUMN   = 300;
    private static final int TABLE_TOP    = TITLE + 44;
    private static final int HEADER_H     = 34;
    private static final int ROW_H        = 30;
    private static final int VISIBLE_ROWS = 16;
    private static final int ROWS_TOP     = TABLE_TOP + HEADER_H;
    private static final int CONTROL_H    = 38;
    private static final int CONTROLS_TOP = FULL_HEIGHT - PAD - CONTROL_H;
    private static final int VERDICT_H    = 62;
    private static final int VERDICT_TOP  = CONTROLS_TOP - 12 - VERDICT_H;
    private static final int FULL_WIDTH   = TABLE_X + TABLE_WIDTH + PAD;

    private static final double   START_SECONDS = 1.2;   // a call shows its start row before the first step
    private static final double   STEP_SECONDS  = 1.0;   // at normal speed
    private static final double   HOLD_SECONDS  = 3.0;   // a finished table stays up before the next call starts
    private static final double   FLASH_SECONDS = 0.5;
    private static final double[] SPEEDS        = {0.5, 1, 2, 4};

    private static final Color BACKGROUND = new Color(0x12161C);
    private static final Color TEXT       = new Color(0xE6EDF3);
    private static final Color MUTED      = new Color(0x8B949E);
    private static final Color TABLE      = new Color(0x1C222B);
    private static final Color NEW_ROW    = new Color(0x243447);
    private static final Color ACCENT     = new Color(0x58A6FF);
    private static final Color CONDITION  = new Color(0xA5C8F0);
    private static final Color PANEL      = new Color(0x161B22);
    private static final Color PANEL_EDGE = new Color(0x30363D);
    private static final Color BUTTON     = new Color(0x21262D);
    private static final Color OK         = new Color(0x3FB950);
    private static final Color WARN       = new Color(0xD29922);
    private static final Color BAD        = new Color(0xF85149);

    private static final Font TITLE_FONT   = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Font HEAD_FONT    = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font TEXT_FONT    = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font SMALL_FONT   = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font CODE_FONT    = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private static final Font CODE_BOLD    = new Font(Font.MONOSPACED, Font.BOLD, 13);
    private static final Font SOURCE_FONT  = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    private static final Font CALL_FONT    = new Font(Font.MONOSPACED, Font.BOLD, 18);
    private static final Font VERDICT_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 26);

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final List<String>    errors    = new ArrayList<>();
    private final List<Case>      cases     = new ArrayList<>();
    private final List<Button>    buttons   = new ArrayList<>();
    private final List<Rectangle> listLines = new ArrayList<>();   // where each call sits in the list, for clicks
    private final Timer           timer     = new Timer(16, e -> tick());
    private final SourceCode      source;
    private final double          scale;

    private int firstCodeLine;   // the range of source lines the code panel shows
    private int lastCodeLine;

    private int     call;                // the index in cases of the call on screen
    private int     position;            // the steps of that call played so far, where the last one shows the result
    private boolean isPlaying = true;
    private int     speed     = 1;       // an index in SPEEDS
    private double  waited;              // seconds the current step has been on screen, at the current speed
    private long    lastTick;
    private long    changedAt;           // when the position last changed, for the flash on the newest cell

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    Display(Class<? extends Solution> type) {
        super(true);
        setBackground(BACKGROUND);
        setFocusable(true);

        source = SourceCode.find(type);
        traceEveryInput(type);
        findCodeRange();

        // Laid out at full size, then scaled down as a whole when the screen is too small for it
        if (GraphicsEnvironment.isHeadless()) {
            scale = 1.0;
        } else {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            scale = Math.min(1.0, Math.min((screen.getHeight() - 60) / FULL_HEIGHT, (screen.getWidth() - 40) / FULL_WIDTH));
        }
        setPreferredSize(new Dimension((int) Math.ceil(FULL_WIDTH * scale), (int) Math.ceil(FULL_HEIGHT * scale)));

        addButtons();
        addKey(KeyEvent.VK_SPACE, this::togglePlaying);
        addKey(KeyEvent.VK_RIGHT, this::stepForward);
        addKey(KeyEvent.VK_LEFT, this::stepBack);
        addKey(KeyEvent.VK_R, this::restart);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                click(new Point((int) (e.getX() / scale), (int) (e.getY() / scale)));
            }
        });

        lastTick  = System.nanoTime();
        changedAt = lastTick;
        timer.start();
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.scale(scale, scale);

        paintTitle(g2);
        paintCode(g2);
        paintList(g2);
        if (!cases.isEmpty() && !isWaitingForAnswer()) {
            paintTable(g2);
            paintVerdict(g2);
            paintButtons(g2);
        }
        paintErrors(g2);

        g2.dispose();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        timer.stop();
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    // Every call runs once, here, against a copy of the class that reports its steps. The window then only replays.
    private void traceEveryInput(Class<? extends Solution> type) {
        Solution traced;
        try {
            traced = (Solution) Tracer.instrument(type, source).getDeclaredConstructor().newInstance();
        } catch (IOException | ReflectiveOperationException | LinkageError ex) {
            errors.add(String.format("Could not trace %s: %s. The class needs to be public, with a constructor that takes no parameters.", type.getSimpleName(), ex));
            return;
        }
        for (int[] input : INPUTS) {
            int small = input[0];
            int big   = input[1];
            cases.add(new Case(small, big, Tracer.record(() -> traced.gcd(small, big))));
        }
    }

    // The code panel shows the lines your calls ran, with one line either side for the method header and its brace
    private void findCodeRange() {
        int first = Integer.MAX_VALUE;
        int last  = -1;
        for (Case c : cases) {
            for (Trace.Step step : c.trace().steps()) {
                if (step.line() > 0) {
                    first = Math.min(first, step.line());
                    last  = Math.max(last, step.line());
                }
            }
            if (c.trace().returnLine() > 0) {
                first = Math.min(first, c.trace().returnLine());
                last  = Math.max(last, c.trace().returnLine());
            }
        }
        firstCodeLine = last < 0 ? 1 : Math.max(1, first - 1);
        lastCodeLine  = last < 0 ? Math.min(source.lineCount(), CODE_LINES) : Math.min(source.lineCount(), last + 1);
    }

    // Every call throwing the placeholder's exception means gcd has yet to be written, so there is nothing to judge
    private boolean isWaitingForAnswer() {
        if (cases.isEmpty()) {
            return false;
        }
        for (Case c : cases) {
            if (!c.isUnwritten()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPlayedEveryCall() {
        for (Case c : cases) {
            if (!c.hasPlayed) {
                return false;
            }
        }
        return !cases.isEmpty();
    }

    // ---- replay ----

    private void tick() {
        long   now     = System.nanoTime();
        double elapsed = (now - lastTick) / 1e9;
        lastTick = now;

        if (isPlaying && !cases.isEmpty() && !isWaitingForAnswer()) {
            waited += elapsed * SPEEDS[speed];
            int    total = cases.get(call).totalSteps();
            double wait  = position == 0 ? START_SECONDS : position == total ? HOLD_SECONDS : STEP_SECONDS;
            if (waited >= wait) {
                if (position == total && call == cases.size() - 1) {
                    isPlaying = false;
                } else {
                    advance();
                }
            }
        }
        repaint();
    }

    private void advance() {
        if (position < cases.get(call).totalSteps()) {
            moveTo(call, position + 1);
        } else if (call < cases.size() - 1) {
            moveTo(call + 1, 0);
        }
    }

    private void moveTo(int newCall, int newPosition) {
        call      = newCall;
        position  = newPosition;
        waited    = 0;
        changedAt = System.nanoTime();
        if (position == cases.get(call).totalSteps()) {
            cases.get(call).hasPlayed = true;
        }
    }

    private void stepForward() {
        isPlaying = false;
        advance();
    }

    private void stepBack() {
        isPlaying = false;
        if (position > 0) {
            moveTo(call, position - 1);
        } else if (call > 0) {
            moveTo(call - 1, cases.get(call - 1).totalSteps());
        }
    }

    private void togglePlaying() {
        boolean atEnd = call == cases.size() - 1 && position == cases.get(call).totalSteps();
        if (!isPlaying && atEnd) {
            restart();
            return;
        }
        isPlaying = !isPlaying;
        waited    = 0;
    }

    private void restart() {
        isPlaying = true;
        moveTo(0, 0);
    }

    private void changeSpeed() {
        speed = (speed + 1) % SPEEDS.length;
    }

    // ---- input ----

    private void addButtons() {
        int[]    widths = {96, 84, 96, 84, 120};
        Button[] made   = {
            new Button(() -> "Restart", this::restart, new Rectangle()),
            new Button(() -> "Back", this::stepBack, new Rectangle()),
            new Button(() -> isPlaying ? "Pause" : "Play", this::togglePlaying, new Rectangle()),
            new Button(() -> "Next", this::stepForward, new Rectangle()),
            new Button(() -> String.format("Speed %sx", formatSpeed(SPEEDS[speed])), this::changeSpeed, new Rectangle())
        };
        int x = TABLE_X;
        for (int i = 0; i < made.length; i++) {
            made[i].bounds().setBounds(x, CONTROLS_TOP, widths[i], CONTROL_H);
            buttons.add(made[i]);
            x += widths[i] + 10;
        }
    }

    private void addKey(int keyCode, Runnable action) {
        String name = "key" + keyCode;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!cases.isEmpty() && !isWaitingForAnswer()) {
                    action.run();
                }
            }
        });
    }

    private void click(Point point) {
        if (cases.isEmpty() || isWaitingForAnswer()) {
            return;
        }
        for (Button button : buttons) {
            if (button.bounds().contains(point)) {
                button.action().run();
                return;
            }
        }
        for (int i = 0; i < listLines.size(); i++) {
            if (listLines.get(i).contains(point)) {
                isPlaying = true;
                moveTo(i, 0);
                return;
            }
        }
    }

    // ---- painting ----

    private void paintTitle(Graphics2D g) {
        g.setFont(TITLE_FONT);
        g.setColor(TEXT);
        g.drawString("Trace Table", PAD, 36);

        String status;
        if (cases.isEmpty()) {
            status = "";
        } else if (isWaitingForAnswer()) {
            status = "Waiting for gcd to be written";
        } else {
            status = String.format("Call %d of %d, step %d of %d%s", call + 1, cases.size(), position, cases.get(call).totalSteps(), isPlaying ? "" : ", paused");
        }
        g.setFont(TEXT_FONT);
        g.setColor(MUTED);
        g.drawString(status, TABLE_X + TABLE_WIDTH - g.getFontMetrics().stringWidth(status), 36);
    }

    // The line that filled the newest cell, or -1 before the first step of a call
    private int currentLine() {
        if (cases.isEmpty() || isWaitingForAnswer() || position == 0) {
            return -1;
        }
        var c = cases.get(call);
        if (position == c.totalSteps()) {
            return c.trace().returnLine();
        }
        return c.stepAt(position).line();
    }

    private void paintCode(Graphics2D g) {
        paintPanel(g, PAD, CODE_TOP, LEFT_WIDTH, CODE_HEIGHT, "Your code", source.fileName());

        if (!source.isFound()) {
            g.setFont(SMALL_FONT);
            g.setColor(WARN);
            String note = String.format("Could not find %s in the folder the program runs from, so the table shows your variables only. Conditions need the source file.", source.fileName());
            int ly = CODE_TOP + 76;
            for (String line : wrap(g.getFontMetrics(), note, LEFT_WIDTH - 32)) {
                g.drawString(line, PAD + 16, ly);
                ly += g.getFontMetrics().getHeight();
            }
            return;
        }

        // A method longer than the panel scrolls, keeping the running line near the middle
        int current = currentLine();
        int first   = firstCodeLine;
        if (lastCodeLine - firstCodeLine + 1 > CODE_LINES) {
            int centred = (current < 0 ? firstCodeLine : current) - CODE_LINES / 2;
            first = Math.max(firstCodeLine, Math.min(centred, lastCodeLine - CODE_LINES + 1));
        }
        int last   = Math.min(lastCodeLine, first + CODE_LINES - 1);
        int indent = findIndent(first, last);

        g.setFont(SOURCE_FONT);
        for (int number = first; number <= last; number++) {
            int y = CODE_TOP + 58 + (number - first) * CODE_LINE_H;
            if (number == current) {
                g.setColor(NEW_ROW);
                g.fill(new RoundRectangle2D.Double(PAD + 8, y, LEFT_WIDTH - 16, CODE_LINE_H, 8, 8));
                g.setColor(ACCENT);
                g.fill(new RoundRectangle2D.Double(PAD + 8, y, 4, CODE_LINE_H, 4, 4));
            }
            String label = String.valueOf(number);
            g.setColor(MUTED);
            g.drawString(label, PAD + 46 - g.getFontMetrics().stringWidth(label), y + 16);

            String code = source.line(number).replace("\t", "    ");
            code = code.length() >= indent ? code.substring(indent) : code.strip();
            g.setColor(number == current ? TEXT : new Color(0xC9D1D9));
            g.drawString(fit(g, code, LEFT_WIDTH - 76), PAD + 58, y + 16);
        }
    }

    // The spaces every shown line starts with, which are cut so the code sits against the left of the panel
    private int findIndent(int first, int last) {
        int indent = Integer.MAX_VALUE;
        for (int number = first; number <= last; number++) {
            String code = source.line(number).replace("\t", "    ");
            if (!code.isBlank()) {
                indent = Math.min(indent, code.length() - code.stripLeading().length());
            }
        }
        return indent == Integer.MAX_VALUE ? 0 : indent;
    }

    private void paintList(Graphics2D g) {
        paintPanel(g, PAD, LIST_TOP, LEFT_WIDTH, LIST_HEIGHT, "Your answers", String.format("gcd is called %d time(s). Click a call to play it.", cases.size()));

        listLines.clear();
        if (isWaitingForAnswer()) {
            g.setFont(SMALL_FONT);
            g.setColor(MUTED);
            int ly = LIST_TOP + 78;
            for (String line : wrap(g.getFontMetrics(), "Nothing yet. Override gcd in your class, and each call fills in a trace table here.", LEFT_WIDTH - 32)) {
                g.drawString(line, PAD + 16, ly);
                ly += g.getFontMetrics().getHeight();
            }
            return;
        }

        int cy = LIST_TOP + 62;
        for (int i = 0; i < cases.size(); i++) {
            var c = cases.get(i);
            listLines.add(new Rectangle(PAD + 8, cy, LEFT_WIDTH - 16, LIST_LINE_H - 4));

            if (i == call) {
                g.setColor(TABLE);
                g.fill(new RoundRectangle2D.Double(PAD + 8, cy, LEFT_WIDTH - 16, LIST_LINE_H - 6, 10, 10));
            }
            Color colour = !c.hasPlayed ? MUTED : c.isCorrect() ? OK : BAD;
            g.setColor(colour);
            if (c.hasPlayed) {
                g.fill(new Ellipse2D.Double(PAD + 16, cy + 8, 8, 8));
            } else {
                g.setStroke(new BasicStroke(1.5f));
                g.draw(new Ellipse2D.Double(PAD + 16, cy + 8, 8, 8));
            }

            g.setFont(CODE_FONT);
            g.setColor(TEXT);
            g.drawString(fit(g, c.hasPlayed ? c.summary() : c.call(), LEFT_WIDTH - 48), PAD + 32, cy + 16);

            g.setFont(SMALL_FONT);
            g.setColor(c.hasPlayed && !c.isCorrect() ? BAD : MUTED);
            g.drawString(fit(g, c.hasPlayed ? c.detail() : "not played yet", LEFT_WIDTH - 48), PAD + 32, cy + 32);
            cy += LIST_LINE_H;
        }
    }

    private void paintTable(Graphics2D g) {
        var c       = cases.get(call);
        var trace   = c.trace();
        var columns = trace.columns();
        int total   = c.totalSteps();

        g.setFont(CALL_FONT);
        g.setColor(TEXT);
        g.drawString(c.call(), TABLE_X, TABLE_TOP - 16);
        if (position == total) {
            int after = TABLE_X + g.getFontMetrics().stringWidth(c.call()) + 20;
            g.setFont(HEAD_FONT);
            g.setColor(c.isCorrect() ? OK : BAD);
            g.drawString(fit(g, c.outcome(), TABLE_X + TABLE_WIDTH - after), after, TABLE_TOP - 17);
        }

        int[] widths = measureColumns(g, columns);
        int[] lefts  = new int[columns.size()];
        int   x      = TABLE_X + LABEL_WIDTH;
        for (int col = 0; col < columns.size(); col++) {
            lefts[col] = x;
            x += widths[col];
        }
        int tableRight = x;

        g.setFont(CODE_BOLD);
        for (int col = 0; col < columns.size(); col++) {
            g.setColor(columns.get(col).isCondition() ? CONDITION : MUTED);
            drawCentred(g, fit(g, columns.get(col).heading(), widths[col] - 12), lefts[col] + widths[col] / 2.0, TABLE_TOP + 22);
        }

        // The cells filled so far, and the newest of them
        int        applied = Math.min(trace.steps().size(), trace.startSteps() + position);
        String[][] cells   = new String[trace.rowCount()][columns.size()];
        int        rows    = trace.hasStartRow() ? 1 : 0;
        for (int s = 0; s < applied; s++) {
            var step = trace.steps().get(s);
            cells[step.row()][step.column()] = step.value();
            rows = Math.max(rows, step.row() + 1);
        }
        Trace.Step newest = position > 0 && position < total ? c.stepAt(position) : null;

        int first = Math.max(0, rows - VISIBLE_ROWS);
        if (first > 0) {
            g.setFont(SMALL_FONT);
            g.setColor(MUTED);
            String note = String.format("%d earlier row(s) above", first);
            g.drawString(note, TABLE_X + TABLE_WIDTH - g.getFontMetrics().stringWidth(note), TABLE_TOP - 18);
        }

        double flash = Math.max(0, 1 - (System.nanoTime() - changedAt) / 1e9 / FLASH_SECONDS);
        for (int r = first; r < rows; r++) {
            boolean isNewest = newest != null && newest.row() == r;
            double  y        = ROWS_TOP + (r - first) * ROW_H;

            g.setColor(isNewest ? NEW_ROW : TABLE);
            g.fill(new RoundRectangle2D.Double(TABLE_X, y + 2, tableRight - TABLE_X, ROW_H - 4, 8, 8));

            boolean isStart = trace.hasStartRow() && r == 0;
            g.setFont(SMALL_FONT);
            g.setColor(MUTED);
            drawCentred(g, isStart ? "start" : String.valueOf(trace.hasStartRow() ? r : r + 1), TABLE_X + LABEL_WIDTH / 2.0, y + 19);

            for (int col = 0; col < columns.size(); col++) {
                if (cells[r][col] == null) {
                    continue;
                }
                if (isNewest && newest.column() == col) {
                    g.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), (int) (70 + 130 * flash)));
                    g.fill(new RoundRectangle2D.Double(lefts[col] + 3, y + 4, widths[col] - 6, ROW_H - 8, 8, 8));
                }
                g.setFont(CODE_FONT);
                g.setColor(TEXT);
                drawCentred(g, fit(g, cells[r][col], widths[col] - 8), lefts[col] + widths[col] / 2.0, y + 20);
            }
        }

        if (trace.isCutShort() && position == total) {
            g.setFont(SMALL_FONT);
            g.setColor(WARN);
            g.drawString(String.format("The table stops at %d rows.", Trace.MAX_ROWS), TABLE_X, VERDICT_TOP - 6);
        }
    }

    // Each column is as wide as its heading needs. When they overflow the table, every column shrinks by the same share.
    private int[] measureColumns(Graphics2D g, List<Trace.Column> columns) {
        var   metrics = g.getFontMetrics(CODE_BOLD);
        int[] widths  = new int[columns.size()];
        int   sum     = 0;
        for (int col = 0; col < widths.length; col++) {
            widths[col] = Math.max(MIN_COLUMN, Math.min(MAX_COLUMN, metrics.stringWidth(columns.get(col).heading()) + 28));
            sum += widths[col];
        }
        int room = TABLE_WIDTH - LABEL_WIDTH;
        if (sum > room) {
            for (int col = 0; col < widths.length; col++) {
                widths[col] = widths[col] * room / sum;
            }
        }
        return widths;
    }

    // The verdict covers every call, so it shows only once each of them has played to its end
    private void paintVerdict(Graphics2D g) {
        if (!hasPlayedEveryCall()) {
            return;
        }
        int wrong  = 0;
        int rows   = 0;
        int euclid = 0;
        for (Case c : cases) {
            if (!c.isCorrect()) {
                wrong++;
            }
            rows   += c.trace().passCount();
            euclid += c.euclidRows();
        }
        boolean passed = wrong == 0;
        Color   accent = passed ? OK : BAD;

        var box = new RoundRectangle2D.Double(TABLE_X, VERDICT_TOP, TABLE_WIDTH, VERDICT_H, 16, 16);
        g.setColor(new Color(13, 17, 23));
        g.fill(box);
        g.setColor(accent);
        g.setStroke(new BasicStroke(2f));
        g.draw(box);

        g.setFont(VERDICT_FONT);
        g.drawString(passed ? "PASSED" : "FAILED", TABLE_X + 20, VERDICT_TOP + 40);

        g.setFont(TEXT_FONT);
        g.setColor(TEXT);
        if (passed) {
            g.drawString("Every answer is right.", TABLE_X + 160, VERDICT_TOP + 26);
            g.drawString(String.format("Your gcd filled %d rows. Euclid's algorithm fills %d.", rows, euclid), TABLE_X + 160, VERDICT_TOP + 46);
        } else {
            g.drawString(String.format("%d of %d answers are wrong. The list on the left says why.", wrong, cases.size()), TABLE_X + 160, VERDICT_TOP + 36);
        }
    }

    private void paintButtons(Graphics2D g) {
        for (Button button : buttons) {
            var bounds = button.bounds();
            g.setColor(BUTTON);
            g.fill(new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10));
            g.setColor(PANEL_EDGE);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10));
            g.setFont(HEAD_FONT);
            g.setColor(TEXT);
            drawCentred(g, button.label().get(), bounds.getCenterX(), bounds.y + 24);
        }
        g.setFont(SMALL_FONT);
        g.setColor(MUTED);
        String keys = "Space pauses, the arrow keys step, R restarts";
        g.drawString(keys, TABLE_X + TABLE_WIDTH - g.getFontMetrics().stringWidth(keys), CONTROLS_TOP + 23);
    }

    private void paintErrors(Graphics2D g) {
        if (errors.isEmpty()) {
            return;
        }
        g.setFont(TEXT_FONT);
        var          fm    = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        for (String error : errors) {
            lines.addAll(wrap(fm, error, TABLE_WIDTH - 24));
        }

        double h = lines.size() * fm.getHeight() + 20;
        g.setColor(new Color(0x3D1A1A));
        g.fill(new RoundRectangle2D.Double(TABLE_X, TABLE_TOP, TABLE_WIDTH, h, 12, 12));
        g.setColor(BAD);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(TABLE_X, TABLE_TOP, TABLE_WIDTH, h, 12, 12));

        g.setColor(TEXT);
        double ly = TABLE_TOP + 10 + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, (float) (TABLE_X + 12), (float) ly);
            ly += fm.getHeight();
        }
    }

    private static void paintPanel(Graphics2D g, int x, int y, int w, int h, String title, String subtitle) {
        g.setColor(PANEL);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 16, 16));
        g.setColor(PANEL_EDGE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 16, 16));

        g.setFont(HEAD_FONT);
        g.setColor(TEXT);
        g.drawString(title, x + 16, y + 28);
        g.setFont(SMALL_FONT);
        g.setColor(MUTED);
        g.drawString(subtitle, x + 16, y + 46);
    }

    private static String formatSpeed(double speed) {
        return speed == Math.rint(speed) ? String.format("%.0f", speed) : String.valueOf(speed);
    }

    private static void drawCentred(Graphics2D g, String text, double cx, double baseline) {
        g.drawString(text, (float) (cx - g.getFontMetrics().stringWidth(text) / 2.0), (float) baseline);
    }

    private static String fit(Graphics2D g, String text, int width) {
        var fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String shortened = text;
        while (!shortened.isEmpty() && fm.stringWidth(shortened + "...") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "...";
    }

    private static List<String> wrap(FontMetrics fm, String text, int width) {
        List<String> lines = new ArrayList<>();
        var          line  = new StringBuilder();
        for (String word : String.valueOf(text).split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\
    // A button under the table: the words on it, what a click does, and where it is drawn
    private record Button(Supplier<String> label, Runnable action, Rectangle bounds) {}

    // One call to gcd: the numbers the display chose, and the trace of what the learner's code did with them
    private static final class Case {

        private final int   small;
        private final int   big;
        private final Trace trace;

        private boolean hasPlayed;   // true once the replay has reached the end of this call

        Case(int small, int big, Trace trace) {
            this.small = small;
            this.big   = big;
            this.trace = trace;
        }

        Trace trace() {
            return trace;
        }

        // One step for each cell after the start row, and a last one that shows what the call returned
        int totalSteps() {
            return trace.steps().size() - trace.startSteps() + 1;
        }

        // The step that fills the newest cell at a position from 1 to totalSteps() - 1
        Trace.Step stepAt(int position) {
            return trace.steps().get(trace.startSteps() + position - 1);
        }

        int expected() {
            int a = small;
            int b = big;
            while (b != 0) {
                int rest = a % b;
                a = b;
                b = rest;
            }
            return a;
        }

        // The rows Euclid's algorithm fills for these numbers when it is written as gcd(small, big). A table with
        // condition columns has one more row, for the test that ends the loop.
        int euclidRows() {
            int a    = small;
            int b    = big;
            int rows = trace.hasConditions() ? 1 : 0;
            while (b != 0) {
                int rest = a % b;
                a = b;
                b = rest;
                rows++;
            }
            return rows;
        }

        boolean isUnwritten() {
            return trace.failure() instanceof UnsupportedOperationException;
        }

        boolean isCorrect() {
            return trace.failure() == null && Integer.valueOf(expected()).equals(trace.result());
        }

        String call() {
            return String.format("gcd(%d, %d)", small, big);
        }

        // What the call returned, or how it failed
        String outcome() {
            if (trace.failure() != null) {
                return describeFailure();
            }
            if (isCorrect()) {
                return String.format("returns %s", trace.result());
            }
            return String.format("returns %s, but the answer is %d", trace.result(), expected());
        }

        String summary() {
            return trace.failure() == null ? String.format("%s = %s", call(), trace.result()) : call();
        }

        String detail() {
            if (trace.failure() != null) {
                return describeFailure();
            }
            if (!isCorrect()) {
                return String.format("the answer is %d", expected());
            }
            return String.format("%d row(s). Euclid's algorithm fills %d.", trace.passCount(), euclidRows());
        }

        private String describeFailure() {
            var failure = trace.failure();
            if (failure instanceof Probe.Runaway) {
                return String.format("%s, and the loop shows no sign of ending", failure.getMessage());
            }
            if (failure instanceof StackOverflowError) {
                return "the method keeps calling itself and never returns";
            }
            String where = trace.lastLine() > 0 ? String.format(" on line %d", trace.lastLine()) : "";
            return String.format("threw %s%s: %s", failure.getClass().getSimpleName(), where, failure.getMessage());
        }
    }

}
