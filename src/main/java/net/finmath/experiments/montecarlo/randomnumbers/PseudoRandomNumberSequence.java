/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 *
 * Created on 25.10.2012
 */
package net.finmath.experiments.montecarlo.randomnumbers;

import java.util.Random;

import net.finmath.randomnumbers.MersenneTwister;

/**
 * @author Christian Fries
 */
public class PseudoRandomNumberSequence {

	enum RandomNumberGeneratorType {
		LCG_JAVA,
		MERSENNE_TWISTER
	}

	private RandomNumberGeneratorType	type;
	private long						seed;
	private int							length;
	private double[]					randomNumbers;


	/**
	 * Create a random number sequence using a specified generator, seed and length.
	 * Note: The sequence is pre-calculated and stored in memory to allow random access.
	 *
	 * @param type Random number generator to use.
	 * @param seed Seed of the generator.
	 * @param length Length of the sequence.
	 */
	public PseudoRandomNumberSequence(RandomNumberGeneratorType type, long seed, int length) {
		super();
		this.type = type;
		this.seed = seed;
		this.length = length;
	}

	/**
	 * Create a random number sequence using a specified generator, seed and length.
	 * Note: The sequence is pre-calculated and stored in memory to allow random access.
	 * Using a Mersenne Twister as default.
	 *
	 * @param seed Seed of the generator.
	 * @param length Length of the sequence.
	 */
	public PseudoRandomNumberSequence(long seed, int length) {
		super();
		this.type = RandomNumberGeneratorType.MERSENNE_TWISTER;
		this.seed = seed;
		this.length = length;
	}

	public double getRandomNumber(int index) {
		if(randomNumbers == null || randomNumbers.length < length) initRandomNumbers();
		return randomNumbers[index];
	}

	private void initRandomNumbers() {
		randomNumbers = new double[length];

		// Create random number sequence
		switch(type) {
		case LCG_JAVA:
			Random lcgJava = new Random(seed);
			for(int numberIndex=0; numberIndex < length; numberIndex++) {
				randomNumbers[numberIndex] = lcgJava.nextDouble();
			}
			break;
		case MERSENNE_TWISTER:
		default:
			MersenneTwister mersenneTwister = new MersenneTwister(seed);
			for(int numberIndex=0; numberIndex < length; numberIndex++) {
				randomNumbers[numberIndex] = mersenneTwister.nextDouble();
			}
			break;
		}
	}
}
