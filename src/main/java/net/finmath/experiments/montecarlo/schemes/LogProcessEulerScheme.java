/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 20.12.2003
 */
package net.finmath.experiments.montecarlo.schemes;

import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFromDoubleArray;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * Monte-Carlo simulation of a time-discrete process.
 * Time discretization of a log-normal process using the Euler scheme.
 *
 * Note: We do not use RandomVariable for the arithmetics. This is just for didactical reasons.
 * 
 * @author Christian Fries
 */
public class LogProcessEulerScheme implements LognormalProcess
{
	private final int		numberOfTimeSteps;
	private final double	deltaT;
	private final int		numberOfPaths;
	private final double	initialValue;
	private final double	mu;
	private final double	sigma;

	private RandomVariable[]	discreteProcess = null;

	/**
	 * Create a Euler scheme on X.
	 *
	 * @param numberOfTimeSteps The number of time steps.
	 * @param deltaT The time step size.
	 * @param numberOfPaths The number of Monte-Carlo paths.
	 * @param initialValue The initial value.
	 * @param mu The parameter mu (the drift).
	 * @param sigma The parameter sigma.
	 */
	public LogProcessEulerScheme(
			int numberOfTimeSteps,
			double deltaT,
			int numberOfPaths,
			double initialValue,
			double mu,
			double sigma) {
		super();
		this.numberOfTimeSteps = numberOfTimeSteps;
		this.deltaT = deltaT;
		this.numberOfPaths = numberOfPaths;
		this.initialValue = initialValue;
		this.mu = mu;
		this.sigma = sigma;
	}

	@Override
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
	 * Calculates the whole (discrete) process.
	 */
	private void doPrecalculateProcess() {

		final TimeDiscretization	timeDiscretization = new TimeDiscretizationFromArray(
				0.0 /* initial */, getNumberOfTimeSteps(), getDeltaT());

		final BrownianMotion		brownianMotion	= new BrownianMotionFromMersenneRandomNumbers(
				timeDiscretization, 1 /* numberOfFactors */, getNumberOfPaths(), 31415 /* seed */);

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
					final double drift = mu * deltaT;

					// Diffusion
					final double diffusion = sigma * deltaW.get(iPath);

					// Previous value
					final double previousValue = previouseRealization.get(iPath);

					// Numerical scheme
					final double newValue = previousValue + previousValue * drift + previousValue * diffusion;

					// Store new value
					newRealization[iPath] = newValue;
				}
			}

			// Store values
			discreteProcess[timeIndex] = new RandomVariableFromDoubleArray(timeIndex, newRealization);
		}
	}

	/**
	 * Returns the time step size deltaT.
	 * 
	 * @return the time step size deltaT.
	 */
	@Override
	public double getDeltaT() {
		return deltaT;
	}

	/**
	 * Returns the initial value.
	 * 
	 * @return the initial value.
	 */
	@Override
	public double getInitialValue() {
		return initialValue;
	}

	/**
	 * Returns the number of paths.
	 * 
	 * @return the number of paths.
	 */
	@Override
	public int getNumberOfPaths() {
		return numberOfPaths;
	}

	/**
	 * Returns the number of time steps.
	 *
	 * @return the number of time steps.
	 */
	@Override
	public int getNumberOfTimeSteps() {
		return numberOfTimeSteps;
	}

	/**
	 * Returns the log-normal drift &mu;.
	 * 
	 * @return the log-normal drift &mu;.
	 */
	@Override
	public double getDrift() {
		return mu;
	}

	/**
	 * Returns the log-normal volatiltiy &sigma;.
	 * 
	 * @return the log-normal volatiltiy &sigma;.
	 */
	@Override
	public double getSigma() {
		return sigma;
	}
}
