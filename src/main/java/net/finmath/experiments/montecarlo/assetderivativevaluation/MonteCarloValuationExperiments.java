package net.finmath.experiments.montecarlo.assetderivativevaluation;

import java.util.List;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BachelierModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.models.HestonModel;
import net.finmath.montecarlo.assetderivativevaluation.models.HestonModel.Scheme;
import net.finmath.montecarlo.assetderivativevaluation.products.AsianOption;
import net.finmath.montecarlo.assetderivativevaluation.products.AssetMonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.products.BermudanOption;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.model.ProcessModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * Illustrating how to use the Monte-Carlo Euler-Scheme simulation (TimeDiscretization, BrownianMotion, EulerScheme) with
 * different models and products.
 * 
 * @author Christian Fries
 */
public class MonteCarloValuationExperiments {

	private double	initialValue = 100.0;		// S_0 = S(t_0)
	private double	riskFreeRate = 0.05;		// r
	private double	sigma = 0.20;				// sigma

	private final double theta = sigma*sigma;
	private final double kappa = 0.1;
	private final double xi = 0.2;
	private final double rho = 0.2;

	private double	optionMaturity = 5.0;		// T
	private double	optionStrike = 130;			// K

	private double	initialTime = 0.0;			// t_0
	private int		numberOfTimeSteps = 5;
	
	private long	seed = 3216;
	private int		numberOfFactors = 2;		// dimension of the Brownian Motion 
	private long	numberOfSamples = 2000000;	// 2*10^7

	public static void main(String[] args) throws CalculationException {
		
		MonteCarloValuationExperiments experiment = new MonteCarloValuationExperiments();
		experiment.printValuations();
	}

	private void printValuations() throws CalculationException {
		List<ProcessModel> models = List.of(
				new BlackScholesModel(initialValue, riskFreeRate, sigma),
				new BachelierModel(initialValue, riskFreeRate, sigma * initialValue),
				new HestonModel(initialValue, riskFreeRate, sigma, theta, kappa, xi, rho, Scheme.FULL_TRUNCATION)				
				);
		
		List<AssetMonteCarloProduct> products = List.of(
				new EuropeanOption(optionMaturity, optionStrike),
				new AsianOption(optionMaturity, optionStrike, new TimeDiscretizationFromArray(1.0, optionMaturity, 1.0)),
				new BermudanOption(new double[] { 3.0, 5.0 } , new double[] { 1.0, 1.0 }, new double[] { 110, optionStrike })
				);

		printValuations(models, products);
	}

	/**
	 * Print the values of product for all models.
	 * @param models List of the models.
	 * @param products List of the products.
	 * @throws CalculationException
	 */
	public void printValuations(List<ProcessModel> models, List<AssetMonteCarloProduct> products) throws CalculationException {

		// Value all product with all models
		for(ProcessModel model : models) {

			System.out.println(String.format(model.getClass().getSimpleName()) + ":");
			MonteCarloAssetModel monteCarloAssetModel = getMonteCarloAssetModel(model);

			for(AssetMonteCarloProduct product : products) {

				double value = product.getValue(initialTime, monteCarloAssetModel).expectation().doubleValue();

				System.out.println(String.format("\t%15s: %f", product.getClass().getSimpleName(), value));
			}
			System.out.println();
		}
	}

	/**
	 * Create the Monte-Carlo simulation of the Euler-Scheme approximation for a given model.
	 * 
	 * @param model A {@link ProcessModel}
	 * @return The corresponding {@link MonteCarloAssetModel}
	 * @throws CalculationException
	 */
	MonteCarloAssetModel getMonteCarloAssetModel(ProcessModel model) throws CalculationException {

		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, optionMaturity/numberOfTimeSteps);

		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, (int)numberOfSamples, (int)seed);

		MonteCarloProcess process = new EulerSchemeFromProcessModel(model, brownianMotion);

		MonteCarloAssetModel monteCarloAssetModel = new MonteCarloAssetModel(process);

		return monteCarloAssetModel;
	}
	
}
