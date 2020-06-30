package net.finmath.experiments.hedgesimulator;

import javax.swing.SwingUtilities;

import javafx.application.Application;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class HedgeSimulatorApp extends Application {

	@Override
	public void start(Stage stage) {
		final SwingNode swingNode = new SwingNode();
		createAndSetSwingContent(swingNode);

		final StackPane pane = new StackPane();
		pane.getChildren().add(swingNode);

		stage.setScene(new Scene(pane, 1200, 800));
		stage.show();
	}

	private void createAndSetSwingContent(final SwingNode swingNode) {
		SwingUtilities.invokeLater(new Runnable() {
			//		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				final HedgeSimulatorPanel panel = new HedgeSimulatorPanel();
				swingNode.setContent(panel);
			}
		});
	}

	public static void main(String[] args) {
		launch(args);
	}
}