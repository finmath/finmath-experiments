/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 05.12.2013
 */

package net.finmath.experiments.montecarlo.assetderivativevaluation;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.assetderivativevaluation.AssetModelMonteCarloSimulationModel;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloBlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.AbstractAssetMonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * A simple demo on how to value a financial product with a given model.
 *
 * This relates to the "separation of model and product". The only assumptions
 * made by the valuation code in the product is that the model implements
 * the interface {@code AssetModelMonteCarloSimulationModel}.
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

		final AssetModelMonteCarloSimulationModel model = new MonteCarloBlackScholesModel(new TimeDiscretizationFromArray(0, 10, 0.5), 10000, 100, 0.05, 0.20);

		final AbstractAssetMonteCarloProduct product = new EuropeanOption(2.0, 110);

		final double value = product.getValue(model);

		System.out.println("The value is " + value);
	}

}
