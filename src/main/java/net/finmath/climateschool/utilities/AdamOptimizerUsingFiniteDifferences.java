package net.finmath.climateschool.utilities;


import java.util.Arrays;

import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.automaticdifferentiation.RandomVariableDifferentiable;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory;
import net.finmath.stochastic.RandomVariable;

/**
 * Implementation of the ADAM optimizer for objective functions (loss functions)
 * that map <code>RandomVariable[]</code> parameters to a <code>RandomVariable</code> value.
 *
 * The gradient is calculated using finite differences.
 *
 * @author Maximilian Singhof
 * @author Chritian Fries
 */
public abstract class AdamOptimizerUsingFiniteDifferences {

	public enum GradientMethod {
		COMPLETE,
		AVERAGE,
		VALUE_AT_RISK,
		EXPECTED_SHORTFALL
	}

	private final GradientMethod gradientMethod;

	private final int iterations;
	private boolean runnning = false;
	private double[] learningRate ;
	private final double eps ;
	private final double[] betas ;

	private final RandomVariableDifferentiable[] parameters ;
	private RandomVariableDifferentiable[] bestFitParameters;
	private double bestValue = Double.MAX_VALUE;

	public AdamOptimizerUsingFiniteDifferences(double[] initialParameters, int iterations, double learningRate, double eps, double[] betas,
			GradientMethod gradientMethod) {
		final RandomVariableDifferentiableAADFactory randomVariableAADFactory = new RandomVariableDifferentiableAADFactory();

		this.iterations = iterations;
		this.learningRate = new double[initialParameters.length];
		Arrays.fill(this.learningRate,learningRate);
		this.eps = eps;
		this.betas = betas;
		this.gradientMethod = gradientMethod;

		this.parameters = new RandomVariableDifferentiable[initialParameters.length];
		for(int i=0; i<initialParameters.length; i++) {
			this.parameters[i] = randomVariableAADFactory.createRandomVariable(initialParameters[i]);
		}
	}

	public AdamOptimizerUsingFiniteDifferences(double[] initialParameters, int iterations, double learningRate, GradientMethod gradientMethod) {
		this(initialParameters, iterations, learningRate, 1e-8, new double[] {0.9, 0.999}, gradientMethod);
	}

	public AdamOptimizerUsingFiniteDifferences(double[] initialParameters, int iterations, GradientMethod gradientMethod) {
		this(initialParameters, iterations, 1e-3, 1e-8, new double[] {0.9, 0.999}, gradientMethod);
	}

	public static void main(String[] args) {
		// Rosenbrock function
		final double[] initialParameters = new double[] {0.4,2};

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		final RandomVariable a = randomVariableFactory.createRandomVariable(1.0);
		final RandomVariable b = randomVariableFactory.createRandomVariable(100.0);

		final AdamOptimizerUsingFiniteDifferences optimizer = new AdamOptimizerUsingFiniteDifferences(initialParameters, 8001, 0.01, GradientMethod.AVERAGE) {
			@Override
			public RandomVariable setValue(RandomVariable[] parameters) {
				return a.sub(parameters[0]).squared().add(b.mult(parameters[1].sub(parameters[0].squared()).squared()));
			}
		};

		optimizer.run();
		System.out.println(Arrays.toString(Arrays.stream(optimizer.getBestFitParameters()).mapToDouble(RandomVariable::getAverage).toArray()));
	}

	public abstract RandomVariable setValue(RandomVariable[] parameters) ;

	public void run() {
		runnning = true;
		if (gradientMethod != GradientMethod.COMPLETE) {
			final double[] m = new double[parameters.length];
			final double[] v = new double[parameters.length];

			for(int k=0; k<iterations && runnning; k++) {
				final RandomVariable value = setValue(parameters);
				if (value.getAverage() < bestValue || bestFitParameters == null) {
					bestValue = value.getAverage();
					bestFitParameters=parameters.clone();
				}

				final RandomVariable[] derivative = getGradient(parameters, value);

				for(int i=0; i< parameters.length; i++) {
					double gradient;
					try {
						gradient = (gradientMethod == GradientMethod.AVERAGE) ? derivative[i].getAverage() :
							-RandomOperators.expectedShortFall(derivative[i].mult(-1.0),0.05).getAverage();
					} catch (final NullPointerException e) {
						continue;
					}

					m[i] = (betas[0]*m[i] + (1-betas[0])*gradient);
					v[i] = (betas[1]*v[i] + (1-betas[1])*gradient*gradient);

					final double update_m = m[i] / (1-Math.pow(betas[0],k+1));
					final double update_v = v[i] / (1-Math.pow(betas[1],k+1));
					final double stepDirection = update_m / (Math.sqrt(update_v)+eps);

					parameters[i] = ((RandomVariableDifferentiable) parameters[i].sub(learningRate[i]*stepDirection)).getCloneIndependent();
				}

				if (k % 10 == 0) {
					final double valueForPrinting = (gradientMethod == GradientMethod.AVERAGE) ? value.getAverage() :
						-RandomOperators.expectedShortFall(value.mult(-1.0),0.05).doubleValue();
					if (k % 100 == 0) {
						System.out.printf("iteration %8d \t\t value %8.4f %n", k, -valueForPrinting);
					} else {
						//						System.out.printf("iteration %8d \t\t value %8.4f \r", k, -valueForPrinting);
					}
				}
			}
		} else {
			final RandomVariable[] m = new RandomVariable[parameters.length];
			final RandomVariable[] v = new RandomVariable[parameters.length];

			final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();
			for(int i=0; i<m.length; i++) {
				m[i] = randomVariableFactory.createRandomVariable(0);
				v[i] = randomVariableFactory.createRandomVariable(0);
			}

			for(int k=0; k<iterations && runnning; k++) {
				final RandomVariable value = setValue(parameters);
				if (value.getAverage() < bestValue || bestFitParameters == null) {
					bestValue = value.getAverage();
					bestFitParameters=parameters.clone();
				}
				
				final RandomVariable[] derivative = getGradient(parameters, value);
				for(int i=0; i< parameters.length; i++) {

					RandomVariable gradient;
					try {
						gradient = derivative[i];
					} catch (final NullPointerException e) {
						continue;
					}

					m[i] = m[i].mult(betas[0]).add(gradient.mult(1-betas[0]));
					v[i] = v[i].mult(betas[1]).add(gradient.squared().mult(1-betas[1]));

					final RandomVariable update_m = m[i].div(1-Math.pow(betas[0],k+1));
					final RandomVariable update_v = v[i].div(1-Math.pow(betas[1],k+1));
					final RandomVariable stepDirection = update_m.div(update_v.sqrt().add(eps));

					parameters[i] =
							((RandomVariableDifferentiable) parameters[i].sub(stepDirection.mult(learningRate[i]))).getCloneIndependent();
				}

				if (k % 100 == 0) {
					System.out.printf("iteration %8.4f \t\t value %8.4f %n", (double) k,value.getAverage());
				}
			}
		}
	}
	
	public void stop() {
		runnning = false;
	}

	public RandomVariableDifferentiable[] getBestFitParameters() {
		return bestFitParameters;
	}

	public RandomVariableDifferentiable[] getLastParameters() {
		return parameters;
	}

	public void setLearningRate(double[] learningRate) {
		this.learningRate = learningRate;
	}

	public void setLearningRate(double learningRate, int index) {
		this.learningRate[index] = learningRate;
	}

	private RandomVariable[] getGradient(RandomVariable[] parameters, RandomVariable value) {

		final double epsilon = 1E-8;
		final RandomVariable[] gradient =  new RandomVariable[parameters.length];
		for(int i=0; i<parameters.length; i++) {
			final RandomVariable[] parametersShifted = parameters.clone();
			final RandomVariable parametersShift = parameters[i].abs().add(1).mult(epsilon);
			parametersShifted[i] = parameters[i].add(parametersShift);
			final RandomVariable valueShiftedUp = setValue(parametersShifted);
			gradient[i] = valueShiftedUp.sub(value).div(parametersShift);
		}

		return gradient;
	}
}
