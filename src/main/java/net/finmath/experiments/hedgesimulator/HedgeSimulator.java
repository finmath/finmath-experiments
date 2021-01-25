package net.finmath.experiments.hedgesimulator;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.stage.Stage;

public class HedgeSimulator {

	public static void main(String[] args) {
		System.out.println("Starting HedgeSimulator...");
		run();
	}

	public static void run() {
		new JFXPanel();		// Hack to ensure that Java FX Platform is initialized
		Platform.runLater(() -> new HedgeSimulatorApp().start(new Stage()));
		Platform.setImplicitExit(false);
	}
}
