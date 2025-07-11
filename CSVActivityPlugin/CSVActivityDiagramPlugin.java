package com.example.csvactivityplugin;

import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.actions.ActionsConfiguratorsManager;
import com.nomagic.magicdraw.core.Application;
import javax.swing.JOptionPane;

/**
 * Main plugin class that Cameo Systems Modeler loads at startup.
 * This class is referenced in the plugin.xml file.
 * 
 * The plugin lifecycle:
 * 1. Cameo loads this class when starting up
 * 2. Calls init() to initialize the plugin
 * 3. Calls close() when shutting down
 */
public class CSVActivityDiagramPlugin extends Plugin {
    
    // Unique identifier for this plugin - should match the ID in plugin.xml
    public static final String PLUGIN_ID = "com.nomagic.magicdraw.exceltoDiagram";
    
    /**
     * Called by Cameo when the plugin is loaded.
     * This is where we register our menu items and actions.
     */
    @Override
    public void init() {
        // Log to both console and Cameo's notification system
        System.out.println("=== CSV Activity Diagram Plugin LOADED SUCCESSFULLY ===");
        // Show a popup to see if the plugin loaded
        JOptionPane.showMessageDialog(null, "Test plugin loaded successfully!", "Plugin Test", JOptionPane.INFORMATION_MESSAGE);
        
        // Try to show a notification in Cameo
        try {
            Application app = Application.getInstance();
            if (app != null) {
                app.getGUILog().log("CSV Activity Diagram Plugin initialized successfully!");
            }
        } catch (Exception e) {
            // Ignore if notification fails
        }
        
        // Register our menu configurator with Cameo
        // This will add our "Import CSV" action to the Tools menu
        ActionsConfiguratorsManager manager = ActionsConfiguratorsManager.getInstance();
        
        // Use our CSVMenuConfigurator
        manager.addMainMenuConfigurator(new CSVMenuConfigurator());
        
        System.out.println("CSV Activity Diagram Plugin initialization complete");
    }
    
    /**
     * Called by Cameo when the plugin is being unloaded.
     * Return true to allow the plugin to be closed.
     */
    @Override
    public boolean close() {
        System.out.println("CSV Activity Diagram Plugin closing...");
        return true;
    }
    
    /**
     * Indicates whether this plugin is supported in the current environment.
     * We return true because we support all Cameo environments.
     */
    @Override
    public boolean isSupported() {
        return true;
    }
}