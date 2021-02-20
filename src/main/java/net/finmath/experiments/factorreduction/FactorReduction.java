package net.finmath.experiments.factorreduction;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.stage.Stage;

public class FactorReduction {

	public static void main(String[] args) {
		System.out.println("Starting FactorReduction...");
		run();
	}

	public static void run() {
		new JFXPanel();		// Hack to ensure that Java FX Platform is initialized
		Platform.runLater(() -> new FactorReductionApp().start(new Stage()));
		Platform.setImplicitExit(false);
	}
}
