/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 03.05.2014
 */
package net.finmath.experiments.concurrency;

import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

/**
 * This is a test of Java 8 parallel streams.
 *
 * The idea behind this code is that the Semaphore concurrentExecutions
 * should limit the parallel executions of the outer forEach (which is an
 * <code>IntStream.range(0,numberOfTasks).parallel().forEach</code> (for example:
 * the parallel executions of the outer forEach should be limited due to a
 * memory constrain).
 *
 * Inside the execution block of the outer forEach we use another parallel stream
 * to create an inner forEach. The number of concurrent
 * executions of the inner forEach is not limited by us (it is however limited by a
 * system property "java.util.concurrent.ForkJoinPool.common.parallelism").
 *
 * Problem: If the semaphore is used AND the inner forEach is active, then
 * the execution will be DEADLOCKED.
 *
 * @author Christian Fries
 */
public class ForkJoinPoolTest {

	// Any combination of the booleans works, except (true,true,false)
	final boolean isUseSemaphore			= true;
	final boolean isUseInnerStream			= true;
	final boolean isWrappedInnerLoopThread	= false;

	final int		numberOfTasksInOuterLoop = 20;				// In real applications this can be a large number (e.g. > 1000).
	final int		numberOfTasksInInnerLoop = 100;				// In real applications this can be a large number (e.g. > 1000).
	final int		concurrentExecusionsLimitInOuterLoop = 5;
	final int		concurrentExecutionsLimitForStreams = 10;

	final Semaphore concurrentExecutions;

	public static void main(String[] args) {
		(new ForkJoinPoolTest()).testNestedLoops();
	}

	public ForkJoinPoolTest() {
		super();
		this.concurrentExecutions = new Semaphore(concurrentExecusionsLimitInOuterLoop);
	}

	public void testNestedLoops() {

		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",Integer.toString(concurrentExecutionsLimitForStreams));
		System.out.println("java.util.concurrent.ForkJoinPool.common.parallelism = " + System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism"));

		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {

			if(isUseSemaphore) {
				concurrentExecutions.acquireUninterruptibly();
			}

			try {
				System.out.println(i + "\t" + "-" + "\t" + concurrentExecutions.availablePermits() + "\t" + Thread.currentThread());

				if(isUseInnerStream) {
					try {
						Thread.sleep(10*numberOfTasksInInnerLoop);
					} catch (final Exception e) {
					}
					runCodeWhichUsesParallelStream(i, Thread.currentThread().toString());
				}
				else {
					try {
						Thread.sleep(10*numberOfTasksInInnerLoop);
					} catch (final Exception e) {
					}
				}
			}
			finally {
				if(isUseSemaphore) {
					concurrentExecutions.release();
				}
			}
		});

		System.out.println("D O N E");
	}

	/**
	 * Runs code in a parallel forEach using streams.
	 *
	 * @param numberOfTasksInInnerLoop Number of tasks to execute.
	 */
	private void runCodeWhichUsesParallelStream(int i, String callingThread) {

		final Runnable innerLoop = new Runnable() {
			@Override
			public void run() {
				// Inner loop
				IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
					try {
						System.out.println(i + "\t" + j + "\t" + concurrentExecutions.availablePermits() + "\t" + callingThread + "\t" + Thread.currentThread());
						Thread.sleep(10);
					} catch (final Exception e) {
					}
				});
			}
		};

		if(isWrappedInnerLoopThread) {
			final Thread t = new Thread(innerLoop, "Wrapper Thread");
			try {
				t.start();
				t.join();
			} catch (final InterruptedException e) {
			}
		}
		else {
			innerLoop.run();
		}
	}
}
