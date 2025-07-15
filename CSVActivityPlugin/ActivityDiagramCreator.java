package com.example.csvactivityplugin;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ControlFlow;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityFinalNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OpaqueAction;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.CallBehaviorAction;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.InputPin;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OutputPin;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.Pin;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.impl.ElementsFactory;
import java.awt.Frame;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates an Activity model plus a diagram, then applies vertical layout.
 * 
 * This class is responsible for:
 * 1. Creating a UML Activity model from imported Excel data
 * 2. Allowing users to choose action types (Structured Activity Node or Call Behavior Action)
 * 3. Generating activity nodes with input/output pins based on the data
 * 4. Creating a visual diagram representation
 * 5. Applying automatic layout to organize the diagram elements
 * 6. Reusing existing actions instead of creating duplicates
 */
public class ActivityDiagramCreator {

    /**
     * Main method to create a complete activity diagram from Excel data.
     * 
     * This method orchestrates the entire process:
     * - Shows user dialog to choose action types
     * - Creates a MagicDraw session for safe model modifications
     * - Builds the activity model with nodes and flows
     * - Creates and opens the visual diagram
     * - Applies automatic layout for better presentation
     * 
     * @param project The MagicDraw project to add the diagram to
     * @param rows List of ActivityData objects containing the imported Excel data
     * @throws Exception If any step in the creation process fails
     */
    public void createActivityDiagram(Project project, List<ActivityData> rows) throws Exception {
        // Step 1: Show dialog to let user choose action types
        Frame parentFrame = MDDialogParentProvider.getProvider().getDialogParent();
        Map<String, ActionTypeChooser.ActionType> actionTypes = 
            ActionTypeChooser.chooseActionTypes(parentFrame, rows);
        
        if (actionTypes == null) {
            // User cancelled the action type selection
            throw new Exception("Action type selection was cancelled");
        }
        
        // Get the session manager to handle model modifications safely
        SessionManager sm = SessionManager.getInstance();
        
        // Create a new session - this allows us to rollback changes if something goes wrong
        sm.createSession(project, "Import Excel Activities");
        
        try {
            // Step 2: Let user choose where to place the activity
            Element selectedParent = DiagramParentChooser.chooseParent(project);
            if (selectedParent == null) {
                // User cancelled the selection
                throw new Exception("No parent selected for the activity diagram");
            }
            
            // Step 3: Create the main Activity element
            Activity activity = createActivityElement(project);
            
            // Step 4: Move the activity to the selected parent
            ModelElementsManager mgr = ModelElementsManager.getInstance();
            if (activity.getOwner() != selectedParent) {
                mgr.moveElement(activity, selectedParent);
            }
            
            // Step 5: Create all the activity nodes (start, actions, end) and connect them
            createActivityNodes(project, activity, rows, actionTypes);

            // Step 6: Create the visual diagram and open it in the MagicDraw interface
            DiagramPresentationElement dpe = createAndOpenDiagram(project, activity);
            
            // Step 7: Add visual representations of nodes ONLY (not paths yet)
            populateDiagramNodes(activity, dpe);

            // Step 8: Apply automatic layout - arrange elements in a vertical grid
            DiagramGridLayouter.layout(activity, dpe, 100, 100, 100);
            
            // Step 9: NOW create the paths after layout is done
            populateDiagramPaths(activity, dpe);

            // Commit all changes - the session was successful
            sm.closeSession(project);
            
        } catch (Exception ex) {
            // If anything went wrong, rollback all changes made in this session
            sm.cancelSession(project);
            throw ex; // Re-throw the exception to let the caller handle it
        }
    }

    /**
     * Creates the main Activity element that will serve as the container for all nodes.
     * 
     * @param project The MagicDraw project
     * @return The newly created Activity element
     * @throws ReadOnlyElementException If the project is read-only
     */
    private Activity createActivityElement(Project project) throws ReadOnlyElementException {
        // Get the factory for creating UML elements
        ElementsFactory f = project.getElementsFactory();
        
        // Create a new Activity instance
        Activity act = f.createActivityInstance();
        act.setName("Imported Activities from Excel");
        
        // Add the activity to the project's primary model (root package)
        ModelElementsManager.getInstance().addElement(act, project.getPrimaryModel());
        
        return act;
    }

    /**
     * Searches for an existing action with the given name in the project.
     * Now searches for both OpaqueAction and other action types.
     * 
     * @param project The MagicDraw project to search in
     * @param actionName The name of the action to find
     * @return The existing action if found, null otherwise
     */
    private ActivityNode findExistingAction(Project project, String actionName) {
        // Start searching from the primary model (root package)
        Package primaryModel = project.getPrimaryModel();
        return findActionRecursively(primaryModel, actionName);
    }
    
    /**
     * Recursively searches for an action with the given name in a package and its sub-packages.
     * 
     * @param pkg The package to search in
     * @param actionName The name of the action to find
     * @return The existing action if found, null otherwise
     */
    private ActivityNode findActionRecursively(Package pkg, String actionName) {
        // Check all owned elements in this package
        Collection<Element> ownedElements = pkg.getOwnedElement();
        
        for (Element element : ownedElements) {
            // Check for various action types
            if (element instanceof OpaqueAction || 
                element instanceof CallBehaviorAction || 
                element instanceof StructuredActivityNode) {
                
                ActivityNode action = (ActivityNode) element;
                if (actionName.equals(action.getName())) {
                    return action;
                }
            }
            // If we find a sub-package, search recursively
            else if (element instanceof Package) {
                Package subPackage = (Package) element;
                ActivityNode found = findActionRecursively(subPackage, actionName);
                if (found != null) {
                    return found;
                }
            }
            // If we find an Activity, search its nodes too
            else if (element instanceof Activity) {
                Activity activity = (Activity) element;
                for (ActivityNode node : activity.getNode()) {
                    if ((node instanceof OpaqueAction || 
                         node instanceof CallBehaviorAction || 
                         node instanceof StructuredActivityNode) && 
                        actionName.equals(node.getName())) {
                        return node;
                    }
                }
            }
        }
        
        return null; // Not found in this package
    }
    
    /**
     * Checks if an existing action has compatible pins with the required pins from Excel data.
     * Works with both OpaqueAction and CallBehaviorAction.
     * 
     * @param existingAction The existing action to check
     * @param requiredInputs List of required input pin names
     * @param requiredOutputs List of required output pin names
     * @return true if the action has all required pins, false otherwise
     */
    private boolean hasCompatiblePins(ActivityNode existingAction, List<String> requiredInputs, List<String> requiredOutputs) {
        // Get existing pin names
        List<String> existingInputNames = new ArrayList<>();
        List<String> existingOutputNames = new ArrayList<>();
        
        if (existingAction instanceof OpaqueAction) {
            OpaqueAction opaqueAction = (OpaqueAction) existingAction;
            for (Pin pin : opaqueAction.getInput()) {
                if (pin instanceof InputPin) {
                    existingInputNames.add(pin.getName());
                }
            }
            for (Pin pin : opaqueAction.getOutput()) {
                if (pin instanceof OutputPin) {
                    existingOutputNames.add(pin.getName());
                }
            }
        } else if (existingAction instanceof CallBehaviorAction) {
            CallBehaviorAction callAction = (CallBehaviorAction) existingAction;
            for (Pin pin : callAction.getArgument()) {
                if (pin instanceof InputPin) {
                    existingInputNames.add(pin.getName());
                }
            }
            for (Pin pin : callAction.getResult()) {
                if (pin instanceof OutputPin) {
                    existingOutputNames.add(pin.getName());
                }
            }
        } else if (existingAction instanceof StructuredActivityNode) {
            StructuredActivityNode structuredNode = (StructuredActivityNode) existingAction;
            for (Pin pin : structuredNode.getStructuredNodeInput()) {
                if (pin instanceof InputPin) {
                    existingInputNames.add(pin.getName());
                }
            }
            for (Pin pin : structuredNode.getStructuredNodeOutput()) {
                if (pin instanceof OutputPin) {
                    existingOutputNames.add(pin.getName());
                }
            }
        }
        
        // Check if all required inputs exist
        for (String requiredInput : requiredInputs) {
            if (!existingInputNames.contains(requiredInput)) {
                return false;
            }
        }
        
        // Check if all required outputs exist
        for (String requiredOutput : requiredOutputs) {
            if (!existingOutputNames.contains(requiredOutput)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Adds missing pins to an existing action to match the Excel data requirements.
     * 
     * @param project The MagicDraw project
     * @param existingAction The existing action to modify
     * @param requiredInputs List of required input pin names
     * @param requiredOutputs List of required output pin names
     * @throws ReadOnlyElementException If the project is read-only
     */
    private void addMissingPins(Project project, ActivityNode existingAction, 
                               List<String> requiredInputs, List<String> requiredOutputs) 
                               throws ReadOnlyElementException {
        
        ElementsFactory f = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        
        // Get existing pin names
        List<String> existingInputNames = new ArrayList<>();
        List<String> existingOutputNames = new ArrayList<>();
        
        if (existingAction instanceof OpaqueAction) {
            OpaqueAction opaqueAction = (OpaqueAction) existingAction;
            for (Pin pin : opaqueAction.getInput()) {
                if (pin instanceof InputPin) {
                    existingInputNames.add(pin.getName());
                }
            }
            for (Pin pin : opaqueAction.getOutput()) {
                if (pin instanceof OutputPin) {
                    existingOutputNames.add(pin.getName());
                }
            }
            
            // Add missing pins to OpaqueAction
            for (String requiredInput : requiredInputs) {
                if (!existingInputNames.contains(requiredInput)) {
                    InputPin newPin = f.createInputPinInstance();
                    newPin.setName(requiredInput);
                    mgr.addElement(newPin, opaqueAction);
                }
            }
            
            for (String requiredOutput : requiredOutputs) {
                if (!existingOutputNames.contains(requiredOutput)) {
                    OutputPin newPin = f.createOutputPinInstance();
                    newPin.setName(requiredOutput);
                    mgr.addElement(newPin, opaqueAction);
                }
            }
        } else if (existingAction instanceof CallBehaviorAction) {
            CallBehaviorAction callAction = (CallBehaviorAction) existingAction;
            for (Pin pin : callAction.getArgument()) {
                if (pin instanceof InputPin) {
                    existingInputNames.add(pin.getName());
                }
            }
            for (Pin pin : callAction.getResult()) {
                if (pin instanceof OutputPin) {
                    existingOutputNames.add(pin.getName());
                }
            }
            
            // Add missing pins to CallBehaviorAction
            for (String requiredInput : requiredInputs) {
                if (!existingInputNames.contains(requiredInput)) {
                    InputPin newPin = f.createInputPinInstance();
                    newPin.setName(requiredInput);
                    mgr.addElement(newPin, callAction);
                }
            }
            
            for (String requiredOutput : requiredOutputs) {
                if (!existingOutputNames.contains(requiredOutput)) {
                    OutputPin newPin = f.createOutputPinInstance();
                    newPin.setName(requiredOutput);
                    mgr.addElement(newPin, callAction);
                }
            }
        } else if (existingAction instanceof StructuredActivityNode) {
            StructuredActivityNode structuredNode = (StructuredActivityNode) existingAction;
            for (Pin pin : structuredNode.getStructuredNodeInput()) {
                if (pin instanceof InputPin) {
                    existingInputNames.add(pin.getName());
                }
            }
            for (Pin pin : structuredNode.getStructuredNodeOutput()) {
                if (pin instanceof OutputPin) {
                    existingOutputNames.add(pin.getName());
                }
            }
            
            // Add missing pins to StructuredActivityNode
            for (String requiredInput : requiredInputs) {
                if (!existingInputNames.contains(requiredInput)) {
                    InputPin newPin = f.createInputPinInstance();
                    newPin.setName(requiredInput);
                    mgr.addElement(newPin, structuredNode);
                }
            }
            
            for (String requiredOutput : requiredOutputs) {
                if (!existingOutputNames.contains(requiredOutput)) {
                    OutputPin newPin = f.createOutputPinInstance();
                    newPin.setName(requiredOutput);
                    mgr.addElement(newPin, structuredNode);
                }
            }
        }
        
        System.out.println("Added missing pins to action: " + existingAction.getName());
    }
    
    /**
     * Creates a new action based on the specified action type.
     * 
     * @param project The MagicDraw project
     * @param activity The parent activity
     * @param activityData The data for this action
     * @param actionType The type of action to create
     * @return The newly created action
     * @throws ReadOnlyElementException If the project is read-only
     */
    private ActivityNode createNewAction(Project project, Activity activity, 
                                       ActivityData activityData, ActionTypeChooser.ActionType actionType) 
                                       throws ReadOnlyElementException {
        
        ElementsFactory f = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        
        ActivityNode action = null;
        
        switch (actionType) {
            case STRUCTURED_ACTIVITY:
                StructuredActivityNode structuredNode = f.createStructuredActivityNodeInstance();
                structuredNode.setName(activityData.getName());
                mgr.addElement(structuredNode, activity);
                
                // Add input pins
                for (String inputName : activityData.getInputs()) {
                    InputPin pin = f.createInputPinInstance();
                    pin.setName(inputName);
                    mgr.addElement(pin, structuredNode);
                    structuredNode.getStructuredNodeInput().add(pin);
                }
                
                // Add output pins
                for (String outputName : activityData.getOutputs()) {
                    OutputPin pin = f.createOutputPinInstance();
                    pin.setName(outputName);
                    mgr.addElement(pin, structuredNode);
                    structuredNode.getStructuredNodeOutput().add(pin);
                }
                
                action = structuredNode;
                break;
                
            case CALL_BEHAVIOR:
                CallBehaviorAction callAction = f.createCallBehaviorActionInstance();
                callAction.setName(activityData.getName());
                mgr.addElement(callAction, activity);
                
                // Add input pins (arguments)
                for (String inputName : activityData.getInputs()) {
                    InputPin pin = f.createInputPinInstance();
                    pin.setName(inputName);
                    mgr.addElement(pin, callAction);
                    callAction.getArgument().add(pin);
                }
                
                // Add output pins (results)
                for (String outputName : activityData.getOutputs()) {
                    OutputPin pin = f.createOutputPinInstance();
                    pin.setName(outputName);
                    mgr.addElement(pin, callAction);
                    callAction.getResult().add(pin);
                }
                
                action = callAction;
                break;
        }
        
        System.out.println("Created new " + actionType.getDisplayName() + ": " + activityData.getName());
        return action;
    }

    /**
     * Creates all the activity nodes and connects them with control flows.
     * 
     * This method builds the complete activity flow:
     * - Creates an initial node (start point)
     * - Creates or reuses action nodes for each row of Excel data
     * - Creates input/output pins for each action based on the data
     * - Creates control flows to connect everything in sequence
     * - Creates a final node (end point)
     * 
     * @param project The MagicDraw project
     * @param activity The parent Activity element to contain all nodes
     * @param rows The Excel data rows to convert into activity nodes
     * @param actionTypes Map of action names to their selected types
     * @throws ReadOnlyElementException If the project is read-only
     */
    private void createActivityNodes(Project project, Activity activity, List<ActivityData> rows,
                                   Map<String, ActionTypeChooser.ActionType> actionTypes)
            throws ReadOnlyElementException {
        
        ElementsFactory f = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();

        // Create the initial node (start of the activity)
        InitialNode start = f.createInitialNodeInstance();
        start.setName("Start");
        mgr.addElement(start, activity);

        // Keep track of the previous node to create sequential flow
        ActivityNode prev = start;
        
        // Process each row of Excel data to create action nodes
        for (ActivityData d : rows) {
            ActivityNode action = null;
            ActionTypeChooser.ActionType selectedType = actionTypes.get(d.getName());
            
            // First, try to find an existing action with the same name
            ActivityNode existingAction = findExistingAction(project, d.getName());
            
            if (existingAction != null) {
                // Found an existing action - check if it has compatible pins
                if (hasCompatiblePins(existingAction, d.getInputs(), d.getOutputs())) {
                    // Use the existing action as-is
                    action = existingAction;
                    System.out.println("Reusing existing action: " + d.getName());
                } else {
                    // Existing action needs additional pins
                    addMissingPins(project, existingAction, d.getInputs(), d.getOutputs());
                    action = existingAction;
                    System.out.println("Reusing existing action with added pins: " + d.getName());
                }
                
                // Add the existing action to our activity if it's not already there
                if (!activity.getNode().contains(action)) {
                    mgr.addElement(action, activity);
                }
            } else {
                // No existing action found - create a new one based on selected type
                action = createNewAction(project, activity, d, selectedType);
            }
            
            // Create a control flow from the previous node to this action
            ControlFlow flow = f.createControlFlowInstance();
            flow.setSource(prev);  // Where the flow comes from
            flow.setTarget(action); // Where the flow goes to
            mgr.addElement(flow, activity);
            
            // Update prev to point to this action for the next iteration
            prev = action;
        }
        
        // Create the final node (end of the activity)
        ActivityFinalNode end = f.createActivityFinalNodeInstance();
        end.setName("End");
        mgr.addElement(end, activity);

        // Connect the last action to the end node
        ControlFlow finalFlow = f.createControlFlowInstance();
        finalFlow.setSource(prev);  // The last action created
        finalFlow.setTarget(end);   // The final node
        mgr.addElement(finalFlow, activity);
    }

    /**
     * Creates the visual diagram and opens it in the MagicDraw interface.
     * 
     * @param project The MagicDraw project
     * @param activity The Activity element to create a diagram for
     * @return The diagram presentation element for further manipulation
     * @throws ReadOnlyElementException If the project is read-only
     */
    private DiagramPresentationElement createAndOpenDiagram(Project project, Activity activity)
            throws ReadOnlyElementException {
        
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        
        // Create a new SysML Activity Diagram
        // The diagram is semantically owned by the activity element
        Diagram sem = mgr.createDiagram(
            "SysML Activity Diagram", activity);
        sem.setName("Imported Activities from Excel");

        // Get the presentation element (the visual representation of the diagram)
        DiagramPresentationElement dpe = project.getDiagram(sem);
        
        // Open the diagram in the MagicDraw interface so user can see it
        dpe.open();
        
        return dpe;
    }

    /**
     * Populates the diagram with visual representations of nodes only.
     */
    private void populateDiagramNodes(Activity activity, DiagramPresentationElement dpe)
            throws ReadOnlyElementException {
        
        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        
        // Create visual shapes for all activity nodes
        // This includes InitialNode, various action types, ActivityFinalNode, and any pins
        for (ActivityNode node : activity.getNode()) {
            pem.createShapeElement(node, dpe);
        }
    }

    /**
     * Populates the diagram with paths (control flows) after layout is done.
     */
    private void populateDiagramPaths(Activity activity, DiagramPresentationElement dpe)
            throws ReadOnlyElementException {
        
        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        
        // Create visual connectors (arrows) for all control flows
        for (Object o : activity.getEdge()) {
            if (o instanceof ControlFlow) {
                ControlFlow f = (ControlFlow)o;
                
                // Find the visual elements for the source and target nodes
                PresentationElement src = dpe.findPresentationElement(f.getSource(), PresentationElement.class);
                PresentationElement tgt = dpe.findPresentationElement(f.getTarget(), PresentationElement.class);
                
                // Create the path element (arrow) connecting the two shapes
                // Only create if both source and target visual elements exist
                if (src != null && tgt != null) {
                    pem.createPathElement(f, src, tgt);
                }
            }
        }
    }
}