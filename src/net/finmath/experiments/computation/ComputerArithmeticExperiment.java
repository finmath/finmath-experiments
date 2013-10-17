/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 * 
 * Created on 18.10.2012
 */
package net.finmath.experiments.computation;

/**
 * A simple class illustrating some aspects related to floating point arithmetic.
 * 
 * @author Christian Fries
 */
public class ComputerArithmeticExperiment {

	/**
	 * Construct the test class.
	 */
	public ComputerArithmeticExperiment() {
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		/*
		 * Test the machine precision
		 */
		System.out.println("\n\nTest 1: Machine Precision");

		double eps = getCalculatedMachinePrecision();

		System.out.println("Calculated Machine Precision: eps = " + eps);
		System.out.println("                          1 + eps = " + (1+eps));
		System.out.println("                        1 + eps/2 = " + (1+eps/2.0));

		/*
		 * Test calculations close to the maximum value
		 */
		System.out.println("\n\nTest 2: Infinity");
		
		double maxDouble	= 	Double.MAX_VALUE;
		
		System.out.println("Largest possible number:              maxDouble = " + maxDouble);
		System.out.println("                               maxDouble + eps  = " + (maxDouble + eps));		
		System.out.println("                    maxDouble + maxDouble * eps = " + (maxDouble + maxDouble * eps));
		System.out.println("(maxDouble + maxDouble * eps) - maxDouble * eps = " + ((maxDouble + maxDouble * eps) - maxDouble * eps));

		/*
		 * Test solution of quadratic equation
		 */
		double p = 1000000.0;
		double q = 1;
		System.out.println("\n\nTest 3: Solution of quadratic equation x^2 - 2px + q = 0 with p=" + p + " q="+q+"");
		
		System.out.println("Method 1");
		double x1 = getSmallestSolutionOfQuadraticEquation1(p,q);
		System.out.println("Solution:           x = " + x1);
		System.out.println("Result: x^2 - 2px + q = " + (x1 * x1 - 2 * p * x1 + q));

		System.out.println("Method 2");
		double x2 = getSmallestSolutionOfQuadraticEquation2(p,q);
		System.out.println("Solution:           x = " + x2);
		System.out.println("Result: x^2 - 2px + q = " + (x2 * x2 - 2 * p * x2 + q));

		/*
		 * Test calculations close to the maximum value
		 */
		System.out.println("\n\nTest 4: Summation.");
		int numberOfSummations = 10000000;

		System.out.println("Method 1");
		double sum = getSumOfNumberClassical(1.0/3.0, numberOfSummations);
		double average = sum / numberOfSummations;
		System.out.println("Sum:     " + sum);
		System.out.println("Average: " + average);

		System.out.println("Method 2");
		double sum2 = getSumOfNumbersKahan(1.0/3.0, numberOfSummations);
		double average2 = sum2 / numberOfSummations;		
		System.out.println("Sum:     " + sum2);
		System.out.println("Average: " + average2);
	}
	
	
	/**
	 * Returns epsilon such that 1 + epsilon > 1 = 1 + epsilon/2.
	 * 
	 * @return The machine precision.
	 */
	static double getCalculatedMachinePrecision() {
		
		double epsilon = 1.0;
		while(1 + epsilon > 1) {
			epsilon = epsilon / 2.0;
		}
		
		return epsilon * 2.0;
	}

	/**
	 * Returns the solution of x*2 - 2 p x + q = 0 as p - sqrt(p*p-q).
	 * 
	 * @param p The parameter p.
	 * @param q The parameter q.
	 * @return The presumed solution x.
	 */
	static double getSmallestSolutionOfQuadraticEquation1(double p, double q) {
		return p - Math.sqrt(p * p - q);
	}

	/**
	 * Returns the solution of x*2 - 2 p x + q = 0 as q / (p + sqrt(p*p-q)).
	 * 
	 * @param p The parameter p.
	 * @param q The parameter q.
	 * @return The presumed solution x.
	 */
	static double getSmallestSolutionOfQuadraticEquation2(double p, double q) {
		return q / (p + Math.sqrt(p * p - q));
	}
	
	/**
	 * Calculates a sum by summing up <code>numberOfSummations</code> times the (identical) value <code>value</code>.
	 * 
	 * @param valueToSum A value.
	 * @param numberOfSummations The number of summations.
	 * @return The result of summing up value numerToSum times.
	 */
	static double getSumOfNumberClassical(double valueToSum, int numberOfSummations) {
		double sum = 0.0;
		for(int i=0; i<numberOfSummations; i++) sum += valueToSum;
		return sum;
	}
	
	/**
	 * Calculates the sum of summing up <code>numberOfSummations</code> times the (identical) value <code>value</code>
	 * using the Kahan algorithm.
	 * 
	 * @param valueToSum A value.
	 * @param numberOfSummations The number of summations.
	 * @return The result of summing up value numerToSum times.
	 */
	static double getSumOfNumbersKahan(double valueToSum, int numberOfSummations) {
	    double error = 0.0;				// Running error compensation
	    double sum = 0.0;				// Running sum
	    for(int i=0; i<numberOfSummations; i++)  {
	    	double value	= valueToSum - error;			// Error corrected value
	    	double newSum	= sum + value;				// New sum
	    	error = (newSum - sum) - value;				// Numerical error
	    	sum = newSum;
	    }
	    return sum;
	}

}
