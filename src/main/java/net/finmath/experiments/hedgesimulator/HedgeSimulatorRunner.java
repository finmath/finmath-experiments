/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 16 Jan 2020
 */

package net.finmath.experiments.hedgesimulator;

/**
 * @author Christian Fries
 *
 */
public class HedgeSimulatorRunner {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Starting...");

		javafx.application.Application.launch(HedgeSimulatorApp.class, args);
	}

}
