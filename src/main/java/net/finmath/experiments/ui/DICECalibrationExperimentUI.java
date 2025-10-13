package net.finmath.experiments.ui;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import net.finmath.climate.models.ClimateModel;
import net.finmath.climate.models.dice.DICEModel;
import net.finmath.climateschool.utilities.AdamOptimizerUsingFiniteDifferences;
import net.finmath.climateschool.utilities.AdamOptimizerUsingFiniteDifferences.GradientMethod;
import net.finmath.climateschool.utilities.DICEModelPlots;
import net.finmath.experiments.ui.parameter.BooleanParameter;
import net.finmath.experiments.ui.parameter.DoubleParameter;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class DICECalibrationExperimentUI extends ExperimentUI {

	private static final double timeStep = 1.0;
	private static final double timeHorizon = 500.0;

	private final DecimalFormat numberDigit3 = new DecimalFormat("#.000");
	private final DecimalFormat numberPercent2 = new DecimalFormat("#.00%");

	double[] initialParameters;

	private final DICEModelPlots plots = new DICEModelPlots();

	public DICECalibrationExperimentUI() {
		super(List.of(
				new DoubleParameter("Discount Rate", 0.03, 0.01, 0.05),
				new BooleanParameter("Show Cost", false)
				//				new Parameter("Abatement Max Time", 50.0, 10.0, 200.0)
				));
	}

	public String getTitle() { return "DICE Model - Full Abatement Model - Optimized Emisison Path (Calibration)"; }

	public void runCalculation(BooleanSupplier isCancelled, DoubleConsumer progress) {
		Map<String, Object> currentParameterSet = getExperimentParameters().stream().collect(Collectors.toMap(p -> p.getBindableValue().getName(), p -> p.getBindableValue().getValue()));

		System.out.println("Calculation with Parameters: " + currentParameterSet);

		/*
		 * Create a time discretization
		 */
		final int numberOfTimeSteps = (int)Math.round(timeHorizon / timeStep);
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps, timeStep);

		/*
		 * Create our savings rate model: a constant.
		 */
		final UnaryOperator<Double> savingsRateFunction = time -> 0.26;

		/*
		 * Discount rate
		 */
		final double discountRate = (Double)currentParameterSet.get("Discount Rate");

		// Initial parameters for our abatement function
		if(initialParameters == null) {
			initialParameters = new double[timeDiscretization.getNumberOfTimes()];
			Arrays.fill(initialParameters, -Math.log(-Math.log(0.8)));
		}

		final AdamOptimizerUsingFiniteDifferences optimizer = new AdamOptimizerUsingFiniteDifferences(initialParameters, 800, 0.05, GradientMethod.AVERAGE) {
			private int iteration = 0;
			@Override
			public RandomVariable setValue(RandomVariable[] parameters) {
				if(Thread.currentThread().isInterrupted()) {
					this.stop();
				}

				double[] abatementParameter = Arrays.stream(parameters).mapToDouble(RandomVariable::getAverage).map(x -> Math.exp(-Math.exp(-x))).toArray();
				abatementParameter[0] = 0.03;
				initialParameters = Arrays.stream(parameters).mapToDouble(RandomVariable::getAverage).toArray();

				/*
				 * Create our abatement model
				 */
				final UnaryOperator<Double> abatementFunction = time -> abatementParameter[(int)Math.round(time/timeStep)];

				/*
				 * Create the DICE model
				 */
				final ClimateModel climateModel = new DICEModel(timeDiscretization, abatementFunction, savingsRateFunction, discountRate);

				final double value = climateModel.getValue().expectation().doubleValue();

				// Penalty for non-smoothness - it works without this, but this helps the optimizer to avoid onszillations (that are la
				double roughness = 0.0;
				for(int i=1; i<abatementParameter.length-2; i++) {
					roughness += Math.pow(abatementParameter[i+1] - abatementParameter[i], 2.0);
				}
				roughness = Math.sqrt(roughness);
				roughness /= abatementParameter.length;
				roughness *= 0.1;

				// Update the plot every 200 iterations
				if(iteration%200 == 0 && !Thread.currentThread().isInterrupted()) {
					String spec = "r = " + numberPercent2.format(discountRate) + "; value = " + numberDigit3.format(value);
					plots.plot(climateModel, spec);
					boolean showCost = (boolean)currentParameterSet.get("Show Cost");
					if(showCost) {
						plots.plotCost(climateModel, discountRate, spec);
					}
					else {
						plots.closeCost();
					}
				}
				iteration++;

				return Scalar.of(-value);// + roughness);
			}
		};

		optimizer.run();

		System.out.println("Optimizer finished.");

		// Get optimal value
		final RandomVariable[] bestParameters = optimizer.getBestFitParameters();
		double[] abatementParameter = Arrays.stream(bestParameters).mapToDouble(RandomVariable::getAverage).map(x -> Math.exp(-Math.exp(-x))).toArray();
		abatementParameter[0] = 0.03;
		System.out.println(Arrays.toString(abatementParameter));

		/*
		 * Create our abatement model
		 */
		final UnaryOperator<Double> abatementFunction = time -> abatementParameter[(int)Math.round(time/timeStep)];

		/*
		 * Create the DICE model
		 */
		final ClimateModel climateModel = new DICEModel(timeDiscretization, abatementFunction, savingsRateFunction, discountRate);

		/*
		 * Plots
		 */

		if(!Thread.currentThread().isInterrupted() && !isCancelled.getAsBoolean()) {
			synchronized(this) {
			String spec = "r = " + numberPercent2.format(discountRate);		
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
