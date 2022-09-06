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
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * This is an experiment to test the quality of the random number generator
 * and the discretization scheme.
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
	 * @param initialValue The inital value.
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
	 * @return Returns the deltaT.
	 */
	@Override
	public double getDeltaT() {
		return deltaT;
	}

	/**
	 * @return Returns the initialValue.
	 */
	@Override
	public double getInitialValue() {
		return initialValue;
	}

	/**
	 * @return Returns the nPaths.
	 */
	@Override
	public int getNumberOfPaths() {
		return numberOfPaths;
	}

	/**
	 * @return Returns the numberOfTimeSteps.
	 */
	@Override
	public int getNumberOfTimeSteps() {
		return numberOfTimeSteps;
	}

	/**
	 * @return Returns the mu.
	 */
	@Override
	public double getDrift() {
		return mu;
	}

	/**
	 * @return Returns the sigma.
	 */
	@Override
	public double getSigma() {
		return sigma;
	}
}
