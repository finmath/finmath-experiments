package net.finmath.experiments.montecarlo.interestrates;

import java.util.Arrays;
import java.util.Map;

import net.finmath.exception.CalculationException;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterpolation;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.interestrate.CalibrationProduct;
import net.finmath.montecarlo.interestrate.LIBORMonteCarloSimulationFromLIBORModel;
import net.finmath.montecarlo.interestrate.TermStructureMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.BlendedLocalVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelFourParameterExponentialForm;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class ModelFactory {

	public static TermStructureMonteCarloSimulationModel createTermStuctureModel(
			RandomVariableFactory randomVariableFactory,
			String measure,
			String simulationTimeInterpolationMethod,
			double forwardRate,
			double periodLength,
			boolean useDiscountCurve,
			double volatility,
			double volatilityExponentialDecay,
			double localVolNormalityBlend,
			double correlationDecayParam,
			int numberOfFactors,
			int numberOfPaths,
			int seed
			) throws CalculationException {

		/*
		 * Create the forward rate tenor structure and the initial values (the T_j)
		 */
		final double forwardRatePeriodLength 		= periodLength;
		final double forwardRateRateTimeHorzion		= 20.0;
		final int numberOfPeriods = (int) (forwardRateRateTimeHorzion / forwardRatePeriodLength);
		final TimeDiscretization liborPeriodDiscretization = new TimeDiscretizationFromArray(0.0, numberOfPeriods, forwardRatePeriodLength);

		/*
		 * Interest rate curve (here a flat (constant) value of forwardRate for the curve of forward rates
		 */

		// Create the forward curve (initial value of the term structure model) - this curve is flat
		final ForwardCurve forwardCurve = ForwardCurveInterpolation.createForwardCurveFromForwards(
				"forwardCurve"								/* name of the curve */,
				new double[] {       0.5 ,        1.0 ,        2.0 ,        5.0 ,        40.0}	/* fixings of the forward */,
				new double[] {forwardRate, forwardRate, forwardRate, forwardRate, forwardRate}	/* forwards */,
				forwardRatePeriodLength							/* tenor / period length */
				);

		// Discount curve used for numeraire adjustment (if requested)
		final DiscountCurve discountCurve = useDiscountCurve ? new DiscountCurveFromForwardCurve(forwardCurve) : null;

		/*
		 * Create a simulation time discretization (the t_i)
		 */
		final double lastTime	= 20.0;
		final double dt			= periodLength;
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, (int) (lastTime / dt), dt);

		/*
		 * Create a volatility structure v[i][j] = sigma_j(t_i): here all sigma are constant
		 */
		final int numberOfCompnents = liborPeriodDiscretization.getNumberOfTimeSteps();
		final int numberOfTimesSteps = timeDiscretization.getNumberOfTimes();
		final double[][] volatilities = new double[numberOfTimesSteps][numberOfCompnents];
		for(int i=0; i<volatilities.length; i++) {
			Arrays.fill(volatilities[i], volatility);
		}
//		final LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelFromGivenMatrix(randomVariableFactory, timeDiscretization, liborPeriodDiscretization, volatilities);

		final LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelFourParameterExponentialForm(timeDiscretization, liborPeriodDiscretization, volatility, 0, volatilityExponentialDecay, 0.0, false);
		
		/*
		 * Create a correlation model rho_{i,j} = exp(-a * abs(T_i-T_j))
		 */
		final LIBORCorrelationModel correlationModel = new LIBORCorrelationModelExponentialDecay(
				timeDiscretization, liborPeriodDiscretization, numberOfFactors,
				correlationDecayParam);

		/*
		 * Combine volatility model and correlation model to a covariance model
		 */
		final LIBORCovarianceModelFromVolatilityAndCorrelation covarianceModelWithConstantVolatility =
				new LIBORCovarianceModelFromVolatilityAndCorrelation(timeDiscretization,
						liborPeriodDiscretization, volatilityModel, correlationModel);

		/*
		 * BlendedLocalVolatlityModel: Puts the factor (a L_{i}(0) + (1-a) L_{i}(t)) in front of the diffusion.
		 */
		final LIBORCovarianceModel covarianceModelBlended = new BlendedLocalVolatilityModel(
				covarianceModelWithConstantVolatility, forwardCurve, localVolNormalityBlend, false);

		/*
		 * Set model properties
		 */
		final Map<String, String> properties = Map.of(
				"measure", measure,																		// Choose the simulation measure
				"stateSpace", LIBORMarketModelFromCovarianceModel.StateSpace.NORMAL.name(),				// Choose state space
				"interpolationMethod", "linear",														// Interpolation of the tenor
				"simulationTimeInterpolationMethod", simulationTimeInterpolationMethod					// Interpolation of the simulation time
				);

		// Empty array of calibration items - hence, model will use given covariance
		final CalibrationProduct[] calibrationItems = new CalibrationProduct[0];

		/*
		 * Create corresponding LIBOR Market Model
		 */
		final LIBORMarketModelFromCovarianceModel liborMarketModel = new LIBORMarketModelFromCovarianceModel(
				liborPeriodDiscretization, null /* analyticModel */, forwardCurve, discountCurve, randomVariableFactory,
				covarianceModelBlended, calibrationItems, properties);

		final BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, seed);

		final MonteCarloProcess process = new EulerSchemeFromProcessModel(
				liborMarketModel, brownianMotion, EulerSchemeFromProcessModel.Scheme.EULER);

		return new LIBORMonteCarloSimulationFromLIBORModel(process);
	}

	/*
	 * Some version with default arguments
	 */
	public static TermStructureMonteCarloSimulationModel createTermStuctureModel(
			RandomVariableFactory randomVariableFactory,
			String measure,
			double forwardRate,
			double periodLength,
			boolean useDiscountCurve,
			double volatility,
			double volatilityExponentialDecay,
			double localVolNormalityBlend,
			double correlationDecayParam,
			int numberOfFactors,
			int numberOfPaths,
			int seed
			) throws CalculationException {

		return createTermStuctureModel(randomVariableFactory, measure, "round_down" /* simulationTimeInterpolationMethod */, forwardRate, periodLength, useDiscountCurve, volatility, volatilityExponentialDecay, localVolNormalityBlend, correlationDecayParam, numberOfFactors, numberOfPaths, seed);
	}

	public static TermStructureMonteCarloSimulationModel createTermStuctureModel(
			RandomVariableFactory randomVariableFactory,
			String measure,
			String simulationTimeInterpolationMethod,
			double forwardRate,
			double periodLength,
			boolean useDiscountCurve,
			double volatility,
			double localVolNormalityBlend,
			double correlationDecayParam,
			int numberOfFactors,
			int numberOfPaths,
			int seed
			) throws CalculationException {

		return createTermStuctureModel(randomVariableFactory, measure, simulationTimeInterpolationMethod, forwardRate, periodLength, useDiscountCurve, volatility, 0.0 /* volatilityExponentialDecay */, localVolNormalityBlend, correlationDecayParam, numberOfFactors, numberOfPaths, seed);
	}

	public static TermStructureMonteCarloSimulationModel createTermStuctureModel(
			RandomVariableFactory randomVariableFactory,
			String measure,
			double forwardRate,
			double periodLength,
			boolean useDiscountCurve,
			double volatility,
			double localVolNormalityBlend,
			double correlationDecayParam,
			int numberOfFactors,
			int numberOfPaths,
			int seed
			) throws CalculationException {

		return createTermStuctureModel(randomVariableFactory, measure, "round_down" /* simulationTimeInterpolationMethod */, forwardRate, periodLength, useDiscountCurve, volatility,  0.0 /* volatilityExponentialDecay */, localVolNormalityBlend, correlationDecayParam, numberOfFactors, numberOfPaths, seed);
	}
}
