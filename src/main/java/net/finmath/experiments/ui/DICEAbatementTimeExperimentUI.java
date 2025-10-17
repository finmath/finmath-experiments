package net.finmath.experiments.ui;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import net.finmath.climate.models.ClimateModel;
import net.finmath.climate.models.dice.DICEModel;
import net.finmath.experiments.ui.parameter.BooleanParameter;
import net.finmath.experiments.ui.parameter.DoubleParameter;
import net.finmath.experiments.ui.utilities.DICEModelPlots;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class DICEAbatementTimeExperimentUI extends ExperimentUI {

	private static final double timeStep = 1.0;
	private static final double timeHorizon = 500.0;

	private final DecimalFormat numberDigit3 = new DecimalFormat("#.000");
	private final DecimalFormat numberPercent2 = new DecimalFormat("#.00%");

	private final DICEModelPlots plots = new DICEModelPlots();

	public DICEAbatementTimeExperimentUI() {
		super(List.of(
				new DoubleParameter("Discount Rate", 0.03, 0.01, 0.05),
				new DoubleParameter("Abatement Max Time", 50.0, 10.0, 200.0),
				new BooleanParameter("Show Cost", false)
				));
	}


	public String getTitle() { return "DICE Model - One Parametric Abatement Model - NOT CALIBRATED"; }

	public void runCalculation(BooleanSupplier isCancelled, DoubleConsumer progress) {
		Map<String, Object> currentParameterSet = getExperimentParameters().stream().collect(Collectors.toMap(p -> p.getBindableValue().getName(), p -> p.getBindableValue().getValue()));

		System.out.println("Calculation with Parameters: " + currentParameterSet);

		/*
		 * Discount rate
		 */
		final double discountRate = (Double)currentParameterSet.get("Discount Rate");

		/*
		 * Parameters for the abatement model (abatement = fraction of industrial CO2 reduction; 1.00 ~ 100 % reduction ~ carbon neutral).
		 */
		final double abatementInitial = 0.03;
		final double abatementMax = 1.00;
		final double abatementMaxTime = (Double)currentParameterSet.get("Abatement Max Time");

		/*
		 * Create a time discretization
		 */
		final int numberOfTimeSteps = (int)Math.round(timeHorizon / timeStep);
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps, timeStep);

		/*
		 * Create our abatement model: it is a piecewise linear funtion: starting in abatementInitial, then reaching abatementMax in abatementMaxTime years, then staying at abatementMax.
		 */
		final UnaryOperator<Double> abatementFunction = time -> Math.min(abatementInitial + (abatementMax-abatementInitial)/abatementMaxTime * time, abatementMax);

		/*
		 * Create our savings rate model: a constant.
		 */
		final UnaryOperator<Double> savingsRateFunction = time -> 0.26;

		/*
		 * Create the DICE model from the given parameters.
		 */
		final ClimateModel climateModel = new DICEModel(timeDiscretization, abatementFunction, savingsRateFunction, discountRate);

		/*
		 * Plots
		 */

		if(!Thread.currentThread().isInterrupted() && !isCancelled.getAsBoolean()) {
			synchronized(this) {
			String spec = "T(\u03BC=1) =" + numberDigit3.format(abatementMaxTime) + ", r = " + numberPercent2.format(discountRate);		

			plots.plot(climateModel, spec);

			boolean showCost = (boolean)currentParameterSet.get("Show Cost");
			if(showCost) {
				plots.plotCost(climateModel, discountRate, spec);
			}
			else {
				plots.closeCost();
			}
			}
		}
	}


	@Override
	protected void onClose() {
		synchronized(this) {
		super.onClose();

		if(plots != null) plots.close();
		}
	}
}
