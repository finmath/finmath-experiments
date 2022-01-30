package net.finmath.experiments.dice.submodels;

import java.util.function.BiFunction;

import net.finmath.experiments.LinearAlgebra;

/**
 * The evolution of the temperature.
 * \(
 * 	T(t_{i+1}) = \Phi T(t_{i}) + (forcingToTemp \cdot forcing, 0, 0)
 * \)
 * 
 * TODO: Fix time step (5 to 1)
 * 
 * @author Christian Fries
 */
public class EvolutionOfTemperature implements BiFunction<Temperature, Double, Temperature> {

	private static double timeStep = 5.0;	// time step in the original model (should become a parameter)
	private static double c1 = 0.1005;		// sometimes called xi1
	
	private static double[][] transitionMatrixDefault;
	static {
		double eta = 3.6813;            // Forcings of Equilibrium CO2 Doubling (GAMS fco22x)
	    double t2xco2 = 3.1;
	    double c3 = 0.088;
		double c4 = 0.025;

	    double phi11 = 1-c1*((eta/t2xco2) + c3);
	    double phi12 = c1*c3;
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
		double[] temperatureNext = LinearAlgebra.multMatrixVector(transitionMatrix, temperature.getAsDoubleArray());
		temperatureNext[0] += c1 * forcing;
		return new Temperature(temperatureNext);
	}

}
