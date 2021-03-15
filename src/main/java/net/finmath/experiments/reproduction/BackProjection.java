package net.finmath.experiments.reproduction;

import java.util.Arrays;
import java.util.stream.DoubleStream;

import org.apache.commons.math3.util.CombinatoricsUtils;

/**
 * Implementation of an inverse convolution (aka BackProjection) using a given
 * distribution kernel (which can be interpreted as distribution of incubation times).
 *
 * @author Christian Fries
 */
public class BackProjection {

	private final double[] distribution;
	private final int smoothingIntervalStart;
	private final int smoothingIntervalEnd;

	public BackProjection(double[] distribution, int smoothingIntervalStart,
			int smoothingIntervalEnd) {
		super();
		this.distribution = distribution;
		this.smoothingIntervalStart = smoothingIntervalStart;
		this.smoothingIntervalEnd = smoothingIntervalEnd;
	}

	double[] getInfections(double[] observations) {

		boolean converged = false;

		final double averageObservataion = DoubleStream.of(observations).average().orElse(1.0);

		final double[] infectionsNew = new double[observations.length];
		Arrays.fill(infectionsNew, averageObservataion);

		final double[] infections = new double[infectionsNew.length];
		System.arraycopy(infectionsNew, 0, infections, 0, infections.length);

		while(!converged) {
			double squaredDeviation = 0.0;

			for(int k=0; k<observations.length-(distribution.length-1); k++) {
				double sum = 0.0;
				final double infection = infections[k];
				for(int i=0; i<distribution.length; i++) {
					double observationProjected = 0.0;
					for(int j=0; j<distribution.length; j++) {
						observationProjected += infections[Math.max(k+i-j,0)] * distribution[j];
					}
					sum += observations[k+i] * distribution[i] / observationProjected;

				}
				sum *= infection;
				infectionsNew[k] = sum;
			}

			// Smoothing step
			for(int i=0; i<infectionsNew.length; i++) {
				final double infectionPrev = infections[i];
				infections[i] = 0.0;

				double sumOfWeight = 0.0;
				for(int k=smoothingIntervalStart; k<=smoothingIntervalEnd; k++) {
					final double weight = CombinatoricsUtils.binomialCoefficient(smoothingIntervalEnd-smoothingIntervalStart, k-smoothingIntervalStart) / Math.pow(2, smoothingIntervalEnd-smoothingIntervalStart);
					infections[i] += infectionsNew[Math.min(Math.max(i+k,0),infections.length-1)] * weight;
					sumOfWeight += weight;
				}
				infections[i] /= sumOfWeight;

				squaredDeviation += Math.pow(infections[i]-infectionPrev, 2);
			}

			System.out.println(squaredDeviation);
			converged = squaredDeviation < 1E-12;
		}

		return infections;
	}
}
