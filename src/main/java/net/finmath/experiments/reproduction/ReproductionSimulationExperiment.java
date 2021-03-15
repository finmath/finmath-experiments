package net.finmath.experiments.reproduction;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.finmath.experiments.reproduction.ReproductionSimulationExperiment.StateProbabilities.State;
import net.finmath.plots.GraphStyle;
import net.finmath.plots.Plot2D;
import net.finmath.plots.Plotable2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.plots.Point2D;
import net.finmath.rootfinder.BisectionSearch;
import net.finmath.rootfinder.RootFinder;

public class ReproductionSimulationExperiment {

	private static final int maxIncubation = 25;

	private final double incubationMean;
	private final double incubationStdDev;
	private final int timeInfectious;

	private int currentTime = 0;

	public static class StateProbabilities {

		public enum State {
			UNINFECTED,
			INFECTED_NOT_INFECTIOUS,
			INFECTED_AND_INFECTIOUS,
			IMMUNE
		}

		private final Map<State, Double> probabilities;

		public StateProbabilities(double infectedNotInfectious, double infectedAndInfectious, double immune) {
			probabilities = Map.of(
					State.UNINFECTED, - infectedNotInfectious - infectedAndInfectious - immune,
					State.INFECTED_NOT_INFECTIOUS, infectedNotInfectious,
					State.INFECTED_AND_INFECTIOUS, infectedAndInfectious,
					State.IMMUNE, immune
					);
		}
	}

	private final List<StateProbabilities> stateProbabilitiyEvolution = new ArrayList<StateProbabilities>();


	public static double getCalibrateRate(double rateTarget, double incubationMean, double incubationStdDev, int timeInfectious) {
		final RootFinder rf = new BisectionSearch(1.00, 1.1);
		while(!rf.isDone()) {
			final double rate = rf.getNextPoint();

			final ReproductionSimulationExperiment sim = new ReproductionSimulationExperiment(incubationMean, incubationStdDev, timeInfectious);

			for(int i=0; i<200; i++) {
				final StateProbabilities prob = sim.evolve(rate, incubationMean, incubationStdDev);
			}
			final List<Double> rates = getCalculatedRates(getInfected(sim), 50, 100, 5);
			final double rateMeasured = rates.get(rates.size()-10);
			System.out.println(rateMeasured);
			rf.setValue(1.0+rateMeasured-rateTarget);
		}

		return rf.getBestPoint();

	}

	static List<Double> getCalculatedRates(List<Double> infected, int start, int end, int averagePeriod) {
		final List<Double> rate = new ArrayList<Double>();
		for(int j=start; j<end; j++) {
			double sum1 = 0.0;
			for(int k=averagePeriod; k<2*averagePeriod; k++) {
				sum1 += (infected.get(j-k)-infected.get(j-k-1));
			}
			double sum2 = 0.0;
			for(int k=0; k<averagePeriod; k++) {
				sum2 += (infected.get(j-k)-infected.get(j-k-1));
			}
			rate.add((sum2-sum1)/sum1/averagePeriod);
		}
		return rate;
	}

	public static void main(String[] args) throws IOException {

		createPlot("ConstantIncubationTimeOfMeasurement.pdf", 5.0, 0.01, 1, false);
		createPlot("ConstantIncubationTimeOfInfection.pdf", 5.0, 0.01, 1, true);

		createPlot("StochasticIncubationTimeOfMeasurement.pdf", 5.0, 4.0, 3, false);
		createPlot("StochasticIncubationTimeOfInfection.pdf", 5.0, 4.0, 3, true);

	}

	private static void createPlot(String filename, double incubationMean, double incubationStdDev, int timeInfectious, boolean useTimeOfInfection) throws IOException {
		final ReproductionSimulationExperiment sim = new ReproductionSimulationExperiment(incubationMean, incubationStdDev, timeInfectious);

		final double rate1 = getCalibrateRate(1.04, incubationMean, incubationStdDev, timeInfectious);
		final double rate2 = getCalibrateRate(1.02, incubationMean, incubationStdDev, timeInfectious);

		System.out.println(rate1);
		System.out.println(rate2);

		for(int i=0; i<500; i++) {
			if(i < 100) {
				final StateProbabilities prob = sim.evolve(rate1, incubationMean, incubationStdDev);
			}
			else {
				final StateProbabilities prob = sim.evolve(rate2, incubationMean, incubationStdDev);
			}
		}

		final List<Double> infected = getInfected(sim);

		final int plotStart = 50;
		final int plotEnd = 150;
		final int average = 5;

		final List<Double> day = IntStream.range(plotStart, plotEnd).asDoubleStream().map(x -> (useTimeOfInfection ? x - incubationMean : x)).boxed().collect(Collectors.toList());
		final List<Double> rate = getCalculatedRates(infected, plotStart, plotEnd, average);

		final List<Double> dayOfInventionX = IntStream.range(0, 500).mapToDouble(x -> 100.0).boxed().collect(Collectors.toList());
		final List<Double> dayOfInventionY = IntStream.range(0, 500).mapToDouble(x -> 1.01 + x*(1.05-1.01)/500.0).boxed().collect(Collectors.toList());

		final List<Point2D> series1 = new ArrayList<Point2D>();
		for(int i=0; i<day.size(); i++) {
			series1.add(new Point2D(day.get(i), 1.0+rate.get(i)));
		}
		final List<Point2D> series2 = new ArrayList<Point2D>();
		for(int i=0; i<dayOfInventionX.size(); i++) {
			series2.add(new Point2D(dayOfInventionX.get(i), dayOfInventionY.get(i)));
		}

		final List<Plotable2D> plotables = Arrays.asList(
				new PlotablePoints2D("Measurement", series1, new GraphStyle(new Rectangle(4, 4), null, Color.RED)),
				new PlotablePoints2D("Intervention", series2, new GraphStyle(new Rectangle(2, 2), null, Color.BLUE))
				);

		final Plot2D plot2 = new Plot2D(plotables);
		plot2
		.setTitle("Reproduction rate derived from simulated data\n (mean = " + incubationMean + ", std.dev = " + incubationStdDev + ")")
		.setXAxisLabel(useTimeOfInfection ? "Estimated time of infection (day)" : "Time of measurement (day)")
		.setYAxisLabel("Rate of New Infections")
		.setYAxisNumberFormat(new DecimalFormat("0.000"))
		.setIsLegendVisible(true);

		plot2.show();
		plot2.saveAsPDF(new File(filename), 600, 400);
	}

	private static List<Double> getInfected(ReproductionSimulationExperiment sim) {
		final List<Double> infected = new ArrayList<Double>();
		for(final StateProbabilities prob : sim.stateProbabilitiyEvolution) {
			infected.add(prob.probabilities.get(State.INFECTED_AND_INFECTIOUS)+prob.probabilities.get(State.IMMUNE));
		}
		return infected;
	}

	public ReproductionSimulationExperiment(double incubationMean, double incubationStdDev, int timeInfectious) {
		this.incubationMean = incubationMean;
		this.incubationStdDev = incubationStdDev;
		this.timeInfectious = timeInfectious;

		final double seed = 1E-12;

		// Seed the simulation
		stateProbabilitiyEvolution.add(new StateProbabilities(0.0, seed, 0.0));
		stateProbabilitiyEvolution.add(new StateProbabilities(0.0, seed, 0.0));
		stateProbabilitiyEvolution.add(new StateProbabilities(0.0, seed, 0.0));
		stateProbabilitiyEvolution.add(new StateProbabilities(0.0, seed, 0.0));
		stateProbabilitiyEvolution.add(new StateProbabilities(0.0, seed, 0.0));
		stateProbabilitiyEvolution.add(new StateProbabilities(0.0, 0.0, seed));
	}

	StateProbabilities evolve(double rate, double mean, double stddev) {

		/*
		 * Transformation to lognormal distribution parameters
		 */
		final double mu = Math.log(  mean / Math.sqrt(Math.pow(stddev / mean,2)+1 ) );
		final double sigma = Math.sqrt( 2 * (Math.log(mean)-mu) );

		final double[] incubation = new double[maxIncubation];

		double sum = 0.0;
		for(int i=0; i<incubation.length; i++) {
			final double x = i+1;
			final double p = 1.0/(x*Math.sqrt(2*Math.PI)*sigma) * Math.exp(-Math.pow(Math.log(x)-mu, 2.0)/(2*sigma*sigma));
			incubation[i] = p;
			sum += p;
		}

		// Renormalize
		for(int i=0; i<incubation.length; i++) {
			incubation[i] /= sum;
		}

		double avg = 0.0;
		for(int i=0; i<incubation.length; i++) {
			avg += (i+1) * incubation[i];
		}

		double var = 0.0;
		for(int i=0; i<incubation.length; i++) {
			var += Math.pow((i+1) - avg, 2.0) * incubation[i];
		}

		final StateProbabilities currentState = stateProbabilitiyEvolution.get(currentTime);
		final double currentUninfected = 1+currentState.probabilities.get(State.UNINFECTED);
		final double currentIntfectedAnd = currentState.probabilities.get(State.INFECTED_AND_INFECTIOUS);

		final double effectiveRate = solveForRate(rate, incubation, timeInfectious);

		final double newInfected = effectiveRate * currentIntfectedAnd * currentUninfected;

		final StateProbabilities lastState = stateProbabilitiyEvolution.get(stateProbabilitiyEvolution.size()-1);
		while(stateProbabilitiyEvolution.size() < currentTime+incubation.length+timeInfectious+1) {
			stateProbabilitiyEvolution.add(lastState);
		}

		for(int i=1; i<stateProbabilitiyEvolution.size()-currentTime; i++) {

			double p = 0;
			double q = 0;
			for(int j = 0; j<Math.min(i, incubation.length); j++) {
				if(i-j > timeInfectious) {
					q += incubation[j];
				} else {
					p += incubation[j];
				}
			}

			final StateProbabilities state = stateProbabilitiyEvolution.get(currentTime+i);
			final StateProbabilities newState = new StateProbabilities(
					state.probabilities.get(State.INFECTED_NOT_INFECTIOUS) + newInfected * (1-p-q),
					state.probabilities.get(State.INFECTED_AND_INFECTIOUS) + newInfected * p,
					state.probabilities.get(State.IMMUNE) + newInfected * q);

			stateProbabilitiyEvolution.set(currentTime+i, newState);
		}

		currentTime++;
		return stateProbabilitiyEvolution.get(currentTime);
	}

	private double solveForRate(double rateTarget, double[] distribution, int n) {
		final RootFinder rf = new BisectionSearch(0.0, 2.0);
		while(!rf.isDone()) {
			final double rate = rf.getNextPoint();
			double rateEff = 1.0;
			for(int i=0; i<distribution.length; i++) {
				for(int j=0; j<n; j++) {
					rateEff += (rate-1.0) * distribution[i] / n / (i+j+1);
				}
			}
			rf.setValue(rateEff-rateTarget);
		}

		final double rateGeom = rf.getBestPoint()/n;

		return rateGeom;
	}
}
