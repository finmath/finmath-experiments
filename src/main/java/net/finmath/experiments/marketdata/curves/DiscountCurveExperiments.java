package net.finmath.experiments.marketdata.curves;


import java.time.LocalDate;
import java.util.function.DoubleUnaryOperator;

import net.finmath.marketdata.model.AnalyticModel;
import net.finmath.marketdata.model.AnalyticModelFromCurvesAndVols;
import net.finmath.marketdata.model.curves.Curve;
import net.finmath.marketdata.model.curves.CurveInterpolation;
import net.finmath.marketdata.model.curves.CurveInterpolation.ExtrapolationMethod;
import net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationEntity;
import net.finmath.marketdata.model.curves.CurveInterpolation.InterpolationMethod;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.curves.DiscountCurveInterpolation;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveFromDiscountCurve;
import net.finmath.marketdata.products.AnalyticProduct;
import net.finmath.marketdata.products.SwapLeg;
import net.finmath.plots.Plot;
import net.finmath.plots.Plot2D;
import net.finmath.time.RegularSchedule;
import net.finmath.time.Schedule;
import net.finmath.time.ScheduleGenerator;
import net.finmath.time.ScheduleGenerator.DaycountConvention;
import net.finmath.time.ScheduleGenerator.Frequency;
import net.finmath.time.ScheduleGenerator.ShortPeriodConvention;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;
import net.finmath.time.businessdaycalendar.BusinessdayCalendar;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarAny;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarExcludingTARGETHolidays;
import net.finmath.time.businessdaycalendar.BusinessdayCalendar.DateRollConvention;

/**
 * 
 * Small experiments related to interest rate curves.
 * 
 * @author Christian Fries
 */
public class DiscountCurveExperiments {

	public static void main(String[] args) throws CloneNotSupportedException {

		testCurve();
		testSwapLeg();
		testSchedule();
	}

	private static void testSchedule() {
		final LocalDate referenceDate = LocalDate.of(2022, 11, 23);
		final LocalDate startDate = referenceDate.plusDays(0);
		final LocalDate maturityDate = startDate.plusYears(5);
		final Frequency frequency = Frequency.QUARTERLY;
		final DaycountConvention daycountConvention = DaycountConvention.ACT_365;
		final ShortPeriodConvention shortPeriodConvention = ShortPeriodConvention.FIRST;
		final BusinessdayCalendar businessdayCalendar = new BusinessdayCalendarAny();// BusinessdayCalendarExcludingTARGETHolidays();
		final DateRollConvention dateRollConvention = DateRollConvention.FOLLOWING;
		final int	fixingOffsetDays = 0;
		final int	paymentOffsetDay = 0;

		Schedule legSchedule = ScheduleGenerator.createScheduleFromConventions(referenceDate, startDate, maturityDate, frequency, daycountConvention, shortPeriodConvention, dateRollConvention, businessdayCalendar, fixingOffsetDays, paymentOffsetDay);
		double maturity = legSchedule.getPayment(legSchedule.getNumberOfPeriods()-1);

		DiscountCurve discountCurve = DiscountCurveInterpolation.createDiscountCurveFromZeroRates("EURSTR", referenceDate,
				new double[] { 1.0, maturity, 5.5, 6.0, 6.5 },
				new double[] { 0.05, 0.05, 0.05, 0.05, 0.05 },
				InterpolationMethod.CUBIC_SPLINE, ExtrapolationMethod.LINEAR, InterpolationEntity.LOG_OF_VALUE_PER_TIME);

		ForwardCurve forwardCurve = new ForwardCurveFromDiscountCurve(discountCurve.getName(), referenceDate, null);
		
		AnalyticModel model = new AnalyticModelFromCurvesAndVols(new Curve[] { discountCurve, forwardCurve });
				
		AnalyticProduct swapLegFloat = new SwapLeg(legSchedule, forwardCurve.getName(), 0.0, discountCurve.getName());
		
		double valueLegFloat = swapLegFloat.getValue(0.0, model);
		
		System.out.println("value P(T\u2081)-P(T\u2099)\t = " + (discountCurve.getValue(0.0) - discountCurve.getValue(5.0)));
		System.out.println("value float leg  \t = " + valueLegFloat);
				
		AnalyticProduct swapLegFixed = new SwapLeg(legSchedule, null, 0.05, discountCurve.getName());
		double valueLegFix = swapLegFixed.getValue(0.0, model);

		System.out.println("value fix   leg  \t = " + valueLegFix);
	}

	private static void testSwapLeg() {
		final LocalDate referenceDate = LocalDate.of(2022, 11, 23);
		
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, 10, 0.5);
		Schedule legSchedule = new RegularSchedule(timeDiscretization);
		
		double maturity = legSchedule.getPayment(legSchedule.getNumberOfPeriods()-1);

		DiscountCurve discountCurve = DiscountCurveInterpolation.createDiscountCurveFromZeroRates("EURSTR", referenceDate,
				new double[] { 1.0, maturity, 5.5, 6.0, 6.5 },
				new double[] { 0.05, 0.05, 0.05, 0.05, 0.05 },
				InterpolationMethod.CUBIC_SPLINE, ExtrapolationMethod.LINEAR, InterpolationEntity.LOG_OF_VALUE_PER_TIME);

		ForwardCurve forwardCurve = new ForwardCurveFromDiscountCurve(discountCurve.getName(), referenceDate, null);
		
		AnalyticModel model = new AnalyticModelFromCurvesAndVols(new Curve[] { discountCurve, forwardCurve });
				
		AnalyticProduct swapLegFloat = new SwapLeg(legSchedule, forwardCurve.getName(), 0.0, discountCurve.getName());
		
		double valueLegFloat = swapLegFloat.getValue(0.0, model);
		
		System.out.println("value P(T\u2081)-P(T\u2099)\t = " + (discountCurve.getValue(0.0) - discountCurve.getValue(5.0)));
		System.out.println("value float leg  \t = " + valueLegFloat);
				
		AnalyticProduct swapLegFixed = new SwapLeg(legSchedule, null, 0.05, discountCurve.getName());
		double valueLegFix = swapLegFixed.getValue(0.0, model);

		System.out.println("value fix   leg  \t = " + valueLegFix);

	}

	private static void testCurve() throws CloneNotSupportedException {
		Curve curve = new CurveInterpolation("curve", null,
				InterpolationMethod.PIECEWISE_CONSTANT,
				ExtrapolationMethod.CONSTANT, InterpolationEntity.VALUE,
				new double[] { 0.0, 1.0, 2.0, 5.0, 10.0 },		// arguments
				new double[] { 1.0, 0.9, 0.8, 0.6, 0.4 }		// values
		);
		
		plotCurve(curve);
	}

	/*
	private static void testCurveBuilder() throws CloneNotSupportedException {
		Curve curve = (new CurveInterpolation.Builder())
				.setInterpolationMethod(InterpolationMethod.PIECEWISE_CONSTANT)
				.setInterpolationEntity(InterpolationEntity.VALUE)
				.addPoint(0.0, 1.0, false)
				.addPoint(1.0, 0.9, true)
				.addPoint(2.0, 0.8, true)
				.addPoint(5.0, 0.6, true)
				.addPoint(10.0, 0.4, true)
				.build();
		
		plotCurve(curve);
	}
	*/

	private static void plotCurve(Curve curve) {
		DoubleUnaryOperator interpolation = x -> curve.getValue(x);
		
		(new Plot2D(0.0, 15.0, interpolation)).show();
	}
	
	
	
}
