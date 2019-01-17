/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 21.01.2004
 */
package net.finmath.experiments.montecarlo.assetderivativevaluation.products;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.assetderivativevaluation.AssetModelMonteCarloSimulationInterface;
import net.finmath.montecarlo.assetderivativevaluation.products.AbstractAssetMonteCarloProduct;
import net.finmath.stochastic.RandomVariableAccumulatorInterface;
import net.finmath.stochastic.RandomVariableInterface;

/**
 * Implements valuation of a European stock option.
 * The code is equivalent to the code in <code>net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption</code>.
 *
 * @author Christian Fries
 * @version 1.0
 */
public class EuropeanOption2 extends AbstractAssetMonteCarloProduct {

	private double maturity;
	private double strike;

	/**
	 * Construct a product representing an European option on an asset S (where S the asset with index 0 from the model - single asset case).
	 *
	 * @param strike The strike K in the option payoff max(S(T)-K,0).
	 * @param maturity The maturity T in the option payoff max(S(T)-K,0)
	 */
	public EuropeanOption2(double maturity, double strike) {
		super();
		this.maturity = maturity;
		this.strike = strike;
	}

	/**
	 * Calculates the value of the option under a given model.
	 *
	 * @param model A reference to a model
	 * @return the value
	 * @throws CalculationException
	 */
	public double getValue(AssetModelMonteCarloSimulationInterface model) throws CalculationException
	{
		// Get underlying and numeraire
		RandomVariableInterface underlyingAtMaturity	= model.getAssetValue(maturity,0);
		RandomVariableInterface numeraireAtMaturity	= model.getNumeraire(maturity);
		RandomVariableInterface monteCarloWeights		= model.getMonteCarloWeights(maturity);
		RandomVariableInterface numeraireAtToday		= model.getNumeraire(0);

		/*
		 *  The following way of calculating the expected value (average) is discouraged since it makes too strong
		 *  assumptions on the internals of the <code>RandomVariableAccumulatorInterface</code>. Instead you should use
		 *  the mutators sub, div, mult and the getter getAverage. This code is provided for illustrative purposes.
		 */
		double average = 0.0;
		for(int path=0; path<model.getNumberOfPaths(); path++)
		{
			// Expectation of N(0) * ( max(S(T)-K,0) / N(T) )
			if(underlyingAtMaturity.get(path) > strike)
			{
				average += (underlyingAtMaturity.get(path) - strike) / numeraireAtMaturity.get(path) * monteCarloWeights.get(path)
						* numeraireAtToday.get(path);
			}
		}

		return average;
	}

	@Override
	public RandomVariableAccumulatorInterface getValue(double evaluationTime, AssetModelMonteCarloSimulationInterface model) {
		throw new RuntimeException("Method not supported.");
	}
}
