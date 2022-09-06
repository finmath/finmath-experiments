package net.finmath.experiments.blackscholes;

import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import net.finmath.functions.AnalyticFormulas;
import net.finmath.plots.Named;
import net.finmath.plots.Plot;
import net.finmath.plots.Plot2D;

/**
 * Plot the shape of the implied log-normal volatility of values created from
 * displaced log-normal models.
 *
 * @author Christian Fries
 */
public class DisplaceLognormalImpliedVolExperiment {

	public static void main(String[] args) throws Exception {

		final double forward = 0.02;
		final double optionMaturity = 5.0;
		final double lognormalVolatility = 0.212;

		final double payoffUnit = 1.0;		// does not matter in this conversion

		final DoubleBinaryOperator volCurveForDisplacement = (optionStrike, displacement) -> {
			final double optionValue = AnalyticFormulas.blackScholesGeneralizedOptionValue(
					forward+displacement, lognormalVolatility, optionMaturity, optionStrike+displacement, payoffUnit);
			final double impliedLognormalVolatility = AnalyticFormulas.blackScholesOptionImpliedVolatility(forward, optionMaturity, optionStrike, payoffUnit, optionValue);
			return impliedLognormalVolatility;
		};

		final DoubleUnaryOperator volCurve0 = x -> volCurveForDisplacement.applyAsDouble(x, 0.00);
		final DoubleUnaryOperator volCurve1 = x -> volCurveForDisplacement.applyAsDouble(x, 0.01);
		final DoubleUnaryOperator volCurve2 = x -> volCurveForDisplacement.applyAsDouble(x, 0.02);

		final Plot plot = new Plot2D(0.01, 0.05, 100, List.of(
				new Named<>("0.00", volCurve0),
				new Named<>("0.01", volCurve1),
				new Named<>("0.02", volCurve2)
				));
		plot.setXAxisLabel("strike");
		plot.setYAxisLabel("implied lognormal vol");
		plot.setIsLegendVisible(true);
		plot.setTitle("Displacement");
		plot.show();
	}

}
