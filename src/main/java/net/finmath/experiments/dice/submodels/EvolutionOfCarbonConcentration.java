package net.finmath.experiments.dice.submodels;

import java.util.function.BiFunction;

import net.finmath.functions.LinearAlgebra;

/**
 * the evolution of the carbon concentration.
 * \(
 * 	M(t_{i+1}) = \Phi M(t_{i}) + emission
 * \)
 * 
 * @author Christian Fries
 */
public class EvolutionOfCarbonConcentration implements BiFunction<CarbonConcentration, Double, CarbonConcentration> {

	private static double[][] transitionMatrixDefault;
	static {
	    double b12 = 0.12;
	    double b23 = 0.007;
	    double mateq = 588;
	    double mueq = 360;
	    double mleq = 1720;

	    double zeta11 = 1 - b12;
	    double zeta21 = b12;
	    double zeta12 = (mateq/mueq)*zeta21;
	    double zeta22 = 1 - zeta12 - b23;
	    double zeta32 = b23;
	    double zeta23 = zeta32*(mueq/mleq);
	    double zeta33 = 1 - zeta23;

	    transitionMatrixDefault = new double[][] { new double[] { zeta11, zeta12, 0.0 }, new double[] { zeta21, zeta22, zeta23 }, new double[] { 0.0, zeta32, zeta33 } };
	}
	
	private double[][] transitionMatrix;		// phi in [i][j] (i = row, j = column)

	public EvolutionOfCarbonConcentration() {
		this(transitionMatrixDefault);
	}

	public EvolutionOfCarbonConcentration(double[][] transitionMatrix) {
		super();
		this.transitionMatrix = transitionMatrix;
	}

	@Override
	public CarbonConcentration apply(CarbonConcentration carbonConcentration, Double emissions) {
		// This is a bit clumsy code. We have to convert the row vector to a column vector, multiply it, then convert it back to a row.
		double[] carbonConcentrationNext = LinearAlgebra.transpose(LinearAlgebra.multMatrices(transitionMatrix, LinearAlgebra.transpose(new double[][] { carbonConcentration.getAsDoubleArray() })))[0];

		// Add emissions
		carbonConcentrationNext[0] += emissions;
		
		return new CarbonConcentration(carbonConcentrationNext);
	}

}
