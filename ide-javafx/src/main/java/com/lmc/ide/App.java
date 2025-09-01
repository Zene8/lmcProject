package com.lmc.ide;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
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
    private AIModelManager aiModelManager;
    private Preferences prefs;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("LMC IDE");
        prefs = Preferences.userNodeForPackage(App.class);

        // --- Initialize Core Components ---
        interpreter = new LMCInterpreter();
        parser = new LMCParser();
        aiModelManager = new AIModelManager();

        // --- Initialize Controllers & Managers ---
        uiController = new UIController(primaryStage);
        fileManager = new FileManager(primaryStage, uiController);
        lmcExecutor = new LMCExecutor(interpreter, parser, uiController);

        // --- Link Core Controllers for UI Initialization ---
        uiController.setFileManager(fileManager);
        uiController.setLmcExecutor(lmcExecutor);
        uiController.setAiModelManager(aiModelManager);
        uiController.setPrefs(prefs);
        // --- Finalize UI construction now that its core dependencies are set ---
        uiController.initComponents();

        lmcExecutor.setConsole(uiController.getConsoleOutputArea()); // Set console output area

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
