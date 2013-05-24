/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 16.01.2004
 */
package net.finmath.experiments.montecarlo.schemes;

import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionInterface;
import net.finmath.montecarlo.RandomVariable;
import net.finmath.stochastic.ImmutableRandomVariableInterface;
import net.finmath.time.TimeDiscretization;
import cern.jet.random.AbstractDistribution;
import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;

/**
 * @author Christian Fries
 */
public class LogProcessMilsteinScheme
{
	private int		numberOfTimeIndices;
	private double	deltaT;
	private int		numberOfPaths;
	private double	initialValue;
	private double	sigma;
		
	private ImmutableRandomVariableInterface[]	discreteProcess = null;

	public static void main(String[] args)
	{
		// Create an instance of this class
		LogProcessMilsteinScheme milsteinScheme = new LogProcessMilsteinScheme(
				101,	// numberOfTimeIndices
				0.1,	// deltaT
				100000,	// numberOfPaths
				1.0,	// initialValue
				0.2);	// sigma

		// Get start time of calculation
		double startMillis = System.currentTimeMillis();

		int		lastTimeIndex	= milsteinScheme.getNumberOfTimeIndices()-1;
		double	sigma			= milsteinScheme.getSigma();
		double	deltaT			= milsteinScheme.getDeltaT();
		
		double	average			= milsteinScheme.getAverageOfLog( lastTimeIndex );
		double	averageAnalytic	= -(0.5 * sigma * sigma * (lastTimeIndex * deltaT) );

		// Get end time of calculation
		double endMillis = System.currentTimeMillis();

		double calculationTimeInSeconds = ((float)( endMillis - startMillis )) / 1000.0;

		// Print result
		System.out.println( "Average (numeric) : " + average + "." );
		System.out.println( "Average (analytic): " + averageAnalytic + "." );
		System.out.println( "Calculation Time: " + calculationTimeInSeconds + " seconds." );
	}
	
	/**
	 * @param numberOfTimeIndices
	 * @param deltaT
	 * @param paths
	 * @param initialValue
	 * @param sigma
	 * gleicher Constructor wie im Euler-Schema. 
	 */
	public LogProcessMilsteinScheme(
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

		BrownianMotionInterface	brownianMotion	= new BrownianMotion(
				new TimeDiscretization(0.0, getNumberOfTimeIndices(), getDeltaT()),
				1,						// numberOfFactors
				getNumberOfPaths(),
				31415					// seed
				);

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
				// Milstein Scheme
				ImmutableRandomVariableInterface previouseRealization	= discreteProcess[timeIndex-1];
				ImmutableRandomVariableInterface deltaW					= brownianMotion.getBrownianIncrement(timeIndex, 0);

				// Generate values 
				for (int iPath = 0; iPath < numberOfPaths; iPath++ )
				{
					// Drift
					double drift = 0;

					// Diffusion
					double diffusion = sigma * deltaW.get(iPath);
					
					double previousValue = previouseRealization.get(iPath);
					
					double newValue = previousValue + drift * previousValue * deltaT + previousValue * diffusion
										+ 1.0/2.0 * previousValue * sigma * sigma * (deltaW.get(iPath) * deltaW.get(iPath) - deltaT);  // Milstein-Zusatz

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
