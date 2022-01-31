package net.finmath.experiments.dice;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

import net.finmath.experiments.dice.submodels.AbatementCostFunction;
import net.finmath.experiments.dice.submodels.CarbonConcentration;
import net.finmath.experiments.dice.submodels.DamageFromTemperature;
import net.finmath.experiments.dice.submodels.EmissionFunction;
import net.finmath.experiments.dice.submodels.EmissionIntensityFunction;
import net.finmath.experiments.dice.submodels.EvolutionOfCapital;
import net.finmath.experiments.dice.submodels.EvolutionOfCarbonConcentration;
import net.finmath.experiments.dice.submodels.EvolutionOfTemperature;
import net.finmath.experiments.dice.submodels.ForcingFunction;
import net.finmath.experiments.dice.submodels.Temperature;
import net.finmath.plots.Plots;

/*
 * Simplified version of the dice model: The evolution of the economic factors
 * is missing here.
 */
public class DICEModelExperiment {

	private static int numberOfTimes = 100;

	/*
	 * Note: Calling default constructors for the sub-models will initialise the default parameters.
	 */

	// State vectors initial values
	private Temperature temperatureInitial = new Temperature(0.85, 0.0068);	
	private CarbonConcentration carbonConcentrationInitial = new CarbonConcentration(851, 460, 1740);	// Level of Carbon (GtC)

	/*
	 * Sub-Modules: functional dependencies and evolution
	 */

	// Model that describes the damage on the GBP as a function of the temperature-above-normal
	DoubleUnaryOperator damageFunction = new DamageFromTemperature();
	double[] damage = new double[numberOfTimes];

	EvolutionOfTemperature evolutionOfTemperature = new EvolutionOfTemperature();
	Temperature[] temperature = new Temperature[numberOfTimes];

	EvolutionOfCarbonConcentration evolutionOfCarbonConcentration = new EvolutionOfCarbonConcentration();
	CarbonConcentration[] carbonConcentration = new CarbonConcentration[numberOfTimes];

	double forcingExternal = 1.0;
	ForcingFunction forcingFunction = new ForcingFunction();

	EmissionIntensityFunction emissionIntensityFunction = new EmissionIntensityFunction();
	EmissionFunction emissionFunction = new EmissionFunction(emissionIntensityFunction);

	// Abatement
	AbatementCostFunction abatementCostFunction = new AbatementCostFunction();

	/*
	 * GDB - currently constant, but the values from the original model
	 */
	private double A0 = 5.115;		// Initial Total Factor of Productivity 
	private double K0 = 223;		// Initial Capital
	private double L0 = 7403;		// Initial Population
	private double gamma = 0.3;		// Capital Elasticity in Production Function
	private double gdpInitial = A0*Math.pow(K0,gamma)*Math.pow(L0/1000,1-gamma);

	// Capital
	EvolutionOfCapital evolutionOfCapital = new EvolutionOfCapital();
	double[] capital = new double[numberOfTimes];

	/*
	 * Simulated values - stored for plotting
	 */
	double[] gdp = new double[numberOfTimes];
	double[] emission = new double[numberOfTimes];
	double[] abatement = new double[numberOfTimes];
	double[] welfare = new double[numberOfTimes];
	double[] value = new double[numberOfTimes];

	static double abatementMax = 1.0;

	double r = 0.02;

	public static void main(String[] args) {

		System.out.println("1: x with 500/x = Year in which max abatement is reached (linear interpolation, then constant).");
		System.out.println("2: Value");

		double abatementInitial = 0.03;
		for(int abatementSzenario = 0; abatementSzenario < 100; abatementSzenario++) {
			double abatementIncrease = abatementSzenario * 0.05;			// 0 to 25

			DICEModelExperiment diceModel = new DICEModelExperiment();

			/*
			 * Linear abatement model
			 */
			for(int i=0; i<numberOfTimes; i++) {
				diceModel.abatement[i] = Math.min(abatementInitial + abatementIncrease*i/numberOfTimes, abatementMax);
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
			System.out.println(String.format("%8.4f \t %8.4f", abatementIncrease, diceModel.value[numberOfTimes-1]));

			if(abatementSzenario%20 == 0) {
				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), diceModel.welfare, 0, 300, 3)
				.setTitle("welfare (szenario=" + abatementSzenario + ")").setXAxisLabel("time (years)").show();

				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), diceModel.damage, 0, 300, 3)
				.setTitle("damage (szenario=" + abatementSzenario + ")").setXAxisLabel("time (years)").show();

				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), Arrays.stream(diceModel.carbonConcentration).mapToDouble(CarbonConcentration::getCarbonConcentrationInAtmosphere).toArray(), 0, 300, 3)
				.setTitle("carbon (szenario=" + abatementSzenario + ")").setXAxisLabel("time (years)").show();

				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), Arrays.stream(diceModel.temperature).mapToDouble(Temperature::getTemperatureOfAtmosphere).toArray(), 0, 300, 3)
				.setTitle("temperature (szenario=" + abatementSzenario + ")").setXAxisLabel("time (years)").show();

				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), diceModel.abatement, 0, 300, 3)
				.setTitle("abatement (szenario=" + abatementSzenario + ")").setXAxisLabel("time (years)").show();
			}
		}
	}

	public DICEModelExperiment() { }

	private void init() {

		temperature[0] = temperatureInitial;

		gdp[0] = gdpInitial;
		carbonConcentration[0] = carbonConcentrationInitial;
		capital[0] = K0;
		double utility = 0;

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

			//			double abatementCost = emissionIntensityFunction.apply(time) * abatementCostFunction.apply(time, abatement[i]);
			double abatementCost = abatementCostFunction.apply(time, abatement[i]);

			double gdpNet = gdp[i] * (1-damage[i]) * (1 - abatementCost);
			
			// Constant from the original model - in the original model this is a time varying control variable.
			double savingsRate = 0.259029014481802;
			
			double consumption = (1-savingsRate) * gdpNet;
			double investment = savingsRate * gdpNet;
			
			capital[i+1] = evolutionOfCapital.apply(time).apply(capital[i], 5*investment);
			
			double La = 11500; // Asymptotic population
			double lg = 0.134; // Population growth			
			L0 = L0 * Math.pow(La/L0,lg);

		    double ga = 0.076;          // Initial TFP rate
		    double deltaA = 0.005;      // TFP increase rate
		    A0 = A0 / (1 - ga * Math.exp(-deltaA * 5 * (i-1)));
			
			gdp[i+1] = A0*Math.pow(capital[i+1],gamma)*Math.pow(L0/1000,1-gamma);
			
			double alpha = 1.45;           // Elasticity of marginal utility of consumption (GAMS elasmu)
			double C = consumption;
			double L = L0;
			double rho = r/5;
//			J = J - L0*(( Math.pow(1000/L*C,1-alpha) - 1)/((1-alpha)))/Math.pow(1+rho,5*(i-1));
			utility = utility + L*Math.pow(C / (L/1000),1-alpha)/(1-alpha)/Math.pow(1+rho,5*(i-1));

			// The 150000 is just a shift to make te plot nice

			welfare[i] = utility + 150000; //consumption;
			
			double discountFactor = Math.exp(- r * (time*5.0));
			value[i+1] = welfare[i]; //value[i] + welfare[i] * discountFactor;
		}
	}
}
