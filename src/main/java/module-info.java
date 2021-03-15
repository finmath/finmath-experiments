
module net.finmath.experiments {
	exports net.finmath.experiments.hedgesimulator;
	exports net.finmath.experiments.factorreduction;
	exports net.finmath.experiments.shortrate;
	exports net.finmath.experiments.montecarlo.assetderivativevaluation;
	exports net.finmath.experiments.montecarlo.automaticdifferentiation;
	exports net.finmath.experiments.montecarlo.interestrates;
	exports net.finmath.experiments.reproduction;
	
	requires transitive net.finmath.lib;
	requires transitive net.finmath.opencl;
	requires transitive net.finmath.plots;
	requires transitive net.finmath.cuda;

	requires org.jfree.jfreechart;
	requires junit;

	requires javafx.controls;
	requires javafx.base;
	requires transitive javafx.graphics;
	requires javafx.swing;

	requires java.logging;
	requires java.management;
	requires java.sql;
	requires commons.csv;
	requires commons.math3;
	requires org.apache.commons.lang3;
}