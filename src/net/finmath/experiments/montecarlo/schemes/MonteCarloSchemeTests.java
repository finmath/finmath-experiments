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
		// Just for demonstration: Print the command line parameters
		for(int i=0; i<args.length; i++)
		{
			System.out.println( "Parameter " + i + ": " + args[i]);
		}
		
		double initialValue = 1.0;
		double sigma = 0.5;		// Note: Try different sigmas: 0.2, 0.5, 0.7, 0.9		
		int numberOfPath = 10000;
		
		for(int numberOfTimeSteps=2; numberOfTimeSteps<=202; numberOfTimeSteps+=20)
		{
			double lastTime = 10.0;
			double deltaT = lastTime/(numberOfTimeSteps-1);
			
			// Create an instance of the euler scheme class
			LogProcessEulerScheme eulerScheme = new LogProcessEulerScheme(
					numberOfTimeSteps,	// numberOfTimeIndices
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Create an instance of the euler scheme class
			LogProcessMilsteinScheme milsteinScheme = new LogProcessMilsteinScheme(
					numberOfTimeSteps,	// numberOfTimeIndices
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Create an instance of the euler scheme class
			LogProcessExpEulerScheme expEulerScheme = new LogProcessExpEulerScheme(
					numberOfTimeSteps,	// numberOfTimeIndices
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Get start time of calculation
			double startMillis = System.currentTimeMillis();

			int		lastTimeIndex	= eulerScheme.getNumberOfTimeIndices()-1;
			
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
			System.out.print("\tEuler...: m=" + decimalFormatPercent.format(errorAverageEuler)    + " V=" + decimalFormatPercent.format(errorVarianceEuler));
			System.out.print("\tMilstein: m=" + decimalFormatPercent.format(errorAverageMilstein) + " V=" + decimalFormatPercent.format(errorVarianceMilstein));
			System.out.print("\tExpEuler: m=" + decimalFormatPercent.format(errorAverageExpEuler) + " V=" + decimalFormatPercent.format(errorVarianceExpEuler));
			System.out.println("\t(Time=" + calculationTimeInSeconds + " sec)." );
		}
	}
}
