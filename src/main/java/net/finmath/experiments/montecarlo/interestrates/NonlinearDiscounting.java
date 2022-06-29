package net.finmath.experiments.montecarlo.interestrates;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.finmath.exception.CalculationException;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.marketdata.model.AnalyticModelFromCurvesAndVols;
import net.finmath.marketdata.model.curves.Curve;
import net.finmath.marketdata.model.curves.DiscountCurveInterpolation;
import net.finmath.marketdata.model.curves.ForwardCurveInterpolation;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloBlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BachelierModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.products.AbstractAssetMonteCarloProduct;
import net.finmath.montecarlo.assetderivativevaluation.products.EuropeanOption;
import net.finmath.montecarlo.assetderivativevaluation.products.ForwardAgreement;
import net.finmath.montecarlo.assetderivativevaluation.products.ForwardAgreementWithFundingRequirement;
import net.finmath.montecarlo.interestrate.CalibrationProduct;
import net.finmath.montecarlo.interestrate.LIBORMarketModel;
import net.finmath.montecarlo.interestrate.LIBORModelMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.LIBORMonteCarloSimulationFromLIBORModel;
import net.finmath.montecarlo.interestrate.models.FundingCapacity;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel.Measure;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCorrelationModelExponentialDecay;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORCovarianceModelFromVolatilityAndCorrelation;
import net.finmath.montecarlo.interestrate.models.covariance.LIBORVolatilityModelFromGivenMatrix;
import net.finmath.montecarlo.interestrate.products.SwapLegWithFundingProvider;
import net.finmath.montecarlo.interestrate.products.components.Notional;
import net.finmath.montecarlo.interestrate.products.components.NotionalFromConstant;
import net.finmath.montecarlo.interestrate.products.indices.AbstractIndex;
import net.finmath.montecarlo.interestrate.products.indices.LIBORIndex;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.plots.GraphStyle;
import net.finmath.plots.Named;
import net.finmath.plots.Plot;
import net.finmath.plots.Plot2D;
import net.finmath.plots.Plotable2D;
import net.finmath.plots.PlotableFunction2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.plots.Point2D;
import net.finmath.rootfinder.RiddersMethod;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;
import net.finmath.time.Schedule;
import net.finmath.time.ScheduleGenerator;
import net.finmath.time.ScheduleGenerator.DaycountConvention;
import net.finmath.time.ScheduleGenerator.Frequency;
import net.finmath.time.ScheduleGenerator.ShortPeriodConvention;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;
import net.finmath.time.businessdaycalendar.BusinessdayCalendar;
import net.finmath.time.businessdaycalendar.BusinessdayCalendar.DateRollConvention;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarExcludingTARGETHolidays;

/**
 * This program conducts some analysis related to non-linear discounting / compensation as
 * described in "Discounting Damage" (2020).
 *
 * The program generates the plots of the paper.
 *
 * @author Christian Fries
 */
public class NonlinearDiscounting {

	private static int numberOfPaths = 1000000;
	private static String currency = "EUR";

	public static void main(String[] args) throws Exception {

		final NonlinearDiscounting nld = new NonlinearDiscounting();
		//		nld.testLMM();
		//		nld.testLMMByMaturity(0.5);
		//		nld.testLMMByMaturity(1.0);
		//		nld.testContinousFlowByMaturity();
		nld.testBSForwardExchangeByVolatility(0.0);
		nld.testBSForwardExchangeByVolatility(1.0);
		nld.testBSSequenceOfForwardExchangeByMaturity(0.0);
		nld.testBSSequenceOfForwardExchangeByMaturity(1.3);
	}

	enum Key {
		VOLATILITY,
		MATURITY,
		RISK_FREE,
		FUNDING_CONSTANT,
		FUNDING_STATE_DEP,
		OPTION
	}

	public void testBSForwardExchangeByVolatility(final double forwardValue) throws Exception {

		final double maturity = 5.0;
		final double survivalProbLevel = 1.5;

		final double initialValue = 1.0;
		final double riskFreeRate = 0.05;
		final double volatility = 0.20;
		final int numberOfFactors = 1;
		final int seed = 3216;

		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0, maturity);
		final BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, seed);

		final BlackScholesModel blackScholesModel = new BlackScholesModel(initialValue, riskFreeRate, volatility);
		final MonteCarloProcess process = new EulerSchemeFromProcessModel(blackScholesModel, brownianMotion);
		final MonteCarloAssetModel model = new MonteCarloAssetModel(process);

		/*
		 * Survival probabilities
		 */
		final SortedMap<Double, Double> instSurvivalProb = new TreeMap<Double, Double>();
		instSurvivalProb.put(survivalProbLevel, 1.0);
		instSurvivalProb.put(Double.MAX_VALUE, 0.75);

		final SortedMap<Double, Double> instSurvivalProbConst = new TreeMap<Double, Double>();
		instSurvivalProbConst.put(0.0, 0.75);
		instSurvivalProbConst.put(Double.MAX_VALUE, 0.75);


		final DoubleStream volatilities = IntStream.range(0, 100).mapToDouble(x -> x*0.7/100);

		final List<Map<Key,Double>> valuations = volatilities.mapToObj(
				newVolatility -> {
					try {
						final MonteCarloAssetModel newModel = model.getCloneWithModifiedData(Map.of("volatility", newVolatility));


						final double valueRiskFree = new ForwardAgreement(maturity, forwardValue, 0).getValue(newModel);
						final double valueOption = new EuropeanOption(maturity, survivalProbLevel+forwardValue, 0).getValue(newModel);

						final Map<Key, Double> value = Map.of(
								Key.VOLATILITY, newVolatility,
								Key.RISK_FREE, valueRiskFree,
								Key.FUNDING_CONSTANT, new ForwardAgreementWithFundingRequirement(maturity, forwardValue, 0, new FundingCapacity(currency, new Scalar(0.0), instSurvivalProbConst)).getValue(newModel),
								Key.FUNDING_STATE_DEP, new ForwardAgreementWithFundingRequirement(maturity, forwardValue, 0, new FundingCapacity(currency, new Scalar(0.0), instSurvivalProb)).getValue(newModel),
								Key.OPTION, valueRiskFree+(1.0/0.75-1.0)*valueOption
								);
						return value;
					} catch (final CalculationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return null;
				}).collect(Collectors.toList());

		final BiFunction<Key, Key, Function<Map<Key,Double>,Point2D>> mapElementToPoint = (key,value) -> (x -> new Point2D(x.get(key),x.get(value)));

		final List<Point2D> valuesRiskFree = valuations.stream().map(mapElementToPoint.apply(Key.VOLATILITY, Key.RISK_FREE)).collect(Collectors.toList());
		final List<Point2D> valuesFundConst = valuations.stream().map(mapElementToPoint.apply(Key.VOLATILITY, Key.FUNDING_CONSTANT)).collect(Collectors.toList());
		final List<Point2D> valuesFundStateDep = valuations.stream().map(mapElementToPoint.apply(Key.VOLATILITY, Key.FUNDING_STATE_DEP)).collect(Collectors.toList());
		final List<Point2D> valuesOptions = valuations.stream().map(mapElementToPoint.apply(Key.VOLATILITY, Key.OPTION)).collect(Collectors.toList());

		final Plot plot = new Plot2D(List.of(
				new PlotablePoints2D("default compensated (state-dependent survival prob.)", valuesFundStateDep, new GraphStyle(new Rectangle(3, 3)))
				, new PlotablePoints2D("risk free", valuesRiskFree, new GraphStyle(new Rectangle(3, 3)))
				, new PlotablePoints2D("default compensated (constant survival prob.)", valuesFundConst, new GraphStyle(new Rectangle(3, 3)))
				, new PlotablePoints2D("benchmark (using option)", valuesOptions, new GraphStyle(new Rectangle(5, 5)))
				));
		plot.setIsLegendVisible(true);
		plot.setTitle("volatility dependency of compensation cost");
		plot.setXAxisLabel("volatility").setYAxisLabel("value");
		plot.show();
		plot.saveAsPDF(new File("volatility-dependency-of-compensation-cost-" + forwardValue + ".pdf"), 900, 600);
	}

	/**
	 * @param forwardValue Use 0.0 or 1.30.
	 * @throws Exception
	 */
	public void testBSSequenceOfForwardExchangeByMaturity(final double forwardValue) throws Exception {

		final double timeHorizon = 15.0;
		final double dt = 0.5;
		final double initialValue = 1.0;
		final double riskFreeRate = 0.05;
		final double volatility = 0.30;
		final int numberOfFactors = 1;
		final int seed = 3216;

		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0d, (int)Math.round(timeHorizon/dt), dt);
		final BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, seed);

		final MonteCarloBlackScholesModel model = new MonteCarloBlackScholesModel(initialValue, riskFreeRate, volatility, brownianMotion);

		final double survivalProbLevel = 10.0;//1.5;

		/*
		 * Survival probabilities
		 */
		final SortedMap<Double, Double> instSurvivalProb = new TreeMap<Double, Double>();
		instSurvivalProb.put(survivalProbLevel, 1.0);
		instSurvivalProb.put(Double.MAX_VALUE, 0.75);

		final SortedMap<Double, Double> instSurvivalProbConst = new TreeMap<Double, Double>();
		instSurvivalProbConst.put(0.0, 0.75);
		instSurvivalProbConst.put(Double.MAX_VALUE, 0.75);

		final FundingCapacity fundingCapacity = new FundingCapacity("EUR", new Scalar(0.0), instSurvivalProb);
		final FundingCapacity fundingCapacityConstant = new FundingCapacity("EUR", new Scalar(0.0), instSurvivalProbConst);

		final Stream<Map<String, Double>> values = IntStream.range(0, 20).mapToDouble(x -> x*0.5).mapToObj(
				maturity -> {
					try {
						//						double maturity = 5.0;
						final AbstractAssetMonteCarloProduct product = new ForwardAgreement(maturity, forwardValue, 0);
						final AbstractAssetMonteCarloProduct product2 = new ForwardAgreementWithFundingRequirement(maturity, forwardValue, 0, fundingCapacityConstant);
						final AbstractAssetMonteCarloProduct product3 = new ForwardAgreementWithFundingRequirement(maturity, forwardValue, 0, fundingCapacity);
						final AbstractAssetMonteCarloProduct product4 = new EuropeanOption(maturity, survivalProbLevel+forwardValue, 0);

						final double analytic = AnalyticFormulas.blackScholesOptionValue(initialValue, riskFreeRate, volatility, maturity, survivalProbLevel+forwardValue);
						final Map<String, Double> value = Map.of(
								"maturity", maturity,
								"forward.plain", product.getValue(model),
								"forward.funding.fixed", product2.getValue(model),
								"forward.funding", product3.getValue(model),
								"european.plain", product4.getValue(model)
								);
						return value;
					} catch (final CalculationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return null;
				});

		final List<Point2D> valueSwapPlain = new ArrayList<Point2D>();
		final List<Point2D> valueSwapFunding = new ArrayList<Point2D>();
		final List<Point2D> valueSwapFundingHalf = new ArrayList<Point2D>();

		values.forEach(value -> {
			System.out.println(value);
			valueSwapPlain.add(new Point2D(value.get("maturity"), value.get("forward.plain")));
			valueSwapFundingHalf.add(new Point2D(value.get("maturity"), value.get("forward.funding.fixed")));
			valueSwapFunding.add(new Point2D(value.get("maturity"), value.get("forward.funding")));
		});

		final Plot plot = new Plot2D(List.of(
				new PlotablePoints2D("default compensated (state-dependent)", valueSwapFunding, new GraphStyle(new Rectangle(3, 3))),
				new PlotablePoints2D("risk free", valueSwapPlain, new GraphStyle(new Rectangle(3, 3))),
				new PlotablePoints2D("default compensated (constant)", valueSwapFundingHalf, new GraphStyle(new Rectangle(3, 3)))
				));
		plot.setIsLegendVisible(true);
		plot.setTitle("maturity dependency of comensation cost (conditional to previous compensations)");
		plot.setXAxisLabel("maturity").setYAxisLabel("value");
		plot.show();
		plot.saveAsPDF(new File("maturity-dependency-of-comensation-cost-X-" + forwardValue + ".pdf"), 900, 600);
	}

	public void testContinousFlowByMaturity() throws Exception {

		final double timeHorizon = 10.0;
		final double dt = 0.025;
		final double initialValue = 0.0;
		final double riskFreeRate = 0.00;
		final double volatility = 0.20;
		final int numberOfFactors = 1;
		final int seed = 3216;

		final int numberOfPaths = 20000;

		final TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(0.0d, (int)(timeHorizon/dt), dt);
		final BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, seed);

		final BachelierModel bachelierModel = new BachelierModel(initialValue, riskFreeRate, volatility);
		final MonteCarloProcess process = new EulerSchemeFromProcessModel(bachelierModel, brownianMotion);
		final MonteCarloAssetModel model = new MonteCarloAssetModel(process);

		final double survivalProbLevel = 10.0;//1.5;

		final SortedMap<Double, Double> probsPiecewiseConstant = new TreeMap<Double, Double>();
		probsPiecewiseConstant.put(survivalProbLevel, 1.0);
		probsPiecewiseConstant.put(Double.MAX_VALUE, 0.75);

		final double lambda = 1.0;

		final SortedMap<Double, Double> probsExponential = new TreeMap<Double, Double>();
		probsExponential.put(0.0, 1.0);
		final int n = 500;
		for(int i=0; i<n; i++) {
			final double x = (double)i/(double)n - 0.5;	// [0,1)
			final double y = x * 5;
			probsExponential.put(y, Math.exp(-lambda * y));
		}

		final SortedMap<Double, Double> probsConstant = new TreeMap<Double, Double>();
		probsConstant.put(0.0, 0.75);
		probsConstant.put(Double.MAX_VALUE, 0.75);

		final double mu = 0.10;

		final TimeDiscretization simulationTimes = model.getTimeDiscretization();

		final List<Point2D> valuations = new ArrayList<Point2D>();
		final FundingCapacity fundingCapacity = new FundingCapacity("EUR", new Scalar(0.0), probsExponential);


		final RandomVariable value = new Scalar(0.0);
		RandomVariable previousUnderlying = new Scalar(initialValue);
		for(final double time : simulationTimes) {

			RandomVariable underlying = model.getAssetValue(time, 0);
			underlying = underlying.add(mu * time);

			final var increment = underlying.sub(previousUnderlying);
			previousUnderlying = underlying;

			final var defaultFactors = fundingCapacity.getDefaultFactors(time, increment);

			valuations.add(new Point2D(time, defaultFactors.getSurvivalProbability().getAverage()));
		}

		final double beta = -lambda*mu + 0.5 * lambda*lambda * volatility*volatility;
		System.out.println(beta);
		final DoubleUnaryOperator analyticDefaultIntensity = x -> Math.exp(beta * x);

		final Plot plot = new Plot2D(List.of(
				new PlotablePoints2D("survival probability in exponential state dependent model", valuations, new GraphStyle(new Rectangle(3, 3))),
				new PlotableFunction2D(0.0, 10.0, 200, new Named<DoubleUnaryOperator>("analytic survival probability (classic intensity model)", analyticDefaultIntensity))
				));
		plot.setIsLegendVisible(true);
		plot.setTitle("maturity dependency of survival probability under continous funding requirements");
		plot.setXAxisLabel("maturity").setYAxisLabel("value");
		plot.show();
		plot.saveAsPDF(new File("maturity-dependency-of-survival-probability-in-continuous-funding.pdf"), 900, 600);
	}

	public void testLMM() throws Exception {

		/*
		 * Create a LIBOR market model
		 */
		final LIBORModelMonteCarloSimulationModel model = createLIBORMarketModel(Measure.SPOT, numberOfPaths/100, 5, 0.01, 0.30);

		final Stream<Map<String, Double>> values = IntStream.range(-100, 100).parallel().mapToDouble(x -> x/100.0*2000.0).mapToObj(
				notionalAmount -> {
					try {
						// Create swap leg

						final LocalDate referenceDate = LocalDate.of(2020, 9, 27);
						final LocalDate startDate = LocalDate.of(2020, 9, 29);
						final LocalDate maturity = LocalDate.of(2040, 9, 27);

						final Frequency frequency = Frequency.ANNUAL;

						final DaycountConvention daycountConvention = DaycountConvention.ACT_360;
						final ShortPeriodConvention shortPeriodConvention = ShortPeriodConvention.FIRST;
						final DateRollConvention dateRollConvention = DateRollConvention.FOLLOWING;
						final BusinessdayCalendar businessdayCalendar = new BusinessdayCalendarExcludingTARGETHolidays();
						final int fixingOffsetDays = 0;
						final int paymentOffsetDays = 0;
						final Schedule legSchedule = ScheduleGenerator.createScheduleFromConventions(referenceDate, startDate, maturity, frequency, daycountConvention, shortPeriodConvention, dateRollConvention, businessdayCalendar, fixingOffsetDays, paymentOffsetDays);

						final Notional notional = new NotionalFromConstant(notionalAmount);
						final AbstractIndex index = new LIBORIndex(0, 1.0);
						final boolean isNotionalExchanged = false;

						final double[] notionals = new double[legSchedule.getNumberOfPeriods()];
						Arrays.fill(notionals, notionalAmount);

						final double[] spreads = new double[legSchedule.getNumberOfPeriods()];

						final String currency = "EUR";
						final SortedMap<Double, Double> probs = new TreeMap<Double, Double>();
						probs.put(5.0, 1.0);
						probs.put(6.0, 0.9);
						probs.put(7.0, 0.8);
						probs.put(8.0, 0.7);
						probs.put(9.0, 0.6);
						probs.put(10.0, 0.5);
						probs.put(Double.MAX_VALUE, 0.1);

						final SortedMap<Double, Double> probsOne = new TreeMap<Double, Double>();
						probsOne.put(0.5, 1.0);
						probsOne.put(Double.MAX_VALUE, 1.0);

						final RiddersMethod optimizer = new RiddersMethod(0.0, 0.2);
						while(optimizer.getAccuracy() > 1E-11 && !optimizer.isDone()) {
							final double spread = -optimizer.getNextPoint();
							Arrays.fill(spreads, spread);

							final FundingCapacity fundingCapacity = new FundingCapacity(currency, new Scalar(0.0), probsOne);
							final SwapLegWithFundingProvider leg = new SwapLegWithFundingProvider(legSchedule, notionals, index, spreads, fundingCapacity);

							/*
							SwapLeg leg = new SwapLeg(legSchedule, notional, index, spread, isNotionalExchanged );
							 */

							final double value = leg.getValue(model);
							optimizer.setValue(value);
						}

						final RiddersMethod optimizer2 = new RiddersMethod(0.0, 0.2);
						while(optimizer2.getAccuracy() > 1E-11 && !optimizer2.isDone()) {
							final double spread = -optimizer2.getNextPoint();
							Arrays.fill(spreads, spread);

							final FundingCapacity fundingCapacity = new FundingCapacity(currency, new Scalar(0.0), probs);
							final SwapLegWithFundingProvider leg2 = new SwapLegWithFundingProvider(legSchedule, notionals, index, spreads, fundingCapacity);

							final double value = leg2.getValue(model);
							optimizer2.setValue(value);
						}

						final Map<String, Double> value = Map.of("notional", notionalAmount ,"swap.plain", optimizer.getBestPoint(), "swap.funding", optimizer2.getBestPoint());

						System.out.println(value);
						return value;
					} catch (final CalculationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return null;
				});

		final List<Point2D> valueSwapPlain = new ArrayList<Point2D>();
		final List<Point2D> valueSwapFunding = new ArrayList<Point2D>();

		values.forEach(value -> {
			System.out.println(value);
			valueSwapPlain.add(new Point2D(value.get("notional"), value.get("swap.plain")));
			valueSwapFunding.add(new Point2D(value.get("notional"), value.get("swap.funding")));
		});

		final Plot2D plot = new Plot2D(List.of(
				new PlotablePoints2D("risky swap rate", valueSwapFunding, new GraphStyle(new Rectangle(3, 3))),
				new PlotablePoints2D("risk free swap rate", valueSwapPlain, new GraphStyle(new Rectangle(3, 3)))
				));
		plot.setYAxisNumberFormat(new DecimalFormat("0.00%"));
		plot.setXAxisLabel("notional");
		plot.setYAxisLabel("swap rate");
		plot.setIsLegendVisible(true);
		plot.setTitle("notional dependency of a par-swap rate of a swap");
		plot.saveAsPDF(new File("notional-dependency-of-par-swap-rate-with-survival-probability.pdf"), 900, 600);
		plot.show();
	}

	public void testLMMByMaturity(double volatiltiyLevel) throws Exception {

		{
			final LocalDate referenceDate = LocalDate.of(2020, 9, 27);
			final LocalDate startDate = LocalDate.of(2020, 9, 29);
			final LocalDate maturity = LocalDate.of(2040, 9, 29);

			final Frequency frequency = Frequency.ANNUAL;

			final DaycountConvention daycountConvention = DaycountConvention.ACT_ACT;
			final ShortPeriodConvention shortPeriodConvention = ShortPeriodConvention.FIRST;
			final DateRollConvention dateRollConvention = DateRollConvention.FOLLOWING;
			final BusinessdayCalendar businessdayCalendar = new BusinessdayCalendarExcludingTARGETHolidays();
			final int fixingOffsetDays = 0;
			final int paymentOffsetDays = 0;
			final Schedule legSchedule = ScheduleGenerator.createScheduleFromConventions(referenceDate, startDate, maturity, frequency, daycountConvention, shortPeriodConvention, dateRollConvention, businessdayCalendar, fixingOffsetDays, paymentOffsetDays);

			System.out.println(legSchedule);
		}

		/*
		 * Create a LIBOR market model
		 */
		final LIBORModelMonteCarloSimulationModel model = createLIBORMarketModel(Measure.SPOT, numberOfPaths/4, 5, 0.01, volatiltiyLevel);

		final List<Plotable2D> plotables = new ArrayList<Plotable2D>();
		for(int notionalLevel = -5; notionalLevel<= 5; notionalLevel++) {
			if(notionalLevel == 0) {
				continue;
			}
			final double notionalAmount = notionalLevel/5.0*1000.0;

			final Stream<Map<String, Double>> values = IntStream.rangeClosed(1, 20).parallel().mapToDouble(x -> x).mapToObj(maturityYears -> {
				try {
					// Create swap leg

					final LocalDate referenceDate = LocalDate.of(2020, 9, 27);
					final LocalDate startDate = LocalDate.of(2020+(int)maturityYears-1, 9, 29);
					final LocalDate maturity = LocalDate.of(2020+(int)maturityYears, 9, 29);

					final Frequency frequency = Frequency.ANNUAL;

					final DaycountConvention daycountConvention = DaycountConvention.ACT_360;
					final ShortPeriodConvention shortPeriodConvention = ShortPeriodConvention.FIRST;
					final DateRollConvention dateRollConvention = DateRollConvention.FOLLOWING;
					final BusinessdayCalendar businessdayCalendar = new BusinessdayCalendarExcludingTARGETHolidays();
					final int fixingOffsetDays = 0;
					final int paymentOffsetDays = 0;
					final Schedule legSchedule = ScheduleGenerator.createScheduleFromConventions(referenceDate, startDate, maturity, frequency, daycountConvention, shortPeriodConvention, dateRollConvention, businessdayCalendar, fixingOffsetDays, paymentOffsetDays);

					final Notional notional = new NotionalFromConstant(notionalAmount);
					final AbstractIndex index = new LIBORIndex(0, 1.0);
					final boolean isNotionalExchanged = false;

					final double[] notionals = new double[legSchedule.getNumberOfPeriods()];
					Arrays.fill(notionals, notionalAmount);

					final double[] spreads = new double[legSchedule.getNumberOfPeriods()];

					final String currency = "EUR";
					final SortedMap<Double, Double> probs = new TreeMap<Double, Double>();
					probs.put(5.0, 1.0);
					probs.put(6.0, 0.9);
					probs.put(7.0, 0.8);
					probs.put(8.0, 0.7);
					probs.put(9.0, 0.6);
					probs.put(10.0, 0.5);
					probs.put(Double.MAX_VALUE, 0.1);

					final SortedMap<Double, Double> probsOne = new TreeMap<Double, Double>();
					probsOne.put(0.5, 1.0);
					probsOne.put(Double.MAX_VALUE, 1.0);

					final RiddersMethod optimizer2 = new RiddersMethod(0.0, 0.2);
					while(optimizer2.getAccuracy() > 1E-11 && !optimizer2.isDone()) {
						final double spread = -optimizer2.getNextPoint();
						Arrays.fill(spreads, spread);

						final FundingCapacity fundingCapacity = new FundingCapacity(currency, new Scalar(0.0), probs);
						final SwapLegWithFundingProvider leg2 = new SwapLegWithFundingProvider(legSchedule, notionals, index, spreads, fundingCapacity);

						double value = leg2.getValue(model);
						// TODO Check for NaN
						if(Double.isNaN(value)) {
							value = 0.0;
						}
						optimizer2.setValue(value);
					}

					final Map<String, Double> value = Map.of("maturity", maturityYears, "swap.funding", optimizer2.getBestPoint());

					System.out.println(value);
					return value;
				} catch (final CalculationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			});

			final List<Point2D> valueSwapFunding = new ArrayList<Point2D>();
			values.forEach(value -> {
				System.out.println(value);
				valueSwapFunding.add(new Point2D(value.get("maturity"), value.get("swap.funding")));
			});
			final float red = Math.max(1.0f-(notionalLevel-(-5.0f))/5.0f, 0.0f);
			final float blue = Math.max((notionalLevel)/5.0f, 0.0f);
			final float green = 1.0f-Math.abs(notionalLevel/5.0f);
			final PlotablePoints2D plotable = new PlotablePoints2D("" + notionalAmount, valueSwapFunding, new GraphStyle(new Rectangle(3, 3), new BasicStroke(3), new Color(red, blue, green)));
			plotables.add(plotable);
		}

		final Plot2D plot = new Plot2D(plotables);
		plot.setYAxisNumberFormat(new DecimalFormat("0.00%"));
		plot.setXAxisLabel("maturity");
		plot.setYAxisLabel("forward rate");
		plot.setIsLegendVisible(true);
		plot.setTitle("notional dependency of the par forward rate");
		plot.saveAsPDF(new File("notional-dependency-of-forward-rate-curve-with-survival-probability-volatility-" + volatiltiyLevel + ".pdf"), 900, 600);
		plot.show();
	}

	public static LIBORModelMonteCarloSimulationModel createLIBORMarketModel(
			final Measure measure, final int numberOfPaths, final int numberOfFactors, final double correlationDecayParam, double volatilityLevel) throws CalculationException {

		final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory(false);

		/*
		 * Create the libor tenor structure and the initial values
		 */
		final double liborPeriodLength	= 0.5;
		final double liborRateTimeHorzion	= 40.0;
		final TimeDiscretizationFromArray liborPeriodDiscretization = new TimeDiscretizationFromArray(0.0, (int) (liborRateTimeHorzion / liborPeriodLength), liborPeriodLength);

		// Create the forward curve (initial value of the LIBOR market model)
		final ForwardCurveInterpolation forwardCurveInterpolation = ForwardCurveInterpolation.createForwardCurveFromForwards(
				"forwardCurve"								/* name of the curve */,
				new double[] {0.5 , 1.0 , 2.0 , 5.0 , 40.0}	/* fixings of the forward */,
				new double[] {0.05, 0.05, 0.05, 0.05, 0.05}	/* forwards */,
				liborPeriodLength							/* tenor / period length */
				);

		// Create the discount curve
		final DiscountCurveInterpolation discountCurveInterpolation = DiscountCurveInterpolation.createDiscountCurveFromZeroRates(
				"discountCurve"								/* name of the curve */,
				new double[] {0.5 , 1.0 , 2.0 , 5.0 , 40.0}	/* maturities */,
				new double[] {0.04, 0.04, 0.04, 0.04, 0.05}	/* zero rates */
				);


		/*
		 * Create a simulation time discretization
		 */
		final double lastTime	= 40.0;
		final double dt		= 0.5;

		final TimeDiscretizationFromArray timeDiscretizationFromArray = new TimeDiscretizationFromArray(0.0, (int) (lastTime / dt), dt);

		/*
		 * Create a volatility structure v[i][j] = sigma_j(t_i)
		 */
		final double[][] volatility = new double[timeDiscretizationFromArray.getNumberOfTimeSteps()][liborPeriodDiscretization.getNumberOfTimeSteps()];
		for (int timeIndex = 0; timeIndex < volatility.length; timeIndex++) {
			for (int liborIndex = 0; liborIndex < volatility[timeIndex].length; liborIndex++) {
				// Create a very simple volatility model here
				final double time = timeDiscretizationFromArray.getTime(timeIndex);
				final double maturity = liborPeriodDiscretization.getTime(liborIndex);
				final double timeToMaturity = maturity - time;

				double instVolatility;
				if(timeToMaturity <= 0) {
					instVolatility = 0;				// This forward rate is already fixed, no volatility
				} else {
					instVolatility = volatilityLevel * (0.3 * Math.exp(-0.25 * timeToMaturity));
				}

				// Store
				volatility[timeIndex][liborIndex] = instVolatility;
			}
		}
		final LIBORVolatilityModelFromGivenMatrix volatilityModel = new LIBORVolatilityModelFromGivenMatrix(timeDiscretizationFromArray, liborPeriodDiscretization, volatility);

		/*
		 * Create a correlation model rho_{i,j} = exp(-a * abs(T_i-T_j))
		 */
		final LIBORCorrelationModelExponentialDecay correlationModel = new LIBORCorrelationModelExponentialDecay(
				timeDiscretizationFromArray, liborPeriodDiscretization, numberOfFactors,
				correlationDecayParam);


		/*
		 * Combine volatility model and correlation model to a covariance model
		 */
		final LIBORCovarianceModelFromVolatilityAndCorrelation covarianceModel =
				new LIBORCovarianceModelFromVolatilityAndCorrelation(timeDiscretizationFromArray,
						liborPeriodDiscretization, volatilityModel, correlationModel);

		// BlendedLocalVolatlityModel (future extension)
		//		AbstractLIBORCovarianceModel covarianceModel2 = new BlendedLocalVolatlityModel(covarianceModel, 0.00, false);

		// Set model properties
		final Map<String, String> properties = new HashMap<>();

		// Choose the simulation measure
		properties.put("measure", measure.name());

		// Choose log normal model
		properties.put("stateSpace", LIBORMarketModelFromCovarianceModel.StateSpace.LOGNORMAL.name());

		// Empty array of calibration items - hence, model will use given covariance
		final CalibrationProduct[] calibrationItems = new CalibrationProduct[0];

		/*
		 * Create corresponding LIBOR Market Model
		 */
		final LIBORMarketModel liborMarketModel = LIBORMarketModelFromCovarianceModel.of(
				liborPeriodDiscretization,
				new AnalyticModelFromCurvesAndVols(new Curve[] { forwardCurveInterpolation, discountCurveInterpolation}),
				forwardCurveInterpolation,
				discountCurveInterpolation,
				randomVariableFactory,
				covarianceModel,
				calibrationItems,
				properties);

		final BrownianMotion brownianMotion = new net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers(timeDiscretizationFromArray, numberOfFactors, numberOfPaths, 3141 /* seed */);

		final EulerSchemeFromProcessModel process = new EulerSchemeFromProcessModel(liborMarketModel, brownianMotion);//, EulerSchemeFromProcessModel.Scheme.PREDICTOR_CORRECTOR);

		return new LIBORMonteCarloSimulationFromLIBORModel(process);
	}
}
