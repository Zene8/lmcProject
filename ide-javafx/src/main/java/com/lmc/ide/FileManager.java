package com.lmc.ide;

import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TreeItem;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.regex.Pattern;

public class FileManager {
    private final Stage primaryStage;
    private final UIController uiController;
    private File currentProjectDirectory;

    public FileManager(Stage primaryStage, UIController uiController) {
        this.primaryStage = primaryStage;
        this.uiController = uiController;
    }

    public void newFile() {
        int newFileCount = 1;
        String tabName;
        boolean nameExists;
        do {
            nameExists = false;
            tabName = "Untitled-" + newFileCount;
            for (Tab t : uiController.getCodeTabPane().getTabs()) {
                if (t.getText().equals(tabName)) {
                    newFileCount++;
                    nameExists = true;
                    break;
                }
            }
        } while (nameExists);

        Tab tab = new Tab(tabName);
        tab.setContent(uiController.createCodeArea());
        uiController.getCodeTabPane().getTabs().add(tab);
        uiController.getCodeTabPane().getSelectionModel().select(tab);
        updateTitle();
    }

    public void openFile(File file) {
        for (Tab tab : uiController.getCodeTabPane().getTabs()) {
            if (file.equals(tab.getUserData())) {
                uiController.getCodeTabPane().getSelectionModel().select(tab);
                return;
            }
        }
        try {
            String content = Files.readString(file.toPath());
            Tab tab = new Tab(file.getName());
            tab.setUserData(file);
            CodeArea codeArea = uiController.createCodeArea();
            codeArea.replaceText(content);
            tab.setContent(codeArea);
            uiController.getCodeTabPane().getTabs().add(tab);
            uiController.getCodeTabPane().getSelectionModel().select(tab);
            updateTitle();
        } catch (IOException e) {
            showAlert("Error", "Could not read file: " + e.getMessage());
        }
    }

    public void saveFile() {
        Tab selectedTab = uiController.getCodeTabPane().getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            File file = (File) selectedTab.getUserData();
            CodeArea codeArea = uiController.getCurrentCodeArea();
            if (file != null && codeArea != null) {
                try {
                    Files.writeString(file.toPath(), codeArea.getText(), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    showAlert("Error", "Could not save file: " + e.getMessage());
                }
            } else {
                saveFileAs();
            }
        }
    }

    public void saveFileAs() {
        Tab selectedTab = uiController.getCodeTabPane().getSelectionModel().getSelectedItem();
        CodeArea codeArea = uiController.getCurrentCodeArea();
        if (selectedTab != null && codeArea != null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save File As");
            if (currentProjectDirectory != null)
                fileChooser.setInitialDirectory(currentProjectDirectory);
            File file = fileChooser.showSaveDialog(primaryStage);
            if (file != null) {
                try {
                    Files.writeString(file.toPath(), codeArea.getText(), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    selectedTab.setText(file.getName());
                    selectedTab.setUserData(file);
                    updateTitle();
                } catch (IOException e) {
                    showAlert("Error", "Could not save file: " + e.getMessage());
                }
            }
        }
    }

    public void openProject() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open LMC Project");
        File initialDir = (currentProjectDirectory != null) ? currentProjectDirectory
                : new File(System.getProperty("user.home"));
        directoryChooser.setInitialDirectory(initialDir);
        File dir = directoryChooser.showDialog(primaryStage);
        if (dir != null) {
            currentProjectDirectory = dir;
            refreshFileExplorer();
            updateTitle();
        }
    }

    private void refreshFileExplorer() {
        if (currentProjectDirectory != null) {
            TreeItem<File> rootItem = new TreeItem<>(currentProjectDirectory, uiController.createFolderIcon());
            rootItem.setExpanded(true);
            uiController.getFileExplorer().setRoot(rootItem);
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
                    TreeItem<File> item = new TreeItem<>(file,
                            file.isDirectory() ? uiController.createFolderIcon() : uiController.createFileIcon());
                    if (file.isDirectory()) {
                        populateTreeView(file, item);
                    }
                    parentItem.getChildren().add(item);
                }
            }
        }
    }

    public void closeCurrentTab() {
        Tab selectedTab = uiController.getCodeTabPane().getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            uiController.getCodeTabPane().getTabs().remove(selectedTab);
        }
    }

    public void updateTitle() {
        String title = "LMC IDE";
        if (currentProjectDirectory != null) {
            title += " - " + currentProjectDirectory.getName();
        }
        Tab selectedTab = uiController.getCodeTabPane().getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            title += " (" + selectedTab.getText() + ")";
        }
        primaryStage.setTitle(title);
    }

    public void findNext(String query) {
        CodeArea codeArea = uiController.getCurrentCodeArea();
        if (codeArea == null || query.isEmpty())
            return;

        int nextMatch = codeArea.getText().toLowerCase().indexOf(query.toLowerCase(), codeArea.getCaretPosition());
        if (nextMatch == -1) {
            nextMatch = codeArea.getText().toLowerCase().indexOf(query.toLowerCase());
        }

        if (nextMatch != -1) {
            codeArea.selectRange(nextMatch, nextMatch + query.length());
        } else {
            showAlert("Info", "No occurrences found for '" + query + "'.");
        }
    }

    public void replaceNext() {
        CodeArea codeArea = uiController.getCurrentCodeArea();
        String query = uiController.getReplaceFindPopupText();
        String replacement = uiController.getReplaceWithPopupText();
        if (codeArea == null || query.isEmpty())
            return;

        if (codeArea.getSelectedText().equalsIgnoreCase(query)) {
            codeArea.replaceSelection(replacement);
        }
        findNext(query);
    }

    public void replaceAll() {
        CodeArea codeArea = uiController.getCurrentCodeArea();
        String query = uiController.getReplaceFindPopupText();
        String replacement = uiController.getReplaceWithPopupText();
        if (codeArea == null || query.isEmpty())
            return;

        codeArea.replaceText(codeArea.getText().replaceAll("(?i)" + Pattern.quote(query), replacement));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
