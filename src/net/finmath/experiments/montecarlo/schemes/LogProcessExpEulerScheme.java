/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 16.01.2004
 */
package net.finmath.experiments.montecarlo.schemes;

import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionInterface;
import net.finmath.montecarlo.RandomVariable;
import net.finmath.stochastic.RandomVariableInterface;
import net.finmath.time.TimeDiscretization;

/**
 * @author Christian Fries
 */
public class LogProcessExpEulerScheme
{
	private int		numberOfTimeIndices;
	private double	deltaT;
	private int		numberOfPaths;
	private double	initialValue;
	private double	sigma;
		
	private RandomVariableInterface[]	discreteProcess = null;

	/**
	 * @param numberOfTimeIndices
	 * @param deltaT
	 * @param paths
	 * @param initialValue
	 * @param sigma
	 */
	public LogProcessExpEulerScheme(
			int numberOfTimeIndices,
			double deltaT,
			int numberOfPaths,
			double initialValue,
			double sigma) {
		super();
		this.numberOfTimeIndices = numberOfTimeIndices;
		this.deltaT = deltaT;
		this.numberOfPaths = numberOfPaths;
		this.initialValue = initialValue;
		this.sigma = sigma;
	}
	
	public RandomVariableInterface getProcessValue(int timeIndex)
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
		RandomVariableInterface randomVariable = getProcessValue(timeIndex);
		return randomVariable.getAverage();
	}
	
	public double getAverageOfLog(int timeIndex)
	{
		// Get the random variable from the process represented by this object
		RandomVariableInterface randomVariable = getProcessValue(timeIndex);
		return randomVariable.getMutableCopy().log().getAverage();
	}

	public double getVarianceOfLog(int timeIndex)
	{
		// Get the random variable from the process represented by this object
		RandomVariableInterface randomVariable = getProcessValue(timeIndex);
		return randomVariable.getMutableCopy().log().getVariance();
	}
	
	/**
	 * Calculates the whole (discrete) process.
	 */
	private void doPrecalculateProcess() {
		
		BrownianMotionInterface	brownianMotion	= new BrownianMotion(
				new TimeDiscretization(0.0, getNumberOfTimeIndices(), getDeltaT()),
				1,						// numberOfFactors
				getNumberOfPaths(),
				31415					// seed
				);
		
		// Allocate Memory
		discreteProcess = new RandomVariableInterface[getNumberOfTimeIndices()];

		for(int timeIndex = 0; timeIndex < getNumberOfTimeIndices(); timeIndex++)
		{
			double[] newRealization = new double[numberOfPaths];
			
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
				// Euler Scheme
				RandomVariableInterface previouseRealization	= discreteProcess[timeIndex-1];
				RandomVariableInterface deltaW					= brownianMotion.getBrownianIncrement(timeIndex, 0);

				// Generate values 
				for (int iPath = 0; iPath < numberOfPaths; iPath++ )
				{
					// Drift
					double drift = 0;
					
					// Diffusion
					double diffusion = sigma * deltaW.get(iPath);
					
					// Previous value
					double previousValue = previouseRealization.get(iPath);
					
					// Numerical scheme
					double newValue = previousValue * Math.exp(drift -0.5 * sigma * sigma * deltaT + diffusion);

					// Store new value
					newRealization[iPath] = newValue;
				};
			}
			
			// Store values
			discreteProcess[timeIndex] = new RandomVariable((double)timeIndex, newRealization);
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
	 * @return Returns the numberOfTimeIndices.
	 */
	public int getNumberOfTimeIndices() {
		return numberOfTimeIndices;
	}

	/**
	 * @return Returns the sigma.
	 */
	public double getSigma() {
		return sigma;
	}
}