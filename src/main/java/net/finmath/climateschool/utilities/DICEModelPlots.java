package net.finmath.climateschool.utilities;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import net.finmath.climate.models.CarbonConcentration;
import net.finmath.climate.models.ClimateModel;
import net.finmath.climate.models.Temperature;
import net.finmath.plots.GraphStyle;
import net.finmath.plots.Plot2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.plots.Plots;
import net.finmath.plots.Point2D;
import net.finmath.stochastic.RandomVariable;
import net.finmath.stochastic.Scalar;
import net.finmath.time.TimeDiscretization;

public class DICEModelPlots {

	Plot2D plotTemperature = null;
	Plot2D plotCarbon = null;
	Plot2D plotEmission = null;
	Plot2D plotOutput = null;
	Plot2D plotAbatement = null;

	Plot2D plotCostDiscounted = null;
	Plot2D plotCostPerGDP = null;

	public void plot(ClimateModel climateModel, String spec) {
		/*
		 * Plots
		 */

		TimeDiscretization timeDiscretization = climateModel.getTimeDiscretization();

		if(plotTemperature == null) {
			plotTemperature = Plots
					.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getTemperature()).mapToDouble(Temperature::getExpectedTemperatureOfAtmosphere).toArray(), 0, 300, 3)
					.setTitle("Temperature (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("Temperature [°C]");
			plotTemperature.show();
		}
		else {
			Plots
			.updateScatter(plotTemperature, timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getTemperature()).mapToDouble(Temperature::getExpectedTemperatureOfAtmosphere).toArray(), 0, 300, 3)
			.setTitle("Temperature (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("Temperature [°C]");
		}

		if(plotCarbon == null) {
			plotCarbon = Plots
					.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getCarbonConcentration()).mapToDouble(CarbonConcentration::getExpectedCarbonConcentrationInAtmosphere).toArray(), 0, 300, 3)
					.setTitle("Carbon Concentration (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("Carbon concentration [GtC]");
			plotCarbon.show();
		}
		else {
			Plots
			.updateScatter(plotCarbon, timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getCarbonConcentration()).mapToDouble(CarbonConcentration::getExpectedCarbonConcentrationInAtmosphere).toArray(), 0, 300, 3)
			.setTitle("Carbon Concentration (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("Carbon concentration [GtC]");

		}

		if(plotEmission == null) {
			plotEmission = Plots
					.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getEmission()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
					.setTitle("Emission (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("Emission [GtCO2/yr]");
			plotEmission.show();
		}
		else {
			Plots
			.updateScatter(plotEmission, timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getEmission()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
			.setTitle("Emission (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("Emission [GtCO2/yr]");

		}

		if(plotOutput == null) {
			plotOutput = Plots
					.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getGDP()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
					.setTitle("GDP (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("GDP [Tr$2005]");			
			plotOutput.show();
		}
		else {
			Plots
			.updateScatter(plotOutput, timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getGDP()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
			.setTitle("GDP (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("GDP [Tr$2005]");
		}

		if(plotAbatement == null) {
			plotAbatement = Plots
					.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getAbatement()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
					.setTitle("Abatement (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("Abatement \u03bc");
			plotAbatement.show();
		}
		else {
			Plots
			.updateScatter(plotAbatement, timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getAbatement()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
			.setTitle("Abatement (" + spec + ")").setXAxisLabel("time (years)").setYAxisLabel("Abatement \u03bc");
		}
	}

	public void plotCost(ClimateModel climateModel, double discountRate, String paramSpec) {
		System.out.println("Generating plots for " + climateModel);

		final double timeHorizonInPlot = 400;

		final TimeDiscretization timeDiscretization = climateModel.getTimeDiscretization();

		final RandomVariable[] abatement = climateModel.getAbatement();
		final RandomVariable[] damage = climateModel.getDamage();
		final RandomVariable[] damageCosts = climateModel.getDamageCosts();
		final RandomVariable[] abatementCosts = climateModel.getAbatementCosts();
		final RandomVariable[] gdp = climateModel.getGDP();
		final RandomVariable[] emission = climateModel.getEmission();

		final List<Point2D> costDamage			= new ArrayList<Point2D>();
		final List<Point2D> costDamageDiscounted	= new ArrayList<Point2D>();
		final List<Point2D> costDamagePerGDP	= new ArrayList<Point2D>();

		final List<Point2D> costAbatement			= new ArrayList<Point2D>();
		final List<Point2D> costAbatementDiscounted	= new ArrayList<Point2D>();
		final List<Point2D> costAbatementPerGDP	= new ArrayList<Point2D>();

		final List<Point2D> costTotal			= new ArrayList<Point2D>();
		final List<Point2D> costTotalDiscounted	= new ArrayList<Point2D>();
		final List<Point2D> costTotalPerGDP	= new ArrayList<Point2D>();

		final List<Point2D> costAveraged			= new ArrayList<Point2D>();
		final List<Point2D> costAveragedDiscounted	= new ArrayList<Point2D>();
		final List<Point2D> costAveragedPerGDP	= new ArrayList<Point2D>();

		for(int i=0; i<damageCosts.length-1; i+=1) {
			final double time = timeDiscretization.getTime(i);
			final RandomVariable numeraire = Scalar.of(Math.exp(discountRate * time));

			costDamage.add(new Point2D(timeDiscretization.getTime(i),damageCosts[i].getAverage()));
			costDamageDiscounted.add(new Point2D(timeDiscretization.getTime(i),damageCosts[i].div(numeraire).getAverage()));
			costDamagePerGDP.add(new Point2D(timeDiscretization.getTime(i),damageCosts[i].div(gdp[i]).getAverage()));

			costAbatement.add(new Point2D(timeDiscretization.getTime(i),abatementCosts[i].getAverage()));
			costAbatementDiscounted.add(new Point2D(timeDiscretization.getTime(i),abatementCosts[i].div(numeraire).getAverage()));
			costAbatementPerGDP.add(new Point2D(timeDiscretization.getTime(i),abatementCosts[i].div(gdp[i]).getAverage()));

			costTotal.add(new Point2D(timeDiscretization.getTime(i), damageCosts[i].add(abatementCosts[i]).getAverage()));
			costTotalDiscounted.add(new Point2D(timeDiscretization.getTime(i),damageCosts[i].add(abatementCosts[i]).div(numeraire).getAverage()));
			costTotalPerGDP.add(new Point2D(timeDiscretization.getTime(i),damageCosts[i].add(abatementCosts[i]).div(gdp[i]).getAverage()));

			costAveragedDiscounted.add(new Point2D(timeDiscretization.getTime(i),IntStream.range(i, Math.min(i+100, damageCosts.length-1)).mapToDouble(j -> damageCosts[j].add(abatementCosts[j]).div(climateModel.getNumeraire(timeDiscretization.getTime(j))).div(100.0).getAverage()).sum()));
			costAveragedPerGDP.add(new Point2D(timeDiscretization.getTime(i),IntStream.range(i, Math.min(i+100, damageCosts.length-1)).mapToDouble(j -> damageCosts[j].add(abatementCosts[j]).div(climateModel.getNumeraire(timeDiscretization.getTime(j))).getAverage()).sum() / IntStream.range(i, Math.min(i+100, damageCosts.length)).mapToDouble(j -> gdp[j].div(climateModel.getNumeraire(timeDiscretization.getTime(j))).getAverage()).sum()));
		}

		if(plotCostDiscounted == null) {
			plotCostDiscounted = new Plot2D(
					List.of(
							new PlotablePoints2D("damage", costDamageDiscounted, new GraphStyle(new Rectangle(2, 2), new BasicStroke(), Color.RED)),
							new PlotablePoints2D("abatement", costAbatementDiscounted, new GraphStyle(new Rectangle(2, 2), new BasicStroke(), Color.GREEN)),
							new PlotablePoints2D("total", costTotalDiscounted, new GraphStyle(new Rectangle(3, 3), new BasicStroke(), Color.BLUE))))
					//							new PlotablePoints2D("total averaged", costAveragedDiscounted, new GraphStyle(new Rectangle(3, 3), new BasicStroke(), Color.BLACK))))
					.setXAxisLabel("time t")
					.setYAxisLabel("cost (discounted)")
					.setXRange(0, timeHorizonInPlot)
					.setIsLegendVisible(true)
					.setTitle("Cost Discounted");
			plotCostDiscounted.show();
		}
		else {
			plotCostDiscounted.update(
					List.of(
							new PlotablePoints2D("damage", costDamageDiscounted, new GraphStyle(new Rectangle(2, 2), new BasicStroke(), Color.RED)),
							new PlotablePoints2D("abatement", costAbatementDiscounted, new GraphStyle(new Rectangle(2, 2), new BasicStroke(), Color.GREEN)),
							new PlotablePoints2D("total", costTotalDiscounted, new GraphStyle(new Rectangle(3, 3), new BasicStroke(), Color.BLUE))));
			//							new PlotablePoints2D("total averaged", costAveragedDiscounted, new GraphStyle(new Rectangle(3, 3), new BasicStroke(), Color.BLACK))));
		}

		if(plotCostPerGDP == null) {
			plotCostPerGDP = new Plot2D(
					List.of(
							new PlotablePoints2D("damage", costDamagePerGDP, new GraphStyle(new Rectangle(2, 2), new BasicStroke(), Color.RED)),
							new PlotablePoints2D("abatement", costAbatementPerGDP, new GraphStyle(new Rectangle(2, 2), new BasicStroke(), Color.GREEN)),
							new PlotablePoints2D("total", costTotalPerGDP, new GraphStyle(new Rectangle(3, 3), new BasicStroke(), Color.BLUE)),
							new PlotablePoints2D("total averaged", costAveragedPerGDP, new GraphStyle(new Rectangle(3, 3), new BasicStroke(), Color.BLACK))))
					.setXAxisLabel("time t")
					.setYAxisLabel("cost/gdp")
					.setXRange(0, timeHorizonInPlot)
					.setYAxisNumberFormat(new DecimalFormat("0.0%"))
					.setIsLegendVisible(true)
					.setTitle("Cost per GDP");
			plotCostPerGDP.show();
		}
		else {
			plotCostPerGDP.update(
					List.of(
							new PlotablePoints2D("damage", costDamagePerGDP, new GraphStyle(new Rectangle(2, 2), new BasicStroke(), Color.RED)),
							new PlotablePoints2D("abatement", costAbatementPerGDP, new GraphStyle(new Rectangle(2, 2), new BasicStroke(), Color.GREEN)),
							new PlotablePoints2D("total", costTotalPerGDP, new GraphStyle(new Rectangle(3, 3), new BasicStroke(), Color.BLUE)),
							new PlotablePoints2D("total averaged", costAveragedPerGDP, new GraphStyle(new Rectangle(3, 3), new BasicStroke(), Color.BLACK))));
		}

		if(plotAbatement == null) {
			plotAbatement = Plots
					.createScatter(timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getAbatement()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
					.setTitle("Abatement (" + paramSpec + ")").setXAxisLabel("time (years)").setYAxisLabel("Abatement \u03bc");
			plotAbatement.show();
		}
		else {
			Plots
			.updateScatter(plotAbatement, timeDiscretization.getAsDoubleArray(), Arrays.stream(climateModel.getAbatement()).mapToDouble(RandomVariable::getAverage).toArray(), 0, 300, 3)
			.setTitle("Abatement (" + paramSpec + ")").setXAxisLabel("time (years)").setYAxisLabel("Abatement \u03bc");
		}
	}

	public void closeCost() {
		if(plotCostDiscounted != null) plotCostDiscounted.close();
		if(plotCostPerGDP != null) plotCostPerGDP.close();
	}
}
