// Run the JShell from this project using Maven JShell plugin: 	 mvn jshell:run


// EXPERIMENT 1

import net.finmath.montecarlo.*;
import net.finmath.plots.*;
import net.finmath.stochastic.*;
import net.finmath.time.*;
import static net.finmath.experiments.plots.Plots.*;

var td = new TimeDiscretizationFromArray(0.0, 100, 0.1);
var bm = new BrownianMotionLazyInit(td, 1, 1000, 3213);   // change number of paths
var x = bm.getBrownianIncrement(0,0);

var plot = createPlotOfHistogram(x, 100, 5.0);
plot.setTitle("Histogram").setXAxisLabel("value").setYAxisLabel("frequency");
plot.show();


// for func, plot the following
// for(int i=1; i<100000; i+=10) updatePlotOfHistogram(plot, (new BrownianMotionLazyInit(td, 1, i, 3213)).getBrownianIncrement(0,0), 100, 5.0)



// EXPERIMENT 2

import net.finmath.montecarlo.*;
import net.finmath.montecarlo.process.*;
import net.finmath.montecarlo.assetderivativevaluation.*;
import net.finmath.montecarlo.assetderivativevaluation.models.*;
import net.finmath.stochastic.*;
import net.finmath.time.*;
import net.finmath.plots.*;
import static net.finmath.experiments.plots.Plots.*;

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

EuropeanOption europeanOption = new EuropeanOption(maturity, strike);

RandomVariable valueOfEuropeanOption = europeanOption.getValue(0.0, simulation).average();

var value = valueOfEuropeanOption.doubleValue();


createPlotOfHistogramBehindValues(simulation.getAssetValue(maturity, 0 /* assetIndex */), europeanOption.getValue(0.0, simulation), 100, 5.0).show()



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





// EXPERIMENT 5 - Delta of Digital Option with AAD
 
valueOfEuropeanOption.getGradient().get(initialValue.getID()).getAverage();

var digitalOption = new DigitalOption(maturity, strike);
var valueOfDigitalOption = (RandomVariableDifferentiable) digitalOption.getValue(0.0, simulation).average();

var deltaMonteCarloAAD = valueOfDigitalOption.getGradient().get(initialValue.getID()).getAverage();

var deltaAnalytic = AnalyticFormulas.blackScholesDigitalOptionDelta(modelInitialValue, modelRiskFreeRate, modelVolatility, maturity, strike);


// EXPERIMENT 5 - Delta Hedge with AAD - European Option

var hedge = new DeltaHedgedPortfolioWithAAD(europeanOption);
var underlyingAtMaturity = simulation.getAssetValue(maturity, 0);
var hedgeValue = hedge.getValue(maturity, simulation);
createPlotScatter(underlyingAtMaturity, hedgeValue, 50.0, 110.0).show();



// EXPERIMENT 6 - Delta Hedge with AAD - Digital Option

var digitalOption = new DigitalOption(maturity, strike);
var hedge = new DeltaHedgedPortfolioWithAAD(digitalOption);
var underlyingAtMaturity = simulation.getAssetValue(maturity, 0);
var hedgeValue = hedge.getValue(maturity, simulation);
createPlotScatter(underlyingAtMaturity, hedgeValue, 90.0, 110.0).show();



// EXPERIMENT 7 - Delta Hedge with AAD

var underlyingAtMaturity = simulation.getAssetValue(maturity-0.3, 0);
var hedgeValue = hedge.getValue(maturity-0.3, simulation);
createPlotScatter(underlyingAtMaturity, hedgeValue, 90.0, 110.0).show()




//properties.put("barrierDiracWidth", new Double(0.1));
////properties.put("diracDeltaApproximationMethod", "REGRESSION_ON_DENSITY");

