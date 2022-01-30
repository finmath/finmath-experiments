package net.finmath.experiments.dice.submodels;

import java.util.function.BiFunction;

/**
 * The function that maps (relative) abatement coefficient to (relative) cost.
 * 
 * Function of (time, abatement)
 * TODO: Sigma factor is missing here (moved to the outside)
 * 
 * @author Christian Fries
 */
public class AbatementCostFunction implements BiFunction<Double, Double, Double> {

    double backstopPriceInitial = 550.0/1000.0;		// Initial cost for backstop
    double backstopRate = 0.025;					// Decay of backstop cost.
    double theta2 = 2.6;							// Exponent abatement in cost function (GAMS expcost2)

	@Override
	public Double apply(Double time, Double abatement) {
		double abatementCost = backstopPriceInitial * Math.pow(1-backstopRate, time) * Math.pow(abatement , theta2)/theta2;
		// alternatively, express the backstopRate as exponential decay
//		double abatementCost = backstopPriceInitial * Math.exp(-backstopRate * time) * Math.pow(abatement , theta2)/theta2;

		return abatementCost;
	}
}
