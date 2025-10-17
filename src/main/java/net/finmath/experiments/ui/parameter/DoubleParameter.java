package net.finmath.experiments.ui.parameter;

import javafx.beans.property.SimpleDoubleProperty;

public class DoubleParameter extends Parameter {

	public static record DoubleParameterSpec(double initial, double min, double max) implements ParameterSpec {};

	public DoubleParameter(String name, double value, double min, double max) {
		super(new SimpleDoubleProperty(null, name, value), new DoubleParameterSpec(value, min,max));
	}

	public SimpleDoubleProperty getBindableValue() {
		return (SimpleDoubleProperty)super.getBindableValue();
	}

	public DoubleParameter.DoubleParameterSpec getSpec() {
		return (DoubleParameter.DoubleParameterSpec)super.getSpec();
	}
}