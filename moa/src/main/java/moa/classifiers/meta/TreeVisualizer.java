package moa.classifiers.meta;

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.IntOption;
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
import java.util.*;
import java.util.List;

public class TreeVisualizer extends AbstractClassifier implements MultiClassClassifier, CapabilitiesHandler {
    Map<Integer, String> instancesHeader = new HashMap<>();

    protected JFrame treeViewFrame = new JFrame("TreeView");

    protected JPanel treeViewLeftPanel = new JPanel();

    protected JPanel treeViewRightPanel = new JPanel();

    protected JPanel treeViewPanel = new JPanel();

    public JTree treesBreadcrumb = new JTree(new DefaultMutableTreeNode("loading..."));

    public int activeBreadcrumbTree = -1;

    public JPanel selectedNodeDetailPanel = new JPanel();
    static JLabel sliderTracker = new JLabel();
    /*
    * For each tree (List1 of trees)
    * There is a List2
    * of multiples snapshots (Lists3 of nodes)
    * */
    public List<List<List<FakeNode>>> treesSnapshots = new ArrayList<>();

    protected List<HoeffdingTree.Node> treesRoots = new ArrayList<>();

    public int instancias = 0;

    private JPanel sliderPanel = new JPanel();

    public int activeTreeSnapshot = 0;
    private int FPS_MIN = 0;
    private int FPS_MAX = 0;
    private int FPS_INIT = 0;

    public ClassOption treeLearnerOption = new ClassOption("treeLearner", 'l',
            "Random Forest Tree.", Classifier.class,
            "trees.HoeffdingTree");

/*    public ClassOption updateTiming = public IntOption maxByteSizeOption = new IntOption("maxByteSize", 'm',
            "Maximum memory consumed by the tree.", 33554432, 0,
            Integer.MAX_VALUE);*/

    public FlagOption visualizeTree = new FlagOption("visualizeTree", 't',
            "Should visually track the trees?");

    private Classifier arvore = null;

    @Override
    public double[] getVotesForInstance(Instance inst) {
        return arvore.getVotesForInstance(inst);
    }

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

    private void createTreesSnapshots(Instance inst){
        if(!treesRoots.isEmpty()) {
            //Initialize array to keep each tree multiple snapshots
            if(treesSnapshots.isEmpty()){
                for (int i = 0; i < treesRoots.size(); i++) {
                    treesSnapshots.add(new ArrayList<>());
                }
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

                        if(a instanceof NumericAttributeBinaryTest){
                            NumericAttributeBinaryTest n = (NumericAttributeBinaryTest) a;
                            attrIndex = n.attIndex;
                        } else if(a instanceof NominalAttributeBinaryTest){
                            NominalAttributeBinaryTest n = (NominalAttributeBinaryTest) a;
                            attrIndex = n.attIndex;
                        } else if(a instanceof NumericAttributeBinaryRulePredicate){
                            NumericAttributeBinaryRulePredicate n = (NumericAttributeBinaryRulePredicate) a;
                            attrIndex = n.attIndex;
                        } else if(a instanceof NominalAttributeMultiwayTest){
                            NominalAttributeMultiwayTest n = (NominalAttributeMultiwayTest) a;
                            attrIndex = n.attIndex;
                        }

                        currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)), instancesHeader.get(attrIndex), attrIndex);
                        //add all childrens to nodesToRead
                        for(int j = 0; j < ((HoeffdingTree.SplitNode) nodesToRead.get(0)).numChildren(); j++){
                            currentNode.addChildrenId(nodesToRead.size() + nodeId);
                            nodesToRead.add(((HoeffdingTree.SplitNode) nodesToRead.get(0)).getChild(j));
                        }
                    } else {
                        currentNode = new FakeNode(nodeId, getNodeType(nodesToRead.get(0)));
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

    /*
    * This is the Frame lifecycle controller
    * */
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
            if (instancias%10000 == 0) {
/*            if(instancias == inst.getAttributesList().size()){
                System.out.println("última instância.");
            }*/
                //Ou, quantidade de instancias == insts.size()
                //Aqui estou recebendo apenas uma isntância, quanto é a quantidade máxima?
                //Em uso real, seria necessário calcular via Window (Hoeffding Limit)
                createTreesSnapshots(inst);
                renderTreesBreadcrumb();
                if(activeBreadcrumbTree != -1){
                    renderSlider();
                }
            }
            if(treesRoots.size() > 0){
                try{
                    //String example = treesRoots.get(0).toString();
                    StringBuilder example2 = new StringBuilder();
                    treesRoots.get(0).getDescription(example2, 0);
                }catch(Exception e){

                }

            //System.out.println("bla");
            }
        }
    }

    private void renderSlider(){
        JSlider slider = new JSlider(JSlider.HORIZONTAL,
                0, this.treesSnapshots.get(this.activeBreadcrumbTree).size()-1, 0 );
        //Turn on labels at major tick marks.
        slider.setMajorTickSpacing(1);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);

        Hashtable labelTable = new Hashtable();
        for(int i = 0; i < this.treesSnapshots.size(); i++){
            labelTable.put( new Integer( i ), new JLabel(Integer.toString(i)) );
        }
        slider.setLabelTable( labelTable );

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
                    sliderTracker.setText("Snapshot selected: " + snapshotToVisualize + ".");
                    //System.out.println("Available snapshots: " + treesSnapshots.get(activeBreadcrumbTree).size());
                    //System.out.println("Snapshot selected description: " + treesSnapshots.get(activeBreadcrumbTree).get(snapshotToVisualize));
                    renderTreeViewPanel();
                }
            }
        });
        sliderPanel.removeAll();
        sliderPanel.add(slider);
        sliderPanel.add(sliderTracker);
        sliderPanel.revalidate();
        sliderPanel.repaint();
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
                    Object tempVertex = graph.insertVertex(parent, null, lastSnapshot.get(i).getName() + "\n" + lastSnapshot.get(i).getFakeNodeType(), x, y, 80, 30, "ROUNDED");
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
            }catch(Exception e) {
                System.out.println("Erro ao gerar edges: " + e);
            }
        }catch(Exception e){
            System.out.println("Erro ao renderizar a parte gráfica da árvore: " + e);
        }finally{
            graph.getModel().endUpdate();
        }
        //Onde node click
        mxGraphComponent graphView = new mxGraphComponent(graph);
        graphView.getGraphControl().addMouseListener(new MouseAdapter(){
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
                    newNodeInfo.setLayout(new GridLayout(2+ (activeNode.getChildrens().size() * 2), 2));
                    newNodeInfo.add(new JLabel("Label: "));
                    newNodeInfo.add(new JLabel(activeNode.getName()));
                    newNodeInfo.add(new JLabel("Childrens: "));
                    newNodeInfo.add(new JLabel(""));
                    for(int j = 0; j < activeNode.getChildrens().size(); j++){
                        newNodeInfo.add(new JLabel("Label: "));
                        newNodeInfo.add(new JLabel(activeNode.getName()));
                        newNodeInfo.add(new JLabel("State: "));
                        newNodeInfo.add(new JLabel(activeNode.getName()));
                    }
                    selectedNodeDetailPanel.removeAll();
                    selectedNodeDetailPanel.add(newNodeInfo);
                    selectedNodeDetailPanel.revalidate();
                    selectedNodeDetailPanel.repaint();
                }
            }
        });

        treeViewPanel.removeAll();
        treeViewPanel.add(graphView);
        treeViewPanel.revalidate();
        treeViewPanel.repaint();
    }

    private void renderTreesBreadcrumb(){
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        for(int i = 0; i < this.treesRoots.size(); i++){
            root.add(new DefaultMutableTreeNode("Árvore " + Integer.toString(i)));
        }
        JTree breadcrumb = new JTree(root);
        breadcrumb.setRootVisible(false);
        breadcrumb.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) breadcrumb.getLastSelectedPathComponent();
                //System.out.println("selecting breadcrumb: " + selectedNode.getUserObject().toString() + " : " + selectedNode.getUserObject().toString().replaceAll("\\D+",""));
                activeBreadcrumbTree = Integer.parseInt(selectedNode.getUserObject().toString().replaceAll("\\D+",""));
                renderTreeViewPanel();
                renderSlider();
                //HERE the tree, slider and node info should be rendered again
            }
        });
        treeViewLeftPanel.removeAll();
        treeViewLeftPanel.add(new JScrollPane(breadcrumb));
        treeViewLeftPanel.revalidate();
        treeViewLeftPanel.repaint();
    }

    private void renderTreeFrame() {
        //Create new window
        treeViewFrame = new JFrame("treeViewFrame");
        treeViewFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //Window grid
        treeViewFrame.setLayout(new GridLayout(1, 2));
        //Config left Window component
        treeViewLeftPanel = new JPanel();
        treeViewLeftPanel.setLayout(new GridLayout(1, 1));
        treeViewLeftPanel.setOpaque(true); // content panes must be opaque
        treeViewLeftPanel.setSize(360, 240);
        treeViewLeftPanel.add(treesBreadcrumb);
        treeViewFrame.add(treeViewLeftPanel);
        //Config right Window component
        treeViewRightPanel = new JPanel();
        treeViewRightPanel.setLayout(new GridLayout(3, 1));
        treeViewRightPanel.setOpaque(true); // content panes must be opaque

        JScrollPane treeScroller = new JScrollPane(treeViewPanel);
        treeScroller.setPreferredSize(new Dimension(350, 150));
        treeViewRightPanel.add(treeScroller);

        //Slider
        JScrollPane sliderScroller = new JScrollPane(sliderPanel);
        sliderScroller.setPreferredSize(new Dimension(350, 150));
        treeViewRightPanel.add(treeScroller);
        treeViewRightPanel.add(sliderScroller);

        JScrollPane detailScroller = new JScrollPane(selectedNodeDetailPanel);
        detailScroller.setPreferredSize(new Dimension(350, 150));
        treeViewRightPanel.add(detailScroller);

        treeViewFrame.add(treeViewRightPanel);
        // Display the windows
        treeViewFrame.setSize(1280, 720);
        treeViewFrame.setBackground(Color.RED);

        treeViewFrame.pack();
        treeViewFrame.setLocationByPlatform(true);
        treeViewFrame.setVisible(true);
    }
}

enum FakeNodeType {
    LEARNING,
    LEARNINGNODE,
    LEARNINGNODENB,
    LEARNINGNODENBADAPTIVE,
    SPLITNODE,
    UNKNOWN
}

class FakeNode {
    private int id;
    private String name;
    //Value is the attribute index in the instance
    private int value;
    private FakeNode parent;
    private List<FakeNode> childrens = new ArrayList<>();
    private List<Integer> childrensIds = new ArrayList<>();
    private FakeNodeType type;

    FakeNode(int id, FakeNodeType type){
        this.id = id;
        this.type = type;
    }

    FakeNode(int id, FakeNodeType type, String name, int value){
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

    public Integer getValue(){
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