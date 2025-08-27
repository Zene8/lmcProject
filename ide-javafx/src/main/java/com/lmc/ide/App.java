package com.lmc.ide;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.ListView;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.lmc.ide.LMCParser.ParseError;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.nio.file.StandardOpenOption;

public class App extends Application {

    private CodeArea codeEditor;
    private TextArea outputArea;
    private TextField inputField;
    private Button runButton;
    private Button stepButton;
    private Button resetButton;
    private Label statusBar;

    private TextArea registerView;
    private TextArea memoryView;
    private TextArea assemblyView;
    private TextArea disassemblyView;

    private ListView<File> projectFilesList;

    private LMCInterpreter interpreter;
    private File currentFile;
    private File currentProjectDirectory;

    private Scene mainScene;
    private double currentFontSize = 14; // Default font size

    // LMC Keywords for Syntax Highlighting and Autocomplete
    private static final String[] LMC_INSTRUCTIONS = new String[]{
            "INP", "OUT", "LDA", "STA", "ADD", "SUB", "BRA", "BRZ", "BRP", "HLT", "DAT"
    };

    private static final String LMC_INSTRUCTION_PATTERN = "\\b(" + String.join("|", LMC_INSTRUCTIONS) +
            ")\\b";
    private static final String LMC_LABEL_PATTERN = "\\b[A-Za-z_][A-Za-z0-9_]*\\b(?=\\s*(?:INP|OUT|LDA|STA|ADD|SUB|BRA|BRZ|BRP|HLT|DAT|\\n|$))";
    private static final String LMC_NUMBER_PATTERN = "\\b\\d+\\b";
    private static final String LMC_COMMENT_PATTERN = "//[^\\n]*";

    private static final Pattern SYNTAX_PATTERN = Pattern.compile(
            "(?<INSTRUCTION>" + LMC_INSTRUCTION_PATTERN +
            ")|(?<LABEL>" + LMC_LABEL_PATTERN +
            ")|(?<NUMBER>" + LMC_NUMBER_PATTERN +
            ")|(?<COMMENT>" + LMC_COMMENT_PATTERN +
            ")"
    );

    private ContextMenu autocompleteMenu;
    private ListView<String> suggestionsList;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("LMC IDE");

        // Menu Bar
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem newMenuItem = new MenuItem("New");
        newMenuItem.setOnAction(e -> newFile());
        MenuItem openMenuItem = new MenuItem("Open...");
        openMenuItem.setOnAction(e -> openFile(primaryStage));
        MenuItem saveMenuItem = new MenuItem("Save");
        saveMenuItem.setOnAction(e -> saveFile(primaryStage));
        MenuItem saveAsMenuItem = new MenuItem("Save As...");
        saveAsMenuItem.setOnAction(e -> saveFileAs(primaryStage));
        MenuItem exitMenuItem = new MenuItem("Exit");
        exitMenuItem.setOnAction(e -> primaryStage.close());
        fileMenu.getItems().addAll(newMenuItem, openMenuItem, saveMenuItem, saveAsMenuItem, new SeparatorMenuItem(), exitMenuItem);
        menuBar.getMenus().add(fileMenu);

        Menu projectMenu = new Menu("Project");
        MenuItem openProjectMenuItem = new MenuItem("Open Project...");
        openProjectMenuItem.setOnAction(e -> openProject(primaryStage));
        projectMenu.getItems().add(openProjectMenuItem);
        menuBar.getMenus().add(projectMenu);

        Menu settingsMenu = new Menu("Settings");
        MenuItem increaseFont = new MenuItem("Increase Font");
        increaseFont.setOnAction(e -> changeFontSize(2));
        MenuItem decreaseFont = new MenuItem("Decrease Font");
        decreaseFont.setOnAction(e -> changeFontSize(-2));
        MenuItem resetFont = new MenuItem("Reset Font");
        resetFont.setOnAction(e -> resetFontSize());

        Menu themeMenu = new Menu("Theme");
        MenuItem lightTheme = new MenuItem("Light Theme");
        lightTheme.setOnAction(e -> setTheme("light-theme"));
        MenuItem darkTheme = new MenuItem("Dark Theme");
        darkTheme.setOnAction(e -> setTheme("dark-theme"));
        themeMenu.getItems().addAll(lightTheme, darkTheme);

        settingsMenu.getItems().addAll(increaseFont, decreaseFont, resetFont, new SeparatorMenuItem(), themeMenu);
        menuBar.getMenus().add(settingsMenu);

        // Project Files List
        projectFilesList = new ListView<>();
        projectFilesList.setPrefWidth(200);
        projectFilesList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Double click to open file
                File selectedFile = projectFilesList.getSelectionModel().getSelectedItem();
                if (selectedFile != null) {
                    openFile(primaryStage, selectedFile);
                }
            }
        });

        // Code Editor Area
        codeEditor = new CodeArea();
        codeEditor.setPlaceholder(new Label("Enter LMC code here..."));
        
        codeEditor.getStylesheets().add(getClass().getResource("/lmc-syntax.css").toExternalForm());
        codeEditor.setParagraphGraphicFactory(LineNumberFactory.get(codeEditor)); // Line numbers
        codeEditor.setStyle("-fx-font-size: " + currentFontSize + "px;"); // Apply initial font size

        // Apply syntax highlighting and parse for errors on text changes
        codeEditor.textProperty().addListener((obs, oldText, newText) -> {
            codeEditor.setStyleSpans(0, computeHighlighting(newText));
            showAutocompleteSuggestions();
            
            // Static Syntax Error Highlighting
            LMCParser parser = new LMCParser();
            List<ParseError> errors = parser.parse(newText);
            if (!errors.isEmpty()) {
                statusBar.setText("Error: " + errors.get(0).toString());
                runButton.setDisable(true);
                stepButton.setDisable(true);
            } else {
                statusBar.setText("Ready");
                runButton.setDisable(false);
                stepButton.setDisable(false);
            }
        });

        // Autocomplete setup
        suggestionsList = new ListView<>();
        suggestionsList.setPrefWidth(150);
        suggestionsList.setMaxHeight(200);
        autocompleteMenu = new ContextMenu();
        autocompleteMenu.getScene().setRoot(suggestionsList);

        suggestionsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                insertSelectedSuggestion();
            }
        });

        codeEditor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && autocompleteMenu.isShowing()) {
                insertSelectedSuggestion();
                event.consume(); // Consume the event to prevent new line
            } else if (event.getCode() == KeyCode.ESCAPE && autocompleteMenu.isShowing()) {
                autocompleteMenu.hide();
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN && autocompleteMenu.isShowing()) {
                suggestionsList.getSelectionModel().selectNext();
                event.consume();
            } else if (event.getCode() == KeyCode.UP && autocompleteMenu.isShowing()) {
                suggestionsList.getSelectionModel().selectPrevious();
                event.consume();
            }
        });

        // Output Area
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setPromptText("Program Output...");
        outputArea.setPrefRowCount(10);
        outputArea.setPrefColumnCount(50);

        // Input Field
        inputField = new TextField();
        inputField.setPromptText("Enter input here...");

        // Run, Step, Reset Buttons
        runButton = new Button("Run");
        runButton.setOnAction(e -> runCode());

        stepButton = new Button("Step");
        stepButton.setOnAction(e -> stepCode());
        stepButton.setDisable(true); // Disabled until a program is loaded/run or no errors

        resetButton = new Button("Reset");
        resetButton.setOnAction(e -> resetInterpreter());
        resetButton.setDisable(true); // Disabled until a program is loaded/run

        // Debugging Views
        registerView = new TextArea();
        registerView.setEditable(false);
        registerView.setPrefHeight(100);
        registerView.setPromptText("Registers: PC, ACC");

        memoryView = new TextArea();
        memoryView.setEditable(false);
        memoryView.setPrefHeight(200);
        memoryView.setPromptText("Memory (00-99)");

        assemblyView = new TextArea();
        assemblyView.setEditable(false);
        assemblyView.setPrefHeight(200);
        assemblyView.setPromptText("Assembly View");

        disassemblyView = new TextArea();
        disassemblyView.setEditable(false);
        disassemblyView.setPrefHeight(200);
        disassemblyView.setPromptText("Disassembly View");

        // Layout for Input and Run/Step/Reset Buttons
        HBox controlLayout = new HBox(10);
        controlLayout.setPadding(new Insets(10));
        controlLayout.getChildren().addAll(new Label("Input:"), inputField, runButton, stepButton, resetButton);

        // Layout for Debugging Views
        VBox debugLayout = new VBox(10);
        debugLayout.setPadding(new Insets(10));
        debugLayout.getChildren().addAll(new Label("Registers:"), registerView, new Label("Memory:"), memoryView);

        VBox assemblyDisassemblyLayout = new VBox(10);
        assemblyDisassemblyLayout.setPadding(new Insets(10));
        assemblyDisassemblyLayout.getChildren().addAll(new Label("Assembly:"), assemblyView, new Label("Disassembly:"), disassemblyView);

        // Status Bar
        statusBar = new Label("Ready");
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        // Main Layout
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setPadding(new Insets(10));

        VBox editorOutputLayout = new VBox(10);
        editorOutputLayout.getChildren().addAll(codeEditor, outputArea);

        HBox centerContent = new HBox(10);
        centerContent.getChildren().addAll(projectFilesList, editorOutputLayout, debugLayout, assemblyDisassemblyLayout);

        root.setCenter(centerContent);
        root.setBottom(new VBox(controlLayout, statusBar));

        mainScene = new Scene(root, 1400, 700); // Increased width for new views
        mainScene.getStylesheets().add(getClass().getResource("/lmc-syntax.css").toExternalForm());
        primaryStage.setScene(mainScene);
        primaryStage.show();

        setTheme("light-theme"); // Set default theme
    }

    private void changeFontSize(double delta) {
        currentFontSize += delta;
        if (currentFontSize < 8) currentFontSize = 8; // Minimum font size
        codeEditor.setStyle("-fx-font-size: " + currentFontSize + "px;");
    }

    private void resetFontSize() {
        currentFontSize = 14; // Reset to default
        codeEditor.setStyle("-fx-font-size: " + currentFontSize + "px;");
    }

    private void setTheme(String themeName) {
        mainScene.getRoot().getStyleClass().remove("light-theme");
        mainScene.getRoot().getStyleClass().remove("dark-theme");
        mainScene.getRoot().getStyleClass().add(themeName);
    }

    private void newFile() {
        codeEditor.clear();
        outputArea.clear();
        inputField.clear();
        registerView.clear();
        memoryView.clear();
        assemblyView.clear();
        disassemblyView.clear();
        statusBar.setText("Ready");
        currentFile = null;
        stepButton.setDisable(true);
        resetButton.setDisable(true);
        runButton.setDisable(false); // Re-enable run button
        projectFilesList.getItems().clear(); // Clear project files
        currentProjectDirectory = null;
    }

    private void openFile(Stage primaryStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open LMC File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("LMC Files", "*.lmc"));
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            openFile(primaryStage, file);
        }
    }

    private void openFile(Stage primaryStage, File file) {
        try {
            String content = Files.readString(file.toPath());
            codeEditor.replaceText(content);
            currentFile = file;
            statusBar.setText("Opened: " + file.getName());
            resetInterpreter();
        } catch (IOException e) {
            statusBar.setText("Error opening file: " + e.getMessage());
        }
    }

    private void saveFile(Stage primaryStage) {
        if (currentFile != null) {
            try {
                Files.writeString(currentFile.toPath(), codeEditor.getText());
                statusBar.setText("Saved: " + currentFile.getName());
            } catch (IOException e) {
                statusBar.setText("Error saving file: " + e.getMessage());
            }
        } else {
            saveFileAs(primaryStage);
        }
    }

    private void saveFileAs(Stage primaryStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save LMC File As");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("LMC Files", "*.lmc"));
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), codeEditor.getText());
                currentFile = file;
                statusBar.setText("Saved as: " + file.getName());
            } catch (IOException e) {
                statusBar.setText("Error saving file: " + e.getMessage());
            }
        }
    }

    private void openProject(Stage primaryStage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open LMC Project Directory");
        File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            currentProjectDirectory = selectedDirectory;
            projectFilesList.getItems().clear();
            try {
                Files.walk(selectedDirectory.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase().endsWith(".lmc"))
                        .forEach(p -> projectFilesList.getItems().add(p.toFile()));
                statusBar.setText("Opened project: " + selectedDirectory.getName());
            } catch (IOException e) {
                statusBar.setText("Error opening project: " + e.getMessage());
            }
        }
    }

    private void showAutocompleteSuggestions() {
        String currentLine = codeEditor.getParagraph(codeEditor.getCurrentParagraph()).getText();
        int caretPosInLine = codeEditor.getCaretColumn();
        String prefix = currentLine.substring(0, caretPosInLine).replaceAll(".*\\s", "").trim();

        List<String> allSuggestions = Arrays.asList(LMC_INSTRUCTIONS);
        // Add labels from the interpreter's parsed labels (if interpreter exists and has parsed)
        if (interpreter != null && interpreter.getLabels() != null) {
            allSuggestions.addAll(interpreter.getLabels().keySet());
        }

        List<String> filteredSuggestions = allSuggestions.stream()
                .filter(s -> s.toUpperCase().startsWith(prefix.toUpperCase()))
                .sorted()
                .collect(Collectors.toList());

        if (!filteredSuggestions.isEmpty() && !prefix.isEmpty()) {
            suggestionsList.getItems().setAll(filteredSuggestions);
            autocompleteMenu.show(codeEditor, codeEditor.localToScreen(codeEditor.getCaretBounds().get().getMinX(), codeEditor.getCaretBounds().get().getMaxY()).getX(), codeEditor.localToScreen(codeEditor.getCaretBounds().get().getMinX(), codeEditor.getCaretBounds().get().getMaxY()).getY());
        } else {
            autocompleteMenu.hide();
        }
    }

    private void insertSelectedSuggestion() {
        String selected = suggestionsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String currentLine = codeEditor.getParagraph(codeEditor.getCurrentParagraph()).getText();
            int caretPosInLine = codeEditor.getCaretColumn();
            String prefix = currentLine.substring(0, caretPosInLine).replaceAll(".*\\s", "").trim();

            int start = codeEditor.getCaretPosition() - prefix.length();
            int end = codeEditor.getCaretPosition();
            codeEditor.replaceText(start, end, selected);
            autocompleteMenu.hide();
        }
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = SYNTAX_PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass = null;
            if (matcher.group("INSTRUCTION") != null) {
                styleClass = "lmc-instruction";
            } else if (matcher.group("LABEL") != null) {
                styleClass = "lmc-label";
            } else if (matcher.group("NUMBER") != null) {
                styleClass = "lmc-number";
            } else if (matcher.group("COMMENT") != null) {
                styleClass = "lmc-comment";
            }

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private void runCode() {
        outputArea.clear();
        statusBar.setText("Running...");
        String lmcCode = codeEditor.getText();

        // Attempt to compile LMC to assembly first
        Optional<File> compiledAssemblyFile = compileLmcToAssembly(lmcCode);

        if (compiledAssemblyFile.isPresent()) {
            outputArea.appendText("LMC code compiled to assembly successfully.\n");
            statusBar.setText("LMC code compiled to assembly successfully.");
            // Next step: assemble and link the .s file, then run the executable
            // For now, just indicate success and then fall back to interpreter
            outputArea.appendText("Falling back to Java interpreter for execution (native execution not yet implemented)...\n");
            interpreter = new LMCInterpreter(lmcCode, inputField, outputArea, statusBar, codeEditor);
            interpreter.runFull(); // Run until HLT or error
            stepButton.setDisable(true);
            resetButton.setDisable(false);
            updateDebugViews();
            updateAssemblyDisassemblyViews();
            // Clean up temp .s file
            try { Files.deleteIfExists(compiledAssemblyFile.get().toPath()); } catch (IOException e) { /* ignore */ }
        } else {
            // If compilation failed, directly use the Java interpreter
            outputArea.appendText("C Compiler failed. Executing with Java interpreter...\n");
            interpreter = new LMCInterpreter(lmcCode, inputField, outputArea, statusBar, codeEditor);
            interpreter.runFull(); // Run until HLT or error
            stepButton.setDisable(true);
            resetButton.setDisable(false);
            updateDebugViews();
            updateAssemblyDisassemblyViews();
        }
    }

    private Optional<File> compileLmcToAssembly(String lmcCode) {
        try {
            // 1. Create a temporary .lmc file
            Path tempLmcFile = Files.createTempFile("lmc_input_", ".lmc");
            Files.writeString(tempLmcFile, lmcCode, StandardOpenOption.WRITE);

            // 2. Define output .s file path
            Path tempSFile = Files.createTempFile("lmc_output_", ".s");

            // 3. Call the LMC C compiler
            // Assuming the compiler executable is in the LittleMachineCompiler directory
            String compilerPath = "/home/astra/projects/lmcProject/LittleMachineCompiler/compiler";
            ProcessBuilder processBuilder = new ProcessBuilder(compilerPath, tempLmcFile.toString(), tempSFile.toString());
            Process process = processBuilder.start();

            // Capture output and errors
            StringBuilder compilerOutput = new StringBuilder();
            StringBuilder compilerError = new StringBuilder();

            Consumer<String> outputConsumer = (line) -> compilerOutput.append(line).append("\n");
            Consumer<String> errorConsumer = (line) -> compilerError.append(line).append("\n");

            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), outputConsumer);
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), errorConsumer);

            Future<?> outputFuture = Executors.newSingleThreadExecutor().submit(outputGobbler);
            Future<?> errorFuture = Executors.newSingleThreadExecutor().submit(errorGobbler);

            int exitCode = process.waitFor();

            outputFuture.get(); // Wait for output gobbler to finish
            errorFuture.get();  // Wait for error gobbler to finish

            // Clean up temp .lmc file
            Files.deleteIfExists(tempLmcFile);

            if (exitCode == 0) {
                outputArea.appendText("LMC Compiler Output:\n" + compilerOutput.toString());
                statusBar.setText("LMC code compiled to assembly successfully.");
                return Optional.of(tempSFile.toFile());
            } else {
                outputArea.appendText("LMC Compiler Error (Exit Code: " + exitCode + "):\n" + compilerError.toString());
                statusBar.setText("LMC compilation failed.");
                // Clean up temp .s file on failure
                Files.deleteIfExists(tempSFile);
                return Optional.empty();
            }

        } catch (IOException | InterruptedException | java.util.concurrent.ExecutionException e) {
            outputArea.appendText("Error during LMC compilation: " + e.getMessage() + "\n");
            statusBar.setText("Error during LMC compilation.");
            return Optional.empty();
        }
    }

    private void stepCode() {
        if (interpreter == null || interpreter.isHalted()) {
            // If interpreter is null or halted, initialize for a new run
            outputArea.clear();
            statusBar.setText("Stepping...");
            String lmcCode = codeEditor.getText();
            interpreter = new LMCInterpreter(lmcCode, inputField, outputArea, statusBar, codeEditor);
            stepButton.setDisable(false);
            resetButton.setDisable(false);
        }
        
        if (!interpreter.isHalted()) {
            interpreter.step();
            updateDebugViews();
            updateAssemblyDisassemblyViews();
        } else {
            statusBar.setText("Program Halted.");
            stepButton.setDisable(true);
        }
    }

    private void resetInterpreter() {
        outputArea.clear();
        registerView.clear();
        memoryView.clear();
        assemblyView.clear();
        disassemblyView.clear();
        statusBar.setText("Ready");
        interpreter = null;
        stepButton.setDisable(false); // Enable step for new run
        runButton.setDisable(false); // Enable run for new run
        resetButton.setDisable(true);
        codeEditor.setStyleSpans(0, computeHighlighting(codeEditor.getText())); // Clear line highlight
    }

    private void updateDebugViews() {
        if (interpreter != null) {
            registerView.setText(
                    "PC: " + interpreter.getProgramCounter() + "\n" +
                    "ACC: " + interpreter.getAccumulator()
            );
            StringBuilder memBuilder = new StringBuilder();
            int[] mem = interpreter.getMemory();
            for (int i = 0; i < mem.length; i++) {
                memBuilder.append(String.format("%02d: %d   ", i, mem[i]));
                if ((i + 1) % 5 == 0) {
                    memBuilder.append("\n");
                }
            }
            memoryView.setText(memBuilder.toString());

            // Highlight current line
            codeEditor.setStyleSpans(0, computeHighlighting(codeEditor.getText())); // Clear previous highlight
            if (!interpreter.isHalted()) {
                int currentLine = interpreter.getProgramCounter();
                if (currentLine >= 0 && currentLine < codeEditor.getParagraphs().size()) {
                    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
                    // Get the paragraph object
                    org.fxmisc.richtext.model.Paragraph<Collection<String>, String, Collection<String>> paragraph = codeEditor.getParagraph(currentLine);
                    // Get the length of the paragraph
                    int paragraphLength = paragraph.length();
                    // Calculate the absolute start and end positions
                    int start = codeEditor.getAbsolutePosition(currentLine, 0);
                    int end = codeEditor.getAbsolutePosition(currentLine, paragraphLength);
                    spansBuilder.add(Collections.emptyList(), start);
                    spansBuilder.add(Collections.singleton("current-line"), end - start);
                    spansBuilder.add(Collections.emptyList(), codeEditor.getText().length() - end);
                    codeEditor.setStyleSpans(0, spansBuilder.create());
                }
            }
        }
    }

    private void updateAssemblyDisassemblyViews() {
        if (interpreter != null) {
            // Assembly View (LMC code with addresses)
            StringBuilder asmBuilder = new StringBuilder();
            String[] lines = codeEditor.getText().split("\\n");
            for (int i = 0; i < lines.length; i++) {
                asmBuilder.append(String.format("%02d: %s\\n", i, lines[i].trim()));
            }
            assemblyView.setText(asmBuilder.toString());

            // Disassembly View (Memory content as LMC instructions)
            StringBuilder disasmBuilder = new StringBuilder();
            int[] mem = interpreter.getMemory();
            for (int i = 0; i < mem.length; i++) {
                String instruction = interpreter.getInstructionAt(i);
                disasmBuilder.append(String.format("%02d: %s\\n", i, instruction));
            }
            disassemblyView.setText(disasmBuilder.toString());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

class LMCInterpreter {
    private int[] memory;
    private int programCounter;
    private int accumulator;
    private Map<String, Integer> labels;
    private TextField inputField;
    private TextArea outputArea;
    private Label statusBar;
    private CodeArea codeEditor;
    private String[] programLines;
    private Scanner inputScanner;
    private boolean halted;

    // LMC instruction opcodes (simplified for interpreter)
    private static final Map<String, Integer> OPCODE_MAP = new HashMap<>();
    static {
        OPCODE_MAP.put("ADD", 100);
        OPCODE_MAP.put("SUB", 200);
        OPCODE_MAP.put("STA", 300);
        OPCODE_MAP.put("LDA", 500);
        OPCODE_MAP.put("BRA", 600);
        OPCODE_MAP.put("BRZ", 700);
        OPCODE_MAP.put("BRP", 800);
        OPCODE_MAP.put("INP", 901);
        OPCODE_MAP.put("OUT", 902);
        OPCODE_MAP.put("HLT", 0);
        OPCODE_MAP.put("DAT", 0); // DAT is a directive, not an instruction, but for simplicity in memory view
    }

    public LMCInterpreter(String lmcCode, TextField inputField, TextArea outputArea, Label statusBar, CodeArea codeEditor) {
        this.memory = new int[100]; // LMC has 100 memory locations (00-99)
        this.programCounter = 0;
        this.accumulator = 0;
        this.labels = new HashMap<>();
        this.inputField = inputField;
        this.outputArea = outputArea;
        this.statusBar = statusBar;
        this.codeEditor = codeEditor;
        this.programLines = lmcCode.split("\\n");
        this.inputScanner = new Scanner(inputField.getText());
        this.halted = false;

        parseLabelsAndData();
    }

    private void parseLabelsAndData() {
        for (int i = 0; i < programLines.length; i++) {
            String line = programLines[i].trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            String[] parts = line.split("\\s+");
            if (parts.length == 0) continue;

            // Check for label
            String potentialLabel = parts[0];
            if (!isInstruction(potentialLabel)) {
                labels.put(potentialLabel, i); // Store line number for label
                // Shift parts to remove label for instruction parsing
                String[] newParts = new String[parts.length - 1];
                System.arraycopy(parts, 1, newParts, 0, parts.length - 1);
                parts = newParts;
            }

            // Handle DAT instruction and store in memory
            if (parts.length >= 1 && parts[0].equalsIgnoreCase("DAT")) {
                if (parts.length >= 2) {
                    try {
                        int value = Integer.parseInt(parts[1]);
                        memory[i] = value;
                    } catch (NumberFormatException e) {
                        outputArea.appendText("Error: Invalid data value on line " + (i + 1) + "\n");
                        statusBar.setText("Error: Invalid data value on line " + (i + 1));
                        halted = true;
                    }
                } else {
                    // DAT without an explicit value defaults to 0
                    memory[i] = 0;
                }
            } else if (parts.length >= 1 && isInstruction(parts[0])) {
                // Store instruction opcode in memory
                String instruction = parts[0].toUpperCase();
                int opcode = OPCODE_MAP.getOrDefault(instruction, 0); // Default to 0 for HLT/DAT
                int operand = 0;

                if (parts.length > 1) {
                    try {
                        operand = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        // If operand is a label, its address will be resolved during execution
                        // For now, store 0, and resolve during execution
                        operand = 0;
                    }
                }
                memory[i] = opcode + operand;
            }
        }
    }

    private boolean isInstruction(String s) {
        return Arrays.asList("INP", "OUT", "LDA", "STA", "ADD", "SUB", "BRA", "BRZ", "BRP", "HLT", "DAT").contains(s.toUpperCase());
    }

    public void runFull() {
        while (programCounter < programLines.length && !halted) {
            step();
        }
    }

    public void step() {
        if (halted) return;

        if (programCounter >= programLines.length) {
            outputArea.appendText("Error: Program Counter out of bounds.\n");
            statusBar.setText("Error: Program Counter out of bounds.");
            halted = true;
            return;
        }

        String line = programLines[programCounter].trim();
        if (line.isEmpty() || line.startsWith("//")) {
            programCounter++;
            return;
        }

        String[] parts = line.split("\\s+");
        if (parts.length == 0) {
            programCounter++;
            return;
        }

        // Skip label if present (already handled in parseLabelsAndData)
        String potentialInstruction = parts[0];
        if (!isInstruction(potentialInstruction) && labels.containsKey(potentialInstruction) && parts.length > 1) {
            String[] newParts = new String[parts.length - 1];
            System.arraycopy(parts, 1, newParts, 0, parts.length - 1);
            parts = newParts;
        } else if (!isInstruction(potentialInstruction) && labels.containsKey(potentialInstruction) && parts.length == 1) {
            programCounter++;
            return;
        }

        String instruction = parts[0].toUpperCase();
        int operand = 0; // Default for instructions without operand
        if (parts.length > 1) {
            try {
                operand = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // If not a number, it must be a label
                if (labels.containsKey(parts[1])) {
                    operand = labels.get(parts[1]);
                } else {
                    outputArea.appendText("Error: Undefined label or invalid operand on line " + (programCounter + 1) + "\n");
                    statusBar.setText("Error: Undefined label or invalid operand on line " + (programCounter + 1));
                    halted = true;
                    return;
                }
            }
        }

        try {
            switch (instruction) {
                case "INP":
                    if (inputScanner.hasNextInt()) {
                        accumulator = inputScanner.nextInt();
                        outputArea.appendText("Input: " + accumulator + "\n");
                    } else {
                        outputArea.appendText("Error: No integer input available. Program halted.\n");
                        statusBar.setText("Error: No integer input available.");
                        halted = true;
                        return;
                    }
                    break;
                case "OUT":
                    outputArea.appendText("Output: " + accumulator + "\n");
                    break;
                case "LDA":
                    accumulator = memory[operand];
                    break;
                case "STA":
                    memory[operand] = accumulator;
                    break;
                case "ADD":
                    accumulator += memory[operand];
                    break;
                case "SUB":
                    accumulator -= memory[operand];
                    break;
                case "BRA":
                    programCounter = operand - 1; // -1 because programCounter increments after switch
                    break;
                case "BRZ":
                    if (accumulator == 0) {
                        programCounter = operand - 1;
                    }
                    break;
                case "BRP":
                    if (accumulator >= 0) {
                        programCounter = operand - 1;
                    }
                    break;
                case "HLT":
                    outputArea.appendText("Program Halted.\n");
                    statusBar.setText("Program Halted.");
                    halted = true;
                    return;
                case "DAT":
                    // Handled during parsing phase
                    break;
                default:
                    outputArea.appendText("Error: Unknown instruction " + instruction + " on line " + (programCounter + 1) + "\n");
                    statusBar.setText("Error: Unknown instruction " + instruction + " on line " + (programCounter + 1));
                    halted = true;
                    return;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            outputArea.appendText("Error: Memory access out of bounds at address " + operand + " on line " + (programCounter + 1) + ".\n");
            statusBar.setText("Error: Memory access out of bounds.");
            halted = true;
            return;
        }

        programCounter++;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public int getAccumulator() {
        return accumulator;
    }

    public int[] getMemory() {
        return Arrays.copyOf(memory, memory.length);
    }

    public Map<String, Integer> getLabels() {
        return labels;
    }

    public boolean isHalted() {
        return halted;
    }

    // Method to get LMC instruction from memory value
    public String getInstructionAt(int address) {
        if (address < 0 || address >= memory.length) {
            return "Invalid Address";
        }
        int value = memory[address];
        int opcode = value / 100 * 100; // Get the hundreds digit as opcode
        int operand = value % 100;     // Get the last two digits as operand

        for (Map.Entry<String, Integer> entry : OPCODE_MAP.entrySet()) {
            if (entry.getValue().equals(opcode)) {
                String instruction = entry.getKey();
                if (instruction.equals("HLT") || instruction.equals("INP") || instruction.equals("OUT")) {
                    return instruction;
                } else if (instruction.equals("DAT")) {
                    return "DAT " + value; // For DAT, show the full value
                } else {
                    // Try to find a label for the operand
                    for (Map.Entry<String, Integer> labelEntry : labels.entrySet()) {
                        if (labelEntry.getValue().equals(operand)) {
                            return instruction + " " + labelEntry.getKey();
                        }
                    }
                    return instruction + " " + operand;
                }
            }
        }
        return "??? " + value; // Unknown instruction
    }
}

class StreamGobbler implements Runnable {
    private InputStream inputStream;
    private Consumer<String> consumer;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
        this.inputStream = inputStream;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(inputStream)).lines()
                .forEach(consumer);
    }
}