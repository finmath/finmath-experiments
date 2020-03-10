/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 27.01.2008
 */
package net.finmath.experiments.blackScholes;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

import net.finmath.swing.JNumberField;

/**
 * @author Christian Fries
 *
 */
public class BlackScholesOptionCalculator extends JApplet implements ActionListener {

	/**
	 *
	 */
	private static final long serialVersionUID = -6829185838379446505L;

	private JPanel specificationPanel = null;
	private JNumberField spot = null;
	private JLabel labelForSpot = null;
	private JLabel labelForVolatility = null;
	private JNumberField riskFreeRate = null;
	private JLabel labelForRate = null;
	private JPanel modelPanel = null;
	private JNumberField volatility = null;
	private JPanel optionPanel = null;
	private JNumberField optionStrike = null;
	private JLabel labelForMaturity = null;
	private JNumberField optionMaturity = null;
	private JLabel labelForValue = null;
	private JNumberField optionValue = null;
	private JLabel labelForStrike = null;

	private JPanel appletPanel = null;

	private JTextArea descriptionTextField = null;

	/**
	 * This is the BlackScholesOptionCaclulator default constructor
	 */
	public BlackScholesOptionCalculator() {
		super();
	}

	/**
	 * Start this as a Java applet.
	 *
	 * @param argv not used
	 */
	public static void main (String argv[]) {
		final JApplet applet = new BlackScholesOptionCalculator();
		final JFrame frame = new JFrame("BlackScholesOptionCalculator");			// create graphics frame
		frame.getContentPane().add(applet);									// add the applet
		frame.setSize(500,300);
		applet.init();														// initialise applet
		applet.start();
		frame.setVisible(true);
	}

	/* (non-Javadoc)
	 * @see java.applet.Applet#init()
	 */
	@Override
	public void init() {
		this.setSize(450, 250);
		this.setContentPane(getAppletPanel());
		// The following will initially update the value in the value field.
		this.actionPerformed(new ActionEvent(this, 0, "init"));
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// If the is an input in any field, this method will be called
		if(e.getSource() == optionValue) {
			// Value was changed, calculate implied volatility
			final double payoffUnit	= Math.exp(-this.riskFreeRate.getDoubleValue()* this.optionMaturity.getDoubleValue());
			final double forward		= this.spot.getDoubleValue() / payoffUnit;
			final double impliedVolatility = net.finmath.functions.AnalyticFormulas.blackScholesOptionImpliedVolatility(forward, this.optionMaturity.getDoubleValue(), this.optionStrike.getDoubleValue(), payoffUnit, this.optionValue.getDoubleValue());
			this.volatility.setValue(impliedVolatility);
		}
		final double value = net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(this.spot.getDoubleValue(), this.riskFreeRate.getDoubleValue(), this.volatility.getDoubleValue(), this.optionMaturity.getDoubleValue(), this.optionStrike.getDoubleValue());
		this.optionValue.setValue(value);
	}

	/**
	 * This method initializes specificationPanel
	 *
	 * @return javax.swing.JPanel
	 */
	private JPanel getSpecificationPanel() {
		if (specificationPanel == null) {
			labelForRate = new JLabel();
			labelForRate.setText("risk free rate (r)");
			labelForRate.setHorizontalAlignment(SwingConstants.TRAILING);
			labelForVolatility = new JLabel();
			labelForVolatility.setText("volatility");
			labelForVolatility.setHorizontalAlignment(SwingConstants.TRAILING);
			labelForSpot = new JLabel();
			labelForSpot.setText("spot");
			labelForSpot.setHorizontalAlignment(SwingConstants.TRAILING);
			specificationPanel = new JPanel();
			specificationPanel.setLayout(new BoxLayout(getSpecificationPanel(), BoxLayout.X_AXIS));
			specificationPanel.add(getModelPanel(), null);
			specificationPanel.add(getOptionPanel(), null);
		}
		return specificationPanel;
	}

	/**
	 * This method initializes spot
	 *
	 * @return net.finmath.swing.JNumberField
	 */
	private JNumberField getSpot() {
		if (spot == null) {
			spot = new JNumberField();
			spot.setValue(100);
			spot.addActionListener(this);
		}
		return spot;
	}

	/**
	 * This method initializes riskFreeRate
	 *
	 * @return net.finmath.swing.JNumberField
	 */
	private JNumberField getRiskFreeRate() {
		if (riskFreeRate == null) {
			riskFreeRate = new JNumberField();
			riskFreeRate.setValue(0.05);
			riskFreeRate.addActionListener(this);
		}
		return riskFreeRate;
	}

	/**
	 * This method initializes modelPanel
	 *
	 * @return javax.swing.JPanel
	 */
	private JPanel getModelPanel() {
		if (modelPanel == null) {
			modelPanel = new JPanel();
			modelPanel.setLayout(new BoxLayout(getModelPanel(), BoxLayout.Y_AXIS));
			modelPanel.setBorder(BorderFactory.createTitledBorder(null, "Model Specification", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
			modelPanel.add(labelForSpot, null);
			modelPanel.add(getSpot(), null);
			modelPanel.add(labelForRate, null);
			modelPanel.add(getRiskFreeRate(), null);
			modelPanel.add(labelForVolatility, null);
			modelPanel.add(getVolatility(), null);
		}
		return modelPanel;
	}

	/**
	 * This method initializes volatility
	 *
	 * @return net.finmath.swing.JNumberField
	 */
	private JNumberField getVolatility() {
		if (volatility == null) {
			volatility = new JNumberField();
			volatility.addActionListener(this);
			volatility.setValue(0.20);
		}
		return volatility;
	}

	/**
	 * This method initializes optionPanel
	 *
	 * @return javax.swing.JPanel
	 */
	private JPanel getOptionPanel() {
		if (optionPanel == null) {
			labelForStrike = new JLabel();
			labelForStrike.setHorizontalAlignment(SwingConstants.TRAILING);
			labelForStrike.setText("strike");
			labelForValue = new JLabel();
			labelForValue.setText("value");
			labelForValue.setHorizontalAlignment(SwingConstants.TRAILING);
			labelForMaturity = new JLabel();
			labelForMaturity.setText("maturity");
			labelForMaturity.setHorizontalAlignment(SwingConstants.TRAILING);
			optionPanel = new JPanel();
			optionPanel.setLayout(new BoxLayout(getOptionPanel(), BoxLayout.Y_AXIS));
			optionPanel.setBorder(BorderFactory.createTitledBorder(null, "Option Specification", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
			optionPanel.add(labelForStrike, null);
			optionPanel.add(getOptionStrike(), null);
			optionPanel.add(labelForMaturity, null);
			optionPanel.add(getOptionMaturity(), null);
			optionPanel.add(labelForValue, null);
			optionPanel.add(getOptionValue(), null);
		}
		return optionPanel;
	}

	/**
	 * This method initializes optionStrike
	 *
	 * @return net.finmath.swing.JNumberField
	 */
	private JNumberField getOptionStrike() {
		if (optionStrike == null) {
			optionStrike = new JNumberField();
			optionStrike.setValue(100);
			optionStrike.addActionListener(this);
		}
		return optionStrike;
	}

	/**
	 * This method initializes optionMaturity
	 *
	 * @return net.finmath.swing.JNumberField
	 */
	private JNumberField getOptionMaturity() {
		if (optionMaturity == null) {
			optionMaturity = new JNumberField();
			optionMaturity.setValue(1.0);
			optionMaturity.addActionListener(this);
		}
		return optionMaturity;
	}

	/**
	 * This method initializes optionValue
	 *
	 * @return net.finmath.swing.JNumberField
	 */
	private JNumberField getOptionValue() {
		if (optionValue == null) {
			optionValue = new JNumberField();
			optionValue.addActionListener(this);
		}
		return optionValue;
	}

	/**
	 * This method initializes appletPanel
	 *
	 * @return javax.swing.JPanel
	 */
	private JPanel getAppletPanel() {
		if (appletPanel == null) {
			appletPanel = new JPanel();
			appletPanel.setLayout(new BoxLayout(getAppletPanel(), BoxLayout.Y_AXIS));
			appletPanel.setBorder(BorderFactory.createTitledBorder(null, "Tiny Black-Scholes Option Valuer", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
			appletPanel.add(getSpecificationPanel(), null);
			appletPanel.add(getDescriptionTextField(), null);
		}
		return appletPanel;
	}

	/**
	 * This method initializes descriptionTextField
	 *
	 * @return javax.swing.JTextField
	 */
	private JTextArea getDescriptionTextField() {
		if (descriptionTextField == null) {
			descriptionTextField = new JTextArea();
			descriptionTextField.setText("Enter a new value in any field and press enter. If you change 'value' then the (implied) volatility will be calculated. If you change any other field then the value will be calculated.");
			descriptionTextField.setEditable(false);
			descriptionTextField.setRows(3);
			descriptionTextField.setLineWrap(true);
			descriptionTextField.setWrapStyleWord(true);
		}
		return descriptionTextField;
	}

}
