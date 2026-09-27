package exam.internal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen you watch your fraction wall on.
 * <p>
 * The display hands your list to a {@link Replay} once, when the window opens, and then draws the result about sixty
 * times a second:
 *
 * <ul>
 *   <li>the wall on the left, with one table per denominator and an outline for every fraction your list should
 *   contain</li>
 *   <li>your fractions filling those outlines one at a time, with every wrong fraction in red</li>
 *   <li>a panel on the right that lists your fractions in the order they play, each marked green when it is right,
 *   red when it is wrong and amber when it is missing, with the reason underneath</li>
 * </ul>
 *
 * The window shrinks to fit a small screen. Once the last fraction has played, a banner says whether you passed.
 * Click anywhere to watch it again.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-09-27
 * @since 2.0.0
 */
final class Display extends JPanel {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    private static final int PAD        = 20;
    private static final int TITLE      = 56;
    private static final int GUTTER     = 130;
    private static final int WALL_WIDTH = 560;
    private static final int SPILL      = 80;   // room right of the wall for a fraction of more than one whole
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_STEP   = 22;
    private static final int TABLE_GAP  = 14;
    private static final int LOG_WIDTH  = 340;
    private static final int MIN_HEIGHT = 560;
    private static final int WALL_X     = PAD + GUTTER;
    private static final int WALL_TOP   = TITLE + 16;
    private static final int WALL_RIGHT = WALL_X + WALL_WIDTH;
    private static final int SPILL_END  = WALL_RIGHT + SPILL;

    private static final double MIN_CELL      = 4;    // narrower cells are drawn as one solid bar
    private static final double VERDICT_DELAY = 0.6;  // seconds between the last turn and the banner

    private static final Color BACKGROUND = new Color(0x12161C);
    private static final Color TEXT       = new Color(0xE6EDF3);
    private static final Color MUTED      = new Color(0x8B949E);
    private static final Color TABLE      = new Color(0x1C222B);
    private static final Color RED_TABLE  = new Color(0x2A1719);
    private static final Color CELL_EDGE  = new Color(0x2D333B);
    private static final Color OUTLINE    = new Color(0x57606A);
    private static final Color PANEL      = new Color(0x161B22);
    private static final Color PANEL_EDGE = new Color(0x30363D);
    private static final Color OK         = new Color(0x3FB950);
    private static final Color WARN       = new Color(0xD29922);
    private static final Color BAD        = new Color(0xF85149);

    private static final Font TITLE_FONT  = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Font HEAD_FONT   = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font TEXT_FONT   = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font SMALL_FONT  = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font CODE_FONT   = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font BANNER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 36);

    private static final Stroke DASHED = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] {4f, 3f}, 0f);

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final int          n;
    private final List<String> errors = new ArrayList<>();
    private final Replay       replay;  // null when n is outside the range the wall can draw
    private final int          fullWidth;
    private final int          fullHeight;
    private final double       scale;
    private final Timer        timer = new Timer(16, e -> repaint());

    private long startNanos;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    Display(int n, List<String> fractions) {
        super(true);
        this.n = n;
        setBackground(BACKGROUND);

        if (!Replay.isValidN(n)) {
            errors.add(String.format("n is %d. The wall is drawn for an n from %d to %d, so choose one in that range in your main method.", n, Replay.MIN_N, Replay.MAX_N));
        } else if (fractions == null) {
            errors.add("simpFrac returned null. Return a list, even an empty one, so the wall has something to play.");
        }
        replay = Replay.isValidN(n) ? Replay.run(n, fractions == null ? List.of() : fractions) : null;

        // A list with nothing readable in it is one mistake repeated, so it gets one message that explains it
        if (replay != null && replay.entryCount() > 0 && replay.readableCount() == 0) {
            String first = String.valueOf(fractions.get(0));
            if (first.length() > 40) {
                first = first.substring(0, 40) + "...";
            }
            errors.add(String.format("None of the %d string(s) in your list is a fraction. Each string should hold one fraction, written like \"3/4\". Your first string was \"%s\".", replay.entryCount(), first));
        }

        // Laid out at full size, then scaled down as a whole when the screen is too small for it
        fullWidth  = SPILL_END + PAD + LOG_WIDTH + PAD;
        fullHeight = Math.max(MIN_HEIGHT, wallBottom() + PAD);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        scale = Math.min(1.0, Math.min((screen.getHeight() - 60) / fullHeight, (screen.getWidth() - 40) / fullWidth));
        setPreferredSize(new Dimension((int) Math.ceil(fullWidth * scale), (int) Math.ceil(fullHeight * scale)));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                startNanos = System.nanoTime();
            }
        });
        startNanos = System.nanoTime();
        timer.start();
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.scale(scale, scale);

        double t = (System.nanoTime() - startNanos) / 1e9;

        paintTitle(g2, t);
        if (replay != null) {
            paintTables(g2, t);
            for (int r = 0; r < replay.rows().size(); r++) {
                paintRow(g2, r, t);
            }
            paintLandings(g2, t);
            paintSlides(g2, t);
        }
        paintLog(g2, t);
        paintErrors(g2);
        paintVerdict(g2, t);

        g2.dispose();
    }

    // A new list shown in the same window replaces this display, which then stops redrawing
    @Override
    public void removeNotify() {
        super.removeNotify();
        timer.stop();
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private boolean isFinished(double t) {
        return replay == null || t >= replay.endTime() + VERDICT_DELAY;
    }

    // An empty list with no errors means simpFrac has yet to be written, so there is nothing to judge
    private boolean isWaitingForList() {
        return replay != null && replay.entryCount() == 0 && errors.isEmpty();
    }

    // Every table is pushed down by the gap between it and the table above
    private double rowTop(int row) {
        return WALL_TOP + row * ROW_STEP + replay.rows().get(row).table() * TABLE_GAP;
    }

    private int wallBottom() {
        if (replay == null || replay.rows().isEmpty()) {
            return WALL_TOP;
        }
        return (int) rowTop(replay.rows().size() - 1) + ROW_HEIGHT + 6;
    }

    private void paintTitle(Graphics2D g, double t) {
        g.setFont(TITLE_FONT);
        g.setColor(TEXT);
        g.drawString(replay == null ? "Fraction Wall" : String.format("Fraction Wall for n = %d", n), PAD, 36);

        String status;
        if (replay == null) {
            status = "";
        } else if (isWaitingForList()) {
            status = String.format("Waiting for simpFrac(%d) to return some fractions", n);
        } else if (isFinished(t)) {
            status = "Click anywhere to replay";
        } else {
            status = String.format("Playing  %.1f s", Math.max(0, t));
        }
        g.setFont(TEXT_FONT);
        g.setColor(MUTED);
        g.drawString(status, SPILL_END - g.getFontMetrics().stringWidth(status), 36);
    }

    private void paintTables(Graphics2D g, double t) {
        List<Replay.Table> tables = replay.tables();
        for (int i = 0; i < tables.size(); i++) {
            Replay.Table table = tables.get(i);
            if (t < replay.tableAppearsAt(i)) {
                continue;
            }
            double top    = rowTop(table.firstRow()) - 5;
            double bottom = rowTop(table.lastRow()) + ROW_HEIGHT + 5;
            RoundRectangle2D.Double box = new RoundRectangle2D.Double(WALL_X - 6, top, WALL_WIDTH + 12, bottom - top, 10, 10);

            g.setColor(table.onWall() ? TABLE : RED_TABLE);
            g.fill(box);
            if (!table.onWall()) {
                g.setColor(BAD);
                g.setStroke(new BasicStroke(1.5f));
                g.draw(box);
            }

            // The table's name goes in the gutter beside its first row
            g.setFont(SMALL_FONT);
            g.setColor(table.onWall() ? MUTED : BAD);
            String name = fit(g, Replay.nameOf(table.denominator()), 64);
            g.drawString(name, PAD, (float) textBaseline(g, rowTop(table.firstRow())));
        }
    }

    private void paintRow(Graphics2D g, int r, double t) {
        Replay.Row      row      = replay.rows().get(r);
        Replay.Step     step     = replay.stepAt(r);
        Replay.Fraction fraction = row.fraction();
        double          y        = rowTop(r);

        if (row.outline()) {
            paintOutline(g, fraction, y, step, t);
            return;
        }
        if (step == null || t < step.start()) {
            return;
        }

        // A row of zero or less shakes once it has had its turn to fill
        double shake = 0;
        if (fraction.numerator() <= 0) {
            double q = step.stayProgress(t);
            shake = q > 0 && q < 1 ? Math.sin(t * 45) * 6 * (1 - q) : 0;
        }

        paintLabel(g, step.text(), y, BAD);
        strokeCells(g, WALL_X + shake, y, fraction.denominator(), fraction.denominator(), CELL_EDGE, new BasicStroke(1f));
        if (fraction.numerator() <= 0) {
            strokeCells(g, WALL_X + shake, y, fraction.denominator(), fraction.denominator(), BAD, new BasicStroke(1.5f));
            return;
        }

        boolean slides = step.target() >= 0;
        if (slides && step.slideProgress(t) > 0) {
            // The row slides away, leaving a dashed ghost where it was
            strokeCells(g, WALL_X, y, fraction.denominator(), fraction.numerator(), BAD, DASHED);
            return;
        }
        fillCells(g, WALL_X, y, fraction.denominator(), fraction.numerator() * step.fillProgress(t), BAD);
    }

    // An outline of a fraction the list should contain, filled green by a correct turn or marked amber when missing
    private void paintOutline(Graphics2D g, Replay.Fraction fraction, double y, Replay.Step step, double t) {
        strokeCells(g, WALL_X, y, fraction.denominator(), fraction.denominator(), CELL_EDGE, new BasicStroke(1f));

        boolean started = step != null && t >= step.start();
        if (started && step.status() == Replay.Status.MISSING) {
            double q = step.missingProgress(t);
            fillCells(g, WALL_X, y, fraction.denominator(), fraction.numerator(), withAlpha(WARN, 40));
            strokeCells(g, WALL_X, y, fraction.denominator(), fraction.numerator(), WARN, new BasicStroke((float) (1.5 + 2.5 * (1 - q))));
            paintLabel(g, fraction.toString(), y, WARN);
            return;
        }

        strokeCells(g, WALL_X, y, fraction.denominator(), fraction.numerator(), OUTLINE, DASHED);
        if (started) {
            fillCells(g, WALL_X, y, fraction.denominator(), fraction.numerator() * step.fillProgress(t), OK);
            paintLabel(g, fraction.toString(), y, TEXT);
        } else {
            paintLabel(g, fraction.toString(), y, MUTED);
        }
    }

    // A wrong fraction in mid-slide, drawn over everything else on its way to the row of the same size
    private void paintSlides(Graphics2D g, double t) {
        for (Replay.Step step : replay.steps()) {
            if (step.target() < 0) {
                continue;
            }
            double p = step.slideProgress(t);
            if (p <= 0 || p >= 1) {
                continue;
            }
            Replay.Fraction fraction = replay.rows().get(step.row()).fraction();
            double          y        = lerp(rowTop(step.row()), rowTop(step.target()), ease(p));

            fillCells(g, WALL_X, y, fraction.denominator(), fraction.numerator(), withAlpha(BAD, 230));
            strokeCells(g, WALL_X, y, fraction.denominator(), fraction.numerator(), TEXT, new BasicStroke(1.5f));
            paintTag(g, step.text(), WALL_X + barWidth(fraction) + 8, y, BAD);
        }
    }

    // A wrong fraction that has landed stays on its target, drawn with its own columns so the two sizes can be compared
    private void paintLandings(Graphics2D g, double t) {
        List<Replay.Step> steps = replay.steps();
        for (int i = 0; i < steps.size(); i++) {
            Replay.Step step = steps.get(i);
            if (step.target() < 0 || step.slideProgress(t) < 1) {
                continue;
            }
            Replay.Fraction fraction = replay.rows().get(step.row()).fraction();
            double          y        = rowTop(step.target());
            double          q        = step.landProgress(t);

            fillCells(g, WALL_X, y, fraction.denominator(), fraction.numerator(), withAlpha(BAD, 110));
            strokeCells(g, WALL_X, y, fraction.denominator(), fraction.numerator(), BAD, new BasicStroke((float) (1.5 + 3 * (1 - q))));

            // Several fractions can land on one row, so each tag moves along past the ones before it
            int earlier = 0;
            for (int j = 0; j < i; j++) {
                if (steps.get(j).target() == step.target() && steps.get(j).slideProgress(t) >= 1) {
                    earlier++;
                }
            }
            paintTag(g, step.text(), WALL_X + barWidth(fraction) + 8 + earlier * 44, y, BAD);
        }
    }

    // Fills whole cells from the left, with a part cell at the end; cells past the right edge spill out and fade
    private static void fillCells(Graphics2D g, double x, double y, int parts, double cells, Color colour) {
        if (cells <= 0) {
            return;
        }
        double cellWidth = (double) WALL_WIDTH / parts;
        if (cellWidth < MIN_CELL) {
            g.setColor(colour);
            g.fill(new RoundRectangle2D.Double(x, y, Math.min(cells * cellWidth, SPILL_END - x), ROW_HEIGHT, 5, 5));
            return;
        }
        for (int k = 0; k < cells; k++) {
            double left = x + k * cellWidth;
            if (left >= SPILL_END) {
                break;
            }
            double amount = Math.min(1, cells - k);
            double fade   = left < WALL_RIGHT ? 1 : 1 - (left - WALL_RIGHT) / SPILL;
            g.setColor(withAlpha(colour, (int) (colour.getAlpha() * fade)));
            double cell = Math.min(cellWidth * amount, SPILL_END - left);
            g.fill(new RoundRectangle2D.Double(left + 1.5, y, Math.max(0, cell - 3), ROW_HEIGHT, 5, 5));
        }
    }

    private static void strokeCells(Graphics2D g, double x, double y, int parts, int cells, Color colour, Stroke stroke) {
        if (cells <= 0) {
            return;
        }
        double cellWidth = (double) WALL_WIDTH / parts;
        g.setColor(colour);
        g.setStroke(stroke);
        if (cellWidth < MIN_CELL) {
            g.draw(new RoundRectangle2D.Double(x, y, Math.min(cells * cellWidth, SPILL_END - x), ROW_HEIGHT, 5, 5));
            return;
        }
        for (int k = 0; k < cells && x + k * cellWidth < WALL_RIGHT; k++) {
            g.draw(new RoundRectangle2D.Double(x + k * cellWidth + 1.5, y, cellWidth - 3, ROW_HEIGHT, 5, 5));
        }
    }

    private static double barWidth(Replay.Fraction fraction) {
        return Math.min(WALL_WIDTH, WALL_WIDTH * (double) fraction.numerator() / fraction.denominator());
    }

    // The fraction's text, right-aligned in the gutter beside its row
    private static void paintLabel(Graphics2D g, String text, double y, Color colour) {
        g.setFont(CODE_FONT);
        g.setColor(colour);
        String shown = fit(g, text, 56);
        g.drawString(shown, (float) (WALL_X - 12 - g.getFontMetrics().stringWidth(shown)), (float) textBaseline(g, y));
    }

    // A small dark tag with the fraction's text, drawn beside a bar on the wall
    private static void paintTag(Graphics2D g, String text, double x, double y, Color colour) {
        g.setFont(CODE_FONT);
        FontMetrics fm    = g.getFontMetrics();
        String      shown = fit(g, text, 60);
        double      w     = fm.stringWidth(shown) + 10;
        g.setColor(new Color(0, 0, 0, 170));
        g.fill(new RoundRectangle2D.Double(x, y, w, ROW_HEIGHT, ROW_HEIGHT, ROW_HEIGHT));
        g.setColor(colour);
        g.drawString(shown, (float) (x + 5), (float) textBaseline(g, y));
    }

    private static double textBaseline(Graphics2D g, double rowTop) {
        FontMetrics fm = g.getFontMetrics();
        return rowTop + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2.0;
    }

    private void paintLog(Graphics2D g, double t) {
        List<PanelRow> rows = new ArrayList<>();
        String         subtitle = "";
        if (replay != null) {
            for (Replay.Step step : replay.steps()) {
                if (t >= step.shownAt()) {
                    rows.add(new PanelRow(statusColour(step.status()), step.text(), step.detail()));
                }
            }
            subtitle = String.format("simpFrac(%d) returned %d fraction(s)", n, replay.entryCount());
        }
        String emptyNote = isWaitingForList() ? "Nothing yet. Write simpFrac so it returns a list, and your fractions fill the wall as they play." : "";

        int x = SPILL_END + PAD;
        int y = TITLE - 8;
        int w = LOG_WIDTH;
        int h = fullHeight - y - PAD;

        g.setColor(PANEL);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 16, 16));
        g.setColor(PANEL_EDGE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 16, 16));

        g.setFont(HEAD_FONT);
        g.setColor(TEXT);
        g.drawString("Your fractions", x + 16, y + 28);
        g.setFont(SMALL_FONT);
        g.setColor(MUTED);
        g.drawString(subtitle, x + 16, y + 46);

        int top    = y + 66;
        int bottom = y + h - 12;

        if (rows.isEmpty() && !emptyNote.isEmpty()) {
            int ly = top + 12;
            for (String line : wrap(g.getFontMetrics(), emptyNote, w - 32)) {
                g.drawString(line, x + 16, ly);
                ly += g.getFontMetrics().getHeight();
            }
            return;
        }

        // The newest rows that fit, so the list scrolls as it grows
        int first = rows.size();
        int used  = 0;
        while (first > 0) {
            int need = rows.get(first - 1).height();
            if (used + need > bottom - top) {
                break;
            }
            used += need;
            first--;
        }

        int cy = top;
        for (int i = first; i < rows.size(); i++) {
            PanelRow row = rows.get(i);

            g.setColor(row.colour());
            g.fill(new Ellipse2D.Double(x + 16, cy + 5, 8, 8));

            g.setFont(CODE_FONT);
            g.setColor(TEXT);
            g.drawString(fit(g, row.line(), w - 48), x + 32, cy + 13);

            if (!row.detail().isEmpty()) {
                g.setFont(SMALL_FONT);
                g.setColor(row.colour());
                g.drawString(fit(g, row.detail(), w - 48), x + 32, cy + 29);
            }
            cy += row.height();
        }
    }

    private void paintErrors(Graphics2D g) {
        if (errors.isEmpty()) {
            return;
        }
        g.setFont(TEXT_FONT);
        FontMetrics  fm    = g.getFontMetrics();
        double       w     = SPILL_END - PAD;
        List<String> lines = new ArrayList<>();
        for (String error : errors) {
            lines.addAll(wrap(fm, error, (int) w - 24));
        }

        double h   = lines.size() * fm.getHeight() + 20;
        double x   = PAD;
        double top = fullHeight - PAD - h;
        g.setColor(new Color(0x3D1A1A));
        g.fill(new RoundRectangle2D.Double(x, top, w, h, 12, 12));
        g.setColor(BAD);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(x, top, w, h, 12, 12));

        g.setColor(TEXT);
        double ly = top + 10 + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, (float) (x + 12), (float) ly);
            ly += fm.getHeight();
        }
    }

    private void paintVerdict(Graphics2D g, double t) {
        if (!isFinished(t) || isWaitingForList()) {
            return;
        }
        boolean passed = errors.isEmpty() && replay.passed();
        String  head   = passed ? "PASSED" : "FAILED";
        String  body;
        if (passed) {
            body = "Every fraction is on the wall, simplified and listed once.";
        } else if (!errors.isEmpty()) {
            body = "See the error message below.";
        } else {
            List<String> parts = new ArrayList<>();
            if (replay.wrongCount() > 0) {
                parts.add(String.format("%d wrong", replay.wrongCount()));
            }
            if (replay.missingCount() > 0) {
                parts.add(String.format("%d missing", replay.missingCount()));
            }
            body = String.join(", ", parts) + ". The list on the right says why.";
        }

        double w      = 440;
        double h      = 124;
        double cx     = WALL_X + WALL_WIDTH / 2.0;
        double cy     = Math.min(fullHeight / 2.0, fullHeight - PAD - h);
        Color  accent = passed ? OK : BAD;

        g.setColor(new Color(13, 17, 23, 225));
        g.fill(new RoundRectangle2D.Double(cx - w / 2, cy - h / 2, w, h, 18, 18));
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new RoundRectangle2D.Double(cx - w / 2, cy - h / 2, w, h, 18, 18));

        drawCentred(g, head, BANNER_FONT, accent, cx, cy - 16);
        drawCentred(g, body, TEXT_FONT, TEXT, cx, cy + 16);
        drawCentred(g, "Click anywhere to replay.", SMALL_FONT, MUTED, cx, cy + 38);
    }

    private static Color statusColour(Replay.Status status) {
        return switch (status) {
            case ON_WALL -> OK;
            case MISSING -> WARN;
            case NOT_SIMPLIFIED, REPEATED, WHOLE_OR_MORE, ZERO_OR_LESS, OFF_THE_WALL, UNREADABLE -> BAD;
        };
    }

    private static void drawCentred(Graphics2D g, String text, Font font, Color colour, double cx, double baseline) {
        g.setFont(font);
        g.setColor(colour);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) baseline);
    }

    private static String fit(Graphics2D g, String text, int width) {
        FontMetrics fm = g.getFontMetrics();
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
        List<String>  lines = new ArrayList<>();
        StringBuilder line  = new StringBuilder();
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

    private static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static double lerp(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    private static double ease(double p) {
        return p * p * (3 - 2 * p);
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\
    // One row of the right-hand panel: a status colour, the fraction itself, and an optional reason beneath it
    private record PanelRow(Color colour, String line, String detail) {

        int height() {
            return detail.isEmpty() ? 22 : 38;
        }
    }

}
