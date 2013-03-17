
import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.assetderivativevaluation.products.AbstractAssetMonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.AssetModelMonteCarloSimulationInterface;
import net.finmath.stochastic.ImmutableRandomVariableInterface;
import net.finmath.stochastic.RandomVariableInterface;

/**
 * Implements pricing of a European stock option.
 * 
 * @author Christian Fries
 * @version 1.2
 */
public class UserDefiniedAssetDerivative extends AbstractAssetMonteCarloProduct {

	double maturity;
	double strike;
	
	/**
	 * @param strike
	 * @param maturity
	 */
	public UserDefiniedAssetDerivative(double maturity, double strike) {
		super();
		this.maturity	= maturity;
		this.strike		= strike;
	}

	/**
	 * This method returns the value random variable of the product within the specified model, evaluated at a given evalutationTime.
	 * Note: For a lattice this is often the value conditional to evalutationTime, for a Monte-Carlo simulation this is the (sum of) value discounted to evaluation time.
	 * Cashflows prior evaluationTime are not considered.
	 * 
	 * @param evaluationTime The time on which this products value should be observed.
	 * @param model The model used to value the product.
	 * @return The random variable representing the value of the product discounted to evaluation time
	 */
	@Override
	public RandomVariableInterface getValues(double evaluationTime, AssetModelMonteCarloSimulationInterface model) throws CalculationException {
		// Get underlying and numeraire
		RandomVariableInterface underlyingAtMaturity	= model.getAssetValue(maturity,0);
		
		// The payoff
		RandomVariableInterface values = underlyingAtMaturity.sub(strike).floor(0.0);
		
		// Discounting...
		ImmutableRandomVariableInterface	numeraireAtMaturity		= model.getNumeraire(maturity);
		values.div(numeraireAtMaturity);

		// ...to evaluation time.
		ImmutableRandomVariableInterface	numeraireAtZero				= model.getNumeraire(evaluationTime);
		values.mult(numeraireAtZero);

		return values;
	}
}
