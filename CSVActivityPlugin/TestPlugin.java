package com.example.csvactivityplugin;

import com.nomagic.magicdraw.plugins.Plugin;
import javax.swing.JOptionPane;

/**
 * Minimal test plugin to verify plugin loading works
 */
public class TestPlugin extends Plugin {
    
    @Override
    public void init() {
        // Show a popup to prove the plugin loaded
        JOptionPane.showMessageDialog(null, 
            "Test Plugin Loaded Successfully!", 
            "Plugin Test", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    @Override
    public boolean close() {
        return true;
    }
    
    @Override
    public boolean isSupported() {
        return true;
    }
}