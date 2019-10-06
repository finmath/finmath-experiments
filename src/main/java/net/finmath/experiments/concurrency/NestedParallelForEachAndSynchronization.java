/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 06.05.2014
 */
package net.finmath.experiments.concurrency;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

/**
 * This is a test of Java 8 parallel streams.
 *
 * We are testing nested parallel forEach loops, which appear to
 * have a deadlock problem in Java 1.8.0u5.
 *
 * The problem is, that the following code will deadlock:
 * {@code
 * 		// Outer loop
 * 		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {
 *
 * 			doWork(outerLoopOverheadFactor);
 *
 * 			synchronized(this) {
 * 				// Inner loop
 * 				IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
 * 					doWork(1);
 * 				});
 * 			}
 * 		});
 * }
 *
 *
 * Now, there is a funny workaround. The method <code>wrapInThread</code>
 * wraps an operation in its own thread, but immediately synchronizes it.
 *
 * If we use this to wrap the inner loop in its own thread via
 * {@code
 * 				wrapInThread(() ->
 * 					// Inner loop
 * 					IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
 * 						doWork(1);
 * 					})
 * 				);
 * }
 * And the issue is gone. Note that this does not introduce any new
 * parallelism and that the inner loop tasks are still submitted to the same
 * common fork-join pool!
 *
 * The reason, why this fix works, is because the inner loop is started form a Thread
 * and not from possible ForkJoinWorkerThread of the outer loop. In the latter case
 * the ForkJoinTask by mistake assumes that the starting thread is a worker of itself
 * and issues a join, effectively joining inner loop tasks with outer loop tasks (imho this is a bug).
 *
 * Note: A minimal demo for the deadlock is the static method <code>nestedLoopDeadlockDemo</code>.
 *
 * For details see: http://www.christian-fries.de/blog/files/2014-nested-java-8-parallel-foreach.html
 *
 * @author Christian Fries
 */
public class NestedParallelForEachAndSynchronization {


	final NumberFormat formatter2 = new DecimalFormat("0.00");

	final int		numberOfTasksInOuterLoop = 24;				// In real applications this can be a large number.
	final int		numberOfTasksInInnerLoop = 100;				// In real applications this can be a large number.
	final int		concurrentExecutionsLimitForStreams	= 8;	// java.util.concurrent.ForkJoinPool.common.parallelism

	final int		outerLoopOverheadFactor	= 10000;
	final long	 	calculationTaskFactor	= 10;		// You might need to calibrate this for your machine

	public static void main(String[] args) {
		(new NestedParallelForEachAndSynchronization()).testNestedLoops();
	}

	public NestedParallelForEachAndSynchronization() {
		super();
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",Integer.toString(concurrentExecutionsLimitForStreams));
		System.out.println("java.util.concurrent.ForkJoinPool.common.parallelism = " + System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism"));
	}

	public void testNestedLoops() {
		System.out.println("");
		System.out.println("We perfrom three different tests of nested Java streams (parallel) forEach loops.");
		System.out.println("After each test you should see a DONE.");
		System.out.println("For the last one you will likely not see that, because it has a deadlock.");
		System.out.println("For each method we print the actual threads used - such that you can see that NO COMPENSATION THREADS are generated.");
		System.out.println("");

		System.out.println("Test 1 (inner loop sequential and synchronized) [works]___________________________________________:");
		runNestedLoopWithInnerSequentialSynchronized();
		System.out.println("DONE.");

		System.out.println("Test 2 (inner loop parallel and synchronized, but wrapped in a thread (a workaround) [works]______:");
		runNestedLoopWithInnerParallelSynchronizedButWrappedInThread();
		System.out.println("DONE.");

		System.out.println("Test 3 (inner loop parallel and synchronized [DEADLOCKS]__________________________________________:");
		runNestedLoopWithInnerParallelSynchronized();
		System.out.println("DONE.");
	}

	public void runNestedLoopWithInnerParallelSynchronized() {
		Set<String> threadsUsedInThisTest = Collections.synchronizedSet(new HashSet<String>());

		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {
			doWork(outerLoopOverheadFactor);
			if(threadsUsedInThisTest.add(Thread.currentThread().toString())) System.out.println("\t" + Thread.currentThread());
			synchronized(this) {
				// Inner loop
				IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
					doWork(1);
					if(threadsUsedInThisTest.add(Thread.currentThread().toString())) System.out.println("\t" + Thread.currentThread());
				});
			}
		});
	}

	public void runNestedLoopWithInnerSequentialSynchronized() {
		Set<String> threadsUsedInThisTest = Collections.synchronizedSet(new HashSet<String>());

		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {
			doWork(outerLoopOverheadFactor);
			if(threadsUsedInThisTest.add(Thread.currentThread().toString())) System.out.println("\t" + Thread.currentThread());
			synchronized(this) {
				// Inner loop
				IntStream.range(0,numberOfTasksInInnerLoop).sequential().forEach(j -> {
					doWork(1);
					if(threadsUsedInThisTest.add(Thread.currentThread().toString())) System.out.println("\t" + Thread.currentThread());
				});
			}
		});
	}

	public void runNestedLoopWithInnerParallelSynchronizedButWrappedInThread() {
		Set<String> threadsUsedInThisTest = Collections.synchronizedSet(new HashSet<String>());

		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {
			doWork(outerLoopOverheadFactor);
			if(threadsUsedInThisTest.add(Thread.currentThread().toString())) System.out.println("\t" + Thread.currentThread());
			synchronized(this) {
				wrapInThread(() ->
				// Inner loop
				IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
					doWork(1);
					if(threadsUsedInThisTest.add(Thread.currentThread().toString())) System.out.println("\t" + Thread.currentThread());
				})
						);
			}
		});
	}

	private double doWork(long overhead) {
		double x = 0;
		for(long i=0; i<overhead*calculationTaskFactor; i++) {
			x += Math.cos(i*0.0023);
		}
		return x/calculationTaskFactor;
	}

	private void wrapInThread(Runnable runnable) {
		Thread t = new Thread(runnable, "wrapper");
		try {
			t.start();
			t.join();
		} catch (InterruptedException e) { }
	}

	public static void nestedLoopDeadlockDemo() {
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism","8");
		Object lock = new Object();

		IntStream.range(0,24).parallel().forEach(i -> {
			// do some work here
			synchronized(lock) {
				IntStream.range(0,100).parallel().forEach(j -> {
					// do some work here
				});
			}
		});

		System.out.println("Done.");
	}


	public static int twoDimenstionalIntegrationDemo() {
		IntUnaryOperator func = (i) -> IntStream.range(0,100).parallel().map(j -> i*j).sum();
		int m = IntStream.range(0,100).parallel().map(func).sum();

		System.out.println(m);
		return m;
	}
}
