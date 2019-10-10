/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 12.10.2013
 */

package net.finmath.experiments.montecarlo;

import net.finmath.experiments.montecarlo.randomnumbers.HaltonSequence;

/**
 * A simple class illustrating a Monte-Carlo integration using a Halton sequence (low discrepancy sequence)
 *
 * @author Christian Fries
 */
public class MonteCarloIntegrationWithQuasiRandomNumbersExperiment {


	/**
	 * Main program to run the experiment.
	 *
	 * @param args Arguments, not used
	 */
	public static void main(String[] args) {
		long numberOfSimulations = 20000000;

		// Measure calculation time - start
		long millisStart = System.currentTimeMillis();

		double pi = getMonteCarloApproximationOfPi(numberOfSimulations);

		// Measure calculation time - end
		long millisEnd = System.currentTimeMillis();

		System.out.println("Simulation with n = " + numberOfSimulations + " resulted in approximation of pi = " + pi +"\n");

		System.out.println("Approximation error is                                = " + Math.abs(pi-Math.PI));
		System.out.println("Theoretical order of the (quasi) Monte-Carlo error is = " + Math.pow(Math.log(numberOfSimulations),2)/numberOfSimulations + "\n");

		System.out.println("Calculation took " + (millisEnd-millisStart)/1000.0 + " sec.");
	}

	/**
	 * Calculates an approximation of pi via Monte-Carlo integration.
	 *
	 * @param numberOfSimulations The number of elements to use from the random number sequence.
	 * @return An approximation of pi.
	 */
	public static double getMonteCarloApproximationOfPi(long numberOfSimulations) {
		long numberOfPointsInsideUnitCircle = 0;
		for(long i=0; i<numberOfSimulations; i++) {
			double x = 2.0 * (HaltonSequence.getHaltonNumber(i, 2) - 0.5);	// quasi random number between -1 and 1
			double y = 2.0 * (HaltonSequence.getHaltonNumber(i, 3) - 0.5);	// quasi random number between -1 and 1
			if(x*x + y*y < 1.0) numberOfPointsInsideUnitCircle++;
		}

		double areaOfUnitCircle = 4.0 * (double)numberOfPointsInsideUnitCircle / (double)numberOfSimulations;

		// The theoretical area of a circle is pi r^2. Hence we have:
		double pi = areaOfUnitCircle;

		return pi;
	}
}
