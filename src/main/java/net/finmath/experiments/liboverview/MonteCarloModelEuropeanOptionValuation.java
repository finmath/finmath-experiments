package net.finmath.experiments.liboverview;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.MonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BachelierModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.models.HestonModel;
import net.finmath.montecarlo.assetderivativevaluation.models.HestonModel.Scheme;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.model.ProcessModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.plots.PlotProcess2D;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class MonteCarloModelEuropeanOptionValuation {

	public static void main(String[] args) throws CalculationException {

		System.out.println("\n");
		System.out.println("Valuation (using product):");
		System.out.println("_".repeat(79));

		// Model X - BlackScholesModel Parameters
		double initialValue = 100.0;		// X(0)
		double riskFreeRate = 0.05;			// r
		double sigma = 0.20;				// σ

		ProcessModel blackScholesModel = new BlackScholesModel(initialValue, riskFreeRate, sigma);
		testValuationUsingProduct(blackScholesModel);

		ProcessModel bachelierModel = new BachelierModel(initialValue, riskFreeRate, sigma*100);
		testValuationUsingProduct(bachelierModel);

		final double theta = sigma*sigma;
		final double kappa = 0.1;
		final double xi = 0.1;
		final double rho = 0.1;
		ProcessModel hestonModel = new HestonModel(initialValue, riskFreeRate, sigma, theta, kappa, xi, rho, Scheme.FULL_TRUNCATION);
		testValuationUsingProduct(hestonModel);
	}

	public static void testValuationUsingProduct(ProcessModel processModel) throws CalculationException {

		// TimeDiscretization
		double initialTime = 0.0;
		int numberOfTimeSteps = 50;
		double dt = 0.5;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, dt);

		// BrownianMotion
		int numberOfFactors = 2;
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
		double value = product.getValue(model);

		System.out.println(String.format("%-20s value.........: %8.4f", processModel.getClass().getSimpleName(), value));


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
