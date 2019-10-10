/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 18.10.2012
 */
package net.finmath.experiments.computation;

/**
 * A simple class illustrating an aspect related to floating point arithmetic:
 * the loss of significance.
 *
 * @author Christian Fries
 */
public class ComputerArithmeticSolverQuadraticEquationExperiment {

	public static void main(String[] args) {

		/*
		 * Test solution of quadratic equation
		 */
		double p = 1000000.0;
		double q = 1;
		System.out.println("\nLoss of significance: Solution of quadratic equation f(x) = 0 with");
		System.out.println("f(x) = x^2 - 2px + q and p=" + p + " q="+q+"");
		System.out.println("__________________________________________________________________");

		System.out.println("Method 1");

		double x1 = p - Math.sqrt(p * p - q);
		double value1 = (x1 * x1 - 2 * p * x1 + q);

		System.out.println("Solution.: x = " + x1);
		System.out.println("Result: f(x) = " + value1);

		System.out.println("");
		System.out.println("Method 2");

		double x2 = q / (p + Math.sqrt(p * p - q));
		double value2 = (x2 * x2 - 2 * p * x2 + q);

		System.out.println("Solution.: x = " + x2);
		System.out.print("Result: f(x) = " + value2);
	}
}
