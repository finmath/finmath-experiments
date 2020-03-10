/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 12.10.2013
 */

package net.finmath.experiments.montecarlo;

/**
 * A simple class illustrating a Monte-Carlo integration.
 *
 * @author Christian Fries
 */
public class MonteCarloIntegrationExperiment {


	/**
	 * Main program to run the experiment.
	 *
	 * @param args Arguments, not used
	 */
	public static void main(String[] args) {
		final long numberOfSimulations = 20000000;

		// Measure calculation time - start
		final long millisStart = System.currentTimeMillis();

		final double pi = getMonteCarloApproximationOfPi(numberOfSimulations);

		// Measure calculation time - end
		final long millisEnd = System.currentTimeMillis();

		System.out.println("Simulation with n = " + numberOfSimulations + " resulted in approximation of pi = " + pi +"\n");

		System.out.println("Approximation error is                        = " + Math.abs(pi-Math.PI));
		System.out.println("Theoretical order of the Monte-Carlo error is = " + 1.0/Math.sqrt(numberOfSimulations) + "\n");

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
			final double x = 2.0 * (Math.random() - 0.5);		// pseudo random number between -1 and 1
			final double y = 2.0 * (Math.random() - 0.5);		// pseudo random number between -1 and 1
			if(x*x + y*y < 1.0) {
				numberOfPointsInsideUnitCircle++;
			}
		}

		final double areaOfUnitCircle = 4.0 * numberOfPointsInsideUnitCircle / numberOfSimulations;

		// The theoretical area of a circle is pi r^2. Hence we have:
		final double pi = areaOfUnitCircle;

		return pi;
	}
}
