package com.lmc.ide;

import java.util.ArrayList;
import java.util.List;

public class AIModelManager {

    private final List<String> localModels;
    private final List<String> cloudModels;
    private String selectedModel;

    public AIModelManager() {
        localModels = new ArrayList<>();
        cloudModels = new ArrayList<>();
        initializeModels();
    }

    private void initializeModels() {
        // Add local models
        localModels.add("Local Lightweight Model");
        localModels.add("Local Medium Model");
        localModels.add("Local Heavy Model");

        // Add cloud models
        cloudModels.add("Cloud Model A");
        cloudModels.add("Cloud Model B");
        cloudModels.add("Cloud Model C");

        // Set a default model
        selectedModel = localModels.get(0);
    }

    public List<String> getLocalModels() {
        return localModels;
    }

    public List<String> getCloudModels() {
        return cloudModels;
    }

    public String getSelectedModel() {
        return selectedModel;
    }

    public void setSelectedModel(String selectedModel) {
        this.selectedModel = selectedModel;
    }
}