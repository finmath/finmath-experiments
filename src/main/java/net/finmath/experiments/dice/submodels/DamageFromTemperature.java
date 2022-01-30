package net.finmath.experiments.dice.submodels;

import java.util.function.DoubleUnaryOperator;

/**
 * The function \( T \mapsto \Omega(T) \) with \( T \) being the temperature above baseline, i.e., \( Omega(0) = 0 \).
 * 
 * The function is a second order polynomial.
 * 
 * @author Christian Fries
 */
public class DamageFromTemperature implements DoubleUnaryOperator {

	/*
	 * Default coefficients from paper
	 */
	private final double tempToDamage0;
	private final double tempToDamage1;
	private final double tempToDamage2;
	
	public DamageFromTemperature(double tempToDamage0, double tempToDamage1, double tempToDamage2) {
		super();
		this.tempToDamage0 = tempToDamage0;
		this.tempToDamage1 = tempToDamage1;
		this.tempToDamage2 = tempToDamage2;
	}

	public DamageFromTemperature() {
		// Parameters from original model
		this(0.0, 0.0, 0.00236);
	}

	@Override
	public double applyAsDouble(double temperature) {
		double damage = tempToDamage0 + tempToDamage1 * temperature + tempToDamage2 * temperature * temperature;

		return damage / (1+damage);
	}

}
