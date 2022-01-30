package net.finmath.experiments.dice;

import java.util.function.DoubleUnaryOperator;

import net.finmath.experiments.dice.submodels.AbatementCostFunction;
import net.finmath.experiments.dice.submodels.CarbonConcentration;
import net.finmath.experiments.dice.submodels.DamageFromTemperature;
import net.finmath.experiments.dice.submodels.EmissionFunction;
import net.finmath.experiments.dice.submodels.EmissionIntensityFunction;
import net.finmath.experiments.dice.submodels.EvolutionOfCarbonConcentration;
import net.finmath.experiments.dice.submodels.EvolutionOfTemperature;
import net.finmath.experiments.dice.submodels.ForcingFunction;
import net.finmath.experiments.dice.submodels.Temperature;

/*
 * Simplified version of the dice model: The evolution of the economic factors
 * is missing here.
 */
public class DICEModelExperiment {

	private Temperature temperatureInitial = new Temperature(0.85, 0.0068);	
	private CarbonConcentration carbonConcentrationInitial = new CarbonConcentration(851, 460, 1740);	// Level of Carbon (GtC)

	int numberOfTimes = 300;

	
	/*
	 * Note: Calling default constructors for the sub-models will initialise the default parameters.
	 */

	/*
	 * Model that describes the damage on the GBP as a function of the temperature-above-normal
	 */

	DoubleUnaryOperator damageFunction = new DamageFromTemperature();
	double[] damage = new double[numberOfTimes];

	EvolutionOfTemperature evolutionOfTemperature = new EvolutionOfTemperature();
	Temperature[] temperature = new Temperature[numberOfTimes];

	EvolutionOfCarbonConcentration evolutionOfCarbonConcentration = new EvolutionOfCarbonConcentration();
	CarbonConcentration[] carbonConcentration = new CarbonConcentration[numberOfTimes];

	double forcingExternal = 0.5;
	ForcingFunction forcingFunction = new ForcingFunction();
	
	EmissionIntensityFunction emissionIntensityFunction = new EmissionIntensityFunction();
	EmissionFunction emissionFunction = new EmissionFunction(emissionIntensityFunction);

	/*
	 * Abatement 
	 */
	AbatementCostFunction abatementCostFunction = new AbatementCostFunction();
	

	/*
	 * GPB - currently constant, but the values from the original model
	 */
	double A0 = 5.115;		// Initial Total Factor of Productivity 
	double K0 = 223;		// Initial Capital
	double L0 = 7403;		// Initial Population
	double gamma = 0.3;		// Capital Elasticity in Production Function
	double gdpInitial = A0*Math.pow(K0,gamma)*Math.pow(L0/1000,1-gamma);


	/*
	 * Simulated values - stored for plotting
	 */
	double[] gdp = new double[numberOfTimes];
	double[] emission = new double[numberOfTimes];
	double[] abatement = new double[numberOfTimes];
	double[] welfare = new double[numberOfTimes];
	double[] value = new double[numberOfTimes];

	double growth = 0.01;
	static double abatementMax = 1.2;

	double r = 0.02;

	public static void main(String[] args) {

		System.out.println("1: x with 300/x = Year in which max abatement is reached (linear interpolation, then constant).");
		System.out.println("2: Value");
		
		double abatementInitial = 0.03;
		for(double abatementIncrease=0.00; abatementIncrease<50.0; abatementIncrease += 0.05) {

			DICEModelExperiment diceModel = new DICEModelExperiment();
			
			/*
			 * Linear abatement model
			 */
			for(int i=0; i<300; i++) {
				diceModel.abatement[i] = Math.min(abatementInitial + abatementIncrease*i/300.0, abatementMax);
			}

			diceModel.init();

			/*
			for(int i=0; i<300; i++) {
				System.out.println(
						String.format("%3d\t%4.2f\t%4.2f\t%4.2f\t%4.2f\t%4.2f",
								i, diceModel.damage[i], diceModel.emission[i], diceModel.carbonConcentration[i].getCarbonConcentrationInAtmosphere(), diceModel.temperature[i].getTemperatureOfAtmosphere(), diceModel.gdp[i]
								));
			}
			*/
			System.out.println(String.format("%8.4f \t %8.4f", abatementIncrease, diceModel.value[300-2]));

			/*
			Plots
			.createScatter(IntStream.range(0, 300).mapToDouble(i -> (double)i).toArray(), diceModel.welfare, 0, 300, 3)
			.setTitle("welfare").setXAxisLabel("time (years)").show();

			Plots
			.createScatter(IntStream.range(0, 300).mapToDouble(i -> (double)i).toArray(), Arrays.stream(diceModel.temperature).mapToDouble(Temperature::getTemperatureOfAtmosphere).toArray(), 0, 300, 3)
			.setTitle("temperature").setXAxisLabel("time (years)").show();

			Plots
			.createScatter(IntStream.range(0, 300).mapToDouble(i -> (double)i).toArray(), Arrays.stream(diceModel.carbonConcentration).mapToDouble(CarbonConcentration::getCarbonConcentrationInAtmosphere).toArray(), 0, 300, 3)
			.setTitle("carbon").setXAxisLabel("time (years)").show();

			Plots
			.createScatter(IntStream.range(0, 300).mapToDouble(i -> (double)i).toArray(), diceModel.damage, 0, 300, 3)
			.setTitle("damage").setXAxisLabel("time (years)").show();
			*/
		}
	}

	public DICEModelExperiment() { }

	private void init() {

		temperature[0] = temperatureInitial;

		gdp[0] = gdpInitial;
		carbonConcentration[0] = carbonConcentrationInitial;

		for(int i=0; i<numberOfTimes-1; i++) {

			// We are stepping in years (the models are currently hardcoded to dT = 1 year
			double time = i;

			/*
			 * Evolve geo-physical quantities
			 */
			
			/*
			 * Note: In the original model the 1/(1-\mu(0)) is part of the emission function.
			 * Here we add the factor on the outside
			 */
			emission[i] = (1 - abatement[i])/(1-abatement[0]) * emissionFunction.apply(time, gdp[i]);

			carbonConcentration[i+1] = evolutionOfCarbonConcentration.apply(carbonConcentration[i], emission[i]);

			double forcing = forcingFunction.apply(carbonConcentration[i], forcingExternal);
			temperature[i+1] = evolutionOfTemperature.apply(temperature[i], forcing);

			/*
			 * Apply damage to economy
			 */
			damage[i] = damageFunction.applyAsDouble(temperature[i].getTemperatureOfAtmosphere());

			/*
			 * Abatement cost
			 */
			
		    double e0 = 35.85;			// Initial emissions
		    double q0 = 105.5;			// Initial global output
		    double mu0 = 0.03;			// Initial mitigation rate
		    double sigma0 = e0/(q0*(1-mu0));			// Calculated initial emissions intensity

//			double abatementCost = emissionIntensityFunction.apply(time) * abatementCostFunction.apply(time, abatement[i]);
			double abatementCost = abatementCostFunction.apply(time, abatement[i]);
			double discountFactor = Math.exp(- r * time);

			welfare[i] = gdp[i] * (1-damage[i]) * (1 - abatementCost);
			value[i] = value[i] + welfare[i] * discountFactor;

			gdp[i+1] = gdp[i] * (1+growth);	// Simplified
		}
	}
}
