/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 10.02.2004
 */
package net.finmath.experiments.montecarlo;

import java.text.DecimalFormat;

import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.RandomVariable;
import net.finmath.stochastic.ImmutableRandomVariableInterface;
import net.finmath.stochastic.RandomVariableInterface;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationInterface;

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
		int numberOfPaths	= 10000;
		int seed			= 53252;
		
		double lastTime = 4.0;
		double dt = 0.1;

		// Create the time discretization
		TimeDiscretizationInterface timeDiscretization = new TimeDiscretization(0.0, (int)(lastTime/dt), dt);

		// Test the quality of the Brownian motion
		BrownianMotion brownian = new BrownianMotion(
				timeDiscretization,
				1,
				numberOfPaths,
				seed
		);
		
		System.out.println("Average, variance and other properties of a BrownianMotion.\nTime step size: " + dt + "  Number of path: " + numberOfPaths + "\n");

		System.out.println("time  " + "\t" + "   mean  " + "\t" + "    var  ");

		RandomVariable brownianMotionRealization	= new RandomVariable(0.0);
		RandomVariable sumOfSquaredIncrements 		= new RandomVariable(0.0);
		for(int timeIndex=0; timeIndex<timeDiscretization.getNumberOfTimeSteps(); timeIndex++) {
			ImmutableRandomVariableInterface brownianIncrement = brownian.getBrownianIncrement(timeIndex,0);
			
			// Calculate W(t+dt) from dW
			brownianMotionRealization.add(brownianIncrement);

			double time		= timeDiscretization.getTime(timeIndex);
			double mean		= brownianMotionRealization.getAverage();
			double variance	= brownianMotionRealization.getVariance();

			// Calculate x = \int dW(t) * dW(t)
			RandomVariableInterface squaredIncrements = brownianIncrement.getMutableCopy().squared();
			sumOfSquaredIncrements.add(squaredIncrements);

			double meanOfSumOfSquaredIncrements		= sumOfSquaredIncrements.getAverage();
			double varianceOfSumOfSquaredIncrements	= sumOfSquaredIncrements.getVariance();

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
