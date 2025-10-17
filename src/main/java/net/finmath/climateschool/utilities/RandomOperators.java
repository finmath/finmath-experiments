package net.finmath.climateschool.utilities;

import net.finmath.stochastic.RandomOperator;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;

/**
 * Operators on <code>RandomVariable</code>.
 *
 * @author Christian Fries
 * @author Lennart Quante
 */
public class RandomOperators {

	/**
	 * X &mapsto; E(X)
	 *
	 * @return The operator X &mapsto; E(X)
	 */
	public static RandomOperator expectation() {
		return x -> x.average();
	}

	/**
	 * X &mapsto; ES_\alpha(X) where ES_\alpha(X) = E(X \cdot 1(x \leq VaR_\alpha(X)) / \alpha
	 *
	 * This is the same as <code>leftTailExpectedShortFall</code>.
	 *
	 * @param percentageLevel the percentage \alpha level of the expected short fall.
	 * @return The operator X &mapsto; ES_\alpha(X)
	 */
	public static RandomOperator expectedShortFall(Double percentageLevel) {
		return x -> expectedShortFall(x, percentageLevel);
	}

	/**
	 * ES_\alpha(X) = E(X \cdot 1(x \leq VaR_\alpha(X)) / \alpha
	 *
	 * This is the same as <code>leftTailExpectedShortFall</code>.
	 *
	 * @param percentageLevel the percentage \alpha level of the expected short fall.
	 * @return ES_\alpha(X)
	 */
	public static RandomVariable expectedShortFall(RandomVariable x, Double percentageLevel) {
		return leftTailExpectedShortFall(x, percentageLevel);
	}

	/**
	 * X &mapsto; E(X) - alpha ES_\alpha(X)
	 *
	 * @param percentageLevel the percentage \alpha level of the expected short fall.
	 * @return The E(X) - alpha ES_\alpha(X)
	 */
	public static RandomOperator expectedShortFallComplement(Double percentageLevel) {
		return x -> x.average().sub(RandomOperators.expectedShortFall(percentageLevel).apply(x).mult(percentageLevel));
	}

	/**
	 * X &mapsto; ES_\alpha(X)
	 *
	 * @param percentageLevel the percentage \alpha level of the expected short fall.
	 * @return The operator X &mapsto; ES_\alpha(X)
	 */
	public static RandomOperator rightTailExpectedShortFall(Double percentageLevel) {
		return x -> rightTailExpectedShortFall(x, percentageLevel);
	}

	/**
	 * X &mapsto; ES_\alpha(X)
	 *
	 * @param percentageLevel the percentage \alpha level of the expected short fall.
	 * @return The operator X &mapsto; ES_\alpha(X)
	 */
	public static RandomOperator leftTailExpectedShortFall(Double percentageLevel) {
		return x -> leftTailExpectedShortFall(x, percentageLevel);
	}


	/**
	 * X &mapsto; VaR_\alpha(X)
	 *
	 * @param percentageLevel the percentage \alpha level of the value at risk.
	 * @return The operator X &mapsto; VaR_\alpha(X)
	 */
	public static RandomOperator valueAtRisk(Double percentageLevel) {
		return x -> valueAtRisk(x,percentageLevel);
	}

	/**
	 * X &mapsto; VaR_\alpha(X)
	 *
	 * @param percentageLevel the percentage \alpha level of the value at risk.
	 * @return The VaR_\alpha(X)
	 */
	public static RandomVariable valueAtRisk(RandomVariable x, Double percentageLevel) {

		final double valueAtRisk = x.getQuantile(percentageLevel);
		return Scalar.of(valueAtRisk);
	}


	/**
	 * ES_\alpha(X)
	 *
	 * @param percentageLevel the percentage \alpha level of the expected short fall.
	 * @return ES_\alpha(X)
	 */
	public static RandomVariable rightTailExpectedShortFall(RandomVariable x, Double percentageLevel) {
		if(x.isDeterministic() || x.getVariance() == 0) {
			return x;
		}

		final double valueAtRisk = x.getQuantile(percentageLevel);
		// 1(x >= VaR)
		final RandomVariable indicator = x.sub(valueAtRisk).choose(Scalar.of(1.0), Scalar.of(0.0));
		final RandomVariable averageBiggerThanVar = x.mult(indicator).average().div(1-percentageLevel);

		return averageBiggerThanVar;
	}

	/**
	 * ES_\alpha(X) for a value RandomVariable where lower values are worse outcomes, i.e. we have to invert the percentage level and average all values below the percentile
	 *
	 * @param percentageLevel the percentage \alpha level of the expected short fall.
	 * @return ES_\alpha(X)
	 */
	public static RandomVariable leftTailExpectedShortFall(RandomVariable x, Double percentageLevel) {
		if(x.isDeterministic() || x.getVariance() == 0) {
			return x;
		}
		if(percentageLevel == 1) {
			return x.average(); // just return expectation
		}

		final double valueAtRisk = x.getQuantile(percentageLevel);
		// 1(x <= VaR)
		final RandomVariable indicatorSmallerThanVar = x.sub(valueAtRisk).choose(Scalar.of(1.0), Scalar.of(0.0)).bus(1.0);
		final RandomVariable averageSmallerThanVar = x.mult(indicatorSmallerThanVar).average().div(percentageLevel);

		return averageSmallerThanVar;
	}
}
