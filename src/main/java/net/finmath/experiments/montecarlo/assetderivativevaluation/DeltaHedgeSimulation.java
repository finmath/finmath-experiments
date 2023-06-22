/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 19.01.2014
 */

package net.finmath.experiments.montecarlo.assetderivativevaluation;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionLazyInit;
import net.finmath.montecarlo.assetderivativevaluation.AssetModelMonteCarloSimulationModel;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloBlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.AbstractAssetMonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.products.AssetMonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.products.BlackScholesDeltaHedgedPortfolio;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * @author Christian Fries
 *
 */
public class DeltaHedgeSimulation {

	/**
	 *
	 */
	public DeltaHedgeSimulation() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 * @throws CalculationException
	 */
	public static void main(String[] args) throws CalculationException {
		testHedge();

	}

	private static void testHedge() throws CalculationException {
		System.out.println("\n\nT E S T   o f   C a l i b r a t i o n   a n d   H e d g e \n");

		// Test parameters
		final double initialValue	= 100;
		final double riskFreeRate	= 0.05;
		final double volatility		= 0.32;
		final int numberOfPaths		= 1000;
		final int numberOfTimeSteps	= 100;
		final TimeDiscretizationFromArray simulationTimeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps, 2.0 / numberOfTimeSteps);
		final BrownianMotion brownianMotion = new BrownianMotionLazyInit(simulationTimeDiscretization, 1, numberOfPaths, 3141);
		final AssetModelMonteCarloSimulationModel model = new MonteCarloBlackScholesModel(simulationTimeDiscretization, numberOfPaths, initialValue, riskFreeRate, volatility);

		final TimeDiscretizationFromArray simulationTimeDiscretization2 = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps/100, 2.0 / numberOfTimeSteps * 100);
		final AssetModelMonteCarloSimulationModel model2 = new MonteCarloBlackScholesModel(simulationTimeDiscretization2, numberOfPaths, initialValue, riskFreeRate, volatility);

		// Calibration products
		final double maturity = 2.0;

		final int[]		testProductGroup			= { 6,		5,		2,		7,		1,		4,		3,		8		};
		final double[]	testProductStrikes			= { 100,	102,	105,	108,	110,	112,	115,	118		};
		final double[]	testProductTargetValues	= { 22.215,	21.329,	20.060,	18.859,	18.094,	17.358,	16.305,	15.312	};

		for(int testProductIndex = 0; testProductIndex<testProductStrikes.length; testProductIndex++) {
			final int		group	=	testProductGroup[testProductIndex];
			final double	strike	= testProductStrikes[testProductIndex];
			final double	targetValue = testProductTargetValues[testProductIndex];
			System.out.println("\nGroup = " + group + "\tStrike = " + strike + "\n------------------------------------------------" + "\n");

			// Create hedge product
			final AbstractAssetMonteCarloProduct product = new EuropeanOption(maturity, strike);

			// Hedge portfolio
			final AssetMonteCarloProduct hedgePortfolio = new BlackScholesDeltaHedgedPortfolio(maturity, strike, riskFreeRate, volatility);
			//			AbstractAssetMonteCarloProduct hedgePortfolio = new BlackScholesExchangeOptionDeltaHedgedPortfolio(maturity, strike, riskFreeRate, volatility);
			//			AbstractAssetMonteCarloProduct hedgePortfolio = new FiniteDifferenceDeltaHedgedPortfolio(product, model2);

			// Print stuff about model
			System.out.println("Model............................: " + model.getClass().getSimpleName());
			System.out.println("Value of calibration product.....: " + product.getValue(model));

			// Check hedge
			final RandomVariable hedgePortfolioValues	= hedgePortfolio.getValue(maturity, model);
			final RandomVariable derivativeValues		= product.getValue(maturity, model);
			final RandomVariable underlyingValues		= model.getAssetValue(maturity, 0);

			final RandomVariable hedgePortfolioErrorValues	= hedgePortfolioValues.sub(derivativeValues);
			System.out.println("Hedge error (rms)................: " + Math.sqrt(hedgePortfolioErrorValues.squared().getAverage()));
			System.out.println("");

			// Output
			final boolean printSamples = false;
			if(printSamples) {
				for(int pathIndex=0; pathIndex<hedgePortfolioValues.size(); pathIndex++) {
					System.out.print(underlyingValues.get(pathIndex) + "\t");
					System.out.print(hedgePortfolioValues.get(pathIndex) + "\t");
				}
			}
		}
	}
}

