package com.lmc.ide;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

public class App extends Application {

    private CodeArea codeArea;
    private TextArea combinedConsole;
    private TreeView<File> fileExplorer;
    private Label memoryDisplay;
    private Stage primaryStage;
    private File currentOpenFile;
    private File currentProjectDirectory;
    private BorderPane root;
    private CompletableFuture<Integer> pendingInputRequest;
    private LMCIDEFeatures ideFeatures;
    private LMCInterpreter interpreter; // Now a class field

    private boolean autocorrectEnabled = true;
    private boolean autoFormattingEnabled = true;
    private boolean errorHighlightingEnabled = true;

    private Button startButton;
    private Button stopButton;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("LMC IDE");
        interpreter = new LMCInterpreter();

        Preferences prefs = Preferences.userNodeForPackage(App.class);
        int initialFontSize = prefs.getInt("fontSize", 14);

        // --- UI Components ---
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("code-area");

        memoryDisplay = new Label("Memory Used: 0 / 100");
        memoryDisplay.getStyleClass().add("label");

        ideFeatures = new LMCIDEFeatures(codeArea, new LMCParser(), memoryDisplay,
                autocorrectEnabled, autoFormattingEnabled, errorHighlightingEnabled);

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, LMCSyntaxHighlighter.computeHighlighting(newText));
        });
        codeArea.setStyleSpans(0, LMCSyntaxHighlighter.computeHighlighting(codeArea.getText()));

        combinedConsole = new TextArea();
        combinedConsole.setPromptText("LMC Console (Input/Output)");
        combinedConsole.getStyleClass().add("console-area");
        combinedConsole.setPrefHeight(100);
        combinedConsole.setEditable(false);

        fileExplorer = new TreeView<>();
        fileExplorer.getStyleClass().add("tree-view");
        fileExplorer.setPrefWidth(200);
        fileExplorer.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.getValue().isFile()) {
                openFile(newValue.getValue());
            }
        });

        VBox memoryBox = new VBox(5, memoryDisplay);
        memoryBox.getStyleClass().add("memory-box");
        memoryBox.setPrefWidth(250);

        // --- Layout ---
        root = new BorderPane();
        root.setStyle("-fx-font-size: " + initialFontSize + "px;");

        // --- Toolbar with Start/Stop Buttons ---
        MenuBar menuBar = createMenuBar(prefs, initialFontSize);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        startButton = new Button("▶ Start");
        startButton.setOnAction(e -> runLMC());

        stopButton = new Button("■ Stop");
        stopButton.setOnAction(e -> stopLMC());
        stopButton.setDisable(true);

        ToolBar toolBar = new ToolBar(menuBar, spacer, startButton, stopButton);
        root.setTop(toolBar);

        // --- Center Content ---
        SplitPane horizontalMainSplitPane = new SplitPane();
        horizontalMainSplitPane.getItems().addAll(fileExplorer, codeArea);
        horizontalMainSplitPane.setDividerPositions(0.2);

        SplitPane verticalMainSplitPane = new SplitPane();
        verticalMainSplitPane.setOrientation(Orientation.VERTICAL);
        verticalMainSplitPane.getItems().addAll(horizontalMainSplitPane, combinedConsole);
        verticalMainSplitPane.setDividerPositions(0.75);

        root.setCenter(verticalMainSplitPane);
        root.setRight(memoryBox);

        // --- Scene and Stage ---
        Scene scene = new Scene(root, 1200, 800);
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

        // File Menu
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

        // Edit Menu
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

        // Features Menu
        Menu featuresMenu = new Menu("Features");
        Label autocorrectCheck = new Label("✓");
        autocorrectCheck.setMinWidth(20);
        MenuItem autocorrectToggle = new MenuItem("Autocorrect");
        autocorrectToggle.setGraphic(autocorrectEnabled ? autocorrectCheck : null);
        autocorrectToggle.setOnAction(e -> {
            autocorrectEnabled = !autocorrectEnabled;
            ideFeatures.setAutocorrectEnabled(autocorrectEnabled);
            autocorrectToggle.setGraphic(autocorrectEnabled ? autocorrectCheck : null);
        });

        Label autoFormatCheck = new Label("✓");
        autoFormatCheck.setMinWidth(20);
        MenuItem autoFormattingToggle = new MenuItem("Auto-formatting");
        autoFormattingToggle.setGraphic(autoFormattingEnabled ? autoFormatCheck : null);
        autoFormattingToggle.setOnAction(e -> {
            autoFormattingEnabled = !autoFormattingEnabled;
            ideFeatures.setAutoFormattingEnabled(autoFormattingEnabled);
            autoFormattingToggle.setGraphic(autoFormattingEnabled ? autoFormatCheck : null);
        });

        Label errorHighlightCheck = new Label("✓");
        errorHighlightCheck.setMinWidth(20);
        MenuItem errorHighlightingToggle = new MenuItem("Error Highlighting");
        errorHighlightingToggle.setGraphic(errorHighlightingEnabled ? errorHighlightCheck : null);
        errorHighlightingToggle.setOnAction(e -> {
            errorHighlightingEnabled = !errorHighlightingEnabled;
            ideFeatures.setErrorHighlightingEnabled(errorHighlightingEnabled);
            errorHighlightingToggle.setGraphic(errorHighlightingEnabled ? errorHighlightCheck : null);
        });
        featuresMenu.getItems().addAll(autocorrectToggle, autoFormattingToggle, errorHighlightingToggle);

        // View Menu
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

        menuBar.getMenus().addAll(fileMenu, editMenu, featuresMenu, viewMenu);
        return menuBar;
    }

    private void stopLMC() {
        if (interpreter != null) {
            interpreter.stop();
        }
        stopButton.setDisable(true);
        startButton.setDisable(false);
    }

    private void runLMC() {
        startButton.setDisable(true);
        stopButton.setDisable(false);
        combinedConsole.clear();
        combinedConsole.setEditable(false);

        String lmcCode = codeArea.getText();

        if (autocorrectEnabled) {
            String correctedCode = ideFeatures.autocorrectCode(lmcCode);
            if (!lmcCode.equals(correctedCode)) {
                codeArea.replaceText(correctedCode);
                lmcCode = correctedCode;
            }
        }

        final String finalLmcCode = lmcCode;

        InputProvider inputProvider = () -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                combinedConsole.appendText("\nEnter input: ");
                combinedConsole.setEditable(true);
                combinedConsole.requestFocus();
                pendingInputRequest = future;
            });
            return future;
        };

        CompletableFuture.supplyAsync(() -> {
            try {
                return interpreter.run(finalLmcCode, inputProvider);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((output, ex) -> {
            Platform.runLater(() -> {
                combinedConsole.setEditable(false);
                if (ex == null) {
                    combinedConsole.appendText("\nLMC Program Output:\n" + output);
                } else {
                    Throwable cause = ex.getCause();
                    if (cause instanceof LMCParser.LMCParseException) {
                        LMCParser.LMCParseException parseEx = (LMCParser.LMCParseException) cause;
                        combinedConsole.appendText("\nLMC Parse Error:\n" + parseEx.getMessage() + "\n");
                    } else if (cause instanceof LMCInterpreter.LMCRuntimeException) {
                        combinedConsole.appendText("\nLMC Runtime Error:\n" + cause.getMessage() + "\n");
                    } else {
                        combinedConsole.appendText("\nAn unexpected error occurred:\n"
                                + (cause != null ? cause.getMessage() : ex.getMessage()) + "\n");
                    }
                }
                startButton.setDisable(false);
                stopButton.setDisable(true);
            });
        });
    }

    // ... (All other helper methods: openProject, saveFile, applyTheme, etc. remain
    // the same)

    private void openProject() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open LMC Project");
        File selectedDirectory = directoryChooser.showDialog(primaryStage);

        if (selectedDirectory != null) {
            currentProjectDirectory = selectedDirectory;
            refreshFileExplorer();
            primaryStage.setTitle("LMC IDE - " + selectedDirectory.getName());
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
            if (currentProjectDirectory != null) {
                primaryStage.setTitle("LMC IDE - " + currentProjectDirectory.getName() + " - " + file.getName());
            } else {
                primaryStage.setTitle("LMC IDE - " + file.getName());
            }
        } catch (IOException e) {
            showAlert("Error", "Could not read file: " + e.getMessage());
        }
    }

    private void newFile() {
        codeArea.clear();
        currentOpenFile = null;
        primaryStage.setTitle("LMC IDE - New File");
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
        // Note: Ctrl+R hotkey for run is removed as we now have a dedicated button
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