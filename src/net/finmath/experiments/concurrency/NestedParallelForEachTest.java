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
 * We are testing nested parellel forEach loops, which appear to
 * have an unexpected performance in Java 1.8.0u5.
 * 
 * The setup is as follows: We have a nested parallel forEach.
 * The inner loop is independent and consumes 1 second in total.
 * Half of the tasks of the outer loop consume 10 seconds prior that loop.
 * Half consume 10 seconds after that loop.
 * If we remove the inner loop I add another 1 second.
 * Hence every thread consumes 11 seconds (with and without inner loop) in total.
 * 
 * Now: submitting 10 Threads to a pool of 20 we would expect 22 seconds at best.
 * 
 * The result is:
 * - Without inner loop:	22 seconds.
 * - With inner loop:		>40 seconds (I had 60 seconds).
 * 
 * @author Christian Fries
 */
public class NestedParallelForEachTest {

	// The program uses 22 sec with this boolean to false and around 40+ with this boolean true:
	final boolean isUseInnerStream			= true;

	final int		numberOfTasksInOuterLoop = 20;				// In real applications this can be a large number (e.g. > 1000).
	final int		numberOfTasksInInnerLoop = 100;				// In real applications this can be a large number (e.g. > 1000).
	final int		concurrentExecutionsLimitForStreams		= 10;


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

				if(isUseInnerStream) {
					// Inner loop
					IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
						try { Thread.sleep(10); } catch (Exception e) { e.printStackTrace(); }
					});
						
				}
				else {
					try { Thread.sleep(10 * numberOfTasksInInnerLoop); } catch (Exception e) { e.printStackTrace(); }
				}

				if(i >= 10) Thread.sleep(10 * 1000);
			} catch (Exception e) { e.printStackTrace(); }

		});

		long end = System.currentTimeMillis();

		System.out.println("Done in " + (end-start)/1000 + " sec.");
	}
}
