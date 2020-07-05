package net.finmath.experiments.hedgesimulator;

import javax.swing.SwingUtilities;

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
		//SwingUtilities.invokeLater(new Runnable() {
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
//		System.exit(0); // Not very graceful, but currently a workaround for runing in jshell.
	}

	public static  void launch() {
		main(new String[0]);
	}
}