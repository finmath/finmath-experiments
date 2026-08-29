/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 14.05.2026
 */
package net.finmath.experiments.forwardsensitivities;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import net.finmath.exception.CalculationException;
import net.finmath.experiments.input.LectureProjectData;
import net.finmath.experiments.utilities.ModelFactoryInterestRates;
import net.finmath.functions.LinearAlgebra;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.MonteCarloSimulationModel;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.automaticdifferentiation.RandomVariableDifferentiable;
import net.finmath.montecarlo.automaticdifferentiation.backward.RandomVariableDifferentiableAADFactory;
import net.finmath.montecarlo.automaticdifferentiation.forwardsensitivities.ForwardSensitivities.ProjectedHedgeRatioResult;
import net.finmath.montecarlo.automaticdifferentiation.forwardsensitivities.ForwardSensitivities.ReductionMethod;
import net.finmath.montecarlo.interestrate.TermStructureModel;
import net.finmath.montecarlo.interestrate.TermStructureMonteCarloSimulationModel;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel.Measure;
import net.finmath.montecarlo.interestrate.models.LIBORMarketModelFromCovarianceModel.StateSpace;
import net.finmath.montecarlo.interestrate.products.AbstractTermStructureMonteCarloProduct;
import net.finmath.montecarlo.interestrate.products.Bond;
import net.finmath.montecarlo.interestrate.products.Caplet;
import net.finmath.montecarlo.interestrate.products.DiscreteTenorRollOver;
import net.finmath.montecarlo.interestrate.products.ForwardSensitivityDeltaHedgedPortfolio;
import net.finmath.montecarlo.interestrate.products.ForwardSensitivityDeltaHedgedPortfolio.HedgeRatioProvider;
import net.finmath.montecarlo.interestrate.products.Swap;
import net.finmath.montecarlo.interestrate.products.SwapAnnuity;
import net.finmath.montecarlo.interestrate.products.SwapLeg;
import net.finmath.montecarlo.interestrate.products.Swaption;
import net.finmath.montecarlo.interestrate.products.TermStructureMonteCarloProduct;
import net.finmath.montecarlo.interestrate.products.components.Notional;
import net.finmath.montecarlo.interestrate.products.components.NotionalFromConstant;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.plots.GraphStyle;
import net.finmath.plots.Named;
import net.finmath.plots.Plot2D;
import net.finmath.plots.Plotable2D;
import net.finmath.plots.PlotableFunction2D;
import net.finmath.plots.PlotableFunctionWithConfidenceInterval2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.plots.PlotablePointsWithConfidenceIntervall2D;
import net.finmath.plots.Point2D;
import net.finmath.plots.util.ColorUtils;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;
import net.finmath.stochastic.operators.Monomials;
import net.finmath.time.RegularSchedule;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * Experiment for a caplet hedge in an LMM, an HW short-rate model, and an LMM
 * configured to reproduce a Hull-White volatility structure.
 *
 * <p>
 * The class supports the following diagnostics.
 * </p>
 * <ul>
 *   <li>payment-time hedge error versus fixing-time mark-to-market hedge error,</li>
 *   <li>two-bond hedge versus a discrete-roll hedge instrument,</li>
 *   <li>cash-account roll-over diagnostic from fixing to payment,</li>
 *   <li>caplet-forward basis versus process-state basis,</li>
 *   <li>LMM / LMM-HW / HW model comparison.</li>
 * </ul>
 *
 * <p>
 * The key diagnostic for the LMM-vs-HW difference is the comparison of the
 * model's cash roll
 * </p>
 *
 * \[
 *     B(T_{i+1}) / B(T_i)
 * \]
 *
 * <p>
 * with the discrete Libor roll
 * </p>
 *
 * \[
 *     1 + (T_{i+1}-T_i)L(T_i;T_i,T_{i+1}).
 * \]
 *
 * <p>
 * In an LMM spot-measure setup this identity is essentially built into the
 * numeraire convention. In a short-rate/Hull-White setup it is not a pathwise
 * identity, and this difference can dominate payment-time hedge errors if the
 * hedge leaves cash to be rolled from fixing to payment.
 * </p>
 *
 * @author Christian Fries
 */
public class ForwardSensitivityCapletHedgingExperiment {

	final static BrownianMotion noiseGenerator_2000 = new BrownianMotionFromMersenneRandomNumbers(new TimeDiscretizationFromArray(0.0, 0.2), 2, 2000 /* numberOfPath */, 3134531);
	final static BrownianMotion noiseGenerator_10000 = new BrownianMotionFromMersenneRandomNumbers(new TimeDiscretizationFromArray(0.0, 0.2), 2, 10000 /* numberOfPath */, 3134531);


	/*
	 * Product specification defaults. The experiment matrix below overrides the
	 * strike to zero for static identity tests.
	 */
	private static final double DEFAULT_CAPLET_STRIKE = 2.00 / 100.0;
	private static final double TENOR_PERIOD_LENGTH = 0.5;
	private static final int SEED = 3141;

	/*
	 * Model specific parameters.
	 * Note that the model must allow the specified rebalancingPerPeriod (part of the config below).
	 * Below we use rebalancingPerPeriod = numberOfSimulationStepsPerTenor, but it is possible to use a coarser rebalancing on a finer model
	 */
	public enum ModelType {
		HULL_WHITE_FINE25("HullWhite@25", 4000, 2, 25),
		HULL_WHITE_FINE("HullWhite@10", 10000, 2, 10),
		HULL_WHITE("HullWhite@1", 10000, 2, 1),
		LMM("LMM(2K)@1", 2000, 5, 1),
		LMM_10K("LMM@1", 10000, 5, 1),
		LMM_100K("LMM(20K)@1", 20000, 5, 1),
		LMM_HW("LMM-HW(2K)@1", 2000, 5, 1);

		private final String name;
		private final int numberOfPaths;
		private final int numberOfFactors;
		private final int numberOfSimulationStepsPerTenor;

		ModelType(final String name, final int numberOfPaths, final int numberOfFactors, final int numberOfSimulationStepsPerTenor) {
			this.name = name;
			this.numberOfPaths = numberOfPaths;
			this.numberOfFactors = numberOfFactors;
			this.numberOfSimulationStepsPerTenor = numberOfSimulationStepsPerTenor;
		}

		public int getNumberOfPaths() {
			return numberOfPaths;
		}

		public int getNumberOfFactors() {
			return numberOfFactors;
		}

		public int getNumberOfSimulationStepsPerTenor() {
			return numberOfSimulationStepsPerTenor;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public enum ProductToHedge {
		FRA_15__SEMIANNUAL("FRA(15.0)", new Caplet(15.0, 0.5, 0.0)),
		CAPLET_15_SEMIANNUAL("Caplet(" + DEFAULT_CAPLET_STRIKE + ")",
				new Caplet(15.0, 0.5, DEFAULT_CAPLET_STRIKE)),
		SWAPTION_15_IN_5("Swaption(15,20," + DEFAULT_CAPLET_STRIKE + ")",
				new Swaption(15.0,
						new TimeDiscretizationFromArray(15.0, 10, TENOR_PERIOD_LENGTH),
						DEFAULT_CAPLET_STRIKE));


		private final String name;
		private final AbstractTermStructureMonteCarloProduct productToHedge;

		ProductToHedge(String name, AbstractTermStructureMonteCarloProduct productToHedge)
		{
			this.name = name;
			this.productToHedge = productToHedge;
		}

		@Override
		public String toString() {
			return name;
		}		
	}

	public enum HedgeInstrumentSet {
		TWO_BONDS,
		SHORT_AND_LONG_BOND,
		BOND_AND_ANNUITY,
		FULL_BOND_CURVE,
		TWO_BONDS_WITH_NOISE,

		/*
		 * Replace the fixing-date bond by an instrument that pays
		 * 1 + Delta L(T_i;T_i,T_{i+1}) at T_{i+1}. This removes the HW short-rate
		 * cash roll from T_i to T_{i+1} and tests the roll-over hypothesis.
		 */
		DISCRETE_ROLL_OVER_AND_PAYMENT_BOND,
	}

	/*
	 * Where to unwind the hedge.
	 * Important: If you use PAYMENT_TIME_CASHFLOW you need to use DISCRETE_ROLL_OVER_AND_PAYMENT_BOND
	 * because with TWO_BONDS the first bond will be matured and the delta hedge class currently
	 * does not do a roll over.
	 * If you like to investigate FULL_BOND_CURVE then unwind the hedge at fixing using FIXING_TIME_MARK_TO_MARKET
	 */
	public enum EvaluationMode {
		/* Compare against the actual caplet cashflow at paymentTime. */
		PAYMENT_TIME_CASHFLOW,

		/* Compare against the adapted caplet mark-to-market at fixingTime. */
		FIXING_TIME_MARK_TO_MARKET
	}

	public enum BasisType {		
		UNDERLYING_FORWARD_ONLY,
		UNDERLYING_FORWARD_POLYNOMIAL,
		PROCESS_STATE_POLYNOMIAL
	}

	public enum SensitivityExchangeMode {
		EXPORT_RAW,
		IMPORT_PROJECTED
	}

	private static final BasisType[] BASIS_TYPES = new BasisType[] {
			BasisType.UNDERLYING_FORWARD_POLYNOMIAL,
			BasisType.PROCESS_STATE_POLYNOMIAL,
			BasisType.UNDERLYING_FORWARD_ONLY,
	};

	/*
	 * List of lambdas we try below.
	 */
	private static final double[] REGULARIZATION_LAMBDAS = new double[] { 1E-6, 1E-8,
			1E-12, 1E-3 /*, 0.0 */ };

	public static void main(final String[] args) throws Exception {
		if(Arrays.asList(args).contains("--help")) {
			printCommandLineUsage();
			return;
		}

		System.out.println("System setup:");
		System.out.println("\tisSolverUseApacheCommonsMath = " + LinearAlgebra.isSolverUseApacheCommonsMath());
		System.out.println("\tisJBlasAvailable = " + LinearAlgebra.isJBlasAvailable());
		System.out.println("\tisEigenvalueDecompositionViaSVD = " + LinearAlgebra.isEigenvalueDecompositionViaSVD());

		final long timeStart = System.currentTimeMillis();

		if(args.length > 0) {
			runCapletHedgingExperiment(createConfigFromArguments(args));
			final long timeEnd = System.currentTimeMillis();
			System.out.println("Calculation time: " + (timeEnd - timeStart) / 1000.0 + " s");
			return;
		}

		final boolean runExperimentMatrix = true;
		if(runExperimentMatrix) {
			runDiagnosticExperimentMatrix();
		}
		else {
			runCapletHedgingExperiment(ExperimentConfig.defaultConfig());
		}

		final long timeEnd = System.currentTimeMillis();
		System.out.println("Calculation time: " + (timeEnd - timeStart) / 1000.0 + " s");
	}

	private static ExperimentConfig createConfigFromArguments(final String[] args) {
		ExperimentConfig config = ExperimentConfig.defaultConfig().withName("command-line run");
		SensitivityExchangeMode exchangeMode = null;

		for(int argumentIndex = 0; argumentIndex < args.length; argumentIndex++) {
			final String argument = args[argumentIndex];
			switch(argument) {
			case "--export-sensitivities":
				if(exchangeMode != null) {
					throw new IllegalArgumentException("Specify exactly one sensitivity export/import mode.");
				}
				exchangeMode = SensitivityExchangeMode.EXPORT_RAW;
				config = config.withSensitivityExport(Path.of(requireArgumentValue(args, ++argumentIndex, argument)));
				break;
			case "--import-projected-sensitivities":
				if(exchangeMode != null) {
					throw new IllegalArgumentException("Specify exactly one sensitivity export/import mode.");
				}
				exchangeMode = SensitivityExchangeMode.IMPORT_PROJECTED;
				config = config.withSensitivityImport(Path.of(requireArgumentValue(args, ++argumentIndex, argument)));
				break;
			case "--model":
				config = config.withModelType(ModelType.valueOf(requireArgumentValue(args, ++argumentIndex, argument)));
				break;
			case "--product":
				config = config.withProductToHedge(ProductToHedge.valueOf(requireArgumentValue(args, ++argumentIndex, argument)));
				break;
			case "--hedge-instruments":
				config = config.withHedgeInstrumentSet(HedgeInstrumentSet.valueOf(requireArgumentValue(args, ++argumentIndex, argument)));
				break;
			case "--evaluation-mode":
				config = config.withEvaluationMode(EvaluationMode.valueOf(requireArgumentValue(args, ++argumentIndex, argument)));
				break;
			case "--rebalancing-per-period":
				config = config.withRebalancingPerPeriod(Integer.parseInt(requireArgumentValue(args, ++argumentIndex, argument)));
				break;
			case "--seed":
				config = config.withSeed(Integer.parseInt(requireArgumentValue(args, ++argumentIndex, argument)));
				break;
			case "--static-hedge":
				config = config.withStaticHedgeOnly(true);
				break;
			case "--no-plots":
				config = config.withShowScatterPlots(false);
				break;
			case "--save-plots":
				config = config.withSaveToFile(true);
				break;
			default:
				throw new IllegalArgumentException("Unknown argument '" + argument + "'. Use --help for usage.");
			}
		}
		return config;
	}

	private static String requireArgumentValue(
			final String[] args,
			final int valueIndex,
			final String argument) {

		if(valueIndex >= args.length) {
			throw new IllegalArgumentException("Missing value after " + argument + ".");
		}
		return args[valueIndex];
	}

	private static void printCommandLineUsage() {
		System.out.println("Usage: ForwardSensitivityCapletHedgingExperiment [options]");
		System.out.println("  --export-sensitivities <directory>");
		System.out.println("  --import-projected-sensitivities <directory>");
		System.out.println("  --model <" + enumNames(ModelType.values()) + ">");
		System.out.println("  --product <" + enumNames(ProductToHedge.values()) + ">");
		System.out.println("  --hedge-instruments <" + enumNames(HedgeInstrumentSet.values()) + ">");
		System.out.println("  --evaluation-mode <" + enumNames(EvaluationMode.values()) + ">");
		System.out.println("  --rebalancing-per-period <integer>");
		System.out.println("  --seed <integer>");
		System.out.println("  --static-hedge --no-plots --save-plots");
	}

	private static String enumNames(final Enum<?>[] values) {
		final String[] names = new String[values.length];
		for(int index = 0; index < values.length; index++) {
			names[index] = values[index].name();
		}
		return String.join("|", names);
	}

	/**
	 * Runs a compact experiment matrix designed to isolate the likely causes of
	 * LMM-vs-HW hedge differences.
	 */
	public static void runDiagnosticExperimentMatrix() throws Exception {

		final List<ExperimentConfig> experiments = new ArrayList<>();

		boolean runForward = false;

		if(runForward) {
			experiments.add(ExperimentConfig.defaultConfig()
					.withName("HW static K=0 payment two-bonds")
					.withModelType(ModelType.HULL_WHITE)
					.withProductToHedge(ProductToHedge.FRA_15__SEMIANNUAL)
					.withStaticHedgeOnly(false)
					.withEvaluationMode(EvaluationMode.PAYMENT_TIME_CASHFLOW)
					.withHedgeInstrumentSet(HedgeInstrumentSet.TWO_BONDS)
					.withShowScatterPlots(true));

			experiments.add(ExperimentConfig.defaultConfig()
					.withName("LMM-HW static K=0 payment two-bonds")
					.withModelType(ModelType.LMM_HW)
					.withProductToHedge(ProductToHedge.FRA_15__SEMIANNUAL)
					.withStaticHedgeOnly(false)
					.withEvaluationMode(EvaluationMode.PAYMENT_TIME_CASHFLOW)
					.withHedgeInstrumentSet(HedgeInstrumentSet.TWO_BONDS)
					.withShowScatterPlots(true));

			experiments.add(ExperimentConfig.defaultConfig()
					.withName("HW static K=0 fixing two-bonds")
					.withModelType(ModelType.HULL_WHITE)
					.withProductToHedge(ProductToHedge.FRA_15__SEMIANNUAL)
					.withStaticHedgeOnly(false)
					.withEvaluationMode(EvaluationMode.FIXING_TIME_MARK_TO_MARKET)
					.withHedgeInstrumentSet(HedgeInstrumentSet.TWO_BONDS)
					.withShowScatterPlots(true));

			experiments.add(ExperimentConfig.defaultConfig()
					.withName("D HW static K=0 payment discrete-roll")
					.withModelType(ModelType.HULL_WHITE)
					.withProductToHedge(ProductToHedge.FRA_15__SEMIANNUAL)
					.withStaticHedgeOnly(false)
					.withEvaluationMode(EvaluationMode.PAYMENT_TIME_CASHFLOW)
					.withHedgeInstrumentSet(HedgeInstrumentSet.DISCRETE_ROLL_OVER_AND_PAYMENT_BOND)
					.withShowScatterPlots(true));
		}

		for(ModelType modelType : new ModelType[] {
				ModelType.HULL_WHITE_FINE25,
				ModelType.HULL_WHITE_FINE,
				ModelType.LMM_10K,
				ModelType.LMM_100K,
				ModelType.HULL_WHITE,
//				ModelType.LMM,
				ModelType.LMM_HW
		}) {
			for(HedgeInstrumentSet hedgeInstrumentSet : new HedgeInstrumentSet[] {
					HedgeInstrumentSet.BOND_AND_ANNUITY,
//					HedgeInstrumentSet.SHORT_AND_LONG_BOND,
//					HedgeInstrumentSet.TWO_BONDS,
//					HedgeInstrumentSet.TWO_BONDS_WITH_NOISE,
					HedgeInstrumentSet.FULL_BOND_CURVE, /* HedgeInstrumentSet.DISCRETE_ROLL_OVER_AND_PAYMENT_BOND */ }) {
				experiments.add(ExperimentConfig.defaultConfig()
						.withName(modelType.name() + " dynamic K>0")
						.withModelType(modelType)
						.withProductToHedge(ProductToHedge.SWAPTION_15_IN_5)
//						.withProductToHedge(ProductToHedge.CAPLET_15_SEMIANNUAL)
						.withRebalancingPerPeriod(modelType.getNumberOfSimulationStepsPerTenor())
						.withStaticHedgeOnly(false)
						.withEvaluationMode(EvaluationMode.FIXING_TIME_MARK_TO_MARKET)
						.withHedgeInstrumentSet(hedgeInstrumentSet)
						.withShowScatterPlots(true)
						.withSaveToFile(true)
						);
			}
		}

		/*
		 * Payment-time diagnostic. The basis and regularization variations are run
		 * inside runCapletHedgingExperiment below.
		 */
		experiments.add(ExperimentConfig.defaultConfig()
				.withName("H HW dynamic K>0 payment")
				.withModelType(ModelType.HULL_WHITE)
				.withProductToHedge(ProductToHedge.CAPLET_15_SEMIANNUAL)
				.withStaticHedgeOnly(false)
				.withEvaluationMode(EvaluationMode.PAYMENT_TIME_CASHFLOW)
				.withHedgeInstrumentSet(HedgeInstrumentSet.DISCRETE_ROLL_OVER_AND_PAYMENT_BOND)
				.withShowScatterPlots(true));

		for(final ExperimentConfig experiment : experiments) {
			System.out.println("================================================================");
			runCapletHedgingExperiment(experiment);
		}
	}

	public static void runCapletHedgingExperiment(final ExperimentConfig config) throws Exception {

		/*
		 * Interest rate curves.
		 */
		final DiscountCurve discountCurve = LectureProjectData.getDiscountCurve();
		final ForwardCurve forwardCurve = LectureProjectData.getForwardCurve();

		/*
		 * Tenor TimeDiscretization.
		 */
		final double tenorHorizon = 20.0;
		final int numberOfTenorPeriods = (int)Math.round(tenorHorizon / TENOR_PERIOD_LENGTH);
		final TimeDiscretization tenorTimeDiscretization = new TimeDiscretizationFromArray(
				0.0,
				numberOfTenorPeriods,
				TENOR_PERIOD_LENGTH);

		/*
		 * Simulation TimeDiscretization.
		 */
		final double simulationLastTime = tenorHorizon;
		final double simulationTimeStep = TENOR_PERIOD_LENGTH / config.modelType.getNumberOfSimulationStepsPerTenor();

		/*
		 * Hull-White short-rate parameters.
		 */
		final double shortRateVolatility = 0.0024;
		final double shortRateMeanReversion = 0.05;

		/*
		 * BrownianMotion.
		 */
		final int numberOfFactors = config.modelType.getNumberOfFactors();

		final String tenorInterpolationMethod = "linear";
		final String simulationTimeInterpolationMethod = "round_down";

		final double volatility = 0.10;
		final double volatilityExponentialDecay = 0.05;
		final double correlationDecayParam = 0.05;

		/*
		 * Inject AAD algorithm that keeps track of sensitivities to process states.
		 */
		final Map<String, Object> aadProperties = new HashMap<>();
		aadProperties.put("isGradientRetainsLeafNodesOnly", false);
		final RandomVariableFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory(new RandomVariableFromArrayFactory(false), aadProperties);

		final double fixingTime = tenorHorizon - 10*TENOR_PERIOD_LENGTH;
		final double paymentTime = fixingTime + TENOR_PERIOD_LENGTH;
		final double evaluationTime = config.evaluationMode == EvaluationMode.PAYMENT_TIME_CASHFLOW ? paymentTime : fixingTime;

		//		AbstractTermStructureMonteCarloProduct capletPlain = config.
		final AbstractTermStructureMonteCarloProduct capletPlain = config.productToHedge.productToHedge;
		AbstractTermStructureMonteCarloProduct capletProduct = new AbstractTermStructureMonteCarloProduct()
		{
			@Override
			public RandomVariable getValue(double evaluationTime, TermStructureMonteCarloSimulationModel model)
					throws CalculationException {
				BrownianMotion noiseGenerator;
				if(model.getNumberOfPaths() == 2000) noiseGenerator = noiseGenerator_2000;
				else noiseGenerator = noiseGenerator_10000;
				final RandomVariable noiseFactor2 = noiseGenerator.getBrownianIncrement(0.0, 1).mult(0.5).add(1.0);
				return capletPlain.getValue(evaluationTime,model).mult(noiseFactor2);
			}
		};

		capletProduct = capletPlain;

		final double hedgeTimeStep = tenorHorizon/numberOfTenorPeriods/config.rebalancingPerPeriod;
		final TimeDiscretization rebalancingTimes = config.staticHedgeOnly ? new TimeDiscretizationFromArray(new double[] { 0.0 }) : new TimeDiscretizationFromArray(0.0, (int)Math.round(fixingTime/hedgeTimeStep), hedgeTimeStep);

		/*
		 * Build TermStructureMonteCarloSimulationModel.
		 */
		final TermStructureMonteCarloSimulationModel liborSimulationPlain = switch(config.modelType) {
		case HULL_WHITE, HULL_WHITE_FINE, HULL_WHITE_FINE25 -> ModelFactoryInterestRates.createHullWhiteSimulation(
				forwardCurve,
				config.useDiscountCurve ? discountCurve : null,
						tenorHorizon,
						TENOR_PERIOD_LENGTH,
						simulationLastTime,
						simulationTimeStep,
						shortRateVolatility,
						shortRateMeanReversion,
						numberOfFactors*5,		// Run HW on 5 time the LMM path
						config.modelType.getNumberOfPaths(),
						config.seed,
						randomVariableFactory);
		case LMM,LMM_10K,LMM_100K -> ModelFactoryInterestRates.createSimulationOriginal(
				forwardCurve,
				config.useDiscountCurve,
				discountCurve,
				tenorHorizon,
				TENOR_PERIOD_LENGTH, 
				simulationLastTime,
				simulationTimeStep,
				tenorInterpolationMethod,
				simulationTimeInterpolationMethod,
				Measure.SPOT,
				StateSpace.LOGNORMAL,
				volatility,
				volatilityExponentialDecay,
				correlationDecayParam,
				numberOfFactors,
				config.modelType.getNumberOfPaths(),
				config.seed,
				randomVariableFactory);
		case LMM_HW -> ModelFactoryInterestRates.createSimulationHWasLMM(
				forwardCurve,
				config.useDiscountCurve ? discountCurve : null,
						tenorHorizon,
						TENOR_PERIOD_LENGTH,
						simulationLastTime,
						simulationTimeStep,
						shortRateVolatility,
						shortRateMeanReversion,
						tenorInterpolationMethod,
						simulationTimeInterpolationMethod,
						numberOfFactors,
						config.modelType.getNumberOfPaths(),
						config.seed,
						randomVariableFactory);
		default -> throw new IllegalArgumentException("Unexpected value: " + config.modelType);
		};

		// liborSimulation
		TermStructureMonteCarloSimulationModel liborSimulation = new TermStructureMonteCarloSimulationModel() {

			@Override
			public int getNumberOfPaths() {
				return liborSimulationPlain.getNumberOfPaths();
			}

			@Override
			public TimeDiscretization getTimeDiscretization() {
				return liborSimulationPlain.getTimeDiscretization();
			}

			@Override
			public double getTime(int timeIndex) {
				return liborSimulationPlain.getTime(timeIndex);
			}

			@Override
			public int getTimeIndex(double time) {
				return liborSimulationPlain.getTimeIndex(time);
			}

			@Override
			public RandomVariable getRandomVariableForConstant(double value) {
				return liborSimulationPlain.getRandomVariableForConstant(value);
			}

			@Override
			public RandomVariable getMonteCarloWeights(int timeIndex) throws CalculationException {
				return liborSimulationPlain.getMonteCarloWeights(timeIndex);
			}

			@Override
			public RandomVariable getMonteCarloWeights(double time) throws CalculationException {
				return liborSimulationPlain.getMonteCarloWeights(time);
			}

			@Override
			public MonteCarloSimulationModel getCloneWithModifiedData(Map<String, Object> dataModified)
					throws CalculationException {
				return liborSimulationPlain.getCloneWithModifiedData(dataModified);
			}

			@Override
			public RandomVariable getForwardRate(double time, double periodStart, double periodEnd)
					throws CalculationException {
				return liborSimulationPlain.getForwardRate(time, periodStart, periodEnd);
			}

			@Override
			public RandomVariable getNumeraire(double time) throws CalculationException {
				BrownianMotion noiseGenerator;
				if(getNumberOfPaths() == 2000) noiseGenerator = noiseGenerator_2000;
				else noiseGenerator = noiseGenerator_10000;
				final RandomVariable noiseFactor2 = noiseGenerator.getBrownianIncrement(0.0, 1).mult(0.5).add(1.0);
				return liborSimulationPlain.getNumeraire(time).invert().mult(noiseFactor2).invert();
			}

			@Override
			public TermStructureModel getModel() {
				return liborSimulationPlain.getModel();
			}

			@Override
			public MonteCarloProcess getProcess() {
				return liborSimulationPlain.getProcess();
			}

			@Override
			public Object getCloneWithModifiedSeed(int seed) {
				return liborSimulationPlain.getCloneWithModifiedSeed(seed);
			}
		};

		liborSimulation = liborSimulationPlain;

		/*
		 * Create Hedge Instruments
		 */
		final List<TermStructureMonteCarloProduct> hedgeInstruments = new ArrayList<>();

		switch(config.hedgeInstrumentSet) {
		case FULL_BOND_CURVE:
			for(final double maturity : tenorTimeDiscretization) {
				if(maturity > 0.0) {
					hedgeInstruments.add(new Bond(maturity));
				}
			}
			break;

		case DISCRETE_ROLL_OVER_AND_PAYMENT_BOND:
			hedgeInstruments.add(new DiscreteTenorRollOver(fixingTime, paymentTime, TENOR_PERIOD_LENGTH));
			hedgeInstruments.add(new Bond(paymentTime));
			break;
		case TWO_BONDS_WITH_NOISE:
			/*
			 * Two independent source of noise
			 */
			AbstractTermStructureMonteCarloProduct bondWithNoise = new Bond(fixingTime) {
				@Override
				public RandomVariable getValue(final double evaluationTime, final TermStructureMonteCarloSimulationModel model) throws CalculationException {
					BrownianMotion noiseGenerator;
					if(model.getNumberOfPaths() == 2000) noiseGenerator = noiseGenerator_2000;
					else noiseGenerator = noiseGenerator_10000;
					final RandomVariable noiseFactor1 = noiseGenerator.getBrownianIncrement(0.0, 0).mult(0.5).add(1.0);
					return super.getValue(evaluationTime, model).mult(noiseFactor1);
				}
			};
			AbstractTermStructureMonteCarloProduct bondWithNoise2 = new Bond(paymentTime) {
				@Override
				public RandomVariable getValue(final double evaluationTime, final TermStructureMonteCarloSimulationModel model) throws CalculationException {
					BrownianMotion noiseGenerator;
					if(model.getNumberOfPaths() == 2000) noiseGenerator = noiseGenerator_2000;
					else noiseGenerator = noiseGenerator_10000;
					final RandomVariable noiseFactor3 = noiseGenerator.getBrownianIncrement(0.0, 0).mult(0.5).sub(1.0).mult(-1);
					return super.getValue(evaluationTime, model).mult(noiseFactor3);
				}
			};
			hedgeInstruments.add(bondWithNoise);
			hedgeInstruments.add(bondWithNoise2);
			break;

		case BOND_AND_ANNUITY:
			hedgeInstruments.add(new Bond(fixingTime));
			hedgeInstruments.add(new Bond(tenorHorizon));
			hedgeInstruments.add(new SwapAnnuity(new TimeDiscretizationFromArray(15.0, 10, TENOR_PERIOD_LENGTH)));
			break;

		case SHORT_AND_LONG_BOND:
			hedgeInstruments.add(new Bond(fixingTime));
			hedgeInstruments.add(new Bond(tenorHorizon));
			break;

		case TWO_BONDS:
		default:
			//				hedgeInstruments.add(new DiscreteTenorRollOver(fixingTime, paymentTime, TENOR_PERIOD_LENGTH));
			hedgeInstruments.add(new Bond(fixingTime));
			hedgeInstruments.add(new Bond(paymentTime));
			break;
		}


		final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentValueProvider hedgeInstrumentValueProvider =
				false & config.useAnalyticBondValuation
				? ForwardSensitivityDeltaHedgedPortfolio.getAnalyticBondValueProvider(TENOR_PERIOD_LENGTH)
						: ForwardSensitivityDeltaHedgedPortfolio.getProductValueProvider();
		final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentTradeValueProvider hedgeInstrumentTradeValueProvider =
				true & config.useAnalyticBondValuation
				? ForwardSensitivityDeltaHedgedPortfolio.getAnalyticBondTradeValueProvider(TENOR_PERIOD_LENGTH)
						: ForwardSensitivityDeltaHedgedPortfolio.getRegressionTradeValueProvider();

		/*
		 * Final marking convention.
		 * - At payment time we want product/cashflow convention.
		 * - At fixing time we want adapted tradable mark-to-market values.
		 */
		final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentValueProvider finalHedgeInstrumentValueProvider =
				config.evaluationMode == EvaluationMode.PAYMENT_TIME_CASHFLOW
				? ForwardSensitivityDeltaHedgedPortfolio.getProductValueProvider()
						: ForwardSensitivityDeltaHedgedPortfolio.getAnalyticBondValueProvider(TENOR_PERIOD_LENGTH);

		final RandomVariable targetValueAtEvaluationTime = getProductTargetValue(
				config.evaluationMode,
				config.productToHedge,
				fixingTime,
				paymentTime,
				TENOR_PERIOD_LENGTH,
				capletPlain,
				liborSimulation);
		final RandomVariable productValueAtTimeZero = capletProduct.getValue(0.0, liborSimulation);
		final RandomVariable plotStateAtFixing = getPlotStateAtFixing(
				config.productToHedge,
				fixingTime,
				paymentTime,
				tenorHorizon,
				TENOR_PERIOD_LENGTH,
				liborSimulation);
		final String plotStateLabel = getPlotStateLabel(config.productToHedge, fixingTime, paymentTime, tenorHorizon);

		System.out.println("Forward-sensitivity hedge experiment");
		System.out.println("experiment=" + config.name);
		System.out.println("modelType=" + config.modelType.name() + ", fixingTime=" + fixingTime + ", paymentTime=" + paymentTime
				+ ", evaluationTime=" + evaluationTime + ", product=" + config.productToHedge + ", numberOfPaths=" + config.modelType.getNumberOfPaths());
		System.out.println("useDiscountCurve=" + config.useDiscountCurve
				+ ", staticHedgeOnly=" + config.staticHedgeOnly
				+ ", hedgeInstrumentSet=" + config.hedgeInstrumentSet
				+ ", evaluationMode=" + config.evaluationMode
				+ ", useAnalyticBondValuation=" + config.useAnalyticBondValuation);
		System.out.println("basisTypes=" + Arrays.toString(BASIS_TYPES)
		+ ", regularizationLambdas=" + Arrays.toString(REGULARIZATION_LAMBDAS));
		System.out.println("hedge instruments=" + describeHedgeInstruments(hedgeInstruments));
		System.out.println("rebalancing times=" + rebalancingTimes.getNumberOfTimes() + " from "
				+ rebalancingTimes.getFirstTime() + " to " + rebalancingTimes.getLastTime());
		System.out.println("product value t=0 = " + productValueAtTimeZero.getAverage());
		System.out.println();

		if(config.printBondValuationDiagnostics && config.useAnalyticBondValuation) {
			printAnalyticBondValuationDiagnostics(
					"t=0",
					0.0,
					bondDiagnosticsSubset(hedgeInstruments),
					TENOR_PERIOD_LENGTH,
					liborSimulation);
			if(!config.staticHedgeOnly && rebalancingTimes.size() > 2) {
				printAnalyticBondValuationDiagnostics(
						"middle rebalance",
						rebalancingTimes.getTime(rebalancingTimes.size() / 2),
						bondDiagnosticsSubset(hedgeInstruments),
						TENOR_PERIOD_LENGTH,
						liborSimulation);
			}
		}

		if(config.printStaticDiagnostics) {
			printRollOverDiagnostic(
					fixingTime,
					paymentTime,
					TENOR_PERIOD_LENGTH,
					liborSimulation);
		}

		/*
		if(config.printStaticDiagnostics && config.productToHedge == ProductToHedge.FRA_15__SEMIANNUAL) {
			printZeroStrikeStaticDiagnostics(
					capletPlain,
					fixingTime,
					paymentTime,
					TENOR_PERIOD_LENGTH,
					liborSimulation);
			printDiscreteRollStaticDiagnostics(
					capletPlain,
					fixingTime,
					paymentTime,
					TENOR_PERIOD_LENGTH,
					liborSimulation);
		}
		*/

		final ForwardSensitivityFileExchange sensitivityExchange = createSensitivityExchange(
				config,
				rebalancingTimes,
				hedgeInstruments,
				liborSimulation,
				evaluationTime);
		HedgeRunResult pathwiseRawResult = null;
		HedgeRunResult externallyProjectedResult = null;

		/*
		 * Vary basis and lambda only after the model, product and hedge instruments
		 * have been constructed.
		 */
		for(final BasisType basisType : BASIS_TYPES) {
			for(final double regularizationLambda : REGULARIZATION_LAMBDAS) {
				final ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider solutionBasis = createBasis(
						basisType,
						config.productToHedge,
						fixingTime,
						paymentTime,
						tenorHorizon,
						3);
				final ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider petrovTestBasis = createBasis(
						basisType,
						config.productToHedge,
						fixingTime,
						paymentTime,
						tenorHorizon,
						1);

				System.out.println("basisType=" + basisType + ", regularizationLambda=" + regularizationLambda);

				final List<HedgeRunResult> results = new ArrayList<>();
				if(pathwiseRawResult == null) {
					final HedgeRatioProvider rawHedgeRatioProvider = sensitivityExchange != null
							&& sensitivityExchange.getMode() == ForwardSensitivityFileExchange.Mode.EXPORT_RAW
									? sensitivityExchange
									: ForwardSensitivityDeltaHedgedPortfolio.getDefaultHedgeRatioProvider();
					pathwiseRawResult = runHedge(
							"Pathwise-Raw",
							capletProduct,
							hedgeInstruments,
							rebalancingTimes,
							null,
							null,
							hedgeInstrumentValueProvider,
							hedgeInstrumentTradeValueProvider,
							finalHedgeInstrumentValueProvider,
							0.0,
							ReductionMethod.PATHWISE,
							liborSimulation,
							evaluationTime,
							targetValueAtEvaluationTime,
							rawHedgeRatioProvider);
					if(sensitivityExchange != null
							&& sensitivityExchange.getMode() == ForwardSensitivityFileExchange.Mode.EXPORT_RAW) {
						System.out.println("Raw forward sensitivities exported to " + sensitivityExchange.getDirectory());
						return;
					}
				}
				results.add(pathwiseRawResult);

				if(sensitivityExchange != null
						&& sensitivityExchange.getMode() == ForwardSensitivityFileExchange.Mode.IMPORT_PROJECTED) {
					if(externallyProjectedResult == null) {
						externallyProjectedResult = runHedge(
								"External-Projected",
								capletProduct,
								hedgeInstruments,
								rebalancingTimes,
								null,
								null,
								hedgeInstrumentValueProvider,
								hedgeInstrumentTradeValueProvider,
								finalHedgeInstrumentValueProvider,
								0.0,
								ReductionMethod.PATHWISE,
								liborSimulation,
								evaluationTime,
								targetValueAtEvaluationTime,
								sensitivityExchange);
					}
					results.add(externallyProjectedResult);
				}

				results.add(runHedge(
						"Pathwise-Projected",
						capletProduct,
						hedgeInstruments,
						rebalancingTimes,
					solutionBasis,
					null,
					hedgeInstrumentValueProvider,
					hedgeInstrumentTradeValueProvider,
					finalHedgeInstrumentValueProvider,
					0.0,
						ReductionMethod.PATHWISE,
						liborSimulation,
						evaluationTime,
						targetValueAtEvaluationTime));

				results.add(runHedge(
						"Galerkin",
						capletProduct,
						hedgeInstruments,
						rebalancingTimes,
						solutionBasis,
						null,
						hedgeInstrumentValueProvider,
						hedgeInstrumentTradeValueProvider,
						finalHedgeInstrumentValueProvider,
						regularizationLambda,
						ReductionMethod.PROJECTED_GALERKIN,
						liborSimulation,
						evaluationTime,
						targetValueAtEvaluationTime));

				results.add(runHedge(
						"Petrov-Galerkin",
						capletProduct,
						hedgeInstruments,
						rebalancingTimes,
						solutionBasis,
						petrovTestBasis,
						hedgeInstrumentValueProvider,
						hedgeInstrumentTradeValueProvider,
						finalHedgeInstrumentValueProvider,
						regularizationLambda,
						ReductionMethod.PROJECTED_GALERKIN,
						liborSimulation,
						evaluationTime,
						targetValueAtEvaluationTime));

				results.add(runHedge(
						"L2 Minimization",
						capletProduct,
						hedgeInstruments,
						rebalancingTimes,
						solutionBasis,
						null,
						hedgeInstrumentValueProvider,
						hedgeInstrumentTradeValueProvider,
						finalHedgeInstrumentValueProvider,
						regularizationLambda,
						ReductionMethod.L2,
						liborSimulation,
						evaluationTime,
						targetValueAtEvaluationTime));

				if(config.showScatterPlots) {
					//						CompletableFuture.runAsync(() -> {
					try {
						showScatterPlots(
								plotStateAtFixing,
								plotStateLabel,
								config.productToHedge.toString(),
								targetValueAtEvaluationTime,
								results,
								fixingTime,
								paymentTime,
								evaluationTime,
								config.saveToFile,
								"" + config.modelType + "," + config.hedgeInstrumentSet + "@" + config.rebalancingPerPeriod + "," + basisType + "," + regularizationLambda + "," + config.productToHedge + "," + config.evaluationMode);
					} catch (CalculationException e) {
						e.printStackTrace();
					}
					//						});
				}
			}
		}
	}

	private static HedgeRunResult runHedge(
			final String name,
			final TermStructureMonteCarloProduct productToReplicate,
			final List<TermStructureMonteCarloProduct> hedgeInstruments,
			final TimeDiscretization rebalancingTimes,
			final ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider solutionBasis,
			final ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider testBasis,
			final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentValueProvider hedgeInstrumentValueProvider,
			final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentTradeValueProvider hedgeInstrumentTradeValueProvider,
			final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentValueProvider finalHedgeInstrumentValueProvider,
			final double regularizationLambda,
			final ReductionMethod reductionMethod,
			final TermStructureMonteCarloSimulationModel model,
			final double evaluationTime,
			final RandomVariable targetValueAtEvaluationTime) throws CalculationException {

		return runHedge(
				name,
				productToReplicate,
				hedgeInstruments,
				rebalancingTimes,
				solutionBasis,
				testBasis,
				hedgeInstrumentValueProvider,
				hedgeInstrumentTradeValueProvider,
				finalHedgeInstrumentValueProvider,
				regularizationLambda,
				reductionMethod,
				model,
				evaluationTime,
				targetValueAtEvaluationTime,
				ForwardSensitivityDeltaHedgedPortfolio.getDefaultHedgeRatioProvider());
	}

	private static HedgeRunResult runHedge(
			final String name,
			final TermStructureMonteCarloProduct productToReplicate,
			final List<TermStructureMonteCarloProduct> hedgeInstruments,
			final TimeDiscretization rebalancingTimes,
			final ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider solutionBasis,
			final ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider testBasis,
			final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentValueProvider hedgeInstrumentValueProvider,
			final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentTradeValueProvider hedgeInstrumentTradeValueProvider,
			final ForwardSensitivityDeltaHedgedPortfolio.HedgeInstrumentValueProvider finalHedgeInstrumentValueProvider,
			final double regularizationLambda,
			final ReductionMethod reductionMethod,
			final TermStructureMonteCarloSimulationModel model,
			final double evaluationTime,
			final RandomVariable targetValueAtEvaluationTime,
			final HedgeRatioProvider hedgeRatioProvider) throws CalculationException {

		final ForwardSensitivityDeltaHedgedPortfolio hedge = new ForwardSensitivityDeltaHedgedPortfolio(
				productToReplicate,
				hedgeInstruments,
				rebalancingTimes,
				solutionBasis,
				testBasis,
				ForwardSensitivityDeltaHedgedPortfolio.getProcessStateParameterIDProvider(),
				hedgeInstrumentValueProvider,
				hedgeInstrumentTradeValueProvider,
				finalHedgeInstrumentValueProvider,
				regularizationLambda,
				reductionMethod,
				hedgeRatioProvider);

		final RandomVariable hedgeValue = hedge.getValue(evaluationTime, model);

		RandomVariable targetValueAtEvaluationTimeDiscounted = targetValueAtEvaluationTime.div(model.getNumeraire(evaluationTime)).mult(model.getNumeraire(0.0));
		RandomVariable hedgeValueDiscounted = hedgeValue.div(model.getNumeraire(evaluationTime)).mult(model.getNumeraire(0.0));
		printHedgeStatistics(name, targetValueAtEvaluationTimeDiscounted, hedgeValueDiscounted, hedge);

		return new HedgeRunResult(name, hedgeValue, hedgeValueDiscounted.sub(targetValueAtEvaluationTimeDiscounted), hedge);
	}

	private static ForwardSensitivityFileExchange createSensitivityExchange(
			final ExperimentConfig config,
			final TimeDiscretization rebalancingTimes,
			final List<TermStructureMonteCarloProduct> hedgeInstruments,
			final TermStructureMonteCarloSimulationModel model,
			final double evaluationTime) throws IOException {

		if(config.sensitivityExchange == null) {
			return null;
		}
		final TimeDiscretization exchangeRebalancingTimes = getSensitivityExchangeRebalancingTimes(
				rebalancingTimes,
				evaluationTime);

		final List<String> targetNames = new ArrayList<>();
		targetNames.add("product:" + config.productToHedge.name());
		for(int hedgeIndex = 0; hedgeIndex < hedgeInstruments.size(); hedgeIndex++) {
			targetNames.add("hedge[" + hedgeIndex + "]:" + describeHedgeInstrument(hedgeInstruments.get(hedgeIndex)));
		}

		final String experimentDescription = String.join(";",
				"model=" + config.modelType.name(),
				"modelClass=" + model.getModel().getClass().getName(),
				"product=" + config.productToHedge.name(),
				"productSpec=" + config.productToHedge,
				"hedgeSet=" + config.hedgeInstrumentSet.name(),
				"evaluationMode=" + config.evaluationMode.name(),
				"evaluationTime=" + evaluationTime,
				"seed=" + config.seed,
				"numberOfFactors=" + config.modelType.getNumberOfFactors(),
				"simulationStepsPerTenor=" + config.modelType.getNumberOfSimulationStepsPerTenor(),
				"useDiscountCurve=" + config.useDiscountCurve,
				"useAnalyticBondValuation=" + config.useAnalyticBondValuation,
				"staticHedgeOnly=" + config.staticHedgeOnly,
				"rebalancingPerPeriod=" + config.rebalancingPerPeriod,
				"tenorPeriodLength=" + TENOR_PERIOD_LENGTH);

		final ForwardSensitivityFileExchange exchange;
		if(config.sensitivityExchange.mode == SensitivityExchangeMode.EXPORT_RAW) {
			exchange = ForwardSensitivityFileExchange.forExport(
					config.sensitivityExchange.directory,
					exchangeRebalancingTimes,
					model.getNumberOfPaths(),
					targetNames,
					experimentDescription,
					(time, parameterIDsByName) -> getProcessStateValues(time, parameterIDsByName, model));
		}
		else {
			exchange = ForwardSensitivityFileExchange.forImport(
					config.sensitivityExchange.directory,
					exchangeRebalancingTimes,
					model.getNumberOfPaths(),
					targetNames,
					experimentDescription,
					(time, parameterIDsByName) -> getProcessStateValues(time, parameterIDsByName, model));
		}

		System.out.println("sensitivityExchange=" + config.sensitivityExchange.mode
				+ ", directory=" + exchange.getDirectory());
		return exchange;
	}

	static TimeDiscretization getSensitivityExchangeRebalancingTimes(
			final TimeDiscretization rebalancingTimes,
			final double evaluationTime) {
		return rebalancingTimes.filter(time -> time >= 0.0 && time < evaluationTime);
	}

	private static Map<String, RandomVariable> getProcessStateValues(
			final double evaluationTime,
			final Map<String, Long> parameterIDsByName,
			final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		int processTimeIndex = model.getTimeIndex(evaluationTime);
		if(processTimeIndex < 0) {
			processTimeIndex = model.getProcess().getTimeDiscretization().getTimeIndexNearestLessOrEqual(evaluationTime);
		}
		if(processTimeIndex < 0) {
			throw new IllegalArgumentException("Could not find process time index for evaluationTime " + evaluationTime + ".");
		}

		final Map<Long, RandomVariable> stateValuesByID = new HashMap<>();
		for(final RandomVariable stateValue : model.getProcess().getProcessValue(processTimeIndex)) {
			if(stateValue instanceof RandomVariableDifferentiable) {
				stateValuesByID.put(((RandomVariableDifferentiable)stateValue).getID(), stateValue.getValues());
			}
		}

		final Map<String, RandomVariable> stateValuesByName = new LinkedHashMap<>();
		for(final Map.Entry<String, Long> parameter : parameterIDsByName.entrySet()) {
			final RandomVariable stateValue = stateValuesByID.get(parameter.getValue());
			if(stateValue == null) {
				throw new IllegalArgumentException("Could not find process-state value for risk factor "
						+ parameter.getKey() + ".");
			}
			stateValuesByName.put(parameter.getKey(), stateValue);
		}
		return stateValuesByName;
	}

	private static ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider createBasis(
			final BasisType basisType,
			final ProductToHedge productToHedge,
			final double fixingTime,
			final double paymentTime,
			final double tenorHorizon,
			final int maxPower) {

		switch(basisType) {
		case PROCESS_STATE_POLYNOMIAL:
			return createProcessStatePolynomialBasis(maxPower);

		case UNDERLYING_FORWARD_ONLY:
			return createUnderlyingForwardPolynomialBasis(productToHedge, fixingTime, paymentTime, tenorHorizon, 1, false);

		case UNDERLYING_FORWARD_POLYNOMIAL:
		default:
			return createUnderlyingForwardPolynomialBasis(productToHedge, fixingTime, paymentTime, tenorHorizon, maxPower, maxPower != 1);
		}
	}

	private static ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider createUnderlyingForwardPolynomialBasis(
			final ProductToHedge productToHedge,
			final double fixingTime,
			final double paymentTime,
			final double tenorHorizon,
			final int maxPower,
			final boolean localize) {

		switch(productToHedge) {
		case SWAPTION_15_IN_5:
//			return createForwardCurvePolynomialBasis(fixingTime, tenorHorizon, TENOR_PERIOD_LENGTH, maxPower);
			return createForwardSwapratePolynomialBasis(fixingTime, tenorHorizon, TENOR_PERIOD_LENGTH, maxPower);

		case FRA_15__SEMIANNUAL:
		case CAPLET_15_SEMIANNUAL:
		default:
			return createCapletForwardPolynomialBasis(fixingTime, paymentTime, maxPower, localize);
		}
	}

	private static ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider createForwardSwapratePolynomialBasis(
			final double firstFixingTime,
			final double lastPaymentTime,
			final double tenorPeriodLength,
			final int maxPower) {

		return (evaluationTime, model) -> {
			final List<RandomVariable> basis = new ArrayList<>();
			basis.add(model.getRandomVariableForConstant(1.0));

			if(evaluationTime == 0.0) {
				return basis.toArray(new RandomVariable[basis.size()]);
			}

			RandomVariable annuityAggregate = Scalar.of(0.0);
			for(double periodStart = firstFixingTime; periodStart < lastPaymentTime - 1E-12; periodStart += tenorPeriodLength) {
				final double periodEnd = Math.min(periodStart + tenorPeriodLength, lastPaymentTime);
				final double periodLength = periodEnd - periodStart;
				final RandomVariable forwardBond = model.getForwardRate(evaluationTime, firstFixingTime, periodEnd).mult(periodEnd-firstFixingTime).add(1.0).invert();
				annuityAggregate = annuityAggregate.addProduct(forwardBond, periodLength);
			}
			basis.add(annuityAggregate);

			final RandomVariable forwardSwapRate = model.getForwardRate(evaluationTime, firstFixingTime, lastPaymentTime).mult(lastPaymentTime-firstFixingTime).add(1.0).invert().bus(1.0).div(annuityAggregate);

			boolean localize = true;
			if(!localize) {
				basis.addAll(new Monomials(0, maxPower + 1).of(forwardSwapRate));
			}
			else {
				addLocalizedForwardBasisFunctions(basis, forwardSwapRate, DEFAULT_CAPLET_STRIKE, 0, maxPower+1);
				addLocalizedForwardBasisFunctions(basis, forwardSwapRate, forwardSwapRate.getAverage() + 1.0 * forwardSwapRate.getStandardDeviation(), 0, maxPower+1);
				addLocalizedForwardBasisFunctions(basis, forwardSwapRate, forwardSwapRate.getAverage() - 1.0 * forwardSwapRate.getStandardDeviation(), 0, maxPower+1);
			}

			return basis.toArray(new RandomVariable[basis.size()]);
		};
	}

	private static ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider createForwardCurvePolynomialBasis(
			final double firstFixingTime,
			final double lastPaymentTime,
			final double tenorPeriodLength,
			final int maxPower) {

		return (evaluationTime, model) -> {
			final List<RandomVariable> basis = new ArrayList<>();
			basis.add(model.getRandomVariableForConstant(1.0));

			if(evaluationTime == 0.0) {
				return basis.toArray(new RandomVariable[basis.size()]);
			}

			for(double periodStart = firstFixingTime; periodStart < lastPaymentTime - 1E-12; periodStart += tenorPeriodLength) {
				final double periodEnd = Math.min(periodStart + tenorPeriodLength, lastPaymentTime);
				final RandomVariable forward = model.getForwardRate(evaluationTime, periodStart, periodEnd);
				basis.addAll(new Monomials(1, maxPower + 1).of(forward));
			}

			return basis.toArray(new RandomVariable[basis.size()]);
		};
	}

	private static ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider createCapletForwardPolynomialBasis(
			final double fixingTime,
			final double paymentTime,
			final int maxPower,
			final boolean localize) {

		return (evaluationTime, model) -> {
			final RandomVariable one = model.getRandomVariableForConstant(1.0);
			final RandomVariable zero = model.getRandomVariableForConstant(0.0);

			/*
			 * At time zero the forward is deterministic, so powers of the forward are
			 * collinear with the constant. Returning only the constant avoids a rank-
			 * deficient reduced system at the initial hedge.
			 */
			if(evaluationTime == 0.0) {
				return new RandomVariable[] { one };
			}

			final List<RandomVariable> basis = new ArrayList<>();
			final RandomVariable forward = model.getForwardRate(evaluationTime, fixingTime, paymentTime);
			if(!localize) {
				basis.addAll(new Monomials(0, maxPower + 1).of(forward));
			}
			else {
				addLocalizedForwardBasisFunctions(basis, forward, forward.getAverage() + 0.0 * forward.getStandardDeviation(), 0, maxPower+1);
				addLocalizedForwardBasisFunctions(basis, forward, forward.getAverage() + 1.0 * forward.getStandardDeviation(), 0, maxPower+1);
				addLocalizedForwardBasisFunctions(basis, forward, forward.getAverage() - 1.0 * forward.getStandardDeviation(), 0, maxPower+1);
			}

			//			final RandomVariable numeraire = model.getNumeraire(evaluationTime);
			//			basis.addAll(new Monomials(1, maxPower + 1).of(numeraire));

			return basis.toArray(new RandomVariable[basis.size()]);
		};
	}

	private static void addLocalizedForwardBasisFunctions(
			final List<RandomVariable> basis,
			final RandomVariable value,
			final double slitPoint,
			final int powerStart,
			final int powerEnd) {

		final RandomVariable localizer = value.sub(slitPoint).choose(Scalar.ONE, Scalar.ZERO);

		final List<RandomVariable> monomials = new Monomials(powerStart, powerEnd).of(value);
		for(RandomVariable monomial : monomials) basis.add(monomial.mult(localizer));
	}

	private static ForwardSensitivityDeltaHedgedPortfolio.BasisFunctionProvider createProcessStatePolynomialBasis(
			final int maxPower) {

		return (evaluationTime, model) -> {
			final List<RandomVariable> basis = new ArrayList<>();
			basis.add(model.getRandomVariableForConstant(1.0));

			if(evaluationTime == 0.0) {
				return basis.toArray(new RandomVariable[basis.size()]);
			}

			int processTimeIndex = model.getTimeIndex(evaluationTime);
			if(processTimeIndex < 0) {
				processTimeIndex = model.getProcess().getTimeDiscretization().getTimeIndexNearestLessOrEqual(evaluationTime);
			}
			if(processTimeIndex < 0) {
				throw new IllegalArgumentException("Could not find process time index for evaluationTime " + evaluationTime + ".");
			}

			final RandomVariable[] processState = model.getProcess().getProcessValue(processTimeIndex);
			for(final RandomVariable stateComponent : processState) {
				/* Skip exponent 0 here, because the constant was already added. */
				basis.addAll(new Monomials(1, maxPower + 1).of(stateComponent.getValues()));
			}

			return basis.toArray(new RandomVariable[basis.size()]);
		};
	}

	private static RandomVariable getProductTargetValue(
			final EvaluationMode evaluationMode,
			final ProductToHedge productToHedge,
			final double fixingTime,
			final double paymentTime,
			final double tenorPeriodLength,
			final TermStructureMonteCarloProduct product,
			final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		if(evaluationMode == EvaluationMode.PAYMENT_TIME_CASHFLOW && productToHedge == ProductToHedge.SWAPTION_15_IN_5) {
			throw new IllegalArgumentException("PAYMENT_TIME_CASHFLOW is not defined for the swaption experiment. Use FIXING_TIME_MARK_TO_MARKET at the exercise date instead.");
		}

		final double targetEvaluationTime = evaluationMode == EvaluationMode.PAYMENT_TIME_CASHFLOW ? paymentTime : fixingTime;
		return product.getValue(targetEvaluationTime, model);
	}

	private static RandomVariable getPlotStateAtFixing(
			final ProductToHedge productToHedge,
			final double fixingTime,
			final double paymentTime,
			final double tenorHorizon,
			final double tenorPeriodLength,
			final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		switch(productToHedge) {
		case SWAPTION_15_IN_5:
			return getForwardSwapRate(fixingTime, fixingTime, tenorHorizon, tenorPeriodLength, model);

		case FRA_15__SEMIANNUAL:
		case CAPLET_15_SEMIANNUAL:
		default:
			return model.getForwardRate(fixingTime, fixingTime, paymentTime);
		}
	}

	private static String getPlotStateLabel(
			final ProductToHedge productToHedge,
			final double fixingTime,
			final double paymentTime,
			final double tenorHorizon) {

		switch(productToHedge) {
		case SWAPTION_15_IN_5:
			return "Forward swap rate S(" + fixingTime + "; " + fixingTime + ", " + tenorHorizon + ") at exercise";

		case FRA_15__SEMIANNUAL:
		case CAPLET_15_SEMIANNUAL:
		default:
			return "Forward rate L(" + fixingTime + ", " + paymentTime + ") at fixing";
		}
	}

	private static RandomVariable getForwardSwapRate(
			final double evaluationTime,
			final double firstFixingTime,
			final double lastPaymentTime,
			final double tenorPeriodLength,
			final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		RandomVariable floatLeg = model.getRandomVariableForConstant(0.0);
		RandomVariable annuity = model.getRandomVariableForConstant(0.0);

		for(double periodStart = firstFixingTime; periodStart < lastPaymentTime - 1E-12; periodStart += tenorPeriodLength) {
			final double periodEnd = Math.min(periodStart + tenorPeriodLength, lastPaymentTime);
			final RandomVariable forward = model.getForwardRate(evaluationTime, periodStart, periodEnd);
			final RandomVariable discountFactor = ForwardSensitivityDeltaHedgedPortfolio.getAnalyticBondValue(
					evaluationTime,
					periodEnd,
					tenorPeriodLength,
					model);
			final RandomVariable periodAnnuity = discountFactor.mult(periodEnd - periodStart);

			floatLeg = floatLeg.add(forward.mult(periodAnnuity));
			annuity = annuity.add(periodAnnuity);
		}

		return floatLeg.div(annuity);
	}

	private static void printRollOverDiagnostic(
			final double fixingTime,
			final double paymentTime,
			final double tenorPeriodLength,
			final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		final RandomVariable one = model.getRandomVariableForConstant(1.0);
		final RandomVariable liborAtFixing = model.getForwardRate(fixingTime, fixingTime, paymentTime);
		final RandomVariable discreteLiborRoll = one.add(liborAtFixing.mult(tenorPeriodLength));

		final RandomVariable cashRollFromMaturedBond = new Bond(fixingTime).getValue(paymentTime, model);
		final RandomVariable paymentBondAtPayment = new Bond(paymentTime).getValue(paymentTime, model);
		final RandomVariable cashRollNetOfPaymentBond = cashRollFromMaturedBond.div(paymentBondAtPayment);

		final RandomVariable rollError = cashRollNetOfPaymentBond.sub(discreteLiborRoll);

		System.out.println("Fixing-to-payment roll-over diagnostic");
		System.out.printf(Locale.US,
				"E[cashRoll]=% .10e  E[liborRoll]=% .10e  E[diff]=% .4e  RMSE(diff)=% .4e  q05=% .4e  q50=% .4e  q95=% .4e%n%n",
				cashRollNetOfPaymentBond.getAverage(),
				discreteLiborRoll.getAverage(),
				rollError.getAverage(),
				Math.sqrt(averageSquare(rollError)),
				rollError.getQuantile(0.05),
				rollError.getQuantile(0.50),
				rollError.getQuantile(0.95));
	}

	private static void printZeroStrikeStaticDiagnostics(
			final Caplet capletProduct,
			final double fixingTime,
			final double paymentTime,
			final double tenorPeriodLength,
			final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		final RandomVariable payoff = capletProduct.getValue(paymentTime, model);
		final RandomVariable staticBondGain = new Bond(fixingTime).getValue(paymentTime, model)
				.sub(new Bond(paymentTime).getValue(paymentTime, model));
		final RandomVariable staticError = staticBondGain.sub(payoff);

		final RandomVariable bondAtFixingTime0 = new Bond(fixingTime).getValue(0.0, model);
		final RandomVariable bondAtPaymentTime0 = new Bond(paymentTime).getValue(0.0, model);
		final double staticPrice0 = bondAtFixingTime0.getAverage() - bondAtPaymentTime0.getAverage();
		final double capletPrice0 = capletProduct.getValue(0.0, model).getAverage();

		final RandomVariable analyticBondAtFixingTime0 = ForwardSensitivityDeltaHedgedPortfolio.getAnalyticBondValue(
				0.0,
				fixingTime,
				tenorPeriodLength,
				model);
		final RandomVariable analyticBondAtPaymentTime0 = ForwardSensitivityDeltaHedgedPortfolio.getAnalyticBondValue(
				0.0,
				paymentTime,
				tenorPeriodLength,
				model);
		final double analyticStaticPrice0 = analyticBondAtFixingTime0.getAverage()
				- analyticBondAtPaymentTime0.getAverage();

		System.out.println("Zero-strike static two-bond diagnostic");
		System.out.printf(Locale.US,
				"static payoff identity: E[error]=% .4e  RMSE=% .4e  q05=% .4e  q50=% .4e  q95=% .4e%n",
				staticError.getAverage(),
				Math.sqrt(averageSquare(staticError)),
				staticError.getQuantile(0.05),
				staticError.getQuantile(0.50),
				staticError.getQuantile(0.95));
		System.out.printf(Locale.US,
				"static price identity : P(%.1f)-P(%.1f)=% .10e  analytic=% .10e  capletPrice=% .10e  diff=% .4e%n%n",
				fixingTime,
				paymentTime,
				staticPrice0,
				analyticStaticPrice0,
				capletPrice0,
				staticPrice0 - capletPrice0);
	}

	private static void printDiscreteRollStaticDiagnostics(
			final Caplet capletProduct,
			final double fixingTime,
			final double paymentTime,
			final double tenorPeriodLength,
			final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		final RandomVariable payoff = capletProduct.getValue(paymentTime, model);
		final RandomVariable discreteRollGain = new DiscreteTenorRollOver(fixingTime, paymentTime, tenorPeriodLength)
				.getValue(paymentTime, model)
				.sub(new Bond(paymentTime).getValue(paymentTime, model));
		final RandomVariable discreteRollError = discreteRollGain.sub(payoff);

		System.out.println("Zero-strike discrete-roll diagnostic");
		System.out.printf(Locale.US,
				"discrete-roll payoff identity: E[error]=% .4e  RMSE=% .4e  q05=% .4e  q50=% .4e  q95=% .4e%n%n",
				discreteRollError.getAverage(),
				Math.sqrt(averageSquare(discreteRollError)),
				discreteRollError.getQuantile(0.05),
				discreteRollError.getQuantile(0.50),
				discreteRollError.getQuantile(0.95));
	}

	private static void printAnalyticBondValuationDiagnostics(
			final String label,
			final double evaluationTime,
			final List<TermStructureMonteCarloProduct> hedgeInstruments,
			final double tenorPeriodLength,
			final TermStructureMonteCarloSimulationModel model) throws CalculationException {

		System.out.println("Model-implied bond valuation diagnostic (" + label + ", t=" + evaluationTime + ")");
		for(final TermStructureMonteCarloProduct hedgeInstrument : hedgeInstruments) {
			if(!(hedgeInstrument instanceof Bond)) {
				continue;
			}

			final double maturity = ((Bond)hedgeInstrument).getMaturity();
			final RandomVariable productValue = hedgeInstrument.getValue(evaluationTime, model);
			final RandomVariable modelBondValue = ForwardSensitivityDeltaHedgedPortfolio.getAnalyticBondValue(
					evaluationTime,
					maturity,
					tenorPeriodLength,
					model);
			final RandomVariable forwardProductValue = ForwardSensitivityDeltaHedgedPortfolio.getForwardProductBondValue(
					evaluationTime,
					maturity,
					tenorPeriodLength,
					model);
			final RandomVariable productMinusModel = productValue.sub(modelBondValue);
			final RandomVariable forwardProductMinusModel = forwardProductValue.sub(modelBondValue);

			System.out.printf(Locale.US,
					"  P(%.2f): E[Bond.getValue]=% .10e  E[model P(t,T)]=% .10e  E[Bond-model]=% .4e  RMSE(Bond-model)=% .4e%n",
					maturity,
					productValue.getAverage(),
					modelBondValue.getAverage(),
					productMinusModel.getAverage(),
					Math.sqrt(averageSquare(productMinusModel)));
			System.out.printf(Locale.US,
					"           E[forward-product]=% .10e  E[forward-product - model]=% .4e  RMSE(forward-product - model)=% .4e%n",
					forwardProductValue.getAverage(),
					forwardProductMinusModel.getAverage(),
					Math.sqrt(averageSquare(forwardProductMinusModel)));
		}
		System.out.println();
	}

	private static List<TermStructureMonteCarloProduct> bondDiagnosticsSubset(
			final List<TermStructureMonteCarloProduct> hedgeInstruments) {

		final int maximumNumberOfBondsToPrint = 6;
		final List<TermStructureMonteCarloProduct> subset = new ArrayList<>();
		for(final TermStructureMonteCarloProduct hedgeInstrument : hedgeInstruments) {
			if(hedgeInstrument instanceof Bond) {
				subset.add(hedgeInstrument);
			}
			if(subset.size() >= maximumNumberOfBondsToPrint) {
				break;
			}
		}
		return subset;
	}

	private static void printHedgeStatistics(
			final String name,
			final RandomVariable targetValue,
			final RandomVariable hedgeValue,
			final ForwardSensitivityDeltaHedgedPortfolio hedge) {

		final RandomVariable hedgeError = hedgeValue.sub(targetValue);
		final double variancePayoff = variance(targetValue);
		final double varianceError = variance(hedgeError);
		final double varianceReduction = variancePayoff > 0.0 ? 1.0 - varianceError / variancePayoff : Double.NaN;

		System.out.printf(Locale.US,
				"%-16s E[target]=% .10e  E[hedge]=% .10e  bias=% .4e  RMSE=% .4e  MAE=% .4e  q05=% .4e  q50=% .4e  q95=% .4e  varRed=% .2f%%%n",
				name,
				targetValue.getAverage(),
				hedgeValue.getAverage(),
				hedgeError.getAverage(),
				Math.sqrt(averageSquare(hedgeError)),
				averageAbsolute(hedgeError),
				hedgeError.getQuantile(0.05),
				hedgeError.getQuantile(0.50),
				hedgeError.getQuantile(0.95),
				100.0 * varianceReduction);

		System.out.printf(Locale.US,
				"%-16s timings: total=%6.2fs, valuation=%6.2fs, trade-values=%6.2fs, hedge-ratios=%6.2fs, project=%6.2fs, solve=%6.2fs, rebalances=%d%n",
				name,
				hedge.getLastOperationTimingTotal(),
				hedge.getLastOperationTimingValuation(),
				hedge.getLastOperationTimingTradeValues(),
				hedge.getLastOperationTimingHedgeRatios(),
				hedge.getLastOperationTimingHedgeRatioProject(),
				hedge.getLastOperationTimingHedgeRatioSolve(),
				hedge.getLastRebalancingTimes().size());

		printFirstRebalanceHedgeRatios(name, hedge);
		printLastRebalanceHedgeRatios(name, hedge);
		System.out.println();
	}

	private static void printFirstRebalanceHedgeRatios(
			final String name,
			final ForwardSensitivityDeltaHedgedPortfolio hedge) {

		if(hedge.getLastHedgeRatioResults().isEmpty()) {
			return;
		}

		printHedgeRatios(name + " first", hedge.getLastHedgeRatioResults().get(0));
	}

	private static void printLastRebalanceHedgeRatios(
			final String name,
			final ForwardSensitivityDeltaHedgedPortfolio hedge) {

		if(hedge.getLastHedgeRatioResults().size() < 2) {
			return;
		}

		printHedgeRatios(name + " last ", hedge.getLastHedgeRatioResults().get(hedge.getLastHedgeRatioResults().size() - 1));
	}

	private static void printHedgeRatios(
			final String label,
			final ProjectedHedgeRatioResult result) {

		final RandomVariable[] hedgeRatios = result.getHedgeRatios();
		final StringBuilder message = new StringBuilder();
		message.append(String.format(Locale.US, "%-22s hedge ratios: ", label));

		for(int hedgeIndex = 0; hedgeIndex < hedgeRatios.length; hedgeIndex++) {
			if(hedgeIndex > 0) {
				message.append(", ");
			}
			message.append("phi[").append(hedgeIndex).append("] avg=")
			.append(String.format(Locale.US, "% .8e", hedgeRatios[hedgeIndex].getAverage()))
			.append(" q05=")
			.append(String.format(Locale.US, "% .8e", hedgeRatios[hedgeIndex].getQuantile(0.05)))
			.append(" q95=")
			.append(String.format(Locale.US, "% .8e", hedgeRatios[hedgeIndex].getQuantile(0.95)));
		}

		System.out.println(message.toString());
	}

	private static void showScatterPlots(
			final RandomVariable plotStateAtFixing,
			final String plotStateLabel,
			final String productName,
			final RandomVariable targetValue,
			final List<HedgeRunResult> results,
			final double fixingTime,
			final double paymentTime,
			final double evaluationTime,
			final boolean saveToFile,
			final String configSpec) throws CalculationException {

		final NumberFormat percentageFormat = DecimalFormat.getPercentInstance();
		percentageFormat.setMinimumFractionDigits(2);

		// For plots: weight of the stroke
		Stroke dotted = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, new float[] {5,5}, 0);
		Stroke stroke = new BasicStroke(3.0f);
		double r = 0.5;
		java.awt.Shape point = new Ellipse2D.Double(-r, -r, 2*r, 2*r);

		for(final HedgeRunResult result : results) {

			boolean isPlotValue = true;
			if(isPlotValue) {
				List<Point2D> target = new ArrayList<>();
				List<Point2D> hedge = new ArrayList<>();
				for(int pathIndex = 0; pathIndex < result.hedgeError.size(); pathIndex++) {
					target.add(new Point2D(plotStateAtFixing.get(pathIndex), targetValue.get(pathIndex)));
					hedge.add(new Point2D(plotStateAtFixing.get(pathIndex), result.hedgeValue.get(pathIndex)));
				}

				final List<Plotable2D> plotables = List.of(
						new PlotablePoints2D("hedge", hedge,
								new GraphStyle(point, null, Color.RED)),
						new PlotablePointsWithConfidenceIntervall2D("hedge", target,
								new Named<DoubleUnaryOperator>("target - 2 stdDev", (DoubleUnaryOperator)
										x -> -2*result.hedgeError.getStandardDeviation()
										),
								new Named<DoubleUnaryOperator>("target + 2 stdDev", (DoubleUnaryOperator)
										x -> +2*result.hedgeError.getStandardDeviation()
										),
								new GraphStyle(null, dotted, Color.GREEN, ColorUtils.colorWithAlpha(Color.CYAN, 0.4)))
						);
				final Plot2D plotValue = new Plot2D(plotables);
				plotValue
				.setTitle("Hedge of " + productName + " using " + result.name)
				.setSubtitle("(" + configSpec + ")")
				.setXAxisLabel(plotStateLabel)
				.setYAxisLabel("value at t=" + evaluationTime)
				.setXAxisNumberFormat(percentageFormat)
				.setYAxisNumberFormat(percentageFormat)
				.setXRange( 0.00, 0.04)
				.setYRange(-0.01, 0.05)
				.show();
				if(saveToFile) {
					try {
						final Path path = Files.createDirectories(Path.of("images",ForwardSensitivityCapletHedgingExperiment.class.getName()));
						plotValue.saveAsPDF(new File(path + File.separator + "Hedge-Value-(" + configSpec + ")-" + result.name + ".pdf"), 800, 400);
					} catch (IOException e) {
						e.printStackTrace();
					}
					plotValue.close();
				}
			}


			List<Point2D> errors = new ArrayList<>();
			for(int pathIndex = 0; pathIndex < result.hedgeError.size(); pathIndex++) {
				errors.add(new Point2D(plotStateAtFixing.get(pathIndex), result.hedgeError.get(pathIndex)));
			}
			final List<Plotable2D> plotables = List.of(
					new PlotablePoints2D("error", errors,
							new GraphStyle(point, null, Color.RED)),
					new PlotableFunctionWithConfidenceInterval2D(-0.02, 0.07, 400,
							new Named<DoubleUnaryOperator>("Mean Error", (DoubleUnaryOperator)
									t -> result.hedgeError.getAverage()
									),
							new Named<DoubleUnaryOperator>("mean - stdDev", (DoubleUnaryOperator)
									t -> result.hedgeError.getAverage()-2*result.hedgeError.getStandardDeviation()
									),
							new Named<DoubleUnaryOperator>("mean + stdDev", (DoubleUnaryOperator)
									t -> result.hedgeError.getAverage()+2*result.hedgeError.getStandardDeviation()
									),
							new GraphStyle(null, dotted, Color.BLUE, ColorUtils.colorWithAlpha(Color.CYAN, 0.4))),
					new PlotableFunction2D(-0.02, 0.07, 400,
							new Named<DoubleUnaryOperator>("(zero)", (DoubleUnaryOperator)
									t -> 0.0
									),
							new GraphStyle(null, stroke, Color.BLACK))
					);
			final Plot2D plot = new Plot2D(plotables);
			plot
			.setTitle("Hedge Error of " + productName + " Hedge using " + result.name)
			.setSubtitle("(" + configSpec + ")")
			.setXAxisLabel(plotStateLabel)
			.setYAxisLabel("error (hedge - target)")
			.setXAxisNumberFormat(percentageFormat)
			.setYAxisNumberFormat(percentageFormat)
			.setXRange( 0.00, 0.04)
			.setYRange(-0.02, 0.02)
			.show();
			if(saveToFile) {
				try {
					final Path path = Files.createDirectories(Path.of("images",ForwardSensitivityCapletHedgingExperiment.class.getName()));
					plot.saveAsPDF(new File(path + File.separator + "Hedge-Error-(" + configSpec + ")-" + result.name + ".pdf"), 800, 400);
				} catch (IOException e) {
					e.printStackTrace();
				}
				plot.close();
			}
		}
	}

	private static String describeHedgeInstruments(final List<TermStructureMonteCarloProduct> hedgeInstruments) {
		final List<String> descriptions = new ArrayList<>();
		for(final TermStructureMonteCarloProduct hedgeInstrument : hedgeInstruments) {
			descriptions.add(describeHedgeInstrument(hedgeInstrument));
		}
		return descriptions.toString();
	}

	private static String describeHedgeInstrument(final TermStructureMonteCarloProduct hedgeInstrument) {
		if(hedgeInstrument instanceof Bond) {
			return "P(" + ((Bond)hedgeInstrument).getMaturity() + ")";
		}
		if(hedgeInstrument instanceof DiscreteTenorRollOver) {
			final DiscreteTenorRollOver instrument = (DiscreteTenorRollOver)hedgeInstrument;
			return "DiscreteRoll(" + instrument.getFixingTime() + "," + instrument.getPaymentTime() + ")";
		}
		return hedgeInstrument.getClass().getSimpleName();
	}

	private static double averageAbsolute(final RandomVariable randomVariable) {
		if(randomVariable.isDeterministic()) {
			return Math.abs(randomVariable.doubleValue());
		}

		double sum = 0.0;
		for(int path = 0; path < randomVariable.size(); path++) {
			sum += Math.abs(randomVariable.get(path));
		}
		return sum / randomVariable.size();
	}

	private static double averageSquare(final RandomVariable randomVariable) {
		if(randomVariable.isDeterministic()) {
			return randomVariable.doubleValue() * randomVariable.doubleValue();
		}

		double sum = 0.0;
		for(int path = 0; path < randomVariable.size(); path++) {
			final double value = randomVariable.get(path);
			sum += value * value;
		}
		return sum / randomVariable.size();
	}

	private static double variance(final RandomVariable randomVariable) {
		final double mean = randomVariable.getAverage();
		return averageSquare(randomVariable) - mean * mean;
	}

	private static final class SensitivityExchangeConfig {

		private final SensitivityExchangeMode mode;
		private final Path directory;

		private SensitivityExchangeConfig(final SensitivityExchangeMode mode, final Path directory) {
			this.mode = mode;
			this.directory = directory.toAbsolutePath().normalize();
		}
	}

	public static final class ExperimentConfig {

		private final String name;
		private final boolean useDiscountCurve;
		private final boolean showScatterPlots;
		private final boolean staticHedgeOnly;
		private final boolean printStaticDiagnostics;
		private final boolean useAnalyticBondValuation;
		private final boolean printBondValuationDiagnostics;
		private final boolean saveToFile;
		private final ModelType modelType;
		private final HedgeInstrumentSet hedgeInstrumentSet;
		private final EvaluationMode evaluationMode;
		private final int seed;
		private final int rebalancingPerPeriod;
		private final ProductToHedge productToHedge;
		private final SensitivityExchangeConfig sensitivityExchange;

		private ExperimentConfig(
				final String name,
				final boolean useDiscountCurve,
				final boolean showScatterPlots,
				final boolean staticHedgeOnly,
				final boolean printStaticDiagnostics,
				final boolean useAnalyticBondValuation,
				final boolean printBondValuationDiagnostics,
				final boolean saveToFile,
				final ModelType modelType,
				final HedgeInstrumentSet hedgeInstrumentSet,
				final EvaluationMode evaluationMode,
				final int seed,
				final int rebalancingPerPeriod,
				final ProductToHedge productToHedge,
				final SensitivityExchangeConfig sensitivityExchange) {
			this.name = name;
			this.useDiscountCurve = useDiscountCurve;
			this.showScatterPlots = showScatterPlots;
			this.staticHedgeOnly = staticHedgeOnly;
			this.printStaticDiagnostics = printStaticDiagnostics;
			this.useAnalyticBondValuation = useAnalyticBondValuation;
			this.printBondValuationDiagnostics = printBondValuationDiagnostics;
			this.saveToFile = saveToFile;
			this.modelType = modelType;
			this.hedgeInstrumentSet = hedgeInstrumentSet;
			this.evaluationMode = evaluationMode;
			this.seed = seed;
			this.rebalancingPerPeriod = rebalancingPerPeriod;
			this.productToHedge = productToHedge;
			this.sensitivityExchange = sensitivityExchange;
		}

		public static ExperimentConfig defaultConfig() {
			return new ExperimentConfig(
					"single run",
					true, /* useDiscountCurve */
					true, /* showScatterPlots */
					false, /* staticHedgeOnly */
					true, /* printStaticDiagnostics */
					true, /* useAnalyticBondValuation */
					true, /* printBondValuationDiagnostics */
					false, /* saveToFile */
					ModelType.LMM_HW,
					HedgeInstrumentSet.DISCRETE_ROLL_OVER_AND_PAYMENT_BOND,
					EvaluationMode.PAYMENT_TIME_CASHFLOW,
					SEED, /* seed */
					1,
					ProductToHedge.CAPLET_15_SEMIANNUAL,
					null);
		}

		public ExperimentConfig withName(final String value) {
			return copy(value, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withUseDiscountCurve(final boolean value) {
			return copy(name, value, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withShowScatterPlots(final boolean value) {
			return copy(name, useDiscountCurve, value, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withSaveToFile(final boolean value) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, value, modelType, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withStaticHedgeOnly(final boolean value) {
			return copy(name, useDiscountCurve, showScatterPlots, value, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withModelType(final ModelType value) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, value, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withHedgeInstrumentSet(final HedgeInstrumentSet value) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, value,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withEvaluationMode(final EvaluationMode value) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					value, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withRebalancingPerPeriod(final int value) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, seed, value, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withSeed(final int value) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, value, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		public ExperimentConfig withProductToHedge(final ProductToHedge productToHedge) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge, sensitivityExchange);
		}

		/**
		 * Exports the raw pathwise product/hedge sensitivity system and conditioning
		 * process state. The experiment returns after the raw pathwise run, allowing
		 * the external estimator to process the completed dataset.
		 *
		 * @param directory Dataset directory shared with the external estimator.
		 * @return A configuration with raw sensitivity export enabled.
		 */
		public ExperimentConfig withSensitivityExport(final Path directory) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge,
					new SensitivityExchangeConfig(SensitivityExchangeMode.EXPORT_RAW, directory));
		}

		/**
		 * Imports externally projected product/hedge sensitivities and adds an
		 * {@code External-Projected} delta-hedge result to the analysis.
		 *
		 * @param directory Dataset directory previously produced by an export run.
		 * @return A configuration with external sensitivity import enabled.
		 */
		public ExperimentConfig withSensitivityImport(final Path directory) {
			return copy(name, useDiscountCurve, showScatterPlots, staticHedgeOnly, printStaticDiagnostics,
					useAnalyticBondValuation, printBondValuationDiagnostics, saveToFile, modelType, hedgeInstrumentSet,
					evaluationMode, seed, rebalancingPerPeriod, productToHedge,
					new SensitivityExchangeConfig(SensitivityExchangeMode.IMPORT_PROJECTED, directory));
		}

		private ExperimentConfig copy(
				final String name,
				final boolean useDiscountCurve,
				final boolean showScatterPlots,
				final boolean staticHedgeOnly,
				final boolean printStaticDiagnostics,
				final boolean useAnalyticBondValuation,
				final boolean printBondValuationDiagnostics,
				final boolean saveToFile,
				final ModelType modelType,
				final HedgeInstrumentSet hedgeInstrumentSet,
				final EvaluationMode evaluationMode,
				final int seed,
				final int rebalancingPerPeriod,
				final ProductToHedge productToHedge,
				final SensitivityExchangeConfig sensitivityExchange) {
			return new ExperimentConfig(
					name,
					useDiscountCurve,
					showScatterPlots,
					staticHedgeOnly,
					printStaticDiagnostics,
					useAnalyticBondValuation,
					printBondValuationDiagnostics,
					saveToFile,
					modelType,
					hedgeInstrumentSet,
					evaluationMode,
					seed,
					rebalancingPerPeriod,
					productToHedge,
					sensitivityExchange);
		}
	}

	private static final class HedgeRunResult {

		private final String name;
		private final RandomVariable hedgeValue;
		private final RandomVariable hedgeError;
		@SuppressWarnings("unused")
		private final ForwardSensitivityDeltaHedgedPortfolio hedge;

		private HedgeRunResult(
				final String name,
				final RandomVariable hedgeValue,
				final RandomVariable hedgeError,
				final ForwardSensitivityDeltaHedgedPortfolio hedge) {
			this.name = name;
			this.hedgeValue = hedgeValue;
			this.hedgeError = hedgeError;
			this.hedge = hedge;
		}
	}
}
