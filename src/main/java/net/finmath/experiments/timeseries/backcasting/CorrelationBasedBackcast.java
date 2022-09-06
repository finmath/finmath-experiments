package net.finmath.experiments.timeseries.backcasting;

import java.util.Arrays;
import java.util.function.Function;

import net.finmath.functions.LinearAlgebra;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;

/**
 * Correlation based reconstruction of a path of a Brownian motion.
 * 
 * Assume that \( dX = F dW where F = { (1,0) , (g,L) } \). Assume that \( dX1 = dW1 \) cannot be observed.
 * We have \( dX_2 = g dW_1 + L dW_2 \). Since \( g^{T} L = 0 \) we find that \( dW1 = g^{T} dX_{2} / g^{T} g \).
 * 
 * @author Christian Fries
 */
public class CorrelationBasedBackcast {
	
	/**
	 * Calculate the projection vector \( g \) from the correlation matrix.
	 * 
	 * @param correlation A symmetric positive definite \( n \times n \) matrix.
	 * @return An n-1 vector.
	 */
	double[] getGeneratorVector(double[][] correlation) {
		/*
		 * From correlation extract factor matrix
		 */
		double[][] rootMatrix = LinearAlgebra.getCholeskyDecomposition(correlation);

		int dimension = rootMatrix.length;
		
		// Take first column without first element: projection vector.
		double[] generatorVector = new double[dimension-1];
		for(int i=1; i<dimension; i++) {
			for(int j=1; i<dimension; i++) {
				generatorVector[j-1] += rootMatrix[i][0] / (dimension-1);
			}
		}
		
		return generatorVector;
	}

	/**
	 * Build a reconstruction of the process X_1 using the processes X_2,\ldots,X_n, the correlation matrix and an independent Brownian motion W.
	 * 
	 * @param marketProcesses The market process i -> t -> X_{i+1}(t)
	 * @param correlation The correlation matrix.
	 * @param brownianForResidual The Brownian motion t -> W(t)
	 * @return The process t -> X_{1}(t)
	 */
	DoubleToRandomVariableFunction buildReconstruction(Function<Integer, DoubleToRandomVariableFunction> marketProcesses, double[][] correlation, DoubleToRandomVariableFunction brownianForResidual) {
		
		double[] generatorVector = getGeneratorVector(correlation);
		double generatorNormSquared = Arrays.stream(generatorVector).map(x -> x*x).sum();
		
		DoubleToRandomVariableFunction reconstruction = time -> {
			RandomVariable value = new Scalar(0.0);
			for(int i=0; i<generatorVector.length; i++) {
				value = value.add(marketProcesses.apply(i).apply(time).mult(generatorVector[i]));
			}
			value = value.add(brownianForResidual.apply(time).mult(Math.sqrt(1-generatorNormSquared)));
			return value;
		};
		
		return reconstruction;
	}
}
