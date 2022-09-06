package net.finmath.experiments.timeseries.backcasting;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.IntStream;

import net.finmath.functions.LinearAlgebra;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.BrownianMotionView;
import net.finmath.montecarlo.CorrelatedBrownianMotion;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.plots.Plot;
import net.finmath.plots.Plot2D;
import net.finmath.plots.PlotableFunction2D;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class BackcastExperiment {

	public static void main(String[] args) throws Exception {

		/*
		 * Build a correlated n-dimension Brownian motion.
		 */

		double correlationDecay = 0.5;

		int dimension = 20;
		int timeSteps = 1000;
		double timeHorizon = 1.0;
		
		double[][] correlationCandidate = new double[dimension][dimension];
		for(int i=0; i<dimension; i++) {
			for(int j=0; j<dimension; j++) {
				correlationCandidate[i][j] = Math.exp(- correlationDecay * Math.abs(i-j));
			}			
		}

		double[][] rootMatrix = LinearAlgebra.getCholeskyDecomposition(correlationCandidate);

		
		TimeDiscretization td = new TimeDiscretizationFromArray(0.0, timeSteps, timeHorizon/timeSteps);
		int numberOfPath = 100;
		int seed = 3141;

		/*
		 * Build various BrownianMotion s based on correlation model that constitute a market
		 * 
		 * The 0-th component of brownianIncrementCorrelated is the path, which we will remove and reconstruct
		 */
		BrownianMotion brownianIncrementUncorelated = new BrownianMotionFromMersenneRandomNumbers(td, dimension+1, numberOfPath, seed);
		BrownianMotion brownianIncrementCorrelated = new CorrelatedBrownianMotion(new BrownianMotionView(brownianIncrementUncorelated,(Integer[]) IntStream.range(0, dimension).mapToObj(Integer::valueOf).toArray(i -> new Integer[i])), rootMatrix);
		BrownianMotion brownianIncrementForResidual = new BrownianMotionView(brownianIncrementUncorelated, new Integer[] { dimension });

		int pathIndex = 0;

		// All market processes (0 to n-1)
		RandomVariable[][] bmCorrelated = new RandomVariable[td.getNumberOfTimes()][dimension];
		for(int j=0; j<dimension; j++) {
			bmCorrelated[0][j] = new Scalar(0.0);
			for(int i=0; i<td.getNumberOfTimeSteps(); i++) {
				bmCorrelated[i+1][j] = bmCorrelated[i][j].add(brownianIncrementCorrelated.getBrownianIncrement(i, j));
			}
		}

		// Build the market (note: index 0 is missing, the market are all processes after 1 to n-1)
		Function<Integer, DoubleToRandomVariableFunction> marketProcesses = index -> time -> {
			return bmCorrelated[td.getTimeIndexNearestLessOrEqual(time)][index+1];	// Note the index+1 on the correlated BM
		};

		// An uncorrelated BM (used in reconstruction)
		RandomVariable[] bmResidual = new RandomVariable[td.getNumberOfTimes()];
		bmResidual[0] = new Scalar(0.0);
		for(int i=0; i<td.getNumberOfTimeSteps(); i++) {
			bmResidual[i+1] = bmResidual[i].add(brownianIncrementForResidual.getBrownianIncrement(i, 0));
		}
		DoubleToRandomVariableFunction bmResProcesses = time -> {
			return bmResidual[td.getTimeIndexNearestLessOrEqual(time)];
		};
		
		
		/*
		 * Reconstruction based on the n-1 market processes and one independent BM.
		 */

		CorrelationBasedBackcast correlationBasedBackcast = new CorrelationBasedBackcast();

		DoubleToRandomVariableFunction reconstruction = correlationBasedBackcast.buildReconstruction(marketProcesses, correlationCandidate, bmResProcesses);

		/*
		 * Drift adjustment - may also consider BrownianBridge
		 */
		DoubleToRandomVariableFunction reconstructionDriftAdjusted = t -> reconstruction.apply(t).add(pathIndex).add(
				bmCorrelated[td.getNumberOfTimes()-1][0]
						.sub(reconstruction.apply(timeHorizon)).mult(t/timeHorizon)
						);

		/*
		 * Plot original path
		 */
		DoubleUnaryOperator pathOriginal = t -> {
			return bmCorrelated[td.getTimeIndexNearestLessOrEqual(t)][0].get(pathIndex);
		};

		/*
		 * Plot reconstructed path
		 */
		DoubleUnaryOperator pathReconstructed = t -> {
			try {
				return reconstructionDriftAdjusted.apply(t).get(pathIndex);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return 0;
			}
		};
		

		/*
		 * Value only needed for plotting
		 */
		double[] generatorVector = correlationBasedBackcast.getGeneratorVector(correlationCandidate);
		double correlationValue = Math.sqrt(Arrays.stream(generatorVector).map(x -> x*x).sum());

		Plot plot = new Plot2D(List.of(
				new PlotableFunction2D(0.0, timeHorizon, 100, pathReconstructed),
				new PlotableFunction2D(0.0, timeHorizon, 100, pathOriginal)
				))
		.setTitle("Original (green) and Reconstructed (red) Path (market correlation = " + String.format("%5.2f", correlationValue)+ ")")
		.setXAxisLabel("time")
		.setYAxisLabel("value");

		plot.show();

		Files.createDirectories(Path.of("results"));
		plot.saveAsPDF(new File("results/backcast-" + (int)(correlationValue*100) + ".pdf"), 800, 400);
		plot.saveAsJPG(new File("results/backcast-" + (int)(correlationValue*100) + ".jpg"), 800, 400);
	}

}
