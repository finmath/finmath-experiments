/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 10.02.2004
 */
package net.finmath.experiments.montecarlo.interestrates;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.finmath.exception.CalculationException;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterpolation;
import net.finmath.montecarlo.AbstractRandomVariableFactory;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.interestrate.CalibrationProduct;
import net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.LIBORMonteCarloSimulationFromLIBORModel;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelFromGivenMatrix;
import net.finmath.montecarlo.interestrate.products.BermudanSwaption;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.optimizer.LevenbergMarquardt;
import net.finmath.optimizer.SolverException;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * This class demonstates that a bermudan option with 5 in 10 and 10 in 5 not only depends on the swaptions
 * 5 in [0,10] and 10 in [0,5] but also on the swaption 5 in [5,10] - the forward start option with exericse in 5
 * swap start in 10 and swap end in 15.
 *
 * @author Christian Fries
 */
public class LIBORMarketModelBermudanValuationTest {

	private static DecimalFormat formatterParam		= new DecimalFormat(" #0.000000;-#0.000000", new DecimalFormatSymbols(Locale.ENGLISH));

	private final int numberOfPaths		= 2000;
	private final int numberOfFactors	= 1;

	private static DecimalFormat formatterMaturity	= new DecimalFormat("00.00", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterValue		= new DecimalFormat(" ##0.000%;-##0.000%", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterMoneyness	= new DecimalFormat(" 000.0%;-000.0%", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterDeviation	= new DecimalFormat(" 0.00000E00;-0.00000E00", new DecimalFormatSymbols(Locale.ENGLISH));


	public static void main(String args[]) throws CalculationException, SolverException {
		(new LIBORMarketModelBermudanValuationTest()).testBermudan();
	}

	public LIBORMarketModelBermudanValuationTest() throws CalculationException {}

	public static LIBORModelMonteCarloSimulationModel createLIBORMarketModel(
			AbstractRandomVariableFactory randomVariableFactory, int numberOfPaths, int numberOfFactors, double volatility1, double volatility2, double volatility3, double correlationDecayParam) throws CalculationException {

		/*
		 * Create the libor tenor structure and the initial values
		 */
		final double liborPeriodLength	= 0.5;
		final double liborRateTimeHorzion	= 20.0;
		final TimeDiscretizationFromArray liborPeriodDiscretization = new TimeDiscretizationFromArray(0.0, (int) (liborRateTimeHorzion / liborPeriodLength), liborPeriodLength);

		// Create the forward curve (initial value of the LIBOR market model)
		final ForwardCurveInterpolation forwardCurveInterpolation = ForwardCurveInterpolation.createForwardCurveFromForwards(
				"forwardCurve"								/* name of the curve */,
				new double[] {0.5 , 1.0 , 2.0 , 5.0 , 40.0}	/* fixings of the forward */,
				new double[] {0.05, 0.05, 0.05, 0.05, 0.05}	/* forwards */,
				liborPeriodLength							/* tenor / period length */
				);

		/*
		 * Create a simulation time discretization
		 */
		final double lastTime	= 20.0;
		final double dt		= 0.5;

		final TimeDiscretizationFromArray timeDiscretizationFromArray = new TimeDiscretizationFromArray(0.0, (int) (lastTime / dt), dt);

		/*
		 * Create a volatility structure v[i][j] = sigma_j(t_i)
		 */
		final double[][] volatility = new double[40][40];
		for(int timeIndex = 0; timeIndex<timeDiscretizationFromArray.getNumberOfTimeSteps(); timeIndex++) {
			for(int liborPeriodIndex = 0; liborPeriodIndex<liborPeriodDiscretization.getNumberOfTimeSteps(); liborPeriodIndex++) {
				final double time = timeDiscretizationFromArray.getTime(timeIndex);
				final double liborPeriod = liborPeriodDiscretization.getTime(liborPeriodIndex);
				double vola = 0.0;

				if(liborPeriod >= 5 && liborPeriod < 10) {
					if(time < 5) {
						vola = volatility1;
					}
				}
				else if(liborPeriod > 10) {
					if(time < 5) {
						vola = volatility2;
					} else if(time > 5 && time < 10) {
						vola = volatility3;
					}
				}

				volatility[timeIndex][liborPeriodIndex] = vola;
			}
		}
		final boolean isCalibrateable = false;
		final LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelFromGivenMatrix(randomVariableFactory, timeDiscretizationFromArray, liborPeriodDiscretization, volatility);

		/*
		 * Create a correlation model rho_{i,j} = exp(-a * abs(T_i-T_j))
		 */
		final LIBORCorrelationModelExponentialDecay correlationModel = new LIBORCorrelationModelExponentialDecay(
				timeDiscretizationFromArray, liborPeriodDiscretization, numberOfFactors,
				correlationDecayParam);


		/*
		 * Combine volatility model and correlation model to a covariance model
		 */
		final LIBORCovarianceModelFromVolatilityAndCorrelation covarianceModel =
				new LIBORCovarianceModelFromVolatilityAndCorrelation(timeDiscretizationFromArray,
						liborPeriodDiscretization, volatilityModel, correlationModel);

		// BlendedLocalVolatlityModel (future extension)
		//		AbstractLIBORCovarianceModel covarianceModel2 = new BlendedLocalVolatlityModel(covarianceModel, 0.00, false);

		// Set model properties
		final Map<String, String> properties = new HashMap<>();

		// Choose the simulation measure
		properties.put("measure", LIBORMarketModelFromCovarianceModel.Measure.SPOT.name());

		// Choose log normal model
		properties.put("stateSpace", LIBORMarketModelFromCovarianceModel.StateSpace.NORMAL.name());

		// Empty array of calibration items - hence, model will use given covariance
		final CalibrationProduct[] calibrationItems = new CalibrationProduct[0];

		/*
		 * Create corresponding LIBOR Market Model
		 */
		final LIBORMarketModelFromCovarianceModel liborMarketModel = new LIBORMarketModelFromCovarianceModel(liborPeriodDiscretization, null /* analyticModel */, forwardCurveInterpolation, new DiscountCurveFromForwardCurve(forwardCurveInterpolation), randomVariableFactory, covarianceModel, calibrationItems, properties);

		final BrownianMotion brownianMotionLazyInit = new BrownianMotionFromMersenneRandomNumbers(timeDiscretizationFromArray, numberOfFactors, numberOfPaths, 3141 /* seed */);

		final EulerSchemeFromProcessModel process = new EulerSchemeFromProcessModel(liborMarketModel, brownianMotionLazyInit, EulerSchemeFromProcessModel.Scheme.PREDICTOR_CORRECTOR);

		return new LIBORMonteCarloSimulationFromLIBORModel(process);
	}

	public void testBermudan() throws CalculationException, SolverException {
		final AbstractRandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();
		final double volatilityRef = 40.0 / 100.0 / 100.0;
		final double correlationDecayParam = 0.0;

		final boolean[] isPeriodStartDateExerciseDate  = { true, false, false, false, false, true, false, false, false, false };
		final boolean[] isPeriodStartDateExerciseDate1  = { true, false, false, false, false, false, false, false, false, false };
		final boolean[] isPeriodStartDateExerciseDate2  = { false, false, false, false, false, true, false, false, false, false };
		final double[] fixingDates = { 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0 };
		final double[] periodLength = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 };
		final double[] paymentDates = { 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0 };
		final double[] periodNotionals  = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 };
		final double[] swaprates = { 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05 };

		final LIBORModelMonteCarloSimulationModel lmmref = createLIBORMarketModel(randomVariableFactory, numberOfPaths, numberOfFactors, volatilityRef, volatilityRef, volatilityRef, correlationDecayParam);

		final double[] periodNotionals2  = { 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0 };
		final BermudanSwaption bermudan = new BermudanSwaption(isPeriodStartDateExerciseDate, fixingDates, periodLength, paymentDates, periodNotionals, swaprates );
		final BermudanSwaption swaption1 = new BermudanSwaption(isPeriodStartDateExerciseDate1, fixingDates, periodLength, paymentDates, periodNotionals, swaprates );
		final BermudanSwaption swaption2 = new BermudanSwaption(isPeriodStartDateExerciseDate2, fixingDates, periodLength, paymentDates, periodNotionals, swaprates );
		final BermudanSwaption swaption3 = new BermudanSwaption(isPeriodStartDateExerciseDate1, fixingDates, periodLength, paymentDates, periodNotionals2, swaprates );

		final double swaption1TargetValue = swaption1.getValue(lmmref);
		final double swaption2TargetValue = swaption2.getValue(lmmref);

		for(double volatilityInBp = 20; volatilityInBp < 50; volatilityInBp +=1) {
			final double volatility2 = volatilityInBp / 100.0 / 100.0;
			double volatility1 = Math.sqrt(volatilityRef*volatilityRef*2-volatility2*volatility2);
			double volatility3 = Math.sqrt(volatilityRef*volatilityRef*2-volatility2*volatility2);

			final LevenbergMarquardt lmOptimizer = new LevenbergMarquardt(new double[] { volatility1, volatility3 }, new double[] { swaption1TargetValue, swaption2TargetValue }, 100, 2) {

				@Override
				public void setValues(double[] parameters, double[] values) throws SolverException {
					try {
						final LIBORModelMonteCarloSimulationModel lmm = createLIBORMarketModel(randomVariableFactory, numberOfPaths, numberOfFactors, parameters[0], volatility2, parameters[1], correlationDecayParam);
						values[0] = swaption1.getValue(lmm);
						values[1] = swaption2.getValue(lmm);
					} catch (final CalculationException e) { }

				}

			};
			lmOptimizer.run();
			volatility1 = lmOptimizer.getBestFitParameters()[0];
			volatility3 = lmOptimizer.getBestFitParameters()[1];
			final LIBORModelMonteCarloSimulationModel lmm = createLIBORMarketModel(randomVariableFactory, numberOfPaths, numberOfFactors, volatility1, volatility2, volatility3, correlationDecayParam);

			final double value = bermudan.getValue(lmm);
			final double valueSwaption1 = swaption1.getValue(lmm);
			final double valueSwaption2 = swaption2.getValue(lmm);
			final double valueSwaption3 = swaption3.getValue(lmm);

			System.out.println(volatilityInBp + "\t" + formatterValue.format(value) + "\t" + formatterValue.format(valueSwaption1) + "\t" + formatterValue.format(valueSwaption2) + "\t" + formatterValue.format(valueSwaption3) + "\t" + formatterValue.format(volatility1) + "\t" + formatterValue.format(volatility2) + "\t" + formatterValue.format(volatility3) );
		}
	}
}
