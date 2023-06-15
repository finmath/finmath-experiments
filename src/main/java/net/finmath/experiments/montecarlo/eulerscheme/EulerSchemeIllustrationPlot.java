package net.finmath.experiments.montecarlo.eulerscheme;

import java.time.LocalDateTime;
import java.util.Map;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.BrownianMotion;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.model.ProcessModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.plots.PlotProcess2D;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * Mimic the approximation performed by an Euler-Scheme.
 * 
 * We can only simulate this, because we have to time-discretize anyway.
 * What we do is we compare two time-discretization: a fine one (representing the true stochastic process) and a coarse one
 * one which we then create the Euler-Scheme approximation of the fine-time-discretization process.
 * 
 * @author fries
 *
 */
public class EulerSchemeIllustrationPlot {

	public static void main(String[] args) {

		double timeInitial = 0.0;
		int numberOfTimeSteps = 1600;
		double timeStep = 0.01;
		TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(timeInitial, numberOfTimeSteps, timeStep);

		int numberOfPaths = 100;
		int numberOfFactors = 1;
		int seed = 3141;
		BrownianMotion brownianMotion = new BrownianMotionFromMersenneRandomNumbers(timeDiscretization, numberOfFactors, numberOfPaths, seed);;

		double initialValue = 100.0;
		double riskFreeRate = 0.05;
		double volatility = 0.30;
		ProcessModel processModel = new BlackScholesModel(initialValue, riskFreeRate, volatility);

		/*
		 * The process that represents the true SDE
		 */
		MonteCarloProcess scheme = new EulerSchemeFromProcessModel(processModel, brownianMotion);
		MonteCarloAssetModel mcModel = new MonteCarloAssetModel(scheme);

		/**
		 * Consider different number of sub-steps
		 */
		for(int numberOfSubSteps : new int[] { 200, 100, 10, 1} ) {

			/*
			 * A coarse time discretization (with less time steps)
			 */
			TimeDiscretization timeDiscretizationCoarse = new TimeDiscretizationFromArray(timeInitial, numberOfTimeSteps/numberOfSubSteps, timeStep*numberOfSubSteps);
			BrownianMotion brownianMotionCoarse = new BrownianMotionCoarseTimeDiscretization(timeDiscretizationCoarse, brownianMotion);

			/* 
			 * The Euler scheme for the lognormal SDE on the coarse time discretization
			 * 	S(t_{i+1}) = S(t_{i}) + r S(t_{i}) Delta t + sigma S(t_{i}) Delta W
			 * on the coarse time-discretization, but using the Brownian increments that match the given Brownian motion.
			 */
			ProcessModel processModelBlackScholesNormalEuler = new ProcessModel() {
				@Override
				public LocalDateTime getReferenceDate() {
					return processModel.getReferenceDate();
				}

				@Override
				public int getNumberOfComponents() {
					return processModel.getNumberOfComponents();
				}

				@Override
				public RandomVariable applyStateSpaceTransform(MonteCarloProcess process, int timeIndex, int componentIndex, RandomVariable randomVariable) {
					return randomVariable;
				}

				@Override
				public RandomVariable[] getInitialState(MonteCarloProcess process) {
					try {
						return scheme.getProcessValue(0);
					} catch (CalculationException e) { throw new RuntimeException(e); }
				}

				@Override
				public RandomVariable getNumeraire(MonteCarloProcess process, double time) throws CalculationException {
					return processModel.getNumeraire(process, time);
				}

				@Override
				public RandomVariable[] getDrift(MonteCarloProcess process, int timeIndex, RandomVariable[] realizationAtTimeIndex, RandomVariable[] realizationPredictor) {
					return new RandomVariable[] { realizationAtTimeIndex[0].mult(riskFreeRate) };
				}

				@Override
				public RandomVariable[] getFactorLoading(MonteCarloProcess process, int timeIndex, int componentIndex, RandomVariable[] realizationAtTimeIndex) {
					return new RandomVariable[] { realizationAtTimeIndex[0].mult(volatility) };
				}

				@Override
				public int getNumberOfFactors() {
					return 1;
				}

				@Override
				public RandomVariable getRandomVariableForConstant(double value) {
					return processModel.getRandomVariableForConstant(value);
				}

				@Override
				public ProcessModel getCloneWithModifiedData(Map<String, Object> dataModified) throws CalculationException {
					throw new UnsupportedOperationException();
				}			
			};

			EulerSchemeFromProcessModel schemeCoars = new EulerSchemeFromProcessModel(processModelBlackScholesNormalEuler, brownianMotionCoarse);
			MonteCarloAssetModel mcModelEulerCoars = new MonteCarloAssetModel(schemeCoars);

			/*
			 * The stochastic process that is the true Euler-Scheme interpolation of the Euler-Scheme approximations.
			 * 	S(t}) = S(t_{i}) + r S(t_{i}) (t-t_{i}) + sigma S(t_{i}) (W(t)-W(t_{i}))
			 * Defined on the fine time discretization, using piecewise const. coefficients from the coarse time discretization.
			 */
			ProcessModel processModelFine = new ProcessModel() {

				@Override
				public RandomVariable[] getDrift(MonteCarloProcess process, int timeIndex, RandomVariable[] realizationAtTimeIndex, RandomVariable[] realizationPredictor) {
					double time = timeDiscretization.getTime(timeIndex);
					int timeIndexCoarse = timeDiscretizationCoarse.getTimeIndexNearestLessOrEqual(time);
					double timeCoarse = timeDiscretizationCoarse.getTime(timeIndexCoarse);

					// r S(t_i)
					RandomVariable[] drift = null;
					try {
						drift = new RandomVariable[] { mcModelEulerCoars.getAssetValue(timeCoarse, 0).mult(riskFreeRate) };
					} catch (CalculationException e) { throw new RuntimeException(e); }
					return drift;
				}

				@Override
				public RandomVariable[] getFactorLoading(MonteCarloProcess process, int timeIndex, int componentIndex, RandomVariable[] realizationAtTimeIndex) {
					double time = timeDiscretization.getTime(timeIndex);
					int timeIndexCoarse = timeDiscretizationCoarse.getTimeIndexNearestLessOrEqual(time);
					double timeCoarse = timeDiscretizationCoarse.getTime(timeIndexCoarse);

					// sigma S(t_i)
					RandomVariable[] factorLoading = null;
					try {
						factorLoading = new RandomVariable[] { mcModelEulerCoars.getAssetValue(timeCoarse, 0).mult(volatility) };
					} catch (CalculationException e) { throw new RuntimeException(e); }
					return factorLoading;
				}

				@Override
				public LocalDateTime getReferenceDate() {
					return processModel.getReferenceDate();
				}

				@Override
				public int getNumberOfComponents() {
					return processModel.getNumberOfComponents();
				}

				@Override
				public RandomVariable applyStateSpaceTransform(MonteCarloProcess process, int timeIndex, int componentIndex, RandomVariable randomVariable) {
					return randomVariable;
				}

				@Override
				public RandomVariable[] getInitialState(MonteCarloProcess process) {
					return processModel.getInitialState(process);
				}

				@Override
				public RandomVariable getNumeraire(MonteCarloProcess process, double time) throws CalculationException {
					return processModel.getNumeraire(process, time);
				}

				@Override
				public int getNumberOfFactors() {
					return 1;
				}

				@Override
				public RandomVariable getRandomVariableForConstant(double value) {
					return processModel.getRandomVariableForConstant(value);
				}

				@Override
				public ProcessModel getCloneWithModifiedData(Map<String, Object> dataModified) throws CalculationException {
					throw new UnsupportedOperationException();
				}			
			};

			EulerSchemeFromProcessModel schemeEuler = new EulerSchemeFromProcessModel(processModelFine, brownianMotion);
			MonteCarloAssetModel mcModelEuler = new MonteCarloAssetModel(schemeEuler);

			/*
			 * Plot Euler-Scheme process - the process with piecewise constant coefficients on the coarse discretization
			 */
			DoubleToRandomVariableFunction processEulerScheme = t -> { return mcModelEuler.getAssetValue(t, 0); };
			PlotProcess2D plotEuler = new PlotProcess2D(timeDiscretization, processEulerScheme, 100);
			plotEuler.setTitle("Euler-scheme process (" + (numberOfTimeSteps/numberOfSubSteps) + " steps)");
			plotEuler.setXAxisLabel("time").setYAxisLabel("value");
			plotEuler.show();

			/*
			 * Plot a piecewise linear interpolation of the Euler scheme approximation on the coarse discretization
			 */
			DoubleToRandomVariableFunction processLinear = t -> {
				TimeDiscretization td = mcModelEulerCoars.getTimeDiscretization();
				int timeIndexLow = td.getTimeIndexNearestLessOrEqual(t);
				int timeIndexHigh = timeIndexLow < td.getNumberOfTimes()-1 ? timeIndexLow+1 : timeIndexLow;
				double weight = (t-td.getTime(timeIndexLow))/(td.getTime(timeIndexHigh)-td.getTime(timeIndexLow));
				RandomVariable value = mcModelEulerCoars.getAssetValue(timeIndexHigh, 0).mult(weight).add(mcModelEulerCoars.getAssetValue(timeIndexLow, 0).mult(1-weight));
				return value;
			};
			PlotProcess2D plotProcessLinear = new PlotProcess2D(timeDiscretization, processLinear, 100);
			plotProcessLinear.setTitle("Piece-wise linear process (" + (numberOfTimeSteps/numberOfSubSteps) + " steps)");
			plotProcessLinear.setXAxisLabel("time").setYAxisLabel("value");
			plotProcessLinear.show();

			/*
			 * Plot a piecewise constant interpolation of the Euler scheme approximation on the coarse discretization
			 */
			DoubleToRandomVariableFunction processConstant = t -> {
				TimeDiscretization td = mcModelEulerCoars.getTimeDiscretization();
				int timeIndexLow = td.getTimeIndexNearestLessOrEqual(t);
				RandomVariable value = mcModelEulerCoars.getAssetValue(timeIndexLow, 0);
				return value;
			};
			PlotProcess2D plotProcessConstant = new PlotProcess2D(timeDiscretization, processConstant, 100);
			plotProcessConstant.setTitle("Piece-wise constant process (" + (numberOfTimeSteps/numberOfSubSteps) + " steps)");
			plotProcessConstant.setXAxisLabel("time").setYAxisLabel("value");
			plotProcessConstant.show();
		}

		DoubleToRandomVariableFunction process = t -> { return mcModel.getAssetValue(t, 0); };
		PlotProcess2D plot = new PlotProcess2D(timeDiscretization, process, 100);
		plot.setTitle("True (time-continuous) process (" + (numberOfTimeSteps) + " steps)");
		plot.setXAxisLabel("time").setYAxisLabel("value");
		plot.show();
	}
}
