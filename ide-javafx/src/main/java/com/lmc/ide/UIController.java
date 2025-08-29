package com.lmc.ide;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.util.Arrays;

public class UIController {

    private Stage primaryStage;
    private BorderPane root;
    private SplitPane mainSplitPane;
    private TabPane editorTabPane;
    private VBox toolsSidebar;
    private TabPane toolsTabPane;
    private BorderPane memoryView;
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
        mainSplitPane = new SplitPane();
        editorTabPane = new TabPane();
        toolsSidebar = createToolsSidebar();
        memoryView = createMemoryView();
        fileExplorer = new TreeView<>();

        mainSplitPane.getItems().addAll(editorTabPane, toolsSidebar);
        mainSplitPane.setDividerPositions(0.75);

        root.setCenter(mainSplitPane);
        root.setBottom(memoryView);

        createFindPopup();
        createReplacePopup();
    }

    private VBox createToolsSidebar() {
        toolsSidebar = new VBox();
        toolsSidebar.getStyleClass().add("tools-container");
        toolsTabPane = new TabPane();

        Tab fileExplorerTab = new Tab("File Explorer");
        fileExplorerTab.setClosable(false);
        fileExplorerTab.setContent(fileExplorer);

        Tab memoryManagementTab = new Tab("Memory");
        memoryManagementTab.setClosable(false);
        // Add memory management components here

        Tab learningToolsTab = new Tab("Learn");
        learningToolsTab.setClosable(false);
        // Add learning tools components here

        toolsTabPane.getTabs().addAll(fileExplorerTab, memoryManagementTab, learningToolsTab);
        toolsSidebar.getChildren().add(toolsTabPane);
        return toolsSidebar;
    }

    private BorderPane createMemoryView() {
        memoryView = new BorderPane();
        memoryView.getStyleClass().add("editor-container");
        memoryUsageLabel = new Label("Memory Usage: 0/100");
        memoryView.setCenter(memoryUsageLabel);
        return memoryView;
    }

    private void createFindPopup() {
        findPopup = new VBox();
        findPopup.setPadding(new Insets(10));
        findPopup.setSpacing(5);
        findField = new TextField();
        Button findNextButton = new Button("Find Next");
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
        Button replaceNextButton = new Button("Replace");
        replaceNextButton.setOnAction(e -> fileManager.replaceNext());
        Button replaceAllButton = new Button("Replace All");
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
        if (mainSplitPane.getItems().contains(toolsSidebar)) {
            mainSplitPane.getItems().remove(toolsSidebar);
        } else {
            mainSplitPane.getItems().add(toolsSidebar);
            mainSplitPane.setDividerPositions(0.75);
        }
    }

    public void toggleMemoryView() {
        if (root.getBottom() == memoryView) {
            root.setBottom(null);
        } else {
            root.setBottom(memoryView);
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

    public void newEditorTab(String name, CodeArea codeArea) {
        Tab tab = new Tab(name);
        BorderPane editorPane = new BorderPane(codeArea);
        tab.setContent(editorPane);
        editorTabPane.getTabs().add(tab);
        editorTabPane.getSelectionModel().select(tab);
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
        return new ImageView(new Image(getClass().getResourceAsStream("/icons/folder.svg")));
    }

    public ImageView createFileIcon() {
        return new ImageView(new Image(getClass().getResourceAsStream("/icons/file.svg")));
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
}