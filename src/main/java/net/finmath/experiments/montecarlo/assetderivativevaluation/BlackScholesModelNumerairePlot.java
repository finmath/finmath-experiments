package net.finmath.experiments.montecarlo.assetderivativevaluation;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.finmath.plots.GraphStyle;
import net.finmath.plots.Named;
import net.finmath.plots.Plot2D;
import net.finmath.plots.PlotableFunction2D;

public class BlackScholesModelNumerairePlot {

	public static void main(String[] args) {
		double modelInitialValue = 100.0;	// S(0)
		double modelRiskFreeRate = 0.08; 	// r
		double modelVolatility = 0.10;		// σ

		var plot = new Plot2D(List.of(new PlotableFunction2D(0.0, 5.0, 500, new Named<>("Numeraire",t -> Math.exp(modelRiskFreeRate * t)), new GraphStyle(new Rectangle(-1,-1,3,3), null, new Color(0, 0.8f, 0)))));
		plot.setTitle("t -> N(t) = exp(r • t)").setXAxisLabel("time").setYAxisLabel("value");
		plot.setYRange(0.7, 2.7);
		plot.show();

		try {
			final Path path = Files.createDirectories(Path.of("images"));
			plot.saveAsPNG(new File(path + File.separator + "BlackScholeModelNumeraire.png"), 800, 400);
		} catch (IOException e) { e.printStackTrace(); }
	}
}
