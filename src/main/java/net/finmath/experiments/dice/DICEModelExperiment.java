package net.finmath.experiments.dice;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import java.util.function.UnaryOperator;
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
 * Experiment related to the DICE model
 */
public class DICEModelExperiment {

	private static int numberOfTimes = 100;

	/*
	 * Input to this class
	 */
	private final UnaryOperator<Double> abatementFunction;

	/*
	 * Simulated values - stored for plotting
	 */
	private Temperature[] temperature = new Temperature[numberOfTimes];
	private CarbonConcentration[] carbonConcentration = new CarbonConcentration[numberOfTimes];
	private double[] gdp = new double[numberOfTimes];
	private double[] emission = new double[numberOfTimes];
	private double[] abatement = new double[numberOfTimes];
	private double[] welfare = new double[numberOfTimes];
	private double[] value = new double[numberOfTimes];
	private double[] damage = new double[numberOfTimes];
	private double[] capital = new double[numberOfTimes];

	public DICEModelExperiment(UnaryOperator<Double> abatementFunction) {
		super();
		this.abatementFunction = abatementFunction;
	}

	public static void main(String[] args) {

		System.out.println("1: x with 500/x = Year in which max abatement is reached (linear interpolation, then constant).");
		System.out.println("2: Value");

		double abatementInitial = 0.03;
		double abatementMax = 1.0;
		for(int abatementSzenario = 0; abatementSzenario < 100; abatementSzenario++) {
			double abatementIncrease = abatementSzenario * 0.05 * 0.97;
			double abatementMaxTime = 100 * (1-abatementInitial) / abatementIncrease;

			/*
			 * In this experiment the abatement is not optimized (and neither the savings rate).
			 * We just plot the model values for different abatement functions (and a constant savings rate).
			 */

			UnaryOperator<Double> abatementFunction = timeIndex -> Math.min(abatementInitial + abatementIncrease * timeIndex/numberOfTimes, abatementMax);

			DICEModelExperiment diceModel = new DICEModelExperiment(abatementFunction);
			diceModel.init();

			System.out.println(String.format("%8.4f \t %8.4f", abatementMaxTime, diceModel.value[numberOfTimes-1]));

			if(abatementSzenario%20 == 0) {
				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), diceModel.welfare, 0, 300, 3)
				.setTitle("welfare (szenario=" + abatementMaxTime + ")").setXAxisLabel("time (years)").show();

				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), diceModel.damage, 0, 300, 3)
				.setTitle("damage (szenario=" + abatementMaxTime + ")").setXAxisLabel("time (years)").show();

				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), Arrays.stream(diceModel.carbonConcentration).mapToDouble(CarbonConcentration::getCarbonConcentrationInAtmosphere).toArray(), 0, 300, 3)
				.setTitle("carbon (szenario=" + abatementMaxTime + ")").setXAxisLabel("time (years)").show();

				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), Arrays.stream(diceModel.temperature).mapToDouble(Temperature::getTemperatureOfAtmosphere).toArray(), 0, 300, 3)
				.setTitle("temperature (szenario=" + abatementMaxTime + ")").setXAxisLabel("time (years)").show();

				Plots
				.createScatter(IntStream.range(0, numberOfTimes).mapToDouble(i -> (double)i).toArray(), diceModel.abatement, 0, 300, 3)
				.setTitle("abatement (szenario=" + abatementMaxTime + ")").setXAxisLabel("time (years)").show();
			}
		}
	}

	private void init() {

		/*
		 * Building the model by composing the different functions
		 */
		
		/*
		 * Discount rate
		 * 
		 * r = 0.01	 78.5091 
		 * r = 0.05 157.0182
		 */
		double r = 0.01;

		/*
		 * Note: Calling default constructors for the sub-models will initialise the default parameters.
		 */

		/*
		 * State vectors initial values
		 */
		Temperature temperatureInitial = new Temperature(0.85, 0.0068);	
		CarbonConcentration carbonConcentrationInitial = new CarbonConcentration(851, 460, 1740);	// Level of Carbon (GtC)

		/*
		 * Sub-Modules: functional dependencies and evolution
		 */

		// Model that describes the damage on the GBP as a function of the temperature-above-normal
		DoubleUnaryOperator damageFunction = new DamageFromTemperature();

		EvolutionOfTemperature evolutionOfTemperature = new EvolutionOfTemperature();

		EvolutionOfCarbonConcentration evolutionOfCarbonConcentration = new EvolutionOfCarbonConcentration();

		ForcingFunction forcingFunction = new ForcingFunction();
		Double forcingExternal = 1.0;

		EmissionIntensityFunction emissionIntensityFunction = new EmissionIntensityFunction();
		EmissionFunction emissionFunction = new EmissionFunction(emissionIntensityFunction);

		// Abatement
		AbatementCostFunction abatementCostFunction = new AbatementCostFunction();

		/*
		 * GDP
		 */
		double A0 = 5.115;		// Initial Total Factor of Productivity 
		double K0 = 223;		// Initial Capital
		double L0 = 7403;		// Initial Population
		double gamma = 0.3;		// Capital Elasticity in Production Function
		double gdpInitial = A0*Math.pow(K0,gamma)*Math.pow(L0/1000,1-gamma);

		// Capital
		EvolutionOfCapital evolutionOfCapital = new EvolutionOfCapital();

		/*
		 * Set initial values
		 */
		temperature[0] = temperatureInitial;
		carbonConcentration[0] = carbonConcentrationInitial;
		gdp[0] = gdpInitial;
		capital[0] = K0;
		double utilityDiscountedSum = 0;
		double L = L0;
		double A = A0;

		/*
		 * Evolve
		 */
		for(int i=0; i<numberOfTimes-1; i++) {

			// We are stepping in years (the models are currently hardcoded to dT = 1 year
			double time = i;

			abatement[i] = abatementFunction.apply(time);

			/*
			 * Evolve geo-physical quantities i -> i+1
			 */
			emission[i] = (1 - abatement[i])/(1-abatement[0]) * emissionFunction.apply(time, gdp[i]);

			carbonConcentration[i+1] = evolutionOfCarbonConcentration.apply(carbonConcentration[i], emission[i]);

			double forcing = forcingFunction.apply(carbonConcentration[i+1], forcingExternal);
			temperature[i+1] = evolutionOfTemperature.apply(temperature[i], forcing);

			/*
			 * Note: In the original model the 1/(1-\mu(0)) is part of the emission function.
			 * Here we add the factor on the outside
			 */

			/*
			 * Evolve economy i -> i+1
			 */

			/*
			 * Apply damage to economy
			 */
			damage[i] = damageFunction.applyAsDouble(temperature[i].getTemperatureOfAtmosphere());

			/*
			 * Abatement cost
			 */

			//			double abatementCost = emissionIntensityFunction.apply(time) * abatementCostFunction.apply(time, abatement[i]);
			double abatementCost = abatementCostFunction.apply(time, abatement[i]);

			/*
			 * Evolve economy to i+1
			 */
			double gdpNet = gdp[i] * (1-damage[i]) * (1 - abatementCost);

			// Constant from the original model - in the original model this is a time varying control variable.
			double savingsRate = 0.259029014481802;

			double consumption = (1-savingsRate) * gdpNet;
			double investment = savingsRate * gdpNet;

			capital[i+1] = evolutionOfCapital.apply(time).apply(capital[i], 5*investment);

			/*
			 * Evolve population and productivity for next GDP
			 */
			double La = 11500; // Asymptotic population
			double lg = 0.134; // Population growth			
			L = L * Math.pow(La/L,lg);

			double ga = 0.076;          // Initial TFP rate
			double deltaA = 0.005;      // TFP increase rate
			A = A / (1 - ga * Math.exp(-deltaA * 5 * (i-1)));

			gdp[i+1] = A*Math.pow(capital[i+1],gamma)*Math.pow(L/1000,1-gamma);

			/*
			 * Calculate utility
			 */
			double alpha = 1.45;           // Elasticity of marginal utility of consumption (GAMS elasmu)
			double C = consumption;
			double utility = L*Math.pow(C / (L/1000),1-alpha)/(1-alpha);

			/*
			 * Discounted utility
			 */
			double discountFactor = Math.exp(- r * (time*5.0));
			welfare[i] = utility; //consumption;

			utilityDiscountedSum = utilityDiscountedSum + utility*discountFactor;

			// The 150000 is just a shift to make te plot nice

			value[i+1] = utilityDiscountedSum; //value[i] + welfare[i] * discountFactor;
		}
	}
}
