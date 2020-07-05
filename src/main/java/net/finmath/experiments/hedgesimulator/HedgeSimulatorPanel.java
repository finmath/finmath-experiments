/*
 * Created on 10.06.2004
 */
package net.finmath.experiments.hedgesimulator;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloBlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.BlackScholesHedgedPortfolio;
import net.finmath.stochastic.RandomVariable;
import net.finmath.swing.JNumberField;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * This applets demonstrates the hedging in discrete time.
 *
 * @author Christian Fries
 * @version 1.0
 */
public class HedgeSimulatorPanel extends JPanel implements ActionListener, Runnable {

	private static final long serialVersionUID = 4409293529502725079L;

	static DecimalFormat formatter = new DecimalFormat("0.00");

	// Data of this applet

	private JPanel inputPanel;

	/**
	 * Model parameters
	 */
	private int				numberOfPaths			= 4000;
	private JTextField		numberOfPathsTextField	= new JTextField();
	private JNumberField	initialValueTextField	= new JNumberField(1.0,	 new DecimalFormat("0.00"), this);
	private JNumberField	riskFreeRateTextField	= new JNumberField(0.05,	 new DecimalFormat("0.00"), this);
	private JNumberField	volatilityTextField		= new JNumberField(0.5,	 new DecimalFormat("0.00"), this);

	/**
	 * Simulation parameters
	 */
	private double			timeHorizon				= 2.0;
	private JNumberField	deltaTTextField			= new JNumberField(1,	 new DecimalFormat("0"), this);

	/**
	 * Option parameters
	 */
	private JNumberField		optionMaturityTextField		= new JNumberField(2.0,	 new DecimalFormat("0.00"), this);
	private JNumberField		optionStrikeTextField		= new JNumberField(1.0,	 new DecimalFormat("0.00"), this);

	/**
	 * Hedge parameters
	 */
	private JNumberField		hedgeRiskFreeRateTextField	= new JNumberField(0.05, new DecimalFormat("0.00"), this);
	private JNumberField		hedgeVolatilityTextField	= new JNumberField(0.5,	 new DecimalFormat("0.00"), this);

	private JComboBox<String> 		comboBoxHedgeStrategy;

	/**
	 * Output
	 */
	private XYSeriesCollection	datasetPayoff			= new XYSeriesCollection();
	private XYSeriesCollection	datasetHistogram		= new XYSeriesCollection();
	private JTextArea			statusLine				= new JTextArea("");


	public HedgeSimulatorPanel() {
		super();
		init();
	}

	/* Applet initialization method. */
	public void init() {
		this.setSize(900, 600);

		/*
		 * Initialize the GUI
		 */

		// We use the GridBagConstraints throughout to specify the postion of the GUI component
		final GridBagConstraints c = new GridBagConstraints();

		// Set up a panel containing the input of the underlying dynamics
		final JPanel inputModelParametersPanel = new JPanel();
		inputModelParametersPanel.setLayout(new GridBagLayout());
		inputModelParametersPanel.setBorder(BorderFactory.createTitledBorder("Underlying Dynamics"));

		// General parameters
		c.weightx = 0.0;
		c.weighty = 0.0;
		c.insets =  new Insets(3,3,3,3);
		c.anchor = GridBagConstraints.LINE_START;

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;		c.gridy = 0;		inputModelParametersPanel.add(new JLabel("Initial value: ",SwingConstants.TRAILING),c);
		c.gridx = 0;		c.gridy = 1;		inputModelParametersPanel.add(new JLabel("Risk free rate: ",SwingConstants.TRAILING),c);
		c.gridx = 0;		c.gridy = 2;		inputModelParametersPanel.add(new JLabel("Volatility: ",SwingConstants.TRAILING),c);
		c.fill = GridBagConstraints.NONE;
		c.gridx = 1;		c.gridy = 0;		inputModelParametersPanel.add(initialValueTextField,c);
		c.gridx = 1;		c.gridy = 1;		inputModelParametersPanel.add(riskFreeRateTextField,c);
		c.gridx = 2;		c.gridy = 1;		inputModelParametersPanel.add(createButton("+","riskFreeRate++"),c);
		c.gridx = 3;		c.gridy = 1;		inputModelParametersPanel.add(createButton("-","riskFreeRate--"),c);
		c.gridx = 1;		c.gridy = 2;		inputModelParametersPanel.add(volatilityTextField,c);
		c.gridx = 2;		c.gridy = 2;		inputModelParametersPanel.add(createButton("+","volatility++"),c);
		c.gridx = 3;		c.gridy = 2;		inputModelParametersPanel.add(createButton("-","volatility--"),c);

		// Set up a panel containing the input of the hedge stategie
		final JPanel inputHedgeParametersPanel = new JPanel();
		inputHedgeParametersPanel.setLayout(new GridBagLayout());
		inputHedgeParametersPanel.setBorder(BorderFactory.createTitledBorder("Hedge assumptions"));

		// Create combo box (selection) of interpolation method
		final String[] items2 = {"Delta hedge", "Delta-Gamma hedge (with 5 year option)", "Delta-Vega hedge (with 5 year option)"};
		comboBoxHedgeStrategy = new JComboBox<String>(items2);
		comboBoxHedgeStrategy.setMaximumSize(comboBoxHedgeStrategy.getPreferredSize());
		comboBoxHedgeStrategy.setSelectedIndex(0);
		comboBoxHedgeStrategy.addActionListener(this);

		// General parameters
		c.weightx = 0.0;
		c.weighty = 0.0;
		c.insets =  new Insets(3,3,3,3);
		c.anchor = GridBagConstraints.LINE_START;

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;		c.gridy = 0;		inputHedgeParametersPanel.add(new JLabel("Strategy: ",SwingConstants.TRAILING),c);
		c.gridx = 0;		c.gridy = 1;		inputHedgeParametersPanel.add(new JLabel("Risk free rate: ",SwingConstants.TRAILING),c);
		c.gridx = 0;		c.gridy = 2;		inputHedgeParametersPanel.add(new JLabel("Volatility: ",SwingConstants.TRAILING),c);
		c.gridx = 0;		c.gridy = 3;		inputHedgeParametersPanel.add(new JLabel("Number of hedge rebalancings: ",SwingConstants.TRAILING),c);
		c.fill = GridBagConstraints.NONE;	c.gridwidth = 3;
		c.gridx = 1;		c.gridy = 0;		inputHedgeParametersPanel.add(comboBoxHedgeStrategy,c);
		c.gridwidth = 1;
		c.gridx = 1;		c.gridy = 1;		inputHedgeParametersPanel.add(hedgeRiskFreeRateTextField,c);
		c.gridx = 2;		c.gridy = 1;		inputHedgeParametersPanel.add(createButton("+","hedgeRiskFreeRate++"),c);
		c.gridx = 3;		c.gridy = 1;		inputHedgeParametersPanel.add(createButton("-","hedgeRiskFreeRate--"),c);
		c.gridx = 1;		c.gridy = 2;		inputHedgeParametersPanel.add(hedgeVolatilityTextField,c);
		c.gridx = 2;		c.gridy = 2;		inputHedgeParametersPanel.add(createButton("+","hedgeVolatility++"),c);
		c.gridx = 3;		c.gridy = 2;		inputHedgeParametersPanel.add(createButton("-","hedgeVolatility--"),c);
		c.gridx = 1;		c.gridy = 3;		inputHedgeParametersPanel.add(deltaTTextField,c);
		c.gridx = 2;		c.gridy = 3;		inputHedgeParametersPanel.add(createButton("+","deltaT++"),c);
		c.gridx = 3;		c.gridy = 3;		inputHedgeParametersPanel.add(createButton("-","deltaT--"),c);

		// Create panel with scenario buttons
		final JPanel scenarioPanel = new JPanel();
		scenarioPanel.setLayout(new BoxLayout(scenarioPanel, BoxLayout.X_AXIS));
		for(int scenarioNumber=1; scenarioNumber<=5; scenarioNumber++) {
			final JButton scenarioButton = new JButton("Scenario " + scenarioNumber);
			scenarioButton.addActionListener(this);
			scenarioButton.setActionCommand("Scenario " + scenarioNumber);
			scenarioPanel.add(scenarioButton);
		}
		scenarioPanel.add(Box.createGlue());
		scenarioPanel.setBorder(BorderFactory.createTitledBorder("Predefined scenarios"));

		// Generate plots

		// Set up a panel containing the charts
		final JPanel chartPanel = new JPanel();
		chartPanel.setLayout(new BoxLayout(chartPanel, BoxLayout.X_AXIS));
		chartPanel.setBorder(BorderFactory.createTitledBorder("Results"));

		final StandardXYItemRenderer renderer = new StandardXYItemRenderer(StandardXYItemRenderer.SHAPES);
		renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-1.0, -1.0, 2.0, 2.0));
		renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-0.9, -0.9, 1.8, 1.8));
		renderer.setSeriesPaint(0, new java.awt.Color(0, 255, 0));
		renderer.setSeriesPaint(1, new java.awt.Color(255, 0, 0));

		NumberAxis xAxis = new NumberAxis("Underlying");
		NumberAxis yAxis = new NumberAxis("Portfolio Value");
		xAxis.setNumberFormatOverride(new DecimalFormat("0.0"));
		yAxis.setNumberFormatOverride(new DecimalFormat("0.0"));
		xAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		yAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		xAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		yAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		xAxis.setRange( 0.0,2.5);
		yAxis.setRange(-0.5,2.5);

		final XYPlot plot = new XYPlot(datasetPayoff, xAxis, yAxis, renderer);
		plot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
		plot.setDomainAxisLocation(AxisLocation.BOTTOM_OR_LEFT);

		final JFreeChart chart = new JFreeChart("Simulation results",
				new Font("SansSerif", Font.BOLD, 14), plot, false);
		chartPanel.add(new ChartPanel(
				chart,
				320, 320,	// size
				320, 320,	// minimum size
				2048, 2048,	// maximum size
				false, true, true, true, true, false		// useBuffer, properties, save, print, zoom, tooltips
				));

		final XYAreaRenderer areaRenderer = new XYAreaRenderer(XYAreaRenderer.AREA);
		xAxis = new NumberAxis("Hedge error");
		yAxis = new NumberAxis("Occurence");
		xAxis.setNumberFormatOverride(new DecimalFormat("0.0"));
		yAxis.setNumberFormatOverride(new DecimalFormat("0.0%"));
		xAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		yAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		xAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		yAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
		xAxis.setRange(-0.5,0.5);

		final XYPlot plot2 = new XYPlot(datasetHistogram, xAxis, yAxis, areaRenderer);
		plot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
		plot.setDomainAxisLocation(AxisLocation.BOTTOM_OR_LEFT);

		final JFreeChart chart2 = new JFreeChart("Distribution of hedge error",
				new Font("SansSerif", Font.BOLD, 14), plot2, false);
		chartPanel.add(new ChartPanel(
				chart2,
				320, 320,	// size
				320, 320,	// minimum size
				2048, 2048,	// maximum size
				false, true, true, true, true, false	// useBuffer, properties, save, print, zoom, tooltips
				));

		// Add the various panels created above to the applet. */
		final JPanel horizontalLayoutPanel = new JPanel();
		horizontalLayoutPanel.setLayout(new BoxLayout(horizontalLayoutPanel, BoxLayout.X_AXIS));
		horizontalLayoutPanel.add(inputModelParametersPanel);
		horizontalLayoutPanel.add(inputHedgeParametersPanel);

		inputPanel = new JPanel();
		inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
		inputPanel.add(horizontalLayoutPanel);
		//		inputPanel.add(scenarioPanel);
		inputPanel.setBorder(BorderFactory.createTitledBorder("Model specification"));

		final JPanel bottomLinePanel = new JPanel();
		bottomLinePanel.setLayout(new BoxLayout(bottomLinePanel, BoxLayout.X_AXIS));
		bottomLinePanel.add(statusLine);
		statusLine.setEditable(false);
		statusLine.setLineWrap(true);
		statusLine.setWrapStyleWord(true);
		statusLine.setFont(new Font("Courier", Font.PLAIN, 18));
		bottomLinePanel.add(Box.createHorizontalGlue());

		final Container cp = this;
		cp.setLayout(new BoxLayout(cp, BoxLayout.Y_AXIS));

		inputPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
		chartPanel.setPreferredSize(new Dimension(900, 200));
		bottomLinePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
		cp.add(Box.createVerticalStrut(10));
		//		cp.add(Box.createVerticalGlue());
		cp.add(inputPanel);
		//		cp.add(Box.createVerticalGlue());
		cp.add(chartPanel);
		//		cp.add(Box.createVerticalGlue());
		cp.add(bottomLinePanel);
		this.setSize(900,600);

		// Call updateData() - initializes the dataseries for the charts
		setContainerEnabled(inputPanel,false);
		final Thread calculationThread = new Thread(this);
		calculationThread.start();
	}

	/**
	 */
	private JButton createButton(String buttonLabel, String commandString) {
		final JButton button = new JButton(buttonLabel);
		button.setActionCommand(commandString);
		button.addActionListener(this);
		return button;
	}

	/* Start the applet. */
	public void start() {
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
		/*		catch(Exception e) {
			setContainerEnabled(inputPanel,true);
			System.err.println(e);
			System.err.println(e.getLocalizedMessage());
		}
		 */		setContainerEnabled(inputPanel,true);
	}

	/* Stop (i.e., pause) the applet. */
	public void stop() {
	}

	// Handle action events generated by applet controls.
	@Override
	public void actionPerformed(ActionEvent e) {

		// Retrieve the command associated with the action. The command is the name of the visual component the triggered the event.
		final String commandString = e.getActionCommand();

		// Check events
		if(commandString.equals("Scenario 1")) {
		}
		else if(commandString.equals("Scenario 2")) {
		}
		else if(commandString.equals("Scenario 3")) {
		}
		else if(commandString.equals("Scenario 4")) {
		}
		else if(commandString.equals("Scenario 5")) {
		}
		else if(commandString.equals("riskFreeRate++")) {
			riskFreeRateTextField.add(+0.01);
		} else if(commandString.equals("riskFreeRate--")) {
			riskFreeRateTextField.add(-0.01);
		} else if(commandString.equals("volatility++")) {
			volatilityTextField.add(+0.01);
		} else if(commandString.equals("volatility--")) {
			volatilityTextField.add(-0.01);
		} else if(commandString.equals("deltaT++")) {
			deltaTTextField.setValue((int)deltaTTextField.getValue().doubleValue()*2);
		} else if(commandString.equals("deltaT--")) {
			deltaTTextField.setValue((int)deltaTTextField.getValue().doubleValue()/2);
		} else if(commandString.equals("optionMaturity++")) {
			optionMaturityTextField.add(+0.5);
		} else if(commandString.equals("optionMaturity--")) {
			optionMaturityTextField.add(-0.5);
		} else if(commandString.equals("optionStrike++")) {
			optionStrikeTextField.add(+0.1);
		} else if(commandString.equals("optionStrike--")) {
			optionStrikeTextField.add(-0.1);
		} else if(commandString.equals("hedgeRiskFreeRate++")) {
			hedgeRiskFreeRateTextField.add(+0.01);
		} else if(commandString.equals("hedgeRiskFreeRate--")) {
			hedgeRiskFreeRateTextField.add(-0.01);
		} else if(commandString.equals("hedgeVolatility++")) {
			hedgeVolatilityTextField.add(+0.01);
		} else if(commandString.equals("hedgeVolatility--")) {
			hedgeVolatilityTextField.add(-0.01);
		}

		// Update the data
		setContainerEnabled(inputPanel,false);
		final Thread calculationThread = new Thread(this);
		calculationThread.start();
	}


	private void updateData() {
		try {

			statusLine.append(" - Calculating...");

			// Adjust deltaT to a valid value
			int			numberOfTimeSteps	= (int)deltaTTextField.getValue().doubleValue();
			double		deltaT = timeHorizon/(numberOfTimeSteps);
			if(deltaT > timeHorizon) {
				deltaT = timeHorizon;
			}
			if(deltaT < 2.0/730) {
				deltaT = 2.0/730;
			}
			numberOfTimeSteps = (int)Math.round(timeHorizon/deltaT);
			deltaT = timeHorizon/(numberOfTimeSteps);
			deltaTTextField.setValue(numberOfTimeSteps);

			/*
			 * Create the model and the product acording to specification
			 *
			 */
			// Create the time discretization
			final TimeDiscretizationFromArray timeDiscretizationFromArray = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps+1, deltaT);

			// Create an instance of a black scholes model
			final MonteCarloBlackScholesModel blackModel = new MonteCarloBlackScholesModel(
					timeDiscretizationFromArray,
					numberOfPaths,
					initialValueTextField.getValue().doubleValue(),
					riskFreeRateTextField.getValue().doubleValue(),
					volatilityTextField.getValue().doubleValue());

			// Create product (hedge portfolio)
			BlackScholesHedgedPortfolio hedgedPortfolio;
			switch(comboBoxHedgeStrategy.getSelectedIndex()) {
			case 0: // Delta hedge
			default:
				hedgedPortfolio = new BlackScholesHedgedPortfolio(
						optionMaturityTextField.getValue().doubleValue(),
						optionStrikeTextField.getValue().doubleValue(),
						hedgeRiskFreeRateTextField.getValue().doubleValue(),
						hedgeVolatilityTextField.getValue().doubleValue());
				break;
			case 1: // Gamma hedge
				hedgedPortfolio = new BlackScholesHedgedPortfolio(
						optionMaturityTextField.getValue().doubleValue(),
						optionStrikeTextField.getValue().doubleValue(),
						hedgeRiskFreeRateTextField.getValue().doubleValue(),
						hedgeVolatilityTextField.getValue().doubleValue(),
						5.0,			// gammaHedgeOptionMaturity
						1.0,			// gammaHedgeOptionStrike
						BlackScholesHedgedPortfolio.HedgeStrategy.deltaGammaHedge
						);
				break;
			case 2: // Vega hedge
				hedgedPortfolio = new BlackScholesHedgedPortfolio(
						optionMaturityTextField.getValue().doubleValue(),
						optionStrikeTextField.getValue().doubleValue(),
						hedgeRiskFreeRateTextField.getValue().doubleValue(),
						hedgeVolatilityTextField.getValue().doubleValue(),
						5.0,			// gammaHedgeOptionMaturity
						1.0,			// gammaHedgeOptionStrike
						BlackScholesHedgedPortfolio.HedgeStrategy.deltaVegaHedge
						);
				break;
			}

			// Get price (will init the hedge information)
			final double hedgeCost = hedgedPortfolio.getValue(0, blackModel).getAverage();

			final double optionMaturity	= optionMaturityTextField.getValue().doubleValue();
			final double optionStrike		= optionStrikeTextField.getValue().doubleValue();

			final RandomVariable portfolioValue	= hedgedPortfolio.getValue(optionMaturity,blackModel);
			final double[]				error			= new double[blackModel.getNumberOfPaths()];


			final RandomVariable underlyingAtMaturity = blackModel.getAssetValue(blackModel.getTimeIndex(optionMaturity),0);

			/*
			 * Update data for plots
			 */
			final int numberOfPaths = underlyingAtMaturity.size();
			double errorMin = 0.0; double errorMax = 0.0;
			final XYSeries seriesPortfolio		= new XYSeries("Portfolio value");
			final XYSeries seriesOption		= new XYSeries("Option value");
			for(int path=0; path<numberOfPaths; path++) {
				final double valueOfUnderlying = underlyingAtMaturity.get(path);
				// Look what happens if you exchange here
				final double valueOfPortfolio = portfolioValue.get(path);
				final double valueOfOption = Math.max(valueOfUnderlying-optionStrike,0.0);

				error[path] = valueOfPortfolio - valueOfOption;
				errorMin = Math.min(errorMin, error[path]);
				errorMax = Math.max(errorMax, error[path]);

				seriesPortfolio.add(valueOfUnderlying, valueOfPortfolio);
				seriesOption.add(valueOfUnderlying, valueOfOption);
			}
			datasetPayoff.removeAllSeries();
			datasetPayoff.addSeries(seriesOption);
			datasetPayoff.addSeries(seriesPortfolio);

			// Create binning for histogram
			final int			numberOfErrorBins	= 64;
			final XYSeries		seriesError			= new XYSeries("Error");
			final int errorCount[] = new int[numberOfErrorBins];
			java.util.Arrays.fill(errorCount,0);
			for(int path=0; path<numberOfPaths; path++) {
				final int indexOfErrorBin = (int)(((error[path] - errorMin) / (errorMax-errorMin) * (numberOfErrorBins-1)));
				errorCount[indexOfErrorBin]++;
			}

			for(int indexOfErrorBin=1; indexOfErrorBin<numberOfErrorBins-1; indexOfErrorBin++) {
				final double densityValue = (double)(errorCount[indexOfErrorBin-1]+errorCount[indexOfErrorBin]+errorCount[indexOfErrorBin+1]) / (double)(3*numberOfPaths);
				//			seriesError.add(errorMin+((double)(indexOfErrorBin)+0.05)/((double) numberOfErrorBins)*(errorMax-errorMin),densityValue);
				seriesError.add(errorMin+((indexOfErrorBin)+0.50)/(numberOfErrorBins)*(errorMax-errorMin),densityValue);
				//			seriesError.add(errorMin+((double)(indexOfErrorBin)+0.95)/((double) numberOfErrorBins)*(errorMax-errorMin),densityValue);
			}
			datasetHistogram.removeAllSeries();
			datasetHistogram.addSeries(seriesError);

			// Calculate statistical parameters
			double mean			= 0.0;
			double secondMoment = 0.0;
			for(int path=0; path<numberOfPaths; path++) {
				mean				+= error[path];
				secondMoment		+= error[path]*error[path];
			}
			mean				/= numberOfPaths;
			secondMoment		/= numberOfPaths;

			// Update status line
			final DecimalFormat formatter = new DecimalFormat("0.000");
			statusLine.setText(
					"\n" +
							"\tMaturity........................: " + optionMaturity + " years.\n" +
							"\tStrike..........................: " + optionStrike + " €\n\n" +
							"\tRe-hedging done every...........: "+formatter.format(deltaT * 365)+" days.\n\n"+
							"\tCost of hedging (option price)..: "+formatter.format(hedgeCost)+" €.\n\n"+
							"\tMean of hedge error..................: "+formatter.format(mean)+" €.\n"+
							"\tStandard deviation of hedge error....: "+formatter.format(Math.sqrt(secondMoment-mean*mean))+" €.\n"+
					"");
		} catch (final CalculationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void setContainerEnabled(Container container, boolean enabled)
	{
		final Component[] components = container.getComponents();

		for(int i=0; i<components.length; i++) {
			components[i].setEnabled(enabled);
			if(components[i] instanceof Container) {
				setContainerEnabled((Container)components[i], enabled);
			}
		}
	}
}