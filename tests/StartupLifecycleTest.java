import ij.IJ;
import ij.gui.Toolbar;
import java.awt.EventQueue;
import java.awt.Frame;
import java.lang.reflect.Field;

/** Integration test using ImageJ's real toolbar and AWT, under xvfb-run. */
public class StartupLifecycleTest {
    private static Cell_Annotator tool;
    private static final Field PANEL;
    static {
        try {
            PANEL = Cell_Annotator.class.getDeclaredField("controlPanel");
            PANEL.setAccessible(true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void panel(boolean visible) throws Exception {
        EventQueue.invokeAndWait(() -> {});
        Frame frame = (Frame) PANEL.get(tool);
        if ((frame != null && frame.isVisible()) != visible)
            throw new AssertionError("Expected panel visible=" + visible);
    }
    public static void main(String[] args) {
        try {
            EventQueue.invokeAndWait(() -> new ij.ImageJ());
            EventQueue.invokeAndWait(() -> {});
            EventQueue.invokeAndWait(() -> {
                Toolbar toolbar = Toolbar.getInstance();
                tool = new Cell_Annotator();
                try {
                    if (PANEL.get(tool) != null || IJ.getLog() != null)
                        throw new AssertionError("Constructor must not open controls or log");
                    // ImageJ uses this flag when restoring remembered startup tools.
                    Field startup = Toolbar.class.getDeclaredField("installingStartupTool");
                    startup.setAccessible(true);
                    startup.setBoolean(toolbar, true);
                    tool.run("");
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            panel(false);
            if (IJ.getLog() != null) throw new AssertionError("Startup opened the log");
            EventQueue.invokeAndWait(() -> Toolbar.getInstance().setTool(Toolbar.getInstance().getToolId("Cell Annotator")));
            panel(true);
            Object first = PANEL.get(tool);
            EventQueue.invokeAndWait(() -> tool.showOptionsDialog());
            panel(true);
            if (PANEL.get(tool) != first) throw new AssertionError("Duplicate panel");
            EventQueue.invokeAndWait(() -> Toolbar.getInstance().setTool(Toolbar.RECTANGLE));
            panel(false);
            EventQueue.invokeAndWait(() -> Toolbar.getInstance().setTool(Toolbar.getInstance().getToolId("Cell Annotator")));
            panel(true);
            EventQueue.invokeAndWait(() -> Toolbar.getInstance().setTool(Toolbar.RECTANGLE));
            panel(false);
            EventQueue.invokeAndWait(() -> tool.run(""));
            panel(true);
            System.out.println("PASS: quiet startup, selection, options, deselection, reselection, manual launch");
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
