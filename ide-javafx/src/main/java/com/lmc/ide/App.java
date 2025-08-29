package com.lmc.ide;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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
    private Preferences prefs;

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

        setupHotkeys(scene);
        uiController.applyTheme(prefs.get("theme", "dark-mode"));
        fileManager.newFile();
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File Menu
        Menu fileMenu = new Menu("File");
        MenuItem newFile = new MenuItem("New");
        newFile.setOnAction(e -> fileManager.newFile());
        MenuItem openProject = new MenuItem("Open");
        openProject.setOnAction(e -> fileManager.openProject());
        MenuItem saveFile = new MenuItem("Save");
        saveFile.setOnAction(e -> fileManager.saveFile());
        MenuItem closeTab = new MenuItem("Close Tab");
        closeTab.setOnAction(e -> fileManager.closeCurrentTab());
        fileMenu.getItems().addAll(newFile, openProject, saveFile, closeTab);

        // Edit Menu
        Menu editMenu = new Menu("Edit");
        MenuItem undo = new MenuItem("Undo");
        undo.setOnAction(e -> {
            if (getCurrentCodeArea() != null) getCurrentCodeArea().undo();
        });
        MenuItem redo = new MenuItem("Redo");
        redo.setOnAction(e -> {
            if (getCurrentCodeArea() != null) getCurrentCodeArea().redo();
        });
        MenuItem find = new MenuItem("Find");
        find.setOnAction(e -> uiController.toggleFindPopup());
        MenuItem replace = new MenuItem("Replace");
        replace.setOnAction(e -> uiController.toggleReplacePopup());
        editMenu.getItems().addAll(undo, redo, find, replace);

        // View Menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem toggleTools = new CheckMenuItem("Toggle Tools Sidebar");
        toggleTools.setSelected(true);
        toggleTools.setOnAction(e -> uiController.toggleToolsSidebar());
        CheckMenuItem toggleMemory = new CheckMenuItem("Toggle Memory View");
        toggleMemory.setSelected(true);
        toggleMemory.setOnAction(e -> uiController.toggleMemoryView());
        MenuItem toggleTheme = new MenuItem("Toggle Theme");
        toggleTheme.setOnAction(e -> toggleTheme());
        viewMenu.getItems().addAll(toggleTools, toggleMemory, toggleTheme);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu);
        return menuBar;
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
