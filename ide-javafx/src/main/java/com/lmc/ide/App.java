package com.lmc.ide;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.geometry.Pos;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import java.util.function.IntFunction;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class App extends Application {

    private Image folderIcon;
    private Image fileIcon;

    public TabPane codeTabPane;
    private TextArea combinedConsole;
    private ListView<String> errorListView;
    private TabPane bottomTabPane;
    private TreeView<File> fileExplorer;
    private Stage primaryStage;
    private BorderPane root;
    private LMCInterpreter interpreter;
    private LMCParser parser;
    private LMCIDEFeatures ideFeatures;
    private LMCParser.AssembledCode currentAssembledCode;

    private File currentOpenFile;
    private File currentProjectDirectory;

    private Timeline executionTimeline;
    private Button startButton, stopButton, stepButton;
    private ToggleButton speedModeToggle;
    private Slider speedSlider;
    private VBox[] memoryCellBoxes = new VBox[100];
    private Label memoryUsedLabel;
    private int lastHighlightedLine = -1;
    private int lastHighlightedMemoryCell = -1;

    private TextField searchField;
    private Button findButton;
    private TextField replaceField;
    private Button replaceButton;
    private Button replaceAllButton;

    private Label lineInfoLabel;
    private Label columnInfoLabel;

    private Map<Integer, String> syntaxErrors = new HashMap<>();
    private Set<Integer> breakpoints = new HashSet<>();

    private boolean autocorrectEnabled = true;
    private boolean autoFormattingEnabled = true;
    private boolean errorHighlightingEnabled = true;
    private CompletableFuture<Integer> pendingInputRequest;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("LMC IDE");
        interpreter = new LMCInterpreter();
        parser = new LMCParser();

        try {
            folderIcon = new Image(getClass().getResourceAsStream("/icons/folder.svg"));
            fileIcon = new Image(getClass().getResourceAsStream("/icons/file.svg"));
        } catch (Exception e) {
            System.err.println("Error loading icons: " + e.getMessage());
        }

        Preferences prefs = Preferences.userNodeForPackage(App.class);
        int initialFontSize = prefs.getInt("fontSize", 14);

        codeTabPane = new TabPane();

        GridPane memoryVisualizer = createMemoryVisualizer();
        memoryUsedLabel = new Label("Memory Used: 0 / 100");

        ideFeatures = new LMCIDEFeatures(this, parser, new Label(),
                autocorrectEnabled, autoFormattingEnabled, errorHighlightingEnabled);

        combinedConsole = new TextArea();
        combinedConsole.setPromptText("LMC Console (Input/Output)");
        combinedConsole.getStyleClass().add("console-area");
        combinedConsole.setEditable(false);

        errorListView = new ListView<>();
        errorListView.getStyleClass().add("error-list-view");

        Tab consoleTab = new Tab("Console", combinedConsole);
        consoleTab.setClosable(false);
        Tab errorsTab = new Tab("Errors", errorListView);
        errorsTab.setClosable(false);

        bottomTabPane = new TabPane(consoleTab, errorsTab);
        bottomTabPane.setPrefHeight(150);

        fileExplorer = new TreeView<>();
        fileExplorer.getStyleClass().add("tree-view");
        fileExplorer.setPrefWidth(200);
        fileExplorer.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null && nv.getValue().isFile())
                openFile(nv.getValue());
        });

        VBox memoryBox = new VBox(10, new Label("Memory Mailboxes"), memoryVisualizer, memoryUsedLabel);
        memoryBox.setPadding(new Insets(5));
        memoryBox.getStyleClass().add("memory-box");
        memoryBox.setPrefWidth(500);

        root = new BorderPane();
        root.setStyle("-fx-font-size: " + initialFontSize + "px;");

        MenuBar menuBar = createMenuBar(prefs, initialFontSize);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        startButton = new Button("▶ Start");
        startButton.setOnAction(e -> runLMC());

        stopButton = new Button("■ Stop");
        stopButton.setOnAction(e -> stopLMC());
        stopButton.setDisable(true);

        stepButton = new Button("Step");
        stepButton.setOnAction(e -> executeStep()); // Re-use executeStep
        stepButton.setDisable(true);

        speedModeToggle = new ToggleButton("Fast Mode");
        speedSlider = new Slider(50, 5000, 500);
        speedSlider.setPrefWidth(150);
        speedSlider.setBlockIncrement(50);
        speedSlider.setDisable(true);
        speedModeToggle.setOnAction(e -> {
            speedSlider.setDisable(!speedModeToggle.isSelected());
            speedModeToggle.setText(speedModeToggle.isSelected() ? "Slow Mode" : "Fast Mode");
        });

        ToolBar toolBar = new ToolBar(menuBar, spacer, speedModeToggle, speedSlider, startButton, stopButton,
                stepButton);

        HBox searchBar = new HBox(5);
        searchBar.setPadding(new Insets(5));
        searchField = new TextField();
        searchField.setPromptText("Search");
        findButton = new Button("Find");
        findButton.setOnAction(e -> {
            String query = searchField.getText();
            if (query != null && !query.isEmpty()) {
                CodeArea codeArea = getCurrentCodeArea();
                if (codeArea != null) {
                    String text = codeArea.getText();
                    int index = text.indexOf(query, codeArea.getCaretPosition());
                    if (index == -1) {
                        index = text.indexOf(query);
                    }
                    if (index != -1) {
                        codeArea.selectRange(index, index + query.length());
                    } else {
                        showAlert("Info", "No more occurrences found.");
                    }
                }
            }
        });
        replaceField = new TextField();
        replaceField.setPromptText("Replace");
        replaceButton = new Button("Replace");
        replaceButton.setOnAction(e -> {
            String query = searchField.getText();
            String replacement = replaceField.getText();
            if (query != null && !query.isEmpty() && replacement != null) {
                CodeArea codeArea = getCurrentCodeArea();
                if (codeArea != null) {
                    if (codeArea.getSelectedText().equals(query)) {
                        codeArea.replaceSelection(replacement);
                    } else {
                        String text = codeArea.getText();
                        int index = text.indexOf(query, codeArea.getCaretPosition());
                        if (index == -1) {
                            index = text.indexOf(query);
                        }
                        if (index != -1) {
                            codeArea.selectRange(index, index + query.length());
                            codeArea.replaceSelection(replacement);
                        } else {
                            showAlert("Info", "No more occurrences found.");
                        }
                    }
                }
            }
        });
        replaceAllButton = new Button("Replace All");
        replaceAllButton.setOnAction(e -> {
            String query = searchField.getText();
            String replacement = replaceField.getText();
            if (query != null && !query.isEmpty() && replacement != null) {
                CodeArea codeArea = getCurrentCodeArea();
                if (codeArea != null) {
                    String text = codeArea.getText();
                    text = text.replace(query, replacement);
                    codeArea.replaceText(text);
                }
            }
        });
        searchBar.getChildren().addAll(searchField, findButton, replaceField, replaceButton, replaceAllButton);
        VBox topBox = new VBox(toolBar, searchBar);
        root.setTop(topBox);

        SplitPane horizontalMainSplitPane = new SplitPane(fileExplorer, codeTabPane);
        horizontalMainSplitPane.setDividerPositions(0.2);

        SplitPane verticalMainSplitPane = new SplitPane(horizontalMainSplitPane, bottomTabPane);
        verticalMainSplitPane.setOrientation(Orientation.VERTICAL);
        verticalMainSplitPane.setDividerPositions(0.8);

        root.setCenter(verticalMainSplitPane);
        root.setRight(memoryBox);

        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5));
        lineInfoLabel = new Label("Line: 1");
        columnInfoLabel = new Label("Column: 1");
        statusBar.getChildren().addAll(lineInfoLabel, columnInfoLabel);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/vscode-style.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/lmc-syntax.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        setupHotkeys(scene);
        setupConsoleInputListener();
        applyTheme("dark-mode");
        newFile();
    }

    private MenuBar createMenuBar(Preferences prefs, int initialFontSize) {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem openProjectItem = new MenuItem("Open Project...");
        openProjectItem.setOnAction(e -> openProject());
        MenuItem newFileItem = new MenuItem("New File");
        newFileItem.setOnAction(e -> newFile());
        MenuItem newFolderItem = new MenuItem("New Folder");
        newFolderItem.setOnAction(e -> newFolder());
        MenuItem saveItem = new MenuItem("Save");
        saveItem.setOnAction(e -> saveFile());
        MenuItem saveAsItem = new MenuItem("Save As...");
        saveAsItem.setOnAction(e -> saveFileAs());
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(openProjectItem, newFileItem, newFolderItem, new SeparatorMenuItem(), saveItem,
                saveAsItem, new SeparatorMenuItem(), exitItem);

        Menu editMenu = new Menu("Edit");
        MenuItem undoItem = new MenuItem("Undo");
        undoItem.setOnAction(e -> {
            CodeArea codeArea = getCurrentCodeArea();
            if (codeArea != null) {
                codeArea.undo();
            }
        });
        MenuItem redoItem = new MenuItem("Redo");
        redoItem.setOnAction(e -> {
            CodeArea codeArea = getCurrentCodeArea();
            if (codeArea != null) {
                codeArea.redo();
            }
        });
        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setOnAction(e -> {
            CodeArea codeArea = getCurrentCodeArea();
            if (codeArea != null) {
                codeArea.cut();
            }
        });
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> {
            CodeArea codeArea = getCurrentCodeArea();
            if (codeArea != null) {
                codeArea.copy();
            }
        });
        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setOnAction(e -> {
            CodeArea codeArea = getCurrentCodeArea();
            if (codeArea != null) {
                codeArea.paste();
            }
        });
        MenuItem formatCodeItem = new MenuItem("Format Code");
        formatCodeItem.setOnAction(e -> formatCode());
        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(), cutItem, copyItem, pasteItem,
                new SeparatorMenuItem(), formatCodeItem);

        Menu featuresMenu = new Menu("Features");
        Label autocorrectCheck = new Label("✓");
        autocorrectCheck.getStyleClass().add("menu-check-mark");
        autocorrectCheck.setMinWidth(20);
        MenuItem autocorrectToggle = new MenuItem("Autocorrect");
        autocorrectToggle.setGraphic(autocorrectEnabled ? autocorrectCheck : null);
        autocorrectToggle.setOnAction(e -> {
            autocorrectEnabled = !autocorrectEnabled;
            ideFeatures.setAutocorrectEnabled(autocorrectEnabled);
            autocorrectToggle.setGraphic(autocorrectEnabled ? autocorrectCheck : null);
        });

        Label autoFormatCheck = new Label("✓");
        autoFormatCheck.getStyleClass().add("menu-check-mark");
        autoFormatCheck.setMinWidth(20);
        MenuItem autoFormattingToggle = new MenuItem("Auto-formatting");
        autoFormattingToggle.setGraphic(autoFormattingEnabled ? autoFormatCheck : null);
        autoFormattingToggle.setOnAction(e -> {
            autoFormattingEnabled = !autoFormattingEnabled;
            ideFeatures.setAutoFormattingEnabled(autoFormattingEnabled);
            autoFormattingToggle.setGraphic(autoFormattingEnabled ? autoFormatCheck : null);
        });

        Label errorHighlightCheck = new Label("✓");
        errorHighlightCheck.getStyleClass().add("menu-check-mark");
        errorHighlightCheck.setMinWidth(20);
        MenuItem errorHighlightingToggle = new MenuItem("Error Highlighting");
        errorHighlightingToggle.setGraphic(errorHighlightingEnabled ? errorHighlightCheck : null);
        errorHighlightingToggle.setOnAction(e -> {
            errorHighlightingEnabled = !errorHighlightingEnabled;
            ideFeatures.setErrorHighlightingEnabled(errorHighlightingEnabled);
            errorHighlightingToggle.setGraphic(errorHighlightingEnabled ? errorHighlightCheck : null);
        });
        featuresMenu.getItems().addAll(autocorrectToggle, autoFormattingToggle, errorHighlightingToggle);

        Menu viewMenu = new Menu("View");
        MenuItem darkModeItem = new MenuItem("Dark Mode");
        darkModeItem.setOnAction(e -> applyTheme("dark-mode"));
        MenuItem highContrastModeItem = new MenuItem("High Contrast Mode");
        highContrastModeItem.setOnAction(e -> applyTheme("high-contrast"));

        ChoiceBox<Integer> fontSizeSelector = new ChoiceBox<>();
        fontSizeSelector.getItems().addAll(12, 14, 16, 18, 20, 22, 24);
        fontSizeSelector.setValue(initialFontSize);
        fontSizeSelector.setOnAction(e -> {
            int newSize = fontSizeSelector.getValue();
            root.setStyle("-fx-font-size: " + newSize + "px;");
            prefs.putInt("fontSize", newSize);
        });
        CustomMenuItem fontSizeMenuItem = new CustomMenuItem(fontSizeSelector);
        fontSizeMenuItem.setHideOnClick(false);
        viewMenu.getItems().addAll(darkModeItem, highContrastModeItem, new SeparatorMenuItem(),
                fontSizeMenuItem);

        Menu helpMenu = new Menu("Help");
        MenuItem lmcOpcodesItem = new MenuItem("LMC Opcodes");
        lmcOpcodesItem.setOnAction(e -> showLmcOpcodes());
        helpMenu.getItems().add(lmcOpcodesItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, featuresMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private CodeArea createCodeArea() {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("code-area");

        codeArea.caretPositionProperty().addListener((obs, oldPosition, newPosition) -> {
            int line = codeArea.getCurrentParagraph();
            int column = codeArea.getCaretColumn();
            lineInfoLabel.setText("Line: " + (line + 1));
            columnInfoLabel.setText("Column: " + (column + 1));
        });

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, LMCSyntaxHighlighter.computeHighlighting(newText));
            syntaxErrors.clear(); // Clear previous errors
            try {
                LMCParser.AssembledCode assembled = parser.parse(newText);
                updateMemoryVisualizer(assembled.memoryMap, -1, assembled);
            } catch (LMCParser.LMCParseException e) {
                syntaxErrors.put(e.getLineNumber(), e.getMessage());
                updateMemoryVisualizer(new int[100], -1, null); // Clear memory visualizer on parse error
            }
            updateErrorDisplay(); // New method to update UI based on errors
        });

        return codeArea;
    }

    private void showLmcOpcodes() {
        Stage stage = new Stage();
        stage.setTitle("LMC Instruction Opcodes");

        TableView<OpcodeEntry> table = new TableView<>();

        TableColumn<OpcodeEntry, String> mnemonicCol = new TableColumn<>("Mnemonic");
        mnemonicCol.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getMnemonic()));

        TableColumn<OpcodeEntry, String> opcodeCol = new TableColumn<>("Opcode");
        opcodeCol.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getOpcode()));

        TableColumn<OpcodeEntry, String> descriptionCol = new TableColumn<>("Description");
        descriptionCol.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDescription()));

        table.getColumns().addAll(mnemonicCol, opcodeCol, descriptionCol);

        table.getItems().addAll(
                new OpcodeEntry("ADD", "1xx", "Add contents of mailbox xx to accumulator"),
                new OpcodeEntry("SUB", "2xx", "Subtract contents of mailbox xx from accumulator"),
                new OpcodeEntry("STA", "3xx", "Store contents of accumulator in mailbox xx"),
                new OpcodeEntry("LDA", "5xx", "Load contents of mailbox xx into accumulator"),
                new OpcodeEntry("BRA", "6xx", "Branch always to mailbox xx"),
                new OpcodeEntry("BRZ", "7xx", "Branch if accumulator is zero to mailbox xx"),
                new OpcodeEntry("BRP", "8xx", "Branch if accumulator is positive or zero to mailbox xx"),
                new OpcodeEntry("INP", "901", "Input value from user to accumulator"),
                new OpcodeEntry("OUT", "902", "Output value from accumulator"),
                new OpcodeEntry("HLT", "000", "Halt program"),
                new OpcodeEntry("DAT", "(data)", "Declare data at this address"));

        VBox vbox = new VBox(table);
        vbox.setPadding(new Insets(10));

        Scene scene = new Scene(vbox, 600, 400);
        stage.setScene(scene);
        stage.show();
    }

    // Helper class for Opcode Table
    public static class OpcodeEntry {
        private final String mnemonic;
        private final String opcode;
        private final String description;

        public OpcodeEntry(String mnemonic, String opcode, String description) {
            this.mnemonic = mnemonic;
            this.opcode = opcode;
            this.description = description;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public String getOpcode() {
            return opcode;
        }

        public String getDescription() {
            return description;
        }
    }

    private void stopLMC() {
        if (executionTimeline != null)
            executionTimeline.stop();
        if (interpreter != null)
            interpreter.stop();
        stopButton.setDisable(true);
        startButton.setDisable(false);
        speedModeToggle.setDisable(false);
        stepButton.setDisable(true); // Disable step button on stop
        highlightCurrentInstruction(-1);
        updateMemoryVisualizer(interpreter.getMemory(), -1, interpreter.getAssembledCode());
    }

    private void runLMC() {
        startButton.setDisable(true);
        stopButton.setDisable(false);
        stepButton.setDisable(true); // Disable step button when running
        speedModeToggle.setDisable(true);
        combinedConsole.clear();
        combinedConsole.setEditable(false);
        highlightCurrentInstruction(-1);
        errorListView.getItems().clear(); // Clear errors on run

        String lmcCode = getCurrentCodeArea().getText();
        if (autocorrectEnabled) {
            String correctedCode = ideFeatures.autocorrectCode(lmcCode);
            if (!lmcCode.equals(correctedCode)) {
                getCurrentCodeArea().replaceText(correctedCode);
                lmcCode = correctedCode;
            }
        }

        try {
            currentAssembledCode = parser.parse(lmcCode);
            interpreter.load(currentAssembledCode, createInputProvider());
            interpreter.setBreakpoints(breakpoints); // Pass breakpoints to interpreter
            updateMemoryVisualizer(interpreter.getMemory(), -1, interpreter.getAssembledCode());
        } catch (LMCParser.LMCParseException e) {
            finishExecution(LMCInterpreter.ExecutionState.ERROR, e.getMessage());
            return;
        }

        if (speedModeToggle.isSelected()) { // SLOW MODE
            executionTimeline = new Timeline();
            executionTimeline.setCycleCount(Timeline.INDEFINITE);
            KeyFrame keyFrame = new KeyFrame(Duration.millis(speedSlider.getValue()), e -> executeStep());
            executionTimeline.getKeyFrames().add(keyFrame);

            speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (executionTimeline.getStatus() == Timeline.Status.RUNNING) {
                    executionTimeline.setRate(speedSlider.getMin() / newVal.doubleValue());
                }
            });
            executionTimeline.setRate(speedSlider.getMin() / speedSlider.getValue());

            interpreter.start();
            executionTimeline.play();
        } else { // FAST MODE
            CompletableFuture.runAsync(() -> {
                interpreter.start();
                LMCInterpreter.ExecutionState state;
                do {
                    state = interpreter.step();
                    if (state == LMCInterpreter.ExecutionState.BREAKPOINT_HIT) {
                        break; // Exit loop if breakpoint hit
                    }
                } while (state == LMCInterpreter.ExecutionState.RUNNING);

                // FIX: Create a final variable to capture the state for the inner lambda
                final LMCInterpreter.ExecutionState finalState = state;
                Platform.runLater(() -> finishExecution(finalState, interpreter.getErrorMessage()));
            });
        }
    }

    private void executeStep() {
        LMCInterpreter.ExecutionState state = interpreter.step();
        if (state == LMCInterpreter.ExecutionState.AWAITING_INPUT
                || state == LMCInterpreter.ExecutionState.BREAKPOINT_HIT) {
            if (executionTimeline != null) {
                executionTimeline.pause();
            }
            if (state == LMCInterpreter.ExecutionState.BREAKPOINT_HIT) {
                finishExecution(state, "Breakpoint hit at address " + interpreter.getProgramCounter());
            }
            stepButton.setDisable(false); // Enable step button when paused
            return; // Wait for input or breakpoint handling
        }

        if (state != LMCInterpreter.ExecutionState.RUNNING) {
            finishExecution(state, interpreter.getErrorMessage());
        } else {
            int pc = interpreter.getProgramCounter();
            updateMemoryVisualizer(interpreter.getMemory(), interpreter.getLastAccessedAddress(),
                    interpreter.getAssembledCode());
            highlightCurrentInstruction(pc);
        }
    }

    private void finishExecution(LMCInterpreter.ExecutionState finalState, String message) {
        if (executionTimeline != null)
            executionTimeline.stop();
        stopButton.setDisable(true);
        startButton.setDisable(false);
        speedModeToggle.setDisable(false);
        stepButton.setDisable(true); // Disable step button by default

        updateMemoryVisualizer(interpreter.getMemory(), -1, interpreter.getAssembledCode());
        highlightCurrentInstruction(-1);

        switch (finalState) {
            case HALTED:
                combinedConsole.appendText("\n--- Program Halted ---\" + interpreter.getOutput());
                break;
            case STOPPED:
                combinedConsole.appendText("\n--- Program Stopped by User ---");
                break;
            case ERROR:
                combinedConsole.appendText("\n--- Runtime Error ---\" + message);
                break;
            case AWAITING_INPUT:
            case BREAKPOINT_HIT:
                combinedConsole.appendText("\n--- Program Paused ---\" + message);
                stepButton.setDisable(false); // Enable step button when paused
                break;
        }
    }

    private void highlightCurrentInstruction(int pc) {
        Platform.runLater(() -> {
            CodeArea codeArea = getCurrentCodeArea();
            if (codeArea != null) {
                if (lastHighlightedLine != -1) {
                    codeArea.setParagraphStyle(lastHighlightedLine, Collections.emptyList());
                }
                if (currentAssembledCode != null && currentAssembledCode.addressToLineMap.containsKey(pc)) {
                    int line = currentAssembledCode.addressToLineMap.get(pc);
                    if (line < codeArea.getParagraphs().size()) {
                        codeArea.setParagraphStyle(line, Collections.singleton("current-line"));
                        lastHighlightedLine = line;
                    }
                } else {
                    lastHighlightedLine = -1;
                }
            }
        });
    }

    private void updateErrorDisplay() {
        CodeArea codeArea = getCurrentCodeArea();
        if (codeArea != null) {
            // This will trigger a re-rendering of the paragraph graphics
            codeArea.setParagraphGraphicFactory(createGutterGraphicFactory());

            errorListView.getItems().clear();
            if (!syntaxErrors.isEmpty()) {
                for (Map.Entry<Integer, String> entry : syntaxErrors.entrySet()) {
                    int lineNumber = entry.getKey();
                    String errorMessage = entry.getValue();
                    String lineContent = "";
                    if (lineNumber > 0 && lineNumber <= codeArea.getParagraphs().size()) {
                        lineContent = codeArea.getParagraph(lineNumber - 1).getText().trim();
                    }
                    errorListView.getItems()
                            .add(String.format("Line %d: %s\n  -> %s", lineNumber, errorMessage, lineContent));
                }
                bottomTabPane.getSelectionModel().select(1); // Select the Errors tab
            } else {
                bottomTabPane.getSelectionModel().select(0); // Select the Console tab if no errors
            }
        }
    }

    private IntFunction<Node> createGutterGraphicFactory() {
        return lineNumber -> {
            HBox hbox = new HBox(5); // 5 pixels spacing
            hbox.setAlignment(Pos.CENTER_LEFT);

            // Line number label
            Label lineNo = new Label(String.format("%3d", lineNumber + 1));
            lineNo.getStyleClass().add("linenumbers");

            // Breakpoint indicator
            Region breakpointIndicator = new Region();
            breakpointIndicator.setPrefSize(16, 16); // Fixed size for alignment
            breakpointIndicator.getStyleClass().add("breakpoint-area"); // For click handling
            if (breakpoints.contains(lineNumber + 1)) {
                breakpointIndicator.getStyleClass().add("breakpoint-indicator");
            }

            // Error indicator
            Region errorIndicator = new Region();
            errorIndicator.setPrefSize(16, 16); // Fixed size for alignment
            if (syntaxErrors.containsKey(lineNumber + 1)) { // LMCParseException uses 1-based line numbers
                errorIndicator.getStyleClass().add("error-indicator");
                Tooltip tooltip = new Tooltip(syntaxErrors.get(lineNumber + 1));
                Tooltip.install(errorIndicator, tooltip);
            }

            hbox.getChildren().addAll(lineNo, breakpointIndicator, errorIndicator);

            // Handle click to toggle breakpoint
            breakpointIndicator.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    int clickedLine = lineNumber + 1;
                    if (breakpoints.contains(clickedLine)) {
                        breakpoints.remove(clickedLine);
                    } else {
                        breakpoints.add(clickedLine);
                    }
                    updateErrorDisplay(); // Re-render gutter to show/hide breakpoint
                }
            });

            return hbox;
        };
    }

    private GridPane createMemoryVisualizer() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("memory-grid");
        for (int i = 0; i < 100; i++) {
            Label valueLabel = new Label("000");
            valueLabel.getStyleClass().add("memory-digit"); // Style for the 3-digit value

            Label instructionLabel = new Label("");
            instructionLabel.getStyleClass().add("memory-instruction-text"); // Style for the instruction text

            VBox cellBox = new VBox(valueLabel, instructionLabel);
            cellBox.getStyleClass().add("memory-cell");
            cellBox.setAlignment(javafx.geometry.Pos.CENTER);
            cellBox.setSpacing(2); // Small spacing between value and instruction

            memoryCellBoxes[i] = cellBox;
            grid.add(cellBox, i % 10, i / 10);
        }
        return grid;
    }

    private void updateMemoryVisualizer(int[] memory, int highlightedAddress, LMCParser.AssembledCode assembledCode) {
        Platform.runLater(() -> {
            if (lastHighlightedMemoryCell != -1) {
                memoryCellBoxes[lastHighlightedMemoryCell].getStyleClass().remove("memory-cell-highlight");
            }
            int memoryUsed = 0;
            for (int i = 0; i < 100; i++) {
                VBox cellBox = memoryCellBoxes[i];
                Label valueLabel = (Label) cellBox.getChildren().get(0);
                Label instructionLabel = (Label) cellBox.getChildren().get(1);

                if (memory[i] != 0)
                    memoryUsed++;
                String formattedValue = String.format("%03d", memory[i]);
                valueLabel.setText(formattedValue);

                // Apply styling and text for instructions
                if (assembledCode != null && assembledCode.instructions.containsKey(i)) {
                    instructionLabel.setText(assembledCode.instructions.get(i));
                    cellBox.getStyleClass().add("memory-cell-instruction");
                } else {
                    instructionLabel.setText("DAT"); // Indicate data cell
                    cellBox.getStyleClass().remove("memory-cell-instruction");
                }
            }
            memoryUsedLabel.setText("Memory Used: " + memoryUsed + " / 100");
            if (highlightedAddress != -1) {
                memoryCellBoxes[highlightedAddress].getStyleClass().add("memory-cell-highlight");
                lastHighlightedMemoryCell = highlightedAddress;
            } else {
                lastHighlightedMemoryCell = -1;
            }
        });
    }

    private void updateMemoryVisualizer(Map<Integer, Integer> memoryMap, int highlightedAddress,
            LMCParser.AssembledCode assembledCode) {
        int[] memArray = new int[100];
        for (Map.Entry<Integer, Integer> entry : memoryMap.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < 100) {
                memArray[entry.getKey()] = entry.getValue();
            }
        }
        updateMemoryVisualizer(memArray, highlightedAddress, assembledCode);
    }

    private InputProvider createInputProvider() {
        return () -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                combinedConsole.appendText("\nEnter input: ");
                combinedConsole.setEditable(true);
                combinedConsole.requestFocus();
                pendingInputRequest = future;
            });
            return future;
        };
    }

    private void openProject() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open LMC Project");
        File initialDir = (currentProjectDirectory != null) ? currentProjectDirectory
                : new File(System.getProperty("user.home"));
        directoryChooser.setInitialDirectory(initialDir);
        File dir = directoryChooser.showDialog(primaryStage);
        if (dir != null) {
            currentProjectDirectory = dir;
            refreshFileExplorer();
            primaryStage.setTitle("LMC IDE - " + dir.getName());
        }
    }

    private void refreshFileExplorer() {
        if (currentProjectDirectory != null) {
            TreeItem<File> rootItem = new TreeItem<>(currentProjectDirectory);
            rootItem.setExpanded(true);
            fileExplorer.setRoot(rootItem);
            populateTreeView(currentProjectDirectory, rootItem);
        }
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
                    TreeItem<File> item = new TreeItem<>(file);
                    if (file.isDirectory()) {
                        item.setGraphic(new ImageView(folderIcon));
                        populateTreeView(file, item);
                    } else {
                        item.setGraphic(new ImageView(fileIcon));
                    }
                    parentItem.getChildren().add(item);
                }
            }
        }
    }

    private void openFile(File file) {
        for (Tab tab : codeTabPane.getTabs()) {
            if (file.equals(tab.getUserData())) {
                codeTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        try {
            String content = Files.readString(file.toPath());
            Tab tab = new Tab(file.getName());
            tab.setUserData(file);
            CodeArea codeArea = createCodeArea();
            codeArea.replaceText(content);
            tab.setContent(codeArea);
            codeTabPane.getTabs().add(tab);
            codeTabPane.getSelectionModel().select(tab);
            updateTitle();
        } catch (IOException e) {
            showAlert("Error", "Could not read file: " + e.getMessage());
        }
    }

    private void newFile() {
        Tab tab = new Tab("New File");
        tab.setContent(createCodeArea());
        codeTabPane.getTabs().add(tab);
        codeTabPane.getSelectionModel().select(tab);
        updateTitle();
    }

    private void updateTitle() {
        String title = "LMC IDE";
        if (currentProjectDirectory != null) {
            title += " - " + currentProjectDirectory.getName();
        }
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            title += " - " + selectedTab.getText();
        } else if (currentProjectDirectory == null) {
            title += " - New File";
        }
        primaryStage.setTitle(title);
    }

    private void newFolder() {
        if (currentProjectDirectory == null) {
            showAlert("Error", "Please open a project first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("New Folder");
        dialog.setTitle("New Folder");
        dialog.setHeaderText("Create New Folder");
        dialog.setContentText("Enter folder name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            File parentDir = currentProjectDirectory;
            TreeItem<File> selectedItem = fileExplorer.getSelectionModel().getSelectedItem();
            if (selectedItem != null && selectedItem.getValue().isDirectory()) {
                parentDir = selectedItem.getValue();
            }

            File newDir = new File(parentDir, name);
            if (!newDir.exists()) {
                if (newDir.mkdir()) {
                    showAlert("Success", "Folder '" + name + "' created.");
                    refreshFileExplorer();
                } else {
                    showAlert("Error", "Failed to create folder '" + name + "'.");
                }
            } else {
                showAlert("Error", "Folder '" + name + "' already exists.");
            }
        });
    }

    private void saveFile() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            File file = (File) selectedTab.getUserData();
            if (file != null) {
                try {
                    Files.writeString(file.toPath(), getCurrentCodeArea().getText(), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    showAlert("Success", "File saved successfully!");
                } catch (IOException e) {
                    showAlert("Error", "Could not save file: " + e.getMessage());
                }
            } else {
                saveFileAs();
            }
        }
    }

    private void saveFileAs() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save File As");
            if (currentProjectDirectory != null) {
                fileChooser.setInitialDirectory(currentProjectDirectory);
            }
            File file = fileChooser.showSaveDialog(primaryStage);
            if (file != null) {
                try {
                    Files.writeString(file.toPath(), getCurrentCodeArea().getText(), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    selectedTab.setText(file.getName());
                    selectedTab.setUserData(file);
                    updateTitle();
                    showAlert("Success", "File saved successfully!");
                    if (currentProjectDirectory != null && file.getParentFile().equals(currentProjectDirectory)) {
                        refreshFileExplorer();
                    }
                } catch (IOException e) {
                    showAlert("Error", "Could not save file: " + e.getMessage());
                }
            }
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setupHotkeys(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::saveFile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN), () -> {
            Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != null) {
                codeTabPane.getTabs().remove(selectedTab);
            }
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN), () -> {
            CodeArea codeArea = getCurrentCodeArea();
            if (codeArea != null) {
                codeArea.undo();
            }
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN), () -> {
            CodeArea codeArea = getCurrentCodeArea();
            if (codeArea != null) {
                codeArea.redo();
            }
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), this::newFile);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::newFolder);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), this::openProject);
    }

    private void applyTheme(String theme) {
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        root.getStyleClass().removeAll("dark-mode", "high-contrast");
        root.getStyleClass().add(theme);
        prefs.put("theme", theme);
    }

    private void formatCode() {
        if (autoFormattingEnabled) {
            String currentCode = getCurrentCodeArea().getText();
            String formattedCode = LMCFormatter.format(currentCode);
            getCurrentCodeArea().replaceText(formattedCode);
        } else {
            showAlert("Info", "Auto-formatting is disabled. Enable it in Features menu.");
        }
    }

    private void setupConsoleInputListener() {
        combinedConsole.setOnKeyPressed(event -> {
            if (pendingInputRequest != null && event.getCode() == KeyCode.ENTER) {
                String text = combinedConsole.getText();
                String[] lines = text.split("\n");
                if (lines.length == 0)
                    return;

                String lastLine = lines[lines.length - 1];
                String inputStr = lastLine.substring(lastLine.lastIndexOf(":") + 1).trim();

                try {
                    int input = Integer.parseInt(inputStr);
                    if (pendingInputRequest != null) {
                        pendingInputRequest.complete(input);
                        pendingInputRequest = null;
                        combinedConsole.setEditable(false);

                        // If slow mode was waiting for input, resume it
                        if (executionTimeline != null && executionTimeline.getStatus() == Timeline.Status.PAUSED) {
                            executeStep(); // Process the input
                            executionTimeline.play(); // Continue the timeline
                        }
                    }
                } catch (NumberFormatException e) {
                    Platform.runLater(() -> {
                        combinedConsole.appendText("\nInvalid input. Please enter an integer.\n");
                        combinedConsole.appendText("Enter input: ");
                    });
                }
                event.consume();
            }
        });
    }

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    public static void main(String[] args) {
        launch(args);
    }

    public CodeArea getCurrentCodeArea() {
        Tab selectedTab = codeTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            return (CodeArea) selectedTab.getContent();
        }
        return null;
    }

    public TabPane getCodeTabPane() {
        return codeTabPane;
    }
}
