package net.finmath.experiments.dice.submodels;

import java.util.function.BiFunction;

/**
 * The function that maps economicOutput to emission at a given time
 * 
 * @author Christian Fries
 */
public class EmissionFunction implements BiFunction<Double, Double, Double> {

    private final EmissionIntensityFunction emissionIntensityFunction;
    private final double externalEmissionsInitial;
    private final double externalEmissionsDecay;
    
     public EmissionFunction(EmissionIntensityFunction emissionIntensityFunction, double externalEmissionsInitial, double externalEmissionsDecay) {
		super();
		this.emissionIntensityFunction = emissionIntensityFunction;
		this.externalEmissionsInitial = externalEmissionsInitial;
		this.externalEmissionsDecay = externalEmissionsDecay;
	}

     public EmissionFunction(EmissionIntensityFunction emissionIntensityFunction) {
 		// Parameters from original model
    	 this(emissionIntensityFunction, 2.6, 0.115);
     }

     @Override
	public Double apply(Double time, Double economicOutput) {
		double emissionPerEconomicOutput = emissionIntensityFunction.apply(time);
		// The parameter externalEmissionsDecay is formulated for a 5 year period
		double externalEmissions = externalEmissionsInitial * Math.pow(1-externalEmissionsDecay, time/5.0);

		return emissionPerEconomicOutput * economicOutput + externalEmissions;
	}
}
