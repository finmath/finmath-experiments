/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 12.10.2013
 */

package net.finmath.experiments.montecarlo;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.finmath.experiments.montecarlo.randomnumbers.HaltonSequence;

/**
 * A simple class illustrating a Monte-Carlo integration using parallel execution of sub-tasks.
 * 
 * @author Christian Fries
 */
public class MonteCarloIntegrationParallelizedExperiment {


	/**
	 * Main program to run the experiment.
	 * 
	 * @param args Arguments, not used
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		int numberOfSimulations = 10000000;
		int numberOfThreads		= 10;
		int numberOfTask		= 100;

		final int numberOfSimulationsPerTask	= numberOfSimulations / numberOfTask;
		final int numberOfSimulationsEffective	= numberOfSimulationsPerTask * numberOfTask;

		/*
		 * Start worker tasks (asynchronously)
		 */
		ExecutorService				executor	= Executors.newFixedThreadPool(numberOfThreads);
		ArrayList<Future<Double>>	results		= new ArrayList<Future<Double>>();
		for(int taskIndex=0; taskIndex<numberOfTask; taskIndex++) {
			
			final int startIndex					=  taskIndex * numberOfSimulationsPerTask;
			Future<Double> value = executor.submit(new Callable<Double>() {
				public Double call() {
					return getMonteCarloApproximationOfPi(startIndex, numberOfSimulationsPerTask);
				}
			});
			
			results.add(value);
		}
		
		/*
		 * Collect results
		 */
		double sumOfResults = 0.0;
		for(int taskIndex=0; taskIndex<numberOfTask; taskIndex++) {
			sumOfResults += results.get(taskIndex).get().doubleValue();
		}

		double pi = sumOfResults / numberOfTask;
		System.out.println("Simulation with n = " + numberOfSimulationsEffective + " resulted in approximation of pi = " + pi);

		System.out.println("Approximation error is                        = " + Math.abs(pi-Math.PI));
		System.out.println("Theoretical order of the Monte-Carlo error is = " + 1.0/Math.sqrt(numberOfSimulations));
		
		/*
		 * End/clean up thread pool
		 */
		executor.shutdown();
	}

	/**
	 * Calculates an approximation of pi via Monte-Carlo integration.
	 * 
	 * @param numberOfSimulations
	 * @return An approximation of pi.
	 */
	public static double getMonteCarloApproximationOfPi(int indexStart, int numberOfSimulations) {
		long numberOfPointsInsideUnitCircle = 0;
		for(int i=indexStart; i<indexStart+numberOfSimulations; i++) {
			double x = 2.0 * (HaltonSequence.getHaltonNumber(i, 2) - 0.5);
			double y = 2.0 * (HaltonSequence.getHaltonNumber(i, 3) - 0.5);
			if(x*x + y*y < 1.0) numberOfPointsInsideUnitCircle++;
		}
		
		double areaOfUnitCircle = 4.0 * (double)numberOfPointsInsideUnitCircle / (double)numberOfSimulations;
		
		// The theoretical area of a circle is pi r^2. Hence we have:
		double pi = areaOfUnitCircle;

		return pi;
	}
}
