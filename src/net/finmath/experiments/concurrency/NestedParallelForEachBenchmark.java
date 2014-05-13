/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 06.05.2014
 */
package net.finmath.experiments.concurrency;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * This is a test of Java 8 parallel streams.
 * 
 * We are testing nested parallel forEach loops, which appear to
 * have unexpected poor performance in Java 1.8.0u5.
 * 
 * We have a nested stream.parallel().forEach():
 * 
 * The inner loop is independent (stateless, no interference, etc. - except of the use of a common pool)
 * and consumes 1 second in total in the worst case, namely if processed sequential.
 * Half of the tasks of the outer loop consume 10 seconds prior that loop.
 * Half consume 10 seconds after that loop.
 * We have a boolean which allows to switch the inner loop from parallel() to sequential().
 * Hence every thread consumes 11 seconds (worst case) in total.
 * Now: submitting 24 outer-loop-tasks to a pool of 8 we would expect 24/8 * 11 = 33 seconds at best (on an 8 core or better machine).
 * 
 * The result is:
 * - With inner sequential loop:	33 seconds.
 * - With inner parallel loop:		>80 seconds (I had 92 seconds).
 * 
 * Now, there is a funny workaround. The method 
 * wraps every operation in its own thread. Use this to wrap the inner loop in its
 * own thread via
 * <code>
 * 					wrapInThread( () ->
 * 						IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
 * 							burnTime(10);
 * 						}));
 * </code>
 * And the performance issue is gone. Note that this does not introduce any new
 * parallelism and that the inner loop tasks are still submitted to the same
 * common fork-join pool.
 * 
 * The reason, why this fix works, is because the inner loop is started form a Thread
 * and not from possible ForkJoinWorkerThread of the outer loop. In the latter case
 * the ForkJoinTask by mistake assumes that the starting thread is a worker of itself
 * and issues a join, effectively joining inner loop tasks with outer loop tasks.
 * 
 * @author Christian Fries
 */
public class NestedParallelForEachBenchmark {

	final NumberFormat formatter2 = new DecimalFormat("0.00");
	
	final int		numberOfWarmUpLoops = 20;
	final int		numberOfBenchmarkLoops = 20;
	
	final int		numberOfTasksInOuterLoop = 24;
	final int		numberOfTasksInInnerLoop = 10;
	final int		concurrentExecutionsLimitForStreams	= 2;	// java.util.concurrent.ForkJoinPool.common.parallelism

	final int		outerLoopOverheadFactor	= 100000;
	final long	 	calculationTaskFactor	= 100;		// You might need to calibrate this for your machine

	// Array where we store calculation results - this is just to prevent the JVM to optimize the task away
	final double[]	results = new double[numberOfTasksInOuterLoop * numberOfTasksInInnerLoop];
	
	public static void main(String[] args) {
		(new NestedParallelForEachBenchmark()).testNestedLoops();
	}

	public NestedParallelForEachBenchmark() {
		super();
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",Integer.toString(concurrentExecutionsLimitForStreams));
		System.out.println("java.util.concurrent.ForkJoinPool.common.parallelism = " + System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism"));
	}

	public void testNestedLoops() {
		System.out.println("We perfrom three different tests of Java streams (parallel) forEach loops.");
		System.out.println("Each test takes around 1 minute and is repeated " + (numberOfWarmUpLoops + numberOfBenchmarkLoops) + " times.");
		System.out.println("Please be patient (we print a '.' after each run).");
		System.out.println("Note: You may like to check cpu usage for each test.");
		System.out.println("");

		System.out.print("Test 1 (inner loop parallel but wrapped in thread)_:");
		String timeWithInnerParallelButWrappedInThread	= timeAction(() -> timeNestedLoopWithInnerParallelButWrappedInThread());

		System.out.print("Test 2 (inner loop sequential)_____________________:");
		String timeForInnerSequential					= timeAction(() -> timeNestedLoopWithInnerSequential());

		System.out.print("Test 3 (inner loop parallel)_______________________:");
		String timeForInnerParallel						= timeAction(() -> timeNestedLoopWithInnerParallel());

		System.out.println("");
		System.out.println("Results:");
		System.out.println("time for inner loop parallel but wrapped in thread__= " + timeWithInnerParallelButWrappedInThread);
		System.out.println("time for inner loop sequential______________________= " + timeForInnerSequential);
		System.out.println("time for inner loop parallel________________________= " + timeForInnerParallel);
	}
	
	public void warmUp(Runnable action) {
		// Some warm up
		for(int i=0; i<numberOfWarmUpLoops; i++) {
			Arrays.fill(results, 0);
			System.out.print(".");
			action.run();
		}
	}

	public String timeAction(Runnable action) {
		warmUp(action);

		// Test case
		double sum			= 0.0;
		double sumOfSquared	= 0.0;
		double max			= 0.0;
		double min			= Double.MAX_VALUE;
		for(int i=0; i<numberOfBenchmarkLoops; i++) {
			System.out.print(".");
			Arrays.fill(results, 0);
			long start = System.currentTimeMillis();
			action.run();
			long end = System.currentTimeMillis();
			
			double seconds = (double)(end-start) / 1000.0;
			max = Math.max(max, seconds);
			min = Math.min(min, seconds);
			sum += seconds;
			sumOfSquared += seconds*seconds;
		}
		System.out.println();

		return "" + formatter2.format(sum / numberOfBenchmarkLoops) + " +/- " + formatter2.format(Math.sqrt(sumOfSquared/numberOfBenchmarkLoops - sum * sum / numberOfBenchmarkLoops / numberOfBenchmarkLoops)) + " (min: " + formatter2.format(min) + " , max: " + formatter2.format(max) + ")" ;
	}
	
	public void timeNestedLoopWithInnerParallel() {
		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {

			if(i < numberOfTasksInOuterLoop/2) results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);

			// Inner loop as parallel: worst case (sequential) it takes 10 * numberOfTasksInInnerLoop millis
			IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
				results[i * numberOfTasksInInnerLoop + j] += burnTime(1);
			});

			if(i >= numberOfTasksInOuterLoop/2) results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);

		});
	}

	public void timeNestedLoopWithInnerSequential() {
		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {

			if(i < numberOfTasksInOuterLoop/2) results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);

			// Inner loop as parallel: worst case (sequential) it takes 10 * numberOfTasksInInnerLoop millis
			IntStream.range(0,numberOfTasksInInnerLoop).sequential().forEach(j -> {
				results[i * numberOfTasksInInnerLoop + j] += burnTime(1);
			});

			if(i >= numberOfTasksInOuterLoop/2) results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);

		});
	}

	public void timeNestedLoopWithInnerParallelButWrappedInThread() {
		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {

			if(i < numberOfTasksInOuterLoop/2) results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);

			// Inner loop as parallel: worst case (sequential) it takes 10 * numberOfTasksInInnerLoop millis
			wrapInThread(() ->
				IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
					results[i * numberOfTasksInInnerLoop + j] += burnTime(1);
				})
			);

			if(i >= numberOfTasksInOuterLoop/2) results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);

		});
	}

	private double burnTime(long millis) {
		double x = 0;
/*		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
*/ 		for(long i=0; i<millis*calculationTaskFactor; i++) {
			// We use a random number generator here, to prevent some optimization by the JVM
			x += Math.cos(i*0.0023*Math.random());
		}
		return x/calculationTaskFactor;
	}
	
	private void wrapInThread(Runnable runnable) {
		Thread t = new Thread(runnable, "Wrapper Thread");
		try {
			t.start();
			t.join();
		} catch (InterruptedException e) { }
	}
}
