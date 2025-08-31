package com.lmc.ide;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LMCExecutor {
    private final LMCInterpreter interpreter;
    private final LMCParser parser;
    private final UIController uiController;
    private LMCIDEFeatures ideFeatures;
    private TextArea console;
    private ListView<String> errorListView;
    private TabPane bottomTabPane;
    private Button startButton, stopButton, stepButton, resetButton;
    private ToggleButton speedModeToggle;
    private Slider speedSlider;
    private Timeline executionTimeline;
    private VBox[] memoryCellBoxes = new VBox[100];
    private LMCParser.AssembledCode currentAssembledCode;
    private int lastHighlightedLine = -1; // Added to track the last highlighted line

    public LMCExecutor(LMCInterpreter interpreter, LMCParser parser, UIController uiController) {
        this.interpreter = interpreter;
        this.parser = parser;
        this.uiController = uiController;
    }

    public void setIdeFeatures(LMCIDEFeatures features) {
        this.ideFeatures = features;
    }

    public void setConsole(TextArea console) {
        this.console = console;
    }

    public void setErrorListView(ListView<String> listView, TabPane tabPane) {
        this.errorListView = listView;
        this.bottomTabPane = tabPane;
    }

    public void setControls(Button start, Button stop, Button step, Button reset) {
        this.startButton = start;
        this.stopButton = stop;
        this.stepButton = step;
        this.resetButton = reset;
        stopButton.setDisable(true);
        stepButton.setDisable(true);
        resetButton.setDisable(true);
    }

    public void setSpeedControls(ToggleButton toggle, Slider slider) {
        this.speedModeToggle = toggle;
        this.speedSlider = slider;
    }

    public VBox[] getMemoryCellBoxes() {
        return memoryCellBoxes;
    }

    public void onCodeChange(String newText) {
        try {
            LMCParser.AssembledCode assembled = parser.parse(newText);
            updateMemoryVisualizer(assembled.memoryMap, -1, assembled);
        } catch (LMCParser.LMCParseException e) {
            updateMemoryVisualizer(new int[100], -1, null);
        }
    }

    public void runLMC() {
        CodeArea currentCodeArea = uiController.getCurrentCodeArea();
        if (currentCodeArea == null) {
            new Alert(Alert.AlertType.WARNING, "No file is open to run.").showAndWait();
            return;
        }

        startButton.setDisable(true);
        stopButton.setDisable(false);
        stepButton.setDisable(true);
        speedModeToggle.setDisable(true);
        if (speedSlider != null)
            speedSlider.setDisable(true);
        console.clear();
        errorListView.getItems().clear();
        uiController.setStatusBarMessage("Running...");
        String lmcCode = currentCodeArea.getText();

        try {
            currentAssembledCode = parser.parse(lmcCode);
            interpreter.load(currentAssembledCode, createInputProvider());
            interpreter.setBreakpoints(ideFeatures.getBreakpoints());
            updateMemoryVisualizer(interpreter.getMemory(), -1, interpreter.getAssembledCode());
        } catch (LMCParser.LMCParseException e) {
            finishExecution(LMCInterpreter.ExecutionState.ERROR, e.getMessage());
            return;
        }

        if (speedModeToggle.isSelected()) {
            executionTimeline = new Timeline(new KeyFrame(Duration.millis(speedSlider.getValue()), e -> executeStep()));
            executionTimeline.setCycleCount(Timeline.INDEFINITE);
            interpreter.start();
            executionTimeline.play();
        } else {
            CompletableFuture.runAsync(() -> {
                interpreter.start();
                LMCInterpreter.ExecutionState state;
                do {
                    state = interpreter.step();
                } while (state == LMCInterpreter.ExecutionState.RUNNING);

                final LMCInterpreter.ExecutionState finalState = state;
                Platform.runLater(() -> finishExecution(finalState, interpreter.getErrorMessage()));
            });
        }
    }

    public void stopLMC() {
        if (executionTimeline != null)
            executionTimeline.stop();
        if (interpreter != null)
            interpreter.stop();
        stopButton.setDisable(true);
        startButton.setDisable(false);
        speedModeToggle.setDisable(false);
        if (speedSlider != null)
            speedSlider.setDisable(!speedModeToggle.isSelected());
        stepButton.setDisable(true);
        finishExecution(LMCInterpreter.ExecutionState.STOPPED, "Stopped by user."); // Call finishExecution to clean up
    }

    public void executeStep() {
        LMCInterpreter.ExecutionState state = interpreter.step();
        if (state == LMCInterpreter.ExecutionState.AWAITING_INPUT
                || state == LMCInterpreter.ExecutionState.BREAKPOINT_HIT) {
            if (executionTimeline != null)
                executionTimeline.pause();
            if (state == LMCInterpreter.ExecutionState.BREAKPOINT_HIT) {
                finishExecution(state, "Breakpoint hit at address " + interpreter.getProgramCounter());
            } else { // Awaiting input
                 finishExecution(state, "Program paused, awaiting input.");
            }
            stepButton.setDisable(false);
            return;
        }

        if (state != LMCInterpreter.ExecutionState.RUNNING) {
            finishExecution(state, interpreter.getErrorMessage());
        } else {
            updateMemoryVisualizer(interpreter.getMemory(), interpreter.getLastAccessedAddress(),
                    interpreter.getAssembledCode());
            // Highlight current line in slow mode
            if (speedModeToggle.isSelected()) {
                int currentPC = interpreter.getProgramCounter();
                if (currentAssembledCode != null && currentAssembledCode.addressToLineMap.containsKey(currentPC)) {
                    int currentLine = currentAssembledCode.addressToLineMap.get(currentPC);
                    uiController.clearLineHighlight(lastHighlightedLine);
                    uiController.highlightLine(currentLine);
                    lastHighlightedLine = currentLine;
                }
            }
        }
    }

    private void finishExecution(LMCInterpreter.ExecutionState finalState, String message) {
        if (executionTimeline != null)
            executionTimeline.stop();
        stopButton.setDisable(true);
        startButton.setDisable(false);
        speedModeToggle.setDisable(false);
        if (speedSlider != null)
            speedSlider.setDisable(!speedModeToggle.isSelected());
        stepButton.setDisable(true);
        uiController.clearLineHighlight(lastHighlightedLine);
        lastHighlightedLine = -1;

        switch (finalState) {
            case HALTED:
                console.appendText("\n--- Program Halted ---\n" + interpreter.getOutput());
                uiController.setStatusBarMessage("Program Halted.");
                break;
            case STOPPED:
                console.appendText("\n--- Program Stopped by User ---");
                uiController.setStatusBarMessage("Program Stopped.");
                break;
            case ERROR:
                console.appendText("\n--- Runtime Error ---" + message);
                errorListView.getItems().add("Runtime Error: " + message);
                bottomTabPane.getSelectionModel().select(1); // Assuming error tab is the second tab
                uiController.setStatusBarMessage("Runtime Error.");
                break;
            case AWAITING_INPUT:
            case BREAKPOINT_HIT:
                console.appendText("\n--- Program Paused ---\n" + message);
                stepButton.setDisable(false);
                resetButton.setDisable(false);
                // Status message already set in executeStep()
                break;
            default:
                uiController.setStatusBarMessage("Ready.");
                break;
        }
    }

    public void resetProgram() {
        interpreter.resetProgram();
        updateMemoryVisualizer(interpreter.getMemory(), -1, interpreter.getAssembledCode());
        console.clear();
        errorListView.getItems().clear();
        uiController.setStatusBarMessage("Ready.");
        startButton.setDisable(false);
        stopButton.setDisable(true);
        stepButton.setDisable(true);
        resetButton.setDisable(true);
    }

    private InputProvider createInputProvider() {
        return () -> {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                console.appendText("\nEnter input: ");
                console.setEditable(true);
                console.requestFocus();
                console.end();
                ideFeatures.setPendingInputRequest(future);
            });
            return future;
        };
    }

    public GridPane createMemoryVisualizer() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("memory-grid");
        for (int i = 0; i < 100; i++) {
            Label valueLabel = new Label("000");
            valueLabel.getStyleClass().add("memory-digit");
            Label instructionLabel = new Label("DAT");
            instructionLabel.getStyleClass().add("memory-instruction-text");
            VBox cellBox = new VBox(valueLabel, instructionLabel);
            cellBox.getStyleClass().add("memory-cell");
            cellBox.setAlignment(Pos.CENTER);
            cellBox.setSpacing(2);
            memoryCellBoxes[i] = cellBox;
            grid.add(cellBox, i % 10, i / 10);
        }
        return grid;
    }

    public void updateMemoryVisualizer(int[] memory, int highlightedAddress, LMCParser.AssembledCode assembledCode) {
        Platform.runLater(() -> {
            int memoryUsed = 0;
            for (int i = 0; i < 100; i++) {
                VBox cellBox = memoryCellBoxes[i];
                ((Label) cellBox.getChildren().get(0)).setText(String.format("%03d", memory[i]));
                if (memory[i] != 0)
                    memoryUsed++;

                cellBox.getStyleClass().remove("memory-cell-instruction");
                String instructionText = "DAT";
                if (assembledCode != null) {
                    if (assembledCode.addressToLabelMap.containsKey(i)) {
                        instructionText = assembledCode.addressToLabelMap.get(i);
                        if (assembledCode.instructions.containsKey(i)) {
                            instructionText += " (" + assembledCode.instructions.get(i) + ")";
                        }
                    } else if (assembledCode.instructions.containsKey(i)) {
                        instructionText = assembledCode.instructions.get(i);
                    }
                }
                ((Label) cellBox.getChildren().get(1)).setText(instructionText);
                if (assembledCode != null && assembledCode.instructions.containsKey(i)) {
                    cellBox.getStyleClass().add("memory-cell-instruction");
                }
            }
            if (uiController.getMemoryUsageLabel() != null) {
                uiController.getMemoryUsageLabel().setText("Memory Used: " + memoryUsed + " / 100");
            }
        });
    }

    public void updateMemoryVisualizer(Map<Integer, Integer> memoryMap, int highlightedAddress,
                                       LMCParser.AssembledCode assembledCode) {
        int[] memArray = new int[100];
        memoryMap.forEach((addr, val) -> {
            if (addr >= 0 && addr < 100)
                memArray[addr] = val;
        });
        updateMemoryVisualizer(memArray, highlightedAddress, assembledCode);
    }
}