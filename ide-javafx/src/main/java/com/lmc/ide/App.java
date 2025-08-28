package com.lmc.ide;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

public class App extends Application {

    private CodeArea codeArea;
    private TextArea combinedConsole;
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
    private Button startButton, stopButton;
    private ToggleButton speedModeToggle;
    private Slider speedSlider;
    private Label[] memoryCellBoxes = new Label[100];
    private Label memoryUsedLabel;
    private int lastHighlightedLine = -1;
    private int lastHighlightedMemoryCell = -1;

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

        Preferences prefs = Preferences.userNodeForPackage(App.class);
        int initialFontSize = prefs.getInt("fontSize", 14);

        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("code-area");

        GridPane memoryVisualizer = createMemoryVisualizer();
        memoryUsedLabel = new Label("Memory Used: 0 / 100");

        ideFeatures = new LMCIDEFeatures(codeArea, parser, new Label(),
                autocorrectEnabled, autoFormattingEnabled, errorHighlightingEnabled);

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, LMCSyntaxHighlighter.computeHighlighting(newText));
            try {
                LMCParser.AssembledCode assembled = parser.parse(newText);
                updateMemoryVisualizer(assembled.memoryMap, -1, assembled);
            } catch (LMCParser.LMCParseException e) {
                // Ignore parse errors for live preview
            }
        });

        combinedConsole = new TextArea();
        combinedConsole.setPromptText("LMC Console (Input/Output)");
        combinedConsole.getStyleClass().add("console-area");
        combinedConsole.setPrefHeight(150);
        combinedConsole.setEditable(false);

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

        speedModeToggle = new ToggleButton("Fast Mode");
        speedSlider = new Slider(50, 2000, 500);
        speedSlider.setPrefWidth(150);
        speedSlider.setBlockIncrement(50);
        speedSlider.setDisable(true);
        speedModeToggle.setOnAction(e -> {
            speedSlider.setDisable(!speedModeToggle.isSelected());
            speedModeToggle.setText(speedModeToggle.isSelected() ? "Slow Mode" : "Fast Mode");
        });

        ToolBar toolBar = new ToolBar(menuBar, spacer, speedModeToggle, speedSlider, startButton, stopButton);
        root.setTop(toolBar);

        SplitPane horizontalMainSplitPane = new SplitPane(fileExplorer, codeArea);
        horizontalMainSplitPane.setDividerPositions(0.2);

        SplitPane verticalMainSplitPane = new SplitPane(horizontalMainSplitPane, combinedConsole);
        verticalMainSplitPane.setOrientation(Orientation.VERTICAL);
        verticalMainSplitPane.setDividerPositions(0.8);

        root.setCenter(verticalMainSplitPane);
        root.setRight(memoryBox);

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/vscode-style.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/lmc-syntax.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        setupHotkeys(scene);
        setupConsoleInputListener();
        applyTheme(prefs.get("theme", "dark-mode"));
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
        undoItem.setOnAction(e -> codeArea.undo());
        MenuItem redoItem = new MenuItem("Redo");
        redoItem.setOnAction(e -> codeArea.redo());
        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setOnAction(e -> codeArea.cut());
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> codeArea.copy());
        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setOnAction(e -> codeArea.paste());
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
        MenuItem lightModeItem = new MenuItem("Light Mode");
        lightModeItem.setOnAction(e -> applyTheme("light-mode"));
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

        ColorPicker accentColorPicker = new ColorPicker();
        accentColorPicker.setValue(Color.web(prefs.get("accentColor", "#007acc")));
        accentColorPicker.setOnAction(e -> {
            String hexColor = toHexString(accentColorPicker.getValue());
            root.setStyle(root.getStyle() + "; -fx-accent-color: " + hexColor + ";");
            prefs.put("accentColor", hexColor);
        });
        CustomMenuItem accentColorMenuItem = new CustomMenuItem(accentColorPicker);
        accentColorMenuItem.setHideOnClick(false);
        viewMenu.getItems().addAll(lightModeItem, darkModeItem, highContrastModeItem, new SeparatorMenuItem(),
                fontSizeMenuItem, accentColorMenuItem);

        Menu helpMenu = new Menu("Help");
        MenuItem lmcOpcodesItem = new MenuItem("LMC Opcodes");
        lmcOpcodesItem.setOnAction(e -> showLmcOpcodes());
        helpMenu.getItems().add(lmcOpcodesItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, featuresMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private void showLmcOpcodes() {
        Stage stage = new Stage();
        stage.setTitle("LMC Instruction Opcodes");

        TableView<OpcodeEntry> table = new TableView<>();

        TableColumn<OpcodeEntry, String> mnemonicCol = new TableColumn<>("Mnemonic");
        mnemonicCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getMnemonic()));

        TableColumn<OpcodeEntry, String> opcodeCol = new TableColumn<>("Opcode");
        opcodeCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getOpcode()));

        TableColumn<OpcodeEntry, String> descriptionCol = new TableColumn<>("Description");
        descriptionCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDescription()));

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
                new OpcodeEntry("DAT", "(data)", "Declare data at this address")
        );

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
        highlightCurrentInstruction(-1);
        updateMemoryVisualizer(interpreter.getMemory(), -1, interpreter.getAssembledCode());
    }

    private void runLMC() {
        startButton.setDisable(true);
        stopButton.setDisable(false);
        speedModeToggle.setDisable(true);
        combinedConsole.clear();
        combinedConsole.setEditable(false);
        highlightCurrentInstruction(-1);

        String lmcCode = codeArea.getText();
        if (autocorrectEnabled) {
            String correctedCode = ideFeatures.autocorrectCode(lmcCode);
            if (!lmcCode.equals(correctedCode)) {
                codeArea.replaceText(correctedCode);
                lmcCode = correctedCode;
            }
        }

        try {
            currentAssembledCode = parser.parse(lmcCode);
            interpreter.load(currentAssembledCode, createInputProvider());
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
                } while (state == LMCInterpreter.ExecutionState.RUNNING);

                // FIX: Create a final variable to capture the state for the inner lambda
                final LMCInterpreter.ExecutionState finalState = state;
                Platform.runLater(() -> finishExecution(finalState, interpreter.getErrorMessage()));
            });
        }
    }

    private void executeStep() {
        LMCInterpreter.ExecutionState state = interpreter.step();
        if (state == LMCInterpreter.ExecutionState.AWAITING_INPUT) {
            if (executionTimeline != null) {
                executionTimeline.pause();
            }
            return; // Wait for input
        }

        if (state != LMCInterpreter.ExecutionState.RUNNING) {
            finishExecution(state, interpreter.getErrorMessage());
        } else {
            int pc = interpreter.getProgramCounter();
            updateMemoryVisualizer(interpreter.getMemory(), interpreter.getLastAccessedAddress(), interpreter.getAssembledCode());
            highlightCurrentInstruction(pc);
        }
    }

    private void finishExecution(LMCInterpreter.ExecutionState finalState, String message) {
        if (executionTimeline != null)
            executionTimeline.stop();
        stopButton.setDisable(true);
        startButton.setDisable(false);
        speedModeToggle.setDisable(false);

        updateMemoryVisualizer(interpreter.getMemory(), -1, interpreter.getAssembledCode());
        highlightCurrentInstruction(-1);

        switch (finalState) {
            case HALTED:
                combinedConsole.appendText("\n--- Program Halted ---\n" + interpreter.getOutput());
                break;
            case STOPPED:
                combinedConsole.appendText("\n--- Program Stopped by User ---");
                break;
            case ERROR:
                combinedConsole.appendText("\n--- Runtime Error ---\n" + message);
                break;
            case AWAITING_INPUT:
                // This state is handled by the input provider, no action needed here
                break;
        }
    }

    private void highlightCurrentInstruction(int pc) {
        Platform.runLater(() -> {
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
        });
    }

    private GridPane createMemoryVisualizer() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("memory-grid");
        for (int i = 0; i < 100; i++) {
            Label cellLabel = new Label("000");
            cellLabel.getStyleClass().add("memory-cell");
            memoryCellBoxes[i] = cellLabel;
            grid.add(cellLabel, i % 10, i / 10);
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
                if (memory[i] != 0)
                    memoryUsed++;
                String formatted = String.format("%03d", memory[i]);
                memoryCellBoxes[i].setText(formatted);

                // Apply styling for instructions
                if (assembledCode != null && assembledCode.instructions.containsKey(i)) {
                    memoryCellBoxes[i].getStyleClass().add("memory-cell-instruction");
                } else {
                    memoryCellBoxes[i].getStyleClass().remove("memory-cell-instruction");
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

    private void updateMemoryVisualizer(Map<Integer, Integer> memoryMap, int highlightedAddress, LMCParser.AssembledCode assembledCode) {
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
                    parentItem.getChildren().add(item);
                    if (file.isDirectory()) {
                        populateTreeView(file, item);
                    }
                }
            }
        }
    }

    private void openFile(File file) {
        try {
            String content = Files.readString(file.toPath());
            codeArea.replaceText(content);
            currentOpenFile = file;
            updateTitle();
        } catch (IOException e) {
            showAlert("Error", "Could not read file: " + e.getMessage());
        }
    }

    private void newFile() {
        codeArea.clear();
        currentOpenFile = null;
        updateTitle();
    }

    private void updateTitle() {
        String title = "LMC IDE";
        if (currentProjectDirectory != null) {
            title += " - " + currentProjectDirectory.getName();
        }
        if (currentOpenFile != null) {
            title += " - " + currentOpenFile.getName();
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
        if (currentOpenFile != null) {
            try {
                Files.writeString(currentOpenFile.toPath(), codeArea.getText(), StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                showAlert("Success", "File saved successfully!");
            } catch (IOException e) {
                showAlert("Error", "Could not save file: " + e.getMessage());
            }
        } else {
            saveFileAs();
        }
    }

    private void saveFileAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File As");
        if (currentProjectDirectory != null) {
            fileChooser.setInitialDirectory(currentProjectDirectory);
        }
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), codeArea.getText(), StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                currentOpenFile = file;
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setupHotkeys(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::saveFile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN), codeArea::undo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN), codeArea::redo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), this::newFile);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::newFolder);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), this::openProject);
    }

    private void applyTheme(String theme) {
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        root.getStyleClass().removeAll("light-mode", "dark-mode", "high-contrast");
        root.getStyleClass().add(theme);
        prefs.put("theme", theme);
    }

    private void formatCode() {
        if (autoFormattingEnabled) {
            String currentCode = codeArea.getText();
            String formattedCode = LMCFormatter.format(currentCode);
            codeArea.replaceText(formattedCode);
        } else {
            showAlert("Info", "Auto-formatting is disabled. Enable it in Features menu.");
        }
    }

    private void setupConsoleInputListener() {
        combinedConsole.setOnKeyPressed(event -> {
            if (pendingInputRequest != null && event.getCode() == KeyCode.ENTER) {
                String text = combinedConsole.getText();
                String[] lines = text.split("\\n");
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
}
