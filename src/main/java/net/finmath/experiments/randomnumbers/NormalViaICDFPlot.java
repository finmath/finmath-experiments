package net.finmath.experiments.randomnumbers;

import java.util.ArrayList;
import java.util.List;

import net.finmath.functions.NormalDistribution;
import net.finmath.plots.Plots;
import net.finmath.randomnumbers.MersenneTwister;
import net.finmath.randomnumbers.RandomNumberGenerator1D;

public class NormalViaICDFPlot {

	public static void main(String[] args) throws Exception {

		final int numberOfSamples = 100000;
		
		RandomNumberGenerator1D randomNumberGenerator = new MersenneTwister(3636);
		
		List<Double> valuesUniform = new ArrayList<>();
		List<Double> valuesNormal = new ArrayList<>();
		for(int i = 0; i<numberOfSamples; i++) {

			double uniform = randomNumberGenerator.nextDouble();

			double normal = NormalDistribution.inverseCumulativeDistribution(uniform);

			valuesUniform.add(uniform);
			valuesNormal.add(normal);
		}

		Plots.createDensity(valuesUniform, 300, 4.0)
		.setTitle("Uniform from " + randomNumberGenerator.getClass().getSimpleName()).show();

		Plots.createDensity(valuesNormal, 300, 4.0)
		.setTitle("Normal via ICDF from " + randomNumberGenerator.getClass().getSimpleName()).show();
	}
}
