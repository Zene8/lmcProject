package com.lmc.ide;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import java.net.URL;
import java.util.prefs.Preferences;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;

public class App extends Application {

    private UIController uiController;
    private FileManager fileManager;
    private LMCExecutor lmcExecutor;
    private LMCInterpreter interpreter;
    private LMCParser parser;
    private LMCIDEFeatures ideFeatures;
    private Preferences prefs;
    private Button startButton, stopButton, stepButton, resetButton;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("LMC IDE");
        prefs = Preferences.userNodeForPackage(App.class);

        // --- Initialize Core Components ---
        interpreter = new LMCInterpreter();
        parser = new LMCParser();

        // --- Initialize Controllers & Managers ---
        uiController = new UIController(primaryStage);
        fileManager = new FileManager(primaryStage, uiController);
        lmcExecutor = new LMCExecutor(interpreter, parser, uiController);

        // --- Link Core Controllers for UI Initialization ---
        uiController.setFileManager(fileManager);
        uiController.setLmcExecutor(lmcExecutor);

        // --- Finalize UI construction now that its core dependencies are set ---
        uiController.initComponents();

        // --- Setup IDE Features now that UI is fully initialized ---
        ideFeatures = new LMCIDEFeatures(uiController, parser, uiController.getMemoryUsageLabel(),
                prefs.getBoolean("autocorrectEnabled", true),
                prefs.getBoolean("autoFormattingEnabled", true),
                prefs.getBoolean("errorHighlightingEnabled", true));

        // --- Link remaining dependencies ---
        uiController.setIdeFeatures(ideFeatures);
        lmcExecutor.setIdeFeatures(ideFeatures);

        // --- Build Scene ---
        BorderPane root = new BorderPane();
        root.setTop(createMenuBar());
        root.setCenter(uiController.getRoot());

        Scene scene = new Scene(root, 1600, 1000);
        loadStylesheet(scene, "/vscode-style.css");
        loadStylesheet(scene, "/lmc-syntax.css");

        primaryStage.setScene(scene);
        primaryStage.show();

        uiController.setupHotkeys(scene);
        uiController.applyTheme(prefs.get("theme", "dark-mode"));
        fileManager.newFile();
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File Menu
        Menu fileMenu = new Menu("File");
        MenuItem newFile = new MenuItem("New", uiController.createIcon("insert_drive_file.svg"));
        newFile.setOnAction(e -> fileManager.newFile());
        MenuItem openProject = new MenuItem("Open", uiController.createIcon("folder_open.svg"));
        openProject.setOnAction(e -> fileManager.openProject());
        MenuItem saveFile = new MenuItem("Save", uiController.createIcon("save.svg"));
        saveFile.setOnAction(e -> fileManager.saveFile());
        MenuItem closeTab = new MenuItem("Close Tab");
        closeTab.setOnAction(e -> fileManager.closeCurrentTab());
        fileMenu.getItems().addAll(newFile, openProject, saveFile, closeTab);

        // Edit Menu
        Menu editMenu = new Menu("Edit");
        MenuItem undo = new MenuItem("Undo");
        undo.setOnAction(e -> uiController.getCurrentCodeArea().undo());
        MenuItem redo = new MenuItem("Redo");
        redo.setOnAction(e -> uiController.getCurrentCodeArea().redo());
        MenuItem find = new MenuItem("Find", uiController.createIcon("search.svg"));
        find.setOnAction(e -> uiController.toggleFindPopup());
        MenuItem replace = new MenuItem("Replace", uiController.createIcon("find_replace.svg"));
        replace.setOnAction(e -> uiController.toggleReplacePopup());
        MenuItem formatCode = new MenuItem("Format Code");
        formatCode.setOnAction(e -> {
            CodeArea currentCodeArea = uiController.getCurrentCodeArea();
            if (currentCodeArea != null) {
                String formattedText = LMCFormatter.format(currentCodeArea.getText());
                currentCodeArea.replaceText(formattedText);
            }
        });
        editMenu.getItems().addAll(undo, redo, find, replace, formatCode);

        // View Menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem toggleTools = new CheckMenuItem("Toggle Tools Sidebar");
        toggleTools.setSelected(true);
        toggleTools.setOnAction(e -> uiController.toggleToolsSidebar());
        CheckMenuItem toggleLeft = new CheckMenuItem("Toggle File Explorer");
        toggleLeft.setSelected(true);
        toggleLeft.setOnAction(e -> uiController.toggleLeftSidebar());
        CheckMenuItem toggleConsole = new CheckMenuItem("Toggle Console View");
        toggleConsole.setSelected(true);
        toggleConsole.setOnAction(e -> uiController.toggleConsoleView());
        CheckMenuItem maximizeEditor = new CheckMenuItem("Maximize Editor");
        maximizeEditor.setSelected(false);
        maximizeEditor.setOnAction(e -> uiController.toggleEditorMaximize());
        MenuItem toggleTheme = new MenuItem("Toggle Theme");
        toggleTheme.setOnAction(e -> toggleTheme());

        // Feature Toggles
        CheckMenuItem toggleAutocorrect = new CheckMenuItem("Autocorrect");
        toggleAutocorrect.setSelected(prefs.getBoolean("autocorrectEnabled", true));
        toggleAutocorrect.setOnAction(e -> {
            boolean enabled = toggleAutocorrect.isSelected();
            prefs.putBoolean("autocorrectEnabled", enabled);
            ideFeatures.setAutocorrectEnabled(enabled);
        });

        CheckMenuItem toggleAutoFormat = new CheckMenuItem("Auto Formatting on Paste");
        toggleAutoFormat.setSelected(prefs.getBoolean("autoFormattingEnabled", true));
        toggleAutoFormat.setOnAction(e -> {
            boolean enabled = toggleAutoFormat.isSelected();
            prefs.putBoolean("autoFormattingEnabled", enabled);
            ideFeatures.setAutoFormattingEnabled(enabled);
        });

        CheckMenuItem toggleErrorHighlighting = new CheckMenuItem("Error Highlighting");
        toggleErrorHighlighting.setSelected(prefs.getBoolean("errorHighlightingEnabled", true));
        toggleErrorHighlighting.setOnAction(e -> {
            boolean enabled = toggleErrorHighlighting.isSelected();
            prefs.putBoolean("errorHighlightingEnabled", enabled);
            ideFeatures.setErrorHighlightingEnabled(enabled);
        });

        viewMenu.getItems().addAll(toggleTools, toggleLeft, toggleConsole, maximizeEditor, toggleTheme,
                new SeparatorMenuItem(), toggleAutocorrect, toggleAutoFormat, toggleErrorHighlighting);

        // Code Menu
        Menu codeMenu = new Menu("Code");
        Menu insertSnippet = new Menu("Insert Snippet");

        MenuItem snippetInputOutput = new MenuItem("Input/Output Loop");
        snippetInputOutput.setOnAction(e -> {
            CodeArea currentCodeArea = uiController.getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP\n        STA LOOP_VAR\nLOOP    LDA LOOP_VAR\n        OUT\n        BRZ END\n        INP\n        STA LOOP_VAR\n        BRA LOOP\nEND     HLT\nLOOP_VAR DAT 0\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetAddTwoNumbers = new MenuItem("Add Two Numbers");
        snippetAddTwoNumbers.setOnAction(e -> {
            CodeArea currentCodeArea = uiController.getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP\n        STA NUM1\n        INP\n        STA NUM2\n        LDA NUM1\n        ADD NUM2\n        OUT\n        HLT\nNUM1    DAT 0\nNUM2    DAT 0\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetDataDefinition = new MenuItem("Data Definition");
        snippetDataDefinition.setOnAction(e -> {
            CodeArea currentCodeArea = uiController.getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "MY_DATA DAT 10\nANOTHER_DATA DAT 20\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetMultiplication = new MenuItem("Multiplication");
        snippetMultiplication.setOnAction(e -> {
            CodeArea currentCodeArea = uiController.getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP         // Input Multiplicand (A)\n        STA MULTIPLICAND\n        INP         // Input Multiplier (B)\n        STA MULTIPLIER\n        LDA ZERO    // Initialize Result to 0\n        STA RESULT\n\nLOOP    LDA MULTIPLIER\n        BRZ END_LOOP // If Multiplier is 0, done\n        SUB ONE     // Decrement Multiplier\n        STA MULTIPLIER\n        LDA RESULT\n        ADD MULTIPLICAND // Add Multiplicand to Result\n        STA RESULT\n        BRA LOOP\n\nEND_LOOP LDA RESULT\n        OUT         // Output Result\n        HLT\n\nMULTIPLICAND DAT 0\nMULTIPLIER   DAT 0\nRESULT       DAT 0\nZERO         DAT 0\nONE          DAT 1\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetDivision = new MenuItem("Division");
        snippetDivision.setOnAction(e -> {
            CodeArea currentCodeArea = uiController.getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP         // Input Dividend (A)\n        STA DIVIDEND\n        INP         // Input Divisor (B)\n        STA DIVISOR\n        LDA ZERO    // Initialize Quotient to 0\n        STA QUOTIENT\n\nLOOP    LDA DIVIDEND\n        SUB DIVISOR // Dividend - Divisor\n        BRP CONTINUE // If result >= 0, continue\n        BRA END_LOOP // If result < 0, done\n\nCONTINUE STA DIVIDEND // Store new Dividend\n        LDA QUOTIENT\n        ADD ONE     // Increment Quotient\n        STA QUOTIENT\n        BRA LOOP\n\nEND_LOOP LDA QUOTIENT\n        OUT         // Output Quotient\n        HLT\n\nDIVIDEND DAT 0\nDIVISOR  DAT 0\nQUOTIENT DAT 0\nZERO     DAT 0\nONE      DAT 1\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetConditionalBranching = new MenuItem("Conditional Branching (IF-ELSE)");
        snippetConditionalBranching.setOnAction(e -> {
            CodeArea currentCodeArea = uiController.getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP         // Input a number\n        STA NUM\n        LDA NUM\n        BRZ IS_ZERO // If NUM == 0, branch to IS_ZERO\n\n        // ELSE block (NUM is not zero)\n        LDA NUM\n        OUT         // Output NUM\n        BRA END_IF\n\nIS_ZERO LDA ZERO\n        OUT         // Output 0\n\nEND_IF  HLT\n\nNUM     DAT 0\nZERO    DAT 0\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        insertSnippet.getItems().addAll(snippetInputOutput, snippetAddTwoNumbers, snippetDataDefinition,
                snippetMultiplication, snippetDivision, snippetConditionalBranching);
        codeMenu.getItems().addAll(insertSnippet);

        menuBar.getMenus().addAll(fileMenu, editMenu, codeMenu, viewMenu);
        return menuBar;
    }

    private HBox createControlPanel() {
        startButton = new Button("", uiController.createIcon("play_arrow.svg", 24));
        startButton.setTooltip(new Tooltip("Run Program"));
        stopButton = new Button("", uiController.createIcon("stop.svg", 24));
        stopButton.setTooltip(new Tooltip("Stop Program"));
        stepButton = new Button("", uiController.createIcon("skip_next.svg", 24));
        stepButton.setTooltip(new Tooltip("Step Program"));
        resetButton = new Button("", uiController.createIcon("refresh.svg", 24));
        resetButton.setTooltip(new Tooltip("Reset Program"));

        // Set actions
        startButton.setOnAction(e -> lmcExecutor.runLMC());
        stopButton.setOnAction(e -> lmcExecutor.stopLMC());
        stepButton.setOnAction(e -> lmcExecutor.executeStep());
        resetButton.setOnAction(e -> lmcExecutor.resetProgram()); // FIX: Point to executor

        // Pass controls to LMCExecutor
        // FIX: Added the missing resetButton argument
        lmcExecutor.setControls(startButton, stopButton, stepButton, resetButton);

        // Speed controls
        ToggleButton speedModeToggle = new ToggleButton("Slow Mode");
        Slider speedSlider = new Slider(10, 1000, 500); // Min, Max, Default
        speedSlider.setBlockIncrement(100);
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(100);

        speedSlider.disableProperty().bind(speedModeToggle.selectedProperty().not());

        lmcExecutor.setSpeedControls(speedModeToggle, speedSlider);

        HBox controlPanel = new HBox(10); // Spacing of 10
        controlPanel.setAlignment(Pos.CENTER_RIGHT);
        controlPanel.setPadding(new Insets(5));
        controlPanel.getChildren().addAll(startButton, stopButton, stepButton, resetButton, speedModeToggle,
                speedSlider);
        return controlPanel;
    }

    private void toggleTheme() {
        String currentTheme = prefs.get("theme", "dark-mode");
        String newTheme = "dark-mode".equals(currentTheme) ? "light-mode" : "dark-mode";
        prefs.put("theme", newTheme);
        uiController.applyTheme(newTheme);
    }

    private void loadStylesheet(Scene scene, String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            System.err.println("Error: Stylesheet not found at " + path);
        } else {
            scene.getStylesheets().add(resource.toExternalForm());
        }
    }
}
