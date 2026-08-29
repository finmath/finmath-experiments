package net.finmath.experiments.forwardsensitivities;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.automaticdifferentiation.forwardsensitivities.ForwardSensitivities.ProjectedHedgeRatioResult;
import net.finmath.montecarlo.automaticdifferentiation.forwardsensitivities.ForwardSensitivities.ReductionMethod;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

class ForwardSensitivityFileExchangeTest {

	private static final int NUMBER_OF_PATHS = 3;
	private static final String EXPERIMENT_DESCRIPTION = "model=test;seed=1234";

	@TempDir
	Path temporaryDirectory;

	@Test
	void exchangeGridExcludesTimesSkippedByTheHedge() {
		final TimeDiscretization configuredTimes = new TimeDiscretizationFromArray(0.0, 3, 1.0);

		final TimeDiscretization exchangeTimes = ForwardSensitivityCapletHedgingExperiment
				.getSensitivityExchangeRebalancingTimes(configuredTimes, 3.0);

		assertArrayEquals(new double[] { 0.0, 1.0, 2.0 }, exchangeTimes.getAsDoubleArray(), 0.0);
	}

	@Test
	void identityProjectionRoundTripPreservesPathwiseHedgeRatios() throws Exception {
		final TimeDiscretization rebalancingTimes = new TimeDiscretizationFromArray(0.0);
		final List<String> targetNames = List.of("product", "hedge[0]");
		final RandomVariableFactory factory = new RandomVariableFromArrayFactory(false);

		final Map<String, Long> parameterIDs = new LinkedHashMap<>();
		parameterIDs.put("risk-0", 10L);
		parameterIDs.put("risk-1", 11L);

		final Map<Long, RandomVariable> productGradient = new LinkedHashMap<>();
		productGradient.put(10L, factory.createRandomVariable(0.0, new double[] { 2.0, 4.0, 6.0 }));
		productGradient.put(11L, factory.createRandomVariable(0.0, new double[] { 4.0, 6.0, 8.0 }));

		final Map<Long, RandomVariable> hedgeGradient = new LinkedHashMap<>();
		hedgeGradient.put(10L, factory.createRandomVariable(0.0, new double[] { 1.0, 2.0, 3.0 }));
		hedgeGradient.put(11L, factory.createRandomVariable(0.0, new double[] { 2.0, 3.0, 4.0 }));
		final List<Map<Long, RandomVariable>> hedgeGradients = List.of(hedgeGradient);
		final Map<String, RandomVariable> stateValues = Map.of(
				"risk-0", factory.createRandomVariable(0.0, new double[] { 10.0, 11.0, 12.0 }),
				"risk-1", factory.createRandomVariable(0.0, new double[] { 20.0, 21.0, 22.0 }));

		final ForwardSensitivityFileExchange exporter = ForwardSensitivityFileExchange.forExport(
				temporaryDirectory,
				rebalancingTimes,
				NUMBER_OF_PATHS,
				targetNames,
				EXPERIMENT_DESCRIPTION,
				(time, ids) -> stateValues);

		final ProjectedHedgeRatioResult rawResult = exporter.getHedgeRatios(
				parameterIDs,
				0.0,
				productGradient,
				hedgeGradients,
				null,
				null,
				0.0,
				ReductionMethod.PATHWISE,
				NUMBER_OF_PATHS);

		assertArrayEquals(
				new double[] { 2.0, 4.0, 6.0, 4.0, 6.0, 8.0, 1.0, 2.0, 3.0, 2.0, 3.0, 4.0 },
				readLittleEndianDoubles(temporaryDirectory.resolve("raw/step-00000.f64")),
				0.0);
		assertArrayEquals(
				new double[] { 10.0, 11.0, 12.0, 20.0, 21.0, 22.0 },
				readLittleEndianDoubles(temporaryDirectory.resolve("state/step-00000.f64")),
				0.0);

		publishIdentityProjection(temporaryDirectory);

		final ForwardSensitivityFileExchange importer = ForwardSensitivityFileExchange.forImport(
				temporaryDirectory,
				rebalancingTimes,
				NUMBER_OF_PATHS,
				targetNames,
				EXPERIMENT_DESCRIPTION,
				(time, ids) -> stateValues);
		final Map<String, Long> remappedParameterIDs = new LinkedHashMap<>();
		remappedParameterIDs.put("risk-0", 100L);
		remappedParameterIDs.put("risk-1", 101L);
		final Map<Long, RandomVariable> remappedProductGradient = Map.of(
				100L, productGradient.get(10L),
				101L, productGradient.get(11L));
		final Map<Long, RandomVariable> remappedHedgeGradient = Map.of(
				100L, hedgeGradient.get(10L),
				101L, hedgeGradient.get(11L));
		final Map<Long, RandomVariable> mismatchedProductGradient = new LinkedHashMap<>(remappedProductGradient);
		mismatchedProductGradient.put(
				100L,
				factory.createRandomVariable(0.0, new double[] { 2.0, 4.0, 7.0 }));
		assertThrows(
				CalculationException.class,
				() -> importer.getHedgeRatios(
						remappedParameterIDs,
						0.0,
						mismatchedProductGradient,
						List.of(remappedHedgeGradient),
						null,
						null,
						0.0,
						ReductionMethod.PATHWISE,
						NUMBER_OF_PATHS));
		final ProjectedHedgeRatioResult importedResult = importer.getHedgeRatios(
				remappedParameterIDs,
				0.0,
				remappedProductGradient,
				List.of(remappedHedgeGradient),
				null,
				null,
				0.0,
				ReductionMethod.PATHWISE,
				NUMBER_OF_PATHS);

		for(int pathIndex = 0; pathIndex < NUMBER_OF_PATHS; pathIndex++) {
			assertEquals(2.0, rawResult.getHedgeRatios()[0].get(pathIndex), 1E-12);
			assertEquals(
					rawResult.getHedgeRatios()[0].get(pathIndex),
					importedResult.getHedgeRatios()[0].get(pathIndex),
					1E-12);
		}
	}

	@Test
	void importRejectsDifferentExperimentConfiguration() throws Exception {
		final TimeDiscretization rebalancingTimes = new TimeDiscretizationFromArray(0.0);
		final List<String> targetNames = List.of("product", "hedge[0]");
		final RandomVariableFactory factory = new RandomVariableFromArrayFactory(false);

		final ForwardSensitivityFileExchange exporter = ForwardSensitivityFileExchange.forExport(
				temporaryDirectory,
				rebalancingTimes,
				NUMBER_OF_PATHS,
				targetNames,
				EXPERIMENT_DESCRIPTION,
				(time, ids) -> Map.of("risk", factory.createRandomVariable(time, 1.0)));

		final Map<String, Long> parameterIDs = new LinkedHashMap<>();
		parameterIDs.put("risk", 10L);
		exporter.getHedgeRatios(
				parameterIDs,
				0.0,
				Map.of(10L, factory.createRandomVariable(0.0, 2.0)),
				List.of(Map.of(10L, factory.createRandomVariable(0.0, 1.0))),
				null,
				null,
				0.0,
				ReductionMethod.PATHWISE,
				NUMBER_OF_PATHS);
		publishIdentityProjection(temporaryDirectory);

		assertThrows(
				IOException.class,
				() -> ForwardSensitivityFileExchange.forImport(
						temporaryDirectory,
						rebalancingTimes,
						NUMBER_OF_PATHS,
						targetNames,
						"model=changed;seed=1234",
						(time, ids) -> Map.of("risk", factory.createRandomVariable(time, 1.0))));
	}

	private static void publishIdentityProjection(final Path directory) throws IOException {
		Files.copy(
				directory.resolve("raw/step-00000.f64"),
				directory.resolve("projected/step-00000.f64"),
				StandardCopyOption.REPLACE_EXISTING);
		final String datasetId = readMetadata(directory.resolve("manifest.tsv")).get("dataset.id");
		Files.writeString(
				directory.resolve("projected/_COMPLETE"),
				"dataset.id\t" + datasetId + "\nnumber.steps\t1\n");
	}

	private static Map<String, String> readMetadata(final Path path) throws IOException {
		final Map<String, String> metadata = new LinkedHashMap<>();
		for(final String line : Files.readAllLines(path)) {
			final String[] fields = line.split("\\t", 2);
			metadata.put(fields[0], fields[1]);
		}
		return metadata;
	}

	private static double[] readLittleEndianDoubles(final Path path) throws IOException {
		final ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
		final List<Double> values = new ArrayList<>();
		while(buffer.hasRemaining()) {
			values.add(buffer.getDouble());
		}
		return values.stream().mapToDouble(Double::doubleValue).toArray();
	}
}
