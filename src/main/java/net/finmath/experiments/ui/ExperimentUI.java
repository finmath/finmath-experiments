package net.finmath.experiments.ui;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;
import net.finmath.experiments.ui.parameter.BooleanParameter;
import net.finmath.experiments.ui.parameter.DoubleParameter;
import net.finmath.experiments.ui.parameter.Parameter;

public abstract class ExperimentUI extends Application {

	// The parameters
	private final List<Parameter> parameters;

	private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "compute-thread");
		t.setDaemon(true);
		return t;
	});
	private Future<?> currentJob;

	private final PauseTransition debounce = new PauseTransition(Duration.millis(300));
	private final DecimalFormat df = new DecimalFormat("#.####");

	public ExperimentUI(List<Parameter> parameters) {
		this.parameters = parameters;
	}

	abstract public String getTitle();
	abstract public void runCalculation();

	public void runCalculationAsync() {
		System.out.println("Starting calculation.");
		// cancel running calculation
		if (currentJob != null && !currentJob.isDone()) {
			System.out.println("Cancel previous calculation.");
			currentJob.cancel(true);
		}

		Task<Double> task = new Task<>() {
			@Override
			protected Double call() throws Exception {
				runCalculation();
				return 0.0;
			}
		};

		currentJob = pool.submit(task);
	}

	public List<Parameter> getExperimentParameters() {
		return parameters;
	}

	/*
	 * UI Part
	 */

	// Für eigenständigen Start (Fallback):
	@Override
	public void start(Stage stage) {
		stage.setTitle("finmath Experiment (Window)");
		stage.setScene(new Scene(createContent()));
		// Achtung: Hier KEIN Platform.exit() im onCloseRequest!
		stage.show();
	}

	// Für EMBEDDING im Host:
	public Parent createContent() {
		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(10);
		grid.setPadding(new Insets(16));

		// Headder
		addHeader(grid);

		// Parameters
		for(int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
			addParameterRow(grid, parameterIndex+1, parameters.get(parameterIndex));
		}

		// Berechnungs-Trigger (entprellt auf 300 ms nach letzter Änderung)
		debounce.setOnFinished(e -> runCalculationAsync());

		// Buttons
		HBox buttons = new HBox(10);
		Button btnReset = new Button("Reset");
		btnReset.setOnAction(e -> resetToDefaults());
		Button btnCompute = new Button("Calculate");
		btnCompute.setOnAction(e -> runCalculationAsync());
		buttons.getChildren().addAll(btnReset, btnCompute);
		buttons.setAlignment(Pos.CENTER_LEFT);

		VBox content = new VBox(12, grid, buttons);
		content.setPadding(new Insets(14));

		TitledPane group = new TitledPane(getTitle(), content);
		group.setCollapsible(false);
		group.setAnimated(false);
		group.setMaxWidth(Double.MAX_VALUE);

		return group;
	}

	/**
	 * Add a header row to the grid
	 * 
	 * @param grid
	 */
	private void addHeader(GridPane grid) {
		Label hName			= new Label("Parameter");
		Label hValue		= new Label("Value");
		Label hConstrain	= new Label("Constrain");

		hName.getStyleClass().add("header");
		hValue.getStyleClass().add("header");
		hConstrain.getStyleClass().add("header");

		grid.add(hName,      0, 0);
		grid.add(hValue,     1, 0);
		grid.add(hConstrain, 2, 0);

		ColumnConstraints c0 = new ColumnConstraints();
		c0.setPercentWidth(25);
		ColumnConstraints c1 = new ColumnConstraints();
		c1.setPercentWidth(55);
		ColumnConstraints c2 = new ColumnConstraints();
		c2.setPercentWidth(30);
		grid.getColumnConstraints().addAll(c0, c1, c2);
	}

	/**
	 * Add a parameter row to the grid
	 * @param grid The grid.
	 * @param row The row index.
	 * @param p The parameter.
	 * @return The next row index.
	 */
	private int addParameterRow(GridPane grid, int row, Parameter parameter) {
		if(parameter instanceof DoubleParameter p) {
			double lo = Math.min(p.getSpec().min(), p.getSpec().max());
			double hi = Math.max(p.getSpec().min(), p.getSpec().max());
			SimpleDoubleProperty value = p.getBindableValue();

			Label name = new Label(value.getName());
			name.setMinWidth(160);

			// Slider + Value-Textfeld gebündelt
			Slider slider = new Slider(lo, hi, value.get());
			slider.setShowTickLabels(true);
			slider.setShowTickMarks(true);
			slider.setMajorTickUnit((hi - lo) / 4.0);
			slider.setMinorTickCount(4);
			slider.setBlockIncrement((hi - lo) / 100.0);

			TextField valueField = new TextField(df.format(value.get()));
			valueField.setPrefColumnCount(8);
			HBox sliderBox = new HBox(8, slider, valueField);
			sliderBox.setAlignment(Pos.CENTER_LEFT);

			// constrain Labels
			Label constrainLabel = new Label("in (" + df.format(lo) + "," + df.format(hi)+ ")");

			// Bidirektionales Binding (mit robuster Konvertierung)
			StringConverter<Number> conv = new NumberStringConverter(df);
			// Slider -> Textfeld
			valueField.textProperty().bindBidirectional(slider.valueProperty(), conv);
			// Property <-> Slider (bleibt Quelle der Wahrheit)
			value.bindBidirectional(slider.valueProperty());

			// Änderungen entprellt berechnen
			slider.valueProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
			valueField.setOnAction(e -> debounce.playFromStart());
			valueField.focusedProperty().addListener((obs, was, isNow) -> {
				if (!isNow) debounce.playFromStart(); // bei Fokus-Verlust
			});

			// Tooltip mit aktuellem Wert
			Tooltip tip = new Tooltip();
			tip.textProperty().bind(Bindings.createStringBinding(
					() -> value.getName() + ": " + df.format(slider.getValue()), slider.valueProperty()));
			Tooltip.install(slider, tip);

			grid.add(name,      0, row);
			grid.add(sliderBox, 1, row);
			grid.add(constrainLabel,  2, row);
		}
		else if(parameter instanceof BooleanParameter p) {
			SimpleBooleanProperty value = p.getBindableValue();

			Label name = new Label(value.getName());
			name.setMinWidth(160);

			CheckBox check = new CheckBox();
			check.setAllowIndeterminate(false);
			check.setSelected(value.get());                       // initial
			check.selectedProperty().bindBidirectional(value);    // bidirektional

			// Änderungen entprellt berechnen
			check.setOnAction(e -> debounce.playFromStart());
			value.addListener((obs, oldV, newV) -> debounce.playFromStart());

			// Tooltip with current state
			Tooltip tip = new Tooltip();
			tip.textProperty().bind(Bindings.when(check.selectedProperty())
					.then(value.getName() + ": yes")
					.otherwise(value.getName() + ": no"));
			Tooltip.install(check, tip);

			grid.add(name, 0, row);
			grid.add(check, 1, row);
		}
		return row + 1;
	}

	/** Setzt alle Werte auf die ursprünglich übergebenen Startwerte (geklammert auf min/max). */
	private void resetToDefaults() {
		for (Parameter parameter : parameters) {
			if(parameter instanceof DoubleParameter p) {
				double lo = Math.min(p.getSpec().min(), p.getSpec().max());
				double hi = Math.max(p.getSpec().min(), p.getSpec().max());
				double value = clamp(p.getSpec().initial(), lo, hi);
				p.getBindableValue().set(value);
			}
			debounce.playFromStart();
		}
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	public static void main(String[] args) {
		// Falls du das aus einer IDE mit Modular-Setup startest,
		// achte darauf, die JavaFX-Module (controls, graphics, base) korrekt zu laden.
		launch(args);
	}
}
