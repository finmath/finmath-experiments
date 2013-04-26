/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 16.01.2004
 */
package net.finmath.experiments.montecarlo2.schemes;

import net.finmath.montecarlo.RandomVariable;
import net.finmath.stochastic.ImmutableRandomVariableInterface;
import cern.jet.random.AbstractDistribution;
import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;

/**
 * @author Christian Fries
 */
public class LogProcessExpEulerScheme
{
	private AbstractDistribution		normalDistribution	= new Normal( 0,1, new MersenneTwister64() );
	private ImmutableRandomVariableInterface[]	discreteProcess = null;

	int		numberOfTimeIndices;
	double	deltaT;
	int		numberOfPaths;
	double	initialValue;
	double	sigma;
		
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
	
	public ImmutableRandomVariableInterface getProcessValue(int timeIndex)
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
		// Get the random variable from the process repesented by this object
		ImmutableRandomVariableInterface randomVariable = getProcessValue(timeIndex);
		return randomVariable.getAverage();
	}
	
	public double getAverageOfLog(int timeIndex)
	{
		// Get the random variable from the process repesented by this object
		ImmutableRandomVariableInterface randomVariable = getProcessValue(timeIndex);
		return randomVariable.getMutableCopy().log().getAverage();
	}

	public double getVarianceOfLog(int timeIndex)
	{
		// Get the random variable from the process repesented by this object
		ImmutableRandomVariableInterface randomVariable = getProcessValue(timeIndex);
		return randomVariable.getMutableCopy().log().getVariance();
	}
	
	/**
	 * Calculates the whole (discrete) process.
	 */
	private void doPrecalculateProcess() {
		// Allocate Memory
		discreteProcess = new ImmutableRandomVariableInterface[getNumberOfTimeIndices()];

		for(int timeIndex = 0; timeIndex < getNumberOfTimeIndices(); timeIndex++)
		{
			RandomVariable newRealization = new RandomVariable((double)timeIndex, numberOfPaths, 0.0);
			
			// Generate process at timeIndex
			if(timeIndex == 0)
			{
				// Set initial value
				for (int iPath = 0; iPath < newRealization.size(); iPath++ )
				{
					newRealization.set(iPath, initialValue);
				}
			}
			else
			{	
				// Euler Scheme
				ImmutableRandomVariableInterface previouseRealization	= discreteProcess[timeIndex-1];

				// Generate values 
				for (int iPath = 0; iPath < numberOfPaths; iPath++ )
				{
					// Drift
					double drift = 0;
					
					// Diffusion
					double randomNumber = normalDistribution.nextDouble();
					double diffusion = sigma * randomNumber * Math.sqrt(deltaT);
					
					double previousValue = previouseRealization.get(iPath);
					
					double newValue = previousValue * Math.exp(drift -0.5 * sigma * sigma * deltaT + diffusion);
					newRealization.set(iPath,newValue); 
				};
			}
			
			// Store values
			discreteProcess[timeIndex] = newRealization;
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