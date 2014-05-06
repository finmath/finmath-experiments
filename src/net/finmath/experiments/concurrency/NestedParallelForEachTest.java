/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 06.05.2014
 */
package net.finmath.experiments.concurrency;

import java.util.stream.IntStream;

/**
 * This is a test of Java 8 parallel streams.
 * 
 * We are testing nested parallel forEach loops, which appear to
 * have unexpected performance in Java 1.8.0u5.
 * 
 * We have a nested stream.parallel().forEach().
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
 * @author Christian Fries
 */
public class NestedParallelForEachTest {

	// The program uses 33 sec with this boolean to false and around 80+ with this boolean true:
	final boolean isInnerStreamParallel		= true;

	// Setup: Inner loop task 0.01 sec in worse case. Outer loop task: 10 sec + inner loop. This setup: (100 * 0.01 sec + 10 sec) * 24/8 = 33 sec.
	final int		numberOfTasksInOuterLoop = 24;				// In real applications this can be a large number (e.g. > 1000).
	final int		numberOfTasksInInnerLoop = 100;				// In real applications this can be a large number (e.g. > 1000).
	final int		concurrentExecutionsLimitForStreams	= 8;	// java.util.concurrent.ForkJoinPool.common.parallelism
	
	public static void main(String[] args) {
		(new NestedParallelForEachTest()).testNestedLoops();
	}

	public void testNestedLoops() {

		long start = System.currentTimeMillis();

		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",Integer.toString(concurrentExecutionsLimitForStreams));
		System.out.println("java.util.concurrent.ForkJoinPool.common.parallelism = " + System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism"));

		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {

			try {
				if(i < 10) Thread.sleep(10 * 1000);

				System.out.println(i + "\t" + Thread.currentThread());

				if(isInnerStreamParallel) {
					// Inner loop as parallel: worst case (sequential) it takes 10 * numberOfTasksInInnerLoop millis
					IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
						try { Thread.sleep(10); } catch (Exception e) { e.printStackTrace(); }
					});
						
				}
				else {
					// Inner loop as sequential
					IntStream.range(0,numberOfTasksInInnerLoop).sequential().forEach(j -> {
						try { Thread.sleep(10); } catch (Exception e) { e.printStackTrace(); }
					});
				}

				if(i >= 10) Thread.sleep(10 * 1000);
			} catch (Exception e) { e.printStackTrace(); }

		});

		long end = System.currentTimeMillis();

		System.out.println("Done in " + (end-start)/1000 + " sec.");
	}
}
