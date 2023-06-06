package net.finmath.experiments.liboverview;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.MonteCarloProduct;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.automaticdifferentiation.RandomVariableDifferentiable;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory;
import net.finmath.montecarlo.model.ProcessModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.plots.PlotProcess2D;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class MonteCarloBlackScholesEuropeanOptionDeltaAAD {

	public static void main(String[] args) throws CalculationException {
		testValuationUsingProduct();
	}

	public static void testValuationUsingProduct() throws CalculationException {

		System.out.println("\n");
		System.out.println("Valuation (using product):");
		System.out.println("_".repeat(79));

		RandomVariableFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory();

		// Model X - BlackScholesModel Parameters
		double initialValue = 100.0;		// X(0)
		double riskFreeRate = 0.05;			// r
		double sigma = 0.20;				// σ
		ProcessModel processModel = new BlackScholesModel(initialValue, riskFreeRate, sigma, randomVariableFactory);

		// TimeDiscretization
		double initialTime = 0.0;
		int numberOfTimeSteps = 50;
		double dt = 0.5;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, dt);

		// BrownianMotion
		int numberOfFactors = 1;
		int numberOfPaths = 100000;
		int randomNumberSeed = 3216;
		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, randomNumberSeed);

		// Time discrete process (Euler Scheme)
		MonteCarloProcess process = new EulerSchemeFromProcessModel(processModel, brownianMotion);

		// Wrapped to AssetModel for valuation
		MonteCarloAssetModel model = new MonteCarloAssetModel(process);

		/*
		 * Valuation of paying max(S(T)-K,0) in T
		 */
		double maturity = 10.0;	// T
		double strike = 160.0;	// K
		MonteCarloProduct product = new EuropeanOption(maturity, strike);

		RandomVariable value = product.getValue(initialTime, model);

		RandomVariable delta = ((RandomVariableDifferentiable)value).getGradient().get(((RandomVariableDifferentiable)((BlackScholesModel)processModel).getInitialValue(process)[0]).getID());

		double valueAnalytic = AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, sigma, maturity, strike);

		double deltaAnalytic = AnalyticFormulas.blackScholesOptionDelta(initialValue, riskFreeRate, sigma, maturity, strike);

		System.out.println("value...............: " + value.average().doubleValue());
		System.out.println("value analytic......: " + valueAnalytic);
		System.out.println("delta...............: " + delta.average().doubleValue());
		System.out.println("delta analytic......: " + deltaAnalytic);


		// Create a function, plotting paths t -> S(t)
		DoubleToRandomVariableFunction paths = time -> model.getAssetValue(timeDiscretization.getTimeIndex(time), 0 /* assetIndex: 0 is S(t) */);

		/*
		 * Plot
		 */

		// Plot 100 of paths against the given time discretization.
		var plot = new PlotProcess2D(timeDiscretization, paths, 1000);
		plot.setTitle(processModel.getClass().getSimpleName() + " paths").setXAxisLabel("time").setYAxisLabel("value");
		plot.show();

	}
}
