/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christianfries.com.
 *
 * Created on 15.06.2016
 */

package net.finmath.experiments.montecarlo.automaticdifferentiation;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.ArrayUtils;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.functions.BachelierModel;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterpolation;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.RandomVariableFromDoubleArray;
import net.finmath.montecarlo.automaticdifferentiation.RandomVariableDifferentiable;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory.DiracDeltaApproximationMethod;
import net.finmath.montecarlo.conditionalexpectation.LinearRegression;
import net.finmath.montecarlo.interestrate.CalibrationProduct;
import net.finmath.montecarlo.interestrate.LIBORMarketModel;
import net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.LIBORMonteCarloSimulationFromLIBORModel;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelCalibrateable;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelFourParameterExponentialForm;
import net.finmath.montecarlo.interestrate.products.AbstractLIBORMonteCarloProduct;
import net.finmath.montecarlo.interestrate.products.DigitalCaplet;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * This program generates data to analyze the calculation of delta of a digital option in a discrete forward rate model (LIBOR market model).
 *
 * Among the methods is the calculation via AAD with regression,
 * see Stochastic Algorithmic Differentiation of (Expectations of) Discontinuous Functions (Indicator Functions).
 * https://ssrn.com/abstract=3282667
 *
 * @author Christian Fries
 */
public class MonteCarloBlackScholesModelDigitalOptionAADRegressionSensitivitiesLMM {

	private static String filenamePrefix = "aad-indicator-analysis-202011-1";

	private static DecimalFormat formatterReal4 = new DecimalFormat("0.0000");

	private static final Function<RandomVariable, String> print = rv -> "\t" + formatterReal4.format(rv.getAverage()) + "\t" + formatterReal4.format(rv.getStandardDeviation());
	private static final BiFunction<RandomVariable, Double, Stream<Double>> printError = (rv,x) -> List.of((rv.sub(x).getAverage()), (rv.sub(x).average().abs().doubleValue()), (rv.getStandardError()), (rv.sub(x).average().abs().doubleValue() + rv.getStandardError())).stream();

	private static final int numberOfFactors = 1;
	private static final double correlationDecayParam = 0.01;

	private static final String measure = LIBORMarketModelFromCovarianceModel.Measure.TERMINAL.name();

	private static final String stateSpace = LIBORMarketModelFromCovarianceModel.StateSpace.NORMAL.name();

	// Model properties
	private final double	modelVolatility     = 0.30;

	// Process discretization properties
	private final int		numberOfPaths		= 250000;
	private final int		seed = 3216;//3141;

	private final double	lastTime	= 10.0;
	private final double	deltaT				= 1.0;

	private double	forwardRate = 0.05;

	// Product properties
	private final int		assetIndex = 0;
	private final double	optionMaturity = 9.0;
	private final double	optionStrike = forwardRate;
	private final double	periodStart = 9.0;
	private final double	periodEnd = 10.0;

	private DiracDeltaApproximationMethod diracDeltaApproximationMethod = DiracDeltaApproximationMethod.REGRESSION_ON_DENSITY;
	//	private DiracDeltaApproximationMethod diracDeltaApproximationMethod = DiracDeltaApproximationMethod.REGRESSION_ON_DISTRIBUITON;


	public static void main(String[] args) throws CalculationException, CloneNotSupportedException, IOException {

		(new MonteCarloBlackScholesModelDigitalOptionAADRegressionSensitivitiesLMM()).run();
	}

	public void run () throws CalculationException, CloneNotSupportedException, IOException {

		String filename = filenamePrefix + "- " + numberOfFactors + "-" + measure + "-" + stateSpace + ".csv";

		System.out.println("Running analyis of algorithmic differentiation of a digital caplet in a LIBOR market model.");
		System.out.println("This calculation runs very long. Data is saved in the file " + filename + ".");

		FileWriter out = new FileWriter(filename);
		try(CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(
				new String[] {
						"index1", "index2",
						"widthConditionalExpectationIndicator", "widthDensityEstimation",
						"deltaAnalytic",
						"findiff-analytic", "findiff-analytic-abs", "findiff-stderror", "findiff-error",
						"adplain-analytic", "adplain-analytic-abs", "adplain-stderror", "adplain-error",
						"adregre-analytic", "adregre-analytic-abs", "adregre-stderror", "adregre-error",
						"ad2step-analytic", "ad2step-analytic-abs", "ad2step-stderror", "ad2step-error",
						"seed", "forwardRate", "numberOfFactors", "numberOfPaths", "measure", "stateSpace"
				}))) {


			/*
			 * Create a simulation time discretization
			 */

			final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, (int) (lastTime / deltaT), deltaT);

			/*
			 * Create product
			 */
			final AbstractLIBORMonteCarloProduct option = new DigitalCaplet(optionMaturity, periodStart, periodEnd, optionStrike);

			final double width1 = 0.1;
			final double width2 = 0.1;

			for(int seed : new int[] { 3216, 3141, 12317 }) {

				// Create Brownian motion with specified seed
				final BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, seed);

				for(double forwardRate : new double[] { 0.05, 0.04, 0.03, 0.02, 0.01 }) {
					this.forwardRate = forwardRate;

					final Map<String, Object> randomVariableForRegression = getSensitivityApproximationByDirectRegressionRandomVariables(option, brownianMotion);

					for(int scaleIndex1 = 0; scaleIndex1 <= 48; scaleIndex1++) {

						final double scale1 = Math.pow(10.0, (scaleIndex1-32)/8.0);
						final double widthConditionalExpectationIndicator = width1 * scale1;

						final Map<String, Object> results1 = getSensitivityApproximationsOneShiftParameter(option, widthConditionalExpectationIndicator, brownianMotion);

						for(int scaleIndex2 = 0; scaleIndex2 <= 48; scaleIndex2 ++) {

							System.out.print(seed + "\t" + forwardRate + "\t" + scaleIndex1 + "\t" + scaleIndex2);

							final double scale2 = Math.pow(10.0, (scaleIndex2-32)/8.0);
							final double widthDensityEstimation = width2 * scale2;

							final Map<String, Object> results = getSensitivityApproximationsTwoShiftParameter(option, widthConditionalExpectationIndicator, widthDensityEstimation, brownianMotion);
							final Map<String, Object> resultsDirect = getSensitivityApproximationByDirectRegression(option, widthConditionalExpectationIndicator, widthDensityEstimation, (RandomVariable)randomVariableForRegression.get("X"), (RandomVariable)randomVariableForRegression.get("A0"), (RandomVariable)randomVariableForRegression.get("A1"));

							final Double deltaAnalytic = (Double)results1.get("delta.analytic");

							final RandomVariable deltaFD = (RandomVariable)results1.get("delta.fd");
							final RandomVariable deltaAAD = (RandomVariable)results1.get("delta.aad");
							final RandomVariable deltaLikelihoodRatio = (RandomVariable)results.get("delta.likelihood");
							final RandomVariable deltaAADRegression = (RandomVariable)results.get("delta.aad.regression");
							final RandomVariable deltaAADRegressionDirect = (RandomVariable)resultsDirect.get("delta.aad.directregression");

							System.out.print("\t" + deltaAnalytic);
							System.out.print("\t" + deltaFD.getAverage());
							System.out.print("\t" + deltaAADRegression.getAverage());
							System.out.print("\t" + deltaAADRegression.sub(deltaAnalytic).squared().average().sqrt().doubleValue());
							System.out.println();

							printer.print(scaleIndex1);
							printer.print(scaleIndex2);
							printer.print(widthConditionalExpectationIndicator);
							printer.print(widthDensityEstimation);

							printer.print(deltaAnalytic);

							printError.apply(deltaFD, deltaAnalytic).forEach(t -> {
								try {
									printer.print(t);
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							});
							printError.apply(deltaAAD, deltaAnalytic).forEach(t -> {
								try {
									printer.print(t);
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							});
							printError.apply(deltaAADRegression, deltaAnalytic).forEach(t -> {
								try {
									printer.print(t);
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							});
							printError.apply(deltaAADRegressionDirect, deltaAnalytic).forEach(t -> {
								try {
									printer.print(t);
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							});

							printer.print(seed);
							printer.print(forwardRate);

							printer.print(numberOfFactors);
							printer.print(numberOfPaths);
							printer.print(measure);
							printer.print(stateSpace);

							printer.println();
							printer.flush();

						}
					}
				}
			}
		}
	}

	/**
	 * Create a Monte-Carlo simulation of a Black-Scholes model using a specified Brownian motion
	 * and random variable factory. The random variable factory will control the use of AAD (by means of dependency injection).
	 *
	 * @param randomVariableFactory The random variable factory to be used.
	 * @param brownianMotion The Brownian motion used to drive the model.
	 * @return A Monte-Carlo simulation of a Black-Scholes model.
	 */
	public LIBORModelMonteCarloSimulationModel getMonteCarloLMM(final RandomVariableFactory randomVariableFactory, final BrownianMotion brownianMotion) throws CalculationException {

		/*
		 * Create the libor tenor structure and the initial values
		 */
		final double liborPeriodLength	= 1.0;
		final double liborRateTimeHorzion	= 10.0;
		final TimeDiscretization liborPeriodDiscretization = new TimeDiscretizationFromArray(0.0, (int) (liborRateTimeHorzion / liborPeriodLength), liborPeriodLength);

		double[] forwardRateFixings = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
		double[] forwardRateValues = new double[forwardRateFixings.length];
		Arrays.fill(forwardRateValues, forwardRate);

		// Create the forward curve (initial value of the LIBOR market model)
		final ForwardCurveInterpolation forwardCurveInterpolation = ForwardCurveInterpolation.createForwardCurveFromForwards(
				"forwardCurve"								/* name of the curve */,
				forwardRateFixings	/* fixings of the forward */,
				forwardRateValues	/* forwards */,
				liborPeriodLength	/* tenor / period length */
				);

		/*
		 * Create a simulation time discretization
		 */
		final TimeDiscretization timeDiscretization = brownianMotion.getTimeDiscretization();

		/*
		 * Create a volatility structure v[i][j] = sigma_j(t_i)
		 */
		final double a = 0.0, b = 0.0, c = 0.0, d = modelVolatility * (stateSpace.equals(LIBORMarketModelFromCovarianceModel.StateSpace.NORMAL.name()) ? forwardRate : 1.0);
		final LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelFourParameterExponentialForm(timeDiscretization, liborPeriodDiscretization, a, b, c, d, false);

		/*
		 * Create a correlation model rho_{i,j} = exp(-a * abs(T_i-T_j))
		 */
		final LIBORCorrelationModelExponentialDecay correlationModel = new LIBORCorrelationModelExponentialDecay(
				timeDiscretization, liborPeriodDiscretization, numberOfFactors,
				correlationDecayParam);


		/*
		 * Combine volatility model and correlation model to a covariance model
		 */
		final LIBORCovarianceModelCalibrateable covarianceModel = new LIBORCovarianceModelFromVolatilityAndCorrelation(
				timeDiscretization, liborPeriodDiscretization, volatilityModel, correlationModel);

		// BlendedLocalVolatlityModel (future extension)
		//		AbstractLIBORCovarianceModel covarianceModel2 = new BlendedLocalVolatlityModel(covarianceModel, 0.00, false);

		// Set model properties
		final Map<String, String> properties = new HashMap<>();

		// Choose the simulation measure
		properties.put("measure", measure);

		// Choose log normal model
		properties.put("stateSpace", stateSpace);

		// Empty array of calibration items - hence, model will use given covariance
		final CalibrationProduct[] calibrationItems = new CalibrationProduct[0];

		/*
		 * Create corresponding LIBOR Market Model
		 */
		final LIBORMarketModel liborMarketModel = LIBORMarketModelFromCovarianceModel.of(liborPeriodDiscretization, null /* analyticModel */, forwardCurveInterpolation, new DiscountCurveFromForwardCurve(forwardCurveInterpolation), randomVariableFactory, covarianceModel, calibrationItems, properties);

		final EulerSchemeFromProcessModel process = new EulerSchemeFromProcessModel(liborMarketModel, brownianMotion, EulerSchemeFromProcessModel.Scheme.EULER_FUNCTIONAL);

		return new LIBORMonteCarloSimulationFromLIBORModel(process);
	}

	public Map<String, Object> getSensitivityApproximationsOneShiftParameter(
			final AbstractLIBORMonteCarloProduct option,
			final double widthConditionalExpectationIndicator,
			final BrownianMotion brownianMotion) throws CalculationException, CloneNotSupportedException {
		final Map<String, Object> results = new HashMap<>();

		/*
		 * Analytic
		 */
		double df = getMonteCarloLMM(new RandomVariableFromArrayFactory(), brownianMotion).getModel().getDiscountCurve().getDiscountFactor(periodEnd);
		final double deltaAnalytic;
		if(stateSpace.equals("LOGNORMAL")) {
			deltaAnalytic = AnalyticFormulas.blackScholesDigitalOptionDelta(forwardRate, 0.0, modelVolatility, optionMaturity, optionStrike) * df * (periodEnd-periodStart);
		}
		else {
			deltaAnalytic = BachelierModel.bachelierDigitalOptionDelta(forwardRate, modelVolatility*forwardRate, optionMaturity, optionStrike, 1.0) * df * (periodEnd-periodStart);
		}
		results.put("delta.analytic", deltaAnalytic);

		/*
		 * Calculate sensitivities using AAD
		 */
		{
			final Map<String, Object> randomVariableProps = new HashMap<>();
			randomVariableProps.put("isGradientRetainsLeafNodesOnly", false);
			randomVariableProps.put("diracDeltaApproximationWidthPerStdDev", widthConditionalExpectationIndicator);	// 0.05 is the default
			final RandomVariableDifferentiableAADFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory(new RandomVariableFromArrayFactory(), randomVariableProps);

			/*
			 * Create Model
			 */
			final LIBORModelMonteCarloSimulationModel monteCarloBlackScholesModel = getMonteCarloLMM(randomVariableFactory, brownianMotion);


			final RandomVariable value = option.getValue(0.0, monteCarloBlackScholesModel);
			final Map<Long, RandomVariable> derivative = ((RandomVariableDifferentiable)value).getGradient();
			final RandomVariableDifferentiable initialValue = (RandomVariableDifferentiable)((LIBORMarketModel)monteCarloBlackScholesModel.getModel()).getLIBOR(monteCarloBlackScholesModel.getProcess(), 0.0, periodStart, periodEnd);
			final RandomVariable deltaAAD = derivative.get(initialValue.getID());

			results.put("delta.aad", deltaAAD);
		}


		/*
		 * Other Methods
		 */
		final LIBORModelMonteCarloSimulationModel monteCarloBlackScholesModel = getMonteCarloLMM(new RandomVariableFromArrayFactory(), brownianMotion);
		final RandomVariable X = monteCarloBlackScholesModel.getLIBOR(optionMaturity, periodStart, periodEnd).sub(optionStrike);

		/*
		 * Finite Difference
		 */
		{

			final double epsilon = widthConditionalExpectationIndicator*X.getStandardDeviation();
			final Map<String, Object> shiftedValues = new HashMap<>();


			double[] forwardCurveValues = ((LIBORMarketModel)monteCarloBlackScholesModel.getModel()).getForwardRateCurve().getParameter();

			double[] forwardCurveUpShift = forwardCurveValues.clone();
			forwardCurveUpShift[forwardCurveUpShift.length-1] = forwardCurveUpShift[forwardCurveUpShift.length-1] + epsilon/2.0;
			shiftedValues.put("forwardRateCurve", ((LIBORMarketModel)monteCarloBlackScholesModel.getModel()).getForwardRateCurve().getCloneForParameter(forwardCurveUpShift));
			final RandomVariable valueUp = option.getValue(0.0, monteCarloBlackScholesModel.getCloneWithModifiedData(shiftedValues));

			double[] forwardCurveDnShift = forwardCurveValues.clone();
			forwardCurveDnShift[forwardCurveUpShift.length-1] = forwardCurveDnShift[forwardCurveUpShift.length-1] - epsilon/2.0;
			shiftedValues.put("forwardRateCurve", ((LIBORMarketModel)monteCarloBlackScholesModel.getModel()).getForwardRateCurve().getCloneForParameter(forwardCurveDnShift));
			final RandomVariable valueDn = option.getValue(0.0, monteCarloBlackScholesModel.getCloneWithModifiedData(shiftedValues));

			final RandomVariable deltaFD = valueUp.sub(valueDn).div(epsilon);

			results.put("delta.fd", deltaFD);
		}

		/*
		 * Likelihood ratio
		 */
		{
			//			final DigitalOptionDeltaLikelihood digitalOption = new DigitalOptionDeltaLikelihood(optionMaturity, optionStrike);
			//			final RandomVariable deltaLikelihoodRatio = new Scalar(digitalOption.getValue(monteCarloBlackScholesModel));

			//			results.put("delta.likelihood", deltaLikelihoodRatio);
		}

		return results;
	}

	public Map<String, Object> getSensitivityApproximationsTwoShiftParameter(
			final AbstractLIBORMonteCarloProduct option,
			final double widthConditionalExpectationIndicator,
			final double widthDensityEstimation,
			final BrownianMotion brownianMotion) throws CalculationException, CloneNotSupportedException {
		final Map<String, Object> results = new HashMap<>();


		{
			final Map<String, Object> randomVariableRegressionProps = new HashMap<>();
			randomVariableRegressionProps.put("isGradientRetainsLeafNodesOnly", false);
			randomVariableRegressionProps.put("diracDeltaApproximationWidthPerStdDev", widthConditionalExpectationIndicator);	// 0.05 is the default
			randomVariableRegressionProps.put("diracDeltaApproximationMethod", diracDeltaApproximationMethod.name());
			randomVariableRegressionProps.put("diracDeltaApproximationDensityRegressionWidthPerStdDev", widthDensityEstimation);	// 0.5 is the default
			final RandomVariableDifferentiableAADFactory randomVariableFactoryRegression = new RandomVariableDifferentiableAADFactory(new RandomVariableFromArrayFactory(), randomVariableRegressionProps);

			/*
			 * Create Model
			 */
			final LIBORModelMonteCarloSimulationModel monteCarloBlackScholesModel = getMonteCarloLMM(randomVariableFactoryRegression, brownianMotion);

			final RandomVariable value = option.getValue(0.0, monteCarloBlackScholesModel);
			final Map<Long, RandomVariable> derivative = ((RandomVariableDifferentiable)value).getGradient();
			final RandomVariableDifferentiable initialValue = (RandomVariableDifferentiable)((LIBORMarketModel)monteCarloBlackScholesModel.getModel()).getLIBOR(monteCarloBlackScholesModel.getProcess(), 0.0, periodStart, periodEnd);

			final RandomVariable deltaRegression = derivative.get(initialValue.getID());

			results.put("delta.aad.regression", deltaRegression);
		}

		return results;
	}

	public Map<String, Object> getSensitivityApproximationByDirectRegressionRandomVariables(
			final AbstractLIBORMonteCarloProduct option,
			final BrownianMotion brownianMotion
			) throws CalculationException, CloneNotSupportedException {

		final Map<String, Object> results = new HashMap<>();


		/*
		 * Other Methods
		 */
		final LIBORModelMonteCarloSimulationModel monteCarloBlackScholesModel = getMonteCarloLMM(new RandomVariableFromArrayFactory(), brownianMotion);
		final RandomVariable X = monteCarloBlackScholesModel.getLIBOR(optionMaturity, periodStart, periodEnd).sub(optionStrike);

		results.put("X", X);

		/*
		 * Calculate A from
		 */
		final Map<String, Object> randomVariablePropsZeroWidth = new HashMap<>();
		randomVariablePropsZeroWidth.put("isGradientRetainsLeafNodesOnly", false);
		randomVariablePropsZeroWidth.put("diracDeltaApproximationWidthPerStdDev", 0.0);
		final RandomVariableDifferentiableAADFactory randomVariableFactoryZeroWidth = new RandomVariableDifferentiableAADFactory(new RandomVariableFromArrayFactory(), randomVariablePropsZeroWidth);

		final Map<String, Object> randomVariablePropsInftyWidth = new HashMap<>();
		randomVariablePropsInftyWidth.put("isGradientRetainsLeafNodesOnly", false);
		randomVariablePropsInftyWidth.put("diracDeltaApproximationWidthPerStdDev", Double.POSITIVE_INFINITY);
		final RandomVariableDifferentiableAADFactory randomVariableFactoryInftyWidth = new RandomVariableDifferentiableAADFactory(new RandomVariableFromArrayFactory(), randomVariablePropsInftyWidth);

		final RandomVariable A0;
		{
			final LIBORModelMonteCarloSimulationModel monteCarloBlackScholesModelZeroWidth = getMonteCarloLMM(randomVariableFactoryZeroWidth, brownianMotion);
			final RandomVariableDifferentiable initialValueZeroWidth = (RandomVariableDifferentiable)((LIBORMarketModel)monteCarloBlackScholesModelZeroWidth.getModel()).getLIBOR(monteCarloBlackScholesModelZeroWidth.getProcess(), 0.0, periodStart, periodEnd);
			A0 = ((RandomVariableDifferentiable)option.getValue(0.0, monteCarloBlackScholesModelZeroWidth)).getGradient().get(initialValueZeroWidth.getID());
		}
		results.put("A0", A0);

		final RandomVariable A1;
		{
			final LIBORModelMonteCarloSimulationModel monteCarloBlackScholesModelInftyWidth = getMonteCarloLMM(randomVariableFactoryInftyWidth, brownianMotion);
			final RandomVariableDifferentiable initialValueInftyWidth = (RandomVariableDifferentiable)((LIBORMarketModel)monteCarloBlackScholesModelInftyWidth.getModel()).getLIBOR(monteCarloBlackScholesModelInftyWidth.getProcess(), 0.0, periodStart, periodEnd);
			A1 = ((RandomVariableDifferentiable)option.getValue(0.0, monteCarloBlackScholesModelInftyWidth)).getGradient().get(initialValueInftyWidth.getID());
		}
		results.put("A1", A1);


		return results;
	}

	public Map<String, Object> getSensitivityApproximationByDirectRegression(
			final AbstractLIBORMonteCarloProduct option,
			final double widthConditionalExpectationIndicator,
			final double widthDensityEstimation,
			final RandomVariable X, final RandomVariable A0, final RandomVariable A1) throws CalculationException, CloneNotSupportedException {
		final Map<String, Object> results = new HashMap<>();


		/*
		 * Calculate sensitivities using AAD with regression by extracting the derivative A
		 *
		 * The following code is only for research purposes. We can explicitly extract the random variables A and X
		 * in the valuation of the derivative being E(A d/dX 1_X ) and analyse the random variables.
		 */
		{
			final RandomVariable A = A1.sub(A0);

			/*
			 * Density regression
			 */
			final double underlyingStdDev = X.getStandardDeviation();
			final ArrayList<Double> maskX = new ArrayList<>();
			final ArrayList<Double> maskY = new ArrayList<>();
			for(double maskSizeFactor = -widthDensityEstimation; maskSizeFactor<=widthDensityEstimation+0.005; maskSizeFactor+=0.01) {
				final double maskSize2 = maskSizeFactor * underlyingStdDev;
				if(Math.abs(maskSizeFactor) < 1E-10) {
					continue;
				}
				final RandomVariable maskPos = X.add(Math.max(maskSize2,0)).choose(new Scalar(1.0), new Scalar(0.0));
				final RandomVariable maskNeg = X.add(Math.min(maskSize2,0)).choose(new Scalar(0.0), new Scalar(1.0));
				final RandomVariable mask2 = maskPos.mult(maskNeg);
				final double density = mask2.getAverage() / Math.abs(maskSize2);
				maskX.add(maskSize2);
				maskY.add(density);
			}
			final RandomVariable densityX = new RandomVariableFromDoubleArray(0.0, ArrayUtils.toPrimitive(maskX.toArray(new Double[0])));
			final RandomVariable densityValues = new RandomVariableFromDoubleArray(0.0, ArrayUtils.toPrimitive(maskY.toArray(new Double[0])));

			final double[] densityRegressionCoeff = new LinearRegression(new RandomVariable[] { densityX.mult(0.0).add(1.0), densityX }).getRegressionCoefficients(densityValues);
			final double density = densityRegressionCoeff[0];

			RandomVariable densityRegression = densityValues.mult(0.0).add(densityRegressionCoeff[0]);
			for(int i=1; i<densityRegressionCoeff.length; i++) {
				densityRegression = densityRegression.add(densityX.pow(i).mult(densityRegressionCoeff[i]));
			}

			results.put("density", density);
			results.put("density.x", densityX);
			results.put("density.values", densityValues);
			results.put("density.regression", densityRegression);

			/*
			 * Localization
			 */
			final double derivativeLocalizationSize = widthConditionalExpectationIndicator*X.getStandardDeviation();
			RandomVariable derivativeLocalizer = X.add(derivativeLocalizationSize/2.0).choose(new Scalar(1.0), new Scalar(0.0));
			derivativeLocalizer = derivativeLocalizer.mult(X.sub(derivativeLocalizationSize/2.0).choose(new Scalar(0.0), new Scalar(1.0)));

			final RandomVariable Atilde = A.mult(derivativeLocalizer);
			final RandomVariable Xtilde = X.mult(derivativeLocalizer);

			/*
			 * Linear regression of A
			 */
			final double alphaLinear = (Xtilde.squared().getAverage() * Atilde.getAverage() - Xtilde.getAverage() * Xtilde.mult(Atilde).getAverage()) / (Xtilde.squared().getAverage() - Xtilde.average().squared().doubleValue());

			/*
			 * Non-linear regression of A
			 */

			//		double[] adjointDerivativeRegressionCoeff = new LinearRegression(new RandomVariable[] { derivativeLocalizer, Xtilde }).getRegressionCoefficients(Atilde);
			final double[] adjointDerivativeRegressionCoeff = new LinearRegression(new RandomVariable[] { derivativeLocalizer }).getRegressionCoefficients(Atilde);

			final double alphaNonLinear = adjointDerivativeRegressionCoeff[0] * density;

			final RandomVariable deltaAADReg = A0.add(alphaNonLinear);

			RandomVariable sensitivityRegression = new Scalar(adjointDerivativeRegressionCoeff[0]);
			RandomVariable densityRegressionOnX = new Scalar(densityRegressionCoeff[0]);
			for(int i=1; i<adjointDerivativeRegressionCoeff.length; i++) {
				sensitivityRegression = sensitivityRegression.add(X.pow(i).mult(adjointDerivativeRegressionCoeff[i]));
				densityRegressionOnX = densityRegressionOnX.add(X.pow(i).mult(densityRegressionCoeff[i]));
			}

			results.put("delta.aad.directregression", deltaAADReg);
			results.put("delta.aad.directregression.regression.x", X);
			results.put("delta.aad.directregression.regression.sensitivity", sensitivityRegression);
			results.put("delta.aad.directregression.regression.density", densityRegressionOnX);
		}

		return results;
	}
}
