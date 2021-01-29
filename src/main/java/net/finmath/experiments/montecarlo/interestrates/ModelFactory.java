package net.finmath.experiments.montecarlo.interestrates;

import java.util.Arrays;
import java.util.HashMap;
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
import net.finmath.montecarlo.interestrate.models.covariance.AbstractLIBORCovarianceModel;
import net.finmath.montecarlo.interestrate.models.covariance.BlendedLocalVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelFromGivenMatrix;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

public class ModelFactory {

	public static TermStructureMonteCarloSimulationModel createLIBORMarketModel(
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
	
		/*
		 * Create the libor tenor structure and the initial values
		 */
		final double liborPeriodLength		= periodLength;
		final double liborRateTimeHorzion	= 20.0;
		final int numberOfPeriods = (int) (liborRateTimeHorzion / liborPeriodLength);
		final TimeDiscretization liborPeriodDiscretization = new TimeDiscretizationFromArray(0.0, numberOfPeriods, liborPeriodLength);
	
		// Create the forward curve (initial value of the term structure model) - this curve is flat
		final ForwardCurve forwardCurve = ForwardCurveInterpolation.createForwardCurveFromForwards(
				"forwardCurve"								/* name of the curve */,
				new double[] {       0.5 ,        1.0 ,        2.0 ,        5.0 ,        40.0}	/* fixings of the forward */,
				new double[] {forwardRate, forwardRate, forwardRate, forwardRate, forwardRate}	/* forwards */,
				liborPeriodLength							/* tenor / period length */
				);
	
		// Discount curve used for numeraire adjustment (if requested)
		final DiscountCurve discountCurve = useDiscountCurve ? new DiscountCurveFromForwardCurve(forwardCurve) : null;
	
		/*
		 * Create a simulation time discretization
		 */
		final double lastTime	= 20.0;
		final double dt			= periodLength;
		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, (int) (lastTime / dt), dt);
	
		/*
		 * Create a volatility structure v[i][j] = sigma_j(t_i)
		 */
		int numberOfCompnents = liborPeriodDiscretization.getNumberOfTimeSteps();
		int numberOfTimesSteps = timeDiscretization.getNumberOfTimes();
		final double[][] volatilities = new double[numberOfTimesSteps][numberOfCompnents];
		for(int i=0; i<volatilities.length; i++) Arrays.fill(volatilities[i], volatility);
		final LIBORVolatilityModel volatilityModel = new LIBORVolatilityModelFromGivenMatrix(
				randomVariableFactory, timeDiscretization, liborPeriodDiscretization, volatilities);
	
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
	
		// BlendedLocalVolatlityModel
		AbstractLIBORCovarianceModel covarianceModelBlended = new BlendedLocalVolatilityModel(
				covarianceModelWithConstantVolatility, forwardCurve, localVolNormalityBlend, false);
//		covarianceModelBlended = covarianceModelWithConstantVolatility;
		
		// Set model properties
		final Map<String, String> properties = new HashMap<>();
	
		// Choose the simulation measure
		properties.put("measure", measure);
	
		// Choose state space
		properties.put("stateSpace", LIBORMarketModelFromCovarianceModel.StateSpace.NORMAL.name());
	
		// Interpolation of the tenor
		properties.put("interpolationMethod", "linear");

		// Interpolation of the simulation time
		properties.put("simulationTimeInterpolationMethod", simulationTimeInterpolationMethod);
	
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

	
	
	
	public static TermStructureMonteCarloSimulationModel createLIBORMarketModel(
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
		
		return createLIBORMarketModel(randomVariableFactory, measure, "round_down", forwardRate, periodLength, useDiscountCurve, volatility, localVolNormalityBlend, correlationDecayParam, numberOfFactors, numberOfPaths, seed);
	}
}
