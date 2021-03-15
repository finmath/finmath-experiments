package net.finmath.experiments.reproduction;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import net.finmath.plots.GraphStyle;
import net.finmath.plots.Plot2D;
import net.finmath.plots.PlotablePoints2D;
import net.finmath.plots.Point2D;
import net.finmath.plots.axis.NumberAxis;

public class BackProjectionTest {

	@Ignore
	@Test
	public void test() {
		final double[] distribution = DiscretizedLognormalDistribution.getDistribution(10, 5, 3);
		final double[] distribution2 = DiscretizedLognormalDistribution.getDistribution(10, 10, 6);

		final double[] infections = new double[200];
		for(int i = 0; i<200; i++) {
			if(i<100) {
				infections[i] = 2;
			} else {
				infections[i] = 1;
			}
		}

		final double[] observations = new double[200-(distribution.length-1)];
		final double[] infectionsExpected = new double[200-(distribution.length-1)];
		for(int i=0; i<observations.length; i++) {
			for(int j=0; j<distribution.length; j++) {
				infectionsExpected[i] += infections[i+(distribution.length-1)];
				observations[i] += infections[i+(distribution.length-1)-j] * distribution[j];
			}
		}

		final double[] infectionsProjected = (new BackProjection(distribution2, -2, 2)).getInfections(observations);

		final List<Point2D> series1 = new ArrayList<Point2D>();
		final List<Point2D> series2 = new ArrayList<Point2D>();
		for(int i=0; i<infectionsExpected.length; i++) {
			series1.add(new Point2D(i, infectionsExpected[i]));
			series2.add(new Point2D(i, infectionsProjected[i]));
		}
		final Plot2D plot = new Plot2D(List.of(
				new PlotablePoints2D("True Data", series1, new NumberAxis(), new NumberAxis(), new GraphStyle(new Rectangle(-1, -1, 2, 2))),
				new PlotablePoints2D("Estimated Data", series2, new NumberAxis(), new NumberAxis(), new GraphStyle(new Rectangle(-1, -1, 2, 2)))
				));
		plot.setTitle("Test");
		plot.setIsLegendVisible(true);
		plot.show();

		try {
			Thread.sleep(5000);
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(Arrays.toString(infections));
		System.out.println(Arrays.toString(infectionsProjected));
	}

}
