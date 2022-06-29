/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 */
package net.finmath.experiments.montecarlo.interestrates;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.interestrate.TermStructureMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.products.AbstractTermStructureMonteCarloProduct;
import net.finmath.montecarlo.interestrate.products.Bond;
import net.finmath.montecarlo.interestrate.products.Caplet;
import net.finmath.montecarlo.interestrate.products.TermStructureMonteCarloProduct;
import net.finmath.opencl.montecarlo.RandomVariableOpenCLFactory;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.plots.Plot;
import net.finmath.plots.PlotProcess2D;
import net.finmath.plots.Plots;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * This class visualizes some of the numerical errors associated with a Monte-Carlo simulation
 * of an Euler scheme approximation of a discrete forward rate term structure model (LIBOR Market Model)
 * and its relation to the equivalent martingale measure.
 *
 * @author Christian Fries
 */
public class TermStructureMonteCarloSimulationExperiments {

	private static final int	numberOfPaths	= 50000;
	private static final int	numberOfFactors	= 1;
	private static final int	seed			= 3141;

	public static void main(String[] args) throws Exception {
		//		(new TermStructureMonteCarloSimulationExperiments()).testBondUnderMeasure();
		//		(new TermStructureMonteCarloSimulationExperiments()).testForwardRateUnderMeasure();
		//		(new TermStructureMonteCarloSimulationExperiments()).testCapletSmile();
		//		(new TermStructureMonteCarloSimulationExperiments()).testCapletSmiles();
				(new TermStructureMonteCarloSimulationExperiments()).testShortRate();
		//		(new TermStructureMonteCarloSimulationExperiments()).testCapletATMImpliedVol();
		//		(new TermStructureMonteCarloSimulationExperiments()).testCapletATMImpliedVolInterpolation();
		//		(new TermStructureMonteCarloSimulationExperiments()).testCapletSmilesOnGPU();
	}

	public TermStructureMonteCarloSimulationExperiments() throws CalculationException {}

	public void testBondUnderMeasure() throws Exception {
		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		final double forwardRate = 0.05;				// constant forward rate
		final double periodLength = 0.5;				// semi-annual tenor discretization
		final double volatility = 0.30;					// constant volatility
		final double localVolNormalityBlend = 0.0;		// Lognormal model
		final double correlationDecayParam = 0.0;		// one factor, correlation of all drivers is 1

		for(final Boolean useDiscountCurve : new Boolean[] { false, true }) {

			for(final String measure : new String[] { "terminal", "spot"}) {
				
				final TermStructureMonteCarloSimulationModel simulationModel = ModelFactory.createTermStuctureModel(
						randomVariableFactory,
						measure,
						forwardRate,
						periodLength,
						useDiscountCurve,
						volatility,
						localVolNormalityBlend,
						correlationDecayParam,
						numberOfFactors,
						numberOfPaths, seed);

				final List<Double> maturities = new ArrayList<Double>();
				final List<Double> errors = new ArrayList<Double>();

				for(double maturity = 0.5; maturity < 20; maturity += 0.5) {
					final TermStructureMonteCarloProduct product = new Bond(maturity);
					final double value = product.getValue(simulationModel);
					final double yieldMonteCarlo = -Math.log(value)/maturity;

					final double valueAnalytic = 1.0/Math.pow((1+forwardRate*periodLength), maturity/periodLength);
					final double yieldAnalytic = -Math.log(valueAnalytic)/maturity;

					maturities.add(maturity);
					errors.add(yieldMonteCarlo-yieldAnalytic);
				}

				final Plot plot = Plots.createScatter(maturities, errors, 0.0, 0.2, 5)
						.setTitle("Zero bond error when using " + measure + " measure" + (useDiscountCurve ? " and numeraire control variate." : "."))
						.setXAxisLabel("maturity")
						.setYAxisLabel("error")
						.setYAxisNumberFormat(new DecimalFormat("0.0E00"));

				final String filename = "BondDiscretizationError-measure-" + measure + (useDiscountCurve ? "-with-control" : "");
				plot.saveAsSVG(new File(filename + ".svg"), 900, 400);
				plot.saveAsPDF(new File(filename + ".pdf"), 900, 400);
				plot.show();
			}
		}
	}

	private void testForwardRateUnderMeasure() throws Exception {
		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		final double forwardRate = 0.05;				// constant forward rate
		final double periodLength = 0.5;				// semi-annual tenor discretization
		final double volatility = 0.30;					// constant volatility
		final double localVolNormalityBlend = 0.0;		// 0 = lognormal model and 1 = normal model
		final double correlationDecayParam = 0.0;		// one factor, correlation of all drivers is 1

		for(final String measure : new String[] { "terminal", "spot"}) {
						
			for(final Boolean useDiscountCurve : new Boolean[] { false, true }) {
			
				final TermStructureMonteCarloSimulationModel lmm = ModelFactory.createTermStuctureModel(
						randomVariableFactory,
						measure,
						forwardRate,
						periodLength,
						useDiscountCurve,
						volatility,
						localVolNormalityBlend,
						correlationDecayParam,
						numberOfFactors,
						numberOfPaths, seed);

				final List<Double> fixings = new ArrayList<Double>();
				final List<Double> errors = new ArrayList<Double>();

				for(double fixing = 0.5; fixing < 20; fixing += 0.5) {
					final TermStructureMonteCarloProduct productForwardRate = new ForwardRate(fixing, fixing, fixing+periodLength, fixing+periodLength, periodLength);
					final TermStructureMonteCarloProduct productBond = new Bond(fixing+periodLength);

					final double valueBondAnalytic = 1.0/Math.pow((1+forwardRate*periodLength), (fixing+periodLength)/periodLength);
					final double value = productForwardRate.getValue(lmm) / valueBondAnalytic;
//					final double value = productForwardRate.getValue(lmm) / productBond.getValue(lmm);

					final double valueAnalytic = forwardRate * periodLength;

					fixings.add(fixing);
					errors.add(value-valueAnalytic);
				}

				final Plot plot = Plots.createScatter(fixings, errors, 0.0, 0.2, 5)
						.setTitle("Forward rate error using " + measure + " measure" + (useDiscountCurve ? " and numeraire control variate." : "."))
						.setXAxisLabel("fixing")
						.setYAxisLabel("error")
						.setYAxisNumberFormat(new DecimalFormat("0.0E00"));

				final String filename = "ForwardRateDiscretizationError-measure-" + measure + (useDiscountCurve ? "-with-control" : "");
//				plot.saveAsSVG(new File(filename + ".svg"), 900, 400);
//				plot.saveAsPDF(new File(filename + ".pdf"), 900, 400);

				plot.show();
			}
		}
	}
	
	public void testCapletSmile() throws Exception {

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		final String simulationTimeInterpolationMethod = "round_down";
		final double forwardRate = 0.05;
		final double periodLength = 0.5;
		final boolean useDiscountCurve = false;
		final double volatility = 0.30;					// constant volatility
		final double localVolNormalityBlend = 0.0;		// 0 = lognormal model and 1 = normal model
		final double correlationDecayParam = 0.0;		// one factor, correlation of all drivers is 1

		for(final String measure : new String[] { "terminal", "spot"}) {
			final TermStructureMonteCarloSimulationModel lmm = ModelFactory.createTermStuctureModel(
					randomVariableFactory,
					measure,
					simulationTimeInterpolationMethod,
					forwardRate,
					periodLength,
					useDiscountCurve,
					volatility,
					localVolNormalityBlend,
					correlationDecayParam,
					numberOfFactors,
					numberOfPaths, seed);

			/*
			 * Value different products
			 */
			final double maturity = 5.0;
			final List<Double> strikes = new ArrayList<Double>();
			final List<Double> impliedVolatilities = new ArrayList<Double>();

			for(double strike = 0.025; strike < 0.10; strike += 0.0025) {

				final TermStructureMonteCarloProduct product = new Caplet(maturity, periodLength, strike);
				final double value = product.getValue(lmm);

				/*
				 * Conversion to implied volatility
				 */
				// Determine the zero bond at payment (numerically)
				final TermStructureMonteCarloProduct bondAtPayment = new Bond(maturity+periodLength);
				final double discountFactor = bondAtPayment.getValue(lmm);

				// Determine the forward rate at fixing (numerically)
				final TermStructureMonteCarloProduct forwardRateProduct = new ForwardRate(maturity, periodLength, periodLength);
				final double forward = forwardRateProduct.getValue(lmm) / discountFactor / periodLength;

				final double optionMaturity = maturity;
				final double optionStrike = strike;
				final double payoffUnit = discountFactor * periodLength;
				final double optionValue = value;
				final double impliedVol = AnalyticFormulas.blackScholesOptionImpliedVolatility(forward, optionMaturity, optionStrike, payoffUnit, optionValue);

				strikes.add(strike);
				impliedVolatilities.add(impliedVol);
			}

			final Plot plot = Plots.createScatter(strikes, impliedVolatilities, 0.0, 0.2, 5)
			.setTitle("Caplet (lognormal) implied volatility using lognormal model (" + measure + " )")
			.setXAxisLabel("strike")
			.setYAxisLabel("implied volatility")
			.setYRange(0.15, 0.45)
			.setXAxisNumberFormat(new DecimalFormat("0.0%"))
			.setYAxisNumberFormat(new DecimalFormat("0.0%"));

			final String filename = "Caplet-implied-vol-measure-" + measure;
			plot.saveAsPDF(new File(filename + ".pdf"), 900, 400);

			plot.show();
		}
	}

	public void testCapletSmiles() throws Exception {

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		final String simulationTimeInterpolationMethod = "round_down";
		final String measure = "spot";
		final double forwardRate = 0.05;
		final double periodLength = 0.5;
		final boolean useDiscountCurve = false;
		final double volatility = 0.30;					// constant volatility
		final double correlationDecayParam = 0.0;		// one factor, correlation of all drivers is 1

		final List<Double> strikes = new ArrayList<Double>();
		final Map<String, List<Double>> impliedVolCurves = new HashMap<>();
		for(double normality = 0.0; normality <= 1.0; normality += 0.1) {		// 0 = lognormal model and 1 = normal model
			
			final TermStructureMonteCarloSimulationModel lmm = ModelFactory.createTermStuctureModel(
					randomVariableFactory, measure, simulationTimeInterpolationMethod,
					forwardRate, periodLength, useDiscountCurve, volatility, normality, correlationDecayParam,
					numberOfFactors, numberOfPaths, seed);

			/*
			 * Value different products
			 */
			final double maturity = 5.0;

			final List<Double> impliedVolatilities = new ArrayList<Double>();
			for(double strike = 0.025; strike < 0.10; strike += 0.0025) {

				final TermStructureMonteCarloProduct product = new Caplet(maturity, periodLength, strike);
				final double value = product.getValue(lmm);

				/*
				 * Conversion to implied volatility
				 */
				// Determine the zero bond at payment (numerically)
				final TermStructureMonteCarloProduct bondAtPayment = new Bond(maturity+periodLength);
				final double discountFactor = bondAtPayment.getValue(lmm);

				// Determine the forward rate at fixing (numerically)
				final TermStructureMonteCarloProduct forwardRateProduct = new ForwardRate(maturity, periodLength, periodLength);
				final double forward = forwardRateProduct.getValue(lmm) / discountFactor / periodLength;

				final double optionMaturity = maturity;
				final double optionStrike = strike;
				final double payoffUnit = discountFactor * periodLength;
				final double optionValue = value;
				final double impliedVol = AnalyticFormulas.blackScholesOptionImpliedVolatility(forward, optionMaturity, optionStrike, payoffUnit, optionValue);

				strikes.add(strike);
				impliedVolatilities.add(impliedVol);
			}
			impliedVolCurves.putIfAbsent(String.valueOf(normality), impliedVolatilities);

		}
		
		Plot plot = Plots.createScatter(strikes, impliedVolCurves, 0.0, 0.2, 5)
		.setTitle("Caplet (lognormal) implied volatility using different displacements (" + measure + " measure)")
		.setXAxisLabel("strike").setYAxisLabel("implied volatility")
		.setYRange(0.15, 0.45).setXAxisNumberFormat(new DecimalFormat("0.0%")).setYAxisNumberFormat(new DecimalFormat("0.0%"));

		final String filename = "Caplet-implied-vol-for-displacement-measure-" + measure;
		plot.saveAsPDF(new File(filename + ".pdf"), 900, 400);

		plot.show();
	}

	public void testShortRate() throws Exception {
		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		final double forwardRate = 0.05;				// constant forward rate
		final double periodLength = 0.5;				// semi-annual tenor discretization
		final double volatility = 0.20;					// constant volatility
		final double localVolNormalityBlend = 0.0;		// Lognormal model
		final double correlationDecayParam = 0.0;		// one factor, correlation of all drivers is 1
		final Boolean useDiscountCurve = false; 

		for(final String measure : new String[] { "spot"}) {

			for(final double volatilityExponentialDecay : new double[] { 0.0, 0.04, 0.08, 0.12 }) {

				final TermStructureMonteCarloSimulationModel forwardRateModel = ModelFactory.createTermStuctureModel(
						randomVariableFactory,
						measure,
						forwardRate,
						periodLength,
						useDiscountCurve,
						volatility,
						volatilityExponentialDecay,
						localVolNormalityBlend,
						correlationDecayParam,
						numberOfFactors,
						numberOfPaths, seed);

				DoubleToRandomVariableFunction shortRateProcess = time -> forwardRateModel.getForwardRate(time, time, time+periodLength);
				
				PlotProcess2D plot = new PlotProcess2D(new TimeDiscretizationFromArray(forwardRateModel.getTimeDiscretization().getAsArrayList().subList(0, forwardRateModel.getTimeDiscretization().getNumberOfTimes()-1)),
						shortRateProcess, 100);
				plot.setTitle("Paths of the (discretized) short rate T ⟼ L(T,T+\u0394T;T) (" + measure + ", vol decay: " + volatilityExponentialDecay + ")").setXAxisLabel("time").setYAxisLabel("forward rate").setYAxisNumberFormat(new DecimalFormat("#.##%")).show();

				/*
				final String filename = "ForwardRateDiscretizationError-measure-" + measure + (useDiscountCurve ? "-with-control" : "");
				plot.saveAsSVG(new File(filename + ".svg"), 900, 400);
				plot.saveAsPDF(new File(filename + ".pdf"), 900, 400);

				plot.show();
				*/
			}
		}
	}

	public void testCapletATMImpliedVol() throws Exception {

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		final String measure = "spot";
		final String simulationTimeInterpolationMethod = "round_down";
		final double forwardRate = 0.05;
		final double periodLength = 0.5;
		final boolean useDiscountCurve = false;

		final double volatility = 0.30;

		final TermStructureMonteCarloSimulationModel lmm = ModelFactory.createTermStuctureModel(
				randomVariableFactory,
				measure,
				simulationTimeInterpolationMethod,
				forwardRate,
				periodLength,
				useDiscountCurve,
				volatility, 0.0, 0.0,
				numberOfFactors,
				numberOfPaths, seed);

		final List<Double> maturities = new ArrayList<Double>();
		final List<Double> impliedVolatilities = new ArrayList<Double>();

		final double strike = forwardRate;
		for(double maturity = 0.5; maturity <= 19.5; maturity += 0.01) {
			final TermStructureMonteCarloProduct product = new Caplet(maturity, periodLength, strike);
			final double value = product.getValue(lmm);

			// Determine the zero bond at payment (numerically)
			final TermStructureMonteCarloProduct bondAtPayment = new Bond(maturity+periodLength);
			final double discountFactor = bondAtPayment.getValue(lmm);

			// Determine the forward rate at fixing (numerically)
			final TermStructureMonteCarloProduct forwardRateProduct = new ForwardRate(maturity, periodLength, periodLength);
			final double forward = forwardRateProduct.getValue(lmm) / discountFactor / periodLength;

			final double impliedVol = AnalyticFormulas.blackModelCapletImpliedVolatility(forward, maturity, strike, periodLength, discountFactor, value);

			maturities.add(maturity);
			impliedVolatilities.add(impliedVol);
		}

		final Plot plot = Plots.createScatter(maturities, impliedVolatilities, 0.0, 0.2, 5)
				.setTitle("Caplet implied volatility")
				.setXAxisLabel("maturity")
				.setYAxisLabel("implied volatility")
				.setYRange(0.1, 0.5)
				.setYAxisNumberFormat(new DecimalFormat("0.0%"));
		plot.show();

		final String filename = "Caplet-Impliled-Vol" + measure + (useDiscountCurve ? "-with-control" : "");
		plot.saveAsSVG(new File(filename + ".svg"), 900, 400);
	}

	public void testCapletATMImpliedVolInterpolation() throws Exception {

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

		final String measure = "spot";
		final double forwardRate = 0.05;
		final double periodLength = 0.5;
		final boolean useDiscountCurve = false;

		for(final String simulationTimeInterpolationMethod : new String[] { "round_down", "round_nearest" }) {
			final TermStructureMonteCarloSimulationModel lmm = ModelFactory.createTermStuctureModel(
					randomVariableFactory,
					measure,
					simulationTimeInterpolationMethod,
					forwardRate,
					periodLength,
					useDiscountCurve,
					0.30, 0.0, 0.0,
					numberOfFactors,
					numberOfPaths, seed);

			final List<Double> maturities = new ArrayList<Double>();
			final List<Double> impliedVolatilities = new ArrayList<Double>();

			final double strike = forwardRate;
			for(double maturity = 0.5; maturity <= 19.5; maturity += 0.01) {
				final TermStructureMonteCarloProduct product = new Caplet(maturity, periodLength, strike);
				final double value = product.getValue(lmm);

				// Determine the zero bond at payment (numerically)
				final TermStructureMonteCarloProduct bondAtPayment = new Bond(maturity+periodLength);
				final double discountFactor = bondAtPayment.getValue(lmm);

				// Determine the forward rate at fixing (numerically)
				final TermStructureMonteCarloProduct forwardRateProduct = new ForwardRate(maturity, periodLength, periodLength);
				final double forward = forwardRateProduct.getValue(lmm) / discountFactor / periodLength;

				final double impliedVol = AnalyticFormulas.blackModelCapletImpliedVolatility(forward, maturity, strike, periodLength, discountFactor, value);

				maturities.add(maturity);
				impliedVolatilities.add(impliedVol);
			}

			final Plot plot = Plots.createScatter(maturities, impliedVolatilities, 0.0, 0.2, 5)
					.setTitle("Caplet implied volatility using simulation time interpolation " + simulationTimeInterpolationMethod + ".")
					.setXAxisLabel("maturity")
					.setYAxisLabel("implied volatility")
					.setYRange(0.1, 0.5)
					.setYAxisNumberFormat(new DecimalFormat("0.0%"));
			plot.show();

			final String filename = "Caplet-Impliled-Vol-" + simulationTimeInterpolationMethod;
			plot.saveAsSVG(new File(filename + ".svg"), 900, 400);
		}
	}

	public void testCapletSmilesOnGPU() throws CalculationException {

		final String simulationTimeInterpolationMethod = "round_down";
		final String measure = "spot";
		final double forwardRate = 0.05;
		final double periodLength = 0.5;
		final boolean useDiscountCurve = false;
		final int		numberOfPaths	= 100000;

		for(final RandomVariableFactory randomVariableFactory : List.of(
				new RandomVariableFromArrayFactory(),
//				new RandomVariableCudaFactory(),
				new RandomVariableOpenCLFactory())) {
			final long timeStart = System.currentTimeMillis();

			final List<Double> strikes = new ArrayList<Double>();
			final Map<String, List<Double>> impliedVolCurves = new HashMap();
			for(double normality = 0.0; normality <= 1.0; normality += 0.5) {
				final TermStructureMonteCarloSimulationModel lmm = ModelFactory.createTermStuctureModel(
						randomVariableFactory,
						measure,
						simulationTimeInterpolationMethod,
						forwardRate,
						periodLength,
						useDiscountCurve,
						0.30, normality, 0.0,
						numberOfFactors,
						numberOfPaths, seed);

				final List<Double> impliedVolatilities = new ArrayList<Double>();
				for(double strike = 0.025; strike < 0.10; strike += 0.001) {
					final TermStructureMonteCarloProduct product = new Caplet(5.0, 0.5, strike);
					final double value = product.getValue(lmm);

					final double forward = 0.05;
					final double optionMaturity = 5.0;
					final AbstractTermStructureMonteCarloProduct bondAtPayment = new Bond(5.5);
					final double optionStrike = strike;
					final double payoffUnit = bondAtPayment.getValue(lmm);
					final double optionValue = value;
					final double impliedVol = AnalyticFormulas.blackModelCapletImpliedVolatility(forwardRate, optionMaturity, optionStrike, periodLength, payoffUnit, optionValue);

					strikes.add(strike);
					impliedVolatilities.add(impliedVol);
				}
				impliedVolCurves.putIfAbsent(String.valueOf(normality), impliedVolatilities);
			}

			final long timeEnd = System.currentTimeMillis();

			System.out.println("Calculation time: "+ ((timeEnd-timeStart)/1000) + " \t" + randomVariableFactory.getClass().getSimpleName());

			Plots.createScatter(strikes, impliedVolCurves, 0.0, 0.2, 5)
			.setTitle("Caplet implied volatility using " + measure + " measure.")
			.setXAxisLabel("strike")
			.setYAxisLabel("implied volatility")
			.setYRange(0.1, 0.5)
			.setYAxisNumberFormat(new DecimalFormat("0.0%")).show();
		}
	}
}
