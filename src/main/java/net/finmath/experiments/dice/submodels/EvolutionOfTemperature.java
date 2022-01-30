package net.finmath.experiments.dice.submodels;

import java.util.function.BiFunction;

import net.finmath.functions.LinearAlgebra;

/**
 * The evolution of the temperature.
 * \(
 * 	T(t_{i+1}) = \Phi T(t_{i}) + (forcingToTemp \cdot forcing, 0, 0)
 * \)
 * 
 * @author Christian Fries
 */
public class EvolutionOfTemperature implements BiFunction<Temperature, Double, Temperature> {

	private static double xi1 = 0.1005;            // (GAMS c1)

	private static double[][] transitionMatrixDefault;
	static {
		double eta = 3.6813;            // Forcings of Equilibrium CO2 Doubling (GAMS fco22x)
	    double c3 = 0.088;
	    double c4 = 0.025;
	    double t2xco2 = 3.1;

	    double phi11 = 1-xi1*((eta/t2xco2) + c3);
	    double phi12 = xi1*c3;
	    double phi21 = c4;
	    double phi22 = 1-c4;

	    transitionMatrixDefault = new double[][] { new double[] { phi11, phi12 }, new double[] { phi21, phi22 } };
	}
	
	private double[][] transitionMatrix;		// phi in [i][j] (i = row, j = column)

	public EvolutionOfTemperature() {
		this(transitionMatrixDefault);
	}

	public EvolutionOfTemperature(double[][] transitionMatrix) {
		super();
		this.transitionMatrix = transitionMatrix;
	}

	@Override
	public Temperature apply(Temperature temperature, Double forcing) {
		// This is a bit clumsy code. We have to convert the row vector to a column vector, multiply it, then convert it back to a row.		
		double[] temperatureNext = LinearAlgebra.transpose(LinearAlgebra.multMatrices(transitionMatrix, LinearAlgebra.transpose(new double[][] { temperature.getAsDoubleArray() })))[0];
		temperatureNext[0] += xi1 * forcing;
		return new Temperature(temperatureNext);
	}

}
