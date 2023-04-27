package net.finmath.experiments.factorreduction;

import java.awt.BasicStroke;
import java.io.File;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.finmath.functions.LinearAlgebra;
import net.finmath.plots.GraphStyle;
import net.finmath.plots.Named;
import net.finmath.plots.Plot;
import net.finmath.plots.Plot2D;
import net.finmath.plots.Plotable2D;
import net.finmath.plots.PlotableFunction2D;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * A small experiment that plots the factor matrix F for an exponential decaying correlation matrix R, i.e.,
 * F F^T = R.
 * 
 * The plots show the function T_i -> f_i,k (that is, the columns) for k = 1, 2, 3, ...
 * 
 * @author Christian Fries
 */
public class FactorMatrixExperiment {

	public static void main(String[] args) throws Exception {
		int		numberOfFactors				= 3;
		double	correlationDecayParameter	= 0.05;

		/*
		 * Create the tenor time discretization (forward rate periods)
		 */
		final double lastTime = 100.0, dt = 1.0;
		TimeDiscretization tenorTimeDiscretization = new TimeDiscretizationFromArray(0.0, (int)(lastTime/dt), dt);
		
		/*
		 * Create instanteaneous correlation matrix
		 */
		final double[][] originalCorrelationMatrix = createCorrelationMatrixFromFunctionalForm(
				tenorTimeDiscretization,
				correlationDecayParameter );

		/*
		 * Get the reduced factor and the full factor matrix
		 */
		final double[][] factorMatrixReduced		= LinearAlgebra.factorReduction(originalCorrelationMatrix, numberOfFactors);
		final double[][] factorMatrixFull			= LinearAlgebra.factorReduction(originalCorrelationMatrix, originalCorrelationMatrix.length);
		
		//Plot plotReduced = new Plot2D(0, lastTime, 300, factorsFromMatrix(tenorTimeDiscretization, factorMatrixReduced))
		Plot plotReduced = new Plot2D(factorPlotableFunctions(tenorTimeDiscretization, factorMatrixReduced))
		.setYRange(-1.1, 1.1).setXAxisLabel("component (time T_i)").setYAxisLabel("factor loading f_{i,k}");
		plotReduced.show();
		plotReduced.saveAsPDF(new File("FactorMatrix-"
				+ String.format("numberOfFactors=%d", numberOfFactors)
				+ String.format("correlationDecay=%d", (int)(correlationDecayParameter*1000))
				+ "-Reduced.pdf"), 900, 600);

		//Plot plotFull = new Plot2D(0, lastTime, 300, factorsFromMatrix(tenorTimeDiscretization, factorMatrixFull))
		Plot plotFull = new Plot2D(factorPlotableFunctions(tenorTimeDiscretization, factorMatrixFull))
		.setYRange(-1.1, 1.1).setXAxisLabel("component (time T_i)").setYAxisLabel("factor loading f_{i,k}");
		plotFull.show();
		plotFull.saveAsPDF(new File("FactorMatrix-"
				+ String.format("numberOfFactors=%d", numberOfFactors)
				+ String.format("correlationDecay=%d", (int)(correlationDecayParameter*1000))
				+ "-Full.pdf"), 900, 600);
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
			TimeDiscretization tenorTimeDiscretization,
			double		correlationDecayParameter) {

		final double[][] correlation = new double[tenorTimeDiscretization.getNumberOfTimes()][tenorTimeDiscretization.getNumberOfTimes()];
		for(int row=0; row<tenorTimeDiscretization.getNumberOfTimes(); row++) {
			for(int col=0; col<tenorTimeDiscretization.getNumberOfTimes(); col++) {
				// Exponentially decreasing instanteaneous correlation
				correlation[row][col] = Math.exp(-correlationDecayParameter * Math.abs(tenorTimeDiscretization.getTime(row)-tenorTimeDiscretization.getTime(col)));
			}
		}
		return correlation;
	}

	/**
	 * Convert a matrix \( f_{i,k} \) to functions \( t -> f_k(t) = f_{i(t),k} \),
	 * where i(t) is the nearest index in the time discretization \( T_{i} \).
	 * 
	 * @param tenorTimeDiscretization The tenor discretization T_{i}
	 * @param factorMatrix The factor matrix f_{i,k}
	 * @return The array of functions f_k
	 */
	public static DoubleUnaryOperator[] factorsFromMatrix(TimeDiscretization tenorTimeDiscretization, double[][] factorMatrix) {
		DoubleUnaryOperator[] factors = IntStream.range(0, factorMatrix[0].length).<DoubleUnaryOperator>mapToObj(i -> {
			DoubleUnaryOperator factor = time -> factorMatrix[tenorTimeDiscretization.getTimeIndexNearestLessOrEqual(time)][i];
			return factor;
		}).toArray(DoubleUnaryOperator[]::new);

		return factors;
	}

	/**
	 * Convert a matrix \( f_{i,k} \) to {@code PlotableFunction2D} functions \( t -> f_k(t) = f_{i(t),k} \),
	 * where i(t) is the nearest index in the time discretization \( T_{i} \).
	 * 
	 * @param tenorTimeDiscretization The tenor discretization T_{i}
	 * @param factorMatrix The factor matrix f_{i,k}
	 * @return The list of functions f_k as Plotable2D
	 */
	public static List<Plotable2D> factorPlotableFunctions(TimeDiscretization tenorTimeDiscretization, double[][] factorMatrix) {
		List<Plotable2D> factorsPlotable = IntStream.range(0, factorMatrix[0].length).<Plotable2D>mapToObj(i -> {
			DoubleUnaryOperator factor = time -> factorMatrix[tenorTimeDiscretization.getTimeIndexNearestLessOrEqual(time)][i];
			Plotable2D factorPlotable = new PlotableFunction2D(0, tenorTimeDiscretization.getNumberOfTimeSteps(), tenorTimeDiscretization.getNumberOfTimeSteps(), new Named<DoubleUnaryOperator>("" + i, factor), new GraphStyle(null, new BasicStroke(5.0f), null));
			return factorPlotable;
		}).collect(Collectors.toList());

		return factorsPlotable;
	}
}
