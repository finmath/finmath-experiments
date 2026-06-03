package net.finmath.experiments.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.curves.DiscountCurveFromForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterpolation;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * Provides data for the calibration exercise.
 * 
 * @author Christian Fries
 */
public class LectureProjectData {

	//	final int numberOfPaths		= 5000;
	//	final int numberOfFactors	= 5;
	private static final double tenorPeriodLength = 0.5;

	public static void main(String[] args) {

		System.out.println("Forward rates:");
		ForwardCurve forwardCurve = getForwardCurve();
		for(double fixing = 0.0; fixing <= 50.0; fixing += 0.5) {
			System.out.print("   " + String.format("L(%.1f,%.1f;0.0) \t = %.4f", fixing, fixing+0.5, forwardCurve.getForward(null, fixing)));

			if(Math.ceil(fixing) == fixing) System.out.print("\t");
			else System.out.print("\n");
		}

		System.out.println("\n");

		System.out.println("Swaption calibration products:");
		List<Map<String,Object>> calibrationProductSpecs = getCalibrationProducts();

		for(Map<String, Object> calibrationProductSpec : calibrationProductSpecs) {
			System.out.println("   " + calibrationProductSpec);
		}

	}

	/**
	 * Return the discount curve associated with the forward curve.
	 * 
	 * @return The discount curve associated with the forward curve.
	 */
	public static DiscountCurve getDiscountCurve() {

		final ForwardCurve forwardCurve = getForwardCurve();
		final DiscountCurve discountCurve = new DiscountCurveFromForwardCurve(forwardCurve, tenorPeriodLength);

		return discountCurve;
	}

	/**
	 * Returns the forward curve.
	 * 
	 * @return The forward curve.
	 */
	public static ForwardCurve getForwardCurve() {

		/*
		 * Definition of curve
		 */
		final double[] fixingTimes = new double[] {
				0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5,
				10.0, 10.5, 11.0, 11.5, 12.0, 12.5, 13.0, 13.5, 14.0, 14.5, 15.0, 15.5, 16.0, 16.5, 17.0, 17.5, 18.0,
				18.5, 19.0, 19.5, 20.0, 20.5, 21.0, 21.5, 22.0, 22.5, 23.0, 23.5, 24.0, 24.5, 25.0, 25.5, 26.0, 26.5,
				27.0, 27.5, 28.0, 28.5, 29.0, 29.5, 30.0, 30.5, 31.0, 31.5, 32.0, 32.5, 33.0, 33.5, 34.0, 34.5, 35.0,
				35.5, 36.0, 36.5, 37.0, 37.5, 38.0, 38.5, 39.0, 39.5, 40.0, 40.5, 41.0, 41.5, 42.0, 42.5, 43.0, 43.5,
				44.0, 44.5, 45.0, 45.5, 46.0, 46.5, 47.0, 47.5, 48.0, 48.5, 49.0, 49.5, 50.0
		};

		final double[] forwardRates = new double[] {
				0.61 / 100.0, 0.61 / 100.0, 0.67 / 100.0, 0.73 / 100.0, 0.80 / 100.0, 0.92 / 100.0, 1.11 / 100.0,
				1.36 / 100.0, 1.60 / 100.0, 1.82 / 100.0, 2.02 / 100.0, 2.17 / 100.0, 2.27 / 100.0, 2.36 / 100.0,
				2.46 / 100.0, 2.52 / 100.0, 2.54 / 100.0, 2.57 / 100.0, 2.68 / 100.0, 2.82 / 100.0, 2.92 / 100.0,
				2.98 / 100.0, 3.00 / 100.0, 2.99 / 100.0, 2.95 / 100.0, 2.89 / 100.0, 2.82 / 100.0, 2.74 / 100.0,
				2.66 / 100.0, 2.59 / 100.0, 2.52 / 100.0, 2.47 / 100.0, 2.42 / 100.0, 2.38 / 100.0, 2.35 / 100.0,
				2.33 / 100.0, 2.31 / 100.0, 2.30 / 100.0, 2.29 / 100.0, 2.28 / 100.0, 2.27 / 100.0, 2.27 / 100.0,
				2.26 / 100.0, 2.26 / 100.0, 2.26 / 100.0, 2.26 / 100.0, 2.26 / 100.0, 2.26 / 100.0, 2.27 / 100.0,
				2.28 / 100.0, 2.28 / 100.0, 2.30 / 100.0, 2.31 / 100.0, 2.32 / 100.0, 2.34 / 100.0, 2.35 / 100.0,
				2.37 / 100.0, 2.39 / 100.0, 2.42 / 100.0, 2.44 / 100.0, 2.47 / 100.0, 2.50 / 100.0, 2.52 / 100.0,
				2.56 / 100.0, 2.59 / 100.0, 2.62 / 100.0, 2.65 / 100.0, 2.68 / 100.0, 2.72 / 100.0, 2.75 / 100.0,
				2.78 / 100.0, 2.81 / 100.0, 2.83 / 100.0, 2.86 / 100.0, 2.88 / 100.0, 2.91 / 100.0, 2.93 / 100.0,
				2.94 / 100.0, 2.96 / 100.0, 2.97 / 100.0, 2.97 / 100.0, 2.97 / 100.0, 2.97 / 100.0, 2.97 / 100.0,
				2.96 / 100.0, 2.95 / 100.0, 2.94 / 100.0, 2.93 / 100.0, 2.91 / 100.0, 2.89 / 100.0, 2.87 / 100.0,
				2.85 / 100.0, 2.83 / 100.0, 2.80 / 100.0, 2.78 / 100.0, 2.75 / 100.0, 2.72 / 100.0, 2.69 / 100.0,
				2.67 / 100.0, 2.64 / 100.0, 2.64 / 100.0
		};


		// Create the forward curve (initial value of the LIBOR market model)
		final ForwardCurve forwardCurve = ForwardCurveInterpolation.createForwardCurveFromForwards(
				"forwardCurve"		/* name of the curve */,
				fixingTimes			/* fixings of the forward */,
				forwardRates		/* forwards */,
				tenorPeriodLength	/* tenor / period length */
				);


		return forwardCurve;
	}

	/**
	 * Returns a list of maps, where each maps describes the properties a calibration product.
	 * 
	 * @return The list of calibration products, each products described by a property map.
	 */
	public static List<Map<String,Object>> getCalibrationProducts() {

		final DiscountCurve discountCurve = getDiscountCurve();
		final ForwardCurve forwardCurve = getForwardCurve();

		/*
		 * Create a set of calibration products.
		 */
		final List<Map<String,Object>> calibrationProducts = new ArrayList<>();

		final double	swapPeriodLength	= 0.5;
		final int		numberOfPeriods		= 20;

		final double[] smileMoneynesses	= { -0.02,	-0.01, -0.005, -0.0025,	0.0,	0.0025,	0.0050,	0.01,	0.02 };
		final double[] smileVolatilities	= { 0.559,	0.377,	0.335,	 0.320,	0.308, 0.298, 0.290, 0.280, 0.270 };

		for(int i=0; i<smileMoneynesses.length; i++ ) {
			final double	exerciseDate		= 5.0;
			final double	moneyness			= smileMoneynesses[i];
			final double	targetVolatility	= smileVolatilities[i];

			calibrationProducts.add(createCalibrationItem(exerciseDate, swapPeriodLength, numberOfPeriods, moneyness, targetVolatility, forwardCurve, discountCurve));
		}


		final double[] atmOptionMaturities	= { 2.00, 3.00, 4.00, 5.00, 7.00, 10.00, 15.00, 20.00, 25.00, 30.00 };
		final double[] atmOptionVolatilities	= { 0.385, 0.351, 0.325, 0.308, 0.288, 0.279, 0.290, 0.272, 0.235, 0.192 };

		for(int i=0; i<atmOptionMaturities.length; i++ ) {

			final double	exerciseDate		= atmOptionMaturities[i];
			final double	moneyness			= 0.0;
			final double	targetVolatility	= atmOptionVolatilities[i];

			calibrationProducts.add(createCalibrationItem(exerciseDate, swapPeriodLength, numberOfPeriods, moneyness, targetVolatility, forwardCurve, discountCurve));
		}

		return calibrationProducts;
	}

	private static Map<String, Object> createCalibrationItem(double exerciseDate, double swapPeriodLength, int numberOfPeriods, double moneyness, double targetVolatility, ForwardCurve forwardCurve, DiscountCurve discountCurve) {

		Map<String, Object> calibrationProductSpecification = new HashMap<>();

		calibrationProductSpecification.put("type", "swaption");

		final double[]	fixingDates			= new double[numberOfPeriods];
		final double[]	paymentDates		= new double[numberOfPeriods];
		final double[]	swapTenor			= new double[numberOfPeriods + 1];

		/*
		 * Regular tenor discretization
		 */
		for (int periodStartIndex = 0; periodStartIndex < numberOfPeriods; periodStartIndex++) {
			fixingDates[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
			paymentDates[periodStartIndex] = exerciseDate + (periodStartIndex + 1) * swapPeriodLength;
			swapTenor[periodStartIndex] = exerciseDate + periodStartIndex * swapPeriodLength;
		}
		swapTenor[numberOfPeriods] = exerciseDate + numberOfPeriods * swapPeriodLength;

		calibrationProductSpecification.put("swapStart", exerciseDate);
		calibrationProductSpecification.put("swapEnd", swapTenor[numberOfPeriods]);
		calibrationProductSpecification.put("swapPeriodLength", swapPeriodLength);


		// Swaptions swap rate
		final double swaprate = moneyness + getParSwaprate(forwardCurve, discountCurve, swapTenor);
		calibrationProductSpecification.put("swaprate", swaprate);


		calibrationProductSpecification.put("marketImpliedBlackVolatility", targetVolatility);

		return calibrationProductSpecification;
	}

	private static double getParSwaprate(final ForwardCurve forwardCurve, final DiscountCurve discountCurve, final double[] swapTenor) {
		return net.finmath.marketdata.products.Swap.getForwardSwapRate(new TimeDiscretizationFromArray(swapTenor), new TimeDiscretizationFromArray(swapTenor), forwardCurve, discountCurve);
	}
}
