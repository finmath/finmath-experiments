/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 10.06.2014
 */

package net.finmath.experiments.blackscholes;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import net.finmath.functions.AnalyticFormulas;

/**
 * @author Christian Fries
 */
public class AnalyticOptionValuation {

	static NumberFormat formatReal2 = new DecimalFormat("#0.00");

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		final double spot1 = 100.0;
		final double spot2 = 100.0;
		final double volatility1 = 0.3;
		final double volatility2 = 0.2;
		final double riskFreeRate = 0.05;
		final double optionMaturity = 2.0;

		System.out.println("Value of exchange option for different correlation parameters:");
		for(double rho = -1.0; rho <= 1.0; rho += 0.1) {
			final double value = AnalyticFormulas.margrabeExchangeOptionValue(spot1, spot2, volatility1, volatility2, rho, optionMaturity);
			System.out.println(formatReal2.format(rho) + "\t" + formatReal2.format(value));
		}
	}
}
