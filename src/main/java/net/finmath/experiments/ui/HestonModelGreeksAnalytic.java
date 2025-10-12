package net.finmath.experiments.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

import net.finmath.experiments.ui.parameter.BooleanParameter;
import net.finmath.experiments.ui.parameter.DoubleParameter;
import net.finmath.functions.AnalyticFormulas;
import net.finmath.functions.HestonModel;
import net.finmath.plots.GraphStyle;
import net.finmath.plots.Named;
import net.finmath.plots.Plot2D;
import net.finmath.plots.Plotable2D;
import net.finmath.plots.PlotableFunction2D;

public class HestonModelGreeksAnalytic extends ExperimentUI {

	private final DecimalFormat numberDigit2 = new DecimalFormat("#.00");
	private final DecimalFormat numberPercent1 = new DecimalFormat("#.0%");

	Plot2D plotDelta = null;
	Plot2D plotGamma = null;
	Plot2D plotVega = null;

	final static String RISK_FREE_RATE = "Risk Free Rate";
	final static String DIVIDEND_YIELD = "Dividend Yield";
	final static String KAPPA = "𝜅 (kappa)";
	final static String THETA = "𝜃 (theta)";
	final static String SIGMA = "𝜎 (sigma)";
	final static String V0 = "v₀";
	final static String RHO = "𝜌 (rho)";
	final static String OPTION_MATURIY = "Option Maturity";
	final static String OPTION_STRIKE = "Option Strike";

	public HestonModelGreeksAnalytic() {
		super(List.of(
				new DoubleParameter(RISK_FREE_RATE, 0.03, 0.00, 0.10),
				new DoubleParameter(DIVIDEND_YIELD, 0.03, 0.00, 0.10),
				new DoubleParameter(KAPPA, 0.8455, 0.001, 2.0),
				new DoubleParameter(THETA, 0.0818, 0.0001, 2.0),
				new DoubleParameter(SIGMA, 0.4639, 0.0, 2.0),
				new DoubleParameter(V0, 0.0423, 0.0001, 2.0),
				new DoubleParameter(RHO, -0.4, -1.0, 1.0),
				new DoubleParameter(OPTION_MATURIY, 0.2, 0.01, 5.0),
				new DoubleParameter(OPTION_STRIKE, 50, 90, 250),
				new BooleanParameter("Show Delta", true),
				new BooleanParameter("Show Gamma", false),
				new BooleanParameter("Show Vega", false)
				));
	}

	public String getTitle() { return "Heston Model - Greeks (Analytic)"; }

	public void runCalculation(BooleanSupplier isCancelled, DoubleConsumer progress) {
		Map<String, Object> currentParameterSet = getExperimentParameters().stream().collect(Collectors.toMap(p -> p.getBindableValue().getName(), p -> p.getBindableValue().getValue()));

		System.out.println("Calculation with Parameters: " + currentParameterSet);

		final double riskFreeRate = (Double)currentParameterSet.get(RISK_FREE_RATE);
		final double dividendYield = (Double)currentParameterSet.get(DIVIDEND_YIELD);
		final double kappa = (Double)currentParameterSet.get(KAPPA);
		final double theta =(Double)currentParameterSet.get(THETA);
		final double sigma = (Double)currentParameterSet.get(SIGMA);
		final double v0 = (Double)currentParameterSet.get(V0);
		final double rho = (Double)currentParameterSet.get(RHO);
		final double optionMaturity = (Double)currentParameterSet.get(OPTION_MATURIY);
		final double optionStrike = (Double)currentParameterSet.get(OPTION_STRIKE);

		String titleSpec = "r="+numberPercent1.format(riskFreeRate) + ", q="+numberPercent1.format(dividendYield) +
				", 𝜅=" + numberDigit2.format(kappa) +
				", 𝜃=" + numberDigit2.format(theta) +
				", 𝜎=" + numberDigit2.format(sigma) +
				", v₀=" + numberDigit2.format(v0) +
				", 𝜌=" + numberDigit2.format(rho) +
				", T=" + numberDigit2.format(optionMaturity) +
				", K=" + numberDigit2.format(optionStrike);

		DoubleUnaryOperator deltaFun = (stock) -> HestonModel.hestonOptionDelta(
				stock,
				riskFreeRate,
				dividendYield,
				kappa, 
				theta, 
				sigma, 
				v0, 
				rho,
				optionMaturity,
				optionStrike);

		DoubleUnaryOperator gammaFun = (stock) -> HestonModel.hestonOptionGamma(
				stock,
				riskFreeRate,
				dividendYield,
				kappa, 
				theta, 
				sigma, 
				v0, 
				rho,
				optionMaturity,
				optionStrike);

		DoubleUnaryOperator vegaFun = (stock) -> HestonModel.hestonOptionVega1(
				stock,
				riskFreeRate,
				dividendYield,
				kappa, 
				theta, 
				sigma, 
				v0, 
				rho,
				optionMaturity,
				optionStrike);

		DoubleUnaryOperator deltaFunBS = (stock) -> { return AnalyticFormulas.blackScholesOptionDelta(
				stock,
				riskFreeRate-dividendYield,
				Math.sqrt(v0),
				optionMaturity,
				optionStrike) * Math.exp(-dividendYield * optionMaturity);
		};

		DoubleUnaryOperator gammaFunBS = (stock) -> { return AnalyticFormulas.blackScholesOptionGamma(
				stock,
				riskFreeRate-dividendYield,
				Math.sqrt(v0),
				optionMaturity,
				optionStrike) * Math.exp(-dividendYield * optionMaturity);
		};
		
		DoubleUnaryOperator vegaFunBS = (stock) -> { return AnalyticFormulas.blackScholesOptionVega(
				stock,
				riskFreeRate-dividendYield,
				Math.sqrt(v0),
				optionMaturity,
				optionStrike) * Math.exp(-dividendYield * optionMaturity);
		};

		synchronized(this) {
			if(!Thread.currentThread().isInterrupted() && !isCancelled.getAsBoolean()) {
				if((Boolean)currentParameterSet.get("Show Delta"))
				plotDelta = plot(plotDelta, "Delta", titleSpec, deltaFunBS, deltaFun);
				else {
					if(plotDelta != null) {
						plotDelta.close();
						plotDelta = null;
					}
				}
			}
			if(!Thread.currentThread().isInterrupted() && !isCancelled.getAsBoolean()) {
				if((Boolean)currentParameterSet.get("Show Gamma"))
				plotGamma = plot(plotGamma, "Gamma", titleSpec, gammaFunBS, gammaFun);
				else {
					if(plotGamma != null) {
						plotGamma.close();
						plotGamma = null;
					}

				}
			}
			if(!Thread.currentThread().isInterrupted() && !isCancelled.getAsBoolean()) {
				if((Boolean)currentParameterSet.get("Show Vega"))
				plotVega = plot(plotVega, "Vega", titleSpec, vegaFunBS, vegaFun);
				else {
					if(plotVega != null) {
						plotVega.close();
						plotVega = null;
					}
				}
			}
		}
		
		progress.accept(1.0);
	}

	private Plot2D plot(Plot2D plot, String nameOfGreek, String titleSpec, DoubleUnaryOperator greekBlackScholes, DoubleUnaryOperator greekHeston) {
		List<Plotable2D> plotables = List.of(
				new PlotableFunction2D(0, 300, 200, new Named<DoubleUnaryOperator>("Heston", greekHeston), new GraphStyle(null, new BasicStroke(), Color.blue)),
				new PlotableFunction2D(0, 300, 200, new Named<DoubleUnaryOperator>("Black Scholes", greekBlackScholes), new GraphStyle(null, new BasicStroke(), Color.RED))
				);

		if(plot == null) {
			try {
				plot = new Plot2D(plotables);
				plot.setTitle("Heston vs Black Scholes Option " + nameOfGreek + "\n(" + titleSpec + ")").setXAxisLabel("Spot").setYAxisLabel(nameOfGreek);
				plot.setIsLegendVisible(true);
				//					plot.setYRange(-0.02, 0.10);
				plot.show();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		else {
			plot.setTitle("Heston vs Black Scholes Option " + nameOfGreek + "\n(" + titleSpec + ")").setXAxisLabel("Spot").setYAxisLabel(nameOfGreek);
			plot.update(plotables);
		}

		return plot;
	}

	@Override
	protected void onClose() {
		synchronized (this) {
			super.onClose();
			System.out.println("Closing plot");
			if(plotDelta != null) {
				plotDelta.close();
				plotDelta = null;
			}
			if(plotGamma != null) {
				plotGamma.close();
				plotGamma = null;
			}
			if(plotVega != null) {
				plotVega.close();
				plotVega = null;
			}
		}
	}
}
