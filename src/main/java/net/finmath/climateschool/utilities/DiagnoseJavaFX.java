package net.finmath.climateschool.utilities;

/**
 * Application to check if JavaFX works.
 */
public class DiagnoseJavaFX {

	public static void main(String[] args) {
		System.out.println("Classpath = " + System.getProperty("java.class.path"));
		check("javafx.application.Application"); // -> javafx-base
		check("javafx.stage.Stage");             // -> javafx-graphics
		check("javafx.scene.control.Button");    // -> javafx-controls
	}

	static void check(String cn) {
		try {
			Class.forName(cn, false, DiagnoseJavaFX.class.getClassLoader());
			System.out.println("OK   " + cn);
		}
		catch (Throwable t) {
			System.out.println("Miss " + cn + "  -> " + t);
		}
	}
}
