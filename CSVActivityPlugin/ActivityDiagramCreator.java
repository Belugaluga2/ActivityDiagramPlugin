package com.example.csvactivityplugin;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OpaqueAction;
import com.nomagic.uml2.ext.magicdraw.actions.mdbasicactions.OutputPin;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ActivityFinalNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.ControlFlow;
import com.nomagic.uml2.ext.magicdraw.activities.mdbasicactivities.InitialNode;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.Activity;
import com.nomagic.uml2.ext.magicdraw.activities.mdfundamentalactivities.ActivityNode;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
// import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Namespace;
import com.nomagic.uml2.impl.ElementsFactory;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.magicdraw.uml.DiagramTypeConstants;

import java.util.List;

/**
 * Reads CSV rows, builds an Activity model, and creates
 * an Activity Diagram under a user-selected parent.
 */
public class ActivityDiagramCreator {

    /**
     * Main entry: runs in a Session, builds model & diagram, then closes or cancels.
     */
    public void createActivityDiagram(Project project, List<ActivityData> rows) throws Exception {
        SessionManager sm = SessionManager.getInstance();
        sm.createSession(project, "Import CSV Activities");
        try {
            Activity activity = createActivityElement(project);
            createActivityNodes(project, activity, rows);
            createAndOpenDiagram(project, activity, project);
            sm.closeSession(project);
        } catch (Exception e) {
            sm.cancelSession(project);
            throw e;
        }
    }

    /**
     * Instantiates a new Activity and adds it under the PrimaryModel.
     */
    private Activity createActivityElement(Project project) throws ReadOnlyElementException {
        ElementsFactory factory = project.getElementsFactory();
        Activity act = factory.createActivityInstance();
        act.setName("Imported Activities from CSV");
        // drop in the PrimaryModel temporarily
        ModelElementsManager.getInstance().addElement(act, project.getPrimaryModel());
        return act;
    }

    /**
     * Adds start/end nodes, an OpaqueAction per row, and ControlFlows between them.
     */
    private void createActivityNodes(Project project, Activity activity, List<ActivityData> rows) throws ReadOnlyElementException {
        ElementsFactory factory = project.getElementsFactory();
        ModelElementsManager mgr = ModelElementsManager.getInstance();

        InitialNode start = factory.createInitialNodeInstance();
        start.setName("Start");
        mgr.addElement(start, activity);

        ActivityFinalNode end = factory.createActivityFinalNodeInstance();
        end.setName("End");
        mgr.addElement(end, activity);

        ActivityNode prev = start;
        for (ActivityData r : rows) {
            OpaqueAction oa = factory.createOpaqueActionInstance();
            oa.setName(r.getName());
            if (!r.getDocumentation().isEmpty()) {
                ModelHelper.setComment(oa, r.getDocumentation());
            }
            mgr.addElement(oa, activity);
            for (String out : r.getOutputs()) {
                OutputPin pin = factory.createOutputPinInstance();
                pin.setName(out);
                mgr.addElement(pin, oa);
            }
            createControlFlow(factory, mgr, activity, prev, oa);
            prev = oa;
        }
        createControlFlow(factory, mgr, activity, prev, end);
    }

    private void createControlFlow(ElementsFactory f, ModelElementsManager mgr, Activity activity, ActivityNode src, ActivityNode tgt) throws ReadOnlyElementException {
        ControlFlow flow = f.createControlFlowInstance();
        flow.setSource(src);
        flow.setTarget(tgt);
        flow.setName("");
        mgr.addElement(flow, activity);
    }

    /**
     * Lets the user pick a parent, re-parents the Activity, creates the diagram, and opens it.
     */
    private void createAndOpenDiagram(Project project, Activity activity, Project prj) throws Exception {
        // 1) pick where in the model
        Element parent = DiagramParentChooser.chooseParent(project);
        if (parent == null) {
            throw new Exception("Diagram creation canceled – no parent selected.");
        }
        ModelElementsManager mgr = ModelElementsManager.getInstance();
        // 2) move under that parent
        try {
            if (activity.getOwner() != parent) {
                mgr.moveElement(activity, parent);
            }
        } catch (ReadOnlyElementException roe) {
            throw new Exception("Cannot move activity – target is read-only.", roe);
        }

        // 3) create the diagram owned by the Activity
        Diagram diagram = mgr.createDiagram(
            DiagramTypeConstants.UML_ACTIVITY_DIAGRAM,
            activity
        );
        diagram.setName("CSV Imported Activity Diagram");

        // 4) open via DiagramPresentationElement
        DiagramPresentationElement dpe = project.getDiagram(diagram);
        if (dpe != null) {
            dpe.open();
        }
    }
}
