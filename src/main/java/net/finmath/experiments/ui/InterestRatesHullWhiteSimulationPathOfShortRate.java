package net.finmath.experiments.ui;

import java.awt.BasicStroke;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.finmath.climateschool.utilities.ModelFactory;
import net.finmath.climateschool.utilities.RandomOperators;
import net.finmath.experiments.ui.parameter.DoubleParameter;
import net.finmath.montecarlo.interestrate.TermStructureMonteCarloSimulationModel;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.plots.GraphStyle;
import net.finmath.plots.Plot2D;
import net.finmath.plots.Plotable2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.plots.Point2D;
import net.finmath.stochastic.RandomOperator;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class InterestRatesHullWhiteSimulationPathOfShortRate extends ExperimentUI {

	private static final double timeStep = 1.0;
	private static final double timeHorizon = 500.0;

	private final DecimalFormat numberDigit3 = new DecimalFormat("#.000");
	private final DecimalFormat numberPercent2 = new DecimalFormat("#.00%");

	Plot2D plot = null;

	public InterestRatesHullWhiteSimulationPathOfShortRate() {
		super(List.of(
				new DoubleParameter("Initial Value", 0.03, 0.01, 0.05),
				new DoubleParameter("Long Term Value", 0.03, 0.01, 0.05),
				new DoubleParameter("Mean Reversion Speed", 0.005, 0.001, 0.050),
				new DoubleParameter("Volatility", 0.001, 0.0001, 0.005)
				));
	}


	public String getTitle() { return "Hull White Model - Simulation of Interest Rate (Short Rate)"; }

	public void runCalculation(BooleanSupplier isCancelled, DoubleConsumer progress) {
		Map<String, Object> currentParameterSet = getExperimentParameters().stream().collect(Collectors.toMap(p -> p.getBindableValue().getName(), p -> p.getBindableValue().getValue()));

		System.out.println("Calculation with Parameters: " + currentParameterSet);

		double shortRateInitialValue = (Double)currentParameterSet.get("Initial Value");
		double shortRateLongTermValue = (Double)currentParameterSet.get("Long Term Value");
		double shortRateMeanreversion = (Double)currentParameterSet.get("Mean Reversion Speed");
		double shortRateVolatility = (Double)currentParameterSet.get("Volatility");

		/*
		 * Create a time discretization
		 */
		final int numberOfTimeSteps = (int) Math.ceil(timeHorizon/timeStep);
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps, timeStep);
		final TermStructureMonteCarloSimulationModel interestRateModel = ModelFactory.getInterestRateModel(
				timeDiscretization,
				timeDiscretization,
				new double[] { 0.0, 1.0, timeHorizon }, new double[] { shortRateInitialValue, shortRateInitialValue, shortRateLongTermValue },
				shortRateVolatility, shortRateMeanreversion, 1000);

		final DoubleToRandomVariableFunction numeraire = t -> interestRateModel.getNumeraire(Math.min(t, 500));
		final DoubleToRandomVariableFunction shortRate = t -> numeraire.apply(t+1).div(numeraire.apply(t)).log();
		final Function<Double, DoubleUnaryOperator> rateAtQuantile = q -> {
			return t -> {
				try {
					final RandomOperator es;
					if(q >= 0) {
						es = RandomOperators.leftTailExpectedShortFall(q);
					} else {
						es = RandomOperators.rightTailExpectedShortFall(1+q);
					}
					return es.apply(numeraire.apply(t+1).invert()).log().mult(-1).div(t+1).getAverage();
				} catch (final Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return 0;
			};
		};

		final String titleSpec = "\u03c3=" + numberPercent2.format(shortRateVolatility) + ", a=" + numberPercent2.format(shortRateMeanreversion);

		int dotSize = 1;
		int numberOfPathsToShow = 50;

		List<RandomVariable> valueSlices = new ArrayList<RandomVariable>();
		for(int j=0; j<timeDiscretization.getNumberOfTimes()-1; j++) {
			double time = timeDiscretization.getTime(j);
			try {
				valueSlices.add(shortRate.apply(time));
			} catch (Exception e) {
				valueSlices.add(Scalar.of(Double.NaN));
			}
			progress.accept((double)j/timeDiscretization.getNumberOfTimes()/2);
		}

		List<Plotable2D> plotables = new ArrayList<Plotable2D>();
		for(int i=0; i<numberOfPathsToShow; i++) {
			final List<Point2D> series = new ArrayList<Point2D>();
			for(int j=0; j<timeDiscretization.getNumberOfTimes()-1; j++) {
				double time = timeDiscretization.getTime(j);
				series.add(new Point2D(time, valueSlices.get(j).get(i)));
			}
			plotables.add(new PlotablePoints2D("Scatter", series, new GraphStyle(new Rectangle(dotSize, dotSize), new BasicStroke(), null)));
			progress.accept(0.5+(double)i/numberOfPathsToShow/2);
		}

		synchronized(this) {
			if(!Thread.currentThread().isInterrupted() && !isCancelled.getAsBoolean()) {
				if(plot == null) {
					plot = new Plot2D(plotables);
					plot.setTitle("Short Rate (" + titleSpec + ")").setXAxisLabel("time (years)").setYAxisLabel("Short Rate (r)");
					plot.setYRange(-0.02, 0.20);
					plot.show();
				}
				else {
					plot.setTitle("Short Rate (" + titleSpec + ")").setXAxisLabel("time (years)").setYAxisLabel("Short Rate (r)");
					plot.update(plotables);
				}
			}
		}
	}

	@Override
	protected void onClose() {
		synchronized (this) {
			super.onClose();
			System.out.println("Closing plot");
			if(plot != null) {
				plot.close();
				plot = null;
			}
		}
	}
}
