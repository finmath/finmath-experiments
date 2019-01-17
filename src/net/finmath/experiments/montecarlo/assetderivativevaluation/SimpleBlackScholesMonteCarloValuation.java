/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 05.12.2013
 */

package net.finmath.experiments.montecarlo.assetderivativevaluation;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.assetderivativevaluation.AssetModelMonteCarloSimulationInterface;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloBlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.AbstractAssetMonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.time.TimeDiscretization;

/**
 * A simple demo on how to value a financial product with a given model.
 *
 * This relates to the "separation of model and product". The only assumptions
 * made by the valuation code in the product is that the model implements
 * the interface {@code AssetModelMonteCarloSimulationInterface}.
 *
 * @author Christian Fries
 */
public class SimpleBlackScholesMonteCarloValuation {

	/**
	 * Demo program.
	 *
	 * @param args Arguments. Not used.
	 * @throws CalculationException
	 */
	public static void main(String[] args) throws CalculationException {

		AssetModelMonteCarloSimulationInterface model = new MonteCarloBlackScholesModel(new TimeDiscretization(0, 10, 0.5), 10000, 100, 0.05, 0.20);

		AbstractAssetMonteCarloProduct product = new EuropeanOption(2.0, 110);

		double value = product.getValue(model);

		System.out.println("The value is " + value);
	}

}
