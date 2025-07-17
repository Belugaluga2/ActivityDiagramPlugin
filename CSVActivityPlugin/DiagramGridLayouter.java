package com.example.csvactivityplugin;

import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.magicdraw.uml.symbols.shapes.ShapeElement;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OpaqueAction;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.CallBehaviorAction;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.InputPin;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OutputPin;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.Pin;

import java.awt.Rectangle;
import java.util.List;
import java.util.ArrayList;

/**
 * Lays out ActivityNode shapes in a single column at regular vertical intervals.
 * Also positions input pins on the left side and output pins on the right side of actions.
 * Now supports multiple action types: OpaqueAction, CallBehaviorAction, and StructuredActivityNode.
 */
public final class DiagramGridLayouter {
    private DiagramGridLayouter() {}

    public static void layout(Activity activity, DiagramPresentationElement dpe, int columnX, int startY, int yStep)
            throws ReadOnlyElementException {
        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        int y = startY;

        // Center the column horizontally in a typical diagram width
        int diagramWidth = 1200; // Typical diagram width
        int actionWidth = 200;   // Standard action width
        int centeredColumnX = (diagramWidth - actionWidth) / 2;

        for (ActivityNode node : activity.getNode()) {
            PresentationElement pe = dpe.findPresentationElement(node, PresentationElement.class);
            if (pe instanceof ShapeElement) {
                ShapeElement se = (ShapeElement) pe;

                // Set size based on node type
                int width = actionWidth;  // Keep consistent width for all actions
                int height = 80;  // Default height for actions
                int nodeX = centeredColumnX; // Default X position
                
                // Smaller size for initial and final nodes
                if (!(node instanceof OpaqueAction) && 
                    !(node instanceof CallBehaviorAction) && 
                    !(node instanceof StructuredActivityNode)) {
                    width = 20;
                    height = 20;
                    // Center smaller nodes within the column
                    nodeX = centeredColumnX + (actionWidth - width) / 2;
                }
                
                // Collect input and output pins from different action types
                List<InputPin> inputPins = new ArrayList<>();
                List<OutputPin> outputPins = new ArrayList<>();
                
                if (node instanceof OpaqueAction) { 
                    OpaqueAction action = (OpaqueAction) node;
                    
                    for (Pin pin : action.getInput()) {
                        if (pin instanceof InputPin) {
                            inputPins.add((InputPin) pin);
                        }
                    }
                    
                    for (Pin pin : action.getOutput()) {
                        if (pin instanceof OutputPin) {
                            outputPins.add((OutputPin) pin);
                        }
                    }
                } else if (node instanceof CallBehaviorAction) {
                    CallBehaviorAction action = (CallBehaviorAction) node;
                    
                    for (Pin pin : action.getArgument()) {
                        if (pin instanceof InputPin) {
                            inputPins.add((InputPin) pin);
                        }
                    }
                    
                    for (Pin pin : action.getResult()) {
                        if (pin instanceof OutputPin) {
                            outputPins.add((OutputPin) pin);
                        }
                    }
                } else if (node instanceof StructuredActivityNode) {
                    StructuredActivityNode action = (StructuredActivityNode) node;
                    
                    for (Pin pin : action.getStructuredNodeInput()) {
                        if (pin instanceof InputPin) {
                            inputPins.add((InputPin) pin);
                        }
                    }
                    
                    for (Pin pin : action.getStructuredNodeOutput()) {
                        if (pin instanceof OutputPin) {
                            outputPins.add((OutputPin) pin);
                        }
                    }
                }
                
                // Make action node taller if it has more than 3 input/output pins
                if (inputPins.size() > 3 || outputPins.size() > 3) {
                    int heightChange = (Math.max(inputPins.size(), outputPins.size()) - 3) * 25;
                    height += heightChange;
                }

                // Position and resize the node - use consistent width
                Rectangle rect = new Rectangle(nodeX, y, width, height);
                pem.reshapeShapeElement(se, rect);
                
                // Debug: Print the node name and size
                System.out.println("Node: " + node.getName() + " (" + node.getClass().getSimpleName() + ") - Set bounds: " + rect);
                
                // If this is an action with pins, position them
                if (!inputPins.isEmpty() || !outputPins.isEmpty()) {
                    // Wait a moment for the shape to update (sometimes needed in MagicDraw)
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    
                    // Get the actual bounds after reshape
                    Rectangle actualBounds = se.getBounds();
                    positionPins(inputPins, outputPins, dpe, pem, actualBounds.x, actualBounds.y, actualBounds.width, actualBounds.height);
                    
                    // Force the action to maintain its width after pin positioning
                    Rectangle currentBounds = se.getBounds();
                    System.out.println("Node: " + node.getName() + " - Actual bounds after pins: " + currentBounds);
                    if (currentBounds.width != width) {
                        Rectangle fixedRect = new Rectangle(nodeX, y, width, height);
                        pem.reshapeShapeElement(se, fixedRect);
                        System.out.println("Node: " + node.getName() + " - Forced back to: " + fixedRect);
                        
                        // Re-position pins with correct bounds
                        positionPins(inputPins, outputPins, dpe, pem, nodeX, y, width, height);
                    }
                }
                
                y += height + yStep; // Add height plus spacing for next node
            }
        }
    }
    
    /**
     * Positions input pins on the left side and output pins on the right side of an action.
     */
    private static void positionPins(List<InputPin> inputPins, List<OutputPin> outputPins, DiagramPresentationElement dpe, 
                                   PresentationElementsManager pem, int actionX, int actionY, 
                                   int actionWidth, int actionHeight) throws ReadOnlyElementException {
        
        // Position input pins on the left side
        int pinWidth = 20;
        int pinHeight = 20;
        int pinSpacing = 5;
        
        // Calculate vertical spacing for input pins
        int totalInputHeight = inputPins.size() * pinHeight + (inputPins.size() - 1) * pinSpacing;
        int inputPinStartY = actionY + (actionHeight - totalInputHeight) / 2;
        
        for (int i = 0; i < inputPins.size(); i++) {
            InputPin pin = inputPins.get(i);
            PresentationElement pinPE = dpe.findPresentationElement(pin, PresentationElement.class);
            
            if (pinPE instanceof ShapeElement) {
                ShapeElement pinShape = (ShapeElement) pinPE;
                // Position on the left edge of the action
                // Pin center should be on the action edge
                int pinX = actionX - pinWidth / 2;
                int pinY = inputPinStartY + i * (pinHeight + pinSpacing);
                
                Rectangle pinRect = new Rectangle(pinX, pinY, pinWidth, pinHeight);
                pem.reshapeShapeElement(pinShape, pinRect);
                
                // Debug
                System.out.println("Input pin " + pin.getName() + " positioned at: " + pinRect);
            }
        }
        
        // Position output pins on the right side
        int totalOutputHeight = outputPins.size() * pinHeight + (outputPins.size() - 1) * pinSpacing;
        int outputPinStartY = actionY + (actionHeight - totalOutputHeight) / 2;
        
        for (int i = 0; i < outputPins.size(); i++) {
            OutputPin pin = outputPins.get(i);
            PresentationElement pinPE = dpe.findPresentationElement(pin, PresentationElement.class);
            
            if (pinPE instanceof ShapeElement) {
                ShapeElement pinShape = (ShapeElement) pinPE;
                // Position on the right edge of the action
                // Pin center should be on the action edge
                int pinX = actionX + actionWidth - pinWidth / 2;
                int pinY = outputPinStartY + i * (pinHeight + pinSpacing);
                
                Rectangle pinRect = new Rectangle(pinX, pinY, pinWidth, pinHeight);
                pem.reshapeShapeElement(pinShape, pinRect);
                
                // Debug
                System.out.println("Output pin " + pin.getName() + " positioned at: " + pinRect);
                System.out.println("Action bounds: x=" + actionX + ", y=" + actionY + ", w=" + actionWidth + ", h=" + actionHeight);
            }
        }
    }
}