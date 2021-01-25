/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 15.01.2004
 */

package net.finmath.experiments.montecarlo.schemes;

import java.text.DecimalFormat;

/**
 * Testing the behavior of some time discretization schemes w.r.t. time step size.
 *
 * @author Christian Fries
 */
public class MonteCarloSchemeTests {

	public static void main(String[] args)
	{
		System.out.println("Checking the mean (m) and the variance (V) of the terminal distribution of log(S(T)).\n"
				+"Generated using different numerical scheme for S (comparing to the analytic values).\n"
				+ "\n"
				+ "Output shows the error \u0394m (error on mean) and \u0394V (error on variance) comparing to theoretical mean and variance at time T.\n");

		final double initialValue = 1.0;
		final double sigma = 0.5;				// Note: Try different sigmas: 0.2, 0.5, 0.7, 0.9
		final int numberOfPath = 100000;		// Note: Try different number of path. For 10000000 you need around 6 GB (parameter is -mx6G)
		final double lastTime = 10.0;

		for(int numberOfTimeSteps=1; numberOfTimeSteps<=1001; numberOfTimeSteps+=10)
		{
			final double deltaT = lastTime/numberOfTimeSteps;

			// Create an instance of the Euler scheme class
			final LogProcessEulerScheme eulerScheme = new LogProcessEulerScheme(
					numberOfTimeSteps,	// numberOfTimeSteps
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Create an instance of the euler scheme class
			final LogProcessMilsteinScheme milsteinScheme = new LogProcessMilsteinScheme(
					numberOfTimeSteps,	// numberOfTimeSteps
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Create an instance of the euler scheme class
			final LogProcessExpEulerScheme expEulerScheme = new LogProcessExpEulerScheme(
					numberOfTimeSteps,	// numberOfTimeSteps
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Get start time of calculation
			final double startMillis = System.currentTimeMillis();

			final int		lastTimeIndex	= eulerScheme.getNumberOfTimeSteps();

			final double	averageEuler	= eulerScheme.getAverageOfLog( lastTimeIndex );
			final double	averageMilstein	= milsteinScheme.getAverageOfLog( lastTimeIndex );
			final double	averageExpEuler	= expEulerScheme.getAverageOfLog( lastTimeIndex );
			final double	averageAnalytic	= Math.log(initialValue)-(0.5 * sigma * sigma * (lastTimeIndex * deltaT) );

			final double	varianceEuler		= eulerScheme.getVarianceOfLog( lastTimeIndex );
			final double	varianceMilstein	= milsteinScheme.getVarianceOfLog( lastTimeIndex );
			final double	varianceExpEuler	= expEulerScheme.getVarianceOfLog( lastTimeIndex );
			final double	varianceAnalytic	= sigma * sigma * (lastTimeIndex * deltaT);

			// Get end time of calculation
			final double endMillis = System.currentTimeMillis();

			final double calculationTimeInSeconds = ((float)( endMillis - startMillis )) / 1000.0;

			// Print result
			final DecimalFormat decimalFormatInteger = new DecimalFormat("000");
			final double errorAverageEuler     = Math.abs(averageEuler    - averageAnalytic);
			final double errorVarianceEuler    = Math.abs(varianceEuler   - varianceAnalytic);
			final double errorAverageExpEuler  = Math.abs(averageExpEuler - averageAnalytic);
			final double errorVarianceExpEuler = Math.abs(varianceExpEuler- varianceAnalytic);
			final double errorAverageMilstein  = Math.abs(averageMilstein - averageAnalytic);
			final double errorVarianceMilstein = Math.abs(varianceMilstein- varianceAnalytic);

			System.out.print("nPath = " + numberOfPath);
			System.out.print("\tnSteps = " + decimalFormatInteger.format(numberOfTimeSteps));
			System.out.print("\tEuler...: \u0394m=" + formatPercent(errorAverageEuler)    + "  \u0394V=" + formatPercent(errorVarianceEuler));
			System.out.print("\tMilstein: \u0394m=" + formatPercent(errorAverageMilstein) + "  \u0394V=" + formatPercent(errorVarianceMilstein));
			System.out.print("\tExpEuler: \u0394m=" + formatPercent(errorAverageExpEuler) + "  \u0394V=" + formatPercent(errorVarianceExpEuler));
			System.out.println("\t(Time=" + calculationTimeInSeconds + " sec)." );
		}
	}

	/*
	 * Small helper to create nice output string.
	 */
	private static String formatInteger(int value) {
		return String.format("%4d", value);
	}

	/*
	 * Small helper to create nice output string.
	 */
	private static String formatPercent(double value) {
		if(Double.isNaN(value)) return "--NaN--";
		else return String.format("%6.3f%%", value * 100);
	}
}
