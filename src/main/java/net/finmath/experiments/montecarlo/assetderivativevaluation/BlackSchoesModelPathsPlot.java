package net.finmath.experiments.montecarlo.assetderivativevaluation;

import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.plots.DoubleToRandomVariableFunction;
import net.finmath.plots.PlotProcess2D;
import net.finmath.time.TimeDiscretizationFromArray;

/**
 * Simple program plotting paths of a Black Scholes model.
 * 
 * @author Christian Fries
 */
public class BlackSchoesModelPathsPlot {

	public static void main(String[] args) {
		double modelInitialValue = 100.0;	// S(0)
		double modelRiskFreeRate = 0.05; 	// r
		double modelVolatility = 0.20;		// σ

		/*
		 * Monte Carlo Simulation
		 */
		
		// Create a model
		var model = new BlackScholesModel(modelInitialValue, modelRiskFreeRate, modelVolatility);

		// Create a corresponding MC process from the model
		var td = new TimeDiscretizationFromArray(0.0, 300, 0.01);
		var brownianMotion = new BrownianMotionFromMersenneRandomNumbers(td, 1, 10000, 3231);
		var process = new EulerSchemeFromProcessModel(model, brownianMotion);

		// Create a function, plotting paths t -> S(t)
		DoubleToRandomVariableFunction paths = time -> process.getProcessValue(td.getTimeIndex(time), 0 /* assetIndex: 0 is S(t) */);

		/*
		 * Plot
		 */

		// Plot 100 of paths against the given time discretization.
		var plot = new PlotProcess2D(td, paths, 100);
		plot.setTitle("Black Scholes model paths").setXAxisLabel("time").setYAxisLabel("value");
		plot.show();
	}
}
