package com.example.csvactivityplugin;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.PresentationElementsManager;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.magicdraw.ui.dialogs.MDDialogParentProvider;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.magicdraw.uml.symbols.shapes.ShapeElement;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ControlFlow;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityFinalNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdstructuredactivities.StructuredActivityNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdintermediateactivities.ActivityPartition;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.CallBehaviorAction;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.InputPin;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OutputPin;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Profile;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.impl.ElementsFactory;

import java.awt.Frame;
import java.awt.Rectangle;
import java.util.*;

/**
 * Builds an Activity, one centered vertical swim‑lane, and lays out
 * the nodes inside that lane.
 */
public class ActivityDiagramCreator {

    /* ================================================================ */
    /*                    INSTANCE FIELDS                               */
    /* ================================================================ */

    /** lane → its PresentationElement */
    private final Map<ActivityPartition, PresentationElement> laneShapes = new HashMap<>();
    private final Map<String, ShapeElement> laneShapeByActor = new HashMap<>();

    /* ================================================================ */
    /*                    PUBLIC ENTRY POINT                            */
    /* ================================================================ */

    public void createActivityDiagram(Project project, List<ActivityData> rows)
            throws Exception {

        Frame parentFrame = MDDialogParentProvider.getProvider().getDialogParent();
        Map<String, ActionTypeChooser.ActionType> actionTypes =
                ActionTypeChooser.chooseActionTypes(parentFrame, rows);
        if (actionTypes == null) throw new Exception("Action type selection canceled");

        SessionManager sm = SessionManager.getInstance();
        sm.createSession(project, "Import Excel Activities");
        try {
            Element parent = DiagramParentChooser.chooseParent(project);
            if (parent == null) throw new Exception("No parent selected");

            /* 1 ── Activity element */
            Activity activity = createActivityElement(project);
            if (activity.getOwner() != parent)
                ModelElementsManager.getInstance().moveElement(activity, parent);

            /* 2 ── Partitions */
            Map<String, ActivityPartition> partitions =
                    createActivityPartitions(project, activity, rows);

            /* 3 ── Diagram */
            DiagramPresentationElement dpe = createAndOpenDiagram(project, activity);

            /* 4 ── Swim‑lane visuals */
            createPartitionPresentations(dpe, partitions);

            /* 5 ── Model nodes/edges & assign nodes to partitions */
            createActivityNodes(project, activity, rows, actionTypes, partitions);

            /* 6 ── Draw node shapes */
            populateDiagramNodes(activity, dpe);

            /* 7 ── Lay out nodes in a column */
            DiagramGridLayouter.layout(activity, dpe, 100, 100, 70);

            /* 8 ── Resize + center lane around its contents */
            fitLaneShapesToContents(dpe);

            /* 9 ── Draw control flow paths (after lane resize) */
            populateDiagramPaths(activity, dpe);

            sm.closeSession(project);
        } catch (Exception ex) {
            sm.cancelSession(project);
            throw ex;
        }
    }

    /* ================================================================ */
    /*                        MODEL HELPERS                             */
    /* ================================================================ */

    private Activity createActivityElement(Project project)
            throws ReadOnlyElementException {
        Activity act = project.getElementsFactory().createActivityInstance();
        act.setName("Imported Activities from Excel");
        ModelElementsManager.getInstance().addElement(act, project.getPrimaryModel());
        return act;
    }

    /** Build one partition per top level action’s actor. */
    private Map<String, ActivityPartition> createActivityPartitions(Project project,
                                                                    Activity activity,
                                                                    List<ActivityData> rows)
            throws ReadOnlyElementException {

        ElementsFactory f   = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        Map<String, ActivityPartition> parts = new LinkedHashMap<>();

        /* collect actors (top level rUnows only) */
        Set<String> actors = new LinkedHashSet<>();
        for (ActivityData d : rows) {
            if (d.isSubAction()) continue;
            String actor = (d.getActor()==null || d.getActor().trim().isEmpty())
                           ? "<Unassigned>" : d.getActor().trim();
            actors.add(actor);
        }

        /* optional SysML stereotype */
        Stereotype allocStereo = null;
        try {
            Profile sysml = StereotypesHelper.getProfile(project, "SysML");
            if (sysml != null) {
                allocStereo = StereotypesHelper.getStereotype(
                        project, "AllocateActivityPartition", sysml);
                if (allocStereo == null)
                    allocStereo = StereotypesHelper.getStereotype(
                            project, "AllocatedActivityPartition", sysml);
            }
        } catch (Exception ignore) { }

        for (String actor : actors) {
            ActivityPartition p = f.createActivityPartitionInstance();
            p.setName(actor);
            p.setDimension(true);
            p.setExternal(false);

            mgr.addElement(p, activity);
            activity.getPartition().add(p);

            if (allocStereo != null) {
                try { StereotypesHelper.addStereotype(p, allocStereo); }
                catch (Exception ignore) { }
            }
            parts.put(actor, p);
        }
        return parts;
    }

    /* ---------------------------------------------------------------- */
    /* Node & edge creation                                             */
    /* ---------------------------------------------------------------- */
    private void createActivityNodes(Project project, Activity activity,
                                     List<ActivityData> rows,
                                     Map<String,ActionTypeChooser.ActionType> actionTypes,
                                     Map<String,ActivityPartition> partitions)
            throws ReadOnlyElementException {

        ElementsFactory f   = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();

        /* Start node */
        InitialNode start = f.createInitialNodeInstance();
        start.setName("Start");
        mgr.addElement(start, activity);
        ActivityPartition firstLane = partitions.values().iterator().next();
        firstLane.getNode().add(start);
        ActivityNode prev = start;

        Map<String, StructuredActivityNode> mainMap = new HashMap<>();

        for (ActivityData d : rows) {
            ActionTypeChooser.ActionType type =
                    actionTypes.getOrDefault(
                            d.getName(), ActionTypeChooser.ActionType.STRUCTURED_ACTIVITY);

            String laneKey = (d.getActor()==null || d.getActor().trim().isEmpty())
                             ? "<Unassigned>" : d.getActor().trim();
            ActivityPartition lane = partitions.get(laneKey);

            if (d.isSubAction()) {
                StructuredActivityNode parent = mainMap.get(d.getParentName());
                if (parent == null) continue;   // orphan safety
                createSubAction(project, parent, d, type);
            } else {
                StructuredActivityNode main =
                        createMainAction(project, activity, d, type);
                if (lane != null) lane.getNode().add(main);
                mainMap.put(d.getName(), main);

                ControlFlow cf = f.createControlFlowInstance();
                cf.setSource(prev); cf.setTarget(main);
                mgr.addElement(cf, activity);
                prev = main;
            }
        }

        /* End node */
        ActivityFinalNode end = f.createActivityFinalNodeInstance();
        end.setName("End");
        mgr.addElement(end, activity);
        firstLane.getNode().add(end);

        ControlFlow last = f.createControlFlowInstance();
        last.setSource(prev); last.setTarget(end);
        mgr.addElement(last, activity);
    }

    /* ---------- helper factories (now with bodies) ------------------ */

    private StructuredActivityNode createMainAction(Project project, Activity act,
                                                    ActivityData d,
                                                    ActionTypeChooser.ActionType t)
            throws ReadOnlyElementException {
        ElementsFactory f = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        StructuredActivityNode n = f.createStructuredActivityNodeInstance();
        n.setName(d.getName());
        mgr.addElement(n, act);
        addPins(n, d, mgr, f);
        return n;
    }

    private ActivityNode createSubAction(Project project, ActivityNode parent,
                                         ActivityData d,
                                         ActionTypeChooser.ActionType t)
            throws ReadOnlyElementException {
        ElementsFactory f = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        ActivityNode n;
        if (t == ActionTypeChooser.ActionType.CALL_BEHAVIOR) {
            CallBehaviorAction c = f.createCallBehaviorActionInstance();
            c.setName(d.getName());
            mgr.addElement(c, parent);
            addPins(c, d, mgr, f);
            n = c;
        } else {
            StructuredActivityNode s = f.createStructuredActivityNodeInstance();
            s.setName(d.getName());
            mgr.addElement(s, parent);
            addPins(s, d, mgr, f);
            n = s;
        }
        return n;
    }

    // Add pins to StructuredActivity Nodes
    private void addPins(StructuredActivityNode n, ActivityData d,
                         ModelElementsManager mgr, ElementsFactory f)
            throws ReadOnlyElementException {
        for (String in : d.getInputs()) {
            InputPin p = f.createInputPinInstance();
            p.setName(in);
            mgr.addElement(p, n);
            n.getStructuredNodeInput().add(p);
        }
        for (String out : d.getOutputs()) {
            OutputPin p = f.createOutputPinInstance();
            p.setName(out);
            mgr.addElement(p, n);
            n.getStructuredNodeOutput().add(p);
        }
    }

    // Add pins to CallBehaviorActions
    private void addPins(CallBehaviorAction n, ActivityData d,
                         ModelElementsManager mgr, ElementsFactory f)
            throws ReadOnlyElementException {
        for (String in : d.getInputs()) {
            InputPin p = f.createInputPinInstance();
            p.setName(in);
            mgr.addElement(p, n);
            n.getArgument().add(p);
        }
        for (String out : d.getOutputs()) {
            OutputPin p = f.createOutputPinInstance();
            p.setName(out);
            mgr.addElement(p, n);
            n.getResult().add(p);
        }
    }

    /* ================================================================ */
    /*                       DIAGRAM HELPERS                            */
    /* ================================================================ */

    private DiagramPresentationElement createAndOpenDiagram(Project project,
                                                            Activity activity)
            throws ReadOnlyElementException {
        Diagram sem = ModelElementsManager.getInstance()
                           .createDiagram("SysML Activity Diagram", activity);
        sem.setName("Imported Activities from Excel");
        DiagramPresentationElement dpe = project.getDiagram(sem);
        dpe.open();
        return dpe;
    }

    /**
     * Create the vertical swim‑lanes, give them an initial location that’s not (0,0),
     * and populate both lane‑maps for easy access later.
     *
     * After the nodes are laid out we’ll call fitLaneShapesToContents(...) which
     * resizes (and can further reposition) the lane so it wraps all its actions.
     */
    private void createPartitionPresentations(DiagramPresentationElement dpe,
                                              Map<String, ActivityPartition> partitions)
            throws ReadOnlyElementException {

        PresentationElementsManager pem = PresentationElementsManager.getInstance();

        /* --- 1. Create the lane(s) -------------------------------------------- */
        List<ActivityPartition> vertical = new ArrayList<>(partitions.values());

        // createSwimlane returns a wrapper shape (the “swimlane set”); we ignore it
        pem.createSwimlane(Collections.emptyList(), vertical, dpe);

        /* --- 2. Find each lane shape & put it into both hash‑maps -------------- */
        for (Map.Entry<String, ActivityPartition> e : partitions.entrySet()) {
            String            actor = e.getKey();
            ActivityPartition part  = e.getValue();

            // Each partition has exactly one ShapeElement
            ShapeElement laneShape = (ShapeElement)
                    dpe.findPresentationElement(part, ShapeElement.class);

            if (laneShape == null) continue;          // very unusual – but be safe

            laneShapes.put(part, laneShape);          // old map (key = partition)
            laneShapeByActor.put(actor, laneShape);   // new map (key = actor name)

            /* --- 3. Give the lane a sensible starting rectangle --------------- *
             * We’ll drop it roughly at (150,70) so the GridLayouter’s default
             * left‑margin (100 px) puts nodes *inside* the lane instead of to
             * its right.  Width & height are just placeholders – the
             * fitLaneShapesToContents() method will correct them later.
             */
            Rectangle r = laneShape.getBounds();
            r.x = 150;       // shift right so it’s not flush against the Y‑axis
            r.y = 70;        // drop a little below the diagram header
            r.width  = 450;  // placeholder – enough for three wide actions
            r.height = 300;  // placeholder height
            pem.reshapeShapeElement(laneShape, r);
        }
    }


    /** Draw one symbol per ActivityNode, inside its lane if available. */
    private void populateDiagramNodes(Activity activity,
                                      DiagramPresentationElement dpe)
            throws ReadOnlyElementException {

        PresentationElementsManager pem = PresentationElementsManager.getInstance();

        for (ActivityNode node : activity.getNode()) {
            PresentationElement parentShape = null;
            for (Map.Entry<ActivityPartition, PresentationElement> e
                    : laneShapes.entrySet()) {
                if (e.getKey().getNode().contains(node)) {
                    parentShape = e.getValue();
                    break;
                }
            }
            pem.createShapeElement(node, parentShape != null ? parentShape : dpe);
        }
    }

    /** Resize & move every lane so it wraps the nodes that belong to its
     *  ActivityPartition, whether or not those nodes are set as graphical
     *  children of the lane shape. */
    private void fitLaneShapesToContents(DiagramPresentationElement dpe)
            throws ReadOnlyElementException {

        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        final int PADDING = 40;

        for (Map.Entry<ActivityPartition, PresentationElement> e : laneShapes.entrySet()) {
            ActivityPartition part = e.getKey();
            ShapeElement      lane = (ShapeElement) e.getValue();

            /* --- 1.  Find the bounding box of ALL node shapes in this partition -- */
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE,
                maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

            for (ActivityNode n : part.getNode()) {
                PresentationElement pe =
                        dpe.findPresentationElement(n, PresentationElement.class);
                if (pe instanceof ShapeElement s) {
                    Rectangle r = s.getBounds();
                    minX = Math.min(minX, r.x);
                    minY = Math.min(minY, r.y);
                    maxX = Math.max(maxX, r.x + r.width);
                    maxY = Math.max(maxY, r.y + r.height);
                }
            }
            /* Skip empty partitions (shouldn’t happen given your import rules) */
            if (minX == Integer.MIN_VALUE) continue;

            /* --- 2.  Build a new rectangle that wraps the nodes + padding -------- */
            Rectangle newRect = new Rectangle(
                    minX - PADDING,
                    minY - PADDING,
                    (maxX - minX) + PADDING * 2,
                    (maxY - minY) + PADDING * 2);

            /* --- 3.  Apply the resize/move to the lane shape --------------------- */
            pem.reshapeShapeElement(lane, newRect);
        }
    }

    private void populateDiagramPaths(Activity activity,
                                      DiagramPresentationElement dpe)
            throws ReadOnlyElementException {

        PresentationElementsManager pem = PresentationElementsManager.getInstance();
        for (Object edge : activity.getEdge()) {
            if (edge instanceof ControlFlow cf) {
                PresentationElement src =
                        dpe.findPresentationElement(cf.getSource(), PresentationElement.class);
                PresentationElement tgt =
                        dpe.findPresentationElement(cf.getTarget(), PresentationElement.class);
                if (src != null && tgt != null)
                    pem.createPathElement(cf, src, tgt);
            }
        }
    }
}
