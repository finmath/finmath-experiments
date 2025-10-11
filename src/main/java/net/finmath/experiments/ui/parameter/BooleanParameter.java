package net.finmath.experiments.ui.parameter;

import javafx.beans.property.SimpleBooleanProperty;

public class BooleanParameter extends Parameter {

	public static record BooleanParameterSpec(boolean initial) implements ParameterSpec {};

	public BooleanParameter(String name, boolean value) {
		super(new SimpleBooleanProperty(null, name, value), new BooleanParameterSpec(value));
	}

	public SimpleBooleanProperty getBindableValue() {
		return (SimpleBooleanProperty)super.getBindableValue();
	}

	public ParameterSpec getSpec() {
		return (ParameterSpec)super.getSpec();
	}
}