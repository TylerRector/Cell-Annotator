import ij.*;
import ij.plugin.tool.PlugInTool;
import ij.plugin.frame.PlugInFrame;
import ij.gui.*;
import ij.io.*;
import ij.process.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Cell_Annotator extends PlugInTool implements IJEventListener {

    // =========================================================================
    // DEBUG
    // =========================================================================
    private boolean DEBUG = true;

    // ── Annotation record ─────────────────────────────────────────────────────
    private static class Annotation {
        final int x, y, label;
        Annotation(int x, int y, int label) {
            this.x = x; this.y = y; this.label = label;
        }
        public String toString() {
            return "Annotation{label=" + label + ",x=" + x + ",y=" + y + "}";
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<Annotation>    annotations = new ArrayList<Annotation>();
    private       int                 activeLabel = 1;
    private       int                 maxLabel    = 9;
    private static final int          ABS_MAX     = 9;

    // ROI crop box: full box = (roiHalfSize*2) x (roiHalfSize*2), label centred
    private int roiHalfSize = 50;   // default: 100 x 100 px

    // Rendering constants
    private static final int   FONT_SIZE     = 14;
    private static final int   CIRCLE_RADIUS = 10;
    private static final Color BG_ALPHA      = new Color(0, 0, 0, 160);

    private final Map<Integer, Color> LABEL_COLORS = new HashMap<Integer, Color>();

    // ── Control panel ─────────────────────────────────────────────────────────
    private AnnotatorPanel controlPanel = null;

    // ── Constructor ───────────────────────────────────────────────────────────
    public Cell_Annotator() {
        LABEL_COLORS.put(Integer.valueOf(1), new Color(255,  80,  80));
        LABEL_COLORS.put(Integer.valueOf(2), new Color( 80, 160, 255));
        LABEL_COLORS.put(Integer.valueOf(3), new Color( 80, 220,  80));
        LABEL_COLORS.put(Integer.valueOf(4), new Color(255, 200,   0));
        LABEL_COLORS.put(Integer.valueOf(5), new Color(220, 100, 255));
        LABEL_COLORS.put(Integer.valueOf(6), new Color(255, 160,  50));
        LABEL_COLORS.put(Integer.valueOf(7), new Color(  0, 220, 200));
        LABEL_COLORS.put(Integer.valueOf(8), new Color(255, 130, 200));
        LABEL_COLORS.put(Integer.valueOf(9), new Color(180, 255, 100));

        // ImageJ constructs remembered tools during startup. Keep this UI-free.
    }

    @Override
    public void run(String arg) {
        super.run(arg);
        IJ.addEventListener(this);
        eventOccurred(IJEventListener.TOOL_CHANGED);
    }

    @Override
    public void eventOccurred(int eventID) {
        if (eventID != IJEventListener.TOOL_CHANGED) return;
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                boolean active = Toolbar.getPlugInTool() == Cell_Annotator.this;
                if (active || controlPanel != null) toolToggled(active);
            }
        });
    }

    public String getToolName() { return "Cell Annotator"; }
    public String getToolIcon() { return "C000T4b14C"; }

    // =========================================================================
    // TOOL TOGGLE
    // =========================================================================
    public void toolToggled(boolean active) {
        IJ.log("[Cell_Annotator] toolToggled: active=" + active);
        if (active) {
            openPanel();
        } else {
            closePanel();
        }
    }

    // =========================================================================
    // OPTIONS DIALOG  (double-click tool icon) -- raises / opens panel
    // =========================================================================
    public void showOptionsDialog() {
        IJ.log("[Cell_Annotator] showOptionsDialog called");
        if (controlPanel == null || !controlPanel.isVisible()) {
            openPanel();
        } else {
            controlPanel.toFront();
        }
    }

    // ── Panel lifecycle ───────────────────────────────────────────────────────

    private void openPanel() {
        IJ.log("[Cell_Annotator] openPanel called");
        if (controlPanel != null && controlPanel.isVisible()) {
            IJ.log("[Cell_Annotator] openPanel: already open");
            controlPanel.toFront();
            return;
        }
        IJ.log("[Cell_Annotator] openPanel: creating AnnotatorPanel ...");
        try {
            controlPanel = new AnnotatorPanel();
            IJ.log("[Cell_Annotator] openPanel: OK, visible=" + controlPanel.isVisible());
        } catch (Exception ex) {
            IJ.log("[Cell_Annotator] openPanel: EXCEPTION: " + ex);
            IJ.log("[Cell_Annotator] " + stackTraceToString(ex));
        }
    }

    private void closePanel() {
        IJ.log("[Cell_Annotator] closePanel called");
        if (controlPanel != null) {
            controlPanel.setVisible(false);
            controlPanel.dispose();
            controlPanel = null;
            IJ.log("[Cell_Annotator] closePanel: disposed");
        }
    }

    // =========================================================================
    // PERSISTENT CONTROL PANEL
    // =========================================================================

    private class AnnotatorPanel extends PlugInFrame implements ActionListener {

        private Choice labelChoice;
        private Label  countLabel;
        private Label  perLabelLabel;
        private Button undoButton;
        private Button clearButton;
        private Button exportCSVButton;
        private Button exportROIButton;
        private Button optionsButton;

        AnnotatorPanel() {
            super("Cell Annotator Controls");
            IJ.log("[Cell_Annotator] AnnotatorPanel constructor start");

            Panel main = new Panel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets  = new Insets(3, 6, 3, 6);
            gbc.fill    = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;

            int row = 0;

            // ── Active label ──────────────────────────────────────────────────
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
            main.add(new Label("Active label:"), gbc);

            labelChoice = new Choice();
            for (int i = 1; i <= maxLabel; i++) labelChoice.add("Label " + i);
            labelChoice.select(activeLabel - 1);
            labelChoice.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    activeLabel = labelChoice.getSelectedIndex() + 1;
                    IJ.log("[Cell_Annotator] label choice -> " + activeLabel);
                    IJ.showStatus("Cell Annotator: active label = " + activeLabel);
                }
            });
            gbc.gridx = 1; gbc.gridy = row++;
            main.add(labelChoice, gbc);

            // ── Separator ─────────────────────────────────────────────────────
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            main.add(new Label("-----------------------------------------"), gbc);

            // ── Total count ───────────────────────────────────────────────────
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
            main.add(new Label("Total annotations:"), gbc);
            countLabel = new Label("0");
            gbc.gridx = 1; gbc.gridy = row++;
            main.add(countLabel, gbc);

            // ── Per-label counts ──────────────────────────────────────────────
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            perLabelLabel = new Label(buildPerLabelText());
            main.add(perLabelLabel, gbc);

            // ── Separator ─────────────────────────────────────────────────────
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            main.add(new Label("-----------------------------------------"), gbc);

            // ── Buttons ───────────────────────────────────────────────────────
            undoButton      = new Button("Undo last  (Ctrl+Z)");
            clearButton     = new Button("Clear all annotations");
            exportCSVButton = new Button("Export coordinates (CSV)");
            exportROIButton = new Button("Export ROI crop images...");
            optionsButton   = new Button("More options...");

            Button[] btns = {undoButton, clearButton,
                             exportCSVButton, exportROIButton, optionsButton};
            for (int i = 0; i < btns.length; i++) {
                btns[i].addActionListener(this);
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                main.add(btns[i], gbc);
            }

            // ── Hint ──────────────────────────────────────────────────────────
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            main.add(new Label("Lclick = place label"), gbc);
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            main.add(new Label("Caps Lock = switch label 1 -> 2"), gbc);

            add(main);
            IJ.log("[Cell_Annotator] AnnotatorPanel: pack()");
            pack();
            IJ.log("[Cell_Annotator] AnnotatorPanel: setVisible(true)");
            setVisible(true);
            IJ.log("[Cell_Annotator] AnnotatorPanel: done, visible=" + isVisible());
        }

        private String buildPerLabelText() {
            Map<Integer, Integer> counts = countByLabel();
            StringBuilder sb = new StringBuilder();
            for (int lbl = 1; lbl <= maxLabel; lbl++) {
                if (sb.length() > 0) sb.append("  ");
                Integer cnt = (Integer) counts.get(Integer.valueOf(lbl));
                sb.append("L").append(lbl).append(":")
                  .append(cnt == null ? 0 : cnt.intValue());
            }
            return sb.toString();
        }

        void refresh() {
            IJ.log("[Cell_Annotator] refresh() total=" + annotations.size());
            countLabel.setText(String.valueOf(annotations.size()));
            perLabelLabel.setText(buildPerLabelText());
            int current = labelChoice.getItemCount();
            if (current != maxLabel) {
                labelChoice.removeAll();
                for (int i = 1; i <= maxLabel; i++) labelChoice.add("Label " + i);
            }
            int sel = activeLabel - 1;
            if (sel >= 0 && sel < labelChoice.getItemCount())
                labelChoice.select(sel);
            pack();
        }

        public void actionPerformed(ActionEvent e) {
            IJ.log("[Cell_Annotator] button: " + e.getActionCommand());
            ImagePlus imp = WindowManager.getCurrentImage();
            Object src = e.getSource();
            if (src == undoButton) {
                undoLast(imp);
            } else if (src == clearButton) {
                if (IJ.showMessageWithCancel("Confirm",
                        "Delete ALL " + annotations.size() + " annotations?")) {
                    annotations.clear();
                    if (imp != null) { imp.setOverlay(null); imp.updateAndDraw(); }
                    IJ.showStatus("Cell Annotator: cleared");
                    IJ.log("[Cell_Annotator] All annotations cleared");
                    refresh();
                }
            } else if (src == exportCSVButton) {
                exportCSV(imp);
            } else if (src == exportROIButton) {
                exportROIImages(imp);
            } else if (src == optionsButton) {
                showFullOptionsDialog();
            }
        }
    }

    // =========================================================================
    // MOUSE
    // =========================================================================

    public void mousePressed(ImagePlus imp, MouseEvent e) {
        debugLog("[mouse] button=" + e.getButton()
            + " screen=(" + e.getX() + "," + e.getY() + ")");

        if (imp == null) { debugLog("[mouse] SKIP: no image"); return; }
        ImageCanvas ic = imp.getCanvas();
        if (ic == null) { debugLog("[mouse] SKIP: null canvas"); return; }

        int sx = ic.offScreenX(e.getX());
        int sy = ic.offScreenY(e.getY());

        if (sx < 0 || sy < 0 || sx >= imp.getWidth() || sy >= imp.getHeight()) {
            debugLog("[mouse] SKIP: out of bounds"); e.consume(); return;
        }

        int label;
        if (SwingUtilities.isRightMouseButton(e)) {
            label = 2;
        } else {
            boolean capsLock = false;
            try {
                capsLock = Toolkit.getDefaultToolkit()
                    .getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
            } catch (Exception ignored) {}
            label = capsLock ? 2 : activeLabel;
        }

        debugLog("[mouse] placing label=" + label);
        addAnnotation(imp, sx, sy, label);
        e.consume();
    }

    // =========================================================================
    // KEYBOARD
    // =========================================================================

    public void keyPressed(ImagePlus imp, KeyEvent e) {
        char c = e.getKeyChar();
        debugLog("[key] char='" + c + "' code=" + e.getKeyCode());

        if (c >= '1' && c <= '9') {
            int num = c - '0';
            if (num <= maxLabel) {
                activeLabel = num;
                IJ.showStatus("Cell Annotator: active label = " + activeLabel);
                if (controlPanel != null) controlPanel.refresh();
            } else {
                IJ.showStatus("Cell Annotator: label " + num
                    + " not active (max=" + maxLabel + ")");
            }
            e.consume();
            return;
        }
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
            undoLast(imp);
            e.consume();
        }
    }

    // =========================================================================
    // ADD / UNDO
    // =========================================================================

    // Windows/canvases on which the Ctrl+Z KeyListener is already installed
    private final java.util.Set<Component> undoListenerTargets =
        new java.util.HashSet<Component>();

    /** Attaches a Ctrl+Z KeyListener to the image canvas and window the first time. */
    private void ensureUndoKeyListener(ImagePlus imp) {
        if (imp == null) return;
        ImageCanvas ic = imp.getCanvas();
        if (ic != null && !undoListenerTargets.contains(ic)) {
            ic.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
                        undoLast(WindowManager.getCurrentImage());
                        e.consume();
                    }
                }
            });
            undoListenerTargets.add(ic);
            debugLog("[undo-listener] installed on canvas of: " + imp.getTitle());
        }
        Window win = imp.getWindow();
        if (win != null && !undoListenerTargets.contains(win)) {
            win.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
                        undoLast(WindowManager.getCurrentImage());
                        e.consume();
                    }
                }
            });
            undoListenerTargets.add(win);
            debugLog("[undo-listener] installed on window of: " + imp.getTitle());
        }
    }

    private void addAnnotation(ImagePlus imp, int x, int y, int label) {
        annotations.add(new Annotation(x, y, label));
        debugLog("[add] label=" + label + " x=" + x + " y=" + y
            + " total=" + annotations.size());
        ensureUndoKeyListener(imp);
        refreshOverlay(imp);
        updateStatusBar();
        if (controlPanel != null) controlPanel.refresh();
    }

    private void undoLast(ImagePlus imp) {
        if (!annotations.isEmpty()) {
            Annotation rem = (Annotation) annotations.remove(annotations.size() - 1);
            debugLog("[undo] removed=" + rem + " remain=" + annotations.size());
            refreshOverlay(imp);
            updateStatusBar();
            if (controlPanel != null) controlPanel.refresh();
            IJ.showStatus("Cell Annotator: undo | " + annotations.size() + " remain");
        } else {
            IJ.showStatus("Cell Annotator: nothing to undo");
        }
    }

    // =========================================================================
    // OVERLAY RENDERING
    // =========================================================================

    private void refreshOverlay(ImagePlus imp) {
        Overlay overlay = new Overlay();
        java.awt.Font font = new java.awt.Font("SansSerif", java.awt.Font.BOLD, FONT_SIZE);

        for (int i = 0; i < annotations.size(); i++) {
            Annotation a = (Annotation) annotations.get(i);
            Color col = (Color) LABEL_COLORS.get(Integer.valueOf(a.label));
            if (col == null) col = Color.WHITE;

            OvalRoi circle = new OvalRoi(
                a.x - CIRCLE_RADIUS, a.y - CIRCLE_RADIUS,
                CIRCLE_RADIUS * 2,   CIRCLE_RADIUS * 2);
            circle.setFillColor(BG_ALPHA);
            circle.setStrokeColor(col);
            circle.setStrokeWidth(1.5);
            overlay.add(circle);

            TextRoi text = new TextRoi(
                a.x - FONT_SIZE / 3, a.y - FONT_SIZE / 2,
                String.valueOf(a.label), font);
            text.setStrokeColor(col);
            text.setAntialiased(true);
            overlay.add(text);
        }

        imp.setOverlay(overlay);
        imp.updateAndDraw();
    }

    // =========================================================================
    // STATUS BAR
    // =========================================================================

    private void updateStatusBar() {
        Map<Integer, Integer> counts = countByLabel();
        StringBuilder sb = new StringBuilder(
            "Cell Annotator | Active:" + activeLabel
            + " | Total:" + annotations.size());
        Iterator<Map.Entry<Integer, Integer>> it = counts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = (Map.Entry<Integer, Integer>) it.next();
            sb.append(" | ").append(e.getKey()).append(":").append(e.getValue());
        }
        IJ.showStatus(sb.toString());
    }

    private Map<Integer, Integer> countByLabel() {
        Map<Integer, Integer> counts = new TreeMap<Integer, Integer>();
        for (int i = 0; i < annotations.size(); i++) {
            Annotation a = (Annotation) annotations.get(i);
            Integer key = Integer.valueOf(a.label);
            Integer v   = (Integer) counts.get(key);
            counts.put(key, Integer.valueOf(v == null ? 1 : v.intValue() + 1));
        }
        return counts;
    }

    // =========================================================================
    // ROI CROP IMAGE EXPORT
    // =========================================================================

    private void exportROIImages(ImagePlus imp) {
        IJ.log("[Cell_Annotator] exportROIImages called");

        if (imp == null) {
            IJ.error("Cell Annotator", "No image is open.");
            return;
        }
        if (annotations.isEmpty()) {
            IJ.error("Cell Annotator", "No annotations to export.");
            return;
        }

        GenericDialog gd = new GenericDialog("Export ROI Crop Images");
        gd.addMessage("A square crop box is centred on each annotation point.");
        gd.addNumericField("Box half-size (px)  [full box = 2 x this value]:", roiHalfSize, 0);
        String[] formats = {"TIFF", "PNG", "JPEG", "BMP"};
        gd.addChoice("Image format:", formats, formats[0]);
        gd.addMessage("Click OK then choose the output folder.");
        gd.showDialog();
        if (gd.wasCanceled()) {
            IJ.log("[Cell_Annotator] exportROIImages: cancelled at settings");
            return;
        }

        int half = (int) gd.getNextNumber();
        if (half < 1) half = 1;
        roiHalfSize = half;
        String fmt  = gd.getNextChoice();
        IJ.log("[Cell_Annotator] ROI export: halfSize=" + roiHalfSize
            + " format=" + fmt + " count=" + annotations.size());

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select output folder for ROI images");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            IJ.log("[Cell_Annotator] exportROIImages: folder selection cancelled");
            return;
        }
        File outDir = fc.getSelectedFile();
        IJ.log("[Cell_Annotator] ROI export: outputDir=" + outDir.getAbsolutePath());

        String ext;
        if      (fmt.equals("TIFF"))  ext = "tif";
        else if (fmt.equals("PNG"))   ext = "png";
        else if (fmt.equals("JPEG"))  ext = "jpg";
        else if (fmt.equals("BMP"))   ext = "bmp";
        else                          ext = "tif";

        String baseName = imp.getTitle().replaceAll("\\.[^.]+$", "");
        int imgW  = imp.getWidth();
        int imgH  = imp.getHeight();
        int boxSz = roiHalfSize * 2;
        int exported = 0;
        int skipped  = 0;

        for (int i = 0; i < annotations.size(); i++) {
            Annotation a = (Annotation) annotations.get(i);

            if (a.x < 0 || a.x >= imgW || a.y < 0 || a.y >= imgH) {
                IJ.log("[Cell_Annotator] SKIP [" + i + "] centre out of bounds");
                skipped++;
                continue;
            }

            int cx0 = Math.max(0, a.x - roiHalfSize);
            int cy0 = Math.max(0, a.y - roiHalfSize);
            int cx1 = Math.min(imgW, a.x - roiHalfSize + boxSz);
            int cy1 = Math.min(imgH, a.y - roiHalfSize + boxSz);
            int cw  = cx1 - cx0;
            int ch  = cy1 - cy0;

            if (cw <= 0 || ch <= 0) {
                IJ.log("[Cell_Annotator] SKIP [" + i + "] zero crop area");
                skipped++;
                continue;
            }

            ImageProcessor srcProc = imp.getProcessor().duplicate();
            srcProc.setRoi(cx0, cy0, cw, ch);
            ImageProcessor cropped = srcProc.crop();
            srcProc = null;

            String fname = baseName
                + "_label" + a.label
                + "_" + (i + 1)
                + "_x" + a.x
                + "_y" + a.y
                + "." + ext;
            File outFile = new File(outDir, fname);

            ImagePlus crop = new ImagePlus(fname, cropped);
            FileSaver  fs  = new FileSaver(crop);
            boolean ok     = false;

            if      (ext.equals("tif")) ok = fs.saveAsTiff(outFile.getAbsolutePath());
            else if (ext.equals("png")) ok = fs.saveAsPng(outFile.getAbsolutePath());
            else if (ext.equals("jpg")) ok = fs.saveAsJpeg(outFile.getAbsolutePath());
            else if (ext.equals("bmp")) ok = fs.saveAsBmp(outFile.getAbsolutePath());

            crop    = null;
            cropped = null;

            if (ok) {
                exported++;
                IJ.log("[Cell_Annotator] saved [" + (i + 1) + "/" + annotations.size()
                    + "] " + fname);
            } else {
                skipped++;
                IJ.log("[Cell_Annotator] FAILED [" + i + "] " + fname);
            }

            IJ.showStatus("Exporting ROI " + (i + 1) + " / " + annotations.size());
            IJ.showProgress(i + 1, annotations.size());
        }

        IJ.showProgress(1.0);
        IJ.log("[Cell_Annotator] ROI export done: exported=" + exported
            + " skipped=" + skipped);
        IJ.showStatus("ROI export done: " + exported + " saved, " + skipped + " skipped");
        IJ.showMessage("ROI Export Complete",
            exported + " image(s) saved to:\n" + outDir.getAbsolutePath()
            + (skipped > 0
               ? "\n\n" + skipped + " annotation(s) skipped -- see Log for details."
               : ""));
    }

    // =========================================================================
    // FULL OPTIONS DIALOG
    // =========================================================================

    private void showFullOptionsDialog() {
        IJ.log("[Cell_Annotator] showFullOptionsDialog called");
        Map<Integer, Integer> counts = countByLabel();

        GenericDialog gd = new GenericDialog("Cell Annotator Options");

        gd.addMessage("=== ANNOTATION COUNTS ===");
        for (int lbl = 1; lbl <= maxLabel; lbl++) {
            Integer cnt = (Integer) counts.get(Integer.valueOf(lbl));
            int c = (cnt == null) ? 0 : cnt.intValue();
            gd.addMessage("Label " + lbl + " :  " + c);
        }
        gd.addMessage("Total   :  " + annotations.size());

        gd.addMessage(" ");
        gd.addMessage("=== SETTINGS ===");
        gd.addNumericField("Number of labels (2-9):", maxLabel, 0);

        gd.addMessage(" ");
        gd.addMessage("=== ACTIONS ===");
        String[] actions = {
            "(no action)",
            "Export label counts to CSV",
            "Export full coordinates to CSV",
            "Save session (CSV)",
            "Load session (CSV)",
            "Print debug summary to Console",
            "Toggle debug logging (currently " + (DEBUG ? "ON)" : "OFF)"),
            "Clear all annotations"
        };
        gd.addChoice("Action:", actions, actions[0]);

        gd.showDialog();
        if (gd.wasCanceled()) {
            IJ.log("[Cell_Annotator] options dialog cancelled");
            return;
        }

        int newMax = (int) gd.getNextNumber();
        if (newMax < 2) newMax = 2;
        if (newMax > ABS_MAX) newMax = ABS_MAX;
        if (newMax != maxLabel) {
            maxLabel = newMax;
            if (activeLabel > maxLabel) activeLabel = maxLabel;
            IJ.log("[Cell_Annotator] maxLabel -> " + maxLabel);
            IJ.showStatus("Cell Annotator: max labels = " + maxLabel);
        }

        String action = gd.getNextChoice();
        IJ.log("[Cell_Annotator] options action='" + action + "'");
        ImagePlus imp = WindowManager.getCurrentImage();

        if      (action.startsWith("Export label counts to CSV"))       exportCountsCSV(imp);
        else if (action.startsWith("Export full coordinates to CSV"))   exportCSV(imp);
        else if (action.startsWith("Save session"))   saveSession();
        else if (action.startsWith("Load session"))   loadSession(imp);
        else if (action.startsWith("Print debug"))    printDebugSummary(imp);
        else if (action.startsWith("Toggle debug")) {
            DEBUG = !DEBUG;
            IJ.log("[Cell_Annotator] Debug logging " + (DEBUG ? "ENABLED" : "DISABLED"));
            IJ.showStatus("Cell Annotator: debug " + (DEBUG ? "ON" : "OFF"));
        } else if (action.startsWith("Clear all")) {
            if (IJ.showMessageWithCancel("Confirm",
                    "Delete ALL " + annotations.size() + " annotations?")) {
                annotations.clear();
                if (imp != null) { imp.setOverlay(null); imp.updateAndDraw(); }
                IJ.log("[Cell_Annotator] All annotations cleared");
                IJ.showStatus("Cell Annotator: cleared");
            }
        }

        if (controlPanel != null) controlPanel.refresh();
    }

    // =========================================================================
    // EXPORT: LABEL COUNTS (CSV)
    // =========================================================================

    private void exportCountsCSV(ImagePlus imp) {
        if (annotations.isEmpty()) { IJ.error("No annotations to export."); return; }
        String imageName   = (imp != null) ? imp.getTitle() : "unknown_image";
        String defaultName = imageName.replaceAll("\\.[^.]+$", "") + "_label_counts.csv";
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));
        fc.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().endsWith(".csv")) file = new File(file.getPath() + ".csv");
        exportCountsCSVToFile(file.getPath(), imageName);
    }

    private void exportCountsCSVToFile(String path, String imageName) {
        Map<Integer, Integer> counts = countByLabel();
        int total = annotations.size();
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new FileWriter(path));

            // ── Metadata block ────────────────────────────────────────────────
            pw.println("Image,Date,Total Annotations");
            pw.println("\"" + imageName + "\","
                + "\"" + new java.util.Date() + "\","
                + total);
            pw.println(); // blank spacer row

            // ── Column headers ────────────────────────────────────────────────
            // % of Total is a plain 4-decimal fraction (e.g. 0.6760) so that
            // spreadsheet apps treat it as a number ready for % formatting,
            // and pandas/R can use it without string parsing.
            pw.println("Label,Count,% of Total");

            // ── Data rows ─────────────────────────────────────────────────────
            for (int lbl = 1; lbl <= maxLabel; lbl++) {
                Integer cnt = (Integer) counts.get(Integer.valueOf(lbl));
                int c = (cnt == null) ? 0 : cnt.intValue();
                double pct = (total > 0) ? (c / (double) total) : 0.0;
                pw.println("Label " + lbl + "," + c + ","
                    + String.format("%.4f", pct));
            }

            // ── Total row ─────────────────────────────────────────────────────
            pw.println("TOTAL," + total + ",1.0000");

            IJ.log("[Cell_Annotator] Label counts CSV: " + path);
            IJ.showMessage("Export complete", "Saved to:\n" + path);
        } catch (IOException ex) {
            IJ.log("[Cell_Annotator] Counts CSV error: " + ex.getMessage());
            IJ.error("CSV export failed: " + ex.getMessage());
        } finally {
            if (pw != null) pw.close();
        }
    }

    // =========================================================================
    // DEBUG SUMMARY
    // =========================================================================

    private void printDebugSummary(ImagePlus imp) {
        IJ.log("========================================");
        IJ.log("[Cell_Annotator] DEBUG SUMMARY");
        IJ.log("========================================");
        IJ.log("Timestamp      : " + new java.util.Date());
        IJ.log("Java version   : " + System.getProperty("java.version"));
        IJ.log("OS             : " + System.getProperty("os.name")
            + " " + System.getProperty("os.arch"));
        IJ.log("ImageJ version : " + IJ.getVersion());
        IJ.log("DEBUG flag     : " + DEBUG);
        IJ.log("Active label   : " + activeLabel);
        IJ.log("Max labels     : " + maxLabel);
        IJ.log("ROI half-size  : " + roiHalfSize
            + " px  (box=" + (roiHalfSize * 2) + "x" + (roiHalfSize * 2) + ")");
        IJ.log("Total annots   : " + annotations.size());
        IJ.log("Panel ref      : " + controlPanel);
        IJ.log("Panel visible  : " + (controlPanel != null && controlPanel.isVisible()));
        IJ.log("----------------------------------------");

        Map<Integer, Integer> counts = countByLabel();
        IJ.log("Per-label counts:");
        for (int lbl = 1; lbl <= maxLabel; lbl++) {
            Integer cnt = (Integer) counts.get(Integer.valueOf(lbl));
            int c = (cnt == null) ? 0 : cnt.intValue();
            IJ.log("  Label " + lbl + " : " + c);
        }
        IJ.log("  TOTAL        : " + annotations.size());
        IJ.log("----------------------------------------");

        if (imp == null) {
            IJ.log("Image: none open");
        } else {
            IJ.log("Image title    : " + imp.getTitle());
            IJ.log("Image size     : " + imp.getWidth() + " x " + imp.getHeight());
            IJ.log("Bit depth      : " + imp.getBitDepth());
            IJ.log("Overlay        : " + (imp.getOverlay() != null
                ? imp.getOverlay().size() + " elements" : "none"));
            if (imp.getOriginalFileInfo() != null)
                IJ.log("File path      : " + imp.getOriginalFileInfo().directory
                    + imp.getOriginalFileInfo().fileName);
        }
        IJ.log("----------------------------------------");

        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem  = Runtime.getRuntime().freeMemory();
        IJ.log("JVM used  : " + ((totalMem - freeMem) / 1024 / 1024) + " MB");
        IJ.log("JVM free  : " + (freeMem  / 1024 / 1024) + " MB");
        IJ.log("JVM total : " + (totalMem / 1024 / 1024) + " MB");
        IJ.log("----------------------------------------");

        int n = annotations.size();
        if (n == 0) {
            IJ.log("No annotations placed yet.");
        } else {
            int first = Math.min(20, n);
            IJ.log("First " + first + " annotations:");
            for (int i = 0; i < first; i++)
                IJ.log("  [" + i + "] " + annotations.get(i));
            if (n > first) {
                int lastStart = Math.max(first, n - 5);
                if (lastStart > first)
                    IJ.log("  ... (" + (lastStart - first) + " omitted) ...");
                for (int i = lastStart; i < n; i++)
                    IJ.log("  [" + i + "] " + annotations.get(i));
            }
        }
        IJ.log("========================================");
        IJ.showMessage("Debug summary printed",
            "Open  Window > Console  to view the full output.");
    }

    // =========================================================================
    // CSV EXPORT (full coordinates)
    // =========================================================================

    private void exportCSV(ImagePlus imp) {
        if (annotations.isEmpty()) { IJ.error("No annotations to export."); return; }
        String imageName   = (imp != null) ? imp.getTitle() : "unknown_image";
        String defaultName = imageName.replaceAll("\\.[^.]+$", "") + "_annotations.csv";
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));
        fc.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().endsWith(".csv")) file = new File(file.getPath() + ".csv");
        exportCSVToFile(file.getPath(), imageName);
    }

    private void exportCSVToFile(String path, String imageName) {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new FileWriter(path));
            pw.println("# Cell Annotator Export");
            pw.println("# Image: " + imageName);
            pw.println("# Date: " + new java.util.Date());
            pw.println("# Total: " + annotations.size());
            pw.println("# Java: " + System.getProperty("java.version"));
            pw.println("# ImageJ: " + IJ.getVersion());
            pw.println();
            Map<Integer, Integer> counts = countByLabel();
            pw.println("=== SUMMARY ===");
            pw.println("Label,Count");
            for (int lbl = 1; lbl <= maxLabel; lbl++) {
                Integer cnt = (Integer) counts.get(Integer.valueOf(lbl));
                int c = (cnt == null) ? 0 : cnt.intValue();
                pw.println("Label " + lbl + "," + c);
            }
            pw.println("TOTAL," + annotations.size());
            pw.println();
            pw.println("=== COORDINATES ===");
            pw.println("Index,Label,X,Y");
            for (int i = 0; i < annotations.size(); i++) {
                Annotation a = (Annotation) annotations.get(i);
                pw.println((i + 1) + "," + a.label + "," + a.x + "," + a.y);
            }
            IJ.log("[Cell_Annotator] CSV OK: " + annotations.size() + " -> " + path);
            IJ.showMessage("Export complete", "Saved to:\n" + path);
        } catch (IOException ex) {
            IJ.log("[Cell_Annotator] CSV error: " + ex.getMessage());
            IJ.error("CSV export failed: " + ex.getMessage());
        } finally {
            if (pw != null) pw.close();
        }
    }

    // =========================================================================
    // SESSION SAVE / LOAD
    // =========================================================================

    private void saveSession() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("annotation_session.csv"));
        fc.setFileFilter(new FileNameExtensionFilter("Session files (*.csv)", "csv"));
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().endsWith(".csv")) file = new File(file.getPath() + ".csv");
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new FileWriter(file));
            pw.println("# Cell_Annotator_Session_v1");
            pw.println("# Saved: " + new java.util.Date());
            pw.println("# maxLabel: " + maxLabel);
            pw.println("label,x,y");
            for (int i = 0; i < annotations.size(); i++) {
                Annotation a = (Annotation) annotations.get(i);
                pw.println(a.label + "," + a.x + "," + a.y);
            }
            IJ.log("[Cell_Annotator] Session saved: " + annotations.size()
                + " -> " + file.getAbsolutePath());
            IJ.showStatus("Session saved: " + annotations.size() + " -> " + file.getName());
        } catch (IOException ex) {
            IJ.log("[Cell_Annotator] Save error: " + ex.getMessage());
            IJ.error("Save failed: " + ex.getMessage());
        } finally {
            if (pw != null) pw.close();
        }
    }

    private void loadSession(ImagePlus imp) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Session files (*.csv)", "csv"));
        if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();

        List<Annotation> loaded = new ArrayList<Annotation>();
        int lineNum = 0, parseErrors = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line;
            boolean headerSkipped = false;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                if (!headerSkipped) { headerSkipped = true; continue; }
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    try {
                        loaded.add(new Annotation(
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()),
                            Integer.parseInt(parts[0].trim())));
                    } catch (NumberFormatException ex) {
                        parseErrors++;
                        IJ.log("[Cell_Annotator] Parse error line " + lineNum
                            + ": '" + line + "'");
                    }
                } else {
                    parseErrors++;
                }
            }
        } catch (IOException ex) {
            IJ.log("[Cell_Annotator] Load error: " + ex.getMessage());
            IJ.error("Load failed: " + ex.getMessage());
            return;
        } finally {
            if (br != null) { try { br.close(); } catch (IOException ignored) {} }
        }

        IJ.log("[Cell_Annotator] Session read: " + loaded.size()
            + " | parseErrors=" + parseErrors);

        if (!annotations.isEmpty()) {
            GenericDialog gd = new GenericDialog("Load Session");
            gd.addMessage("Existing annotations: " + annotations.size());
            gd.addMessage("Annotations to load:  " + loaded.size());
            if (parseErrors > 0) gd.addMessage("Parse errors: " + parseErrors);
            String[] modes = {"Replace existing", "Merge with existing"};
            gd.addChoice("Mode:", modes, modes[0]);
            gd.showDialog();
            if (gd.wasCanceled()) return;
            if (gd.getNextChoice().equals("Replace existing"))
                annotations.clear();
        }

        annotations.addAll(loaded);
        if (imp != null) refreshOverlay(imp);
        updateStatusBar();
        if (controlPanel != null) controlPanel.refresh();
        IJ.log("[Cell_Annotator] Load complete: " + loaded.size()
            + " loaded | total: " + annotations.size());
        IJ.showStatus("Session loaded: " + loaded.size() + " | total: " + annotations.size());
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private void debugLog(String msg) {
        if (DEBUG) IJ.log("[Cell_Annotator] " + msg);
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter  pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
