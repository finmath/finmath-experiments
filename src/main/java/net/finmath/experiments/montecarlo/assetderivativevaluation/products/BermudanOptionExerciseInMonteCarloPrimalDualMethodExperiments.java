package net.finmath.experiments.montecarlo.assetderivativevaluation.products;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFromDoubleArray;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.conditionalexpectation.MonteCarloConditionalExpectationRegression;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.optimizer.Optimizer;
import net.finmath.optimizer.Optimizer.ObjectiveFunction;
import net.finmath.optimizer.OptimizerFactory;
import net.finmath.optimizer.OptimizerFactoryCMAES;
import net.finmath.optimizer.SolverException;
import net.finmath.stochastic.ConditionalExpectationEstimator;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretizationFromArray;

public class BermudanOptionExerciseInMonteCarloPrimalDualMethodExperiments {

	// Model parameters
	final double initialValue = 100.0;
	final double riskFreeRate = 0.00;
	final double volatility = 0.30;

	// Time Discretization
	final double initialTime = 0.0;
	final double timeHorizon = 5.0;
	final double dt = 1.0;

	// Monte-Carlo Simulation
	final int numberOfPaths = 1000000;
	final int seed = 3141;

	// Product parameters
	final double maturity1 = 2.0;
	final double maturity2 = 5.0;

	final double strike1 = 80.0 * Math.exp(riskFreeRate * maturity2);
	final double strike2 = 100.0 * Math.exp(riskFreeRate * maturity2);

	// Regression parameters
	boolean useBinning = false;
	int numberOfBasisFunctions = 5;

	public static void main(String[] args) throws Exception {
		(new BermudanOptionExerciseInMonteCarloPrimalDualMethodExperiments()).value();
	}

	private void value() throws Exception {
		final BlackScholesModel blackScholesModel = new BlackScholesModel(initialValue, riskFreeRate, volatility);
		final BrownianMotion bm = new BrownianMotionFromMersenneRandomNumbers(new TimeDiscretizationFromArray(initialTime, (int)Math.round(timeHorizon/dt), dt), 1, numberOfPaths, seed);
		final MonteCarloProcess process = new EulerSchemeFromProcessModel(blackScholesModel, bm);
		final MonteCarloAssetModel model = new MonteCarloAssetModel(process);

		valueWithAnalyticCondExpectation(model);
		valueBackwardAlgorithmWithAnalyticCondExpectation(model);
		valueWithUpperBoundOptimization(model);

		System.out.println();

		valueWithForesight(model);
		
		System.out.println();

		double valueLowerBound = valueWithRegression(model);
		double valueUpperBound = valueWithUpperBoundWithOptimization(model);

		valueWithUpperBoundWithEstimatedCondExp(model);
		
		System.out.println();

		System.out.println(String.format("Bermudan value (average of lower and upper bound)...................: %10.8f ", (valueLowerBound + valueUpperBound)/2.0));
	}

	private void valueWithAnalyticCondExpectation(MonteCarloAssetModel model) throws Exception {
		final RandomVariable stockInT2 = model.getAssetValue(maturity2, 0);		// S(T2)
		final RandomVariable stockInT1 = model.getAssetValue(maturity1, 0);		// S(T1)

		final RandomVariable valueOption2InT2 = stockInT2.sub(strike2).floor(0.0);	// max(S(T2)-K,0)
		final RandomVariable valueOption1InT1 = stockInT1.sub(strike1).floor(0.0);	// max(S(T1)-K,0)
		final RandomVariable valueOption2InT1 = AnalyticFormulas.blackScholesOptionValue(stockInT1, riskFreeRate, volatility, maturity2-maturity1, strike2);

		// Convert all time T-values to numeraire relative values by dividing by N(T)
		final RandomVariable valueRelativeOption2InT2 = valueOption2InT2.div(model.getNumeraire(maturity2));
		final RandomVariable valueRelativeOption1InT1 = valueOption1InT1.div(model.getNumeraire(maturity1));
		final RandomVariable valueRelativeOption2InT1 = valueOption2InT1.div(model.getNumeraire(maturity1));

		final RandomVariable exerciseCriteria = valueRelativeOption2InT1.sub(valueRelativeOption1InT1);
		final RandomVariable bermudanPathwiseValueAdmissible = exerciseCriteria.choose(valueRelativeOption2InT1, valueRelativeOption1InT1);

		final var bermudanValue = bermudanPathwiseValueAdmissible.mult(model.getNumeraire(0.0));

		System.out.println(String.format("Bermudan value with analytic cond. expectation......................: %10.8f ± %10.8f", bermudanValue.getAverage(), bermudanValue.getStandardError()));
	}

	private void valueBackwardAlgorithmWithAnalyticCondExpectation(MonteCarloAssetModel model) throws Exception {
		final RandomVariable stockInT2 = model.getAssetValue(maturity2, 0);		// S(T2)
		final RandomVariable stockInT1 = model.getAssetValue(maturity1, 0);		// S(T1)

		final RandomVariable valueOption2InT2 = stockInT2.sub(strike2).floor(0.0);	// max(S(T2)-K,0)
		final RandomVariable valueOption1InT1 = stockInT1.sub(strike1).floor(0.0);	// max(S(T1)-K,0)
		final RandomVariable valueOption2InT1 = AnalyticFormulas.blackScholesOptionValue(stockInT1, riskFreeRate, volatility, maturity2-maturity1, strike2);

		// Convert all time T-values to numeraire relative values by dividing by N(T)
		final RandomVariable valueRelativeOption2InT2 = valueOption2InT2.div(model.getNumeraire(maturity2));
		final RandomVariable valueRelativeOption1InT1 = valueOption1InT1.div(model.getNumeraire(maturity1));
		final RandomVariable valueRelativeOption2InT1 = valueOption2InT1.div(model.getNumeraire(maturity1));

		final RandomVariable exerciseCriteria = valueRelativeOption2InT1.sub(valueRelativeOption1InT1);
		final RandomVariable bermudanPathwiseValueAdmissible = exerciseCriteria.choose(valueRelativeOption2InT2, valueRelativeOption1InT1);

		final var bermudanValue = bermudanPathwiseValueAdmissible.mult(model.getNumeraire(0.0));

		System.out.println(String.format("Bermudan value backward algorithm with analytic cond. expectation...: %10.8f ± %10.8f", bermudanValue.getAverage(), bermudanValue.getStandardError()));
	}

	private void valueWithForesight(MonteCarloAssetModel model) throws Exception {
		final RandomVariable stockInT2 = model.getAssetValue(maturity2, 0);		// S(T2)
		final RandomVariable stockInT1 = model.getAssetValue(maturity1, 0);		// S(T1)

		final RandomVariable valueOption2InT2 = stockInT2.sub(strike2).floor(0.0);	// max(S(T2)-K,0)
		final RandomVariable valueOption1InT1 = stockInT1.sub(strike1).floor(0.0);	// max(S(T1)-K,0)
		final RandomVariable valueOption2InT1 = AnalyticFormulas.blackScholesOptionValue(stockInT1, riskFreeRate, volatility, maturity2-maturity1, strike2);

		// Convert all time T-values to numeraire relative values by dividing by N(T)
		final RandomVariable valueRelativeOption2InT2 = valueOption2InT2.div(model.getNumeraire(maturity2));
		final RandomVariable valueRelativeOption1InT1 = valueOption1InT1.div(model.getNumeraire(maturity1));
		final RandomVariable valueRelativeOption2InT1 = valueOption2InT1.div(model.getNumeraire(maturity1));

		final RandomVariable bermudanPathwiseValueForesight = valueRelativeOption2InT2.sub(valueRelativeOption1InT1)
				.choose(valueRelativeOption2InT2, valueRelativeOption1InT1);

		final var bermudanValueWithForsight = bermudanPathwiseValueForesight.mult(model.getNumeraire(0.0));

		System.out.println(String.format("Bermudan value backward algorithm with perfect forsight (wrong).....: %10.8f ± %10.8f", bermudanValueWithForsight.getAverage(), bermudanValueWithForsight.getStandardError()));
	}

	private void valueWithUpperBoundOptimization(MonteCarloAssetModel model) throws Exception {
		final RandomVariable stockInT2 = model.getAssetValue(maturity2, 0);		// S(T2)
		final RandomVariable stockInT1 = model.getAssetValue(maturity1, 0);		// S(T1)

		final RandomVariable valueOption2InT2 = stockInT2.sub(strike2).floor(0.0);	// max(S(T2)-K,0)
		final RandomVariable valueOption1InT1 = stockInT1.sub(strike1).floor(0.0);	// max(S(T1)-K,0)
		final RandomVariable valueOption2InT1 = AnalyticFormulas.blackScholesOptionValue(stockInT1, riskFreeRate, volatility, maturity2-maturity1, strike2);

		// Convert all time T-values to numeraire relative values by dividing by N(T)
		final RandomVariable valueRelativeOption2InT2 = valueOption2InT2.div(model.getNumeraire(maturity2));
		final RandomVariable valueRelativeOption1InT1 = valueOption1InT1.div(model.getNumeraire(maturity1));
		final RandomVariable valueRelativeOption2InT1 = valueOption2InT1.div(model.getNumeraire(maturity1));

		RandomVariable[] martingaleInclrementForDualMethod = new RandomVariable[3];
		martingaleInclrementForDualMethod[2] = model.getRandomVariableForConstant(0.0);
		martingaleInclrementForDualMethod[1] = valueRelativeOption2InT2.sub(valueRelativeOption2InT1);
		martingaleInclrementForDualMethod[0] = valueRelativeOption1InT1.floor(valueRelativeOption2InT1).sub(valueRelativeOption1InT1.floor(valueRelativeOption2InT1).average());

		RandomVariable[] martingale = new RandomVariable[2];
		martingale[0] = martingaleInclrementForDualMethod[0];
		martingale[1] = martingale[0].add(martingaleInclrementForDualMethod[1]);

		RandomVariable exerciseCriteria = valueRelativeOption2InT2.sub(martingale[1]).sub(valueRelativeOption1InT1.sub(martingale[0]));

		RandomVariable value = exerciseCriteria.choose(valueRelativeOption2InT2.sub(martingale[1]), valueRelativeOption1InT1.sub(martingale[0]));

		System.out.println(String.format("Bermudan value dual method with analytic conditional expectation....: %10.8f ± %10.8f", value.getAverage(), value.getStandardError()));
	}

	private double valueWithRegression(MonteCarloAssetModel model) throws Exception {
		final RandomVariable stockInT2 = model.getAssetValue(maturity2, 0);		// S(T2)
		final RandomVariable stockInT1 = model.getAssetValue(maturity1, 0);		// S(T1)

		final RandomVariable valueOption2InT2 = stockInT2.sub(strike2).floor(0.0);	// max(S(T2)-K,0)
		final RandomVariable valueOption1InT1 = stockInT1.sub(strike1).floor(0.0);	// max(S(T1)-K,0)

		// Convert all time T-values to numeraire relative values by dividing by N(T)
		final RandomVariable valueRelativeOption2InT2 = valueOption2InT2.div(model.getNumeraire(maturity2));
		final RandomVariable valueRelativeOption1InT1 = valueOption1InT1.div(model.getNumeraire(maturity1));


		RandomVariable basisFunctionUnderlying = stockInT1.sub(strike1).floor(0.0);
		List<RandomVariable> basisFunctions = useBinning ? getRegressionBasisFunctionsBinning(basisFunctionUnderlying, numberOfBasisFunctions) : getRegressionBasisFunctions(basisFunctionUnderlying, numberOfBasisFunctions);

		final ConditionalExpectationEstimator condExpEstimator = new MonteCarloConditionalExpectationRegression(basisFunctions.toArray(new RandomVariable[0]));

		// Calculate conditional expectation on numeraire relative quantity.
		final RandomVariable valueRelativeOption2InT1 = valueRelativeOption2InT2.getConditionalExpectation(condExpEstimator);


		final RandomVariable bermudanRelativeValueSnellEnvelope = valueRelativeOption2InT1.floor(valueRelativeOption1InT1);
		final var bermudanValueSnellEnvelope = bermudanRelativeValueSnellEnvelope.mult(model.getNumeraire(initialTime));
		System.out.println(String.format("Bermudan value Snell envelope with regression.......................: %10.8f ± %10.8f",bermudanValueSnellEnvelope.getAverage(), bermudanValueSnellEnvelope.getStandardError()));


		final RandomVariable bermudanRelativeValuePathwiseBackwardAlg = valueRelativeOption2InT1.sub(valueRelativeOption1InT1).choose(valueRelativeOption2InT2, valueRelativeOption1InT1);
		final var bermudanValuePathwiseBackwardAlg = bermudanRelativeValuePathwiseBackwardAlg.mult(model.getNumeraire(initialTime));

		System.out.println(String.format("Bermudan value backward algorithm with regression...................: %10.8f ± %10.8f", bermudanValuePathwiseBackwardAlg.getAverage(), bermudanValuePathwiseBackwardAlg.getStandardError()));
		
		return bermudanValuePathwiseBackwardAlg.getAverage();
	}

	private double valueWithUpperBoundWithOptimization(MonteCarloAssetModel model) throws Exception {
		final RandomVariable stockInT2 = model.getAssetValue(maturity2, 0);		// S(T2)
		final RandomVariable stockInT1 = model.getAssetValue(maturity1, 0);		// S(T1)

		final RandomVariable valueOption2InT2 = stockInT2.sub(strike2).floor(0.0);	// max(S(T2)-K,0)
		final RandomVariable valueOption1InT1 = stockInT1.sub(strike1).floor(0.0);	// max(S(T1)-K,0)

		// Convert all time T-values to numeraire relative values by dividing by N(T)
		final RandomVariable valueRelativeOption2InT2 = valueOption2InT2.div(model.getNumeraire(maturity2));
		final RandomVariable valueRelativeOption1InT1 = valueOption1InT1.div(model.getNumeraire(maturity1));

		
		RandomVariable basisFunctionUnderlying = stockInT1.sub(strike1).floor(0.0);
		List<RandomVariable> basisFunctions = useBinning ? getRegressionBasisFunctionsBinning(basisFunctionUnderlying, numberOfBasisFunctions) : getRegressionBasisFunctions(basisFunctionUnderlying, numberOfBasisFunctions);

		final ConditionalExpectationEstimator condExpEstimator = new MonteCarloConditionalExpectationRegression(basisFunctions.toArray(new RandomVariable[0]));

		// Calculate conditional expectation on numeraire relative quantity.
		final RandomVariable valueRelativeOption2InT1 = valueRelativeOption2InT2.getConditionalExpectation(condExpEstimator);

		RandomVariable[] martingaleInclrementForDualMethod = new RandomVariable[3];
		martingaleInclrementForDualMethod[2] = model.getRandomVariableForConstant(0.0);
		martingaleInclrementForDualMethod[1] = valueRelativeOption2InT2.sub(valueRelativeOption2InT1);
		martingaleInclrementForDualMethod[0] = valueRelativeOption1InT1.floor(valueRelativeOption2InT1).sub(valueRelativeOption1InT1.floor(valueRelativeOption2InT1).average());

		RandomVariable[] martingale = new RandomVariable[2];
		martingale[0] = martingaleInclrementForDualMethod[0];
		martingale[1] = martingale[0].add(martingaleInclrementForDualMethod[1]);

		long timeStart, timeEnd;
		
		timeStart = System.currentTimeMillis();
		OptimizerFactory of = new OptimizerFactoryCMAES(1E-5, 1000, new double[] { -5.0, -100.0 }, new double[] { 5.0, 100.0 }, new double[] { 1.0, 10.0 });
		Optimizer opt = of.getOptimizer(new ObjectiveFunction() {
			
			@Override
			public void setValues(double[] parameters, double[] values) throws SolverException {
				double lambda1 = parameters[0];
				double lambda2 = parameters[1];

				try {

					martingale[0] = stockInT1.div(model.getNumeraire(maturity1)).sub(initialValue).mult(lambda1).add(stockInT1.log().sub(Math.log(initialValue)+(riskFreeRate - 0.5 * volatility * volatility) * maturity1).mult(lambda2));
					martingale[1] = stockInT2.div(model.getNumeraire(maturity2)).sub(initialValue).mult(lambda1).add(stockInT2.log().sub(Math.log(initialValue)+(riskFreeRate - 0.5 * volatility * volatility) * maturity2).mult(lambda2));

					RandomVariable exerciseCriteria = valueRelativeOption2InT2.sub(martingale[1]).sub(valueRelativeOption1InT1.sub(martingale[0]));

					RandomVariable value = exerciseCriteria.choose(valueRelativeOption2InT2.sub(martingale[1]), valueRelativeOption1InT1.sub(martingale[0]));
					System.out.print(String.format("\rBermudan value dual method with optimization........................: %10.8f ± %10.8f (%8.5f,%8.5f)  ", value.getAverage(), value.getStandardError(), lambda1, lambda2));
					values[0] = value.getAverage();
				}
				catch(CalculationException e) {
					throw new SolverException(e);
				}
			}
		}, new double[] { 0.0, 60.0 }, new double[] { 0.0 });
		System.out.println();
		
		opt.run();
		timeEnd = System.currentTimeMillis();
		System.out.println("\tTime: " + (timeEnd-timeStart)/1000 + " sec.");
		timeStart = timeEnd;
		
		double[] bestParameters = opt.getBestFitParameters();
		
		double lambda1 = bestParameters[0];
		double lambda2 = bestParameters[1];
		martingale[0] = stockInT1.div(model.getNumeraire(maturity1)).sub(initialValue).mult(lambda1).add(stockInT1.log().sub(Math.log(initialValue)+(riskFreeRate - 0.5 * volatility * volatility) * maturity1).mult(lambda2));
		martingale[1] = stockInT2.div(model.getNumeraire(maturity2)).sub(initialValue).mult(lambda1).add(stockInT2.log().sub(Math.log(initialValue)+(riskFreeRate - 0.5 * volatility * volatility) * maturity2).mult(lambda2));

		RandomVariable exerciseCriteria = valueRelativeOption2InT2.sub(martingale[1]).sub(valueRelativeOption1InT1.sub(martingale[0]));

		return exerciseCriteria.choose(valueRelativeOption2InT2.sub(martingale[1]), valueRelativeOption1InT1.sub(martingale[0])).getAverage();
	}

	private void valueWithUpperBoundWithEstimatedCondExp(MonteCarloAssetModel model) throws Exception {
		final RandomVariable stockInT2 = model.getAssetValue(maturity2, 0);		// S(T2)
		final RandomVariable stockInT1 = model.getAssetValue(maturity1, 0);		// S(T1)

		final RandomVariable valueOption2InT2 = stockInT2.sub(strike2).floor(0.0);	// max(S(T2)-K,0)
		final RandomVariable valueOption1InT1 = stockInT1.sub(strike1).floor(0.0);	// max(S(T1)-K,0)

		// Convert all time T-values to numeraire relative values by dividing by N(T)
		final RandomVariable valueRelativeOption2InT2 = valueOption2InT2.div(model.getNumeraire(maturity2));
		final RandomVariable valueRelativeOption1InT1 = valueOption1InT1.div(model.getNumeraire(maturity1));


		RandomVariable basisFunctionUnderlying = stockInT1.sub(strike1).floor(0.0);
		List<RandomVariable> basisFunctions = useBinning ? getRegressionBasisFunctionsBinning(basisFunctionUnderlying, numberOfBasisFunctions) : getRegressionBasisFunctions(basisFunctionUnderlying, numberOfBasisFunctions);

		final ConditionalExpectationEstimator condExpEstimator = new MonteCarloConditionalExpectationRegression(basisFunctions.toArray(new RandomVariable[0]));

		// Calculate conditional expectation on numeraire relative quantity.
		final RandomVariable valueRelativeOption2InT1 = valueRelativeOption2InT2.getConditionalExpectation(condExpEstimator);

		RandomVariable[] martingaleInclrementForDualMethod = new RandomVariable[3];
		martingaleInclrementForDualMethod[2] = model.getRandomVariableForConstant(0.0);
		martingaleInclrementForDualMethod[1] = valueRelativeOption2InT2.sub(valueRelativeOption2InT1);
		martingaleInclrementForDualMethod[0] = valueRelativeOption1InT1.floor(valueRelativeOption2InT1).sub(valueRelativeOption1InT1.floor(valueRelativeOption2InT1).average());

		RandomVariable[] martingale = new RandomVariable[2];
		martingale[0] = martingaleInclrementForDualMethod[0];
		martingale[1] = martingale[0].add(martingaleInclrementForDualMethod[1]);

		RandomVariable exerciseCriteria = valueRelativeOption2InT2.sub(martingale[1]).sub(valueRelativeOption1InT1.sub(martingale[0]));

		RandomVariable value = exerciseCriteria.choose(valueRelativeOption2InT2.sub(martingale[1]), valueRelativeOption1InT1.sub(martingale[0]));
		System.out.println(String.format("Bermudan value dual method using martingale with estimated cond. exp: %10.8f ± %10.8f", value.getAverage(), value.getStandardError()));
	}

	private ArrayList<RandomVariable> getRegressionBasisFunctions(RandomVariable underlying, int numberOfBasisFunctions) {

		final int orderOfRegressionPolynomial = numberOfBasisFunctions-1;		// Choose maybe something like 4 (numberOfBasisFunctions = 5)

		underlying = new RandomVariableFromDoubleArray(0.0, underlying.getRealizations());

		// Create basis functions - here: 1, S, S^2, S^3, S^4
		final ArrayList<RandomVariable> basisFunctions = new ArrayList<>();
		for(int powerOfRegressionMonomial=0; powerOfRegressionMonomial<=orderOfRegressionPolynomial; powerOfRegressionMonomial++) {
			basisFunctions.add(underlying.pow(powerOfRegressionMonomial));
		}

		return basisFunctions;
	}

	private ArrayList<RandomVariable> getRegressionBasisFunctionsBinning(RandomVariable underlying, int numberOfBasisFunctions) {

		final int numberOfBins = numberOfBasisFunctions;		// Choose maybe something like 20 (numberOfBasisFunctions = 20)

		underlying = new RandomVariableFromDoubleArray(0.0, underlying.getRealizations());
		final double[] values = underlying.getRealizations();
		Arrays.sort(values);

		final ArrayList<RandomVariable> basisFunctions = new ArrayList<>();
		for(int i = 0; i<numberOfBins; i++) {
			final double binLeft = values[(int)((double)i/(double)numberOfBins*values.length)];
			final RandomVariable basisFunction = underlying.sub(binLeft).choose(new RandomVariableFromDoubleArray(1.0), new RandomVariableFromDoubleArray(0.0));
			basisFunctions.add(basisFunction);
		}

		return basisFunctions;
	}
}
