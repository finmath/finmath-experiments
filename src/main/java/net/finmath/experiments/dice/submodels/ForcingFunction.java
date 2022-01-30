package net.finmath.experiments.dice.submodels;

import java.util.function.BiFunction;

/**
 * The function that maps CarbonConcentration and external forcing to forcing.
 * 
 * @author Christian Fries
 */
public class ForcingFunction implements BiFunction<CarbonConcentration, Double, Double> {

	private double carbonConcentrationBase = 580;
	private double forcingPerCarbonDoubling = 3.6813;

	@Override
	public Double apply(CarbonConcentration carbonConcentration, Double forcingExternal) {
		return forcingPerCarbonDoubling * Math.log(carbonConcentration.getCarbonConcentrationInAtmosphere() / carbonConcentrationBase ) / Math.log(2) + forcingExternal;
	}
}
