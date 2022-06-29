package net.finmath.experiments.montecarlo.schemes;

import net.finmath.stochastic.RandomVariable;

/**
 * Interface for classes that implement different time discrete approximations
 * \[
 * 		i \mapsto \tilde{X}(t_{i})
 * \]
 * of the lognormal process
 * \[
 * 		dX(t) = mu X(t) dt + sigma X(t) dW(t), X(0) = X_0
 * \]
 * with a given time discretization
 * \[
 * 		0 = t_{0} < t_{1} < ... < t_{n}
 * \]
 *
 * @author Christian Fries
 *
 */
public interface LognormalProcess {

	/**
	 * Returns the approximation \tilde{X}(t_{i}) as a sample vector.
	 * 
	 * @param timeIndex The time index i
	 * @return The RandomVariable \tilde{X}(t_{i})
	 */
	RandomVariable getProcessValue(int timeIndex);

	/**
	 * Returns the average of the random variable given by the process at the given time index
	 *
	 * @param timeIndex The time index
	 * @return The average
	 */
	default double getExpectation(int timeIndex) {
		return getProcessValue(timeIndex).getAverage();
	}

	default double getExpectationOfLog(int timeIndex) {
		return getProcessValue(timeIndex).log().getAverage();
	}

	default double getVarianceOfLog(int timeIndex) {
		return getProcessValue(timeIndex).log().getVariance();
	}

	/**
	 * @return Returns the nPaths.
	 */
	int getNumberOfPaths();

	/**
	 * @return Returns the numberOfTimeSteps.
	 */
	int getNumberOfTimeSteps();

	/**
	 * @return Returns the deltaT.
	 */
	double getDeltaT();

	/**
	 * @return Returns the initialValue.
	 */
	double getInitialValue();

	/**
	 * @return Returns the mu.
	 */
	double getDrift();

	/**
	 * @return Returns the sigma.
	 */
	double getSigma();
}