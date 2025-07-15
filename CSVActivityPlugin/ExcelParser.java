package com.example.csvactivityplugin;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles parsing of Excel files into ActivityData objects.
 * Supports both .xlsx (newer) and .xls (older) formats.
 * 
 * This parser looks for specific column names and only processes rows
 * where the Name column starts with "Action".
 * 
 * Expected columns:
 * - Name: The activity name (must start with "Action" to be included)
 * - Input: Input pins (comma or semicolon separated)
 * - Output: Output pins (comma or semicolon separated)
 */
public class ExcelParser {
    
    // Delimiters used to separate multiple inputs/outputs
    private static final String[] DELIMITERS = {";", ","};
    
    // Column names to search for (case-insensitive)
    private static final String COL_NAME = "Name";
    private static final String COL_INPUT = "Input";
    private static final String COL_OUTPUT = "Output";
    
    // Prefix that identifies action rows
    private static final String ACTION_PREFIX = "Action";
    
    /**
     * Parses an Excel file and returns a list of ActivityData objects.
     * Only processes rows where the Name column starts with "Action".
     * 
     * @param excelFile The Excel file to parse (.xls or .xlsx)
     * @return List of ActivityData objects parsed from the file
     * @throws IOException If there's an error reading the file
     */
    public List<ActivityData> parseExcel(File excelFile) throws IOException {
        List<ActivityData> activities = new ArrayList<>();
        
        // Determine file type and create appropriate workbook
        Workbook workbook = null;
        FileInputStream fis = null;
        
        try {
            fis = new FileInputStream(excelFile);
            
            // Create workbook based on file extension
            if (excelFile.getName().toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (excelFile.getName().toLowerCase().endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new IOException("Unsupported file format. Please use .xls or .xlsx files.");
            }
            
            // Get the first sheet (or you could let user select)
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IOException("Excel file has no sheets");
            }
            
            // Find header row and column indices
            Map<String, Integer> columnIndices = findColumnIndices(sheet);
            if (columnIndices == null) {
                throw new IOException("Could not find required columns (Name, Input, Output) in the Excel file");
            }
            
            // Validate that we have the required columns
            if (!columnIndices.containsKey(COL_NAME)) {
                throw new IOException("Required column 'Name' not found in Excel file");
            }
            
            int headerRowIndex = findHeaderRowIndex(sheet, columnIndices);
            
            // Process data rows (starting after header)
            for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue; // Skip empty rows
                }
                
                try {
                    ActivityData activity = parseRow(row, columnIndices);
                    if (activity != null) {
                        activities.add(activity);
                    }
                } catch (Exception e) {
                    // Log error but continue processing other rows
                    System.err.println("Warning: Error parsing row " + (rowIndex + 1) + ": " + e.getMessage());
                }
            }
            
        } finally {
            // Clean up resources
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
        }
        
        return activities;
    }
    
    /**
     * Finds the column indices for Name, Input, and Output columns.
     * Searches through the first 10 rows to find the header row.
     * 
     * @param sheet The Excel sheet to search
     * @return Map of column names to their indices, or null if not found
     */
    private Map<String, Integer> findColumnIndices(Sheet sheet) {
        // Search first 10 rows for headers
        for (int rowIndex = 0; rowIndex < Math.min(10, sheet.getLastRowNum() + 1); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            Map<String, Integer> indices = new HashMap<>();
            
            // Check each cell in the row
            for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                Cell cell = row.getCell(colIndex);
                String value = getCellValue(cell).toLowerCase();
                
                // Look for our column names (case-insensitive)
                if (value.contains("name")) {
                    indices.put(COL_NAME, colIndex);
                } else if (value.contains("input")) {
                    indices.put(COL_INPUT, colIndex);
                } else if (value.contains("output")) {
                    indices.put(COL_OUTPUT, colIndex);
                }
            }
            
            // If we found at least the Name column, we've found our header row
            if (indices.containsKey(COL_NAME)) {
                return indices;
            }
        }
        
        return null;
    }
    
    /**
     * Finds the row index of the header row.
     * 
     * @param sheet The Excel sheet
     * @param columnIndices Map containing column indices
     * @return The row index of the header
     */
    private int findHeaderRowIndex(Sheet sheet, Map<String, Integer> columnIndices) {
        // Find which row contains our headers
        for (int rowIndex = 0; rowIndex < Math.min(10, sheet.getLastRowNum() + 1); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            
            Cell nameCell = row.getCell(columnIndices.get(COL_NAME));
            String value = getCellValue(nameCell).toLowerCase();
            
            if (value.contains("name")) {
                return rowIndex;
            }
        }
        
        return 0; // Default to first row
    }
    
    /**
     * Parses a single row of Excel data into an ActivityData object.
     * Only processes rows where the Name starts with "Action".
     * 
     * @param row The Excel row to parse
     * @param columnIndices Map of column names to their indices
     * @return ActivityData object containing the parsed data, or null if not an Action row
     */
    private ActivityData parseRow(Row row, Map<String, Integer> columnIndices) {
        // Get the name value
        Integer nameIndex = columnIndices.get(COL_NAME);
        if (nameIndex == null) return null;
        
        String name = getCellValue(row.getCell(nameIndex));
        
        // Skip rows that don't start with "Action"
        if (!name.startsWith(ACTION_PREFIX)) {
            return null;
        }
        
        // Skip empty rows
        if (name.trim().isEmpty()) {
            return null;
        }
        
        ActivityData activity = new ActivityData();
        
        // Set the name (removing "Action" prefix for cleaner display if desired)
        // For now, keeping the full name as-is
        activity.setName(name);
        
        // Parse inputs if column exists
        Integer inputIndex = columnIndices.get(COL_INPUT);
        if (inputIndex != null) {
            String inputsString = getCellValue(row.getCell(inputIndex));
            if (!inputsString.isEmpty()) {
                List<String> inputs = parseDelimitedString(inputsString);
                activity.setInputs(inputs);
            }
        }
        
        // Parse outputs if column exists
        Integer outputIndex = columnIndices.get(COL_OUTPUT);
        if (outputIndex != null) {
            String outputsString = getCellValue(row.getCell(outputIndex));
            if (!outputsString.isEmpty()) {
                List<String> outputs = parseDelimitedString(outputsString);
                activity.setOutputs(outputs);
            }
        }
        
        return activity;
    }
    
    /**
     * Gets the string value from a cell, handling different cell types.
     * 
     * @param cell The cell to read
     * @return String value of the cell, or empty string if null
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // convert numbers to strings
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Remove decimal point for whole numbers
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        return String.format("%.0f", value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Try to evaluate formula
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return "";
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
    
    /**
     * Parses a delimited string into a list of trimmed values.
     * Tries both semicolon and comma as delimiters.
     * 
     * @param delimitedString The string to parse
     * @return List of parsed values
     */
    private List<String> parseDelimitedString(String delimitedString) {
        List<String> values = new ArrayList<>();
        
        // Determine which delimiter is used
        String delimiter = ";";
        for (String delim : DELIMITERS) {
            if (delimitedString.contains(delim)) {
                delimiter = delim;
                break;
            }
        }
        
        // Split by delimiter and trim each value
        String[] valueArray = delimitedString.split(delimiter);
        
        for (String value : valueArray) {
            String trimmedValue = value.trim();
            if (!trimmedValue.isEmpty()) {
                values.add(trimmedValue);
            }
        }
        
        // If no delimiter found, treat the whole string as one value
        if (values.isEmpty() && !delimitedString.trim().isEmpty()) {
            values.add(delimitedString.trim());
        }
        
        return values;
    }
}