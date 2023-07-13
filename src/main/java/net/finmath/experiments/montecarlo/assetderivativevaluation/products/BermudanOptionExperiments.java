package net.finmath.experiments.montecarlo.assetderivativevaluation.products;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.AssetMonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.products.BermudanOption;
import net.finmath.montecarlo.assetderivativevaluation.products.BermudanOption.ExerciseMethod;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.plots.GraphStyle;
import net.finmath.plots.Plot;
import net.finmath.plots.Plot2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretizationFromArray;

public class BermudanOptionExperiments {

	// Model
	final double initialValue = 100.0;
	final double riskFreeRate = 0.00;
	final double volatility = 0.30;

	// Time Discretization of SDE
	final double initialTime = 0.0;
	final double timeHorizon = 5.0;
	final double dt = 1.0;

	// Monte-Carlo simulation
	final int numberOfPaths = 5000;
	final int seed = 3141;

	public static void main(String[] args) throws Exception {

		(new BermudanOptionExperiments()).ananlyseBermudan(1, false);
		(new BermudanOptionExperiments()).ananlyseBermudan(2, false);
		(new BermudanOptionExperiments()).ananlyseBermudan(4, false);
		(new BermudanOptionExperiments()).ananlyseBermudan(5, false);
		(new BermudanOptionExperiments()).ananlyseBermudan(10, false);
		(new BermudanOptionExperiments()).ananlyseBermudan(10, true);
		(new BermudanOptionExperiments()).ananlyseBermudan(20, true);
		(new BermudanOptionExperiments()).ananlyseBermudan(100, true);
		
	}

	private void ananlyseBermudan(int numberOfBasisFunctions, boolean useBinning) throws Exception {
		
		System.out.print(String.format("basisFunctions = %3d, binning=%s", numberOfBasisFunctions, useBinning));
		System.out.print("\t");
		
		final BlackScholesModel blackScholesModel = new BlackScholesModel(initialValue, riskFreeRate, volatility);
		final BrownianMotion bm = new BrownianMotionFromMersenneRandomNumbers(new TimeDiscretizationFromArray(initialTime, (int)Math.round(timeHorizon/dt), dt), 1, numberOfPaths, seed);
		final MonteCarloProcess process = new EulerSchemeFromProcessModel(blackScholesModel, bm);
		final MonteCarloAssetModel simulationModel = new MonteCarloAssetModel(process);

		final double maturity = timeHorizon;
		final double strike = initialValue*Math.exp(riskFreeRate * maturity);
		
		final double[] exerciseDates	= new double[] { 2.0, 			maturity };
		final double[] notionals		= new double[] { 1.0, 			1.0 };
		final double[] strikes			= new double[] { 0.8*strike, 	1.0*strike };
		final AssetMonteCarloProduct option = new BermudanOption(exerciseDates, notionals, strikes, ExerciseMethod.ESTIMATE_COND_EXPECTATION, numberOfBasisFunctions, false, useBinning);

		RandomVariable bermudanValue = ((BermudanOption) option).getValue(initialTime, simulationModel);
		
		boolean isPrintExerciseProbabilities = true;
		
		if(isPrintExerciseProbabilities && option instanceof BermudanOption) {
			final RandomVariable exerciseTime = ((BermudanOption) option).getLastValuationExerciseTime();
//			final double[] exerciseDates = ((BermudanOption) option).getExerciseDates();
			final double[] probabilities = exerciseTime.getHistogram(exerciseDates);
			for(int exerciseDateIndex=0; exerciseDateIndex<exerciseDates.length; exerciseDateIndex++)
			{
				final double time = exerciseDates[exerciseDateIndex];
				System.out.print("P(\u03C4 = " + time + ") = " + probabilities[exerciseDateIndex]);
				System.out.print("\t");
			}
			System.out.print("P(\u03C4 > " + exerciseDates[exerciseDates.length-1] + ") = " + probabilities[exerciseDates.length]);
			System.out.print("\t");
		}

		System.out.println("V_bermudan(0.0) = " + bermudanValue.getAverage() + " ± " + bermudanValue.getStandardError());
	
		/*
		 * Plot the exercise and continuation value for exercise date exerciseDates[0]
		 */
		RandomVariable stockInT1 = simulationModel.getAssetValue(exerciseDates[0], 0);
		
		RandomVariable exerciseValue = ((BermudanOption) option).getLastValuationExerciseValueAtExerciseTime()[0];
		RandomVariable continuationValue = ((BermudanOption) option).getLastValuationContinuationValueAtExerciseTime()[0];

		RandomVariable continuationValueExpectationRegression = ((BermudanOption) option).getLastValuationContinuationValueEstimatedAtExerciseTime()[0];
		RandomVariable continuationValueExpectationAnalytic = AnalyticFormulas.blackScholesOptionValue(stockInT1, riskFreeRate, volatility, exerciseDates[1]-2.0, strikes[1]);

		final Plot plotBermudanExercise = new Plot2D(
				List.of(
						PlotablePoints2D.of("Continuation Value Expectation Estimated E(max(S(T\u2082)-K\u2082, 0) / N(T\u2082))", stockInT1, continuationValueExpectationRegression, new GraphStyle(new Rectangle(-1,-1,2,2), null, Color.BLUE))
						,
						PlotablePoints2D.of("Continuation Value Expectation Analytic E(max(S(T\u2082)-K\u2082, 0) / N(T\u2082))", stockInT1, continuationValueExpectationAnalytic, new GraphStyle(new Rectangle(-1,-1,2,2), null, Color.GREEN))
						,
						PlotablePoints2D.of("Underlying (S(T\u2081)-K\u2081) / N(T\u2081)", stockInT1, exerciseValue, new GraphStyle(new Rectangle(-1,-1,2,2), null, Color.RED))
						,
						PlotablePoints2D.of("Continuation Value (S(T\u2082)-K\u2082) / N(T\u2082)", stockInT1, continuationValue, new GraphStyle(new Rectangle(-1,-1,1,1), null, Color.GRAY))
						)
				)
				.setXRange(0, 350).setYRange(-5, 250)
				.setIsLegendVisible(true)
				.setXAxisLabel("S(T\u2081)")
				.setYAxisLabel("V\u2081(T\u2081), V\u2082(T\u2081), V\u2082(T\u2082)")
				.setTitle("Time T\u2081 Bermudan option: Analytic (green) / Estimated (blue) Expected Continuation Value  vs  Exercise Value (red). n = " + numberOfBasisFunctions + " " + (useBinning ? "bins" : "monomials"));
		plotBermudanExercise.show();
	}
}
