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
	 * Main program to run the experiment.
	 * 
	 * @param args Arguments, not used
	 */
	public static void main(String[] args) {

		System.out.println("\nSome experiments related to computer arithmetic (IEEE 754).\n");

		/*
		 * Test the machine precision
		 */
		System.out.println("\n\nTest 1: Machine Precision");
		System.out.println("__________________________________________________________________");

		double eps = getCalculatedMachinePrecision();

		System.out.println("Calculated Machine Precision: eps = " + eps);
		System.out.println("                          1 + eps = " + (1+eps));
		System.out.println("                          2.0*eps = " + 2.0*eps);
		System.out.println("                      1 + 2.0*eps = " + (1+2.0*eps));

		/*
		 * Test calculations close to the maximum value
		 */
		System.out.println("\n\nTest 2: Infinity");		
		System.out.println("__________________________________________________________________");

		double maxDouble	= 	Double.MAX_VALUE;

		System.out.println("Largest possible number:              maxDouble = " + maxDouble);
		System.out.println("                                maxDouble + eps = " + (maxDouble + eps));		
		System.out.println("                    maxDouble + maxDouble * eps = " + (maxDouble + maxDouble * eps));
		System.out.println("                maxDouble + maxDouble * eps / 2 = " + (maxDouble + maxDouble * eps/2));

		/*
		 * Test the behavior of +0 and -0.
		 */
		System.out.println("\n\nTest 3: Check behavior +0 and -0.");
		System.out.println("__________________________________________________________________");

		// Create +0
		double zero = 1;
		while(zero > 0) {
			zero = zero / 2;
		}
		System.out.println("     zero   = " + zero);
		
		// Create -0
		double minusZero = -1;
		while(minusZero < 0) {
			minusZero = minusZero / 2;
		}
		System.out.println("minusZero   = " + minusZero);

		// Example where +0 and -0 makes a difference
		System.out.println("1/zero      = " + 1/zero);
		System.out.println("1/minusZero = " + 1/minusZero);

		// Check +0 and -0
		System.out.println("Testing 0 == -0 gives " + (zero == minusZero));


		/*
		 * Test solution of quadratic equation
		 */
		double p = 1000000.0;
		double q = 1;
		System.out.println("\n\nTest 4: Loss of significance: Solution of quadratic equation x^2 - 2px + q = 0 with p=" + p + " q="+q+"");
		System.out.println("__________________________________________________________________");
		
		System.out.println("Method 1");
		double x1 = getSmallestSolutionOfQuadraticEquation1(p,q);
		System.out.println("Solution:           x = " + x1);
		System.out.println("Result: x^2 - 2px + q = " + (x1 * x1 - 2 * p * x1 + q));

		System.out.println("Method 2");
		double x2 = getSmallestSolutionOfQuadraticEquation2(p,q);
		System.out.println("Solution:           x = " + x2);
		System.out.println("Result: x^2 - 2px + q = " + (x2 * x2 - 2 * p * x2 + q));

		/*
		 * Test summation using classical an Kahan summation
		 */
		double value			= 1.0/10.0;
		int numberOfSummations	= 1000000;
		System.out.println("\n\nTest 5: Summation: Calculating the average of " + numberOfSummations + " numbers of value " + value + ".");
		System.out.println("__________________________________________________________________");

		System.out.println("Method 1");
		double sumClassical		= getSumOfNumbersClassical(value, numberOfSummations);
		double averageClassical	= sumClassical / numberOfSummations;
		System.out.println("Average: " + averageClassical);

		System.out.println("Method 2");
		double sumKahan		= getSumOfNumbersKahan(value, numberOfSummations);
		double averageKahan	= sumKahan / numberOfSummations;		
		System.out.println("Average: " + averageKahan);
	}
	
	
	/**
	 * Returns the smallest epsilon such that 1 + epsilon > 1 = 1 + epsilon/2.
	 * 
	 * @return The machine precision.
	 */
	static double getCalculatedMachinePrecision() {
		
		double epsilon = 1.0;
		while(1 + epsilon > 1) {
			epsilon = epsilon / 2.0;
		}
		
		return epsilon;
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
	static double getSumOfNumbersClassical(double valueToSum, int numberOfSummations) {
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
