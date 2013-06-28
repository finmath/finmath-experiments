/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 20.12.2003
 */
package net.finmath.experiments.montecarlo.schemes;

import java.text.DecimalFormat;

import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionInterface;
import net.finmath.montecarlo.RandomVariable;
import net.finmath.stochastic.ImmutableRandomVariableInterface;
import net.finmath.time.TimeDiscretization;

/**
 * This is an experiment to test the quality of the random number generator
 * and the discretization scheme.
 *
 * @author Christian Fries
 */
public class LogProcessEulerScheme
{
	private int		numberOfTimeIndices;
	private double	deltaT;
	private int		numberOfPaths;
	private double	initialValue;
	private double	sigma;
		
	private ImmutableRandomVariableInterface[]	discreteProcess = null;

	/**
	 * @param numberOfTimeIndices
	 * @param deltaT
	 * @param paths
	 * @param initialValue
	 * @param sigma
	 */
	public LogProcessEulerScheme(
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
		// Get the random variable from the process represented by this object
		ImmutableRandomVariableInterface randomVariable = getProcessValue(timeIndex);
		return randomVariable.getAverage();
	}
	
	public double getAverageOfLog(int timeIndex)
	{
		// Get the random variable from the process represented by this object
		ImmutableRandomVariableInterface randomVariable = getProcessValue(timeIndex);
		return randomVariable.getMutableCopy().log().getAverage();
	}

	public double getVarianceOfLog(int timeIndex)
	{
		// Get the random variable from the process represented by this object
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
				// The numerical scheme
				ImmutableRandomVariableInterface previouseRealization	= discreteProcess[timeIndex-1];
				ImmutableRandomVariableInterface deltaW					= brownianMotion.getBrownianIncrement(timeIndex, 0);

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
					double newValue = previousValue + previousValue * drift * deltaT + previousValue * diffusion;

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

	
	public static void main(String[] args)
	{
		double initialValue = 1.0;
		double sigma		= 0.5;		// Note: Try different sigmas: 0.2, 0.5, 0.7, 0.9		
		int numberOfPath	= 10000;
		
		for(int numberOfTimeSteps=20; numberOfTimeSteps<=202; numberOfTimeSteps+=20)
		{
			double lastTime = 10.0;
			double deltaT = lastTime/(numberOfTimeSteps-1);
			
			// Create an instance of the euler scheme class
			LogProcessEulerScheme eulerScheme = new LogProcessEulerScheme(
					numberOfTimeSteps,	// numberOfTimeIndices
					deltaT,				// deltaT
					numberOfPath,		// numberOfPaths
					initialValue,		// initialValue
					sigma);				// sigma

			// Get start time of calculation
			double startMillis = System.currentTimeMillis();

			int		lastTimeIndex	= eulerScheme.getNumberOfTimeIndices()-1;
			
			double	averageNumerical	= eulerScheme.getAverageOfLog( lastTimeIndex );
			double	averageAnalytic		= Math.log(initialValue)-(0.5 * sigma * sigma * (lastTimeIndex * deltaT) );
			
			double	varianceNumerical	= eulerScheme.getVarianceOfLog( lastTimeIndex );
			double	varianceAnalytic	= sigma * sigma * (lastTimeIndex * deltaT);

			// Get end time of calculation
			double endMillis = System.currentTimeMillis();

			double calculationTimeInSeconds = ((float)( endMillis - startMillis )) / 1000.0;

			// Print result
			DecimalFormat decimalFormatPercent = new DecimalFormat("0.000%");
			DecimalFormat decimalFormatInteger = new DecimalFormat("000");
			double errorAverage     = Math.abs(averageNumerical    - averageAnalytic);
			double errorVariance    = Math.abs(varianceNumerical   - varianceAnalytic);
			
			System.out.print("Number of Path=" + numberOfPath);
			System.out.print("\tSteps=" + decimalFormatInteger.format(numberOfTimeSteps));
			System.out.print("\terror of mean=" + decimalFormatPercent.format(errorAverage)    + "\terror of variance=" + decimalFormatPercent.format(errorVariance));
			System.out.println("\t(Time=" + calculationTimeInSeconds + " sec)." );
		}
	}
}