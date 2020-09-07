/*
 *    RegressionTabPanel.java
 *    Copyright (C) 2007 University of Waikato, Hamilton, New Zealand
 *    @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
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
package moa.gui;

import moa.gui.PreviewPanel.TypePanel;

import javax.swing.*;
import java.awt.*;

/**
 * This panel allows the user to select and configure a task, and run it.
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @version $Revision: 7 $
 */
public class treeViewPanel extends AbstractTabPanel {

	private static final long serialVersionUID = 1L;

	protected JPanel mainPanel = null;
	JButton b = new JButton("click");//creating instance of JButton

	public treeViewPanel() {
		mainPanel = new JPanel();//creating instance of JFrame
		b.setBounds(130,100,100, 40);//x axis, y axis, width, height

		mainPanel.add(b);//adding button in JFrame
		mainPanel.setSize(400,500);//400 width and 500 height
		mainPanel.setLayout(null);//using no layout managers
		mainPanel.setVisible(true);//making the frame visible
		/*		this.taskManagerPanel = new RegressionTaskManagerPanel();
		this.previewPanel = new PreviewPanel(TypePanel.REGRESSION);
		this.taskManagerPanel.setPreviewPanel(this.previewPanel);
		setLayout(new BorderLayout());
		add(this.taskManagerPanel, BorderLayout.NORTH);
		add(this.previewPanel, BorderLayout.CENTER);*/
	}

	//returns the string to display as title of the tab
    @Override
	public String getTabTitle() {
		return "Tree View.";
	}

	//a short description (can be used as tool tip) of the tab, or contributor, etc.
    @Override
	public String getDescription(){
		return "Tree View!";
	}

}



