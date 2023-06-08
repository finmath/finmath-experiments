package net.finmath.experiments.montecarlo.process;

import java.time.LocalDateTime;
import java.util.Map;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.MonteCarloProduct;
import net.finmath.montecarlo.RandomVariableFromDoubleArray;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.model.ProcessModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.plots.PlotProcess2D;
import net.finmath.plots.Plots;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * A collection of small demos (with lots of repetitions) showing
 * how to use the basic interfaces (and some implementations) related
 * to the Monte-Carlo Simulation of Time Discretizations of Itô stochastic
 * processes.
 * 
 * The last examples will perform a Monte-Carlo valuation of an European
 * option under a Black-Scholes model.
 * 
 * These building blocks can be re-used to build different and more complex
 * models (e.g. interest rate term structure models).
 * 
 * @author Christian Fries
 */
public class MonteCarloProcessObjectsExperiments {

	public static void main(String[] args) throws CalculationException {
		testRandomVariable();
		testTimeDiscretization();

		testBrownianMotion();
		
		testBlackScholesDirectWay();
		
		// Euler Scheme
		testEulerSchemeWithProcessModelDirectWay();
		
		// Euler Scheme with Model		
		testEulerSchemeWithBlackScholesModel();
		
		// Valuation
		testValuationDirectWay();
		testValuationUsingProduct();
	}

	public static void testRandomVariable() {

		System.out.println("\n");
		System.out.println(RandomVariable.class.getTypeName());
		System.out.println("_".repeat(79));

		/*
		 * The interface RandomVariable provides an abstraction of the concept of a random variable.
		 */

		/*
		 * Scalar random variables are deterministic random variable attaining a single value.
		 */
		RandomVariable Y = new Scalar(2.0);

		/* 
		 * For Monte-Carlo simulations, random variables are represented by sampling vectors.
		 * 
		 * Since we focus on RandomVariables resulting from evaluating a stochastic process,
		 * RandomVariables carry a time parameter (denoting the filtration time).
		 */
		double time = 1.0;	// No relevance in this example. Will be useful, when considering processes
		RandomVariable X = new RandomVariableFromDoubleArray(time , new double[] { 2.0, 3.0, 1.0, 4.0});

		/*
		 * 
		 * Methods provided by a RandomVariable are basic arithmetic operations.
		 * RandomVariables are immutable: The result of such a method is a new random variable.
		 */
		
		RandomVariable Z = X.mult(Y);

		RandomVariable V = Z.average();

		/*
		 * A deterministic RandomVariable can be converted to a floating point double number.
		 */
		double value = V.doubleValue();

		System.out.println(X);
		System.out.println(Y.toString());
		System.out.println(Z);
		System.out.println(V);
		System.out.println(value);
	}

	public static void testTimeDiscretization() {

		System.out.println("\n");
		System.out.println(TimeDiscretization.class.getTypeName());
		System.out.println("_".repeat(79));

		/*
		 * The TimeDiscretization interface provides a few useful methods on time discretizations.
		 * 
		 * Mapping of index i to time t_i
		 * Mapping of times t to largest index i with t_i < t.
		 * Method to obtain time step sizes &Delta; t_i = t_{i+1}-t_{i}
		 */

		TimeDiscretization timeDiscretization1 = new TimeDiscretizationFromArray(0.0, 1.0, 3.0, 5.0);
		System.out.println("timeDiscretization1: " + timeDiscretization1);

		double time = 1.5;
		int timeIndex = timeDiscretization1.getTimeIndexNearestLessOrEqual(time);
		System.out.println("time " + time + " ⟶ " + "timeIndex (m(t)) " + timeIndex);

		double timeStep = timeDiscretization1.getTimeStep(timeIndex);
		System.out.println("timeIndex " + timeIndex + ": timeStep = " + timeStep);
		
		/*
		 * 20 time steps of size 0.5, starting in 0.0
		 */
		TimeDiscretization timeDiscretization2 = new TimeDiscretizationFromArray(0.0, 20, 0.5);
		System.out.println("timeDiscretization2: " + timeDiscretization2);
	}

	public static void testBrownianMotion() {

		System.out.println("\n");
		System.out.println(BrownianMotion.class.getTypeName());
		System.out.println("_".repeat(79));

		/*
		 * A Brownian Motion takes a TimeDiscretization and a RandomNumberGenerator
		 * and creates a set of RandomVariable-s representing the increments of
		 * a n-dimensional Brownian motion.
		 */

		double initialTime = 0.0;
		int numberOfTimeSteps = 50;
		double dt = 0.5;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, dt);

		int numberOfFactors = 1;
		int numberOfPaths = 10000;
		int randomNumberSeed = 3141;
		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(
				timeDiscretization, numberOfFactors, numberOfPaths, randomNumberSeed);

		System.out.println("brownianMotion: " + brownianMotion);
		
		/*
		 * An Euler scheme for dX = dW - that is: create W(t_{i}), i = 0, 1, ...
		 */
		RandomVariable[] X = new RandomVariable[timeDiscretization.getNumberOfTimes()];
		X[0] = new Scalar(0.0);
		for(int i=0; i<timeDiscretization.getNumberOfTimeSteps(); i++) {
			RandomVariable deltaW = brownianMotion.getBrownianIncrement(i, 0);		// Delta W_{0}(t_{i})
			X[i+1] = X[i].add(deltaW);
		}

		// t -> X(t)
		DoubleToRandomVariableFunction paths = time -> X[timeDiscretization.getTimeIndex(time)];
		
		PlotProcess2D plot = new PlotProcess2D(timeDiscretization, paths, 100);
		plot.setTitle("Paths of a BrownianMotion").setXAxisLabel("time").setYAxisLabel("value").show();
	}

	public static void testBlackScholesDirectWay() {

		// TimeDiscretization
		double initialTime = 0.0;
		int numberOfTimeSteps = 50;
		double dt = 0.5;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, dt);

		// BrownianMotion
		int numberOfFactors = 1;
		int numberOfPaths = 10000;
		int randomNumberSeed = 3141;
		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, randomNumberSeed);

		/*
		 * A Log-Euler scheme for dX = r X dt + σ X dW,   X(t_{i}) = X[i]
		 * X[i+1] = X|i] exp( (r-1/2 σ^2) Delta t_{i} + σ Delta W(t_{i})
		 */
		double initialValue = 100.0;	// X(0)
		double riskFreeRate = 0.05;		// r
		double volatility = 0.10;		// σ
		RandomVariable[] X = new RandomVariable[timeDiscretization.getNumberOfTimes()];
		X[0] = new Scalar(initialValue);
		for(int i=0; i<timeDiscretization.getNumberOfTimeSteps(); i++) {
			double deltaT = timeDiscretization.getTimeStep(i);
			RandomVariable deltaW = brownianMotion.getBrownianIncrement(i, 0);		// Delta W_{0}(t_{i})
			X[i+1] = X[i].mult(
					(deltaW.mult(volatility).add((riskFreeRate - 0.5 * volatility*volatility) * deltaT)).exp());
		}

		// t -> X(t)
		DoubleToRandomVariableFunction paths = time -> X[timeDiscretization.getTimeIndex(time)];
		
		PlotProcess2D plot = new PlotProcess2D(timeDiscretization, paths, 100);
		plot.setTitle("Paths of a Black-Scholes Model\n(using direct spec of Euler scheme)").setXAxisLabel("time").setYAxisLabel("value").show();

	}

	public static void testEulerSchemeWithProcessModelDirectWay() {

		System.out.println("\n");
		System.out.println(ProcessModel.class.getTypeName());
		System.out.println(EulerSchemeFromProcessModel.class.getTypeName());
		System.out.println("_".repeat(79));

		// TimeDiscretization
		double initialTime = 0.0;
		int numberOfTimeSteps = 50;
		double dt = 0.5;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, dt);

		// BrownianMotion
		int numberOfFactors = 1;
		int numberOfPaths = 10000;
		int randomNumberSeed = 3141;
		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(
				timeDiscretization, numberOfFactors, numberOfPaths, randomNumberSeed);

		/*
		 * Model specification for dX = r X dt + σ X dW discretized using
		 * X = f(Y), f = exp, dY = (r - 0.5 σ²) dt + σ dW, Y(0) = log(X(0))
		 */
		double initialValue = 100.0;		// X(0)
		double riskFreeRate = 0.05;			// r
		double volatility = 0.10;			// σ

		ProcessModel processModel = new ProcessModel() {
			@Override
			public LocalDateTime getReferenceDate() {
				return LocalDateTime.now();		// the date associated with t=0 (not used)
			}

			@Override
			public int getNumberOfComponents() {
				return 1;						// dimension of the process: d
			}

			@Override
			public RandomVariable applyStateSpaceTransform(MonteCarloProcess process, int timeIndex, int componentIndex, RandomVariable randomVariable) {
				return randomVariable.exp();
			}

			@Override
			public RandomVariable[] getInitialState(MonteCarloProcess process) {
				return new RandomVariable[] { new Scalar(Math.log(initialValue)) };
			}

			@Override
			public RandomVariable getNumeraire(MonteCarloProcess process, double time) throws CalculationException {
				return new Scalar(Math.exp(riskFreeRate * time));
			}

			@Override
			public RandomVariable[] getDrift(MonteCarloProcess process, int timeIndex, RandomVariable[] realizationAtTimeIndex, RandomVariable[] realizationPredictor) {
				return new RandomVariable[] { new Scalar(riskFreeRate - 0.5 * volatility * volatility) };
			}

			@Override
			public int getNumberOfFactors() {
				return 1;
			}

			@Override
			public RandomVariable[] getFactorLoading(MonteCarloProcess process, int timeIndex, int componentIndex, RandomVariable[] realizationAtTimeIndex) {
				return new RandomVariable[] { new Scalar(volatility) };
			}

			@Override
			public RandomVariable getRandomVariableForConstant(double value) {
				return new Scalar(value);
			}

			@Override
			public ProcessModel getCloneWithModifiedData(Map<String, Object> dataModified) throws CalculationException {
				throw new UnsupportedOperationException();
			}
		};

		// Use EulerSchemeFromProcessModel to generate the time-discrete process
		MonteCarloProcess process = new EulerSchemeFromProcessModel(processModel, brownianMotion);

		DoubleToRandomVariableFunction paths = time -> process.getProcessValue(timeDiscretization.getTimeIndex(time))[0];
		PlotProcess2D plot = new PlotProcess2D(timeDiscretization, paths, 100);
		plot.setTitle("Paths of a Black-Scholes Model\n(using EulerSchemeFromProcessModel)").setXAxisLabel("time").setYAxisLabel("value").show();		
	}

	public static void testEulerSchemeWithBlackScholesModel() {

		// TimeDiscretization
		double initialTime = 0.0;
		int numberOfTimeSteps = 50;
		double dt = 0.5;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, dt);

		// BrownianMotion
		int numberOfFactors = 1;
		int numberOfPaths = 10000;
		int randomNumberSeed = 3141;
		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(
				timeDiscretization, numberOfFactors, numberOfPaths, randomNumberSeed);

		// Model X - BlackScholesModel Parameters
		double initialValue = 100.0;		// X(0)
		double riskFreeRate = 0.05;			// r
		double volatility = 0.10;			// σ
		ProcessModel processModel = new BlackScholesModel(initialValue, riskFreeRate, volatility);

		// Time Discrete Process X
		MonteCarloProcess process = new EulerSchemeFromProcessModel(processModel, brownianMotion);

		DoubleToRandomVariableFunction paths = time -> process.getProcessValue(timeDiscretization.getTimeIndex(time))[0];
		PlotProcess2D plot = new PlotProcess2D(timeDiscretization, paths, 100);
		plot.setTitle("Paths of a Black-Scholes Model\n(using EulerSchemeFromProcessModel and BlackScholesModel)").setXAxisLabel("time").setYAxisLabel("value").show();		
	}

	public static void testValuationDirectWay() throws CalculationException {

		System.out.println("\n");
		System.out.println("Valuation (direct way):");
		System.out.println("_".repeat(79));

		// Model X - BlackScholesModel Parameters
		double initialValue = 100.0;		// X(0)
		double riskFreeRate = 0.05;			// r
		double sigma = 0.30;			// σ
		ProcessModel processModel = new BlackScholesModel(initialValue, riskFreeRate, sigma);

		// TimeDiscretization
		double initialTime = 0.0;
		int numberOfTimeSteps = 50;
		double dt = 0.5;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, dt);

		// BrownianMotion
		int numberOfFactors = 1;
		int numberOfPaths = 100000;
		int randomNumberSeed = 3141;
		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, randomNumberSeed);

		// Time discrete process (Euler Scheme)
		MonteCarloProcess process = new EulerSchemeFromProcessModel(processModel, brownianMotion);

		/*
		 * Valuation of paying max(S(T)-K,0) in T
		 */
		double maturity = 10.0;	// T
		double strike = 160.0;	// K
		RandomVariable underlying = process.getProcessValue(process.getTimeIndex(maturity))[0];	// S(T)
		RandomVariable payoff = underlying.sub(strike).floor(0.0);								// V(T)
		RandomVariable numeraireAtPayment = processModel.getNumeraire(process, maturity);		// N(T)
		RandomVariable numeraireAtValuation = processModel.getNumeraire(process, initialTime);	// N(0)

		// V(0) = E( V(T) / N(T) * N(0) )
		double value = payoff.div(numeraireAtPayment).mult(numeraireAtValuation).average().doubleValue();
		double valueAnalytic = AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, sigma, maturity, strike);
		
		System.out.println("value...............: " + value);
		System.out.println("value analytic......: " + valueAnalytic);
		
		MonteCarloAssetModel model = new MonteCarloAssetModel(process);
		MonteCarloProduct product = new EuropeanOption(maturity, strike);
		double value2 = product.getValue(model);
		System.out.println("value...............: " + value2);
	}

	public static void testValuationUsingProduct() throws CalculationException {

		System.out.println("\n");
		System.out.println("Valuation (using product):");
		System.out.println("_".repeat(79));

		// Model X - BlackScholesModel Parameters
		double initialValue = 100.0;		// X(0)
		double riskFreeRate = 0.05;			// r
		double sigma = 0.30;			// σ
		ProcessModel processModel = new BlackScholesModel(initialValue, riskFreeRate, sigma);

		// TimeDiscretization
		double initialTime = 0.0;
		int numberOfTimeSteps = 50;
		double dt = 0.5;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, dt);

		// BrownianMotion
		int numberOfFactors = 1;
		int numberOfPaths = 100000;
		int randomNumberSeed = 3141;
		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, randomNumberSeed);

		// Time discrete process (Euler Scheme)
		MonteCarloProcess process = new EulerSchemeFromProcessModel(processModel, brownianMotion);

		/*
		 * Valuation of paying max(S(T)-K,0) in T
		 */
		double maturity = 10.0;	// T
		double strike = 160.0;	// K

		MonteCarloAssetModel model = new MonteCarloAssetModel(process);
		MonteCarloProduct product = new EuropeanOption(maturity, strike);
		double value = product.getValue(model);

		double valueAnalytic = AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, sigma, maturity, strike);
		
		System.out.println("value...............: " + value);
		System.out.println("value analytic......: " + valueAnalytic);
		
	}
}
