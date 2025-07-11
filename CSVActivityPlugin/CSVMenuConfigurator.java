package com.example.csvactivityplugin;

import com.nomagic.actions.AMConfigurator;
import com.nomagic.actions.ActionsCategory;
import com.nomagic.actions.ActionsManager;

/**
 * Configures the Cameo menu to include our CSV Import action under "Tools".
 */
public class CSVMenuConfigurator implements AMConfigurator {

    /**
     * Called by Cameo to let us add our menu actions.
     */
    @Override
    public void configure(ActionsManager manager) {
        // Try to find the existing "Tools" menu
        ActionsCategory toolsCategory = (ActionsCategory) manager.getActionFor("TOOLS");

        // If it doesn't exist, create it
        if (toolsCategory == null) {
            toolsCategory = new ActionsCategory("TOOLS", "Tools");
            manager.addCategory(toolsCategory);
        }

        // Create and add our CSV import action
        CSVImportAction importAction = new CSVImportAction();
        toolsCategory.addAction(importAction);

        System.out.println("CSV Import action added to Tools menu");
    }

    /**
     * Standard priority so this configurator runs in the normal sequence.
     */
    @Override
    public int getPriority() {
        return AMConfigurator.MEDIUM_PRIORITY;
    }
}
