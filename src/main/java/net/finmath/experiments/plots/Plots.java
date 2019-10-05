package net.finmath.experiments.plots;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.finmath.plots.GraphStyle;
import net.finmath.plots.Plot2D;
import net.finmath.plots.Plotable2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.plots.Point2D;
import net.finmath.stochastic.RandomVariable;

public class Plots {

	
	public static Plot2D createPlotOfHistogram(RandomVariable randomVariable, int numberOfPoints, double standardDeviations) {

		double[][] histogram = randomVariable.getHistogram(numberOfPoints, standardDeviations);
		
		List<Point2D> series = new ArrayList<Point2D>();
		for(int i=0; i<histogram[0].length; i++) {
			series.add(new Point2D(histogram[0][i],histogram[1][i]));
		}

		List<Plotable2D> plotables = Arrays.asList(
				new PlotablePoints2D("Histogram", series, new GraphStyle(new Rectangle(10, 2), null, null))
				);

		return new Plot2D(plotables);
	}

	public static Plot2D updatePlotOfHistogram(Plot2D historgram, RandomVariable randomVariable, int numberOfPoints, double standardDeviations) {

		double[][] histogram = randomVariable.getHistogram(numberOfPoints, standardDeviations);
		
		List<Point2D> series = new ArrayList<Point2D>();
		for(int i=0; i<histogram[0].length; i++) {
			series.add(new Point2D(histogram[0][i],histogram[1][i]));
		}

		List<Plotable2D> plotables = Arrays.asList(
				new PlotablePoints2D("Histogram", series, new GraphStyle(new Rectangle(10, 2), null, null))
				);
		
		historgram.update(plotables);

		return historgram;
	}
	
	public static Plot2D createPlotScatter(RandomVariable x, RandomVariable y, double xmin, double xmax) {
		
		double[] xValues = x.getRealizations();
		double[] yValues = y.getRealizations();
		
		List<Point2D> series = new ArrayList<Point2D>();
		for(int i=0; i<xValues.length; i++) {
			series.add(new Point2D(xValues[i], yValues[i]));
		}

		List<Plotable2D> plotables = Arrays.asList(
				new PlotablePoints2D("Scatter", series, new GraphStyle(new Rectangle(2, 2), null, null))
				);

		return new Plot2D(plotables);
	}
}
