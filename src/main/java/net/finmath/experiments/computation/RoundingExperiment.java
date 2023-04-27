package net.finmath.experiments.computation;

/**
 * Simple experiment illustrating 1 + epsilon = 1
 * 
 * @author Christian Fries
 */
public class RoundingExperiment {

	public static void main(String[] args) {
		
		System.out.println("Rounding: ");

		double x = 1.0;
		
		double epsilon = 1E-16;
		
		double y = x + epsilon;	// 1 + 10^-16 = 1.0000000000000001
		
		System.out.println("x = " + x);
		System.out.println("𝜖 = " + epsilon);		
		System.out.println("y = x + 𝜖 = " + y);
		
		System.out.println("x == y is " + (x==y));
	}
}
