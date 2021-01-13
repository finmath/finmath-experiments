package net.finmath.experiments.reproduction;

import net.finmath.experiments.reproduction.ReproductionSimulationExperiment.StateProbabilities;
import net.finmath.functions.NormalDistribution;
import net.finmath.optimizer.GoldenSectionSearch;
import net.finmath.optimizer.LevenbergMarquardt;
import net.finmath.optimizer.SolverException;

public class DiscretizedLognormalDistribution {

	public static double[] getDistribution(int numberOfSamples, double mean, double stddev) {

		/*
		 * Transformation to lognormal distribution parameters
		 */
		double muGuess = Math.log(  mean / Math.sqrt(Math.pow(stddev / mean,2)+1 ) );
		double sigmaGuess = Math.sqrt( 2 * (Math.log(mean)-muGuess) );

		/*
		 * We use a numerical solver to match the distribution parameters exactly
		 */
		LevenbergMarquardt lm = new LevenbergMarquardt(new double[] { muGuess, sigmaGuess }, new double[] { mean, stddev*stddev }, 100, 2) {
			private static final long serialVersionUID = -6224587584814957914L;

			@Override
			public void setValues(double[] parameters, double[] values) throws SolverException {
				double mu = parameters[0];
				double sigma = parameters[1];

				double[] distribution = getDiscretizedDistribution(numberOfSamples, mu, sigma);

				// Report discitized mean and stddev
				double distribuionMean = 0.0;
				for(int i=0; i<distribution.length; i++) {
					distribuionMean += i * distribution[i];
				}

				double distribuionVariance = 0.0;
				for(int i=0; i<distribution.length; i++) {
					distribuionVariance += Math.pow(i - distribuionMean, 2.0) * distribution[i];
				}
				distribuionVariance *=  distribution.length/(distribution.length-1.0);

				values[0] = distribuionMean;
				values[1] = distribuionVariance;
			}
		};

		try {
			lm.run();
		} catch (SolverException e) {}
		
		double[] parameters = lm.getBestFitParameters();

		return getDiscretizedDistribution(numberOfSamples, parameters[0], parameters[1]);
	}

	private static double[] getDiscretizedDistribution(int numberOfSamples, double mu, double sigma) {
		double[] distribution = new double[numberOfSamples];
		double sum = 0.0;
		for(int i=0; i<distribution.length; i++) {
			double x = i+1;
			double P = (NormalDistribution.cumulativeDistribution((Math.log(i+1)-mu)/sigma)-NormalDistribution.cumulativeDistribution((Math.log(i)-mu)/sigma));
			double p = 1.0/(x*Math.sqrt(2*Math.PI)*sigma) * Math.exp(-Math.pow(Math.log(x)-mu, 2.0)/(2*sigma*sigma));
			distribution[i] = p;
			sum += p;
		}

		// Renormalize
		for(int i=0; i<distribution.length; i++) {
			distribution[i] /= sum;
		}

		return distribution;
	}
}
