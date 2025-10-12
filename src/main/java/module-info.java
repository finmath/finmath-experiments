
module net.finmath.experiments {
	exports net.finmath.experiments.hedgesimulator;
	exports net.finmath.experiments.factorreduction;
	exports net.finmath.experiments.shortrate;
	exports net.finmath.experiments.montecarlo.assetderivativevaluation;
	exports net.finmath.experiments.montecarlo.automaticdifferentiation;
	exports net.finmath.experiments.montecarlo.interestrates;
	exports net.finmath.experiments.reproduction;
	exports net.finmath.experiments.ui;
	
	requires transitive net.finmath.lib;
	requires transitive net.finmath.opencl;
	requires transitive net.finmath.plots;

	requires org.jfree.jfreechart;

	requires transitive javafx.controls;
	requires javafx.graphics;
	requires java.prefs;
	requires javafx.base;
	
	requires javafx.swing;

	requires java.logging;
	requires java.management;
	requires java.sql;
	requires commons.csv;
	requires commons.math3;
	requires org.apache.commons.lang3;
}