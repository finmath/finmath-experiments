package net.finmath.climateschool.utilities;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import net.finmath.marketdata.model.AnalyticModel;
import net.finmath.marketdata.model.AnalyticModelFromCurvesAndVols;
import net.finmath.marketdata.model.curves.Curve;
import net.finmath.marketdata.model.curves.CurveInterpolation.ExtrapolationMethod;
import net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationEntity;
import net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationMethod;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.curves.DiscountCurveInterpolation;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveFromDiscountCurve;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.interestrate.LIBORModel;
import net.finmath.montecarlo.interestrate.LIBORMonteCarloSimulationFromLIBORModel;
import net.finmath.montecarlo.interestrate.TermStructureMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.models.HullWhiteModel;
import net.finmath.montecarlo.interestrate.models.covariance.ShortRateVolatilityModel;
import net.finmath.montecarlo.interestrate.models.covariance.ShortRateVolatilityModelAsGiven;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * Factory for interest rate models.
 *
 * @author Christian Fries
 * @version 0.95
 */
public class ModelFactory {

	/**
	 * Hull White parameters (example: sigma = 0.02, a = 0.1 or sigma = 0.05, a = 0.5)
	 *
	 * final double shortRateVolatility = 0.005;	// Investigating LIBOR in Arrears, use a high volatility here (e.g. 0.1)
	 * final double shortRateMeanreversion = 0.1;
	 *
	 * @param zeroRateShortTerm
	 * @param zeroRateLongTerm
	 * @param shortRateVolatility
	 * @param shortRateMeanreversion
	 * @param numberOfPaths
	 * @return
	 */
	public static TermStructureMonteCarloSimulationModel getInterestRateModel(
			TimeDiscretization simulationTimeDiscretization,
			TimeDiscretization liborPeriodDiscretization,
			double[] zeroRateMaturities,
			double[] zeroRateRates,
			double shortRateVolatility, double shortRateMeanreversion, BrownianMotion brownianMotion) {
		/*
		 * Building the model by composing the different functions
		 */
		final LocalDate referenceDate = LocalDate.of(2017, 6, 15);

		// Create the forward curve (initial value of the LIBOR market model)
		final DiscountCurve discountCurve = DiscountCurveInterpolation.createDiscountCurveFromZeroRates(
				"discount curve",
				referenceDate,
				zeroRateMaturities	/* zero rate end points */,
				zeroRateRates	/* zero rates */,
				InterpolationMethod.LINEAR,
				ExtrapolationMethod.CONSTANT,
				InterpolationEntity.LOG_OF_VALUE_PER_TIME
				);

		AnalyticModel curveModel = new AnalyticModelFromCurvesAndVols(new Curve[] { discountCurve });

		// Create the discount curve
		final ForwardCurve forwardCurve2 = new ForwardCurveFromDiscountCurve(discountCurve.getName(), referenceDate, "6M");

		curveModel = new AnalyticModelFromCurvesAndVols(new Curve[] { discountCurve, forwardCurve2 });

		/*
		 * Create a volatility model: Hull white with constant coefficients (non time dep.).
		 */
		final ShortRateVolatilityModel volatilityModel = new ShortRateVolatilityModelAsGiven(
				new TimeDiscretizationFromArray(0.0),
				new double[] { shortRateVolatility } /* volatility */,
				new double[] { shortRateMeanreversion } /* meanReversion */);

		final Map<String, Object> properties = new HashMap<>();
		properties.put("isInterpolateDiscountFactorsOnLiborPeriodDiscretization", false);

		final LIBORModel hullWhiteModel = new HullWhiteModel(
				liborPeriodDiscretization, curveModel, forwardCurve2, discountCurve, volatilityModel, properties);

		final EulerSchemeFromProcessModel process = new EulerSchemeFromProcessModel(hullWhiteModel, brownianMotion, EulerSchemeFromProcessModel.Scheme.EULER);

		return new LIBORMonteCarloSimulationFromLIBORModel(process);

	}

	/**
	 * Hull White parameters (example: sigma = 0.02, a = 0.1 or sigma = 0.05, a = 0.5)
	 *
	 * final double shortRateVolatility = 0.005;	// Investigating LIBOR in Arrears, use a high volatility here (e.g. 0.1)
	 * final double shortRateMeanreversion = 0.1;
	 *
	 * @param zeroRateShortTerm
	 * @param zeroRateLongTerm
	 * @param shortRateVolatility
	 * @param shortRateMeanreversion
	 * @param numberOfPaths
	 * @return
	 */
	public static TermStructureMonteCarloSimulationModel getInterestRateModel(
			TimeDiscretization simulationTimeDiscretization,
			TimeDiscretization liborPeriodDiscretization,
			double[] zeroRateMaturities,
			double[] zeroRateRates,
			double shortRateVolatility, double shortRateMeanreversion, int numberOfPaths) {
		return getInterestRateModel(simulationTimeDiscretization, liborPeriodDiscretization, zeroRateMaturities, zeroRateRates, shortRateVolatility, shortRateMeanreversion, new BrownianMotionFromMersenneRandomNumbers(simulationTimeDiscretization, 2 /* numberOfFactors */, numberOfPaths, 3141 /* seed */));
	}
}

