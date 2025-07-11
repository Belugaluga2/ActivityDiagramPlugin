package com.example.csvactivityplugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model class that represents a single activity from the CSV file.
 * This is a simple POJO (Plain Old Java Object) that holds the data
 * for one row in the CSV.
 */
public class ActivityData {
    
    // The name of the activity (column 1 in CSV)
    private String name;
    
    // Documentation/description for the activity (column 2 in CSV)
    private String documentation;
    
    // List of output pins for this activity (column 3 in CSV, semicolon-separated)
    private List<String> outputs;
    
    /**
     * Default constructor - initializes with empty values
     */
    public ActivityData() {
        this.name = "";
        this.documentation = "";
        this.outputs = new ArrayList<>();
    }
    
    /**
     * Constructor with all fields
     */
    public ActivityData(String name, String documentation, List<String> outputs) {
        this.name = name;
        this.documentation = documentation;
        this.outputs = outputs;
    }
    
    // Getters and setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name != null ? name : "";
    }
    
    public String getDocumentation() {
        return documentation;
    }
    
    public void setDocumentation(String documentation) {
        this.documentation = documentation != null ? documentation : "";
    }
    
    public List<String> getOutputs() {
        return outputs;
    }
    
    public void setOutputs(List<String> outputs) {
        this.outputs = outputs != null ? outputs : new ArrayList<>();
    }
    
    /**
     * Adds a single output to the outputs list
     */
    public void addOutput(String output) {
        if (output != null && !output.trim().isEmpty()) {
            this.outputs.add(output.trim());
        }
    }
    
    /**
     * Returns a string representation for debugging
     */
    @Override
    public String toString() {
        return "ActivityData{" +
               "name='" + name + '\'' +
               ", documentation='" + documentation + '\'' +
               ", outputs=" + outputs +
               '}';
    }
}