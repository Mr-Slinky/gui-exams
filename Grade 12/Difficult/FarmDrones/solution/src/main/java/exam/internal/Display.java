package exam.fun_exam.internal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen you watch your drones on.
 * <p>
 * When the window opens, the display calls two of your methods, once each and in this order:
 *
 * <ol>
 *   <li>{@code toString()}, whose lines it reads to draw your drones in their bays and to label each bay</li>
 *   <li>{@code processJobs()}, whose script it hands to a {@link Replay}</li>
 * </ol>
 *
 * It then draws the result about sixty times a second:
 *
 * <ul>
 *   <li>the farm, with a dry brown patch for every field in {@code jobs_easy.txt} and a label saying how many litres
 *   it needs</li>
 *   <li>your drones, which take off from their bays, fly to each field your script sends them to and spray it</li>
 *   <li>a panel on the right. Before {@code processJobs()} returns anything, it lists the lines of your
 *   {@code toString()}. Once there is a script, it lists the script, one line at a time. Either way, each line is
 *   marked green when it worked and red or amber when it did not, with the reason underneath</li>
 * </ul>
 *
 * Once the last drone has finished, a banner says whether you passed. You pass when every field has received all
 * of its water. Click anywhere to watch it again.
 */
public class Display extends JPanel {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    private static final int CELL      = 64;
    private static final int LABEL     = 28;
    private static final int BAY_GAP   = 26;
    private static final int PAD       = 20;
    private static final int TITLE     = 56;
    private static final int LOG_WIDTH = 380;
    private static final int FIELD_X   = PAD + LABEL;
    private static final int FIELD_Y   = TITLE + LABEL;
    private static final int MIN_COLS  = 10;
    private static final int MIN_ROWS  = 8;

    private static final double DRONE_SIZE    = CELL * 0.7;
    private static final double ROTOR_SPEED   = 22;   // radians per second while flying
    private static final double VERDICT_DELAY = 0.6;  // seconds between the last drone finishing and the banner

    private static final Color BACKGROUND  = new Color(0x12161C);
    private static final Color TEXT        = new Color(0xE6EDF3);
    private static final Color MUTED       = new Color(0x8B949E);
    private static final Color BAY_ROW     = new Color(0x1C222B);
    private static final Color BAY_PAD     = new Color(0x2D333B);
    private static final Color GRASS       = new Color(0x22362A);
    private static final Color SOIL        = new Color(0x6B4A2B);
    private static final Color WATERED     = new Color(0x3FA34D);
    private static final Color WATER       = new Color(0x58A6FF);
    private static final Color PANEL       = new Color(0x161B22);
    private static final Color PANEL_EDGE  = new Color(0x30363D);
    private static final Color OK          = new Color(0x3FB950);
    private static final Color WARN        = new Color(0xD29922);
    private static final Color BAD         = new Color(0xF85149);
    private static final Color WRECK       = new Color(0x6E7681);
    private static final Color ROTOR_BLUR  = new Color(255, 255, 255, 40);
    private static final Color ROTOR_RIM   = new Color(0xC9D1D9);
    private static final Color ROTOR_BLADE = new Color(0xF0F6FC);

    private static final Font TITLE_FONT  = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Font HEAD_FONT   = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font TEXT_FONT   = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font SMALL_FONT  = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font CODE_FONT   = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font BANNER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 36);

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final Manager           manager;
    private final List<String>      errors = new ArrayList<>();
    private final List<Fleet.Entry> fleet;
    private final Replay.Job[]      jobs;
    private final String            script;
    private final int               cols;
    private final int               rows;

    private Replay replay;
    private long   startNanos;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    public Display(Manager manager) {
        super(true);
        this.manager = manager;
        setBackground(BACKGROUND);

        // toString() first, while the drones are still docked; processJobs() moves them
        fleet  = loadFleet();
        jobs   = loadJobs();
        script = loadScript();
        restart();

        int maxCol = replay.maxCol();
        int maxRow = replay.maxRow();
        for (Fleet.Entry entry : fleet) {
            if (entry.isDrawable()) {
                maxCol = Math.max(maxCol, entry.col());
                maxRow = Math.max(maxRow, entry.row());
            }
        }
        cols = Math.max(MIN_COLS, maxCol + 1);
        rows = Math.max(MIN_ROWS, maxRow);
        setPreferredSize(new Dimension(FIELD_X + cols * CELL + PAD + LOG_WIDTH + PAD,
                                       Math.max(FIELD_Y + (rows + 1) * CELL + BAY_GAP + PAD, 520)));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                restart();
            }
        });
        new Timer(16, e -> repaint()).start();
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

        double t = (System.nanoTime() - startNanos) / 1e9;

        paintTitle(g2, t);
        paintGrid(g2);
        paintJobs(g2, t);
        paintBays(g2);
        paintDrones(g2, t);
        paintLog(g2, t);
        paintErrors(g2);
        paintVerdict(g2, t);

        g2.dispose();
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private List<Fleet.Entry> loadFleet() {
        try {
            String text = manager.toString();
            return Fleet.parse(text == null ? "" : text, manager.copyReferenceDrones());
        } catch (RuntimeException ex) {
            errors.add(String.format("toString() threw %s: %s", ex.getClass().getSimpleName(), ex.getMessage()));
            return new ArrayList<>();
        }
    }

    private Replay.Job[] loadJobs() {
        try {
            return Replay.readJobs("jobs_easy.txt");
        } catch (RuntimeException ex) {
            errors.add(ex.getMessage());
            return new Replay.Job[0];
        }
    }

    private String loadScript() {
        try {
            String result = manager.processJobs();
            return result == null ? "" : result;
        } catch (RuntimeException ex) {
            errors.add(String.format("processJobs() threw %s: %s", ex.getClass().getSimpleName(), ex.getMessage()));
            return "";
        }
    }

    private void restart() {
        replay     = Replay.run(script, manager.copyReferenceDrones(), jobs);
        startNanos = System.nanoTime();
    }

    private boolean isFinished(double t) {
        return t >= replay.endTime() + VERDICT_DELAY;
    }

    // An empty script with no errors means processJobs() has yet to be written, so there is nothing to judge
    private boolean isWaitingForScript() {
        return replay.scriptLineCount() == 0 && errors.isEmpty();
    }

    // Row 0 is the bay row, and the gap below it pushes every field row down by BAY_GAP
    private static double colToX(double col) {
        return FIELD_X + col * CELL + CELL / 2.0;
    }

    private static double rowToY(double row) {
        return FIELD_Y + CELL / 2.0 + row * CELL + BAY_GAP * clamp(row, 0.0, 1.0);
    }

    private void paintTitle(Graphics2D g, double t) {
        g.setFont(TITLE_FONT);
        g.setColor(TEXT);
        g.drawString("Farm Drone Replay", PAD, 36);

        String status;
        if (isWaitingForScript()) {
            status = "Waiting for processJobs() to return a script";
        } else if (isFinished(t)) {
            status = "Click anywhere to replay";
        } else {
            status = String.format("Replaying  %.1f s", Math.max(0, t));
        }
        g.setFont(TEXT_FONT);
        g.setColor(MUTED);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(status, FIELD_X + cols * CELL - fm.stringWidth(status), 36);
    }

    private void paintGrid(Graphics2D g) {
        g.setFont(SMALL_FONT);
        FontMetrics fm = g.getFontMetrics();

        g.setColor(MUTED);
        for (int c = 0; c < cols; c++) {
            String letter = String.valueOf((char) ('A' + c));
            g.drawString(letter, (int) colToX(c) - fm.stringWidth(letter) / 2, FIELD_Y - 10);
        }
        for (int r = 0; r <= rows; r++) {
            String number = String.valueOf(r);
            g.drawString(number, FIELD_X - 8 - fm.stringWidth(number), (int) rowToY(r) + fm.getAscent() / 2 - 1);
        }

        g.setColor(BAY_ROW);
        g.fill(new RoundRectangle2D.Double(FIELD_X, rowToY(0) - CELL / 2.0, cols * CELL, CELL, 14, 14));

        g.setColor(GRASS);
        for (int r = 1; r <= rows; r++) {
            for (int c = 0; c < cols; c++) {
                g.fill(cellShape(c, r));
            }
        }
    }

    private void paintJobs(Graphics2D g, double t) {
        Replay.Job[] all = replay.jobs();
        for (int j = 0; j < all.length; j++) {
            Replay.Job job   = all[j];
            double     level = Math.min(1, replay.wateredAt(j, t) / job.litres());
            Shape      cell  = cellShape(job.col(), job.row());

            g.setColor(SOIL);
            g.fill(cell);

            if (level > 0) {
                // Water fills the field from the bottom up
                Rectangle2D bounds = cell.getBounds2D();
                double      height = bounds.getHeight() * level;
                Shape       old    = g.getClip();
                g.clip(new Rectangle2D.Double(bounds.getX(), bounds.getMaxY() - height, bounds.getWidth(), height));
                g.setColor(WATERED);
                g.fill(cell);
                g.setClip(old);
            }

            double x = colToX(job.col());
            double y = rowToY(job.row());
            if (level >= 1) {
                paintTick(g, x, y);
            } else {
                String label = Replay.formatLitres(job.litres()) + " L";
                g.setFont(HEAD_FONT);
                FontMetrics fm = g.getFontMetrics();
                g.setColor(TEXT);
                g.drawString(label, (float) (x - fm.stringWidth(label) / 2.0), (float) (y + fm.getAscent() / 2.0 - 2));
            }
        }
    }

    private void paintTick(Graphics2D g, double x, double y) {
        Path2D.Double tick = new Path2D.Double();
        tick.moveTo(x - 11, y + 1);
        tick.lineTo(x - 3, y + 9);
        tick.lineTo(x + 12, y - 8);
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(tick);
    }

    // Bays come from toString(), so a line the display cannot read leaves its bay unmarked
    private void paintBays(Graphics2D g) {
        g.setFont(SMALL_FONT);
        FontMetrics fm = g.getFontMetrics();
        for (Fleet.Entry entry : fleet) {
            if (!entry.isDrawable()) {
                continue;
            }
            double x      = colToX(entry.col());
            double y      = rowToY(entry.row());
            Color  colour = parseColour(entry.colourHex());
            double size   = CELL - 12;

            g.setColor(BAY_PAD);
            g.fill(new RoundRectangle2D.Double(x - size / 2, y - size / 2, size, size, 12, 12));
            g.setColor(colour);
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Double(x - size / 2, y - size / 2, size, size, 12, 12));

            // Each bay is labelled with its drone's name in the gap beneath the bay row
            String name = entry.name();
            g.drawString(name, (float) (x - fm.stringWidth(name) / 2.0), (float) (rowToY(0) + CELL / 2.0 + BAY_GAP / 2.0 + fm.getAscent() / 2.0 - 1));
        }
    }

    private static Shape cellShape(double col, double row) {
        return new RoundRectangle2D.Double(FIELD_X + col * CELL + 2, rowToY(row) - CELL / 2.0 + 2, CELL - 4, CELL - 4, 10, 10);
    }

    private void paintDrones(Graphics2D g, double t) {
        if (isWaitingForScript()) {
            paintFleet(g, t);
            return;
        }
        for (int d = 0; d < replay.droneCount(); d++) {
            Replay.DroneState state  = replay.stateAt(d, t);
            Color             colour = parseColour(replay.colourHex(d));
            double            x      = colToX(state.col());
            double            y      = rowToY(state.row());
            double            spin   = t * ROTOR_SPEED + d;

            switch (state.phase()) {
                case DOCKED -> {
                    paintDrone(g, x, y, colour, spin * 0.35, 0, 1f, true);
                    paintBars(g, x, y, state, replay.startPayload(d));
                }
                case FLYING, HOVERING, SPRAYING -> {
                    double bob = Math.sin(t * 4 + d) * 2;
                    if (state.phase() == Replay.Phase.SPRAYING) {
                        paintSpray(g, x, y + bob, state.progress());
                    }
                    paintDrone(g, x, y + bob, colour, spin, 0, 1f, true);
                    paintNameTag(g, x, y + bob, replay.name(d));
                    paintBars(g, x, y + bob, state, replay.startPayload(d));
                }
                case FALLING -> {
                    double p = state.progress();
                    paintDrone(g, x, y + p * CELL * 0.45, blend(colour, WRECK, p), spin * (1 - p), p * 0.9, (float) (1 - 0.35 * p), p < 0.6);
                    paintNameTag(g, x, y, replay.name(d));
                }
                case WRECKED -> {
                    double wy = y + CELL * 0.45;
                    g.setColor(new Color(0, 0, 0, 90));
                    g.fill(new Ellipse2D.Double(x - DRONE_SIZE * 0.45, wy - DRONE_SIZE * 0.12, DRONE_SIZE * 0.9, DRONE_SIZE * 0.3));
                    paintDrone(g, x, wy, WRECK, 0.4, 0.9, 0.65f, false);
                    paintNameTag(g, x, wy - 4, replay.name(d) + " crashed");
                }
            }
        }
    }

    // Before there is a script, the drones are drawn purely from what toString() describes
    private void paintFleet(Graphics2D g, double t) {
        int index = 0;
        for (Fleet.Entry entry : fleet) {
            if (!entry.isDrawable()) {
                continue;
            }
            double x = colToX(entry.col());
            double y = rowToY(entry.row());
            paintDrone(g, x, y, parseColour(entry.colourHex()), (t * ROTOR_SPEED + index) * 0.35, 0, 1f, true);
            paintBars(g, x, y, new Replay.DroneState(entry.col(), entry.row(), 100, entry.payload(), Replay.Phase.DOCKED, 0), entry.payload());
            index++;
        }
    }

    // A quadcopter: four arms, a rotor on each arm, and a square body with a light on its nose
    private void paintDrone(Graphics2D g, double cx, double cy, Color colour, double angle, double tilt, float alpha, boolean spinning) {
        Graphics2D d = (Graphics2D) g.create();
        d.translate(cx, cy);
        d.rotate(tilt);
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        double arm    = DRONE_SIZE * 0.34;
        double rotorR = DRONE_SIZE * 0.2;
        double body   = DRONE_SIZE * 0.36;
        Color  dark   = colour.darker();

        d.setColor(dark);
        d.setStroke(new BasicStroke((float) (DRONE_SIZE * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                d.draw(new Line2D.Double(0, 0, sx * arm, sy * arm));
            }
        }

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                double          rx    = sx * arm;
                double          ry    = sy * arm;
                Ellipse2D.Double disc = new Ellipse2D.Double(rx - rotorR, ry - rotorR, rotorR * 2, rotorR * 2);
                if (spinning) {
                    d.setColor(ROTOR_BLUR);
                    d.fill(disc);
                }
                d.setColor(ROTOR_RIM);
                d.setStroke(new BasicStroke(1.5f));
                d.draw(disc);

                // Neighbouring rotors turn in opposite directions, as on a real quadcopter
                double turn = angle * sx * sy;
                d.setColor(ROTOR_BLADE);
                d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int k = 0; k < 3; k++) {
                    double b = turn + k * 2 * Math.PI / 3;
                    d.draw(new Line2D.Double(rx, ry, rx + Math.cos(b) * rotorR * 0.9, ry + Math.sin(b) * rotorR * 0.9));
                }
                d.setColor(dark);
                d.fill(new Ellipse2D.Double(rx - 2.5, ry - 2.5, 5, 5));
            }
        }

        RoundRectangle2D.Double hull = new RoundRectangle2D.Double(-body / 2, -body / 2, body, body, body * 0.4, body * 0.4);
        d.setColor(colour);
        d.fill(hull);
        d.setColor(dark);
        d.setStroke(new BasicStroke(1.5f));
        d.draw(hull);
        d.setColor(Color.WHITE);
        d.fill(new Ellipse2D.Double(-2.5, -body * 0.3 - 2.5, 5, 5));

        d.dispose();
    }

    private void paintSpray(Graphics2D g, double x, double y, double progress) {
        double fade = 1 - Math.max(0, progress - 0.8) / 0.2;
        for (int k = 0; k < 6; k++) {
            double q     = (progress * 3 + k / 6.0) % 1;
            double dx    = (k - 2.5) * 5;
            double dy    = DRONE_SIZE * 0.25 + q * CELL * 0.45;
            int    alpha = (int) (200 * (1 - q) * fade);
            g.setColor(new Color(WATER.getRed(), WATER.getGreen(), WATER.getBlue(), (int) clamp(alpha, 0, 255)));
            g.fill(new Ellipse2D.Double(x + dx - 2.5, y + dy - 3.5, 5, 7));
        }
    }

    private void paintNameTag(Graphics2D g, double x, double y, String text) {
        g.setFont(SMALL_FONT);
        FontMetrics fm    = g.getFontMetrics();
        double      w     = fm.stringWidth(text) + 10;
        double      h     = fm.getHeight();
        double      top   = y - DRONE_SIZE * 0.62 - h;
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(new RoundRectangle2D.Double(x - w / 2, top, w, h, h, h));
        g.setColor(TEXT);
        g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0), (float) (top + fm.getAscent()));
    }

    // A battery bar, coloured by charge, with a water bar under it
    private void paintBars(Graphics2D g, double x, double y, Replay.DroneState state, double fullPayload) {
        double w   = DRONE_SIZE * 0.8;
        double top = y + DRONE_SIZE * 0.5;

        double battery = clamp(state.battery() / 100, 0.0, 1.0);
        Color  charge  = battery > 0.5 ? OK : battery > 0.2 ? WARN : BAD;
        paintBar(g, x - w / 2, top, w, battery, charge);

        if (fullPayload > 0) {
            double water = clamp(state.payload() / fullPayload, 0.0, 1.0);
            paintBar(g, x - w / 2, top + 6, w, water, WATER);
        }
    }

    private void paintBar(Graphics2D g, double x, double y, double w, double fraction, Color colour) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fill(new RoundRectangle2D.Double(x, y, w, 4, 4, 4));
        g.setColor(colour);
        g.fill(new RoundRectangle2D.Double(x, y, w * fraction, 4, 4, 4));
    }

    private void paintLog(Graphics2D g, double t) {
        if (isWaitingForScript()) {
            List<PanelRow> rows = new ArrayList<>();
            for (Fleet.Entry entry : fleet) {
                // Tabs are shown as \t, so a space where a tab belongs is easy to spot
                rows.add(new PanelRow(fleetColour(entry.status()), entry.line().replace("\t", "\\t"), entry.detail()));
            }
            paintPanel(g, "Your drones", String.format("toString() returned %d line(s)", fleet.size()), rows,
                       "Nothing yet. Write toString() in DroneManager to see your drones.");
            return;
        }

        List<PanelRow> rows = new ArrayList<>();
        for (Replay.LogEntry entry : replay.logUntil(t)) {
            rows.add(new PanelRow(statusColour(entry.status()), entry.line(), entry.detail()));
        }
        paintPanel(g, "Your script", String.format("processJobs() returned %d line(s)", replay.scriptLineCount()), rows, "");
    }

    // A titled list of coloured rows, showing the newest rows that fit so the list scrolls as it grows
    private void paintPanel(Graphics2D g, String title, String subtitle, List<PanelRow> rows, String emptyNote) {
        int x = FIELD_X + cols * CELL + PAD;
        int y = TITLE - 8;
        int w = LOG_WIDTH;
        int h = getHeight() - y - PAD;

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

        int top    = y + 66;
        int bottom = y + h - 12;

        if (rows.isEmpty() && !emptyNote.isEmpty()) {
            List<String> lines = wrap(g.getFontMetrics(), emptyNote, w - 32);
            int ly = top + 12;
            for (String line : lines) {
                g.drawString(line, x + 16, ly);
                ly += g.getFontMetrics().getHeight();
            }
            return;
        }

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
        double       w     = cols * CELL - 24;
        List<String> lines = new ArrayList<>();
        for (String error : errors) {
            lines.addAll(wrap(fm, error, (int) w - 24));
        }

        double h   = lines.size() * fm.getHeight() + 20;
        double x   = FIELD_X + 12;
        double top = rowToY(rows) + CELL / 2.0 - h - 12;
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
        if (!isFinished(t) || isWaitingForScript()) {
            return;
        }
        boolean passed = errors.isEmpty() && replay.passed();
        String  head   = passed ? "PASSED" : "FAILED";
        String  body;
        if (passed) {
            body = "Every field has been watered.";
        } else if (!errors.isEmpty()) {
            body = "See the error message below.";
        } else {
            body = String.format("%d of %d fields are still dry.", replay.dryCount(), jobs.length);
        }

        double w  = Math.min(cols * CELL - 40, 440);
        double h  = 124;
        double cx = FIELD_X + cols * CELL / 2.0;
        double cy = rowToY((rows + 1) / 2.0);
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

    private static Color fleetColour(Fleet.Status status) {
        return switch (status) {
            case OK -> OK;
            case MISMATCH -> WARN;
            case UNREADABLE -> BAD;
        };
    }

    private static Color statusColour(Replay.Status status) {
        return switch (status) {
            case DELIVERED -> OK;
            case SHORT_OF_WATER, WASTED -> WARN;
            case CRASHED, DRONE_DOWN, UNKNOWN_DRONE, UNREADABLE -> BAD;
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

    private static Color parseColour(String hex) {
        try {
            return Color.decode(hex);
        } catch (RuntimeException ex) {
            return WRECK;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Color blend(Color from, Color to, double fraction) {
        double f = clamp(fraction, 0.0, 1.0);
        return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * f),
                         (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * f),
                         (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * f));
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\
    // One row of the right-hand panel: a status colour, the line itself, and an optional reason beneath it
    private record PanelRow(Color colour, String line, String detail) {

        int height() {
            return detail.isEmpty() ? 22 : 38;
        }
    }

}
