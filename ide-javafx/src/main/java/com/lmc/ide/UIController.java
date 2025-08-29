package com.lmc.ide;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import java.util.prefs.Preferences;

public class UIController {

    private final Stage primaryStage;
    private FileManager fileManager;
    private LMCExecutor lmcExecutor;

    private BorderPane root;
    private TreeView<java.io.File> fileExplorer;
    private TabPane codeTabPane;
    private SplitPane mainHorizontalSplit;

    private VBox findPopup, replacePopup;
    private TextField findField, replaceFindField, replaceWithField;

    private Label memoryUsedLabel;
    private double lastExplorerDividerPos = 0.2;
    private double lastToolsDividerPos = 0.8;

    public UIController(Stage primaryStage) {
        this.primaryStage = primaryStage;
        createInitialUI();
    }

    // --- Dependency Injection ---
    public void setFileManager(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public void setLmcExecutor(LMCExecutor lmcExecutor) {
        this.lmcExecutor = lmcExecutor;
    }

    public void setIdeFeatures(LMCIDEFeatures ideFeatures) {
        /* Can be used if needed */ }

    // --- Getters for other classes ---
    public BorderPane getRoot() {
        return root;
    }

    public TabPane getCodeTabPane() {
        return codeTabPane;
    }

    public TreeView<java.io.File> getFileExplorer() {
        return fileExplorer;
    }

    public Label getMemoryUsageLabel() {
        return memoryUsedLabel;
    }

    /**
     * Creates the basic layout and structure. Called from the constructor.
     */
    private void createInitialUI() {
        root = new BorderPane();
        int initialFontSize = Preferences.userNodeForPackage(App.class).getInt("fontSize", 14);
        root.setStyle("-fx-font-size: " + initialFontSize + "px;");
    }

    /**
     * Populates the UI with components that require dependencies.
     * Must be called AFTER dependencies are injected.
     */
    public void initComponents() {
        MenuBar menuBar = createMenuBar(Preferences.userNodeForPackage(App.class));
        ToolBar toolBar = createToolBar();
        HBox topContainer = new HBox(menuBar, new HBox(), toolBar);
        HBox.setHgrow(topContainer.getChildren().get(1), Priority.ALWAYS);
        topContainer.getStyleClass().add("top-container");
        root.setTop(topContainer);

        createMainPanels();
    }

    private void createMainPanels() {
        fileExplorer = new TreeView<>();
        fileExplorer.getStyleClass().add("tree-view");
        fileExplorer.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null && nv.getValue().isFile())
                fileManager.openFile(nv.getValue());
        });

        VBox toolsPanel = createToolsPanel();

        codeTabPane = new TabPane();
        codeTabPane.getStyleClass().add("code-tab-pane");

        findPopup = createFindPopup();
        replacePopup = createReplacePopup();
        StackPane codeAreaStack = new StackPane(codeTabPane, findPopup, replacePopup);
        StackPane.setAlignment(findPopup, Pos.TOP_RIGHT);
        StackPane.setAlignment(replacePopup, Pos.TOP_RIGHT);
        StackPane.setMargin(findPopup, new Insets(10));
        StackPane.setMargin(replacePopup, new Insets(10));

        TabPane bottomTabPane = createBottomPane();

        mainHorizontalSplit = new SplitPane(fileExplorer, codeAreaStack, toolsPanel);
        mainHorizontalSplit.setDividerPositions(lastExplorerDividerPos, lastToolsDividerPos);

        SplitPane verticalSplit = new SplitPane(mainHorizontalSplit, bottomTabPane);
        verticalSplit.setOrientation(Orientation.VERTICAL);
        verticalSplit.setDividerPositions(0.75);
        root.setCenter(verticalSplit);
    }

    private VBox createToolsPanel() {
        GridPane memoryVisualizer = lmcExecutor.createMemoryVisualizer();
        memoryUsedLabel = new Label("Memory Used: 0 / 100");
        VBox panel = new VBox(10, new Label("Tools"), memoryVisualizer, memoryUsedLabel);
        panel.setPadding(new Insets(10));
        panel.getStyleClass().add("tools-panel");
        panel.setMinWidth(350);
        return panel;
    }

    private TabPane createBottomPane() {
        TextArea combinedConsole = new TextArea();
        combinedConsole.setPromptText("LMC Console (Input/Output)");
        combinedConsole.getStyleClass().add("console-area");
        combinedConsole.setEditable(false);
        lmcExecutor.setConsole(combinedConsole);

        ListView<String> errorListView = new ListView<>();
        errorListView.getStyleClass().add("error-list-view");

        Tab consoleTab = new Tab("Console", combinedConsole);
        consoleTab.setClosable(false);
        Tab errorsTab = new Tab("Errors", errorListView);
        errorsTab.setClosable(false);

        TabPane pane = new TabPane(consoleTab, errorsTab);
        lmcExecutor.setErrorListView(errorListView, pane);
        return pane;
    }

    private MenuBar createMenuBar(Preferences prefs) {
        MenuBar menuBar = new MenuBar();
        menuBar.getStyleClass().add("menu-bar");

        Menu fileMenu = new Menu("_File");
        MenuItem newFileItem = new MenuItem("_New File");
        newFileItem.setOnAction(e -> fileManager.newFile());
        MenuItem openProjectItem = new MenuItem("_Open Project...");
        openProjectItem.setOnAction(e -> fileManager.openProject());
        MenuItem saveItem = new MenuItem("_Save");
        saveItem.setOnAction(e -> fileManager.saveFile());
        MenuItem saveAsItem = new MenuItem("Save _As...");
        saveAsItem.setOnAction(e -> fileManager.saveFileAs());
        MenuItem exitItem = new MenuItem("E_xit");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(newFileItem, openProjectItem, new SeparatorMenuItem(), saveItem, saveAsItem,
                new SeparatorMenuItem(), exitItem);

        Menu editMenu = new Menu("_Edit");
        MenuItem undoItem = new MenuItem("_Undo");
        undoItem.setOnAction(e -> {
            if (getCurrentCodeArea() != null)
                getCurrentCodeArea().undo();
        });
        MenuItem redoItem = new MenuItem("_Redo");
        redoItem.setOnAction(e -> {
            if (getCurrentCodeArea() != null)
                getCurrentCodeArea().redo();
        });
        MenuItem findItem = new MenuItem("_Find...");
        findItem.setOnAction(e -> toggleFindPopup());
        MenuItem replaceItem = new MenuItem("R_eplace...");
        replaceItem.setOnAction(e -> toggleReplacePopup());
        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(), findItem, replaceItem);

        Menu viewMenu = new Menu("_View");
        Menu themeMenu = new Menu("_Theme");
        ToggleGroup themeGroup = new ToggleGroup();
        RadioMenuItem lightThemeItem = new RadioMenuItem("_Light");
        lightThemeItem.setToggleGroup(themeGroup);
        lightThemeItem.setSelected("light-mode".equals(prefs.get("theme", "dark-mode")));
        lightThemeItem.setOnAction(e -> applyTheme("light-mode"));
        RadioMenuItem darkThemeItem = new RadioMenuItem("_Dark");
        darkThemeItem.setToggleGroup(themeGroup);
        darkThemeItem.setSelected("dark-mode".equals(prefs.get("theme", "dark-mode")));
        darkThemeItem.setOnAction(e -> applyTheme("dark-mode"));
        themeMenu.getItems().addAll(lightThemeItem, darkThemeItem);
        viewMenu.getItems().add(themeMenu);

        Menu runMenu = new Menu("_Run");
        MenuItem runItem = new MenuItem("_Run Program");
        runItem.setOnAction(e -> lmcExecutor.runLMC());
        MenuItem stopItem = new MenuItem("_Stop Program");
        stopItem.setOnAction(e -> lmcExecutor.stopLMC());
        runMenu.getItems().addAll(runItem, stopItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, runMenu);
        return menuBar;
    }

    private ToolBar createToolBar() {
        Button startButton = new Button("▶");
        startButton.getStyleClass().add("start-button");
        Button stopButton = new Button("■");
        stopButton.getStyleClass().add("stop-button");
        Button stepButton = new Button("↦");

        lmcExecutor.setControls(startButton, stopButton, stepButton);
        startButton.setOnAction(e -> lmcExecutor.runLMC());
        stopButton.setOnAction(e -> lmcExecutor.stopLMC());
        stepButton.setOnAction(e -> lmcExecutor.executeStep());

        ToggleButton speedModeToggle = new ToggleButton("Fast");
        Slider speedSlider = new Slider(50, 2000, 500);
        speedSlider.setPrefWidth(100);
        speedSlider.setDisable(true);
        speedModeToggle.setOnAction(e -> {
            speedSlider.setDisable(!speedModeToggle.isSelected());
            speedModeToggle.setText(speedModeToggle.isSelected() ? "Slow" : "Fast");
        });
        lmcExecutor.setSpeedControls(speedModeToggle, speedSlider);

        Button collapseExplorerBtn = new Button("◀");
        collapseExplorerBtn.setOnAction(e -> toggleExplorerPane());
        Button collapseToolsBtn = new Button("▶");
        collapseToolsBtn.setOnAction(e -> toggleToolsPane());

        ToolBar toolBar = new ToolBar(collapseExplorerBtn, new Separator(), startButton, stopButton, stepButton,
                new Separator(), speedModeToggle, speedSlider, new Separator(), collapseToolsBtn);
        toolBar.getStyleClass().add("main-toolbar");
        return toolBar;
    }

    public void toggleExplorerPane() {
        if (mainHorizontalSplit.getItems().size() < 2)
            return;
        double currentPos = mainHorizontalSplit.getDividerPositions()[0];
        if (currentPos > 0.01) {
            lastExplorerDividerPos = currentPos;
            mainHorizontalSplit.setDividerPosition(0, 0.0);
        } else {
            mainHorizontalSplit.setDividerPosition(0, lastExplorerDividerPos);
        }
    }

    public void toggleToolsPane() {
        if (mainHorizontalSplit.getItems().size() < 3)
            return;
        double currentPos = mainHorizontalSplit.getDividerPositions()[1];
        if (currentPos < 0.99) {
            lastToolsDividerPos = currentPos;
            mainHorizontalSplit.setDividerPosition(1, 1.0);
        } else {
            mainHorizontalSplit.setDividerPosition(1, lastToolsDividerPos);
        }
    }

    private VBox createFindPopup() {
        findField = new TextField();
        Button findNextBtn = new Button("↓");
        findNextBtn.setOnAction(e -> fileManager.findNext(findField.getText()));
        Button closeFindBtn = new Button("✕");
        closeFindBtn.setOnAction(e -> findPopup.setVisible(false));
        HBox hbox = new HBox(5, new Label("Find:"), findField, findNextBtn, closeFindBtn);
        hbox.setAlignment(Pos.CENTER);
        VBox popup = new VBox(hbox);
        popup.getStyleClass().add("popup");
        popup.setVisible(false);
        return popup;
    }

    private VBox createReplacePopup() {
        replaceFindField = new TextField();
        replaceWithField = new TextField();
        Button replaceBtn = new Button("Replace");
        replaceBtn.setOnAction(e -> fileManager.replaceNext());
        Button replaceAllBtn = new Button("All");
        replaceAllBtn.setOnAction(e -> fileManager.replaceAll());
        Button closeReplaceBtn = new Button("✕");
        closeReplaceBtn.setOnAction(e -> replacePopup.setVisible(false));

        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.add(new Label("Find:"), 0, 0);
        grid.add(replaceFindField, 1, 0);
        grid.add(new Label("With:"), 0, 1);
        grid.add(replaceWithField, 1, 1);

        HBox buttons = new HBox(5, replaceBtn, replaceAllBtn, closeReplaceBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox popup = new VBox(10, grid, buttons);
        popup.getStyleClass().add("popup");
        popup.setVisible(false);
        return popup;
    }

    public void toggleFindPopup() {
        replacePopup.setVisible(false);
        findPopup.setVisible(!findPopup.isVisible());
        if (findPopup.isVisible()) {
            if (getCurrentCodeArea() != null && getCurrentCodeArea().getSelectedText() != null
                    && !getCurrentCodeArea().getSelectedText().isEmpty()) {
                findField.setText(getCurrentCodeArea().getSelectedText());
            }
            findField.requestFocus();
        }
    }

    public void toggleReplacePopup() {
        findPopup.setVisible(false);
        replacePopup.setVisible(!replacePopup.isVisible());
        if (replacePopup.isVisible()) {
            if (getCurrentCodeArea() != null && getCurrentCodeArea().getSelectedText() != null
                    && !getCurrentCodeArea().getSelectedText().isEmpty()) {
                replaceFindField.setText(getCurrentCodeArea().getSelectedText());
            }
            replaceFindField.requestFocus();
        }
    }

    public void hidePopups() {
        findPopup.setVisible(false);
        replacePopup.setVisible(false);
    }

    public CodeArea createCodeArea() {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("code-area");

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, LMCSyntaxHighlighter.computeHighlighting(newText));
            lmcExecutor.onCodeChange(newText);
        });

        return codeArea;
    }

    public CodeArea getCurrentCodeArea() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null && selectedTab.getContent() instanceof CodeArea) {
            return (CodeArea) selectedTab.getContent();
        }
        return null;
    }

    public void applyTheme(String theme) {
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        root.getStyleClass().removeAll("dark-mode", "light-mode");
        root.getStyleClass().add(theme);
        prefs.put("theme", theme);
    }

    public Node createFolderIcon() {
        SVGPath svg = new SVGPath();
        svg.setContent("M10 4H4c-1.11 0-2 .9-2 2v10c0 1.1.89 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z");
        svg.getStyleClass().add("icon-folder");
        StackPane pane = new StackPane(svg);
        pane.setPrefSize(16, 16);
        return pane;
    }

    public Node createFileIcon() {
        SVGPath svg = new SVGPath();
        svg.setContent(
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zM13 9V3.5L18.5 9H13z");
        svg.getStyleClass().add("icon-file");
        StackPane pane = new StackPane(svg);
        pane.setPrefSize(16, 16);
        return pane;
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
}
