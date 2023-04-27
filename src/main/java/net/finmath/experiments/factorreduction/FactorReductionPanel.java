/*
 * Created on 03.06.2004, 10.01.2023
 */
package net.finmath.experiments.factorreduction;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import net.finmath.functions.LinearAlgebra;
import net.finmath.plots.jfreechart.HuePaintScale;
import net.finmath.plots.jfreechart.JFreeChartUtilities;

/**
 * GUI Panel to explore the effect of a PCA of a correlation matrix and a factor reduction.
 *
 * @author Christian Fries
 */
public class FactorReductionPanel extends JPanel implements ActionListener, Runnable {

	private static final long serialVersionUID = 2852737071726359012L;

	// Some formatters used
	private static DecimalFormat formatterInt		= new DecimalFormat("0");
	private static DecimalFormat formatterReal3		= new DecimalFormat("0.000");

	/*
	 * Data of this applet
	 */

	private int		numberOfFactors				= 3;
	private double	correlationDecayParameter	= 0.1;

	private Thread	calculationThread		= null;

	private final JTextField	numberOfFactorsLabel	= new JTextField("3");

	// Correlation Model is rho(i,j) = exp(-a *  abs(i-j))
	private final JTextField	correlationParameterA	 = new JTextField(formatterReal3.format(0.1));

	private JPanel	inputPanel			= new JPanel();

	private final JLabel	correlation			= new JLabel("-",JLabel.CENTER);

	private final DefaultXYZDataset	    datasetCorrelationsFull		= new DefaultXYZDataset();
	private final DefaultXYZDataset      	datasetCorrelationsReduced	= new DefaultXYZDataset();
	private final XYSeriesCollection		datasetFactorsFull		= new XYSeriesCollection();
	private final XYSeriesCollection		datasetFactorsReduced		= new XYSeriesCollection();

	public FactorReductionPanel() {
		super();
		init();
	}

	public void init() {
		this.setSize(900, 700);

		// Create the GUI
		updateData();

		correlationParameterA.setColumns(5);
		correlationParameterA.addActionListener(this);

		// Set up a panel containing the input correlation curve
		final JPanel inputPanelCorCurve = new JPanel();
		inputPanelCorCurve.setLayout(new BoxLayout(inputPanelCorCurve, BoxLayout.X_AXIS));
		inputPanelCorCurve.setAlignmentX(Component.LEFT_ALIGNMENT);
		inputPanelCorCurve.add(new JLabel("r(Ti,Tj) = exp( -",SwingConstants.LEFT));
		inputPanelCorCurve.add(correlationParameterA);
		inputPanelCorCurve.add(new JLabel("* abs(Ti - Tj)).",SwingConstants.LEFT));

		// Set up a panel containing the input
		final JPanel inputPanelModelSpec = new JPanel();
		inputPanelModelSpec.setLayout(new GridBagLayout());

		// We use the GridBagConstraints throughout to specify the postion of the GUI component
		final GridBagConstraints c = new GridBagConstraints();

		// General parameters
		c.weightx = 1.0;
		c.weighty = 1.0;
		c.insets =  new Insets(3,3,3,3);
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		// Create number of Factors input
		final JPanel numberOfFactorsPanel = new JPanel();
		numberOfFactorsPanel.setLayout(new BoxLayout(numberOfFactorsPanel, BoxLayout.X_AXIS));
		final JButton buttonPlus	= new JButton("+");
		final JButton buttonMinus	= new JButton("-");
		buttonPlus.setActionCommand("numberOfFactors++");
		buttonPlus.addActionListener(this);
		buttonMinus.setActionCommand("numberOfFactors--");
		buttonMinus.addActionListener(this);
		numberOfFactorsLabel.setColumns(5);
		numberOfFactorsPanel.add(numberOfFactorsLabel);
		numberOfFactorsPanel.add(Box.createHorizontalStrut(12));
		numberOfFactorsPanel.add(buttonPlus);
		numberOfFactorsPanel.add(buttonMinus);

		final JLabel labelFactors		= new JLabel("Number of factors: ",SwingConstants.TRAILING);
		labelFactors.setLabelFor(numberOfFactorsPanel);
		final JLabel labelCorrelation	= new JLabel("Correlation structure: ",SwingConstants.TRAILING);
		labelCorrelation.setLabelFor(inputPanelCorCurve);
		c.gridx = 0;		c.gridy = 0;		inputPanelModelSpec.add(labelFactors,c);
		c.gridx = 0;		c.gridy = 1;		inputPanelModelSpec.add(labelCorrelation,c);

		c.fill = GridBagConstraints.NONE;
		c.gridx = 1;		c.gridy = 0;		inputPanelModelSpec.add(numberOfFactorsPanel,c);
		c.gridx = 1;		c.gridy = 1;		inputPanelModelSpec.add(inputPanelCorCurve,c);

		c.anchor = GridBagConstraints.FIRST_LINE_END;
		c.fill = GridBagConstraints.NONE;
		c.gridx = 1;		c.gridy = 2;

		final JButton calculateButton = new JButton("Calculate");
		calculateButton.addActionListener(this);
		inputPanelModelSpec.add(calculateButton,c);
		inputPanelModelSpec.setBorder(BorderFactory.createTitledBorder("Model specification"));

		// Create panel with scenario buttons
		final JPanel scenarioPanel = new JPanel();
		scenarioPanel.setLayout(new BoxLayout(scenarioPanel, BoxLayout.Y_AXIS));
		for(int scenarioNumber=1; scenarioNumber<=5; scenarioNumber++) {
			final JButton scenarioButton = new JButton("Scenario " + scenarioNumber);
			scenarioButton.addActionListener(this);
			scenarioButton.setActionCommand("Scenario " + scenarioNumber);
			scenarioPanel.add(scenarioButton);
		}
		scenarioPanel.setBorder(BorderFactory.createTitledBorder("Predefined scenarios"));
		scenarioPanel.add(Box.createVerticalGlue());

		/*
		 * Generate factor plot
		 */
		final JFreeChart factorPlotFullChart = JFreeChartUtilities.getXYPlotChart(
				null,
				"component index",
				" 0",
				"factor weight",
				" 0%",
				datasetFactorsFull);
		factorPlotFullChart.getXYPlot().getRangeAxis().setAutoRange(false);
		factorPlotFullChart.getXYPlot().getRangeAxis().setRange(-1.05, 1.05);

		final JPanel factorPlotFullPanel = new JPanel();
		factorPlotFullPanel.setLayout(new BoxLayout(factorPlotFullPanel, BoxLayout.X_AXIS));
		factorPlotFullPanel.add(new ChartPanel(
				factorPlotFullChart,
				320, 320,	// size
				0, 0,	// minimum size
				1024, 1024,	// maximum size
				false, true, true, true, true, false	// useBuffer, properties, save, print, zoom, tooltips
				));

		/*
		 * Generate factor plot
		 */
		final JFreeChart factorPlotReducedChart = JFreeChartUtilities.getXYPlotChart(
				null,
				"component index",
				" 0",
				"factor weight",
				" 0%",
				datasetFactorsReduced);
		factorPlotReducedChart.getXYPlot().getRangeAxis().setAutoRange(false);
		factorPlotReducedChart.getXYPlot().getRangeAxis().setRange(-1.05, 1.05);

		final JPanel factorPlotReduced = new JPanel();
		factorPlotReduced.setLayout(new BoxLayout(factorPlotReduced, BoxLayout.X_AXIS));
		factorPlotReduced.add(new ChartPanel(
				factorPlotReducedChart,
				320, 320,	// size
				0, 0,	// minimum size
				1024, 1024,	// maximum size
				false, true, true, true, true, false	// useBuffer, properties, save, print, zoom, tooltips
				));

		/*
		 * Generate correlation plot
		 */
		final NumberAxis xAxis = new NumberAxis("column");
		final NumberAxis yAxis = new NumberAxis("row");
		final NumberAxis zAxis = new NumberAxis("correlation");
		final HuePaintScale paintScale = new HuePaintScale(-1.0, 1.0) {
			@Override
			public Paint getPaint(double value) {
				if(value > getUpperBound() || value < getLowerBound() || Double.isNaN(value)) {
					return Color.GRAY;
				}
				return Color.getHSBColor((float) ((-value-getLowerBound()) / (getUpperBound()-getLowerBound()) * 240.0/360.0), 1.0f, 1.0f);
			}};

		final JPanel correlationPlotFull = new JPanel();
		correlationPlotFull.setLayout(new BoxLayout(correlationPlotFull, BoxLayout.X_AXIS));
		correlationPlotFull.add(new ChartPanel(
				JFreeChartUtilities.getContourPlot(
						datasetCorrelationsFull,
						new XYBlockRenderer(),
						paintScale,
						xAxis, yAxis,
						zAxis,
						(int)Math.sqrt(datasetCorrelationsFull.getItemCount(0)),
						(int)Math.sqrt(datasetCorrelationsFull.getItemCount(0))
						),
				320, 320,	// size
				0, 0,	// minimum size
				1024, 1024,	// maximum size
				false, true, true, true, true, false	// useBuffer, properties, save, print, zoom, tooltips
				));

		/*
		 * Generate correlation plot
		 */
		final JPanel correlationPlotReduced = new JPanel();
		correlationPlotReduced.setLayout(new BoxLayout(correlationPlotReduced, BoxLayout.X_AXIS));
		correlationPlotReduced.add(new ChartPanel(
				JFreeChartUtilities.getContourPlot(
						datasetCorrelationsReduced,
						new XYBlockRenderer(),
						paintScale,
						xAxis, yAxis,
						zAxis,
						(int)Math.sqrt(datasetCorrelationsReduced.getItemCount(0)),
						(int)Math.sqrt(datasetCorrelationsReduced.getItemCount(0))
						),
				320, 320,	// size
				0, 0,	// minimum size
				1024, 1024,	// maximum size
				false, true, true, true, true, false	// useBuffer, properties, save, print, zoom, tooltips
				));

		paintScale.setLowerBound(-1.0);
		paintScale.setUpperBound( 1.0);
		zAxis.setRange(-1.0,1.0);

		// Compose all interface elements in boxes
		inputPanel = new JPanel();
		inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.X_AXIS));
		inputPanel.add(inputPanelModelSpec);
		//		inputPanel.add(scenarioPanel);

		final JPanel correlationFullPanel = new JPanel();
		correlationFullPanel.setLayout(new BoxLayout(correlationFullPanel, BoxLayout.X_AXIS));
		correlationFullPanel.setBorder(BorderFactory.createTitledBorder("Original correlation"));
		correlationFullPanel.add(correlationPlotFull);
		correlationFullPanel.add(factorPlotFullPanel);

		final JPanel correlationReducedPanel = new JPanel();
		correlationReducedPanel.setLayout(new BoxLayout(correlationReducedPanel, BoxLayout.X_AXIS));
		correlationReducedPanel.setBorder(BorderFactory.createTitledBorder("Factor reduced correlation"));
		correlationReducedPanel.add(correlationPlotReduced);
		correlationReducedPanel.add(factorPlotReduced);

		final JPanel resultPanel = new JPanel();
		resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.Y_AXIS));
		resultPanel.setBorder(BorderFactory.createTitledBorder("Results"));
		resultPanel.add(correlationFullPanel);
		resultPanel.add(correlationReducedPanel);

		final Container cp = this;
		cp.setLayout(new BoxLayout(cp, BoxLayout.Y_AXIS));
		cp.add(Box.createVerticalStrut(10));
		cp.add(Box.createVerticalGlue());
		cp.add(inputPanel);
		cp.add(Box.createVerticalGlue());
		cp.add(resultPanel);

		cp.setName("Factor Reduction / Pricipal Component Analysis");

		// Call updateDate() - initializes the dataseries for the charts
		setContainerEnabled(inputPanel,false);
		calculationThread = new Thread(this);
		calculationThread.start();
	}

	// Handle action events generated by applet controls.
	@Override
	public void actionPerformed(ActionEvent e) {

		// Read GUI
		try {
			numberOfFactors				= formatterInt.parse(numberOfFactorsLabel.getText()).intValue();
			correlationDecayParameter	= formatterReal3.parse(correlationParameterA.getText()).doubleValue();
		}
		catch(final Exception exception) {
		}

		// Check events
		// Retrieve the command associated with the action. The command is the name of the visual component the triggered the event.
		final String commandString = e.getActionCommand();

		if(commandString.equals("numberOfFactors++")) {
			numberOfFactors++;
		}
		else if(commandString.equals("numberOfFactors--")) {
			numberOfFactors--;
		}
		else if(commandString.equals("Scenario 1")) {
			numberOfFactors = 1;
			correlationDecayParameter = 0.1;
		}
		else if(commandString.equals("Scenario 2")) {
			numberOfFactors = 2;
			correlationDecayParameter = 0.1;
		}
		else if(commandString.equals("Scenario 3")) {
			numberOfFactors = 5;
			correlationDecayParameter = 0.1;
		}
		else if(commandString.equals("Scenario 4")) {
			numberOfFactors = 2;
			correlationDecayParameter = 0.005;
		}
		else if(commandString.equals("Scenario 5")) {
			numberOfFactors = 5;
			correlationDecayParameter = 0.005;
		}

		// Apply constrains
		if(numberOfFactors < 1) {
			numberOfFactors = 1;
		}
		if(numberOfFactors > 20) {
			numberOfFactors = 20;
		}

		// Update GUI
		numberOfFactorsLabel.setText(formatterInt.format(numberOfFactors));
		correlationParameterA.setText(formatterReal3.format(correlationDecayParameter));

		setContainerEnabled(inputPanel,false);
		calculationThread = new Thread(this);
		calculationThread.start();
	}

	// Applet start
	public void start() {
		setContainerEnabled(inputPanel,true);
	}

	@Override
	public void run() {
		try {
			updateData();
		}
		catch(final OutOfMemoryError e) {
			setContainerEnabled(inputPanel,true);
			System.err.println(e.getLocalizedMessage());
		}
		setContainerEnabled(inputPanel,true);
	}

	private void setContainerEnabled(Container container, boolean enabled)
	{
		final Component[] components = container.getComponents();

		for(int i=0; i<components.length; i++) {
			components[i].setEnabled(enabled);
			if(components[i] instanceof Container) {
				setContainerEnabled((Container)components[i], enabled);
			}
		}
	}

	void updateData()
	{
		/*
		 * Create the time discretization of the processes
		 */
		final double lastTime = 100.0, dt = 1.0;
		final double[] tenorTimeDiscretization = new double[(int)(lastTime/dt)+1];
		for(int i=0; i<(lastTime/dt)+1; i++) {
			tenorTimeDiscretization[i] = i * dt;
		}

		/*
		 * Create instanteaneous correlation matrix
		 */
		final double[][] originalCorrelationMatrix = createCorrelationMatrixFromFunctionalForm(
				tenorTimeDiscretization,
				correlationDecayParameter);

		/*
		 * Get the reduced factor and the full factor matrix
		 */
		final double[][] factorMatrixReduced		= LinearAlgebra.factorReduction(originalCorrelationMatrix, numberOfFactors);
		final double[][] factorMatrixFull			= LinearAlgebra.factorReduction(originalCorrelationMatrix, originalCorrelationMatrix.length);
		final double[][] reducedCorrelationMatrix	= LinearAlgebra.multMatrices(factorMatrixReduced, LinearAlgebra.transpose(factorMatrixReduced));

		/*
		 * Buid datasets for plot
		 */
		final int		numberOfValues	= originalCorrelationMatrix.length * originalCorrelationMatrix.length;
		final double[]	xValues			= new double[numberOfValues];
		final double[]	yValues			= new double[numberOfValues];
		final double[]	zValues			= new double[numberOfValues];
		final double[]	zValuesRed			= new double[numberOfValues];
		int valueIndex = 0;
		for(int col=0; col<originalCorrelationMatrix.length; col++) {
			for(int row=0; row<originalCorrelationMatrix[col].length; row++) {
				xValues[valueIndex] = col;
				yValues[valueIndex] = row;
				zValues[valueIndex] = originalCorrelationMatrix[row][col];
				zValuesRed[valueIndex] = reducedCorrelationMatrix[row][col];
				valueIndex++;
			}
		}

		datasetCorrelationsFull.addSeries("correlation", new double[][] { xValues, yValues, zValues });
		datasetCorrelationsReduced.addSeries("correlation", new double[][] { xValues, yValues, zValuesRed });
		net.finmath.plots.jfreechart.JFreeChartUtilities.updateContourPlot(
				datasetCorrelationsFull,
				new XYBlockRenderer(),
				null, null, null, null,
				(int)Math.sqrt(datasetCorrelationsReduced.getItemCount(0)),
				(int)Math.sqrt(datasetCorrelationsReduced.getItemCount(0)));
		net.finmath.plots.jfreechart.JFreeChartUtilities.updateContourPlot(
				datasetCorrelationsReduced,
				new XYBlockRenderer(),
				null, null, null, null,
				(int)Math.sqrt(datasetCorrelationsReduced.getItemCount(0)),
				(int)Math.sqrt(datasetCorrelationsReduced.getItemCount(0)));

		/*
		 * Create data series
		 */
		datasetFactorsFull.removeAllSeries();
		for(int col=0; col<factorMatrixFull[0].length; col++) {
			final XYSeries series = new XYSeries("" + col);
			for(int row=0; row<factorMatrixFull.length; row++) {
				series.add(row, factorMatrixFull[row][col]);
			}
			datasetFactorsFull.addSeries(series);
		}
		datasetFactorsReduced.removeAllSeries();
		for(int col=0; col<factorMatrixReduced[0].length; col++) {
			final XYSeries series = new XYSeries("" + col);
			for(int row=0; row<factorMatrixReduced.length; row++) {
				series.add(row, factorMatrixReduced[row][col]);
			}
			datasetFactorsReduced.addSeries(series);
		}
	}

	/* Clean up before exiting. */
	public void destroy() {
	}

	/**
	 * This method creates an instanteaneous correlation matrix according to the functional form
	 *  \( rho(i,j) = exp(-a * abs(T_{i}-T_{j}) ) \).
	 *
	 * @param tenorTimeDiscretization The maturity discretization of the interest rate curve.
	 * @param correlationDecayParameter The parameter a
	 * @return The correlation matrix.
	 */
	public static double[][] createCorrelationMatrixFromFunctionalForm(
			double[]	tenorTimeDiscretization,
			double		correlationDecayParameter) {

		final double[][] correlation = new double[tenorTimeDiscretization.length][tenorTimeDiscretization.length];
		for(int row=0; row<tenorTimeDiscretization.length; row++) {
			for(int col=0; col<tenorTimeDiscretization.length; col++) {
				// Exponentially decreasing instanteaneous correlation
				correlation[row][col] = Math.exp(-correlationDecayParameter * Math.abs(tenorTimeDiscretization[row]-tenorTimeDiscretization[col]));
			}
		}
		return correlation;
	}
}
