package net.finmath.experiments.factorreduction;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.stage.Stage;

public class FactorReduction {

	public static void main(String[] args) {
		System.out.println("Starting FactorReduction...");
		FactorReductionApp.main(args);
	}

	public static void run() {
		new JFXPanel();		// Hack to ensure that Java FX Platform is initialised
		Platform.setImplicitExit(false);
		Platform.runLater(() -> new FactorReductionApp().start(new Stage()));
	}
}
