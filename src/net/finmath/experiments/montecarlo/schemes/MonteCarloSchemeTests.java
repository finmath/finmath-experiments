/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 15.01.2004
 */

package net.finmath.experiments.montecarlo.schemes;

import java.text.DecimalFormat;

/**
 * @author Christian Fries
 */
public class MonteCarloSchemeTests {

	public static void main(String[] args)
	{
		System.out.println(
				"Comparing the mean (m) and the variance (V) of the terminal distribution\n" 
				+ "of log(S(T)), generated using a numerical scheme for S, to the analytic values.\n"
				+ "Output shows the error \u0394m and \u0394V.\n");
		
		double initialValue = 1.0;
		double sigma = 0.5;		// Note: Try different sigmas: 0.2, 0.5, 0.7, 0.9		
		int numberOfPath = 100000;
		
		for(int numberOfTimeSteps=1; numberOfTimeSteps<=2002; numberOfTimeSteps+=20)
		{
			double lastTime = 10.0;
			double deltaT = lastTime/numberOfTimeSteps;
			
			// Create an instance of the euler scheme class
			LogProcessEulerScheme eulerScheme = new LogProcessEulerScheme(
					numberOfTimeSteps,	// numberOfTimeSteps
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Create an instance of the euler scheme class
			LogProcessMilsteinScheme milsteinScheme = new LogProcessMilsteinScheme(
					numberOfTimeSteps,	// numberOfTimeSteps
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Create an instance of the euler scheme class
			LogProcessExpEulerScheme expEulerScheme = new LogProcessExpEulerScheme(
					numberOfTimeSteps,	// numberOfTimeSteps
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Get start time of calculation
			double startMillis = System.currentTimeMillis();

			int		lastTimeIndex	= eulerScheme.getNumberOfTimeSteps();
			
			double	averageEuler	= eulerScheme.getAverageOfLog( lastTimeIndex );
			double	averageMilstein	= milsteinScheme.getAverageOfLog( lastTimeIndex );
			double	averageExpEuler	= expEulerScheme.getAverageOfLog( lastTimeIndex );
			double	averageAnalytic	= Math.log(initialValue)-(0.5 * sigma * sigma * (lastTimeIndex * deltaT) );
			
			double	varianceEuler		= eulerScheme.getVarianceOfLog( lastTimeIndex );
			double	varianceMilstein	= milsteinScheme.getVarianceOfLog( lastTimeIndex );
			double	varianceExpEuler	= expEulerScheme.getVarianceOfLog( lastTimeIndex );
			double	varianceAnalytic	= sigma * sigma * (lastTimeIndex * deltaT);

			// Get end time of calculation
			double endMillis = System.currentTimeMillis();

			double calculationTimeInSeconds = ((float)( endMillis - startMillis )) / 1000.0;

			// Print result
			DecimalFormat decimalFormatPercent = new DecimalFormat("0.000%");
			DecimalFormat decimalFormatInteger = new DecimalFormat("000");
			double errorAverageEuler     = Math.abs(averageEuler    - averageAnalytic);
			double errorVarianceEuler    = Math.abs(varianceEuler   - varianceAnalytic);
			double errorAverageExpEuler  = Math.abs(averageExpEuler - averageAnalytic);
			double errorVarianceExpEuler = Math.abs(varianceExpEuler- varianceAnalytic);
			double errorAverageMilstein  = Math.abs(averageMilstein - averageAnalytic);
			double errorVarianceMilstein = Math.abs(varianceMilstein- varianceAnalytic);
			
			System.out.print("Path =" + numberOfPath);
			System.out.print("\tSteps=" + decimalFormatInteger.format(numberOfTimeSteps));
			System.out.print("\tEuler...: \u0394m=" + decimalFormatPercent.format(errorAverageEuler)    + " \u0394V=" + decimalFormatPercent.format(errorVarianceEuler));
			System.out.print("\tMilstein: \u0394m=" + decimalFormatPercent.format(errorAverageMilstein) + " \u0394V=" + decimalFormatPercent.format(errorVarianceMilstein));
			System.out.print("\tExpEuler: \u0394m=" + decimalFormatPercent.format(errorAverageExpEuler) + " \u0394V=" + decimalFormatPercent.format(errorVarianceExpEuler));
			System.out.println("\t(Time=" + calculationTimeInSeconds + " sec)." );
		}
	}
}
