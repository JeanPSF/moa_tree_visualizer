package moa.classifiers.meta;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.IntOption;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import com.yahoo.labs.samoa.instances.Instance;
import moa.MOAObject;
import moa.capabilities.CapabilitiesHandler;
import moa.classifiers.AbstractClassifier;
import moa.classifiers.Classifier;
import moa.classifiers.MultiClassClassifier;
import moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import moa.classifiers.core.conditionaltests.NominalAttributeBinaryTest;
import moa.classifiers.core.conditionaltests.NominalAttributeMultiwayTest;
import moa.classifiers.core.conditionaltests.NumericAttributeBinaryTest;
import moa.classifiers.rules.core.conditionaltests.NumericAttributeBinaryRulePredicate;
import moa.classifiers.trees.ARFHoeffdingTree;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;
import java.text.AttributedCharacterIterator;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.CategoryDataset;
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
                //treesConfirmSnapshots.get(i).add(result.toString());
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
                            teste = "Numeric Attribute Binary test";
                        } else if(a instanceof NumericAttributeBinaryRulePredicate){
                            NumericAttributeBinaryRulePredicate n = (NumericAttributeBinaryRulePredicate) a;
                            attrIndex = n.attIndex;
                            teste = String.valueOf(((NumericAttributeBinaryRulePredicate) a).getSplitValue());
                        } else if(a instanceof NominalAttributeMultiwayTest){
                            NominalAttributeMultiwayTest n = (NominalAttributeMultiwayTest) a;
                            attrIndex = n.attIndex;
                            teste = "Nominal Attribute Multiway Test";
                        }
                        //Algo de ERROR aqui - não é possível renderizar árvores multiway
                        currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)), instancesHeader.get(attrIndex), teste);
                        //add all childrens to nodesToRead
                        for(int j = 0; j < ((HoeffdingTree.SplitNode) nodesToRead.get(0)).numChildren(); j++){
                            currentNode.addChildrenId(nodesToRead.size() + nodeId);
                            nodesToRead.add(((HoeffdingTree.SplitNode) nodesToRead.get(0)).getChild(j));
                        }
                    } else {
                        if (nodesToRead.get(0) instanceof HoeffdingTree.LearningNode){
                            StringBuilder handler = new StringBuilder("");
                            //nodesToRead.get(0).getDescription(handler, 0);
                            double[] classesValues = nodesToRead.get(0).getObservedClassDistribution();
                            if(classesValues.length < 2){
                                //não possui 2 classes representadas no nó de aprendizagem
                                System.out.println("Erro aqui!");
                            }
                            if(classesValues.length == 2) {
                                String valor = classesValues[0] > classesValues[1] ? "Class 1" : "Class 2";
                                currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)), valor, "{" + String.valueOf(classesValues[0]) + " | " + classesValues[1] + "}");
                            } else {
                                currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)), "Class Undefined", "{" + String.valueOf(classesValues[0]) +  "}");
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
            //Doesnt render the tree if the user is not looking to the latest state of it
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
                    /*for(int k = 0; k < featuresScores.length; k++){
                        if(featuresScores.length != k-1){
                            System.out.print(" - " + featuresScores[k]);
                        } else {
                            System.out.println(" - " + featuresScores[k]);
                        }
                    }
                    System.out.println(this.instancesHeader);*/
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
/*            if(instancias == inst.getAttributesList().size()){
                System.out.println("última instância.");
            }*/
                //Ou, quantidade de instancias == insts.size()
                //Aqui estou recebendo apenas uma isntância, quanto é a quantidade máxima?
                //Em uso real, seria necessário calcular via Window (Hoeffding Limit)
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

        /*Hashtable labelTable = new Hashtable();
        for(int i = 0; i < this.treesSnapshots.size(); i++){
            labelTable.put(i, new JLabel(Integer.toString(i)) );
        }
        slider.setLabelTable( labelTable );*/
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
                    //sliderTracker.setText("Snapshot selected: " + snapshotToVisualize + ".");
                    //System.out.println("Available snapshots: " + treesSnapshots.get(activeBreadcrumbTree).size());
                    //System.out.println("Snapshot selected description: " + treesSnapshots.get(activeBreadcrumbTree).get(snapshotToVisualize));
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
        //sliderPanel.add(sliderTracker);
        sliderPanel.revalidate();
        sliderPanel.repaint();
    }

    /**
     * Render the attributes Panel
     */
    private void renderAttrsPanel(){
        //System.out.println("Slider snapshot value form active tree: " + treeSnapshotIndex + ".");
        List<JLabel> attrs = new ArrayList<>();

        attrsImportancePanel.removeAll();
        // Create chart dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        //attrsImportancePanel.add(new JLabel("Importâncias dos atributos do slider na posição: " + Integer.toString(activeTreeSnapshot)));
        double[] importances = attrsImportanceSnapshots.get(activeBreadcrumbTree).get(activeTreeSnapshot);
        for(int i = 0; i < importances.length; i++){
            //System.out.println("Loop index: " + i + ". Instance header: " + this.instancesHeader.get(i) + ". Importance: " + Double.toString(importances[i]) + ".");
            //JLabel label = new JLabel(this.instancesHeader.get(i) + ": " + Double.toString(importances[i]));
            //attrsImportancePanel.add(label);
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
            //Árvore ativa para visualização
            //this.treesSnapshots.get(this.activeBreadcrumbTree)
            //Qntd de snapshots dessa árvore
            //this.treesSnapshots.get(this.activeBreadcrumbTree).size()
            //Apresentar a última snapshop disponível
            //this.treesSnapshots.get(this.activeBreadcrumbTree).get(this.treesSnapshots.get(this.activeBreadcrumbTree).size() - 1)
            //List<FakeNode> lastSnapshot = this.treesSnapshots.get(this.activeBreadcrumbTree).get(this.treesSnapshots.get(this.activeBreadcrumbTree).size() - 1);
            //Ao inves de apresentar a última snapshot possível, apresentar o estado ativo do slider
            List<FakeNode> lastSnapshot = this.treesSnapshots.get(this.activeBreadcrumbTree).get(this.activeTreeSnapshot);

            List<Object> vertexList = new ArrayList<>();
            try {
                for (int i = 0; i < lastSnapshot.size(); i++) {
                    DecimalFormat formatter = new DecimalFormat("###.##");
                    String description = lastSnapshot.get(i).getName() + "\n" + lastSnapshot.get(i).getFakeNodeType();
                    //get test description
                    if(lastSnapshot.get(i).getFakeNodeType() == FakeNodeType.SPLITNODE){
                        description = description + "\nTest: <=" + formatter.format(Double.parseDouble(lastSnapshot.get(i).getValue()));
                    }
                    if(lastSnapshot.get(i).getFakeNodeType() == FakeNodeType.LEARNINGNODE){
                        String[] values = lastSnapshot.get(i).getValue().split(" | ");
                        values[0] = values[0].substring(1);
                        values[2] = values[2].substring(0, values[2].length()-1);
                        description = description + "\nClass 1: " + formatter.format(Double.parseDouble(values[0]));
                        description = description + "\nClass 2: " + formatter.format(Double.parseDouble(values[2]));
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
                    //String nodeContent = lastSnapshot.get(i).getName() + "\n" + lastSnapshot.get(i).getFakeNodeType();
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
        //Onde node click
        mxGraphComponent graphView = new mxGraphComponent(graph);
        //To able the click, setCellsSelectable should be true
        /*graphView.getGraphControl().addMouseListener(new MouseAdapter(){
            @Override
            public void mouseReleased(MouseEvent e) {
                //Update selected node detail
                mxCell cell = (mxCell) graphView.getCellAt(e.getX(), e.getY());
                if(cell != null)
                {
                    List<FakeNode> lastSnapshot = treesSnapshots.get(activeBreadcrumbTree).get(treesSnapshots.get(activeBreadcrumbTree).size() - 1);
                    FakeNode activeNode = lastSnapshot.get(Integer.parseInt(cell.getId()) - 2);
                    //System.out.println(activeNode);
                    JPanel newNodeInfo = new JPanel();
                    newNodeInfo.setLayout(new GridLayout(6+ (activeNode.getChildrens().size() * 2), 2));
                    newNodeInfo.add(new JLabel("Label: "));
                    newNodeInfo.add(new JLabel(activeNode.getName()));
                    newNodeInfo.add(new JLabel("Teste: "));
                    newNodeInfo.add(new JLabel(activeNode.getValue()));
                    newNodeInfo.add(new JLabel("Childrens: "));
                    newNodeInfo.add(new JLabel(""));
                    List<FakeNode> childrens = activeNode.getChildrens();
                    for(int j = 0; j < childrens.size(); j++){
                        newNodeInfo.add(new JLabel("Label: "));
                        newNodeInfo.add(new JLabel(childrens.get(j).getName()));
                        newNodeInfo.add(new JLabel("Teste: "));
                        newNodeInfo.add(new JLabel(childrens.get(j).getValue()));
                    }
                    selectedNodeDetailPanel.removeAll();
                    selectedNodeDetailPanel.add(newNodeInfo);
                    selectedNodeDetailPanel.revalidate();
                    selectedNodeDetailPanel.repaint();
                }
            }
        });*/

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
                //System.out.println("selecting breadcrumb: " + selectedNode.getUserObject().toString() + " : " + selectedNode.getUserObject().toString().replaceAll("\\D+",""));
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
        //treeViewFrame = new JFrame("treeViewFrame");
        treeViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        //Window
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.X_AXIS));
        treeViewFrame.setPreferredSize(new Dimension(1920, 1080));
        //content.setBackground(Color.BLUE);
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
        //treeViewFrame.setLayout(new GridLayout(1, 3));
        //Config left Window component
        treeViewLeftPanel.setPreferredSize(new Dimension(100,1080));
        //treeViewLeftPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        treeViewLeftPanel.add(treesBreadcrumb);
        boxes[0].add(treeViewLeftPanel);
        //Config right Window component
        //treeViewRightPanel = new JPanel();
        //treeViewRightPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        //treeViewRightPanel.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        treeViewRightPanel.setPreferredSize(new Dimension(1120,1080));
        //treeViewRightPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        //treeViewRightPanel.setBackground(Color.GREEN);
        boxes[1].add(treeViewRightPanel);

        Box contentBoxes[] = new Box[2];
        contentBoxes[0] = Box.createHorizontalBox();
        contentBoxes[1] = Box.createHorizontalBox();
        //contentBoxes[2] = Box.createHorizontalBox();
        contentBoxes[0].createGlue();
        contentBoxes[1].createGlue();
        //[2].createGlue();
        treeViewRightPanel.add(contentBoxes[0]);
        treeViewRightPanel.add(contentBoxes[1]);
        //treeViewRightPanel.add(contentBoxes[2]);

        //Tree visualizer
        JScrollPane treeScroller = new JScrollPane(treeViewPanel);
        treeScroller.setPreferredSize(new Dimension(1120, 900));
        contentBoxes[0].add(treeScroller);

        //Slider
        JScrollPane sliderScroller = new JScrollPane(sliderPanel);
        sliderScroller.setPreferredSize(new Dimension(1120, 100));
        contentBoxes[1].add(sliderScroller);

        //Detail
        /*JScrollPane detailScroller = new JScrollPane(selectedNodeDetailPanel);
        detailScroller.setPreferredSize(new Dimension(1020, 180));
        contentBoxes[2].add(detailScroller);*/

        //Attrs importance
        //treeViewAttrsRelevance = new JPanel();
        //treeViewAttrsRelevance.setBorder(BorderFactory.createLineBorder(Color.black));
        treeViewAttrsRelevance.setPreferredSize(new Dimension(700,1080));
        //treeViewAttrsRelevance.setBorder(BorderFactory.createLineBorder(Color.black));

        boxes[2].add(treeViewAttrsRelevance);

        Box detailsBoxes[] = new Box[2];
        detailsBoxes[0] = Box.createHorizontalBox();
        //detailsBoxes[1] = Box.createHorizontalBox();
        detailsBoxes[0].createGlue();
        //detailsBoxes[1].createGlue();
        treeViewAttrsRelevance.add(detailsBoxes[0]);
        //treeViewAttrsRelevance.add(detailsBoxes[1]);

        attrsImportancePanel.setPreferredSize(new Dimension(800, 800));

        detailsBoxes[0].add(attrsImportancePanel);
        //lastExpectedSnapshot.setSize(new Dimension(800, 280));
        //detailsBoxes[1].add(lastExpectedSnapshot);

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