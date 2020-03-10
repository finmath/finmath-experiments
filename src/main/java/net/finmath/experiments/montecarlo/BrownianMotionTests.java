/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 10.02.2004
 */
package net.finmath.experiments.montecarlo;

import java.text.DecimalFormat;

import net.finmath.montecarlo.BrownianMotionLazyInit;
import net.finmath.montecarlo.RandomVariableFromDoubleArray;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * @author Christian Fries
 *
 */
public class BrownianMotionTests {

	static final DecimalFormat fromatterReal2	= new DecimalFormat("0.00");
	static final DecimalFormat fromatterSci4	= new DecimalFormat(" 0.0000E00;-0.0000E00");

	public static void main(String[] args)
	{
		// The parameters
		final int numberOfPaths	= 10000;
		final int seed			= 53252;

		final double lastTime = 4.0;
		final double dt = 0.1;

		// Create the time discretization
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, (int)(lastTime/dt), dt);

		// Test the quality of the Brownian motion
		final BrownianMotionLazyInit brownian = new BrownianMotionLazyInit(
				timeDiscretization,
				1,
				numberOfPaths,
				seed
				);

		System.out.println("Average, variance and other properties of a BrownianMotionLazyInit.\nTime step size (dt): " + dt + "  Number of path: " + numberOfPaths + "\n");

		System.out.println("      " + "\t" + "  int dW " + "\t" + "         " + "\t" + "int dW dW" + "\t" + "        ");
		System.out.println("time  " + "\t" + "   mean  " + "\t" + "    var  " + "\t" + "   mean  " + "\t" + "    var  ");

		final RandomVariable brownianMotionRealization	= new RandomVariableFromDoubleArray(0.0);
		RandomVariable sumOfSquaredIncrements 		= new RandomVariableFromDoubleArray(0.0);
		for(int timeIndex=0; timeIndex<timeDiscretization.getNumberOfTimeSteps(); timeIndex++) {
			final RandomVariable brownianIncrement = brownian.getBrownianIncrement(timeIndex,0);

			// Calculate W(t+dt) from dW
			brownianMotionRealization.add(brownianIncrement);

			final double time		= timeDiscretization.getTime(timeIndex);
			final double mean		= brownianMotionRealization.getAverage();
			final double variance	= brownianMotionRealization.getVariance();

			// Calculate x = \int dW(t) * dW(t)
			final RandomVariable squaredIncrements = brownianIncrement.squared();
			sumOfSquaredIncrements = sumOfSquaredIncrements.add(squaredIncrements);

			final double meanOfSumOfSquaredIncrements		= sumOfSquaredIncrements.getAverage();
			final double varianceOfSumOfSquaredIncrements	= sumOfSquaredIncrements.getVariance();

			System.out.println(
					fromatterReal2.format(time) + "\t" +
							fromatterSci4.format(mean) + "\t" +
							fromatterSci4.format(variance) + "\t" +
							fromatterSci4.format(meanOfSumOfSquaredIncrements) + "\t" +
							fromatterSci4.format(varianceOfSumOfSquaredIncrements) + "\t" +
							""
					);
		}
	}
}
