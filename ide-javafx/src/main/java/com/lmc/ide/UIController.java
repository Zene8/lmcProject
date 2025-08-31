package com.lmc.ide;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.prefs.Preferences;

public class UIController {

    private Stage primaryStage;
    private BorderPane root;
    private SplitPane mainSplitPane, verticalSplitPane;
    private TabPane editorTabPane;
    private VBox leftSidebar, rightSidebar;
    private BorderPane console;
    private TextArea consoleOutputArea; // New member for console output
    private TreeView<File> fileExplorer;

    private FileManager fileManager;
    private LMCExecutor lmcExecutor;
    private LMCIDEFeatures ideFeatures;
    private AIModelManager aiModelManager;
    private Preferences prefs;

    private Label memoryUsageLabel; // This label will be part of the memory mailboxes view
    private VBox memoryMailboxesView; // New member for memory mailboxes

    private VBox findPopup;
    private TextField findField;
    private VBox replacePopup;
    private TextField replaceFindField;
    private TextField replaceWithField;

    private Label statusBar;

    public UIController(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.memoryUsageLabel = new Label("Memory Usage: 0/100"); // Initialize here
    }

    public void setFileManager(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public void setLmcExecutor(LMCExecutor lmcExecutor) {
        this.lmcExecutor = lmcExecutor;
    }

    public void setIdeFeatures(LMCIDEFeatures ideFeatures) {
        this.ideFeatures = ideFeatures;
    }

    public void setAiModelManager(AIModelManager aiModelManager) {
        this.aiModelManager = aiModelManager;
    }

    public void setPrefs(Preferences prefs) {
        this.prefs = prefs;
    }

    public void initComponents() {
        root = new BorderPane();
        editorTabPane = new TabPane();
        fileExplorer = new TreeView<>();
        statusBar = new Label("Ready"); // Initialized status bar

        // StackPane for editor and popups
        StackPane editorStackPane = new StackPane();
        editorStackPane.getChildren().add(editorTabPane);

        // Main content area with resizable sidebars
        mainSplitPane = new SplitPane();
        mainSplitPane.setDividerPositions(0.166, 0.833);

        // Vertical split for editor and console
        verticalSplitPane = new SplitPane();
        verticalSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        verticalSplitPane.setDividerPositions(0.75);

        // Construct the layout
        leftSidebar = createLeftSidebar();
        rightSidebar = createRightSidebar();
        console = createConsoleView(); // Renamed

        mainSplitPane.getItems().addAll(leftSidebar, verticalSplitPane, rightSidebar);
        verticalSplitPane.getItems().addAll(editorStackPane, console); // Use editorStackPane here

        root.setCenter(mainSplitPane);
        root.setBottom(statusBar); // Added status bar to layout

        createFindPopup();
        createReplacePopup();

        // Add popups to the editorStackPane and position them
        editorStackPane.getChildren().addAll(findPopup, replacePopup);
        StackPane.setAlignment(findPopup, Pos.TOP_RIGHT);
        StackPane.setAlignment(replacePopup, Pos.TOP_RIGHT);

        MenuBar menuBar = createMenuBar();
        root.setTop(menuBar);
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File Menu
        Menu fileMenu = new Menu("File");
        MenuItem newFile = new MenuItem("New", createIcon("insert_drive_file.svg"));
        newFile.setOnAction(e -> fileManager.newFile());
        MenuItem openProject = new MenuItem("Open", createIcon("folder_open.svg"));
        openProject.setOnAction(e -> fileManager.openProject());
        MenuItem saveFile = new MenuItem("Save", createIcon("save.svg"));
        saveFile.setOnAction(e -> fileManager.saveFile());
        MenuItem closeTab = new MenuItem("Close Tab");
        closeTab.setOnAction(e -> fileManager.closeCurrentTab());
        fileMenu.getItems().addAll(newFile, openProject, saveFile, closeTab);

        // Edit Menu
        Menu editMenu = new Menu("Edit");
        MenuItem undo = new MenuItem("Undo");
        undo.setOnAction(e -> getCurrentCodeArea().undo());
        MenuItem redo = new MenuItem("Redo");
        redo.setOnAction(e -> getCurrentCodeArea().redo());
        MenuItem find = new MenuItem("Find", createIcon("search.svg"));
        find.setOnAction(e -> toggleFindPopup());
        MenuItem replace = new MenuItem("Replace", createIcon("find_replace.svg"));
        replace.setOnAction(e -> toggleReplacePopup());
        MenuItem formatCode = new MenuItem("Format Code");
        formatCode.setOnAction(e -> {
            CodeArea currentCodeArea = getCurrentCodeArea();
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
        toggleTools.setOnAction(e -> toggleToolsSidebar());
        CheckMenuItem toggleLeft = new CheckMenuItem("Toggle File Explorer");
        toggleLeft.setSelected(true);
        toggleLeft.setOnAction(e -> toggleLeftSidebar());
        CheckMenuItem toggleConsole = new CheckMenuItem("Toggle Console View");
        toggleConsole.setSelected(true);
        toggleConsole.setOnAction(e -> toggleConsoleView());
        CheckMenuItem maximizeEditor = new CheckMenuItem("Maximize Editor");
        maximizeEditor.setSelected(false);
        maximizeEditor.setOnAction(e -> toggleEditorMaximize());
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
            CodeArea currentCodeArea = getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP\n        STA LOOP_VAR\nLOOP    LDA LOOP_VAR\n        OUT\n        BRZ END\n        INP\n        STA LOOP_VAR\n        BRA LOOP\nEND     HLT\nLOOP_VAR DAT 0\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetAddTwoNumbers = new MenuItem("Add Two Numbers");
        snippetAddTwoNumbers.setOnAction(e -> {
            CodeArea currentCodeArea = getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP\n        STA NUM1\n        INP\n        STA NUM2\n        LDA NUM1\n        ADD NUM2\n        OUT\n        HLT\nNUM1    DAT 0\nNUM2    DAT 0\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetDataDefinition = new MenuItem("Data Definition");
        snippetDataDefinition.setOnAction(e -> {
            CodeArea currentCodeArea = getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "MY_DATA DAT 10\nANOTHER_DATA DAT 20\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetMultiplication = new MenuItem("Multiplication");
        snippetMultiplication.setOnAction(e -> {
            CodeArea currentCodeArea = getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP         // Input Multiplicand (A)\n        STA MULTIPLICAND\n        INP         // Input Multiplier (B)\n        STA MULTIPLIER\n        LDA ZERO    // Initialize Result to 0\n        STA RESULT\n\nLOOP    LDA MULTIPLIER\n        BRZ END_LOOP // If Multiplier is 0, done\n        SUB ONE     // Decrement Multiplier\n        STA MULTIPLIER\n        LDA RESULT\n        ADD MULTIPLICAND // Add Multiplicand to Result\n        STA RESULT\n        BRA LOOP\n\nEND_LOOP LDA RESULT\n        OUT         // Output Result\n        HLT\n\nMULTIPLICAND DAT 0\nMULTIPLIER   DAT 0\nRESULT       DAT 0\nZERO         DAT 0\nONE          DAT 1\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetDivision = new MenuItem("Division");
        snippetDivision.setOnAction(e -> {
            CodeArea currentCodeArea = getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP         // Input Dividend (A)\n        STA DIVIDEND\n        INP         // Input Divisor (B)\n        STA DIVISOR\n        LDA ZERO    // Initialize Quotient to 0\n        STA QUOTIENT\n\nLOOP    LDA DIVIDEND\n        SUB DIVISOR // Dividend - Divisor\n        BRP CONTINUE // If result >= 0, continue\n        BRA END_LOOP // If result < 0, done\n\nCONTINUE STA DIVIDEND // Store new Dividend\n        LDA QUOTIENT\n        ADD ONE     // Increment Quotient\n        STA QUOTIENT\n        BRA LOOP\n\nEND_LOOP LDA QUOTIENT\n        OUT         // Output Quotient\n        HLT\n\nDIVIDEND DAT 0\nDIVISOR  DAT 0\nQUOTIENT DAT 0\nZERO     DAT 0\nONE      DAT 1\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        MenuItem snippetConditionalBranching = new MenuItem("Conditional Branching (IF-ELSE)");
        snippetConditionalBranching.setOnAction(e -> {
            CodeArea currentCodeArea = getCurrentCodeArea();
            if (currentCodeArea != null) {
                String snippet = "        INP         // Input a number\n        STA NUM\n        LDA NUM\n        BRZ IS_ZERO // If NUM == 0, branch to IS_ZERO\n\n        // ELSE block (NUM is not zero)\n        LDA NUM\n        OUT         // Output NUM\n        BRA END_IF\n\nIS_ZERO LDA ZERO\n        OUT         // Output 0\n\nEND_IF  HLT\n\nNUM     DAT 0\nZERO    DAT 0\n";
                currentCodeArea.insertText(currentCodeArea.getCaretPosition(), snippet);
            }
        });

        insertSnippet.getItems().addAll(snippetInputOutput, snippetAddTwoNumbers, snippetDataDefinition,
                snippetMultiplication, snippetDivision, snippetConditionalBranching);
        codeMenu.getItems().addAll(insertSnippet);

        menuBar.getMenus().addAll(fileMenu, editMenu, codeMenu, viewMenu, createAIModelMenu());
        return menuBar;
    }

    private VBox createLeftSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        TitledPane fileExplorerPane = new TitledPane("File Explorer", fileExplorer);
        fileExplorerPane.setCollapsible(true);
        fileExplorerPane.setExpanded(true);
        sidebar.getChildren().add(fileExplorerPane);
        return sidebar;
    }

    private VBox createRightSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // Run Controls Panel
        HBox runControlsPanel = createRunControlsPanel();
        runControlsPanel.getStyleClass().add("control-panel"); // Apply style class

        // Tools Tab Pane
        TabPane toolsTabPane = new TabPane();

        Tab toolsTab = new Tab("Tools");
        toolsTab.setClosable(false);
        // Add tools components here

        Tab memoryTab = new Tab("Memory"); // New tab for memory mailboxes
        memoryTab.setClosable(false);
        memoryMailboxesView = createMemoryMailboxesView(); // Create memory mailboxes view
        memoryTab.setContent(memoryMailboxesView);

        Tab learnTab = new Tab("Learn");
        learnTab.setClosable(false);
        // Add learning components here

        toolsTabPane.getTabs().addAll(toolsTab, memoryTab, learnTab);

        sidebar.getChildren().addAll(runControlsPanel, toolsTabPane);
        return sidebar;
    }

    private HBox createRunControlsPanel() {
        Button startButton = new Button("", createIcon("play_arrow.svg", 24));
        startButton.setTooltip(new Tooltip("Run Program"));
        Button stopButton = new Button("", createIcon("stop.svg", 24));
        stopButton.setTooltip(new Tooltip("Stop Program"));
        Button stepButton = new Button("", createIcon("skip_next.svg", 24));
        stepButton.setTooltip(new Tooltip("Step Program"));
        Button resetButton = new Button("", createIcon("refresh.svg", 24));
        resetButton.setTooltip(new Tooltip("Reset Program"));

        // Set actions
        startButton.setOnAction(e -> lmcExecutor.runLMC());
        stopButton.setOnAction(e -> lmcExecutor.stopLMC());
        stepButton.setOnAction(e -> lmcExecutor.executeStep());
        resetButton.setOnAction(e -> lmcExecutor.resetProgram());

        // Pass controls to LMCExecutor
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

    private BorderPane createConsoleView() {
        BorderPane consolePane = new BorderPane();
        consolePane.getStyleClass().add("console");

        consoleOutputArea = new TextArea();
        consoleOutputArea.setEditable(false);
        consoleOutputArea.getStyleClass().add("console-output-area");
        consolePane.setCenter(consoleOutputArea);

        TitledPane consoleTitlePane = new TitledPane("Console", consolePane);
        consoleTitlePane.setCollapsible(true);
        consoleTitlePane.setExpanded(true);
        return consolePane;
    }

    public TextArea getConsoleOutputArea() {
        return consoleOutputArea;
    }

    private VBox createMemoryMailboxesView() {
        VBox mailboxesView = new VBox();
        mailboxesView.getStyleClass().add("memory-mailboxes-view");
        // memoryUsageLabel is now initialized in the constructor
        mailboxesView.getChildren().add(memoryUsageLabel);
        // Add actual memory mailboxes display here
        mailboxesView.getChildren().add(lmcExecutor.createMemoryVisualizer());
        return mailboxesView;
    }

    private void createFindPopup() {
        findPopup = new VBox();
        findPopup.getStyleClass().add("find-replace-popup");
        findPopup.setPadding(new Insets(10));
        findPopup.setSpacing(5);
        findField = new TextField();
        Button findNextButton = new Button("", createIcon("search.svg"));
        findNextButton.setTooltip(new Tooltip("Find Next"));
        findNextButton.setOnAction(e -> ideFeatures.findNext(findField.getText(), true));
        findPopup.getChildren().addAll(new Label("Find:"), findField, findNextButton);
        findPopup.setVisible(false);
        root.getChildren().add(findPopup);
    }

    private void createReplacePopup() {
        replacePopup = new VBox();
        replacePopup.getStyleClass().add("find-replace-popup");
        replacePopup.setPadding(new Insets(10));
        replacePopup.setSpacing(5);
        replaceFindField = new TextField();
        replaceWithField = new TextField();
        Button replaceNextButton = new Button("", createIcon("find_replace.svg"));
        replaceNextButton.setTooltip(new Tooltip("Replace"));
        replaceNextButton.setOnAction(e -> ideFeatures.replaceNext());
        Button replaceAllButton = new Button("", createIcon("find_replace.svg"));
        replaceAllButton.setTooltip(new Tooltip("Replace All"));
        replaceAllButton.setOnAction(e -> ideFeatures.replaceAll());
        replacePopup.getChildren().addAll(new Label("Find:"), replaceFindField, new Label("Replace with:"),
                replaceWithField, new HBox(5, replaceNextButton, replaceAllButton));
        replacePopup.setVisible(false);
        root.getChildren().add(replacePopup);
    }

    public Node getRoot() {
        return root;
    }

    public TabPane getEditorTabPane() {
        return editorTabPane;
    }

    public CodeArea getCurrentCodeArea() {
        Tab selectedTab = editorTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null && selectedTab.getContent() instanceof BorderPane) {
            Node centerNode = ((BorderPane) selectedTab.getContent()).getCenter();
            if (centerNode instanceof CodeArea) {
                return (CodeArea) centerNode;
            }
        }
        return null;
    }

    public void setupHotkeys(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN), () -> {
            if (getCurrentCodeArea() != null)
                getCurrentCodeArea().undo();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN), () -> {
            if (getCurrentCodeArea() != null)
                getCurrentCodeArea().redo();
        });
    }

    public Label getMemoryUsageLabel() {
        return memoryUsageLabel;
    }

    public void applyTheme(String theme) {
        if (root.getScene() != null) {
            root.getScene().getRoot().getStyleClass().remove("dark-mode");
            root.getScene().getRoot().getStyleClass().remove("light-mode");
            root.getScene().getRoot().getStyleClass().add(theme);
        }
    }

    public void toggleToolsSidebar() {
        if (mainSplitPane.getItems().contains(rightSidebar)) {
            mainSplitPane.getItems().remove(rightSidebar);
        } else {
            mainSplitPane.getItems().add(rightSidebar);
            // Re-add in correct order
            mainSplitPane.getItems().remove(verticalSplitPane);
            mainSplitPane.getItems().add(verticalSplitPane);
            mainSplitPane.getItems().remove(rightSidebar);
            mainSplitPane.getItems().add(rightSidebar);
            mainSplitPane.setDividerPositions(0.166, 0.833);
        }
    }

    public void toggleLeftSidebar() {
        if (mainSplitPane.getItems().contains(leftSidebar)) {
            mainSplitPane.getItems().remove(leftSidebar);
        } else {
            mainSplitPane.getItems().add(0, leftSidebar);
            mainSplitPane.setDividerPositions(0.166, 0.833);
        }
    }

    private double[] originalMainSplitPanePositions = null;
    private double[] originalVerticalSplitPanePositions = null;

    public void toggleEditorMaximize() {
        if (mainSplitPane.getItems().size() == 1 && mainSplitPane.getItems().get(0) == verticalSplitPane) {
            // Editor is maximized, restore original layout
            mainSplitPane.getItems().clear();
            if (leftSidebar != null) mainSplitPane.getItems().add(leftSidebar);
            mainSplitPane.getItems().add(verticalSplitPane);
            if (rightSidebar != null) mainSplitPane.getItems().add(rightSidebar);

            if (originalMainSplitPanePositions != null) {
                mainSplitPane.setDividerPositions(originalMainSplitPanePositions);
            }
            if (originalVerticalSplitPanePositions != null) {
                verticalSplitPane.setDividerPositions(originalVerticalSplitPanePositions);
            }
        } else {
            // Maximize editor
            originalMainSplitPanePositions = mainSplitPane.getDividerPositions();
            originalVerticalSplitPanePositions = verticalSplitPane.getDividerPositions();

            mainSplitPane.getItems().clear();
            mainSplitPane.getItems().add(verticalSplitPane);

            verticalSplitPane.getItems().clear();
            verticalSplitPane.getItems().add(editorTabPane);
            verticalSplitPane.setDividerPositions(0.0); // Editor takes full vertical space
        }
    }

    public void toggleConsoleView() {
        if (verticalSplitPane.getItems().contains(console)) {
            verticalSplitPane.getItems().remove(console);
        } else {
            verticalSplitPane.getItems().add(console);
            verticalSplitPane.setDividerPositions(0.75);
        }
    }

    public void toggleFindPopup() {
        findPopup.setVisible(!findPopup.isVisible());
        replacePopup.setVisible(false);
    }

    public void toggleReplacePopup() {
        replacePopup.setVisible(!replacePopup.isVisible());
        findPopup.setVisible(false);
    }

    public void hidePopups() {
        findPopup.setVisible(false);
        replacePopup.setVisible(false);
    }

    public void newTab(String name, CodeArea codeArea) {
        Tab tab = new Tab(name);
        BorderPane editorPane = new BorderPane(codeArea);
        tab.setContent(editorPane);
        editorTabPane.getTabs().add(tab);
        editorTabPane.getSelectionModel().select(tab);
        // Set syntax highlighting for the new code area
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, LMCSyntaxHighlighter.computeHighlighting(newText));
        });

        // Set paragraph graphic factory for line numbers and error indicators
        codeArea.setParagraphGraphicFactory(lineNumber -> {
            HBox hbox = new HBox(LineNumberFactory.get(codeArea).apply(lineNumber));
            hbox.setAlignment(Pos.CENTER_LEFT);
            hbox.setSpacing(5);

            if (ideFeatures != null && ideFeatures.getLineErrors().containsKey(lineNumber + 1)) { // Line numbers are
                                                                                                  // 1-based
                Circle errorIcon = new Circle(4, Color.RED);
                errorIcon.getStyleClass().add("error-icon");
                Tooltip tooltip = new Tooltip(ideFeatures.getLineErrors().get(lineNumber + 1));
                Tooltip.install(errorIcon, tooltip);
                hbox.getChildren().add(errorIcon);
            }
            return hbox;
        });
    }

    public void setStatusBarMessage(String message) {
        if (statusBar != null) {
            statusBar.setText(message);
        }
    }

    public void refreshFileExplorer(File directory) {
        TreeItem<File> rootItem = new TreeItem<>(directory, createFolderIcon());
        rootItem.setExpanded(true);
        fileExplorer.setRoot(rootItem);
        populateTreeView(directory, rootItem);
    }

    private void populateTreeView(File directory, TreeItem<File> parentItem) {
        File[] files = directory.listFiles();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory())
                    return -1;
                if (!f1.isDirectory() && f2.isDirectory())
                    return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (File file : files) {
                if (!file.getName().startsWith(".")) {
                    TreeItem<File> item = new TreeItem<>(file,
                            file.isDirectory() ? createFolderIcon() : createFileIcon());
                    if (file.isDirectory()) {
                        populateTreeView(file, item);
                    }
                    parentItem.getChildren().add(item);
                }
            }
        }
    }

    public ImageView createFolderIcon() {
        return createIcon("folder.svg");
    }

    public ImageView createFileIcon() {
        return createIcon("file.svg");
    }

    public ImageView createIcon(String iconName) {
        return createIcon(iconName, 16);
    }

    public ImageView createIcon(String iconName, double size) {
        String path = "/icons/" + iconName;
        InputStream stream = getClass().getResourceAsStream(path);

        if (stream == null) {
            System.err.println("Error: Icon resource not found at path: " + path);
            return new ImageView();
        }

        Image image = new Image(stream);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        return imageView;
    }

    public String getFindPopupText() {
        return findField.getText();
    }

    public String getReplaceFindPopupText() {
        return replaceFindField.getText();
    }

    public String getReplaceWithPopupText() {
        return replaceWithField.getText();
    }

    public TreeView<File> getFileExplorer() {
        return fileExplorer;
    }

    public void highlightLine(int lineNumber) {
        CodeArea codeArea = getCurrentCodeArea();
        if (codeArea != null && lineNumber >= 0 && lineNumber < codeArea.getParagraphs().size()) {
            codeArea.setParagraphStyle(lineNumber, Collections.singleton("current-line-highlight"));
        }
    }

    public void clearLineHighlight(int lineNumber) {
        CodeArea codeArea = getCurrentCodeArea();
        if (codeArea != null && lineNumber >= 0 && lineNumber < codeArea.getParagraphs().size()) {
            codeArea.setParagraphStyle(lineNumber, Collections.emptyList());
        }
    }

    private void toggleTheme() {
        String currentTheme = prefs.get("theme", "dark-mode");
        String newTheme = "dark-mode".equals(currentTheme) ? "light-mode" : "dark-mode";
        prefs.put("theme", newTheme);
        applyTheme(newTheme);
    }

    private Menu createAIModelMenu() {
        Menu aiModelMenu = new Menu("AI Model");

        Menu localModelsMenu = new Menu("Local Models");
        ToggleGroup localModelToggleGroup = new ToggleGroup();
        for (String model : aiModelManager.getLocalModels()) {
            RadioMenuItem item = new RadioMenuItem(model);
            item.setToggleGroup(localModelToggleGroup);
            if (model.equals(aiModelManager.getSelectedModel())) {
                item.setSelected(true);
            }
            item.setOnAction(e -> aiModelManager.setSelectedModel(model));
            localModelsMenu.getItems().add(item);
        }

        Menu cloudModelsMenu = new Menu("Cloud Models");
        ToggleGroup cloudModelToggleGroup = new ToggleGroup();
        for (String model : aiModelManager.getCloudModels()) {
            RadioMenuItem item = new RadioMenuItem(model);
            item.setToggleGroup(cloudModelToggleGroup);
            if (model.equals(aiModelManager.getSelectedModel())) {
                item.setSelected(true);
            }
            item.setOnAction(e -> aiModelManager.setSelectedModel(model));
            cloudModelsMenu.getItems().add(item);
        }

        aiModelMenu.getItems().addAll(localModelsMenu, cloudModelsMenu);
        return aiModelMenu;
    }
}