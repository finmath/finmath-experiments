package net.finmath.experiments.factorreduction;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class FactorReduction extends Application {

	@Override
	public void start(Stage stage) {
		SwingNode swingNode = new SwingNode();
		createAndSetSwingContent(swingNode);
		swingNode.setVisible(true);

		final StackPane pane = new StackPane();
		pane.getChildren().add(swingNode);

		Scene scene = new Scene(pane, 1200, 800);

		stage.setScene(scene);
		//		stage.setFullScreen(true);
		stage.show();
	}

	@Override
	public void stop() {
	}

	private void createAndSetSwingContent(final SwingNode swingNode) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				final FactorReductionPanel panel = new FactorReductionPanel();
				swingNode.setContent(panel);
			}
		});
	}

	public static void main(String[] args) {
		launch(args);
	}

	public static  void launch() {
		main(new String[0]);
	}

	public static void run() {
		new JFXPanel();		// Hack to ensure that Java FX Platform is initialised
		Platform.setImplicitExit(false);
		Platform.runLater(() -> new FactorReduction().start(new Stage()));
	}
}