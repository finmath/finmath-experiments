/* 
 * Run the experiments below from jshell lauching the shell from this project via Maven.
 * 
 * 		mvn compile jshell:run
 * 
 * The experiments will generate plots in a separate window. Some experiments require
 * running the previous one, so run them in the numbered order.
 */


// EXPERIMENT 1

import net.finmath.montecarlo.*;
import net.finmath.plots.*;
import net.finmath.time.*;

var td = new TimeDiscretizationFromArray(0.0, 100, 0.1);
var bm = new BrownianMotionLazyInit(td, 1, 1000, 3213);   // change number of paths
var x = bm.getBrownianIncrement(0,0);

var plot = Plots.createPlotOfHistogram(x, 100, 5.0);
plot.setTitle("Histogram").setXAxisLabel("value").setYAxisLabel("frequency");
plot.show();


// for fun, plot the following
/*
for(int i=2; i<100; i+=1) {
	int numberOfPaths = i*i*Math.max(i/10,1);
	Plots.updatePlotOfHistogram(plot, (new BrownianMotionLazyInit(td, 1, numberOfPaths, 3213)).getBrownianIncrement(0,0), 100, 5.0);
	System.out.println(numberOfPaths);
	Thread.sleep(100);
}
*/



// EXPERIMENT 2

import net.finmath.montecarlo.*;
import net.finmath.montecarlo.process.*;
import net.finmath.montecarlo.assetderivativevaluation.*;
import net.finmath.montecarlo.assetderivativevaluation.models.*;
import net.finmath.stochastic.*;
import net.finmath.time.*;
import net.finmath.plots.*;

double modelInitialValue = 100.0;
double modelRiskFreeRate = 0.05;
double modelVolatility = 0.20;

// Create a model
var model = new BlackScholesModel(modelInitialValue, modelRiskFreeRate, modelVolatility);

// Create a corresponding MC process
var td = new TimeDiscretizationFromArray(0.0, 300, 0.01);
var brownianMotion = new BrownianMotionLazyInit(td, 1, 10000, 3231);
var process = new EulerSchemeFromProcessModel(brownianMotion);

// Using the process (Euler scheme), create an MC simulation of a Black-Scholes model
var simulation = new MonteCarloAssetModel(model, process);

// Create a function, plotting every paths.
DoubleFunction<RandomVariable> paths = (time) -> {
	try {
		return simulation.getAssetValue(time, 0 /* assetIndex */);
	} catch (Exception e) { return null; }
};

// Plot 100 of paths against the given time discretization.
var plot = (new PlotProcess2D(td, paths, 100));
plot.setTitle("Black Scholes model paths").setXAxisLabel("time").setYAxisLabel("value");
plot.show();



// EXPERIMENT 3 (requires run of experiment 2)

import net.finmath.functions.AnalyticFormulas;
import net.finmath.montecarlo.assetderivativevaluation.products.*;

double maturity = 3.0;
double strike = 106.0;

var europeanOption = new EuropeanOption(maturity, strike);

var valueOfEuropeanOption = europeanOption.getValue(0.0, simulation).average();

var value = valueOfEuropeanOption.doubleValue();

var plot = Plots.createPlotOfHistogramBehindValues(simulation.getAssetValue(maturity, 0 /* assetIndex */), europeanOption.getValue(0.0, simulation), 100, 5.0);
plot.setTitle("European option value and distribution of underlying").setXAxisLabel("underlying").setYAxisLabel("value");
plot.show();


// EXPERIMENT 4 - Dependency Injection of AAD - Delta of European Option

import net.finmath.montecarlo.*;
import net.finmath.montecarlo.automaticdifferentiation.*;
import net.finmath.montecarlo.automaticdifferentiation.backward.*;

// Use the AAD factory to create AAD enabled random variables
Map<String, Object> properties = Map.of("isGradientRetainsLeafNodesOnly", false);
RandomVariableFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory(properties);

// Create a model
var model = new BlackScholesModel(modelInitialValue, modelRiskFreeRate, modelVolatility, randomVariableFactory);

// Create a corresponding MC process
var td = new TimeDiscretizationFromArray(0.0, 300, 0.01);
var brownianMotion = new BrownianMotionLazyInit(td, 1, 10000, 3231);
var process = new EulerSchemeFromProcessModel(brownianMotion);

// Using the process (Euler scheme), create an MC simulation of a Black-Scholes model
var simulation = new MonteCarloAssetModel(model, process);

var valueOfEuropeanOption = (RandomVariableDifferentiable) europeanOption.getValue(0.0, simulation).average();

valueOfEuropeanOption.doubleValue();


var initialValue = (RandomVariableDifferentiable) model.getInitialValue()[0];

var delta = valueOfEuropeanOption.getGradient().get(initialValue.getID()).average();

var S0 = initialValue.doubleValue();

var deltaValue = delta.doubleValue();



// EXPERIMENT 5 - Dependency Injection of OpenCL


import net.finmath.montecarlo.opencl.*;

// Use the Cuda factory to create GPU enabled random variables
RandomVariableFactory randomVariableFactory = new RandomVariableOpenCLFactory();
//RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory();

// Create a model
var model = new BlackScholesModel(modelInitialValue, modelRiskFreeRate, modelVolatility, randomVariableFactory);

// Create a corresponding MC process
var td = new TimeDiscretizationFromArray(0.0, 30, 0.1);
var brownianMotion = new BrownianMotionLazyInit(td, 1, 5000000, 3231);
var process = new EulerSchemeFromProcessModel(brownianMotion);

// Using the process (Euler scheme), create an MC simulation of a Black-Scholes model
var simulation = new MonteCarloAssetModel(model, process);

var valueOfEuropeanOption = europeanOption.getValue(0.0, simulation).average();

valueOfEuropeanOption.doubleValue();




// EXPERIMENT 6 - Delta of Digital Option with AAD
 
valueOfEuropeanOption.getGradient().get(initialValue.getID()).getAverage();

var digitalOption = new DigitalOption(maturity, strike);
var valueOfDigitalOption = (RandomVariableDifferentiable) digitalOption.getValue(0.0, simulation).average();

var deltaMonteCarloAAD = valueOfDigitalOption.getGradient().get(initialValue.getID()).getAverage();

var deltaAnalytic = AnalyticFormulas.blackScholesDigitalOptionDelta(modelInitialValue, modelRiskFreeRate, modelVolatility, maturity, strike);


// EXPERIMENT 7 - Delta Hedge with AAD - European Option

var hedge = new DeltaHedgedPortfolioWithAAD(europeanOption);
var underlyingAtMaturity = simulation.getAssetValue(maturity, 0);
var hedgeValue = hedge.getValue(maturity, simulation);
var plot = Plots.createPlotScatter(underlyingAtMaturity, hedgeValue, 50.0, 110.0);
plot.setTitle("Hedge Portfolio").setXAxisLabel("underlying").setYAxisLabel("portfolio value");
plot.show();


// EXPERIMENT 8 - Delta Hedge with AAD - Digital Option

var digitalOption = new DigitalOption(maturity, strike);
var hedge = new DeltaHedgedPortfolioWithAAD(digitalOption);
var underlyingAtMaturity = simulation.getAssetValue(maturity, 0);
var hedgeValue = hedge.getValue(maturity, simulation);
var plot = Plots.createPlotScatter(underlyingAtMaturity, hedgeValue, 90.0, 110.0);
plot.setTitle("Hedge Portfolio").setXAxisLabel("underlying").setYAxisLabel("portfolio value");
plot.show();


// EXPERIMENT 9 - Delta Hedge with AAD

var underlyingAtMaturity = simulation.getAssetValue(maturity-0.3, 0);
var hedgeValue = hedge.getValue(maturity-0.3, simulation);
var plot = Plots.createPlotScatter(underlyingAtMaturity, hedgeValue, 90.0, 110.0);
plot.setTitle("Hedge Portfolio").setXAxisLabel("underlying").setYAxisLabel("portfolio value");
plot.show();

//properties.put("barrierDiracWidth", new Double(0.1));
////properties.put("diracDeltaApproximationMethod", "REGRESSION_ON_DENSITY");

