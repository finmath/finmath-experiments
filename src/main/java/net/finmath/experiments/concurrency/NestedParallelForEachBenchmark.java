/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 06.05.2014
 */
package net.finmath.experiments.concurrency;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * and consumes only a small amount of time.
 *
 * Half of the tasks of the outer loop consume a larger portion of time prior that loop.
 * Half consume a larger portion of time after that loop.
 *
 * Now: submitting 24 outer-loop-tasks to a pool of 2 we observe:
 *
 * - With inner sequential loop:					26 seconds.
 * - With inner parallel loop:						41 seconds.
 *
 * Now, there is a funny workaround. The method
 * wraps every operation in its own thread. Use this to wrap the inner loop in its
 * own thread via
 * {@code
 * 					wrapInThread( () ->
 * 						IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
 * 							burnTime(10);
 * 						}));
 * }
 * And the performance issue is gone.
 *
 * - With inner parallel loop, wrapped in thread:	25 seconds.
 *
 * For details see: http://www.christian-fries.de/blog/files/2014-nested-java-8-parallel-foreach.html
 *
 * @author Christian Fries
 */
public class NestedParallelForEachBenchmark {

	private ExecutorService singleThreadExecutor = Executors.newFixedThreadPool(1);

	private final NumberFormat formatter2 = new DecimalFormat("0.00");

	private final int		numberOfWarmUpLoops = 20;
	private final int		numberOfBenchmarkLoops = 20;

	private final int		numberOfTasksInOuterLoop = 24;
	private final int		numberOfTasksInInnerLoop = 10;
	private final int		concurrentExecutionsLimitForStreams	= 2;	// java.util.concurrent.ForkJoinPool.common.parallelism

	private final int		outerLoopOverheadFactor	= 100000;
	private final long	 	calculationTaskFactor	= 100;		// You might need to calibrate this for your machine

	// Array where we store calculation results - this is just to prevent the JVM to optimize the task away
	private final double[]	results = new double[numberOfTasksInOuterLoop * numberOfTasksInInnerLoop];

	public static void main(String[] args) {
		final int testCase = 3;		// Set to 1,2,3
		System.out.println("Running test case " + testCase + " for Java parallel forEach loops.\nNote: you may switch between test case 1,2,3 in the main method.");
		final NestedParallelForEachBenchmark benchmark = new NestedParallelForEachBenchmark();
		benchmark.testNestedLoops(testCase);
		benchmark.singleThreadExecutor.shutdown();
	}

	public NestedParallelForEachBenchmark() {
		super();
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",Integer.toString(concurrentExecutionsLimitForStreams));
		System.out.println("java.util.concurrent.ForkJoinPool.common.parallelism = " + System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism"));
		final ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
		if(bean != null) {
			bean.setThreadCpuTimeEnabled(true);
		}

		System.out.println("Each test takes around 30 seconds and is repeated " + (numberOfWarmUpLoops + numberOfBenchmarkLoops) + " times.");
		System.out.println("Please be patient (we print a '.' after each run).");
		System.out.println("");
	}

	public void testNestedLoops(int testCase) {

		String testCaseName = null;
		String timings = null;
		if(testCase == 1) {
			System.out.print("Test 1 (inner loop parallel with bugfix):");
			testCaseName = "inner loop parallel w/ bugfix...";
			timings	= timeAction(() -> timeNestedLoopWithInnerParallelButWrappedInThread());
		}
		else if(testCase == 2) {
			System.out.print("Test 2 (inner loop sequential):");
			testCaseName = "inner loop sequential...........";
			timings = timeAction(() -> timeNestedLoopWithInnerSequential());
		}
		else if(testCase == 3) {
			System.out.print("Test 3 (inner loop parallel):");
			testCaseName = "inner loop parallel.............";
			timings = timeAction(() -> timeNestedLoopWithInnerParallel());
		}

		System.out.println("");
		System.out.println("Results:");

		final double totalCPUTime = printStats();
		timings += "\t[CPU Time: " + formatter2.format(totalCPUTime) + "]";

		System.out.println("time for " + testCaseName + " = " + timings);
	}

	/*
	 * Test case 3
	 */
	public void timeNestedLoopWithInnerParallel() {
		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {

			if(i < numberOfTasksInOuterLoop/2) {
				results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);
			}

			// Inner loop as parallel
			IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
				results[i * numberOfTasksInInnerLoop + j] += burnTime(1);
			});

			if(i >= numberOfTasksInOuterLoop/2) {
				results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);
			}

		});
	}

	/*
	 * Test case 2
	 */
	public void timeNestedLoopWithInnerSequential() {
		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {

			if(i < numberOfTasksInOuterLoop/2) {
				results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);
			}

			// Inner loop as parallel
			IntStream.range(0,numberOfTasksInInnerLoop).sequential().forEach(j -> {
				results[i * numberOfTasksInInnerLoop + j] += burnTime(1);
			});

			if(i >= numberOfTasksInOuterLoop/2) {
				results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);
			}

		});
	}

	/*
	 * Test case 1
	 */
	public void timeNestedLoopWithInnerParallelButWrappedInThread() {
		// Outer loop
		IntStream.range(0,numberOfTasksInOuterLoop).parallel().forEach(i -> {

			if(i < numberOfTasksInOuterLoop/2) {
				results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);
			}

			// Inner loop as parallel
			wrapInThread(() ->
			IntStream.range(0,numberOfTasksInInnerLoop).parallel().forEach(j -> {
				results[i * numberOfTasksInInnerLoop + j] += burnTime(1);
			})
					);

			if(i >= numberOfTasksInOuterLoop/2) {
				results[i * numberOfTasksInInnerLoop] += burnTime(outerLoopOverheadFactor);
			}

		});
	}

	private double burnTime(long millis) {
		double x = 0;
		for(long i=0; i<millis*calculationTaskFactor; i++) {
			// We use a random number generator here, to prevent some optimization by the JVM
			x += Math.cos(i*0.0023*Math.random());
		}
		return x/calculationTaskFactor;
	}

	private void wrapInThread(Runnable runnable) {
		final Future<?> result = singleThreadExecutor.submit(runnable);
		try {
			result.get();
		} catch (final ExecutionException e) { } catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
			final long start = System.currentTimeMillis();
			action.run();
			final long end = System.currentTimeMillis();

			final double seconds = (end-start) / 1000.0;
			max = Math.max(max, seconds);
			min = Math.min(min, seconds);
			sum += seconds;
			sumOfSquared += seconds*seconds;
		}
		System.out.println();

		return "" + formatter2.format(sum / numberOfBenchmarkLoops) + " +/- " + formatter2.format(Math.sqrt(sumOfSquared/numberOfBenchmarkLoops - sum * sum / numberOfBenchmarkLoops / numberOfBenchmarkLoops)) + " (min: " + formatter2.format(min) + " , max: " + formatter2.format(max) + ")" ;
	}

	private double printStats() {
		final Map<String, Double> cpuTimes = getCPUTime();
		double totalCPUTime = 0;
		for(final String thread: new TreeSet<String>(cpuTimes.keySet())) {
			final double cpuTime = cpuTimes.get(thread);
			totalCPUTime += cpuTimes.get(thread);
			System.out.println("\t" + String.format("%35s",thread) + "\t" + formatter2.format(cpuTime));
		}
		return totalCPUTime;
	}

	/** Get CPU time in nanoseconds. */
	public Map<String, Double> getCPUTime() {

		final Map<String, Double> cpuTimes = new HashMap<String, Double>();

		final ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
		if(!bean.isThreadCpuTimeSupported()) {
			return null;
		}

		final long[] threads = bean.getAllThreadIds();
		for (final long threadId : threads) {
			final ThreadInfo thread = bean.getThreadInfo(threadId);
			final long time = bean.getThreadCpuTime(threadId);
			cpuTimes.put(thread.getThreadName(), new Double(time/1000.0/1000.0/1000.0/(numberOfWarmUpLoops+numberOfBenchmarkLoops)));
		}
		return cpuTimes;
	}
}
