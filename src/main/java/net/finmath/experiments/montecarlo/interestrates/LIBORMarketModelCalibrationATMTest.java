/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 16.01.2015
 */
package net.finmath.experiments.montecarlo.interestrates;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.junit.Assert;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.marketdata.calibration.ParameterObject;
import net.finmath.marketdata.calibration.Solver;
import net.finmath.marketdata.model.AnalyticModel;
import net.finmath.marketdata.model.AnalyticModelFromCurvesAndVols;
import net.finmath.marketdata.model.curves.Curve;
import net.finmath.marketdata.model.curves.CurveInterpolation.ExtrapolationMethod;
import net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationEntity;
import net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationMethod;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.DiscountCurveInterpolation;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveFromDiscountCurve;
import net.finmath.marketdata.products.AnalyticProduct;
import net.finmath.marketdata.products.Swap;
import net.finmath.marketdata.products.SwapAnnuity;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.interestrate.CalibrationProduct;
import net.finmath.montecarlo.interestrate.LIBORMarketModel;
import net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.LIBORMonteCarloSimulationFromLIBORModel;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.AbstractLIBORCovarianceModelParametric;
import net.finmath.montecarlo.interestrate.models.covariance.DisplacedLocalVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelPiecewiseConstant;
import net.finmath.montecarlo.interestrate.products.AbstractTermStructureMonteCarloProduct;
import net.finmath.montecarlo.interestrate.products.SwaptionGeneralizedAnalyticApproximation;
import net.finmath.montecarlo.interestrate.products.SwaptionSimple;
import net.finmath.montecarlo.interestrate.products.TermStructureMonteCarloProduct;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.optimizer.LevenbergMarquardt.RegularizationMethod;
import net.finmath.optimizer.OptimizerFactory;
import net.finmath.optimizer.OptimizerFactoryLevenbergMarquardt;
import net.finmath.optimizer.SolverException;
import net.finmath.time.Schedule;
import net.finmath.time.ScheduleGenerator;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarExcludingTARGETHolidays;
import net.finmath.time.daycount.DayCountConvention_ACT_365;

/**
 * This class does some experiments the LIBOR market model calibration.
 *
 * @author Christian Fries
 */
public class LIBORMarketModelCalibrationATMTest {


	public enum LIBORMarketModelType {
		NORMAL,
		DISPLACED,
	}

	public enum CalibrationProductType {
		MONTECARLO,
		ANALYTIC,
	}

	private static final boolean isPrintResults = false;
	private static final boolean isPrintResultsForCurves = false;

	private static DecimalFormat formatterValue		= new DecimalFormat(" ##0.000%;-##0.000%", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterParam		= new DecimalFormat(" #0.000;-#0.000", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterDeviation	= new DecimalFormat(" 0.00000E00;-0.00000E00", new DecimalFormatSymbols(Locale.ENGLISH));

	private final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();
	//	private final RandomVariableFactory randomVariableFactory = new RandomVariableOpenCLFactory();
	//	private final RandomVariableFactory randomVariableFactory = new RandomVariableCudaFactory();

	private final LIBORMarketModelType modelType;
	private final CalibrationProductType calibrationProductType;
	private final int numberOfPathsCalibration;
	private final int numberOfPathsBenchmark;

	public static void main(String[] args) throws Exception {
		
		/*
		 * You may modify the number of path (e.g. divide all by 10), depending on your machine.
		 * Also: the last run may need more memory: Use -Xmx12G as JVM option to run with 12 GB.
		 */
		
		(new LIBORMarketModelCalibrationATMTest(LIBORMarketModelType.NORMAL, CalibrationProductType.ANALYTIC, 1000 /* numberOfPathsCalibration */, 1000 /* numberOfPathBenchmark */)).testATMSwaptionCalibration();
		(new LIBORMarketModelCalibrationATMTest(LIBORMarketModelType.NORMAL, CalibrationProductType.MONTECARLO, 1000 /* numberOfPathsCalibration */, 1000 /* numberOfPathBenchmark */)).testATMSwaptionCalibration();
		(new LIBORMarketModelCalibrationATMTest(LIBORMarketModelType.NORMAL, CalibrationProductType.MONTECARLO, 1000 /* numberOfPathsCalibration */, 10000 /* numberOfPathBenchmark */)).testATMSwaptionCalibration();
		(new LIBORMarketModelCalibrationATMTest(LIBORMarketModelType.NORMAL, CalibrationProductType.ANALYTIC, 1000 /* numberOfPathsCalibration */, 10000 /* numberOfPathBenchmark */)).testATMSwaptionCalibration();
		(new LIBORMarketModelCalibrationATMTest(LIBORMarketModelType.NORMAL, CalibrationProductType.MONTECARLO, 10000 /* numberOfPathsCalibration */, 50000 /* numberOfPathBenchmark */)).testATMSwaptionCalibration();
		(new LIBORMarketModelCalibrationATMTest(LIBORMarketModelType.NORMAL, CalibrationProductType.ANALYTIC, 10000 /* numberOfPathsCalibration */, 50000 /* numberOfPathBenchmark */)).testATMSwaptionCalibration();
	}

	public LIBORMarketModelCalibrationATMTest(LIBORMarketModelType modelType, CalibrationProductType calibrationProductType, int numberOfPathsCalibration, int numberOfPathBenchmark) {
		super();
		this.modelType = modelType;
		this.calibrationProductType = calibrationProductType;
		this.numberOfPathsCalibration = numberOfPathsCalibration;
		this.numberOfPathsBenchmark = numberOfPathBenchmark;
	}

	/**
	 * Calibration of swaptions - using Brute force Monte-Carlo or Analytic approximation - depending on the calibrationProductType.
	 *
	 * @throws CalculationException Thrown if the model fails to calibrate.
	 * @throws SolverException Thrown if the solver fails to find a solution.
	 */
	public void testATMSwaptionCalibration() throws CalculationException, SolverException {

		/*
		 * Calibration test
		 */
		System.out.println("Calibration to Swaptions:");
		System.out.println("\tModel..........................: " + modelType);
		System.out.println("\tCalibration products...........: " + calibrationProductType);
		System.out.println("\tNumber of path (calibration)...: " + numberOfPathsCalibration);
		System.out.println("\tNumber of path (benchmarking)..: " + numberOfPathsBenchmark);

		/*
		 * Calibration of rate curves
		 */
		System.out.print("\nCalibration of rate curves...");

		final long millisCurvesStart = System.currentTimeMillis();
		final AnalyticModel curveModel = getCalibratedCurve();

		// Create the forward curve (initial value of the LIBOR market model)
		final ForwardCurve forwardCurve = curveModel.getForwardCurve("ForwardCurveFromDiscountCurve(discountCurve-EUR,6M)");
		final DiscountCurve discountCurve = curveModel.getDiscountCurve("discountCurve-EUR");

		final long millisCurvesEnd = System.currentTimeMillis();
		System.out.println("done (" + (millisCurvesEnd-millisCurvesStart)/1000.0 + " sec).");

		/*
		 * Calibration of model volatilities
		 */

		/*
		 * Create a set of calibration products.
		 */
		final ArrayList<String>				calibrationItemNames	= new ArrayList<>();
		final ArrayList<CalibrationProduct>	calibrationProducts		= new ArrayList<>();
		final ArrayList<CalibrationProduct>	calibrationBenchmarks	= new ArrayList<>();
		final ArrayList<CalibrationProduct>	calibrationMonteCarloValue		= new ArrayList<>();

		final double	swapPeriodLength	= 0.5;

		final String[] atmExpiries = {
				"1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "1M", "3M", "3M", "3M",
				"3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "3M", "6M", "6M", "6M", "6M", "6M", "6M",
				"6M", "6M", "6M", "6M", "6M", "6M", "6M", "6M", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y", "1Y",
				"1Y", "1Y", "1Y", "1Y", "1Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y", "2Y",
				"2Y", "2Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "3Y", "4Y",
				"4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "4Y", "5Y", "5Y", "5Y", "5Y",
				"5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "5Y", "7Y", "7Y", "7Y", "7Y", "7Y", "7Y", "7Y",
				"7Y", "7Y", "7Y", "7Y", "7Y", "7Y", "7Y", "10Y", "10Y", "10Y", "10Y", "10Y", "10Y", "10Y", "10Y", "10Y",
				"10Y", "10Y", "10Y", "10Y", "10Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y", "15Y",
				"15Y", "15Y", "15Y", "15Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y", "20Y",
				"20Y", "20Y", "20Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y", "25Y",
				"25Y", "25Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y", "30Y",
		"30Y" };

		final String[] atmTenors = {
				"1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y",
				"3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y",
				"5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y",
				"7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y",
				"9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y",
				"15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y",
				"25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y",
				"1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y",
				"3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y",
				"5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y",
				"7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y",
				"9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y",
				"15Y", "20Y", "25Y", "30Y" };

		final double[] atmNormalVolatilities = {
				0.00151, 0.00169, 0.0021, 0.00248, 0.00291, 0.00329, 0.00365, 0.004, 0.00437, 0.00466, 0.00527, 0.00571,
				0.00604, 0.00625, 0.0016, 0.00174, 0.00217, 0.00264, 0.00314, 0.00355, 0.00398, 0.00433, 0.00469,
				0.00493, 0.00569, 0.00607, 0.00627, 0.00645, 0.00182, 0.00204, 0.00238, 0.00286, 0.00339, 0.00384,
				0.00424, 0.00456, 0.00488, 0.0052, 0.0059, 0.00623, 0.0064, 0.00654, 0.00205, 0.00235, 0.00272, 0.0032,
				0.00368, 0.00406, 0.00447, 0.00484, 0.00515, 0.00544, 0.00602, 0.00629, 0.0064, 0.00646, 0.00279,
				0.00319, 0.0036, 0.00396, 0.00436, 0.00469, 0.00503, 0.0053, 0.00557, 0.00582, 0.00616, 0.00628,
				0.00638, 0.00641, 0.00379, 0.00406, 0.00439, 0.00472, 0.00504, 0.00532, 0.0056, 0.00582, 0.00602,
				0.00617, 0.0063, 0.00636, 0.00638, 0.00639, 0.00471, 0.00489, 0.00511, 0.00539, 0.00563, 0.00583, 0.006,
				0.00618, 0.0063, 0.00644, 0.00641, 0.00638, 0.00635, 0.00634, 0.00544, 0.00557, 0.00572, 0.00591,
				0.00604, 0.00617, 0.0063, 0.00641, 0.00651, 0.00661, 0.00645, 0.00634, 0.00627, 0.00624, 0.00625,
				0.00632, 0.00638, 0.00644, 0.0065, 0.00655, 0.00661, 0.00667, 0.00672, 0.00673, 0.00634, 0.00614,
				0.00599, 0.00593, 0.00664, 0.00671, 0.00675, 0.00676, 0.00676, 0.00675, 0.00676, 0.00674, 0.00672,
				0.00669, 0.00616, 0.00586, 0.00569, 0.00558, 0.00647, 0.00651, 0.00651, 0.00651, 0.00652, 0.00649,
				0.00645, 0.0064, 0.00637, 0.00631, 0.00576, 0.00534, 0.00512, 0.00495, 0.00615, 0.0062, 0.00618,
				0.00613, 0.0061, 0.00607, 0.00602, 0.00596, 0.00591, 0.00586, 0.00536, 0.00491, 0.00469, 0.0045,
				0.00578, 0.00583, 0.00579, 0.00574, 0.00567, 0.00562, 0.00556, 0.00549, 0.00545, 0.00538, 0.00493,
				0.00453, 0.00435, 0.0042, 0.00542, 0.00547, 0.00539, 0.00532, 0.00522, 0.00516, 0.0051, 0.00504, 0.005,
				0.00495, 0.00454, 0.00418, 0.00404, 0.00394 };

		final LocalDate referenceDate = LocalDate.of(2016, Month.SEPTEMBER, 30);
		final BusinessdayCalendarExcludingTARGETHolidays cal = new BusinessdayCalendarExcludingTARGETHolidays();
		final DayCountConvention_ACT_365 modelDC = new DayCountConvention_ACT_365();
		for(int i=0; i<atmNormalVolatilities.length; i++ ) {

			final LocalDate exerciseDate = cal.getDateFromDateAndOffsetCode(referenceDate, atmExpiries[i]);
			final LocalDate tenorEndDate = cal.getDateFromDateAndOffsetCode(exerciseDate, atmTenors[i]);
			double	exercise		= modelDC.getDaycountFraction(referenceDate, exerciseDate);
			double	tenor			= modelDC.getDaycountFraction(exerciseDate, tenorEndDate);

			// We consider an idealized tenor grid (alternative: adapt the model grid)
			exercise	= Math.round(exercise/0.25)*0.25;
			tenor		= Math.round(tenor/0.25)*0.25;

			if(exercise < 1.0) {
				continue;
			}

			final int numberOfPeriods = (int)Math.round(tenor / swapPeriodLength);

			final double	moneyness			= 0.0;
			final double	targetVolatility	= atmNormalVolatilities[i];
			final String	targetVolatilityType = "VOLATILITYNORMAL";
			final double	weight = 1.0;

			calibrationItemNames.add(atmExpiries[i]+"\t"+atmTenors[i]);
			calibrationProducts.add(createCalibrationItem(weight, exercise, swapPeriodLength, numberOfPeriods, moneyness, targetVolatility, targetVolatilityType, forwardCurve, discountCurve, calibrationProductType));
			calibrationBenchmarks.add(createCalibrationItem(weight, exercise, swapPeriodLength, numberOfPeriods, moneyness, targetVolatility, targetVolatilityType, forwardCurve, discountCurve, CalibrationProductType.MONTECARLO));
			calibrationMonteCarloValue.add(createCalibrationItem(weight, exercise, swapPeriodLength, numberOfPeriods, moneyness, targetVolatility, "VALUE", forwardCurve, discountCurve, CalibrationProductType.MONTECARLO));
		}

		/*
		 * Create a simulation time discretization and forward rate curve discretization
		 */
		final double lastTime	= 40.0;
		final double dt		= 0.25;
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, (int) (lastTime / dt), dt);
		final TimeDiscretization liborPeriodDiscretization = timeDiscretization;

		/*
		 * Create covariance model
		 */
		final int numberOfFactors	= 1;
		final LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelPiecewiseConstant(timeDiscretization, liborPeriodDiscretization, new TimeDiscretizationFromArray(0.00, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0, 40.0), new TimeDiscretizationFromArray(0.00, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0, 40.0), 0.50 / 100);
		final LIBORCorrelationModel correlationModel = new LIBORCorrelationModelExponentialDecay(timeDiscretization, liborPeriodDiscretization, numberOfFactors, 0.05, false);
		// Create a covariance model
		//AbstractLIBORCovarianceModelParametric covarianceModelParametric = new LIBORCovarianceModelExponentialForm5Param(timeDiscretizationFromArray, liborPeriodDiscretization, numberOfFactors, new double[] { 0.20/100.0, 0.05/100.0, 0.10, 0.05/100.0, 0.10} );
		final AbstractLIBORCovarianceModelParametric covarianceModelFromVolAndCor = new LIBORCovarianceModelFromVolatilityAndCorrelation(timeDiscretization, liborPeriodDiscretization, volatilityModel, correlationModel);

		// Create blended local volatility model with fixed parameter (0=lognormal, > 1 = almost a normal model).
		final AbstractLIBORCovarianceModelParametric covarianceModelDisplaced = new DisplacedLocalVolatilityModel(covarianceModelFromVolAndCor, 1.0/0.25, false /* isCalibrateable */);

		final AbstractLIBORCovarianceModelParametric covarianceModel;
		switch(modelType) {
		case NORMAL:
			covarianceModel = covarianceModelFromVolAndCor;
			break;
		case DISPLACED:
			covarianceModel = covarianceModelDisplaced;
			break;
		default:
			throw new IllegalArgumentException("Unknown " + modelType.getClass().getSimpleName() + ": " + modelType);
		}


		/*
		 * Create Brownian motion used for calibration
		 */
		final BrownianMotion brownianMotion = new net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPathsCalibration, 31415 /* seed */, randomVariableFactory);

		/*
		 * Specify the optimizer used for calibration, set calibration properties (should use our brownianMotion for calibration).
		 */
		final Double accuracy = 1E-7;	// Lower accuracy to reduce runtime of the unit test
		final int maxIterations = 200;
		final int numberOfThreads = 1;
		final double lambda = 0.1;
		final OptimizerFactory optimizerFactory = new OptimizerFactoryLevenbergMarquardt(
				RegularizationMethod.LEVENBERG, lambda,
				maxIterations, accuracy, numberOfThreads);

		final double[] parameterStandardDeviation = new double[covarianceModelFromVolAndCor.getParameterAsDouble().length];
		final double[] parameterLowerBound = new double[covarianceModelFromVolAndCor.getParameterAsDouble().length];
		final double[] parameterUpperBound = new double[covarianceModelFromVolAndCor.getParameterAsDouble().length];
		Arrays.fill(parameterStandardDeviation, 0.20/100.0);
		Arrays.fill(parameterLowerBound, 0.0);
		Arrays.fill(parameterUpperBound, Double.POSITIVE_INFINITY);

		// Set calibration properties (should use our brownianMotion for calibration - needed to have to right correlation).
		final Map<String, Object> calibrationParameters = Map.of(
				"brownianMotion", brownianMotion,
				"optimizerFactory", optimizerFactory,
				"parameterStep", 1E-4);

		/*
		 *  Set model properties
		 */
		final Map<String, Object> properties = Map.of(
				// Choose the simulation measure
				"measure", LIBORMarketModelFromCovarianceModel.Measure.SPOT.name(),
				// Choose normal state space for the Euler scheme (the covariance model above carries a linear local volatility model, such that the resulting model is log-normal).
				"stateSpace", LIBORMarketModelFromCovarianceModel.StateSpace.NORMAL.name(),
				// Calibration parameters (from above)
				"calibrationParameters", calibrationParameters);


		System.out.print("\nCalibration of model volatilities....");
		final long millisCalibrationStart = System.currentTimeMillis();

		/*
		 * Create corresponding LIBOR Market Model
		 */
		final LIBORMarketModel liborMarketModelCalibrated = LIBORMarketModelFromCovarianceModel.of(
				liborPeriodDiscretization,
				curveModel,
				forwardCurve,
				new DiscountCurveFromForwardCurve(forwardCurve),
				randomVariableFactory,
				covarianceModel,
				calibrationProducts.toArray(new CalibrationProduct[calibrationProducts.size()]),
				properties);

		final long millisCalibrationEnd = System.currentTimeMillis();
		System.out.println("done (" + (millisCalibrationEnd-millisCalibrationStart)/1000.0 + " sec).");

		if(isPrintResults) {
			System.out.println("\nCalibrated parameters are:");
			final double[] param = ((AbstractLIBORCovarianceModelParametric)((LIBORMarketModelFromCovarianceModel) liborMarketModelCalibrated).getCovarianceModel()).getParameterAsDouble();
			for(final double p : param) {
				System.out.println(p);
			}
		}

		/*
		 * Simulation used to calibrate
		 */
		final EulerSchemeFromProcessModel process = new EulerSchemeFromProcessModel(liborMarketModelCalibrated, brownianMotion);
		final LIBORModelMonteCarloSimulationModel simulationCalibrated = new LIBORMonteCarloSimulationFromLIBORModel(process);

		/*
		 * Benchmark simulation (using the calibrated covariance model)
		 */
		final LIBORMarketModel liborMarketModelBenchmark = LIBORMarketModelFromCovarianceModel.of(
				liborPeriodDiscretization,
				curveModel,
				forwardCurve,
				new DiscountCurveFromForwardCurve(forwardCurve),
				randomVariableFactory,
				liborMarketModelCalibrated.getCovarianceModel(),
				null, properties);

		final BrownianMotion brownianMotionBenchmark = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPathsBenchmark, 31415 /* seed */, randomVariableFactory);
		final EulerSchemeFromProcessModel processBenchmark = new EulerSchemeFromProcessModel(liborMarketModelBenchmark, brownianMotionBenchmark);
		final LIBORModelMonteCarloSimulationModel simulationBenchmark = new LIBORMonteCarloSimulationFromLIBORModel(processBenchmark);

		/*
		 * Check the calibrated model - with the analytic and the Monte-Carlo product
		 */
		double deviationCalibrationSum			= 0.0;
		double deviationCalibrationSquaredSum	= 0.0;
		double deviationValuationSum			= 0.0;
		double deviationValuationSquaredSum	= 0.0;
		for (int i = 0; i < calibrationProducts.size(); i++) {
			final TermStructureMonteCarloProduct calibrationProduct = calibrationProducts.get(i).getProduct();
			final TermStructureMonteCarloProduct calibrationBenchmark = calibrationBenchmarks.get(i).getProduct();
			try {
				final double valueModel = calibrationProduct.getValue(simulationCalibrated);
				final double valueBenchmarkModel = calibrationBenchmark.getValue(simulationBenchmark);
				final double valueTarget = calibrationProducts.get(i).getTargetValue().getAverage();
				final double priceModel = calibrationMonteCarloValue.get(i).getProduct().getValue(simulationBenchmark);
				final double priceTarget = calibrationMonteCarloValue.get(i).getTargetValue().getAverage();
				final double errorCalibration = valueModel-valueTarget;
				deviationCalibrationSum += errorCalibration;
				deviationCalibrationSquaredSum += errorCalibration*errorCalibration;

				final double errorValuation = valueBenchmarkModel-valueTarget;
				deviationValuationSum += errorValuation;
				deviationValuationSquaredSum += errorValuation*errorValuation;

				if(isPrintResults) {
					System.out.println(calibrationItemNames.get(i) +
							"\t Model: " + formatterValue.format(valueModel) + "\t Benchmark: " + formatterValue.format(valueBenchmarkModel) +
							"\t Target: " + formatterValue.format(valueTarget) + "\t Deviation: " + formatterDeviation.format(valueModel-valueTarget) +
							"\t Deviation benchmark: " + formatterDeviation.format(valueModel-valueBenchmarkModel) +
							"\t Price: " + formatterValue.format(priceModel) + "\t Target: " + formatterValue.format(priceTarget));
				}
			}
			catch(final Exception e) {
			}
		}

		final double averageCalibrationDeviation = deviationCalibrationSum/calibrationProducts.size();
		System.out.println("\nValuation using the calibration product (" + calibrationProductType + ") and calibration model (paths=" + numberOfPathsCalibration + "):");
		System.out.println("\tCalibration Mean Deviation:" + formatterValue.format(averageCalibrationDeviation));
		System.out.println("\tCalibration RMS Error.....:" + formatterValue.format(Math.sqrt(deviationCalibrationSquaredSum/calibrationProducts.size())));

		final double averageValuationDeviation = deviationValuationSum/calibrationProducts.size();
		System.out.println("\nValuation using the benchmark product (" + CalibrationProductType.MONTECARLO + ") and benchmark model (paths=" + numberOfPathsBenchmark + "):");
		System.out.println("\tValuation   Mean Deviation:" + formatterValue.format(averageValuationDeviation));
		System.out.println("\tValuation   RMS Error.....:" + formatterValue.format(Math.sqrt(deviationValuationSquaredSum/calibrationProducts.size())));

		System.out.println();

		System.out.println("_".repeat(120) + "\n");
	}

	private CalibrationProduct createCalibrationItem(double weight, double exerciseDate, double swapPeriodLength, int numberOfPeriods, double moneyness, double targetVolatility, String targetVolatilityType, ForwardCurve forwardCurve, DiscountCurve discountCurve, CalibrationProductType calibrationProductType) throws CalculationException {

		final double[]	fixingDates			= new double[numberOfPeriods];
		final double[]	paymentDates		= new double[numberOfPeriods];
		final double[]	swapTenor			= new double[numberOfPeriods + 1];

		for (int periodStartIndex = 0; periodStartIndex < numberOfPeriods; periodStartIndex++) {
			fixingDates[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
			paymentDates[periodStartIndex] = exerciseDate + (periodStartIndex + 1) * swapPeriodLength;
			swapTenor[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
		}
		swapTenor[numberOfPeriods] = exerciseDate + numberOfPeriods * swapPeriodLength;

		// Swaptions swap rate
		final double swaprate = moneyness + getParSwaprate(forwardCurve, discountCurve, swapTenor);

		// Set swap rates for each period
		final double[] swaprates = new double[numberOfPeriods];
		Arrays.fill(swaprates, swaprate);

		/*
		 * We use Monte-Carlo calibration on implied volatility.
		 * Alternatively you may change here to Monte-Carlo valuation on price or
		 * use an analytic approximation formula, etc.
		 */
		Double targetValue;
		switch(targetVolatilityType) {
		case "VOLATILITYNORMAL":
		case "VOLATILITYLOGNORMAL":
			targetValue = targetVolatility;
			break;
		case "VALUE":
			targetValue = AnalyticFormulas.bachelierOptionValue(swaprate, targetVolatility, fixingDates[0], swaprate, SwapAnnuity.getSwapAnnuity(new TimeDiscretizationFromArray(swapTenor), discountCurve));
			break;
		default:
			throw new IllegalArgumentException("Unknown targetVolatilityType " + targetVolatilityType);
		}


		AbstractTermStructureMonteCarloProduct product;
		switch(calibrationProductType) {
		case MONTECARLO:
			product = new SwaptionSimple(swaprate, swapTenor, SwaptionSimple.ValueUnit.valueOf(targetVolatilityType));
			break;
		case ANALYTIC:
			product = new SwaptionGeneralizedAnalyticApproximation(
					swaprate, swapTenor,
					SwaptionGeneralizedAnalyticApproximation.ValueUnit.VOLATILITY,
					SwaptionGeneralizedAnalyticApproximation.StateSpace.NORMAL);
			break;
		default:
			throw new IllegalArgumentException("Unknown producType " + calibrationProductType);
		}

		return new CalibrationProduct(product, targetValue, weight);
	}

	public AnalyticModel getCalibratedCurve() throws SolverException {
		final String[] maturity					= { "6M", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "11Y", "12Y", "15Y", "20Y", "25Y", "30Y", "35Y", "40Y", "45Y", "50Y" };
		final String[] frequency				= { "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual", "annual" };
		final String[] frequencyFloat			= { "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual", "semiannual" };
		final String[] daycountConventions		= { "ACT/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360", "E30/360" };
		final String[] daycountConventionsFloat	= { "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360", "ACT/360" };
		final double[] rates					= { -0.00216 ,-0.00208 ,-0.00222 ,-0.00216 ,-0.0019 ,-0.0014 ,-0.00072 ,0.00011 ,0.00103 ,0.00196 ,0.00285 ,0.00367 ,0.0044 ,0.00604 ,0.00733 ,0.00767 ,0.00773 ,0.00765 ,0.00752 ,0.007138 ,0.007 };

		final HashMap<String, Object> parameters = new HashMap<>();

		parameters.put("referenceDate", LocalDate.of(2016, Month.SEPTEMBER, 30));
		parameters.put("currency", "EUR");
		parameters.put("forwardCurveTenor", "6M");
		parameters.put("maturities", maturity);
		parameters.put("fixLegFrequencies", frequency);
		parameters.put("floatLegFrequencies", frequencyFloat);
		parameters.put("fixLegDaycountConventions", daycountConventions);
		parameters.put("floatLegDaycountConventions", daycountConventionsFloat);
		parameters.put("rates", rates);

		return getCalibratedCurve(null, parameters);
	}

	private static AnalyticModel getCalibratedCurve(final AnalyticModel model2, final Map<String, Object> parameters) throws SolverException {

		if(isPrintResultsForCurves) {
			System.out.println("Calibration of rate curves:");
		}

		final LocalDate	referenceDate		= (LocalDate) parameters.get("referenceDate");
		final String	currency			= (String) parameters.get("currency");
		final String	forwardCurveTenor	= (String) parameters.get("forwardCurveTenor");
		final String[]	maturities			= (String[]) parameters.get("maturities");
		final String[]	frequency			= (String[]) parameters.get("fixLegFrequencies");
		final String[]	frequencyFloat		= (String[]) parameters.get("floatLegFrequencies");
		final String[]	daycountConventions	= (String[]) parameters.get("fixLegDaycountConventions");
		final String[]	daycountConventionsFloat	= (String[]) parameters.get("floatLegDaycountConventions");
		final double[]	rates						= (double[]) parameters.get("rates");

		Assert.assertEquals(maturities.length, frequency.length);
		Assert.assertEquals(maturities.length, daycountConventions.length);
		Assert.assertEquals(maturities.length, rates.length);

		Assert.assertEquals(frequency.length, frequencyFloat.length);
		Assert.assertEquals(daycountConventions.length, daycountConventionsFloat.length);

		final int		spotOffsetDays = 2;
		final String	forwardStartPeriod = "0D";

		final String curveNameDiscount = "discountCurve-" + currency;

		/*
		 * We create a forward curve by referencing the same discount curve, since
		 * this is a single curve setup.
		 *
		 * Note that using an independent NSS forward curve with its own NSS parameters
		 * would result in a problem where both, the forward curve and the discount curve
		 * have free parameters.
		 */
		final ForwardCurve forwardCurve		= new ForwardCurveFromDiscountCurve(curveNameDiscount, referenceDate, forwardCurveTenor);

		// Create a collection of objective functions (calibration products)
		final Vector<AnalyticProduct> calibrationProducts = new Vector<>();
		final double[] curveMaturities	= new double[rates.length+1];
		final double[] curveValue			= new double[rates.length+1];
		final boolean[] curveIsParameter	= new boolean[rates.length+1];
		curveMaturities[0] = 0.0;
		curveValue[0] = 1.0;
		curveIsParameter[0] = false;
		for(int i=0; i<rates.length; i++) {

			final Schedule schedulePay = ScheduleGenerator.createScheduleFromConventions(referenceDate, spotOffsetDays, forwardStartPeriod, maturities[i], frequency[i], daycountConventions[i], "first", "following", new BusinessdayCalendarExcludingTARGETHolidays(), -2, 0);
			final Schedule scheduleRec = ScheduleGenerator.createScheduleFromConventions(referenceDate, spotOffsetDays, forwardStartPeriod, maturities[i], frequencyFloat[i], daycountConventionsFloat[i], "first", "following", new BusinessdayCalendarExcludingTARGETHolidays(), -2, 0);

			curveMaturities[i+1] = Math.max(schedulePay.getPayment(schedulePay.getNumberOfPeriods()-1),scheduleRec.getPayment(scheduleRec.getNumberOfPeriods()-1));
			curveValue[i+1] = 1.0;
			curveIsParameter[i+1] = true;
			calibrationProducts.add(new Swap(schedulePay, null, rates[i], curveNameDiscount, scheduleRec, forwardCurve.getName(), 0.0, curveNameDiscount));
		}

		final InterpolationMethod interpolationMethod = InterpolationMethod.LINEAR;

		// Create a discount curve
		final DiscountCurveInterpolation discountCurveInterpolation = DiscountCurveInterpolation.createDiscountCurveFromDiscountFactors(
				curveNameDiscount								/* name */,
				referenceDate	/* referenceDate */,
				curveMaturities	/* maturities */,
				curveValue		/* discount factors */,
				curveIsParameter,
				interpolationMethod ,
				ExtrapolationMethod.CONSTANT,
				InterpolationEntity.LOG_OF_VALUE
				);

		/*
		 * Model consists of the two curves, but only one of them provides free parameters.
		 */
		AnalyticModel model = new AnalyticModelFromCurvesAndVols(new Curve[] { discountCurveInterpolation, forwardCurve });

		/*
		 * Create a collection of curves to calibrate
		 */
		final Set<ParameterObject> curvesToCalibrate = new HashSet<>();
		curvesToCalibrate.add(discountCurveInterpolation);

		/*
		 * Calibrate the curve
		 */
		final Solver solver = new Solver(model, calibrationProducts, 0.0, 1E-4 /* target accuracy */);
		final AnalyticModel calibratedModel = solver.getCalibratedModel(curvesToCalibrate);
		if(isPrintResultsForCurves) {
			System.out.println("Solver reported acccurary....: " + solver.getAccuracy());
		}

		Assert.assertEquals("Calibration accurarcy", 0.0, solver.getAccuracy(), 1E-3);

		// Get best parameters
		final double[] parametersBest = calibratedModel.getDiscountCurve(discountCurveInterpolation.getName()).getParameter();

		// Test calibration
		model			= calibratedModel;

		double squaredErrorSum = 0.0;
		for(final AnalyticProduct c : calibrationProducts) {
			final double value = c.getValue(0.0, model);
			final double valueTaget = 0.0;
			final double error = value - valueTaget;
			squaredErrorSum += error*error;
		}
		final double rms = Math.sqrt(squaredErrorSum/calibrationProducts.size());

		if(isPrintResultsForCurves) {
			System.out.println("Independent checked acccurary: " + rms);
		}

		if(isPrintResultsForCurves && isPrintResults) {
			System.out.println("Calibrated discount curve: ");
			for(int i=0; i<curveMaturities.length; i++) {
				final double maturity = curveMaturities[i];
				System.out.println(maturity + "\t" + calibratedModel.getDiscountCurve(discountCurveInterpolation.getName()).getDiscountFactor(maturity));
			}
		}
		return model;
	}

	private static double getParSwaprate(final ForwardCurve forwardCurve, final DiscountCurve discountCurve, final double[] swapTenor) {
		return net.finmath.marketdata.products.Swap.getForwardSwapRate(new TimeDiscretizationFromArray(swapTenor), new TimeDiscretizationFromArray(swapTenor), forwardCurve, discountCurve);
	}
}
