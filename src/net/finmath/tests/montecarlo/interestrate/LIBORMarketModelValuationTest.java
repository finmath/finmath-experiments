/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 10.02.2004
 */
package net.finmath.tests.montecarlo.interestrate;

import static org.junit.Assert.assertTrue;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterface;
import net.finmath.montecarlo.interestrate.LIBORMarketModel;
import net.finmath.montecarlo.interestrate.LIBORMarketModel.CalibrationItem;
import net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulation;
import net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulationInterface;
import net.finmath.montecarlo.interestrate.modelplugins.AbstractLIBORCovarianceModelParametric;
import net.finmath.montecarlo.interestrate.modelplugins.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.modelplugins.LIBORCovarianceModelExponentialForm7Param;
import net.finmath.montecarlo.interestrate.modelplugins.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.modelplugins.LIBORVolatilityModelFromGivenMatrix;
import net.finmath.montecarlo.interestrate.products.AbstractLIBORMonteCarloProduct;
import net.finmath.montecarlo.interestrate.products.Bond;
import net.finmath.montecarlo.interestrate.products.Swap;
import net.finmath.montecarlo.interestrate.products.Swaption;
import net.finmath.montecarlo.interestrate.products.SwaptionAnalyticApproximation;
import net.finmath.montecarlo.process.ProcessEulerScheme;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationInterface;

import org.junit.Test;

/**
 * This class tests the libor market model and products.
 * 
 * @author Christian Fries
 */
public class LIBORMarketModelValuationTest {

	private boolean isUnitTests = true;
	
	private final int numberOfPaths		= 10000;
	private final int numberOfFactors	= 5;
	
	private LIBORModelMonteCarloSimulationInterface liborMarketModel; 
	
	private static DecimalFormat formatterMaturity	= new DecimalFormat("00.00", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterPrice		= new DecimalFormat(" ##0.000%;-##0.000%", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterDeviation	= new DecimalFormat(" 0.00000E00;-0.00000E00", new DecimalFormatSymbols(Locale.ENGLISH));

	public static void main(String[] args) {

		long start = System.currentTimeMillis();

		LIBORMarketModelValuationTest liborMarketModelValuationTest = new LIBORMarketModelValuationTest();

		// Disable assertions of unit testing
		liborMarketModelValuationTest.setUnitTests(false);

		try {
			// Run bond valuation testing on this model
			liborMarketModelValuationTest.testBond();

			// Run swap valuation testing on this model
			liborMarketModelValuationTest.testSwap();

			// Run swaption valuation testing on this model
			liborMarketModelValuationTest.testSwaption();

			// Run swaption calibration on this model
			liborMarketModelValuationTest.testSwaptionCalibration();

			// Run swaption price testing on this model
			// bermudanSwaptionPriceTest();

			// flexiCapTest();
			// tarnPriceTest();
		} catch (CalculationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		long end = System.currentTimeMillis();
		System.out.println("Calculation Time: " + (double) (end - start) / 1000.0 + "sec.");
	}

	public LIBORMarketModelValuationTest() {

		// Create a libor market model
		liborMarketModel = createLIBORMarketModel(numberOfPaths, numberOfFactors, 0.1 /* Correlation */);
	}

	public void setUnitTests(boolean isUnitTests) {
		this.isUnitTests = isUnitTests;
	}

	public static LIBORModelMonteCarloSimulationInterface createLIBORMarketModel(
	        int numberOfPaths, int numberOfFactors, double correlationDecayParam) {

		/*
		 * Create the libor tenor structure and the initial values
		 */
		double liborPeriodLength = 0.5;
		double liborRateTimeHorzion = 20.0;
		TimeDiscretization liborPeriodDiscretization = new TimeDiscretization(0.0, (int) (liborRateTimeHorzion / liborPeriodLength), liborPeriodLength);

		// Create initial guess for the curve
		ForwardCurve forwardCurve = ForwardCurve.createForwardCurveFromForwards("forwardCurve", new double[] {0.5, 1.0, 2.0, 5.0, 40.0}, new double[] {0.05, 0.05, 0.05, 0.05, 0.05}, 0.5);

		/*
		 * Create a simulation time discretization
		 */
		double lastTime = 20.0;
		double dt = 0.5;

		TimeDiscretization timeDiscretization = new TimeDiscretization(0.0, (int) (lastTime / dt), dt);

		/*
		 * Create a volatility structure v[i][j] = sigma_j(t_i)
		 */
		double[][] volatility = new double[timeDiscretization
		        .getNumberOfTimeSteps()][liborPeriodDiscretization
		        .getNumberOfTimeSteps()];
		for (int timeIndex = 0; timeIndex < volatility.length; timeIndex++) {
			for (int liborIndex = 0; liborIndex < volatility[timeIndex].length; liborIndex++) {
				// Create a very simple volatility model here
				double time = timeDiscretization.getTime(timeIndex);
				double maturity = liborPeriodDiscretization.getTime(liborIndex);
				double timeToMaturity = maturity - time;

				double instVolatility;
				if(timeToMaturity <= 0)
					instVolatility = 0;				// This forward rate is already fixed, no volatility
				else
					instVolatility = 0.1; // + 0.0 * Math.exp(-0.5 * timeToMaturity);

				// Store
				volatility[timeIndex][liborIndex] = instVolatility;
			}
		}

		/*
		 * Create a correlation model rho_{i,j} = exp(-a * abs(T_i-T_j))
		 */
		LIBORCorrelationModelExponentialDecay correlationModel = new LIBORCorrelationModelExponentialDecay(
		        timeDiscretization, liborPeriodDiscretization, numberOfFactors,
		        correlationDecayParam);


		/*
		 * Combine volatility model and corrleation model to a covariance model
		 */
		LIBORCovarianceModelFromVolatilityAndCorrelation covarianceModel =
		 new
		LIBORCovarianceModelFromVolatilityAndCorrelation(timeDiscretization,
		liborPeriodDiscretization,new
		LIBORVolatilityModelFromGivenMatrix(timeDiscretization,
		
				liborPeriodDiscretization, volatility), correlationModel);

		/*
		 * Create corresponding LIBOR Market Model
		 */
		LIBORMarketModel liborMarketModel = new LIBORMarketModel(
		        liborPeriodDiscretization, forwardCurve, covarianceModel);

		// XXX1 Change measure here

		// Choose the simulation measure
		liborMarketModel.setMeasure(LIBORMarketModel.Measure.SPOT);
		// liborMarketModel2.setDriftAproximationMethod(LIBORMarketModel.DRIFTAPROXIMATION_PREDICTOR_CORRECTOR);

		ProcessEulerScheme process = new ProcessEulerScheme(
		        new net.finmath.montecarlo.BrownianMotion(timeDiscretization,
		                numberOfFactors, numberOfPaths, 3141 /* seed */));
//		lnp.setScheme(ProcessEulerScheme.Scheme.PREDICTOR_CORRECTOR);

		return new LIBORModelMonteCarloSimulation(liborMarketModel, process);
	}

	@Test
	public void testBond() throws CalculationException {
		/*
		 * Value a bond
		 */

		System.out.println("Bond prices:\n");
		System.out.println("Maturity      Simulation       Analytic        Deviation");

		for (int maturityIndex = 0; maturityIndex <= liborMarketModel.getNumberOfLibors(); maturityIndex++) {
			double maturity = liborMarketModel.getLiborPeriod(maturityIndex);
			System.out.print(formatterMaturity.format(maturity) + "          ");

			// Create a bond
			Bond bond = new Bond(maturity);

			// Bond price with Monte Carlo
			double priceOfBond = bond.getValue(liborMarketModel);
			System.out.print(formatterPrice.format(priceOfBond) + "          ");

			// Bond price analytic
			double priceOfBondAnalytic = 1.0;

			double lastPeriodIndex = liborMarketModel.getLiborPeriodIndex(bond
			        .getMaturity()) - 1;
			for (int periodIndex = 0; periodIndex <= lastPeriodIndex; periodIndex++)
				priceOfBondAnalytic /= 1 + liborMarketModel.getLIBOR(0, periodIndex).get(0)
				        * (liborMarketModel.getLiborPeriod(periodIndex + 1) - liborMarketModel.getLiborPeriod(periodIndex));

			System.out.print(formatterPrice.format(priceOfBondAnalytic) + "          ");

			// Relative deviation
			double deviation = (priceOfBond - priceOfBondAnalytic);
			System.out.println(formatterDeviation.format(deviation));
			
			
			if(isUnitTests) assertTrue(Math.abs(deviation) < 1E-03);
		}
		System.out.println("__________________________________________________________________________________________\n");
	}

	@Test
	public void testSwap() throws CalculationException {
		/*
		 * Price a bond
		 */
		System.out.println("Par-Swap prices:\n");
		System.out.println("Swap \t\t\t Value");

		for (int maturityIndex = 1; maturityIndex <= liborMarketModel.getNumberOfLibors() - 10; maturityIndex++) {

			double startDate = liborMarketModel.getLiborPeriod(maturityIndex);


			int numberOfPeriods = 5;

			// Create a swap
			double[]	fixingDates			= new double[numberOfPeriods];
			double[]	paymentDates		= new double[numberOfPeriods];
			double[]	swapTenor			= new double[numberOfPeriods + 1];
			double		swapPeriodLength	= 1.0;

			for (int periodStartIndex = 0; periodStartIndex < numberOfPeriods; periodStartIndex++) {
				fixingDates[periodStartIndex]	= startDate + periodStartIndex * swapPeriodLength;
				paymentDates[periodStartIndex]	= startDate + (periodStartIndex + 1) * swapPeriodLength;
				swapTenor[periodStartIndex]		= startDate + periodStartIndex * swapPeriodLength;
			}
			swapTenor[numberOfPeriods] = startDate + numberOfPeriods * swapPeriodLength;

			System.out.print("(" + formatterMaturity.format(swapTenor[0]) + "," + formatterMaturity.format(swapTenor[numberOfPeriods-1]) + "," + swapPeriodLength + ")" + "\t");
			
			// Par swap rate
			double swaprate = getParSwaprate(liborMarketModel, swapTenor);

			// Set swap rates for each period
			double[] swaprates = new double[numberOfPeriods];
			for (int periodStartIndex = 0; periodStartIndex < numberOfPeriods; periodStartIndex++) {
				swaprates[periodStartIndex] = swaprate;
			}

			// Create sa swap
			Swap swap = new Swap(fixingDates, paymentDates, swaprates);
			
			// Vaue the swap
			double value = swap.getValue(liborMarketModel);
			System.out.print(formatterPrice.format(value) + "\n");

			// The swap should be at par (close to zero)
			if(isUnitTests) assertTrue(Math.abs(value) < 1E-3);
		}
		System.out.println("__________________________________________________________________________________________\n");
	}

	@Test
	public void testSwaption() throws CalculationException {
		/*
		 * Value a swaption
		 */
		System.out.println("Swaption prices:\n");
		System.out.println("Maturity      Simulation 1     Analytic        Deviation");

		for (int maturityIndex = 1; maturityIndex <= liborMarketModel.getNumberOfLibors() - 10; maturityIndex++) {

			double exerciseDate = liborMarketModel.getLiborPeriod(maturityIndex);
			System.out.print(formatterMaturity.format(exerciseDate) + "          ");

			int numberOfPeriods = 5;

			// Create a swaption

			double[] fixingDates = new double[numberOfPeriods];
			double[] paymentDates = new double[numberOfPeriods];
			double[] swapTenor = new double[numberOfPeriods + 1];
			double swapPeriodLength = 0.5;

			for (int periodStartIndex = 0; periodStartIndex < numberOfPeriods; periodStartIndex++) {
				fixingDates[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
				paymentDates[periodStartIndex] = exerciseDate + (periodStartIndex + 1) * swapPeriodLength;
				swapTenor[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
			}
			swapTenor[numberOfPeriods] = exerciseDate + numberOfPeriods * swapPeriodLength;

			// Swaptions swap rate
			double swaprate = 0.05;// getParSwaprate(liborMarketModel, swapTenor);

			// Set swap rates for each period
			double[] swaprates = new double[numberOfPeriods];
			for (int periodStartIndex = 0; periodStartIndex < numberOfPeriods; periodStartIndex++) {
				swaprates[periodStartIndex] = swaprate;
			}

			Swaption swaptionMonteCarlo = new Swaption(exerciseDate, fixingDates, paymentDates, swaprates);
			SwaptionAnalyticApproximation swaptionAnalyitc = new SwaptionAnalyticApproximation(
			        swaprate, swapTenor,
			        SwaptionAnalyticApproximation.ValueUnit.VALUE);

			// Value with Monte Carlo
			double valueSimulation = swaptionMonteCarlo.getValue(liborMarketModel);
			System.out.print(formatterPrice.format(valueSimulation) + "          ");

			// Value analytic
			double valueAnalytic = swaptionAnalyitc.getValue(liborMarketModel);
			System.out.print(formatterPrice.format(valueAnalytic) + "          ");

			// Relative deviation
			double deviation1 = (valueSimulation - valueAnalytic);
			System.out.println(formatterDeviation.format(deviation1) + "          ");
		}
		System.out.println("__________________________________________________________________________________________\n");
	}

	@Test
	public void testSwaptionCalibration() throws CalculationException {

		/*
		 * Calibration test
		 */
		System.out.println("Calibration to Swaptions:");

		
		/*
		 * Create a set of calibration products.
		 */
		ArrayList<CalibrationItem> calibrationItems = new ArrayList<CalibrationItem>();
		for (int exerciseIndex = 4; exerciseIndex <= liborMarketModel.getNumberOfLibors() - 5; exerciseIndex+=4) {
			double exerciseDate = liborMarketModel.getLiborPeriod(exerciseIndex);
			for (int numberOfPeriods = 1; numberOfPeriods < liborMarketModel.getNumberOfLibors() - exerciseIndex - 5; numberOfPeriods+=4) {

				// Create a swaption

				double[]	fixingDates			= new double[numberOfPeriods];
				double[]	paymentDates		= new double[numberOfPeriods];
				double[]	swapTenor			= new double[numberOfPeriods + 1];
				double		swapPeriodLength	= 0.5;

				for (int periodStartIndex = 0; periodStartIndex < numberOfPeriods; periodStartIndex++) {
					fixingDates[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
					paymentDates[periodStartIndex] = exerciseDate + (periodStartIndex + 1) * swapPeriodLength;
					swapTenor[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
				}
				swapTenor[numberOfPeriods] = exerciseDate + numberOfPeriods * swapPeriodLength;

				// Swaptions swap rate
				double swaprate = getParSwaprate(liborMarketModel,swapTenor);

				// Set swap rates for each period
				double[] swaprates = new double[numberOfPeriods];
				Arrays.fill(swaprates, swaprate);

				SwaptionAnalyticApproximation swaptionAnalytic = new SwaptionAnalyticApproximation(swaprate, swapTenor, SwaptionAnalyticApproximation.ValueUnit.VOLATILITY);

				// This is just some swaption volatility used for testing, true market data shouold go here.
				double targetValueVolatilty = 0.20 + 0.20 * Math.exp(-exerciseDate / 10.0) + 0.20 * Math.exp(-(exerciseDate+numberOfPeriods) / 10.0);

				// You may also use full Monte-Carlo calibration
				Swaption swaptionMonteCarlo = new Swaption(exerciseDate, fixingDates, paymentDates, swaprates);
				double targetValuePrice = AnalyticFormulas.blackModelSwaptionValue(swaprate, targetValueVolatilty, fixingDates[0], swaprate, getSwapAnnuity(liborMarketModel,swapTenor));

				
				// XXX1: Change the calibration product here
				calibrationItems.add(new CalibrationItem(swaptionAnalytic, targetValueVolatilty, 1.0));
			}
		}
		System.out.println("");

		/*
		 * Take discretization and forward curve from liborMarketModel
		 */
		TimeDiscretizationInterface timeDiscretization = liborMarketModel.getTimeDiscretization();

		ForwardCurveInterface forwardCurve = ((LIBORMarketModel)liborMarketModel.getModel()).getForwardRateCurve();

		/*
		 * Create a LIBOR Market Model
		 */

		// XXX2 Change covariance model here
		AbstractLIBORCovarianceModelParametric covarianceModelParametric = new LIBORCovarianceModelExponentialForm7Param(timeDiscretization, liborMarketModel.getLiborPeriodDiscretization(), liborMarketModel.getNumberOfFactors());

		LIBORMarketModel liborMarketModelCalibrated = new LIBORMarketModel(
				this.liborMarketModel.getLiborPeriodDiscretization(),
				forwardCurve, covarianceModelParametric, calibrationItems.toArray(new CalibrationItem[0]));	

		
		/*
		 * Test our calibration
		 */
		final int numberOfPaths = 10000;
		final int numberOfFactors = liborMarketModel.getNumberOfFactors();

		ProcessEulerScheme process = new ProcessEulerScheme(
		        new net.finmath.montecarlo.BrownianMotion(timeDiscretization,
		                numberOfFactors, numberOfPaths, 3141 /* seed */));
		process.setScheme(ProcessEulerScheme.Scheme.PREDICTOR_CORRECTOR);

		net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulation calMode = new net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulation(
				liborMarketModelCalibrated, process);

		double[] param = ((AbstractLIBORCovarianceModelParametric) liborMarketModelCalibrated.getCovarianceModel()).getParameter();
		for (double p : param) System.out.println(p);

		double deviationSum = 0.0;
		for (int i = 0; i < calibrationItems.size(); i++) {
			AbstractLIBORMonteCarloProduct calibrationProduct = calibrationItems.get(i).calibrationProduct;
			double valueModel = calibrationProduct.getValue(calMode);
			double valueTarget = calibrationItems.get(i).calibrationTargetValue;
			deviationSum += (valueModel-valueTarget);
			System.out.println("Model: " + formatterPrice.format(valueModel) + "\t Target: " + formatterPrice.format(valueTarget) + "\t Deviation: " + formatterDeviation.format(valueModel-valueTarget));
		}
		System.out.println("Mean Deviation:" + deviationSum/calibrationItems.size());
	}

	private static double getParSwaprate(LIBORModelMonteCarloSimulationInterface liborMarketModel, double[] swapTenor) throws CalculationException {
		double swapStart = swapTenor[0];
		double swapEnd = swapTenor[swapTenor.length - 1];

		int swapStartIndex = liborMarketModel.getLiborPeriodIndex(swapStart);
		int swapEndIndex = liborMarketModel.getLiborPeriodIndex(swapEnd);

		// Calculate discount factors from model
		double[] discountFactors = new double[swapEndIndex + 1];
		discountFactors[0] = 1.0;
		for (int periodIndex = 0; periodIndex < swapEndIndex; periodIndex++) {
			double libor = liborMarketModel.getLIBOR(0, periodIndex).get(0);
			double periodLength = liborMarketModel
			        .getLiborPeriod(periodIndex + 1)
			        - liborMarketModel.getLiborPeriod(periodIndex);
			discountFactors[periodIndex + 1] = discountFactors[periodIndex]
			        / (1.0 + libor * periodLength);

		}

		// Calculate swap annuity from discount factors
		double swapAnnuity = 0.0;
		for (int swapPeriodIndex = 0; swapPeriodIndex < swapTenor.length - 1; swapPeriodIndex++) {
			int periodEndIndex = liborMarketModel
			        .getLiborPeriodIndex(swapTenor[swapPeriodIndex + 1]);
			swapAnnuity += discountFactors[periodEndIndex]
			        * (swapTenor[swapPeriodIndex + 1] - swapTenor[swapPeriodIndex]);
		}

		// Calculate swaprate
		double swaprate = (discountFactors[swapStartIndex] - discountFactors[swapEndIndex])
		        / swapAnnuity;
		;

		return swaprate;
	}

	private static double getSwapAnnuity(LIBORModelMonteCarloSimulationInterface liborMarketModel, double[] swapTenor) throws CalculationException {
		double swapStart = swapTenor[0];
		double swapEnd = swapTenor[swapTenor.length - 1];

		int swapStartIndex = liborMarketModel.getLiborPeriodIndex(swapStart);
		int swapEndIndex = liborMarketModel.getLiborPeriodIndex(swapEnd);

		// Calculate discount factors from model
		double[] discountFactors = new double[swapEndIndex + 1];
		discountFactors[0] = 1.0;
		for (int periodIndex = 0; periodIndex < swapEndIndex; periodIndex++) {
			double libor = liborMarketModel.getLIBOR(0, periodIndex).get(0);
			double periodLength = liborMarketModel
			        .getLiborPeriod(periodIndex + 1)
			        - liborMarketModel.getLiborPeriod(periodIndex);
			discountFactors[periodIndex + 1] = discountFactors[periodIndex]
			        / (1.0 + libor * periodLength);

		}

		// Calculate swap annuity from discount factors
		double swapAnnuity = 0.0;
		for (int swapPeriodIndex = 0; swapPeriodIndex < swapTenor.length - 1; swapPeriodIndex++) {
			int periodEndIndex = liborMarketModel
			        .getLiborPeriodIndex(swapTenor[swapPeriodIndex + 1]);
			swapAnnuity += discountFactors[periodEndIndex]
			        * (swapTenor[swapPeriodIndex + 1] - swapTenor[swapPeriodIndex]);
		}

		return swapAnnuity;
	}
}