package com.lmc.ide;

import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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
            for (Tab t : uiController.getEditorTabPane().getTabs()) {
                if (t.getText().equals(tabName)) {
                    newFileCount++;
                    nameExists = true;
                    break;
                }
            }
        } while (nameExists);

        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        uiController.newTab(tabName, codeArea);
    }

    public void openFile(File file) {
        for (Tab tab : uiController.getEditorTabPane().getTabs()) {
            if (file.equals(tab.getUserData())) {
                uiController.getEditorTabPane().getSelectionModel().select(tab);
                return;
            }
        }
        try {
            String content = Files.readString(file.toPath());
            CodeArea codeArea = new CodeArea();
            codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
            codeArea.replaceText(content);
            uiController.newTab(file.getName(), codeArea);
        } catch (IOException e) {
            showAlert("Error", "Could not read file: " + e.getMessage());
        }
    }

    public void saveFile() {
        Tab selectedTab = uiController.getEditorTabPane().getSelectionModel().getSelectedItem();
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
        Tab selectedTab = uiController.getEditorTabPane().getSelectionModel().getSelectedItem();
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
            uiController.refreshFileExplorer(currentProjectDirectory);
        }
    }

    public void closeCurrentTab() {
        Tab selectedTab = uiController.getEditorTabPane().getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            uiController.getEditorTabPane().getTabs().remove(selectedTab);
        }
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