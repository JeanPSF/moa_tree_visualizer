/*
 *    ALTaskManagerPanel.java
 *    Original Work: Copyright (C) 2007 University of Waikato, Hamilton, New Zealand
 *    @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 *    @author Manuel Martín (msalvador@bournemouth.ac.uk)
 *    Modified Work: Copyright (C) 2017 Otto-von-Guericke-University, Magdeburg, Germany
 *    @author Tuan Pham Minh (tuan.pham@ovgu.de)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *    
 */
package moa.gui.active;

import moa.core.StringUtils;
import moa.gui.ClassOptionSelectionPanel;
import moa.gui.FileExtensionFilter;
import moa.gui.GUIUtils;
import moa.options.ClassOption;
import moa.options.OptionHandler;
import moa.tasks.meta.ALMainTask;
import moa.tasks.meta.ALPartitionEvaluationTask;
import moa.tasks.meta.ALTaskThread;
import nz.ac.waikato.cms.gui.core.BaseFileChooser;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;


/**
 * This panel displays the running tasks for active learning experiments.
 *
 * @author Tuan Pham Minh (tuan.pham@ovgu.de)
 * @version $Revision: 1 $
 */
public class ALTaskManagerPanel extends JPanel{
	
	private static final long serialVersionUID = 1L;

    public static final int MILLISECS_BETWEEN_REFRESH = 600;

    public static String exportFileExtension = "log";

    public class ProgressCellRenderer extends JProgressBar implements
            TableCellRenderer {

        private static final long serialVersionUID = 1L;

        public ProgressCellRenderer() {
            super(SwingConstants.HORIZONTAL, 0, 10000);
            setBorderPainted(false);
            setStringPainted(true);
        }

         @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus, int row,
                int column) {
            double frac = -1.0;
            if (value instanceof Double) {
                frac = ((Double) value).doubleValue();
            }
            if (frac >= 0.0) {
                setIndeterminate(false);
                setValue((int) (frac * 10000.0));
                setString(StringUtils.doubleToString(frac * 100.0, 2, 2));
            } else {
                setValue(0);
                //setIndeterminate(true);
                //setString("?");
            }
            return this;
        }

        @Override
        public void validate() {
        }

        @Override
        public void revalidate() {
        }

        @Override
        protected void firePropertyChange(String propertyName, Object oldValue,
                Object newValue) {
        }

        @Override
        public void firePropertyChange(String propertyName, boolean oldValue,
                boolean newValue) {
        }
    }
    
    protected class TaskColorCodingCellRenderer extends DefaultTableCellRenderer implements TableCellRenderer
    {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			setBackground((Color) value);
			return this;
		}
    	
    }
    
    protected class TaskTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        @Override
        public String getColumnName(int col) {
            switch (col) {
	        	case 0:
	        		return "";
	            case 1:
	                return "command";
	            case 2:
	                return "status";
	            case 3:
	                return "time elapsed";
	            case 4:
	                return "current activity";
	            case 5:
	                return "% complete";
            }
            return null;
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public int getRowCount() {
            return ALTaskManagerPanel.this.taskList.size();
        }

        @Override
        public Object getValueAt(int row, int col) {
        	ALTaskThread thread = ALTaskManagerPanel.this.taskList.get(row);
            switch (col) {
            	case 0:
            		return ((ALMainTask) thread.getTask()).getColorCoding();
                case 1:
                	// display the name specified by the task
                    return ((ALMainTask) thread.getTask()).getDisplayName();
                case 2:
                    return thread.getCurrentStatusString();
                case 3:
                    return StringUtils.secondsToDHMSString(thread.getCPUSecondsElapsed());
                case 4:
                    return thread.getCurrentActivityString();
                case 5:
                    return new Double(thread.getCurrentActivityFracComplete());
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    }
    
    
    protected ALMainTask currentTask = new ALPartitionEvaluationTask();
    
    protected List<ALTaskThread> taskList = new ArrayList<ALTaskThread>();
	
    protected JButton configureTaskButton = new JButton("Configure");

    protected JTextField taskDescField = new JTextField();

    protected JButton runTaskButton = new JButton("Run");

    protected TaskTableModel taskTableModel;

    protected JTable taskTable;

    protected JButton pauseTaskButton = new JButton("Pause");

    protected JButton resumeTaskButton = new JButton("Resume");

    protected JButton cancelTaskButton = new JButton("Cancel");

    protected JButton deleteTaskButton = new JButton("Delete");
    
	protected ALPreviewPanel previewPanel;

    private Preferences prefs;
    
    private final String PREF_NAME = "currentTask";

    public ALTaskManagerPanel() {
        // Read current task preference
        prefs = Preferences.userRoot().node(this.getClass().getName());
        String taskText = this.currentTask.getCLICreationString(ALMainTask.class);
        String propertyValue = prefs.get(PREF_NAME, taskText);
        //this.taskDescField.setText(propertyValue);
        
        setTaskString(propertyValue, false); //Not store preference
        this.taskDescField.setEditable(false);
        
        final Component comp = this.taskDescField;
        this.taskDescField.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 1) {
                    if ((evt.getButton() == MouseEvent.BUTTON3)
                            || ((evt.getButton() == MouseEvent.BUTTON1) && evt.isAltDown() && evt.isShiftDown())) {
                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem item;

                        item = new JMenuItem("Copy configuration to clipboard");
                        item.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                copyClipBoardConfiguration();
                            }
                        });
                        menu.add(item);



                        item = new JMenuItem("Save selected tasks to file");
                        item.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent arg0) {
                                saveLogSelectedTasks();
                            }
                        });
                        menu.add(item);


                        item = new JMenuItem("Enter configuration...");
                        item.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent arg0) {
                                String newTaskString = JOptionPane.showInputDialog("Insert command line");
                                if (newTaskString != null) {
                                    setTaskString(newTaskString);
                                }
                            }
                        });
                        menu.add(item);

                        menu.show(comp, evt.getX(), evt.getY());
                    }
                }
            }
        });

        JPanel configPanel = new JPanel();
        configPanel.setLayout(new BorderLayout());
        configPanel.add(this.configureTaskButton, BorderLayout.WEST);
        configPanel.add(this.taskDescField, BorderLayout.CENTER);
        configPanel.add(this.runTaskButton, BorderLayout.EAST);
        this.taskTableModel = new TaskTableModel();
        this.taskTable = new JTable(this.taskTableModel);
        this.taskTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        TaskColorCodingCellRenderer taskColorCodingRenderer = new TaskColorCodingCellRenderer();
        this.taskTable.getColumnModel().getColumn(0).setCellRenderer(
        		taskColorCodingRenderer);
        this.taskTable.getColumnModel().getColumn(2).setCellRenderer(
                centerRenderer);
        this.taskTable.getColumnModel().getColumn(3).setCellRenderer(
                centerRenderer);
        this.taskTable.getColumnModel().getColumn(5).setCellRenderer(
                new ProgressCellRenderer());
        
        // set the color column to the smallest size possible
        TableColumnModel tableColumnModel = taskTable.getColumnModel();
        tableColumnModel.getColumn(0).setMaxWidth(tableColumnModel.getColumn(0).getMinWidth());
        tableColumnModel.getColumn(0).setPreferredWidth(0);
        
        JPanel controlPanel = new JPanel();
        controlPanel.add(this.pauseTaskButton);
        controlPanel.add(this.resumeTaskButton);
        controlPanel.add(this.cancelTaskButton);
        controlPanel.add(this.deleteTaskButton);
        setLayout(new BorderLayout());
        add(configPanel, BorderLayout.NORTH);
        add(new JScrollPane(this.taskTable), BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);
        this.taskTable.getSelectionModel().addListSelectionListener(
                new ListSelectionListener() {

                    @Override
                    public void valueChanged(ListSelectionEvent arg0) {
                    	taskSelectionChanged();
                    }
                });
        this.configureTaskButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                String newTaskString = ClassOptionSelectionPanel.showSelectClassDialog(ALTaskManagerPanel.this,
                        "Configure task", ALMainTask.class,
                        ALTaskManagerPanel.this.currentTask.getCLICreationString(ALMainTask.class),
                        null);

                setTaskString(newTaskString);
            }
        });
        this.runTaskButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
            	runTask((ALMainTask) ALTaskManagerPanel.this.currentTask.copy());
            }
        });
        this.pauseTaskButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
            	pauseSelectedTasks();
            }
        });
        this.resumeTaskButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
            	resumeSelectedTasks();
            }
        });
        this.cancelTaskButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
            	cancelSelectedTasks();
            }
        });
        this.deleteTaskButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
            	deleteSelectedTasks();
            }
        });

        javax.swing.Timer updateListTimer = new javax.swing.Timer(
                MILLISECS_BETWEEN_REFRESH, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
            	ALTaskManagerPanel.this.taskTable.repaint();
            }
        });
        updateListTimer.start();
        setPreferredSize(new Dimension(0, 200));
    }
    
	public void setPreviewPanel(ALPreviewPanel previewPanel) {
        this.previewPanel = previewPanel;
    }

    public void setTaskString(String cliString) {
        setTaskString(cliString, true);
    }
    
    public void setTaskString(String cliString, boolean storePreference) {    
        try {
            this.currentTask = (ALMainTask) ClassOption.cliStringToObject(
                    cliString, ALMainTask.class, null);
            String taskText = this.currentTask.getCLICreationString(ALMainTask.class);
            
            this.taskDescField.setText(taskText);
            if (storePreference == true){
	            //Save task text as a preference
	            prefs.put(PREF_NAME, taskText);
            }
        } catch (Exception ex) {
            GUIUtils.showExceptionDialog(this, "Problem with task", ex);
        }
    }

    public void runTask(ALMainTask task) {

        task.prepareForUse();
    	ALTaskThread thread = new ALTaskThread(task);
        this.taskTableModel.fireTableDataChanged();
        thread.start();
        
        // get the threads for all subtasks
        List<ALTaskThread> subThreads = ((ALMainTask) thread.getTask()).getSubtaskThreads();
        // add the subtask threads to the list of available tasks

        this.taskList.add(0, thread);
        this.taskTable.setRowSelectionInterval(0, 0);
        
        for(int i = 0; i < subThreads.size(); ++i)
        {
        	this.taskList.add(i+1,subThreads.get(i));
        }

    }

    public void taskSelectionChanged() {
        ALTaskThread[] selectedTasks = getSelectedTasks();
        if (selectedTasks.length == 1) {
            setTaskString(((OptionHandler) selectedTasks[0].getTask()).getCLICreationString(ALMainTask.class));
            if (this.previewPanel != null) {
                this.previewPanel.setTaskThreadToPreview(selectedTasks[0]);
            }
        } else {
            this.previewPanel.setTaskThreadToPreview(null);
        }
        
        if(selectedTasks.length > 0)
        {
        	boolean onlyRootTasks = true;
        	for(int i = 0; i < selectedTasks.length; ++i)
        	{
        		onlyRootTasks &= !((ALMainTask)selectedTasks[i].getTask()).isSubtask();
        	}

        	cancelTaskButton.setEnabled(onlyRootTasks);
        	deleteTaskButton.setEnabled(onlyRootTasks);
        }
    }

    public ALTaskThread[] getSelectedTasks() {
        int[] selectedRows = this.taskTable.getSelectedRows();
        ALTaskThread[] selectedTasks = new ALTaskThread[selectedRows.length];
        for (int i = 0; i < selectedRows.length; i++) {
            selectedTasks[i] = this.taskList.get(selectedRows[i]);
        }
        return selectedTasks;
    }

    public void pauseSelectedTasks() {
        ALTaskThread[] selectedTasks = getSelectedTasks();
        for (ALTaskThread thread : selectedTasks) {
            thread.pauseTask();
        }
    }

    public void resumeSelectedTasks() {
        ALTaskThread[] selectedTasks = getSelectedTasks();
        for (ALTaskThread thread : selectedTasks) {
            thread.resumeTask();
        }
    }

    public void cancelSelectedTasks() {
        ALTaskThread[] selectedTasks = getSelectedTasks();
        for (ALTaskThread thread : selectedTasks) {
            thread.cancelTask();
        }
    }

    public void deleteSelectedTasks() {
    	ALTaskThread[] selectedTasks = getSelectedTasks();
    	
        for (ALTaskThread thread : selectedTasks) {
            thread.cancelTask();
            this.taskList.remove(thread);
            
            ALMainTask task =  (ALMainTask)thread.getTask();
            List<ALTaskThread> subtaskThreads = task.getSubtaskThreads();
            for (ALTaskThread subthread : subtaskThreads) {
                this.taskList.remove(subthread);
            }
            
        }
        this.taskTableModel.fireTableDataChanged();
    }

    public void copyClipBoardConfiguration() {

        StringSelection selection = new StringSelection(this.taskDescField.getText().trim());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);

    }

    public void saveLogSelectedTasks() {
        String tasksLog = "";
        ALTaskThread[] selectedTasks = getSelectedTasks();
        for (ALTaskThread thread : selectedTasks) {
        	tasksLog += ((OptionHandler) thread.getTask()).getCLICreationString(ALMainTask.class) + "\n";
        }

        BaseFileChooser fileChooser = new BaseFileChooser();
        fileChooser.setAcceptAllFileFilterUsed(true);
        fileChooser.addChoosableFileFilter(new FileExtensionFilter(
                exportFileExtension));
        if (fileChooser.showSaveDialog(this) == BaseFileChooser.APPROVE_OPTION) {
            File chosenFile = fileChooser.getSelectedFile();
            String fileName = chosenFile.getPath();
            if (!chosenFile.exists()
                    && !fileName.endsWith(exportFileExtension)) {
                fileName = fileName + "." + exportFileExtension;
            }
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(
                        new FileWriter(fileName)));
                out.write(tasksLog);
                out.close();
            } catch (IOException ioe) {
                GUIUtils.showExceptionDialog(
                        this,
                        "Problem saving file " + fileName, ioe);
            }
        }
    }
}
