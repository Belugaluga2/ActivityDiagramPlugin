package com.example.csvactivityplugin;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles parsing of CSV files into ActivityData objects.
 * 
 * Expected CSV format:
 * Activity Name,Documentation,Outputs
 * Initialize System,This initializes the system,Output1;Output2;Output3
 * Process Data,Processes the input data,ProcessedData;ErrorLog
 */
public class CSVParser {
    
    // Delimiter used to separate columns in the CSV
    private static final String CSV_DELIMITER = ",";
    
    // Delimiter used to separate multiple outputs within the outputs column
    private static final String OUTPUT_DELIMITER = ";";
    
    /**
     * Parses a CSV file and returns a list of ActivityData objects.
     * 
     * @param csvFile The CSV file to parse
     * @return List of ActivityData objects parsed from the file
     * @throws IOException If there's an error reading the file
     */
    public List<ActivityData> parseCSV(File csvFile) throws IOException {
        List<ActivityData> activities = new ArrayList<>();
        
        // Use try-with-resources to ensure the file is properly closed
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            
            // Read and skip the header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("CSV file is empty");
            }
            
            // Validate header (optional but good practice)
            validateHeader(headerLine);
            
            // Read each data line
            String line;
            int lineNumber = 2; // Start at 2 since we skipped the header
            
            while ((line = reader.readLine()) != null) {
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    // Parse the line into an ActivityData object
                    ActivityData activity = parseLine(line);
                    activities.add(activity);
                } catch (Exception e) {
                    // Provide helpful error message with line number
                    throw new IOException(
                        "Error parsing line " + lineNumber + ": " + e.getMessage()
                    );
                }
                
                lineNumber++;
            }
        }
        
        return activities;
    }
    
    /**
     * Validates that the CSV header has the expected format.
     * 
     * @param headerLine The header line from the CSV
     * @throws IOException If the header is invalid
     */
    private void validateHeader(String headerLine) throws IOException {
        String[] headers = headerLine.split(CSV_DELIMITER);
        
        if (headers.length < 3) {
            throw new IOException(
                "Invalid CSV header. Expected at least 3 columns: " +
                "Activity Name, Documentation, Outputs"
            );
        }
        
        // Optionally, you could check exact header names here
        // For now, we just check the column count
    }
    
    /**
     * Parses a single line of CSV data into an ActivityData object.
     * 
     * @param line The CSV line to parse
     * @return ActivityData object containing the parsed data
     */
    private ActivityData parseLine(String line) {
        // Split the line by comma
        // Note: This simple split won't handle commas within quoted fields
        // For production code, consider using a proper CSV library
        String[] parts = line.split(CSV_DELIMITER, 3); // Limit to 3 parts
        
        ActivityData activity = new ActivityData();
        
        // Parse activity name (required)
        if (parts.length >= 1) {
            activity.setName(parts[0].trim());
        }
        
        // Parse documentation (optional)
        if (parts.length >= 2) {
            activity.setDocumentation(parts[1].trim());
        }
        
        // Parse outputs (optional)
        if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
            String outputsString = parts[2].trim();
            List<String> outputs = parseOutputs(outputsString);
            activity.setOutputs(outputs);
        }
        
        return activity;
    }
    
    /**
     * Parses the outputs string into a list of individual output names.
     * Outputs are expected to be semicolon-separated.
     * 
     * @param outputsString The outputs string from the CSV (e.g., "Out1;Out2;Out3")
     * @return List of output names
     */
    private List<String> parseOutputs(String outputsString) {
        List<String> outputs = new ArrayList<>();
        
        // Split by semicolon and trim each output
        String[] outputArray = outputsString.split(OUTPUT_DELIMITER);
        
        for (String output : outputArray) {
            String trimmedOutput = output.trim();
            if (!trimmedOutput.isEmpty()) {
                outputs.add(trimmedOutput);
            }
        }
        
        return outputs;
    }
}