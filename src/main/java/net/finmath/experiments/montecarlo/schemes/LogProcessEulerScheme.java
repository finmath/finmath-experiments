/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 20.12.2003
 */
package net.finmath.experiments.montecarlo.schemes;

import java.text.DecimalFormat;

import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFromDoubleArray;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * This is an experiment to test the quality of the random number generator
 * and the discretization scheme.
 *
 * @author Christian Fries
 */
public class LogProcessEulerScheme
{
	private final int		numberOfTimeSteps;
	private final double	deltaT;
	private final int		numberOfPaths;
	private final double	initialValue;
	private final double	sigma;

	private RandomVariable[]	discreteProcess = null;

	/**
	 * Create a Euler scheme on X.
	 *
	 * @param numberOfTimeSteps The number of time steps.
	 * @param deltaT The time step size.
	 * @param numberOfPaths The number of Monte-Carlo paths.
	 * @param initialValue The inital value.
	 * @param sigma The parameter sigma.
	 */
	public LogProcessEulerScheme(
			int numberOfTimeSteps,
			double deltaT,
			int numberOfPaths,
			double initialValue,
			double sigma) {
		super();
		this.numberOfTimeSteps = numberOfTimeSteps;
		this.deltaT = deltaT;
		this.numberOfPaths = numberOfPaths;
		this.initialValue = initialValue;
		this.sigma = sigma;
	}

	public RandomVariable getProcessValue(int timeIndex)
	{
		if(discreteProcess == null)
		{
			doPrecalculateProcess();
		}

		// Return value of process
		return discreteProcess[timeIndex];
	}

	/**
	 * Returns the average of the random variable given by the process at the given time index
	 *
	 * @param timeIndex The time index
	 * @return The average
	 */
	public double getAverage(int timeIndex)
	{
		// Get the random variable from the process represented by this object
		final RandomVariable randomVariable = getProcessValue(timeIndex);
		return randomVariable.getAverage();
	}

	public double getAverageOfLog(int timeIndex)
	{
		// Get the random variable from the process represented by this object
		final RandomVariable randomVariable = getProcessValue(timeIndex);
		return randomVariable.log().getAverage();
	}

	public double getVarianceOfLog(int timeIndex)
	{
		// Get the random variable from the process represented by this object
		final RandomVariable randomVariable = getProcessValue(timeIndex);
		return randomVariable.log().getVariance();
	}

	/**
	 * Calculates the whole (discrete) process.
	 */
	private void doPrecalculateProcess() {

		final BrownianMotion	brownianMotion	= new BrownianMotionFromMersenneRandomNumbers(
				new TimeDiscretizationFromArray(0.0, getNumberOfTimeSteps(), getDeltaT()),
				1,						// numberOfFactors
				getNumberOfPaths(),
				31415					// seed
				);

		// Allocate Memory
		discreteProcess = new RandomVariable[getNumberOfTimeSteps()+1];

		for(int timeIndex = 0; timeIndex < getNumberOfTimeSteps()+1; timeIndex++)
		{
			final double[] newRealization = new double[numberOfPaths];

			// Generate process at timeIndex
			if(timeIndex == 0)
			{
				// Set initial value
				for (int iPath = 0; iPath < numberOfPaths; iPath++ )
				{
					newRealization[iPath] = initialValue;
				}
			}
			else
			{
				// The numerical scheme
				final RandomVariable previouseRealization	= discreteProcess[timeIndex-1];
				final RandomVariable deltaW					= brownianMotion.getBrownianIncrement(timeIndex-1, 0);

				// Generate values
				for (int iPath = 0; iPath < numberOfPaths; iPath++ )
				{
					// Drift
					final double drift = 0;

					// Diffusion
					final double diffusion = sigma * deltaW.get(iPath);

					// Previous value
					final double previousValue = previouseRealization.get(iPath);

					// Numerical scheme
					final double newValue = previousValue + previousValue * drift * deltaT + previousValue * diffusion;

					// Store new value
					newRealization[iPath] = newValue;
				}
			}

			// Store values
			discreteProcess[timeIndex] = new RandomVariableFromDoubleArray(timeIndex, newRealization);
		}
	}


	/**
	 * @return Returns the deltaT.
	 */
	public double getDeltaT() {
		return deltaT;
	}

	/**
	 * @return Returns the initialValue.
	 */
	public double getInitialValue() {
		return initialValue;
	}

	/**
	 * @return Returns the nPaths.
	 */
	public int getNumberOfPaths() {
		return numberOfPaths;
	}

	/**
	 * @return Returns the numberOfTimeSteps.
	 */
	public int getNumberOfTimeSteps() {
		return numberOfTimeSteps;
	}

	/**
	 * @return Returns the sigma.
	 */
	public double getSigma() {
		return sigma;
	}


	public static void main(String[] args)
	{
		final double initialValue = 1.0;
		final double sigma		= 0.5;		// Note: Try different sigmas: 0.2, 0.5, 0.7, 0.9
		final int numberOfPath	= 10000;

		for(int numberOfTimeSteps=20; numberOfTimeSteps<=202; numberOfTimeSteps+=20)
		{
			final double lastTime = 10.0;
			final double deltaT = lastTime/(numberOfTimeSteps-1);

			// Create an instance of the euler scheme class
			final LogProcessEulerScheme eulerScheme = new LogProcessEulerScheme(
					numberOfTimeSteps,	// numberOfTimeSteps
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Get start time of calculation
			final double startMillis = System.currentTimeMillis();

			final int		lastTimeIndex	= eulerScheme.getNumberOfTimeSteps()-1;

			final double	averageNumerical	= eulerScheme.getAverageOfLog( lastTimeIndex );
			final double	averageAnalytic		= Math.log(initialValue)-(0.5 * sigma * sigma * (lastTimeIndex * deltaT) );

			final double	varianceNumerical	= eulerScheme.getVarianceOfLog( lastTimeIndex );
			final double	varianceAnalytic	= sigma * sigma * (lastTimeIndex * deltaT);

			// Get end time of calculation
			final double endMillis = System.currentTimeMillis();

			final double calculationTimeInSeconds = ((float)( endMillis - startMillis )) / 1000.0;

			// Print result
			final DecimalFormat decimalFormatPercent = new DecimalFormat("0.000%");
			final DecimalFormat decimalFormatInteger = new DecimalFormat("000");
			final double errorAverage     = Math.abs(averageNumerical    - averageAnalytic);
			final double errorVariance    = Math.abs(varianceNumerical   - varianceAnalytic);

			System.out.print("Number of Path=" + numberOfPath);
			System.out.print("\tSteps=" + decimalFormatInteger.format(numberOfTimeSteps));
			System.out.print("\terror of mean=" + decimalFormatPercent.format(errorAverage)    + "\terror of variance=" + decimalFormatPercent.format(errorVariance));
			System.out.println("\t(Time=" + calculationTimeInSeconds + " sec)." );
		}
	}
}
