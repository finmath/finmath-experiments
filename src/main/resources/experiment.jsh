
// EXPERIMENT 1

import net.finmath.montecarlo.*;
import net.finmath.plots.*;
import net.finmath.stochastic.*;
import net.finmath.time.*;
import static net.finmath.experiments.plots.Plots.*;


var td = new TimeDiscretizationFromArray(0.0, 1000, 0.01);
var bm = new BrownianMotionLazyInit(td, 1, 1000, 3213)   // change number of paths
var x = bm.getBrownianIncrement(0,0)

var plot = createPlotOfHistogram(x, 100, 5.0)
plot.show()

for(int i=1; i<100000; i+=10) updatePlotOfHistogram(plot, (new BrownianMotionLazyInit(td, 1, i, 3213)).getBrownianIncrement(0,0), 100, 5.0)


// EXPERIMENT 2

import net.finmath.montecarlo.process.*
import net.finmath.montecarlo.assetderivativevaluation.*
import net.finmath.montecarlo.assetderivativevaluation.models.*

double modelInitialValue = 100.0;
double modelRiskFreeRate = 0.05;
double modelVolatility = 0.3;
AbstractRandomVariableFactory randomVariableFactory = null;

// Create a model
var model = new BlackScholesModel(modelInitialValue, modelRiskFreeRate, modelVolatility);

// Create a corresponding MC process
var brownianMotion = new BrownianMotionLazyInit(td, 1, 1000, 3213)
var process = new EulerSchemeFromProcessModel(brownianMotion);

// Using the process (Euler scheme), create an MC simulation of a Black-Scholes model
var simulation = new MonteCarloAssetModel(model, process);

DoubleFunction<RandomVariable> paths = (time) -> {
	try {
		return simulation.getAssetValue(time, 0 /* assetIndex */);
	} catch (Exception e) { return null; }
};

var pp = new PlotProcess2D(td, paths, 100)
pp.show()



// EXPERIMENT 3

import net.finmath.montecarlo.assetderivativevaluation.products.*

double maturity = 3.0;
double strike = 106.0;

EuropeanOption europeanOption = new EuropeanOption(maturity, strike);

RandomVariable valueOfEuropeanOption = europeanOption.getValue(0.0, simulation).average();



// EXPERIMENT 4 - inject AAD
import net.finmath.montecarlo.automaticdifferentiation.* 
import net.finmath.montecarlo.automaticdifferentiation.backward.* 

Map<String, Object> properties = new HashMap<>();
properties.put("barrierDiracWidth", new Double(0.5));
//properties.put("diracDeltaApproximationMethod", "REGRESSION_ON_DENSITY");
properties.put("isGradientRetainsLeafNodesOnly", new Boolean(false));

AbstractRandomVariableFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory();
AbstractRandomVariableFactory randomVariableFactory = new RandomVariableDifferentiableAADFactory(new RandomVariableFactory(), properties);

// Create a model
var model = new BlackScholesModel(modelInitialValue, modelRiskFreeRate, modelVolatility, randomVariableFactory);

// Create a corresponding MC process
var td = new TimeDiscretizationFromArray(0.0, 300, 0.01);
var brownianMotion = new BrownianMotionLazyInit(td, 1, 10000, 3213)
var process = new EulerSchemeFromProcessModel(brownianMotion, EulerSchemeFromProcessModel.Scheme.EULER_FUNCTIONAL);
//var process = new EulerSchemeFromProcessModel(brownianMotion);

// Using the process (Euler scheme), create an MC simulation of a Black-Scholes model
var simulation = new MonteCarloAssetModel(model, process);


RandomVariableDifferentiable valueOfEuropeanOption = (RandomVariableDifferentiable)europeanOption.getValue(0.0, simulation).average();

RandomVariableDifferentiable initialValue = (RandomVariableDifferentiable)((BlackScholesModel)simulation.getModel()).getInitialValue()[0];

valueOfEuropeanOption.getGradient().get(initialValue.getID()).getAverage()


// EXPERIMENT 4.5 - Delta with AAD

import net.finmath.functions.AnalyticFormulas;
 
valueOfEuropeanOption.getGradient().get(initialValue.getID()).getAverage()

DigitalOption digitalOption = new DigitalOption(maturity, strike);
RandomVariableDifferentiable valueOfDigitalOption = (RandomVariableDifferentiable)digitalOption.getValue(0.0, simulation).average();
valueOfDigitalOption.getGradient().get(initialValue.getID()).getAverage()

AnalyticFormulas.blackScholesDigitalOptionDelta(
			modelInitialValue,
			modelRiskFreeRate,
			modelVolatility,
			maturity,
			strike)


// EXPERIMENT 5 - Delta Hedge with AAD

DeltaHedgedPortfolioWithAAD hedge = new DeltaHedgedPortfolioWithAAD(europeanOption);
var underlyingAtMaturity = simulation.getAssetValue(maturity, 0);
RandomVariable hedgeValue = hedge.getValue(maturity, simulation);
createPlotScatter(underlyingAtMaturity, hedgeValue, 90.0, 110.0).show()



// EXPERIMENT 6 - Delta Hedge with AAD

DigitalOption digitalOption = new DigitalOption(maturity, strike);
DeltaHedgedPortfolioWithAAD hedge = new DeltaHedgedPortfolioWithAAD(digitalOption);
var underlyingAtMaturity = simulation.getAssetValue(maturity-0.5, 0);
RandomVariable hedgeValue = hedge.getValue(maturity-0.5, simulation);
createPlotScatter(underlyingAtMaturity, hedgeValue, 90.0, 110.0).show()

