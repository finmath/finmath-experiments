package net.finmath.experiments.reproduction;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.finmath.plots.GraphStyle;
import net.finmath.plots.Plot2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.plots.Point2D;
import net.finmath.plots.axis.NumberAxis;

public class BackProjectionAnalysis {

	public static void main(String[] args) throws IOException {
		createPlot("backprojection-5-4-5-4-0-0", 5.0, 4.0, 5.0, 4.0, 0, 0);
		createPlot("backprojection-5-0-8-0-0-0", 5.0, 0.1, 8.0, 0.1, 0, 0);

		createPlot("backprojection-5-4-8-8-0-0", 5.0, 4.0, 8.0, 8.0, 0, 0);
		createPlot("backprojection-5-4-8-8-5-5", 5.0, 4.0, 8.0, 8.0, -5, 5);

		createPlot("backprojection-5-4-5-4-5-5", 5.0, 4.0, 5.0, 4.0, -5, 5);
		createPlot("backprojection-5-4-8-4-5-5", 5.0, 4.0, 8.0, 4.0, -5, 5);

		createPlot("backprojection-5-10-5-4-1-1", 5.0, 10.0, 5.0, 4.0, -1, 1);
		createPlot("backprojection-5-10-5-4-3-3", 5.0, 10.0, 5.0, 4.0, -3, 3);

		createPlot("backprojection-5-4-5-4-5-5", 5.0, 4.0, 5.0, 4.0, -5, 5);
		createPlot("backprojection-5-4-5-4-0-5", 5.0, 4.0, 5.0, 4.0, 0, 5);
		createPlot("backprojection-5-4-5-4-5-0", 5.0, 4.0, 5.0, 4.0, -5, 0);

		System.out.println("Done.");
	}

	private static void createPlot(
			String filename,
			double distrTrueMean, double distrTrueStdev,
			double distrEstmMean, double distrEstmStdev,
			int smoothingIntervalStart,
			int smoothingIntervalEnd

			) throws IOException {

		final double[] distributionTrue = DiscretizedLognormalDistribution.getDistribution(50, distrTrueMean, distrTrueStdev);
		final double[] distributionEstm = DiscretizedLognormalDistribution.getDistribution(50, distrEstmMean, distrEstmStdev);

		final double[] infections = new double[200];
		for(int i = 0; i<200; i++) {
			if(i<100) {
				infections[i] = 2;
			} else {
				infections[i] = 1;
			}
		}

		final double[] observations = new double[200+(distributionTrue.length-1)];
		final double[] infectionsExpected = new double[200];
		System.arraycopy(infections, 0, infectionsExpected, 0, infections.length);
		for(int i=0; i<infections.length; i++) {
			for(int j=0; j<distributionTrue.length; j++) {
				observations[i+j] += infections[i] * distributionTrue[j];
			}
		}

		final double[] infectionsProjected = (new BackProjection(distributionEstm, smoothingIntervalStart, smoothingIntervalEnd)).getInfections(observations);

		final List<Point2D> series1 = new ArrayList<Point2D>();
		final List<Point2D> series2 = new ArrayList<Point2D>();
		for(int i=0; i<infectionsExpected.length; i++) {
			series1.add(new Point2D(i, infectionsExpected[i]));
			series2.add(new Point2D(i, infectionsProjected[i]));
		}
		final NumberAxis domain = new NumberAxis("Day", 80.0, 120.0);
		final NumberAxis range = new NumberAxis("Value", 0.0, 4.0);
		final Plot2D plot = new Plot2D(List.of(
				new PlotablePoints2D("Estimated Data", series2, domain, range, new GraphStyle(new Rectangle(-2, -2, 3, 3))),
				new PlotablePoints2D("True Data", series1, domain, range, new GraphStyle(new Rectangle(-2, -2, 5, 5)))
				));

		final String description = "\u03BC\u1D63 = " + distrTrueMean + ",  " +
				"\u03C3\u1D63 = " + distrTrueStdev + ",  " +
				"\u03BC\u2091 = " + distrEstmMean + ",  " +
				"\u03C3\u2091 = " + distrEstmStdev + ",  " +
				"k\u2081 = " + smoothingIntervalStart + ",  " +
				"k\u2082 = " + smoothingIntervalEnd;

		plot.setTitle("Estimation via Backprojection (Inverse Convolution)\n"+description);
		plot.setIsLegendVisible(true);
		plot.show();
		//		plot.saveAsPDF(new File(filename + ".pdf"), 800, 450);
		//		plot.saveAsPNG(new File(filename + ".png"), 800, 450);
		plot.saveAsSVG(new File(filename + ".svg"), 800, 450);

		System.out.println(Arrays.toString(infections));
		System.out.println(Arrays.toString(infectionsProjected));
	}
}
