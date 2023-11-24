package net.finmath.experiments.dice;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import net.finmath.climate.models.CarbonConcentration;
import net.finmath.climate.models.ClimateModel;
import net.finmath.climate.models.Temperature;
import net.finmath.climate.models.dice.DICEModel;
import net.finmath.plots.Plots;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/*
 * Experiment related to the DICE model.
 * 
 * Note: The code makes some small simplification: it uses a constant savings rate and a constant external forcings.
 * It may still be useful for illustration.
 */
public class DICEModelExperiment {

	private static final double timeStep = 1.0;
	private static final double timeHorizon = 500.0;

	public static void main(String[] args) {
		
		plotAbatementScenarios();
//		plotSocialCostOfCarbon();
	}
	
	public static void plotAbatementScenarios() {

		System.out.println("Time of max abatement \t   Temperature \t   Emission \t   Value");
		System.out.println("_".repeat(79));

		final double discountRate = 0.03;
		System.out.println("discountRate = " + discountRate);

		final double abatementInitial = 0.03;
		final double abatementMax = 1.00;
		List<Double> abatementMaxTimeScenarios = List.of(30.0, 50.0, 100.0);
		for(double abatementMaxTime : abatementMaxTimeScenarios) {
			final UnaryOperator<Double> abatementFunction = time -> Math.min(abatementInitial + (abatementMax-abatementInitial)/abatementMaxTime * time, abatementMax);

			final int numberOfTimes = (int)Math.round(timeHorizon / timeStep);
			final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimes, timeStep);
			final ClimateModel climateModel = new DICEModel(timeDiscretization, abatementFunction, t -> 0.26, discountRate);

			System.out.println("Abatement 100% at time + " + abatementMaxTime);
			System.out.println(String.format("\t %8.4f \t %8.4f \t %8.4f \t %8.4f", abatementMaxTime, climateModel.getTemperature()[numberOfTimes-1].getExpectedTemperatureOfAtmosphere(), climateModel.getEmission()[numberOfTimes-1].getAverage(), climateModel.getValue().getAverage()));


			Plots
			.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getTemperature()).mapToDouble(Temperature::getExpectedTemperatureOfAtmosphere).toArray(), 0, 300, 3)
			.setTitle("Temperature (scenario =" + abatementMaxTime + ", r = " + discountRate + ")").setXAxisLabel("time (years)").setYAxisLabel("Temperature [°C]").show();

			Plots
			.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getCarbonConcentration()).mapToDouble(CarbonConcentration::getExpectedCarbonConcentrationInAtmosphere).toArray(), 0, 300, 3)
			.setTitle("Carbon Concentration (scenario =" + abatementMaxTime + ", r = " + discountRate + ")").setXAxisLabel("time (years)").setYAxisLabel("Carbon concentration [GtC]").show();

			Plots
			.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getEmission()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
			.setTitle("Emission (scenario =" + abatementMaxTime + ", r = " + discountRate + ")").setXAxisLabel("time (years)").setYAxisLabel("Emission [GtCO2/yr]").show();

			Plots
			.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getGDP()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
			.setTitle("Output (scenario =" + abatementMaxTime + ", r = " + discountRate + ")").setXAxisLabel("time (years)").setYAxisLabel(" Output [Tr$2005]").show();

			Plots
			.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getAbatement()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
			.setTitle("Abatement (scenario =" + abatementMaxTime + ", r = " + discountRate + ")").setXAxisLabel("time (years)").setYAxisLabel("Abatement \u03bc").show();
		}

		System.out.println("_".repeat(79));
	}

	public static void plotSocialCostOfCarbon() {

		System.out.println("\t Discount Rate \t  SCC");
		System.out.println("_".repeat(79));

		final double abatementInitial = 0.03;
		final double abatementMax = 1.00;
		final double abatementMaxTime = 30.0;

		List<Double> discountRates = new ArrayList<>();
		List<Double> socialCostOfCarbons = new ArrayList<>();
		for(double discountRate = 0.005; discountRate <= 0.05; discountRate += 0.001) {

			final UnaryOperator<Double> abatementFunction = time -> Math.min(abatementInitial + (abatementMax-abatementInitial)/abatementMaxTime * time, abatementMax);

			final int numberOfTimes = (int)Math.round(timeHorizon / timeStep);
			final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimes, timeStep);

			final ClimateModel climateModel = new DICEModel(timeDiscretization, abatementFunction, t -> 0.26, discountRate);
			double value = climateModel.getValue().doubleValue();

			double valueDC = (new DICEModel(timeDiscretization, abatementFunction, t -> 0.26, discountRate,
					Map.of("isTimeIndexToShift", (Predicate<Integer>)i -> i==0, "initialConsumptionShift", 0.01))).getValue().doubleValue();

			double valueDE = (new DICEModel(timeDiscretization, abatementFunction, t -> 0.26, discountRate,
					Map.of("isTimeIndexToShift", (Predicate<Integer>)i -> i==0, "initialEmissionShift", 0.01))).getValue().doubleValue();

			// scc = dC/dE = dV/dE / dV/dC
			double scc = -(valueDE-value) / (valueDC-value) * 1000;
			
			discountRates.add(discountRate);
			socialCostOfCarbons.add(scc);
			System.out.println(String.format("\t %8.4f \t %8.4f", discountRate, scc));
		}
		Plots
		.createScatter(discountRates, socialCostOfCarbons, 0, 300, 3)
		.setTitle("Social Cost of Carbon (scenario =" + abatementMaxTime + ")")
		.setXAxisLabel("rate (r)").setXAxisNumberFormat(new DecimalFormat("0.0%")).setYAxisLabel("SCC").show();

		System.out.println("_".repeat(79));
	}
}
