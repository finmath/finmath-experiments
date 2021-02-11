/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 09.02.2004
 */
package net.finmath.experiments.montecarlo.interestrates;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.interestrate.TermStructureMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.products.AbstractLIBORMonteCarloProduct;
import net.finmath.stochastic.RandomVariable;

/**
 * Implements the valuation of a forward rate using a given <code>TermStructureMonteCarloSimulationModel</code>.
 *
 * @author Christian Fries
 * @version 1.0
 */
public class ForwardRate extends AbstractLIBORMonteCarloProduct {

	private final double	fixingTime;
	private final double	periodStartTime;
	private final double	periodEndTime;
	private final double	paymentTime;
	private final double	daycountFraction;

	/**
	 * Create a payment of a forward rate.
	 *
	 * The product pays \( L(T_{1},T_{2};T^{f}) \cdot daycountFraction \) at \( T^{p} \)
	 * where L denotes the forward rate.
	 *
	 * @param fixingTime The fixing date \( T^{f} \) given as double.
	 * @param periodStartTime The period start time \( T_{1} \) of the forward rate period.
	 * @param periodEndTime The period end time \( T_{2} \) of the forward rate period.
	 * @param paymentTime The payment date \( T^{p} \) given as double.
	 * @param daycountFraction The daycount fraction used in the payout function.
	 */
	public ForwardRate(final double fixingTime, final double periodStartTime, final double periodEndTime, final double paymentTime, final double daycountFraction) {
		super();
		this.fixingTime = fixingTime;
		this.periodStartTime = periodStartTime;
		this.periodEndTime = periodEndTime;
		this.paymentTime = paymentTime;
		this.daycountFraction = daycountFraction;
	}

	/**
	 * Create a payment of a forward rate.
	 *
	 * The product pays \( L(T_{1},T_{2};T^{f}) \cdot daycountFraction \) at \( T^{p} \).
	 * where L denotes the forward rate.
	 *
	 * This constructor assumes that \( T^{f} = T_{1} \) and \( T^{p} = T_{2} \).
	 * 
	 * @param fixingTime The fixing date \( T^{f} \) given as double.
	 * @param periodLength The period length \( T_{2}-T_{1} \) of the forward rate period.
	 * @param daycountFraction The daycount fraction used in the payout function.
	 */
	public ForwardRate(final double fixingTime, final double periodLength, final double daycountFraction) {
		super();
		this.fixingTime = fixingTime;
		this.periodStartTime = fixingTime;
		this.periodEndTime = fixingTime+periodLength;
		this.paymentTime = fixingTime+periodLength;
		this.daycountFraction = daycountFraction;
	}

	public ForwardRate(String currency, final double fixingTime, final double periodStartTime, final double periodEndTime, final double paymentTime, final double daycountFraction) {
		super(currency);
		this.fixingTime = fixingTime;
		this.periodStartTime = periodStartTime;
		this.periodEndTime = periodEndTime;
		this.paymentTime = paymentTime;
		this.daycountFraction = daycountFraction;
	}

	/**
	 * This method returns the value random variable of the product within the specified model, evaluated at a given evalutationTime.
	 * Note: For a lattice this is often the value conditional to evalutationTime, for a Monte-Carlo simulation this is the (sum of) value discounted to evaluation time.
	 * Cashflows prior evaluationTime are not considered.
	 *
	 * @param evaluationTime The time on which this products value should be observed.
	 * @param model The model used to price the product.
	 * @return The random variable representing the value of the product discounted to evaluation time
	 * @throws net.finmath.exception.CalculationException Thrown if the valuation fails, specific cause may be available via the <code>cause()</code> method.
	 */
	@Override
	public RandomVariable getValue(final double evaluationTime, final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		// Get random variables
		final RandomVariable	forwardRate				= model.getForwardRate(fixingTime, periodStartTime, periodEndTime);
		final RandomVariable	numeraire				= model.getNumeraire(paymentTime);
		final RandomVariable	monteCarloProbabilities	= model.getMonteCarloWeights(periodStartTime);

		RandomVariable values = forwardRate.mult(daycountFraction);

		values = values.div(numeraire).mult(monteCarloProbabilities);

		final RandomVariable	numeraireAtValuationTime				= model.getNumeraire(evaluationTime);
		final RandomVariable	monteCarloProbabilitiesAtValuationTime	= model.getMonteCarloWeights(evaluationTime);
		values = values.mult(numeraireAtValuationTime).div(monteCarloProbabilitiesAtValuationTime);

		return values;
	}
}
