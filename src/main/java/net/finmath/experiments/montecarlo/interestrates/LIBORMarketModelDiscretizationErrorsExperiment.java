/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 10.02.2004
 */
package net.finmath.experiments.montecarlo.interestrates;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterpolation;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.interestrate.CalibrationProduct;
import net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.LIBORMonteCarloSimulationFromLIBORModel;
import net.finmath.montecarlo.interestrate.TermStructureMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.AbstractLIBORCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.BlendedLocalVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelFromGivenMatrix;
import net.finmath.montecarlo.interestrate.products.AbstractLIBORMonteCarloProduct;
import net.finmath.montecarlo.interestrate.products.Bond;
import net.finmath.montecarlo.interestrate.products.Caplet;
import net.finmath.montecarlo.interestrate.products.Caplet.ValueUnit;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.plots.Plots;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * This class visualises some of the numerical errors associated with a Monte-Carlo simulation
 * of an Euler scheme approximation of a discrete forward rate term structure model (LIBOR Market Model)
 * and its relation to the equivalent martingale measure.
 *
 * @author Christian Fries
 */
public class LIBORMarketModelDiscretizationErrorsExperiment {

	private static DecimalFormat formatterParam		= new DecimalFormat(" #0.000000;-#0.000000", new DecimalFormatSymbols(Locale.ENGLISH));

	private final int numberOfPaths		= 50000;
	private final static int numberOfFactors	= 2;
	private final static int seed = 3141;

	private static DecimalFormat formatterMaturity	= new DecimalFormat("00.00", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterValue		= new DecimalFormat(" ##0.000%;-##0.000%", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterMoneyness	= new DecimalFormat(" 000.0%;-000.0%", new DecimalFormatSymbols(Locale.ENGLISH));
	private static DecimalFormat formatterDeviation	= new DecimalFormat(" 0.00000E00;-0.00000E00", new DecimalFormatSymbols(Locale.ENGLISH));


	public static void main(String args[]) throws CalculationException {
		(new LIBORMarketModelDiscretizationErrorsExperiment()).testBondUnderMeasure();
		(new LIBORMarketModelDiscretizationErrorsExperiment()).testCapletATMImpliedVol();
		(new LIBORMarketModelDiscretizationErrorsExperiment()).testCapletSmile();
		(new LIBORMarketModelDiscretizationErrorsExperiment()).testCapletSmiles();

	}

	public LIBORMarketModelDiscretizationErrorsExperiment() throws CalculationException {}

	public static LIBORModelMonteCarloSimulationModel createLIBORMarketModel(
			RandomVariableFactory randomVariableFactory,
			String measure,
			String simulationTimeInterpolationMethod,
			boolean useDiscountCurve,
			int numberOfPaths,
			double volatility,
			double lognormality
			) throws CalculationException {

		/*
		 * Create the libor tenor structure and the initial values
		 */
		final double liborPeriodLength		= 0.5;
		final double liborRateTimeHorzion	= 20.0;
		final TimeDiscretization liborPeriodDiscretization = new TimeDiscretizationFromArray(0.0, (int) (liborRateTimeHorzion / liborPeriodLength), liborPeriodLength);

		// Create the forward curve (initial value of the LIBOR market model)
		final ForwardCurve forwardCurve = ForwardCurveInterpolation.createForwardCurveFromForwards(
				"forwardCurve"								/* name of the curve */,
				new double[] {0.5 , 1.0 , 2.0 , 5.0 , 40.0}	/* fixings of the forward */,
				new double[] {0.05, 0.05, 0.05, 0.05, 0.05}	/* forwards */,
				liborPeriodLength							/* tenor / period length */
				);

		// Discounted curve used for numeraire adjustment
		final DiscountCurve discountCurve = useDiscountCurve ? new DiscountCurveFromForwardCurve(forwardCurve) : null;

		/*
		 * Create a simulation time discretization
		 */
		final double lastTime	= 20.0;
		final double dt			= 0.5;
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, (int) (lastTime / dt), dt);

		/*
		 * Create a volatility structure v[i][j] = sigma_j(t_i)
		 */
		int numberOfCompnents = liborPeriodDiscretization.getNumberOfTimeSteps();
		int numberOfTimesSteps = timeDiscretization.getNumberOfTimes();
		final double[][] volatilities = new double[numberOfTimesSteps][numberOfCompnents];
		for(int i=0; i<volatilities.length; i++) Arrays.fill(volatilities[i], volatility);
		final boolean isCalibrateable = false;
		final LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelFromGivenMatrix(randomVariableFactory, timeDiscretization, liborPeriodDiscretization, volatilities);

		/*
		 * Create a correlation model rho_{i,j} = exp(-a * abs(T_i-T_j))
		 */
		final double correlationDecayParam = 0.01;
		final LIBORCorrelationModelExponentialDecay correlationModel = new LIBORCorrelationModelExponentialDecay(
				timeDiscretization, liborPeriodDiscretization, numberOfFactors,
				correlationDecayParam);

		/*
		 * Combine volatility model and correlation model to a covariance model
		 */
		final LIBORCovarianceModelFromVolatilityAndCorrelation covarianceModel =
				new LIBORCovarianceModelFromVolatilityAndCorrelation(timeDiscretization,
						liborPeriodDiscretization, volatilityModel, correlationModel);

		// BlendedLocalVolatlityModel (future extension)
		AbstractLIBORCovarianceModel covarianceModel2 = new BlendedLocalVolatilityModel(covarianceModel, forwardCurve, 1.0-lognormality, false);

		// Set model properties
		final Map<String, String> properties = new HashMap<>();

		// Choose the simulation measure
		properties.put("measure", measure);

		// Choose log normal model
		properties.put("stateSpace", LIBORMarketModelFromCovarianceModel.StateSpace.NORMAL.name());

		properties.put("interpolationMethod", "linear");

		properties.put("simulationTimeInterpolationMethod", simulationTimeInterpolationMethod);

		// Empty array of calibration items - hence, model will use given covariance
		final CalibrationProduct[] calibrationItems = new CalibrationProduct[0];

		/*
		 * Create corresponding LIBOR Market Model
		 */
		final LIBORMarketModelFromCovarianceModel liborMarketModel = new LIBORMarketModelFromCovarianceModel(liborPeriodDiscretization, null /* analyticModel */, forwardCurve, discountCurve, randomVariableFactory, covarianceModel2, calibrationItems, properties);

		final BrownianMotion brownianMotionLazyInit = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, seed);

		final EulerSchemeFromProcessModel process = new EulerSchemeFromProcessModel(liborMarketModel, brownianMotionLazyInit, EulerSchemeFromProcessModel.Scheme.EULER);

		return new LIBORMonteCarloSimulationFromLIBORModel(process);
	}

	public void testBondUnderMeasure() throws CalculationException {
		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		String simulationTimeInterpolationMethod = "round_down";
		boolean useDiscountCurve = false;

		for(String measure : new String[] { "terminal", "spot"}) {
			final TermStructureMonteCarloSimulationModel lmm = createLIBORMarketModel(
					randomVariableFactory,
					measure,
					simulationTimeInterpolationMethod,
					useDiscountCurve,
					numberOfPaths, 0.30, 1.0);

			List<Double> maturities = new ArrayList<Double>();
			List<Double> yieldsMonteCarlo = new ArrayList<Double>();
			List<Double> yieldsAnalytic = new ArrayList<Double>();
			List<Double> errors = new ArrayList<Double>();

			for(double maturity = 0.5; maturity < 20; maturity += 0.5) {
				final AbstractLIBORMonteCarloProduct product = new Bond(maturity);
				final double value = product.getValue(lmm);
				final double yieldMonteCarlo = -Math.log(value)/maturity;

				maturities.add(maturity);
				yieldsMonteCarlo.add(yieldMonteCarlo);

				final double valueAnalytic = 1.0/Math.pow((1+0.05*0.5), maturity/0.5);
				final double yieldAnalytic = -Math.log(valueAnalytic)/maturity;
				yieldsAnalytic.add(yieldAnalytic);

				errors.add(yieldMonteCarlo-yieldAnalytic);
			}

			//		Map<String, List<Double>> mapOfValues = Map.of("2-Analytic", yieldsAnalytic, "1-Monte Carlo", yieldsMonteCarlo);
			Plots.createScatter(maturities, errors, 0.0, 0.2, 5)
			.setTitle("Zero bond error when using " + measure + " measure.")
			.setXAxisLabel("maturity")
			.setYAxisLabel("error")
			.setYAxisNumberFormat(new DecimalFormat("0.0E00")).show();
		}
	}

	public void testCapletATMImpliedVol() throws CalculationException {

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		String measure = "spot";
		boolean useDiscountCurve = false;

		for(String simulationTimeInterpolationMethod : new String[] { "round_down", "round_nearest" }) {
			final TermStructureMonteCarloSimulationModel lmm = createLIBORMarketModel(
					randomVariableFactory,
					measure,
					simulationTimeInterpolationMethod,
					useDiscountCurve,
					numberOfPaths, 0.30, 1.0);

			List<Double> maturities = new ArrayList<Double>();
			List<Double> impliedVolatilities = new ArrayList<Double>();

			double strike = 0.05;
			for(double maturity = 0.5; maturity <= 19.5; maturity += 0.05) {
				final AbstractLIBORMonteCarloProduct product = new Caplet(maturity, 0.5, strike);
				final AbstractLIBORMonteCarloProduct productVol = new Caplet(maturity, 0.5, strike, 0.5, false, ValueUnit.LOGNORMALVOLATILITY);
				final double value = product.getValue(lmm);
				final double vol3 = productVol.getValue(lmm);
				double forward = 0.05;
				double optionMaturity = maturity;
				final AbstractLIBORMonteCarloProduct bondAtPayment = new Bond(5.5);
				double optionStrike = strike;
				//			double payoffUnit = bondAtPayment.getValue(lmm);
				double payoffUnit = 1.0/Math.pow(1+0.05*0.5, maturity*2+1) * 0.5;
				double optionValue = value;
				final double impliedVol = AnalyticFormulas.blackScholesOptionImpliedVolatility(forward, optionMaturity, optionStrike, payoffUnit, optionValue);
				//			final double impliedVol = AnalyticFormulas.blackModelCapletImpliedVolatility(forward, optionMaturity, optionStrike, 0.5, payoffUnit, optionValue*0.5);

				maturities.add(maturity);
				impliedVolatilities.add(impliedVol);

				System.out.println(impliedVol + "\t" + vol3);
			}

			Plots.createScatter(maturities, impliedVolatilities, 0.0, 0.2, 5)
			.setTitle("Caplet implied volatility using simulation time interpolation " + simulationTimeInterpolationMethod + ".")
			.setXAxisLabel("maturity")
			.setYAxisLabel("implied volatility")
			.setYAxisNumberFormat(new DecimalFormat("0.0%")).show();
		}
	}

	public void testCapletSmile() throws CalculationException {

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		String simulationTimeInterpolationMethod = "round_down";
		boolean useDiscountCurve = false;

		for(String measure : new String[] { "terminal", "spot"}) {
			final TermStructureMonteCarloSimulationModel lmm = createLIBORMarketModel(
					randomVariableFactory,
					measure,
					simulationTimeInterpolationMethod,
					useDiscountCurve,
					numberOfPaths, 0.30, 1.0);

			List<Double> strikes = new ArrayList<Double>();
			List<Double> impliedVolatilities = new ArrayList<Double>();

			for(double strike = 0.025; strike < 0.10; strike += 0.0025) {
				final AbstractLIBORMonteCarloProduct product = new Caplet(5.0, 0.5, strike);
				final AbstractLIBORMonteCarloProduct productVol = new Caplet(5.0, 0.5, strike, 0.5, false, ValueUnit.LOGNORMALVOLATILITY);
				final double value = product.getValue(lmm);
				final double vol3 = productVol.getValue(lmm);
				double forward = 0.05;
				double optionMaturity = 5.0;
				final AbstractLIBORMonteCarloProduct bondAtPayment = new Bond(5.5);
				double optionStrike = strike;
				//			double payoffUnit = bondAtPayment.getValue(lmm);
				double payoffUnit = 1.0/Math.pow(1+0.05*0.5, 5*2+1) * 0.5;
				double optionValue = value;
				final double impliedVol = AnalyticFormulas.blackScholesOptionImpliedVolatility(forward, optionMaturity, optionStrike, payoffUnit, optionValue);
				//			final double impliedVol = AnalyticFormulas.blackModelCapletImpliedVolatility(forward, optionMaturity, optionStrike, 0.5, payoffUnit, optionValue*0.5);

				strikes.add(strike);
				impliedVolatilities.add(vol3);

				System.out.println(impliedVol + "\t" + vol3);
			}

			Plots.createScatter(strikes, impliedVolatilities, 0.0, 0.2, 5)
			.setTitle("Caplet implied volatility using " + measure + " measure.")
			.setXAxisLabel("strike")
			.setYAxisLabel("implied volatility")
			.setYAxisNumberFormat(new DecimalFormat("0.0%")).show();
		}
	}

	public void testCapletSmiles() throws CalculationException {

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		String simulationTimeInterpolationMethod = "round_down";
		boolean useDiscountCurve = false;

		for(String measure : new String[] { "terminal", "spot"}) {
			List<Double> strikes = new ArrayList<Double>();
			Map<String, List<Double>> impliedVolCurves = new HashMap();
			for(double lognormality = 0.0; lognormality <= 1.0; lognormality += 0.1) {
				final TermStructureMonteCarloSimulationModel lmm = createLIBORMarketModel(
						randomVariableFactory,
						measure,
						simulationTimeInterpolationMethod,
						useDiscountCurve,
						numberOfPaths, 0.30, lognormality);

				List<Double> impliedVolatilities = new ArrayList<Double>();
				for(double strike = 0.025; strike < 0.10; strike += 0.0025) {
					final AbstractLIBORMonteCarloProduct product = new Caplet(5.0, 0.5, strike);
					final AbstractLIBORMonteCarloProduct productVol = new Caplet(5.0, 0.5, strike, 0.5, false, ValueUnit.LOGNORMALVOLATILITY);
					final double value = product.getValue(lmm);
					final double vol3 = productVol.getValue(lmm);
					double forward = 0.05;
					double optionMaturity = 5.0;
					final AbstractLIBORMonteCarloProduct bondAtPayment = new Bond(5.5);
					double optionStrike = strike;
					//			double payoffUnit = bondAtPayment.getValue(lmm);
					double payoffUnit = 1.0/Math.pow(1+0.05*0.5, 5*2+1) * 0.5;
					double optionValue = value;
					final double impliedVol = AnalyticFormulas.blackScholesOptionImpliedVolatility(forward, optionMaturity, optionStrike, payoffUnit, optionValue);
					//			final double impliedVol = AnalyticFormulas.blackModelCapletImpliedVolatility(forward, optionMaturity, optionStrike, 0.5, payoffUnit, optionValue*0.5);

					strikes.add(strike);
					impliedVolatilities.add(vol3);

					System.out.println(impliedVol + "\t" + vol3);
				}
				impliedVolCurves.putIfAbsent(String.valueOf(lognormality), impliedVolatilities);

			}
			Plots.createScatter(strikes, impliedVolCurves, 0.0, 0.2, 5)
			.setTitle("Caplet implied volatility using " + measure + " measure.")
			.setXAxisLabel("strike")
			.setYAxisLabel("implied volatility")
			.setYAxisNumberFormat(new DecimalFormat("0.0%")).show();
		}
	}
}
