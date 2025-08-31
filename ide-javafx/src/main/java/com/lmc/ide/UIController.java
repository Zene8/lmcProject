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
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class UIController {

    private Stage primaryStage;
    private BorderPane root;
    private SplitPane mainSplitPane, verticalSplitPane;
    private TabPane editorTabPane;
    private VBox leftSidebar, rightSidebar;
    private BorderPane console;
    private TreeView<File> fileExplorer;

    private FileManager fileManager;
    private LMCExecutor lmcExecutor;
    private LMCIDEFeatures ideFeatures;

    private Label memoryUsageLabel;

    private VBox findPopup;
    private TextField findField;
    private VBox replacePopup;
    private TextField replaceFindField;
    private TextField replaceWithField;

    private Label statusBar;

    public UIController(Stage primaryStage) {
        this.primaryStage = primaryStage;
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

    public void initComponents() {
        root = new BorderPane();
        editorTabPane = new TabPane();
        fileExplorer = new TreeView<>();

        // Main content area with resizable sidebars
        mainSplitPane = new SplitPane();
        mainSplitPane.setDividerPositions(0.2, 0.8);

        // Vertical split for editor and console
        verticalSplitPane = new SplitPane();
        verticalSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        verticalSplitPane.setDividerPositions(0.75);

        // Construct the layout
        leftSidebar = createLeftSidebar();
        rightSidebar = createRightSidebar();
        console = createConsole();

        mainSplitPane.getItems().addAll(leftSidebar, verticalSplitPane, rightSidebar);
        verticalSplitPane.getItems().addAll(editorTabPane, console);

        root.setCenter(mainSplitPane);

        createFindPopup();
        createReplacePopup();
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
        TabPane toolsTabPane = new TabPane();

        Tab toolsTab = new Tab("Tools");
        toolsTab.setClosable(false);
        // Add tools components here

        Tab learnTab = new Tab("Learn");
        learnTab.setClosable(false);
        // Add learning components here

        toolsTabPane.getTabs().addAll(toolsTab, learnTab);
        sidebar.getChildren().add(toolsTabPane);
        return sidebar;
    }

    private BorderPane createConsole() {
        BorderPane consolePane = new BorderPane();
        consolePane.getStyleClass().add("console");
        memoryUsageLabel = new Label("Memory Usage: 0/100");
        TitledPane consoleTitlePane = new TitledPane("Console", consolePane);
        consoleTitlePane.setCollapsible(true);
        consoleTitlePane.setExpanded(true);
        consolePane.setCenter(memoryUsageLabel);
        return consolePane;
    }

    private void createFindPopup() {
        findPopup = new VBox();
        findPopup.setPadding(new Insets(10));
        findPopup.setSpacing(5);
        findField = new TextField();
        Button findNextButton = new Button("", createIcon("search.svg"));
        findNextButton.setTooltip(new Tooltip("Find Next"));
        findNextButton.setOnAction(e -> fileManager.findNext(findField.getText()));
        findPopup.getChildren().addAll(new Label("Find:"), findField, findNextButton);
        findPopup.setVisible(false);
        root.getChildren().add(findPopup);
    }

    private void createReplacePopup() {
        replacePopup = new VBox();
        replacePopup.setPadding(new Insets(10));
        replacePopup.setSpacing(5);
        replaceFindField = new TextField();
        replaceWithField = new TextField();
        Button replaceNextButton = new Button("", createIcon("find_replace.svg"));
        replaceNextButton.setTooltip(new Tooltip("Replace"));
        replaceNextButton.setOnAction(e -> fileManager.replaceNext());
        Button replaceAllButton = new Button("", createIcon("find_replace.svg"));
        replaceAllButton.setTooltip(new Tooltip("Replace All"));
        replaceAllButton.setOnAction(e -> fileManager.replaceAll());
        replacePopup.getChildren().addAll(new Label("Find:"), replaceFindField, new Label("Replace with:"), replaceWithField, new HBox(5, replaceNextButton, replaceAllButton));
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
            mainSplitPane.setDividerPositions(0.2, 0.8);
        }
    }

    public void toggleMemoryView() {
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

            if (ideFeatures != null && ideFeatures.getLineErrors().containsKey(lineNumber)) {
                Circle errorIcon = new Circle(4, Color.RED);
                errorIcon.getStyleClass().add("error-icon");
                Tooltip tooltip = new Tooltip(ideFeatures.getLineErrors().get(lineNumber));
                Tooltip.install(errorIcon, tooltip);
                hbox.getChildren().add(errorIcon);
            }
            return hbox;
        });
    }

    public void setStatusBarMessage(String message) {
        statusBar.setText(message);
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
        Image image = new Image(getClass().getResourceAsStream("/icons/" + iconName));
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        return imageView;
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
            codeArea.setParagraphStyle(lineNumber, Collections.singleton("current-line"));
        }
    }

    public void clearLineHighlight(int lineNumber) {
        CodeArea codeArea = getCurrentCodeArea();
        if (codeArea != null && lineNumber >= 0 && lineNumber < codeArea.getParagraphs().size()) {
            codeArea.setParagraphStyle(lineNumber, Collections.emptyList());
        }
    }
}