package moa.classifiers.meta;
import com.github.javacliparser.FlagOption;
import com.github.javacliparser.IntOption;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import com.yahoo.labs.samoa.instances.Instance;
import moa.capabilities.CapabilitiesHandler;
import moa.classifiers.AbstractClassifier;
import moa.classifiers.Classifier;
import moa.classifiers.MultiClassClassifier;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NominalAttributeMultiwayTest;
import moa.classifiers.core.conditionaltests.NumericAttributeBinaryTest;
import moa.classifiers.rules.core.conditionaltests.NumericAttributeBinaryRulePredicate;
import moa.classifiers.trees.HoeffdingAdaptiveTree;
import moa.classifiers.trees.HoeffdingTree;
import moa.core.Measurement;
import moa.options.ClassOption;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

public class TreeVisualizer extends AbstractClassifier implements MultiClassClassifier, CapabilitiesHandler {
    //<======== ESTRUTURAL =======>
    // Janela o TreeVisualizer
    protected JFrame treeViewFrame = new JFrame("TreeView");
    // Painel do breadcrumb
    protected JPanel treeViewLeftPanel = new JPanel();
    // Painel do meio
    protected JPanel treeViewRightPanel = new JPanel();
    // Painel das relevâncias dos atributos
    protected JPanel treeViewAttrsRelevance = new JPanel();
    //<======== CONTEÜDO =======>
    // Conteúdo da árvore
    protected JPanel treeViewPanel = new JPanel();
    // Conteúdo do breadcrumb
    public JTree treesBreadcrumb = new JTree(new DefaultMutableTreeNode("loading..."));
    // Conteúdo detalhes do node selecionado
    public JPanel selectedNodeDetailPanel = new JPanel();
    // Conteúdo Slider
    private JPanel sliderPanel = new JPanel();
    // Conteúdo importância dos atributos
    private JPanel attrsImportancePanel = new JPanel();
    //<======== INFOS =======>
    //Quantity of instances processed
    public int instancias = 0;
    // Header da instância sendo processada
    Map<Integer, String> instancesHeader = new HashMap<>();
    // Available trees roots
    protected List<HoeffdingTree.Node> treesRoots = new ArrayList<>();
    // Selected tree in breadcrumb
    public int activeBreadcrumbTree = -1;
    // Snapshot selecionada no slider
    public int activeTreeSnapshot = 0;
    /*
     * Trees snapshot structure
     * For each tree (List1 of trees)
     * There is a List2
     * of multiples snapshots (Lists3 of nodes)
     * */
    public List<List<List<FakeNode>>> treesSnapshots = new ArrayList<>();
    // Attributes importance snapshots structure
    public List<List<double[]>> attrsImportanceSnapshots = new ArrayList<>();
    //<======== DEBUG =======>
    static JLabel sliderTracker = new JLabel();
    static JTextArea lastExpectedSnapshot = new JTextArea("Oi");
    public String treesConfirmSnapshots;

    public ClassOption treeLearnerOption = new ClassOption("treeLearner", 'l',
            "Hoeffding Tree.", Classifier.class,
            "trees.HoeffdingTree");

    public IntOption qttToSnapshot = new IntOption(
            "minNumInstances",
            'i',
            "Quantity of instances to snapshot.",
            10000, 1, Integer.MAX_VALUE);

    public FlagOption visualizeTree = new FlagOption("visualizeTree", 't',
            "Should visually track the trees?");

    private Classifier arvore = null;

    @Override
    public double[] getVotesForInstance(Instance inst) {
        return arvore.getVotesForInstance(inst);
    }

    /**
     *  Get available roots in *- this.arvore -*
     *  from the roots, it will be generated the snapshots
     */
    public void getTreesRoots() {
        int qtt = 0;
        if(this.treesRoots != null){
            qtt = this.treesRoots.size();
        }
        this.treesRoots = new ArrayList<>();
        if (this.arvore instanceof HoeffdingTree) {
            this.treesRoots.add(((HoeffdingTree) this.arvore).getTreeRoot());
        } else if (this.arvore instanceof HoeffdingAdaptiveTree) {
            this.treesRoots.add(((HoeffdingAdaptiveTree) this.arvore).getTreeRoot());
        } else if (this.arvore instanceof AdaptiveRandomForest) {
            for (int i = 0; i < ((AdaptiveRandomForest) this.arvore).ensemble.length; i++) {
                this.treesRoots.add(((AdaptiveRandomForest) this.arvore).ensemble[i].classifier.getTreeRoot());
            }
        }
        //If the quantity of trees changed, update the breadcrumb to select which one to watch
        if(qtt != this.treesRoots.size()){
            renderTreesBreadcrumb();
        }
    }

    @Override
    public void resetLearningImpl() {
        arvore = (Classifier) getPreparedClassOption(this.treeLearnerOption);
        arvore.resetLearning();
    }

    /**
     * Return the type of a Hoeffding Tree Node as a type of a Snapshot Node (FakeNode)
     * @param node
     * @return
     */
    private FakeNodeType getNodeType(HoeffdingTree.Node node){
        if (node instanceof HoeffdingTree.LearningNode) {
            return FakeNodeType.LEARNINGNODE;
        } else if (node instanceof HoeffdingTree.LearningNodeNB) {
            return FakeNodeType.LEARNINGNODENB;
        } else if (node instanceof HoeffdingTree.LearningNodeNBAdaptive) {
            return FakeNodeType.LEARNINGNODENBADAPTIVE;
        } else if (node instanceof HoeffdingTree.SplitNode) {
            return FakeNodeType.SPLITNODE;
        } else {
            return FakeNodeType.UNKNOWN;
        }
    }

    /**
     * Create Tree Snapshots based on Tree Roots
     */
    private void createTreesSnapshots(){
        if(!treesRoots.isEmpty()) {
            //Initialize array to keep each tree multiple snapshots
            if(treesSnapshots.isEmpty()){
                for (int i = 0; i < treesRoots.size(); i++) {
                    treesSnapshots.add(new ArrayList<>());
                    //treesConfirmSnapshots.add(new ArrayList<>());
                }
            }
            if (this.arvore instanceof HoeffdingTree) {
                StringBuilder handler = new StringBuilder();
                ((HoeffdingTree) this.arvore).getModelDescription(handler, 0);
                this.treesConfirmSnapshots = handler.toString();
            }
            //generate one snapshot for each tree
            for (int i = 0; i < treesRoots.size(); i++) {
                List<FakeNode> treeSnapshot = new ArrayList<>();
                List<HoeffdingTree.Node> nodesToRead = new ArrayList<>();
                nodesToRead.add(treesRoots.get(i));
                //find all available nodes of a tree
                int nodeId = 0;
                while(!nodesToRead.isEmpty()) {
                    //if it is a split node, it has childrens
                    FakeNode currentNode = null;
                    if (nodesToRead.get(0) instanceof HoeffdingTree.SplitNode) {
                        int attrIndex = -1;
                        InstanceConditionalTest a = ((HoeffdingTree.SplitNode) nodesToRead.get(0)).splitTest;
                        String teste = "";
                        if(a instanceof NumericAttributeBinaryTest){
                            NumericAttributeBinaryTest n = (NumericAttributeBinaryTest) a;
                            attrIndex = n.attIndex;
                            teste = String.valueOf(((NumericAttributeBinaryTest) a).getSplitValue());
                        } else if(a instanceof NominalAttributeBinaryTest){
                            NominalAttributeBinaryTest n = (NominalAttributeBinaryTest) a;
                            attrIndex = n.attIndex;
                            teste = "NominalMOAInternal" + ((NominalAttributeBinaryTest) a).getAttValue();
                        } else if(a instanceof NumericAttributeBinaryRulePredicate){
                            NumericAttributeBinaryRulePredicate n = (NumericAttributeBinaryRulePredicate) a;
                            attrIndex = n.attIndex;
                            teste = String.valueOf(((NumericAttributeBinaryRulePredicate) a).getSplitValue());
                        } else if(a instanceof NominalAttributeMultiwayTest){
                            NominalAttributeMultiwayTest n = (NominalAttributeMultiwayTest) a;
                            attrIndex = n.attIndex;
                            teste = "Nominal Attribute Multiway Test";
                        }
                        //Não é possível renderizar árvores multiway
                        currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)), instancesHeader.get(attrIndex), teste);
                        //Add all childrens to nodesToRead
                        for(int j = 0; j < ((HoeffdingTree.SplitNode) nodesToRead.get(0)).numChildren(); j++){
                            currentNode.addChildrenId(nodesToRead.size() + nodeId);
                            nodesToRead.add(((HoeffdingTree.SplitNode) nodesToRead.get(0)).getChild(j));
                        }
                    } else {
                        if (nodesToRead.get(0) instanceof HoeffdingTree.LearningNode){
                            StringBuilder handler = new StringBuilder("");
                            double[] classesValues = nodesToRead.get(0).getObservedClassDistribution();
                            if(classesValues.length == 1){
                                currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)), "", "{" + String.valueOf(classesValues[0]) + "}");
                            }else {
                                if (classesValues.length == 2) {
                                    String valor = classesValues[0] > classesValues[1] ? "Class 1" : "Class 2";
                                    currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)), valor, "{" + String.valueOf(classesValues[0]) + " | " + classesValues[1] + "}");
                                } else {
                                    String multipleClassesResults = Arrays.toString(classesValues);
                                    currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)), "Class Undefined", multipleClassesResults);
                                }
                            }
                        } else {
                            currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)));
                        }
                    }
                    //add the current node in snapshot tree
                    treeSnapshot.add(currentNode);
                    //remove the read node from nodes to be created in snapshot tree
                    nodesToRead.remove(0);
                    nodeId = nodeId + 1;
                }

                //re run every single node, adding the children objects into their parents
                //based on the childrens ids
                for(int k = 0; k < treeSnapshot.size(); k++){
                    List<Integer> childrensToAdd = treeSnapshot.get(k).getChildrensIds();
                    //add each children as a real children
                    if(childrensToAdd.size() > 0){
                        for(int l = 0; l < childrensToAdd.size(); l++){
                            if(treeSnapshot.get(k).getFakeNodeType() != FakeNodeType.SPLITNODE){

                            } else {
                                treeSnapshot.get(k).addChildren(treeSnapshot.get(childrensToAdd.get(l)));
                            }
                        }
                    }
                }
                //If the slider is in the last position, update the slider, else keep the state the user wants to
                if(treesSnapshots.size() == activeTreeSnapshot){
                    activeTreeSnapshot = activeTreeSnapshot + 1;
                }
                treesSnapshots.get(i).add(treeSnapshot);
            }
            //Doesnt re-render the tree if the user is not looking to the latest state of it
            if(treesSnapshots.size() == activeTreeSnapshot){
                renderTreeViewPanel();
            }
        }
    };

    /**
    * Create Snapshots to be shown in attributes importance graphic
    */
    public void createAttrsImportanceSnapshots(){
        if(!treesRoots.isEmpty()) {
            //Initialize array to keep each tree multiple snapshots
            if(attrsImportanceSnapshots.isEmpty()){
                for (int i = 0; i < treesRoots.size(); i++) {
                    attrsImportanceSnapshots.add(new ArrayList<>());
                }
            }
            //generate one snapshot for each tree
            for (int i = 0; i < treesRoots.size(); i++) {
                if(this.arvore instanceof HoeffdingTree){
                    double[] featuresScores = ((HoeffdingTree) this.arvore).getFeatureScores();
                    attrsImportanceSnapshots.get(i).add(featuresScores);
                }

            }
        }
    }

    /**
    * This is the Frame lifecycle controller
    */
    @Override
    public void trainOnInstanceImpl(Instance inst) {
        if(visualizeTree.getStateString() == "true"){
            if (instancias == 0) {
                //Windows for tree visualization
                for(String attr : inst.getAttributesList().keySet()){
                    this.instancesHeader.put(inst.getAttributesList().get(attr), attr);
                }
                renderTreeFrame();
            }
            instancias = instancias + 1;
        }

        arvore.trainOnInstance(inst);
        if(visualizeTree.getStateString() == "true"){
            getTreesRoots();
            if (instancias%qttToSnapshot.getValue() == 0) {
                createTreesSnapshots();
                createAttrsImportanceSnapshots();
                renderTreesBreadcrumb();
                createAttrsImportanceSnapshots();
                createLastExpectedSnapshot();
                if(activeBreadcrumbTree != -1){
                    renderSlider();
                }
            }
        }
    }

    /**
     * Return the MOA Tree description of the last available snapshot, for validation purpose.
     */
    private void createLastExpectedSnapshot(){
        if (this.arvore instanceof HoeffdingTree) {
            StringBuilder aux = new StringBuilder();
            ((HoeffdingTree) this.arvore).getModelDescription(aux, 0);
            lastExpectedSnapshot.setText(aux.toString());
        }
    }

    /**
     * Render the slider for snapshots
     */
    private void renderSlider(){
        activeTreeSnapshot = this.treesSnapshots.get(this.activeBreadcrumbTree).size()-1;
        JSlider slider = new JSlider(JSlider.HORIZONTAL,
                0, activeTreeSnapshot, activeTreeSnapshot);

        renderAttrsPanel();
        //Turn on labels at major tick marks.
        slider.setMajorTickSpacing(20);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        
        Font font = new Font("Serif", Font.ITALIC, 15);
        slider.setFont(font);
        slider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                JSlider source = (JSlider)e.getSource();
                if (!source.getValueIsAdjusting()) {
                    int snapshotToVisualize = (int)source.getValue();
                    activeTreeSnapshot = snapshotToVisualize;
                    renderTreeViewPanel();
                    renderAttrsPanel();
                }
            }
        });
        sliderPanel.removeAll();
        sliderPanel.setLayout(new GridLayout(1, 1));
        JPanel aligner = new JPanel();
        aligner.setLayout(new BoxLayout(aligner, BoxLayout.X_AXIS));
        aligner.setAlignmentX(Component.CENTER_ALIGNMENT);
        aligner.add(slider);
        sliderPanel.add(aligner);
        sliderPanel.revalidate();
        sliderPanel.repaint();
    }

    /**
     * Render the attributes Panel
     */
    private void renderAttrsPanel(){
        List<JLabel> attrs = new ArrayList<>();
        attrsImportancePanel.removeAll();
        // Create chart dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        double[] importances = attrsImportanceSnapshots.get(activeBreadcrumbTree).get(activeTreeSnapshot);
        for(int i = 0; i < importances.length; i++){
            dataset.addValue(importances[i], this.instancesHeader.get(i), this.instancesHeader.get(i));
        }
        //Create chart
        JFreeChart chart=ChartFactory.createBarChart(
                "", //Chart Title
                "Attributes", // Category axis
                "Importance", // Value axis
                dataset,
                PlotOrientation.HORIZONTAL,
                true,true,false
        );
        ChartPanel chartPanel = new ChartPanel(chart);
        attrsImportancePanel.add(chartPanel);
        attrsImportancePanel.revalidate();
        attrsImportancePanel.repaint();
    }

    @Override
    protected Measurement[] getModelMeasurementsImpl() {
        return arvore.getModelMeasurements();
    }

    @Override
    public void getModelDescription(StringBuilder out, int indent) {

    }

    @Override
    public boolean isRandomizable() {
        return false;
    }

    /**
     * Render Graphical Tree
     */
    public void renderTreeViewPanel() {
        mxGraph graph = new mxGraph();
        Object parent = graph.getDefaultParent();

        graph.getModel().beginUpdate();
        try{
            int x = 20;
            int y = 20;
            List<FakeNode> lastSnapshot = this.treesSnapshots.get(this.activeBreadcrumbTree).get(this.activeTreeSnapshot);

            List<Object> vertexList = new ArrayList<>();
            try {
                for (int i = 0; i < lastSnapshot.size(); i++) {
                    DecimalFormat formatter = new DecimalFormat("###.##");
                    String description = lastSnapshot.get(i).getName();
                    //get test description
                    if(lastSnapshot.get(i).getFakeNodeType() == FakeNodeType.SPLITNODE){
                        String pseudoValue = lastSnapshot.get(i).getValue();
                        if(pseudoValue.contains("NominalMOAInternal")){
                            try{
                                String[] testing = pseudoValue.split("NominalMOAInternal");
                                description = description + " == " + testing[1];
                            }catch(Exception e){
                                System.out.println("q1?: " + pseudoValue);
                            }
                        } else {
                            description = description + " <= " + formatter.format(Double.parseDouble(pseudoValue));
                        }
                    }
                    if(lastSnapshot.get(i).getFakeNodeType() == FakeNodeType.LEARNINGNODE){
                        String valuesAux = lastSnapshot.get(i).getValue();
                        if(valuesAux.contains(" | ")){
                            String[] values = valuesAux.split(" | ");
                            if (values.length == 3) {
                                values[0] = values[0].substring(1);
                                values[2] = values[2].substring(0, values[2].length() - 1);
                                if(!values[0].isEmpty()){
                                    description = description + "\nClass 1: " + formatter.format(Double.parseDouble(values[0]));
                                }
                                if(!values[2].isEmpty()) {
                                    description = description + "\nClass 2: " + formatter.format(Double.parseDouble(values[2]));
                                }
                            }
                        }else {
                            if(valuesAux.contains(",")){
                                description = valuesAux;
                            } else {
                                String valueFinal = valuesAux;
                                if (!valueFinal.isEmpty()) {
                                    valueFinal = valueFinal.substring(1);
                                    valueFinal = valueFinal.substring(0, valueFinal.length() - 1);
                                    if (!valueFinal.isEmpty()) {
                                        description = description + "Class 1: " + formatter.format(Double.parseDouble(valueFinal));
                                    }
                                }
                            }
                        }
                    }
                    if(lastSnapshot.get(i).getFakeNodeType() == FakeNodeType.LEARNINGNODENB){
                        System.out.println("Tratar");
                    }
                    if(lastSnapshot.get(i).getFakeNodeType() == FakeNodeType.LEARNING){
                        System.out.println("Tratar");
                    }
                    if(lastSnapshot.get(i).getFakeNodeType() == FakeNodeType.LEARNINGNODENBADAPTIVE){
                        System.out.println("Tratar");
                    }
                    description = description + "\n" + lastSnapshot.get(i).getFakeNodeType();
                    Object tempVertex = graph.insertVertex(parent, null, description, x, y, 90, 70, "ROUNDED");
                    vertexList.add(tempVertex);
                    if (i % 2 == 0) {
                        x = x + 140;
                    } else {
                        y = y + 60;
                    }
                }
            }catch(Exception e){
                System.out.println("Erro ao gerar vértices: " + e);
            }
            //make edges
            //for each vertex
            try{
                for(int j = 0; j < lastSnapshot.size(); j++){
                    //get all of their childrens id (pos in snapshot == pos in vertexList)
                    for(int k = 0; k < lastSnapshot.get(j).getChildrensIds().size(); k++){
                        graph.insertEdge(parent, null, "", vertexList.get(j), vertexList.get(lastSnapshot.get(j).getChildrens().get(k).getId()));
                    }
                }
                mxIGraphLayout layout = new mxCompactTreeLayout(graph);
                layout.execute(parent);
            }catch(Exception e) {
                System.out.println("Erro ao gerar edges: " + e);
            }
        }catch(Exception e){
            System.out.println("Erro ao renderizar a parte gráfica da árvore: " + e);
        }finally{
            graph.setCellsMovable(false);
            graph.setCellsBendable(false);
            graph.setCellsDeletable(false);
            graph.setCellsEditable(false);
            graph.setCellsSelectable(false);
            graph.setCellsCloneable(false);
            graph.setCellsDisconnectable(false);
            graph.setCellsResizable(false);
            graph.getModel().endUpdate();
        }

        mxGraphComponent graphView = new mxGraphComponent(graph);
        treeViewPanel.removeAll();
        treeViewPanel.add(graphView);
        treeViewPanel.revalidate();
        treeViewPanel.repaint();
    }

    /**
     * Render Breadcrumb
     */
    private void renderTreesBreadcrumb(){
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        for(int i = 0; i < this.treesRoots.size(); i++){
            root.add(new DefaultMutableTreeNode("Árvore " + Integer.toString(i)));
        }
        JTree breadcrumb = new JTree(root);
        breadcrumb.setScrollsOnExpand(true);
        breadcrumb.setRootVisible(false);
        breadcrumb.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) breadcrumb.getLastSelectedPathComponent();
                activeBreadcrumbTree = Integer.parseInt(selectedNode.getUserObject().toString().replaceAll("\\D+",""));
                renderSlider();
                renderTreeViewPanel();

            }
        });
        treeViewLeftPanel.removeAll();
        treeViewLeftPanel.add(breadcrumb);
        treeViewLeftPanel.revalidate();
        treeViewLeftPanel.repaint();
    }

    /**
     * Render window
     */
    private void renderTreeFrame() {
        //Create new window
        treeViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        //Window
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.X_AXIS));
        treeViewFrame.setPreferredSize(new Dimension(1920, 1080));
        treeViewFrame.getContentPane().add(content);
        Box boxes[] = new Box[3];
        boxes[0] = Box.createHorizontalBox();
        boxes[1] = Box.createHorizontalBox();
        boxes[2] = Box.createHorizontalBox();
        boxes[0].createGlue();
        boxes[1].createGlue();
        boxes[2].createGlue();
        content.add(boxes[0]);
        content.add(boxes[1]);
        content.add(boxes[2]);
        //Config left Window component
        treeViewLeftPanel.setPreferredSize(new Dimension(100,1080));
        //treeViewLeftPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        treeViewLeftPanel.add(treesBreadcrumb);
        boxes[0].add(treeViewLeftPanel);
        //Config right Window component
        treeViewRightPanel.setPreferredSize(new Dimension(1120,1080));
        boxes[1].add(treeViewRightPanel);
        Box contentBoxes[] = new Box[2];
        contentBoxes[0] = Box.createHorizontalBox();
        contentBoxes[1] = Box.createHorizontalBox();
        contentBoxes[0].createGlue();
        contentBoxes[1].createGlue();
        treeViewRightPanel.add(contentBoxes[0]);
        treeViewRightPanel.add(contentBoxes[1]);

        //Tree visualizer
        JScrollPane treeScroller = new JScrollPane(treeViewPanel);
        treeScroller.setPreferredSize(new Dimension(1120, 900));
        contentBoxes[0].add(treeScroller);

        //Slider
        JScrollPane sliderScroller = new JScrollPane(sliderPanel);
        sliderScroller.setPreferredSize(new Dimension(1120, 100));
        contentBoxes[1].add(sliderScroller);

        //Attrs importance
        treeViewAttrsRelevance.setPreferredSize(new Dimension(700,1080));
        boxes[2].add(treeViewAttrsRelevance);

        Box detailsBoxes[] = new Box[2];
        detailsBoxes[0] = Box.createHorizontalBox();
        detailsBoxes[0].createGlue();
        treeViewAttrsRelevance.add(detailsBoxes[0]);
        attrsImportancePanel.setPreferredSize(new Dimension(800, 800));
        detailsBoxes[0].add(attrsImportancePanel);

        // Display the windows
        treeViewFrame.pack();
        treeViewFrame.setLocationByPlatform(true);
        treeViewFrame.setVisible(true);
    }
}

/**
 * Snapshot Nodes Types
 */
enum FakeNodeType {
    LEARNING,
    LEARNINGNODE,
    LEARNINGNODENB,
    LEARNINGNODENBADAPTIVE,
    SPLITNODE,
    UNKNOWN
}

/**
 * Snapshot Node Structure
 */
class FakeNode {
    private int id;
    private String name;
    //Value is the attribute index in the instance
    private String value;
    private FakeNode parent;
    private List<FakeNode> childrens = new ArrayList<>();
    private List<Integer> childrensIds = new ArrayList<>();
    private FakeNodeType type;

    FakeNode(int id, FakeNodeType type){
        this.id = id;
        this.type = type;
    }

    FakeNode(int id, FakeNodeType type, String name, String value){
        this.id = id;
        this.type = type;
        this.name = name;
        this.value = value;
    }

    public boolean isRoot(){
        if(parent == null){
            return true;
        }
        return false;
    }

    public FakeNodeType getFakeNodeType(){
        return this.type;
    }

    public List<FakeNode> getChildrens(){
        return this.childrens;
    }

    public int getId(){
        return this.id;
    }

    public String getName(){
        return this.name;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getValue(){
        return this.value;
    }

    public List<Integer> getChildrensIds(){
        return this.childrensIds;
    }

    public void addChildrenId(int newChildrenId){
        this.childrensIds.add(newChildrenId);
    }

    public void addChildren(FakeNode newChildren){
        if(newChildren != null && this.childrens != null){
            this.childrens.add(newChildren);
        }
    }
}