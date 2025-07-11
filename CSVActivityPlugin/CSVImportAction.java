package com.example.csvactivityplugin;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

/**
 * The action that appears in the Tools menu and handles the CSV import process.
 * MDAction is Cameo's base class for menu actions.
 */
public class CSVImportAction extends MDAction {
    
    /**
     * Constructor - sets up the action's ID, name, and other properties
     */
    public CSVImportAction() {
        // Parameters: ID, Display Name, Mnemonic Key, Group
        super("ImportCSVToActivityDiagram", "Import CSV to Activity Diagram", null, null);
        
        // Set a description that appears in tooltips
        setDescription("Import activities from a CSV file and create an activity diagram");
    }
    
    /**
     * Called when the user clicks on our menu item.
     * This is where the main import logic starts.
     * 
     * @param e The action event from the menu click
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        // Get the currently open project
        Project project = Application.getInstance().getProject();
        
        // Check if a project is open
        if (project == null) {
            JOptionPane.showMessageDialog(
                null, 
                "Please open a project before importing CSV data.", 
                "No Project Open", 
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // Show file chooser dialog for CSV selection
        File csvFile = selectCSVFile();
        if (csvFile == null) {
            // User cancelled the file selection
            return;
        }
        
        try {
            // Parse the CSV file
            CSVParser parser = new CSVParser();
            List<ActivityData> activities = parser.parseCSV(csvFile);
            
            // Validate the parsed data
            if (activities.isEmpty()) {
                JOptionPane.showMessageDialog(
                    null, 
                    "No activities found in the CSV file.", 
                    "Empty CSV", 
                    JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            
            // Create the activity diagram
            ActivityDiagramCreator creator = new ActivityDiagramCreator();
            creator.createActivityDiagram(project, activities);
            
            // Show success message
            JOptionPane.showMessageDialog(
                null, 
                "Successfully imported " + activities.size() + " activities!", 
                "Import Successful", 
                JOptionPane.INFORMATION_MESSAGE
            );
            
        } catch (Exception ex) {
            // Show error dialog if something goes wrong
            JOptionPane.showMessageDialog(
                null, 
                "Error importing CSV: " + ex.getMessage(), 
                "Import Error", 
                JOptionPane.ERROR_MESSAGE
            );
            ex.printStackTrace();
        }
    }
    
    /**
     * Shows a file chooser dialog for selecting a CSV file.
     * 
     * @return The selected file, or null if cancelled
     */
    private File selectCSVFile() {
        JFileChooser fileChooser = new JFileChooser();
        
        // Set up file filter for CSV files only
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files (*.csv)", "csv");
        fileChooser.setFileFilter(filter);
        fileChooser.setAcceptAllFileFilterUsed(false);
        
        // Set dialog title
        fileChooser.setDialogTitle("Select CSV File to Import");
        
        // Show the dialog
        int result = fileChooser.showOpenDialog(null);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }
        
        return null;
    }
    
    /**
     * Called by Cameo to update the enabled/disabled state of this action.
     * We enable the action only when a project is open.
     */
    @Override
    public void updateState() {
        setEnabled(Application.getInstance().getProject() != null);
    }
}