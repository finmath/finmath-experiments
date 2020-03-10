/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 19.01.2004, 21.12.2012, 01.02.2012
 */
package net.finmath.experiments.montecarlo.assetderivativevaluation;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import net.finmath.exception.CalculationException;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOption2;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOptionDeltaLikelihood;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOptionDeltaPathwise;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOptionGammaLikelihood;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOptionGammaPathwise;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOptionRhoLikelihood;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOptionRhoPathwise;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOptionVegaLikelihood;
import net.finmath.experiments.montecarlo.assetderivativevaluation.products.EuropeanOptionVegaPathwise;
import net.finmath.montecarlo.assetderivativevaluation.AssetModelMonteCarloSimulationModel;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloBlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.AsianOption;
import net.finmath.montecarlo.assetderivativevaluation.products.BermudanOption;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;


/**
 * This class represents a collection of several "tests" illustrating different aspects
 * related to the Monte-Carlo Simulation and derivative pricing (using a simple
 * Black-Scholes model.
 *
 * @author Christian Fries
 */
public class BlackScholesMonteCarloValuationTest {

	// Model properties
	private final double	initialValue   = 1.0;
	private final double	riskFreeRate   = 0.02;
	private final double	volatility     = 0.30;

	// Process discretization properties
	private final int		numberOfPaths		= 100000;
	private final int		numberOfTimeSteps	= 100;
	private final double	deltaT				= 0.1;


	private AssetModelMonteCarloSimulationModel model = null;

	/**
	 * This main method will test a Monte-Carlo simulation of a Black-Scholes model and some valuations
	 * performed with this model.
	 *
	 * @throws net.finmath.exception.CalculationException Thrown if the valuation fails, specific cause may be available via the <code>cause()</code> method.
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException, CalculationException, InterruptedException
	{
		final BlackScholesMonteCarloValuationTest pricingTest = new BlackScholesMonteCarloValuationTest();

		/*
		 * Read input
		 */
		final int testNumber = readTestNumber();



		final long start = System.currentTimeMillis();

		switch(testNumber) {
		case 1:
			// This test requires a MonteCarloBlackScholesModel and will not work with others models
			pricingTest.testEuropeanCall();
			break;
		case 2:
			pricingTest.testModelProperties();
			break;
		case 3:
			pricingTest.testModelRandomVariable();
			break;
		case 4:
			pricingTest.testEuropeanAsianBermudanOption();
			break;
		case 5:
			pricingTest.testMultiThreaddedValuation();
			break;
		case 6:
			// This test requires a MonteCarloBlackScholesModel and will not work with others models
			pricingTest.testEuropeanCallDelta();
			break;
		case 7:
			// This test requires a MonteCarloBlackScholesModel and will not work with others models
			pricingTest.testEuropeanCallVega();
			break;
		case 8:
			// This test requires a MonteCarloBlackScholesModel and will not work with others models
			pricingTest.testEuropeanCallGamma();
			break;
		case 9:
			// This test requires a MonteCarloBlackScholesModel and will not work with others models
			//			pricingTest.testEuropeanCallTheta();
			break;
		}

		final long end = System.currentTimeMillis();

		System.out.println("\nCalculation time required: " + (end-start)/1000.0 + " seconds.");
	}

	public BlackScholesMonteCarloValuationTest() {
		super();

		// Create a Model (see method getModel)
		model = getModel();
	}

	private static int readTestNumber() {
		System.out.println("Please select a test to run (click in this window and enter a number):");
		System.out.println("\t 1: Valuation of European call options (with different strikes).");
		System.out.println("\t 2: Some model properties.");
		System.out.println("\t 3: Print some realizations of the S(1).");
		System.out.println("\t 4: Valuation of European, Asian, Bermudan option.");
		System.out.println("\t 5: Multi-Threadded valuation of some ten thousand Asian options.");
		System.out.println("\t 6: Sensitivity (Delta) of European call options (with different strikes) using different methods.");
		System.out.println("\t 7: Sensitivity (Vega) of European call options (with different strikes) using different methods.");
		System.out.println("\t 8: Sensitivity (Gamma) of European call options (with different strikes) using different methods.");
		//		System.out.println("\t 9: Sensitivity (Theta) of European call options (with different strikes) using different methods.");
		System.out.println("");
		System.out.print("Test to run: ");

		//  open up standard input
		final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		int testNumber = 0;
		try {
			final String test = br.readLine();
			testNumber = Integer.valueOf(test);
		} catch (final IOException ioe) {
			System.out.println("IO error trying to read test number!");
			System.exit(1);
		}

		System.out.println("");
		return testNumber;
	}

	public AssetModelMonteCarloSimulationModel getModel()
	{
		// Create the time discretization
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, numberOfTimeSteps, deltaT);

		// Create an instance of a black scholes monte carlo model
		final AssetModelMonteCarloSimulationModel model = new MonteCarloBlackScholesModel(
				timeDiscretization,
				numberOfPaths,
				initialValue,
				riskFreeRate,
				volatility);

		return model;
	}

	@Test
	public void testEuropeanCall() throws CalculationException
	{
		final MonteCarloBlackScholesModel blackScholesModel = (MonteCarloBlackScholesModel)model;

		// Java DecimalFormat for our output format
		final DecimalFormat numberFormatStrike	= new DecimalFormat("     0.00 ");
		final DecimalFormat numberFormatValue		= new DecimalFormat(" 0.00E00");
		final DecimalFormat numberFormatDeviation	= new DecimalFormat("  0.00E00; -0.00E00");

		// Test options with different strike
		System.out.println("Valuation of European Options");
		System.out.println(" Strike \t Monte-Carlo \t Analytic \t Deviation \t Monte-Carlo (alternative implementation)");

		final double initialValue	= blackScholesModel.getAssetValue(0.0, 0).get(0);
		final double riskFreeRate	= blackScholesModel.getModel().getRiskFreeRate().doubleValue();
		final double volatility	= blackScholesModel.getModel().getVolatility().doubleValue();

		final double optionMaturity	= 1.0;
		for(double optionStrike = 0.60; optionStrike < 1.50; optionStrike += 0.05) {

			// Create a product
			final EuropeanOption		callOption	= new EuropeanOption(optionMaturity, optionStrike);
			// Value the product with Monte Carlo
			final double valueMonteCarlo	= callOption.getValue(blackScholesModel);

			// Create a product
			final EuropeanOption2	callOption2	= new EuropeanOption2(optionMaturity, optionStrike);
			// Value the product with Monte Carlo
			final double valueMonteCarlo2	= callOption2.getValue(blackScholesModel);

			// Calculate the analytic value
			final double valueAnalytic	= net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, volatility, optionMaturity, optionStrike);

			// Print result
			System.out.println(numberFormatStrike.format(optionStrike) +
					"\t" + numberFormatValue.format(valueMonteCarlo) +
					"\t" + numberFormatValue.format(valueAnalytic) +
					"\t" + numberFormatDeviation.format(valueMonteCarlo-valueAnalytic) +
					"\t" + numberFormatValue.format(valueMonteCarlo2));

		}
	}

	/**
	 * Test some properties of the model
	 */
	@Test
	public void testModelProperties() throws CalculationException {

		System.out.println("Time \tAverage \t\tVariance");

		final TimeDiscretization modelTimeDiscretization = model.getTimeDiscretization();
		for(final double time : modelTimeDiscretization) {
			final RandomVariable assetValue = model.getAssetValue(time, 0);

			final double average	= assetValue.getAverage();
			final double variance	= assetValue.getVariance();
			final double error	= assetValue.getStandardError();

			final DecimalFormat formater2Digits = new DecimalFormat("0.00");
			final DecimalFormat formater4Digits = new DecimalFormat("0.0000");
			System.out.println(formater2Digits.format(time) + " \t" + formater4Digits.format(average) + "\t+/- " + formater4Digits.format(error) + "\t" + formater4Digits.format(variance));
		}
	}

	@Test
	public void testModelRandomVariable() throws CalculationException {
		final RandomVariable stockAtTimeOne = model.getAssetValue(1.0, 0);

		System.out.println("The first 100 realizations of the " + stockAtTimeOne.size() + " realizations of S(1) are:");
		System.out.println("Path\tValue");
		for(int i=0; i<100;i++) {
			System.out.println(i + "\t" + stockAtTimeOne.get(i));
		}
	}

	/**
	 * Evaluates different options (European, Asian, Bermudan) using the given model.
	 *
	 * The options share the same maturity and strike for the at t=3.0.
	 * Observations which can be made:
	 * <ul>
	 * <li>The Asian is cheaper than the European since averaging reduces the volatility.
	 * <li>The European is cheaper than the Bermudan since exercises into the European is one (out of may) exercises strategies of the Bermudan.
	 * </ul>
	 */
	@Test
	public void testEuropeanAsianBermudanOption() throws CalculationException {
		/*
		 * Common parameters
		 */
		final double maturity = 3.0;
		final double strike = 1.06;

		/*
		 * European Option
		 */
		final EuropeanOption myEuropeanOption = new EuropeanOption(maturity,strike);
		final double valueOfEuropeanOption = myEuropeanOption.getValue(model);

		/*
		 * Asian Option
		 */
		final double[] averagingPoints = { 1.0, 1.5, 2.0, 2.5 , 3.0 };

		final AsianOption myAsianOption = new AsianOption(maturity,strike, new TimeDiscretizationFromArray(averagingPoints));
		final double valueOfAsianOption = myAsianOption.getValue(model);

		/*
		 * Bermudan Option
		 */
		final double[] exerciseDates	= { 1.0,  2.0,  3.0};
		final double[] notionals		= { 1.20, 1.10, 1.0};
		final double[] strikes		= { 1.03, 1.05, 1.06 };

		// Lower bound method
		final BermudanOption myBermudanOptionLowerBound = new BermudanOption(exerciseDates, notionals, strikes, BermudanOption.ExerciseMethod.ESTIMATE_COND_EXPECTATION);
		final double valueOfBermudanOptionLowerBound = myBermudanOptionLowerBound.getValue(model);

		// Upper bound method
		final BermudanOption myBermudanOptionUpperBound = new BermudanOption(exerciseDates, notionals, strikes, BermudanOption.ExerciseMethod.UPPER_BOUND_METHOD);
		final double valueOfBermudanOptionUpperBound = myBermudanOptionUpperBound.getValue(model);

		/*
		 * Output
		 */
		System.out.println("Value of Asian Option is \t"	+ valueOfAsianOption);
		System.out.println("Value of European Option is \t"	+ valueOfEuropeanOption);
		System.out.println("Value of Bermudan Option is \t"	+ "(" + valueOfBermudanOptionLowerBound + "," + valueOfBermudanOptionUpperBound + ")");

		assertTrue(valueOfAsianOption < valueOfEuropeanOption);
		assertTrue(valueOfBermudanOptionLowerBound < valueOfBermudanOptionUpperBound);
		assertTrue(valueOfEuropeanOption < valueOfBermudanOptionUpperBound);
	}

	/**
	 * Evaluates 100000 Asian options in 10 parallel threads (each valuing 10000 options)
	 *
	 * @throws InterruptedException
	 */
	public void testMultiThreaddedValuation() throws InterruptedException {
		final double[] averagingPoints = { 0.5, 1.0, 1.5, 2.0, 2.5, 2.5, 3.0, 3.0 , 3.0, 3.5, 4.5, 5.0 };
		final double maturity = 5.0;
		final double strike = 1.07;

		final int			numberOfThreads	= 10;
		final Thread[]	myThreads		= new Thread[numberOfThreads];

		for(int k=0; k<myThreads.length; k++) {

			final int threadNummer = k;

			// Create a runnable - piece of code which can be run in parallel.
			final Runnable myRunnable = new Runnable() {
				@Override
				public void run() {
					try {
						for(int i=0;i<10000; i++) {
							final AsianOption myAsianOption = new AsianOption(maturity,strike, new TimeDiscretizationFromArray(averagingPoints));
							final double valueOfAsianOption = myAsianOption.getValue(model);
							System.out.println("Thread " + threadNummer + ": Value of Asian Option " + i + " is " + valueOfAsianOption);
						}
					} catch (final CalculationException e) {
					}
				}
			};

			// Create a thread (will run asynchronously)
			myThreads[k] = new Thread(myRunnable);
			myThreads[k].start();
		}

		// Wait for all threads to complete
		for(int i=0; i<myThreads.length; i++) {
			myThreads[i].join();
		}

		// Threads are completed at this point
	}

	@Test
	public void testEuropeanCallDelta() throws CalculationException
	{
		final MonteCarloBlackScholesModel blackScholesModel = (MonteCarloBlackScholesModel)model;

		// Java DecimalFormat for our output format
		final DecimalFormat numberFormatStrike	= new DecimalFormat("     0.00 ");
		final DecimalFormat numberFormatValue		= new DecimalFormat(" 0.00E00");
		final DecimalFormat numberFormatDeviation	= new DecimalFormat("  0.00E00; -0.00E00");

		final double initialValue	= blackScholesModel.getAssetValue(0.0, 0).get(0);
		final double riskFreeRate	= blackScholesModel.getModel().getRiskFreeRate().doubleValue();
		final double volatility	= blackScholesModel.getModel().getVolatility().doubleValue();

		final double optionMaturity	= 1.0;

		// Test options with different strike
		System.out.println("Calculation of Option Delta (European options with maturity " + optionMaturity + "):");
		System.out.println(" Strike \t MC Fin.Diff.\t MC Pathwise\t MC Likelihood\t Analytic \t Diff MC-FD \t Diff MC-PW \t Diff MC-LR");

		for(double optionStrike = 0.60; optionStrike < 1.50; optionStrike += 0.05) {

			// Create a product
			final EuropeanOption		callOption	= new EuropeanOption(optionMaturity, optionStrike);

			// Value the product with Monte Carlo
			final double shift = initialValue * 1E-6;

			final Map<String,Object> dataUpShift = new HashMap<String,Object>();
			dataUpShift.put("initialValue", initialValue + shift);

			final double valueUpShift	= (Double)(callOption.getValuesForModifiedData(blackScholesModel, dataUpShift).get("value"));

			final Map<String,Object> dataDownShift = new HashMap<String,Object>();
			dataDownShift.put("initialValue", initialValue - shift);
			final double valueDownShift	= (Double)(callOption.getValuesForModifiedData(blackScholesModel, dataDownShift).get("value"));

			// Calculate the finite difference of the monte-carlo value
			final double delta = (valueUpShift-valueDownShift) / ( 2 * shift );

			// Calculate the finite difference of the analytic value
			final double deltaFiniteDiffAnalytic	=
					(
							net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue+shift, riskFreeRate, volatility, optionMaturity, optionStrike)
							- net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue-shift, riskFreeRate, volatility, optionMaturity, optionStrike)
							)/(2*shift);

			// Calculate the analytic value
			final double deltaAnalytic	= net.finmath.functions.AnalyticFormulas.blackScholesOptionDelta(initialValue, riskFreeRate, volatility, optionMaturity, optionStrike);

			// Calculate the value using pathwise differentiation
			final EuropeanOptionDeltaPathwise		callOptionDeltaPathwise	= new EuropeanOptionDeltaPathwise(optionMaturity, optionStrike);
			final double							deltaPathwise				= callOptionDeltaPathwise.getValue(blackScholesModel);

			// Calculate the value using likelihood differentiation
			final EuropeanOptionDeltaLikelihood	callOptionDeltaLikelihood	= new EuropeanOptionDeltaLikelihood(optionMaturity, optionStrike);
			final double							deltaLikelihood				= callOptionDeltaLikelihood.getValue(blackScholesModel);

			// Print result
			System.out.println(numberFormatStrike.format(optionStrike) +
					"\t" + numberFormatValue.format(delta) +
					"\t" + numberFormatValue.format(deltaPathwise) +
					"\t" + numberFormatValue.format(deltaLikelihood) +
					"\t" + numberFormatValue.format(deltaAnalytic) +
					"\t" + numberFormatDeviation.format(delta-deltaAnalytic) +
					"\t" + numberFormatDeviation.format(deltaPathwise-deltaAnalytic) +
					"\t" + numberFormatDeviation.format(deltaLikelihood-deltaAnalytic));
		}
		System.out.println("__________________________________________________________________________________________\n");
	}

	@Test
	public void testEuropeanCallVega() throws CalculationException
	{
		final MonteCarloBlackScholesModel blackScholesModel = (MonteCarloBlackScholesModel)model;

		// Java DecimalFormat for our output format
		final DecimalFormat numberFormatStrike	= new DecimalFormat("     0.00 ");
		final DecimalFormat numberFormatValue		= new DecimalFormat(" 0.00E00");
		final DecimalFormat numberFormatDeviation	= new DecimalFormat("  0.00E00; -0.00E00");

		final double initialValue	= blackScholesModel.getAssetValue(0.0, 0).get(0);
		final double riskFreeRate	= blackScholesModel.getModel().getRiskFreeRate().doubleValue();
		final double volatility	= blackScholesModel.getModel().getVolatility().doubleValue();

		final double optionMaturity	= 5.0;

		// Test options with different strike
		System.out.println("Calculation of Option Vega (European options with maturity " + optionMaturity + "):");
		System.out.println(" Strike \t MC Fin.Diff.\t MC Pathwise\t MC Likelihood\t Analytic \t Diff MC-FD \t Diff MC-PW \t Diff MC-LR");

		for(double optionStrike = 0.60; optionStrike < 1.50; optionStrike += 0.05) {

			// Create a product
			final EuropeanOption		callOption	= new EuropeanOption(optionMaturity, optionStrike);

			// Value the product with Monte Carlo
			final double shift = volatility * 1E-6;

			final Map<String,Object> dataUpShift = new HashMap<String,Object>();
			dataUpShift.put("volatility", volatility + shift);
			final double valueUpShift	= (Double)(callOption.getValuesForModifiedData(blackScholesModel, dataUpShift).get("value"));

			final Map<String,Object> dataDownShift = new HashMap<String,Object>();
			dataDownShift.put("volatility", volatility - shift);
			final double valueDownShift	= (Double)(callOption.getValuesForModifiedData(blackScholesModel, dataDownShift).get("value"));

			// Calculate the finite difference of the monte-carlo value
			final double vega = (valueUpShift-valueDownShift) / ( 2 * shift );

			// Calculate the finite difference of the analytic value
			final double vegaFiniteDiffAnalytic	=
					(
							net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, volatility+shift, optionMaturity, optionStrike)
							- net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, volatility-shift, optionMaturity, optionStrike)
							)/(2*shift);

			// Calculate the analytic value
			final double vegaAnalytic	= net.finmath.functions.AnalyticFormulas.blackScholesOptionVega(initialValue, riskFreeRate, volatility, optionMaturity, optionStrike);

			// Calculate the value using pathwise differentiation
			final EuropeanOptionVegaPathwise		callOptionVegaPathwise	= new EuropeanOptionVegaPathwise(optionMaturity, optionStrike);
			final double							vegaPathwise				= callOptionVegaPathwise.getValue(blackScholesModel);

			// Calculate the value using likelihood differentiation
			final EuropeanOptionVegaLikelihood	callOptionVegaLikelihood	= new EuropeanOptionVegaLikelihood(optionMaturity, optionStrike);
			final double							vegaLikelihood				= callOptionVegaLikelihood.getValue(blackScholesModel);

			// Print result
			System.out.println(numberFormatStrike.format(optionStrike) +
					"\t" + numberFormatValue.format(vega) +
					"\t" + numberFormatValue.format(vegaPathwise) +
					"\t" + numberFormatValue.format(vegaLikelihood) +
					"\t" + numberFormatValue.format(vegaAnalytic) +
					"\t" + numberFormatDeviation.format(vega-vegaAnalytic) +
					"\t" + numberFormatDeviation.format(vegaPathwise-vegaAnalytic) +
					"\t" + numberFormatDeviation.format(vegaLikelihood-vegaAnalytic));

		}
		System.out.println("__________________________________________________________________________________________\n");
	}

	@Test
	public void testEuropeanCallRho() throws CalculationException
	{
		final MonteCarloBlackScholesModel blackScholesModel = (MonteCarloBlackScholesModel)model;

		// Java DecimalFormat for our output format
		final DecimalFormat numberFormatStrike	= new DecimalFormat("     0.00 ");
		final DecimalFormat numberFormatValue		= new DecimalFormat(" 0.00E00");
		final DecimalFormat numberFormatDeviation	= new DecimalFormat("  0.00E00; -0.00E00");

		final double initialValue	= blackScholesModel.getAssetValue(0.0, 0).get(0);
		final double riskFreeRate	= blackScholesModel.getModel().getRiskFreeRate().doubleValue();
		final double volatility	= blackScholesModel.getModel().getVolatility().doubleValue();

		final double optionMaturity	= 5.0;

		// Test options with different strike
		System.out.println("Calculation of Option Rho (European options with maturity " + optionMaturity + "):");
		System.out.println(" Strike \t MC Fin.Diff.\t MC Pathwise\t MC Likelihood\t Analytic \t Diff MC-FD \t Diff MC-PW \t Diff MC-LR");

		for(double optionStrike = 0.60; optionStrike < 1.50; optionStrike += 0.05) {

			// Create a product
			final EuropeanOption		callOption	= new EuropeanOption(optionMaturity, optionStrike);

			// Value the product with Monte Carlo
			final double shift = riskFreeRate * 1E-6;

			final Map<String,Object> dataUpShift = new HashMap<String,Object>();
			dataUpShift.put("riskFreeRate", riskFreeRate + shift);
			final double valueUpShift	= (Double)(callOption.getValuesForModifiedData(blackScholesModel, dataUpShift).get("value"));

			final Map<String,Object> dataDownShift = new HashMap<String,Object>();
			dataDownShift.put("riskFreeRate", riskFreeRate - shift);
			final double valueDownShift	= (Double)(callOption.getValuesForModifiedData(blackScholesModel, dataDownShift).get("value"));

			// Calculate the finite difference of the monte-carlo value
			final double rho = (valueUpShift-valueDownShift) / ( 2 * shift );

			// Calculate the finite difference of the analytic value
			final double rhoFiniteDiffAnalytic	=
					(
							net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate+shift, volatility, optionMaturity, optionStrike)
							- net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate-shift, volatility, optionMaturity, optionStrike)
							)/(2*shift);

			// Calculate the analytic value
			final double rhoAnalytic	= net.finmath.functions.AnalyticFormulas.blackScholesOptionRho(initialValue, riskFreeRate, volatility, optionMaturity, optionStrike);

			// Calculate the value using pathwise differentiation
			final EuropeanOptionRhoPathwise		callOptionRhoPathwise		= new EuropeanOptionRhoPathwise(optionMaturity, optionStrike);
			final double							rhoPathwise				= callOptionRhoPathwise.getValue(blackScholesModel);

			// Calculate the value using likelihood differentiation
			final EuropeanOptionRhoLikelihood		callOptionRhoLikelihood		= new EuropeanOptionRhoLikelihood(optionMaturity, optionStrike);
			final double							rhoLikelihood				= callOptionRhoLikelihood.getValue(blackScholesModel);

			// Print result
			System.out.println(numberFormatStrike.format(optionStrike) +
					"\t" + numberFormatValue.format(rho) +
					"\t" + numberFormatValue.format(rhoPathwise) +
					"\t" + numberFormatValue.format(rhoLikelihood) +
					"\t" + numberFormatValue.format(rhoAnalytic) +
					"\t" + numberFormatDeviation.format(rho-rhoAnalytic) +
					"\t" + numberFormatDeviation.format(rhoPathwise-rhoAnalytic) +
					"\t" + numberFormatDeviation.format(rhoLikelihood-rhoAnalytic));

		}
		System.out.println("__________________________________________________________________________________________\n");
	}

	@Test
	public void testEuropeanCallGamma() throws CalculationException
	{
		final MonteCarloBlackScholesModel blackScholesModel = (MonteCarloBlackScholesModel)model;

		// Java DecimalFormat for our output format
		final DecimalFormat numberFormatStrike	= new DecimalFormat("     0.00 ");
		final DecimalFormat numberFormatValue		= new DecimalFormat(" 0.00E00");
		final DecimalFormat numberFormatDeviation	= new DecimalFormat("  0.00E00; -0.00E00");

		final double initialValue	= blackScholesModel.getAssetValue(0.0, 0).get(0);
		final double riskFreeRate	= blackScholesModel.getModel().getRiskFreeRate().doubleValue();
		final double volatility	= blackScholesModel.getModel().getVolatility().doubleValue();

		// Test options with different strike
		System.out.println("Calculation of Option Gamma (European options with maturity 1.0):");
		System.out.println(" Strike \t MC Fin.Diff.\t MC Pathwise\t MC Likelihood\t Analytic \t Diff MC-FD \t Diff MC-PW \t Diff MC-LR");

		final double optionMaturity	= 5.0;
		for(double optionStrike = 0.60; optionStrike < 1.50; optionStrike += 0.05) {

			// Create a product
			final EuropeanOption		callOption	= new EuropeanOption(optionMaturity, optionStrike);

			// Value the product with Monte Carlo
			final double value	= callOption.getValue(blackScholesModel);

			// For gamma the shift has to be comparably large (otherwise FD of MC is very noisy). Try it.
			final double shift = initialValue * 1E-2;

			final Map<String,Object> dataUpShift = new HashMap<String,Object>();
			dataUpShift.put("initialValue", initialValue + shift);
			final double valueUpShift	= (Double)(callOption.getValuesForModifiedData(blackScholesModel, dataUpShift).get("value"));

			final Map<String,Object> dataDownShift = new HashMap<String,Object>();
			dataDownShift.put("initialValue", initialValue - shift);
			final double valueDownShift	= (Double)(callOption.getValuesForModifiedData(blackScholesModel, dataDownShift).get("value"));

			// Calculate the finite difference of the monte-carlo value
			final double gamma = (valueUpShift-2*value+valueDownShift) / (shift * shift);

			// Calculate the finite difference of the analytic value
			final double gammaFiniteDiffAnalytic	=
					(
							net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue+shift, riskFreeRate, volatility, optionMaturity, optionStrike)
							- 2 * net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, volatility, optionMaturity, optionStrike)
							+ net.finmath.functions.AnalyticFormulas.blackScholesOptionValue(initialValue-shift, riskFreeRate, volatility, optionMaturity, optionStrike)
							) / (shift * shift);

			// Calculate the analytic value
			final double gammaAnalytic	= net.finmath.functions.AnalyticFormulas.blackScholesOptionGamma(initialValue, riskFreeRate, volatility, optionMaturity, optionStrike);

			// Calculate the value using pathwise differentiation
			final EuropeanOptionGammaPathwise		callOptionGammaPathwise		= new EuropeanOptionGammaPathwise(optionMaturity, optionStrike);
			final double							gammaPathwise				= callOptionGammaPathwise.getValue(blackScholesModel);

			// Calculate the value using likelihood differentiation
			final EuropeanOptionGammaLikelihood		callOptionGammaLikelihood	= new EuropeanOptionGammaLikelihood(optionMaturity, optionStrike);
			final double								gammaLikelihood				= callOptionGammaLikelihood.getValue(blackScholesModel);

			// Print result
			System.out.println(numberFormatStrike.format(optionStrike) +
					"\t" + numberFormatValue.format(gamma) +
					"\t" + numberFormatValue.format(gammaPathwise) +
					"\t" + numberFormatValue.format(gammaLikelihood) +
					"\t" + numberFormatValue.format(gammaAnalytic) +
					"\t" + numberFormatDeviation.format(gamma-gammaAnalytic) +
					"\t" + numberFormatDeviation.format(gammaPathwise-gammaAnalytic) +
					"\t" + numberFormatDeviation.format(gammaLikelihood-gammaAnalytic));

		}
		System.out.println("__________________________________________________________________________________________\n");
	}
}
