/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 12.10.2013
 */

package net.finmath.experiments.montecarlo;

/**
 * @author Christian Fries
 */
public class MonteCarloIntegrationExperiment {


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		long numberOfSimulations = 1000000;
		
		double pi = getMonteCarloApproximationOfPi(numberOfSimulations);
		System.out.println("Simulation with n = " + numberOfSimulations + " resulted in approximation of pi = " + pi);

		System.out.println("Approximation error is                        = " + Math.abs(pi-Math.PI));
		System.out.println("Theoretical order of the Monte-Carlo error is = " + 1.0/Math.sqrt(numberOfSimulations));
	}

	/**
	 * Calculates an approximation of pi via Monte-Carlo integration.
	 * 
	 * @param numberOfSimulations
	 * @return An approximation of pi.
	 */
	public static double getMonteCarloApproximationOfPi(long numberOfSimulations) {
		long numberOfPointsInsideUnitCircle = 0;
		for(long i=0; i<numberOfSimulations; i++) {
			double x = 2.0 * (Math.random() - 0.5);
			double y = 2.0 * (Math.random() - 0.5);
			if(x*x + y*y < 1.0) numberOfPointsInsideUnitCircle++;
		}
		
		double areaOfUnitCircle = 4.0 * (double)numberOfPointsInsideUnitCircle / (double)numberOfSimulations;
		
		// The theoretical area of a circle is pi r^2. Hence we have:
		double pi = areaOfUnitCircle;

		return pi;
	}
}
