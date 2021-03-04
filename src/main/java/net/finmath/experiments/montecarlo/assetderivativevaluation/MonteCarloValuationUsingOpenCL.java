/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 04.03.2021
 */
package net.finmath.experiments.montecarlo.assetderivativevaluation;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.opencl.montecarlo.RandomVariableOpenCLFactory;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretizationFromArray;

public class MonteCarloValuationUsingOpenCL {

	public static void main(String[] args) {
		
		testWithRandomVariableFactory(new RandomVariableFromArrayFactory(false));
		testWithRandomVariableFactory(new RandomVariableOpenCLFactory());
		
	}

	private static void testWithRandomVariableFactory(RandomVariableFactory randomVariableFactory) {
		
		System.out.println("Using " + randomVariableFactory.getClass().getSimpleName());

		/*
		 * First run
		 */

		var run1Start = System.currentTimeMillis();
		
		// Create Brownian motion
		int numberOfPaths = 1000000;
		var td = new TimeDiscretizationFromArray(0.0, 200, 0.01);
		var brownianMotion = new BrownianMotionFromMersenneRandomNumbers(td, 1, numberOfPaths, 3231, randomVariableFactory);

		var value1 = performValuation(randomVariableFactory, brownianMotion);

		var run1End = System.currentTimeMillis();
		var run1Time = (run1End-run1Start)/1000.0;
		
		System.out.printf("\t value = %6.3f \t computation time = %6.3f seconds.\n", value1, run1Time);

		/*
		 * Second run - reusing brownianMotion
		 */
		var run2Start = System.currentTimeMillis();

		var value2 = performValuation(randomVariableFactory, brownianMotion);

		var run2End = System.currentTimeMillis();
		var run2Time = (run2End-run2Start)/1000.0;

		System.out.printf("\t value = %6.3f \t computation time = %6.3f seconds.\n", value2, run2Time);
	}

	private static double performValuation(RandomVariableFactory randomVariableFactory, BrownianMotion brownianMotion) {
		// Create a model
		double modelInitialValue = 100.0;
		double modelRiskFreeRate = 0.05;
		double modelVolatility = 0.20;
		var model = new BlackScholesModel(modelInitialValue, modelRiskFreeRate, modelVolatility, randomVariableFactory);

		// Create a corresponding MC process
		var process = new EulerSchemeFromProcessModel(model, brownianMotion);

		// Using the process (Euler scheme), create an MC simulation of a Black-Scholes model
		var simulation = new MonteCarloAssetModel(process);

		double maturity = 2.0;
		double strike = 106.0;

		var europeanOption = new EuropeanOption(maturity, strike);

		double value = 0.0;
		try {
			RandomVariable valueOfEuropeanOption = europeanOption.getValue(0.0, simulation).average();
			value = valueOfEuropeanOption.doubleValue();
		} catch (CalculationException e) {
			System.out.println("Calculation failed with exception: " + e.getCause().getMessage());
		}
		
		return value;
	}
}
