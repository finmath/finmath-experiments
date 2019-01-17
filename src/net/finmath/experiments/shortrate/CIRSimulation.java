package net.finmath.experiments.shortrate;

import net.finmath.functions.NormalDistribution;

public class CIRSimulation {

	/*
	 * Model parameters
	 */
	private double initialValue;
	private double b;
	private double beta;
	private double sigma;

	/*
	 * Discretization parameters
	 */
	private double numberOfPaths;
	private double deltaT;

	/*
	 * Product parameters
	 */
	private double maturity;

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
		double	initialValue   = 0.01;
		double	b              = 0.05;
		double	beta           = 1.3;
		double  sigma          = 0.05;

		// Process discretization properties
		int		numberOfPaths	    = 10000;
		double	deltaT				= 0.001;

		double  maturity            = 1;

		CIRSimulation cirSimulationTest = new CIRSimulation(initialValue,b,beta,sigma,maturity,numberOfPaths,deltaT);

		System.out.println("              Analytic value: " + cirSimulationTest.valueAnalytic());
		System.out.println("Monte-Carlo simulation value: " + cirSimulationTest.valueMonteCarlo());
	}


	public double valueAnalytic()
	{
		double gamma	= Math.sqrt(beta*beta+2*sigma*sigma);
		double phi		= (-2*b)/(sigma*sigma)*Math.log((2*gamma*Math.exp(0.5*(gamma-beta)*maturity))/(2*gamma+(gamma-beta)*(Math.exp(maturity*gamma)-1)));
		double psi		= (2*(Math.exp(gamma*maturity)-1))/(2*gamma+(gamma-beta)*(Math.exp(maturity*gamma)-1));

		double value = Math.exp(-phi-psi*initialValue);

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
				double random	= Math.random();
				double z		= NormalDistribution.inverseCumulativeDistribution(random);

				// Euler scheme
				double rateAtNextTime = rateAtCurrentTime+(b+beta*rateAtCurrentTime)*deltaT+sigma*Math.sqrt(rateAtCurrentTime)*Math.sqrt(deltaT)*z;

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