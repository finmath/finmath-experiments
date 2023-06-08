package net.finmath.experiments.liboverview;

import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.automaticdifferentiation.RandomVariableDifferentiable;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory;
import net.finmath.stochastic.RandomVariable;

/**
 * Demonstrate RandomVariable and dependency injection via different implementations of RandomVariable operations.
 * 
 * @author Christian Fries
 */
public class RandomVariableDependencyInjection {

	public static void main(String[] args) {

		testFiniteDifference();

		testAAD();
	}

	public static RandomVariable someFunction(RandomVariable x) {

		// y = exp(-0.5 * x * x)
		RandomVariable y = x.squared().mult(-0.5).exp();

		return y;
	}

	private static void testFiniteDifference() {

		System.out.println("Injecting a standard random variable, calculating dy/dx via finite differences.");
		RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		RandomVariable x = randomVariableFactory.createRandomVariable(new double[] { -1, 0, 1, 2, 3 });

		RandomVariable y = someFunction(x);

		System.out.println("x.............: " + x.getClass().getSimpleName());
		System.out.println("f(x)..........: " + y.getClass().getSimpleName());
		System.out.println("E(f(x)).......: " + y.average().doubleValue());

		double epsilon = 1E-7;
		// (f(x+e)-f(x)) / e
		RandomVariable dydx = someFunction(x.add(epsilon)).sub(someFunction(x)).div(epsilon);

		System.out.println("d/dx E(f(x))..: " + dydx.average().doubleValue());

		System.out.println("_".repeat(79) + "\n");
	}

	private static void testAAD() {

		System.out.println("Injecting a random variable that perfoms AAD.");
		RandomVariableFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory();

		RandomVariableDifferentiable x = (RandomVariableDifferentiable) randomVariableFactory.createRandomVariable(new double[] { -1, 0, 1, 2, 3 });

		RandomVariableDifferentiable y = (RandomVariableDifferentiable) someFunction(x);

		System.out.println("x.............: " + x.getClass().getSimpleName());
		System.out.println("f(x)..........: " + y.getClass().getSimpleName());
		System.out.println("E(f(x)).......: " + y.average().doubleValue());

		RandomVariable dydx = y.getGradient().get(x.getID());

		System.out.println("d/dx E(f(x))..: " + dydx.average().doubleValue());

		System.out.println("_".repeat(79) + "\n");
	}

}
