/*
 * Created on 10.02.2004
 */
package net.finmath.experiments.monteCarlo;

import java.text.DecimalFormat;

import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.RandomVariable;
import net.finmath.stochastic.ImmutableRandomVariableInterface;
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
		
		System.out.println("Average and variance of the increments of a BrownianMotion.\nTime step size: " + dt + "  Number of path: " + numberOfPaths);

		RandomVariable brownianMotionRealization = new RandomVariable(0.0, 0.0);
		for(int timeIndex=0; timeIndex<timeDiscretization.getNumberOfTimeSteps(); timeIndex++) {
			ImmutableRandomVariableInterface brownianIncement = brownian.getBrownianIncrement(timeIndex,0);
			brownianMotionRealization.add(brownianIncement);

			double average = 0.0;
			double secondMoment = 0.0;
			for(int path=0; path<numberOfPaths; path++)
			{	
				double brownianIncementOnPath = brownianIncement.get(path);

				average			+= brownianIncementOnPath;
				secondMoment	+= brownianIncementOnPath * brownianIncementOnPath;
			}
			average			/= numberOfPaths;
			secondMoment	/= numberOfPaths;

			System.out.println(
					"Increment from " + fromatterReal2.format(timeDiscretization.getTime(timeIndex)) + " to " + fromatterReal2.format(timeDiscretization.getTime(timeIndex+1)) + ":  " +
					"avg: " + fromatterSci4.format(average) + "  " +
					"var: " + fromatterSci4.format(secondMoment - average*average) + "    " +
					"Brownian motion at " + fromatterReal2.format(timeDiscretization.getTime(timeIndex+1)) + ":  " +
					"avg: " + fromatterSci4.format(brownianMotionRealization.getAverage()) + "  " +
					"var: " + fromatterSci4.format(brownianMotionRealization.getVariance())
			);
		}
	}	
}
