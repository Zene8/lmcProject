package com.lmc.ide;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.net.URL;
import java.util.prefs.Preferences;

public class App extends Application {

    private UIController uiController;
    private FileManager fileManager;
    private LMCExecutor lmcExecutor;
    private LMCInterpreter interpreter;
    private LMCParser parser;
    private LMCIDEFeatures ideFeatures;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("LMC IDE");

        // --- Initialize Core Components ---
        interpreter = new LMCInterpreter();
        parser = new LMCParser();

        // --- Initialize Controllers & Managers ---
        uiController = new UIController(primaryStage);
        fileManager = new FileManager(primaryStage, uiController);
        lmcExecutor = new LMCExecutor(interpreter, parser, uiController);

        // --- Link Core Controllers for UI Initialization ---
        // The UIController needs these dependencies before its components are built
        uiController.setFileManager(fileManager);
        uiController.setLmcExecutor(lmcExecutor);

        // --- Finalize UI construction now that its core dependencies are set ---
        // This MUST be called before creating LMCIDEFeatures
        uiController.initComponents();

        // --- Setup IDE Features now that UI is fully initialized ---
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        ideFeatures = new LMCIDEFeatures(this, parser, uiController.getMemoryUsageLabel(),
                prefs.getBoolean("autocorrectEnabled", true),
                prefs.getBoolean("autoFormattingEnabled", true),
                prefs.getBoolean("errorHighlightingEnabled", true));

        // --- Link remaining dependencies ---
        uiController.setIdeFeatures(ideFeatures);
        lmcExecutor.setIdeFeatures(ideFeatures);

        // --- Build Scene ---
        Scene scene = new Scene(uiController.getRoot(), 1600, 1000);
        loadStylesheet(scene, "/vscode-style.css");
        loadStylesheet(scene, "/lmc-syntax.css");

        primaryStage.setScene(scene);
        primaryStage.show();

        setupHotkeys(scene);
        uiController.applyTheme(prefs.get("theme", "dark-mode"));
        fileManager.newFile();
    }

    private void loadStylesheet(Scene scene, String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            System.err.println("Error: Stylesheet not found at " + path);
        } else {
            scene.getStylesheets().add(resource.toExternalForm());
        }
    }

    private void setupHotkeys(Scene scene) {
        // File
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                fileManager::newFile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                fileManager::openProject);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                fileManager::saveFile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
                fileManager::closeCurrentTab);

        // Edit
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN), () -> {
            if (getCurrentCodeArea() != null)
                getCurrentCodeArea().undo();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN), () -> {
            if (getCurrentCodeArea() != null)
                getCurrentCodeArea().redo();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                uiController::toggleFindPopup);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN),
                uiController::toggleReplacePopup);

        // Run
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), lmcExecutor::runLMC);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6), lmcExecutor::stopLMC);

        // UI
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), uiController::hidePopups);
    }

    public CodeArea getCurrentCodeArea() {
        return uiController.getCurrentCodeArea();
    }

    public UIController getUiController() {
        return uiController;
    }

    public static void main(String[] args) {
        launch(args);
    }
}