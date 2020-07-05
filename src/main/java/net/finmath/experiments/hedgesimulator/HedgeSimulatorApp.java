package net.finmath.experiments.hedgesimulator;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class HedgeSimulatorApp extends Application {

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
				final HedgeSimulatorPanel panel = new HedgeSimulatorPanel();
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
}