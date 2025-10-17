package net.finmath.experiments.ui.parameter;

import javafx.beans.property.Property;

public class Parameter {

	private Property<?> bindableValue;
	private ParameterSpec spec;

	public Parameter(Property<?> bindableValue, ParameterSpec spec) {
		super();
		this.bindableValue = bindableValue;
		this.spec = spec;
	}

	public Property<?> getBindableValue() {
		return bindableValue;
	}

	public ParameterSpec getSpec() {
		return spec;
	}
}