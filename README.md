# Cell Annotator

An ImageJ 1.x toolbar plugin for placing numbered cell annotations, exporting coordinates and label counts to CSV, saving/loading annotation sessions, and exporting image crops around annotations.

## Install and use

Copy `Cell_Annotator.java` into your ImageJ `plugins` directory. In ImageJ, use **Plugins > Compile and Run...** and choose that file (requires a JDK). Select the Cell Annotator toolbar icon to show its controls; double-click the icon to reopen them. Selecting another tool closes the controls. ImageJ can restore the toolbar tool at startup without opening its controls or log window.

Left-click places the active label. Right-click or Caps Lock uses label 2. Choose the active label in the controls; Ctrl+Z undoes the last annotation. The controls provide CSV export, ROI crop export, and additional settings/session actions.

## Build and regression test

With JDK 9 or later, set `IJ_JAR` to your ImageJ `ij.jar`:

```sh
mkdir -p build
javac --release 8 -cp "$IJ_JAR" -d build Cell_Annotator.java tests/StartupLifecycleTest.java
java -cp "build:$IJ_JAR" StartupLifecycleTest
```

The test opens an actual ImageJ toolbar. On Linux without a display, prefix the Java command with `xvfb-run -a`. It verifies quiet construction/startup restoration, toolbar selection, options, deselection, reselection, and explicit plugin launch. Compiled classes target Java 8.

## Source provenance

Imported from the newest local source, `Downloads/ImageJ/plugins/Cell_Annotator.java`, last modified April 2, 2026 at 17:14 EDT (SHA-256 `3f86edde76f998c5643860999f3e320d572953fbd26c9685456dcb6f6db5b72c`). Compared with the three Desktop versions; this copy adds Ctrl+Z listeners and updated controls/settings. The import fixes startup UI creation and connects the controls to ImageJ's `IJEventListener.TOOL_CHANGED` event.
