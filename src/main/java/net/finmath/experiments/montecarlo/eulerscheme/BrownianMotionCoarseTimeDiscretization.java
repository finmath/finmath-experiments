package net.finmath.experiments.montecarlo.eulerscheme;

import net.finmath.montecarlo.BrownianMotion;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;

public final class BrownianMotionCoarseTimeDiscretization implements BrownianMotion {
	private final BrownianMotion brownianMotion;
	private final TimeDiscretization timeDiscretizationCoarse;

	public BrownianMotionCoarseTimeDiscretization(TimeDiscretization timeDiscretizationCoarse, BrownianMotion brownianMotion) {
		this.timeDiscretizationCoarse = timeDiscretizationCoarse;
		this.brownianMotion = brownianMotion;
	}

	@Override
	public RandomVariable getIncrement(int timeIndex, int factor) {
		double startTime = timeDiscretizationCoarse.getTime(timeIndex);
		double endTime = timeDiscretizationCoarse.getTime(timeIndex+1);
		int startTimeIndexOnFineDiscretization = brownianMotion.getTimeDiscretization().getTimeIndex(startTime);
		int endTimeIndexOnFineDiscretization = brownianMotion.getTimeDiscretization().getTimeIndex(endTime);

		RandomVariable brownianIncrement = getRandomVariableForConstant(0.0);
		for(int indexOnFineDiscretization = startTimeIndexOnFineDiscretization; indexOnFineDiscretization<endTimeIndexOnFineDiscretization; indexOnFineDiscretization++) {
			brownianIncrement = brownianIncrement.add(brownianMotion.getIncrement(indexOnFineDiscretization, factor));
		}
		return brownianIncrement;
	}

	@Override
	public TimeDiscretization getTimeDiscretization() {
		return timeDiscretizationCoarse;
	}

	@Override
	public RandomVariable getRandomVariableForConstant(double value) {
		return brownianMotion.getRandomVariableForConstant(value);
	}

	@Override
	public int getNumberOfPaths() {
		return brownianMotion.getNumberOfPaths();
	}

	@Override
	public int getNumberOfFactors() {
		return brownianMotion.getNumberOfFactors();
	}

	@Override
	public RandomVariable getBrownianIncrement(int timeIndex, int factor) {
		return getIncrement(timeIndex, factor);
	}

	@Override
	public BrownianMotion getCloneWithModifiedTimeDiscretization(TimeDiscretization newTimeDiscretization) {
		throw new UnsupportedOperationException();
	}

	@Override
	public BrownianMotion getCloneWithModifiedSeed(int seed) {
		throw new UnsupportedOperationException();
	}
}