package net.finmath.experiments.shortrate;

import net.finmath.functions.NormalDistribution;

/**
 * CIR Simulation.
 *
 * @author Gnoatto, Fries
 */
public class CIRSimulation {

	/*
	 * Model parameters
	 */
	private final double initialValue;
	private final double b;
	private final double beta;
	private final double sigma;

	/*
	 * Discretization parameters
	 */
	private final double numberOfPaths;
	private final double deltaT;

	/*
	 * Product parameters
	 */
	private final double maturity;

	public CIRSimulation(
			double initialValue,
			double b,
			double beta,
			double sigma,
			double maturity,
			double numberOfPaths,
			double deltaT){
		this.initialValue = initialValue;
		this.b = b;
		this.beta = beta;
		this.sigma = sigma;
		this.maturity = maturity;
		this.numberOfPaths = numberOfPaths;
		this.deltaT = deltaT;
	}


	/**
	 * Test method calculating the a zero coupon bond numerically and comparing with the analytic solution.
	 *
	 * @param args
	 */
	public static void main(String[] args)
	{
		final double	initialValue   = 0.01;
		final double	b              = 0.05;
		final double	beta           = 1.3;
		final double  sigma          = 0.05;

		// Process discretization properties
		final int		numberOfPaths	    = 10000;
		final double	deltaT				= 0.001;

		final double  maturity            = 1;

		final CIRSimulation cirSimulationTest = new CIRSimulation(initialValue,b,beta,sigma,maturity,numberOfPaths,deltaT);

		System.out.println("              Analytic value: " + cirSimulationTest.valueAnalytic());
		System.out.println("Monte-Carlo simulation value: " + cirSimulationTest.valueMonteCarlo());
	}


	public double valueAnalytic()
	{
		final double gamma	= Math.sqrt(beta*beta+2*sigma*sigma);
		final double phi		= (-2*b)/(sigma*sigma)*Math.log((2*gamma*Math.exp(0.5*(gamma-beta)*maturity))/(2*gamma+(gamma-beta)*(Math.exp(maturity*gamma)-1)));
		final double psi		= (2*(Math.exp(gamma*maturity)-1))/(2*gamma+(gamma-beta)*(Math.exp(maturity*gamma)-1));

		final double value = Math.exp(-phi-psi*initialValue);

		return value;
	}

	public double valueMonteCarlo()
	{

		double value = 0;

		for(int i=0; i<numberOfPaths; i++ ){
			double integratedRateOnPath	= 0;

			double rateAtCurrentTime	= initialValue;
			for(double t = 0; t<maturity; t+=deltaT){
				// Calculate the rate at the next time step
				final double random	= Math.random();
				final double z		= NormalDistribution.inverseCumulativeDistribution(random);

				// Euler scheme
				final double rateAtNextTime = rateAtCurrentTime+(b+beta*rateAtCurrentTime)*deltaT+sigma*Math.sqrt(rateAtCurrentTime)*Math.sqrt(deltaT)*z;

				/*
				 *  Calculate the integral of the rates as a simple pice-wise constant approximation, here forward difference
				 *  You may also try:
				 *
				 */
				integratedRateOnPath	+= rateAtCurrentTime*deltaT;

				rateAtCurrentTime	=	rateAtNextTime;
			}

			// Average over all paths
			value += Math.exp(-integratedRateOnPath);
		}

		return value/numberOfPaths;
	}
}
