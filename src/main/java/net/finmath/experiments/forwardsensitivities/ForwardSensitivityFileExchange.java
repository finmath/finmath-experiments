/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 */
package net.finmath.experiments.forwardsensitivities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.finmath.exception.CalculationException;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.automaticdifferentiation.forwardsensitivities.ForwardSensitivities;
import net.finmath.montecarlo.automaticdifferentiation.forwardsensitivities.ForwardSensitivities.ProjectedHedgeRatioResult;
import net.finmath.montecarlo.automaticdifferentiation.forwardsensitivities.ForwardSensitivities.ReductionMethod;
import net.finmath.montecarlo.interestrate.products.ForwardSensitivityDeltaHedgedPortfolio;
import net.finmath.montecarlo.interestrate.products.ForwardSensitivityDeltaHedgedPortfolio.HedgeRatioProvider;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;

/**
 * File exchange for externally projected pathwise forward-sensitivity systems.
 *
 * <p>
 * For every rebalancing step the raw tensor has little-endian float64 layout
 * {@code [target][riskFactor][path]}. At step time t, target 0 is
 * {@code dV(0)/dM(t)}; targets 1..H are {@code dP_j(0)/dM(t)}. The state tensor has layout
 * {@code [riskFactor][path]}. Python writes the projected sensitivity tensor
 * with the same layout as the raw tensor. Java then performs the usual
 * pathwise solve using the projected system.
 * </p>
 *
 * <p>
 * AAD IDs are intentionally not persisted. Risk-factor names define the file
 * contract and are mapped to the current run's IDs during import.
 * </p>
 */
final class ForwardSensitivityFileExchange implements HedgeRatioProvider {

	enum Mode {
		EXPORT_RAW,
		IMPORT_PROJECTED
	}

	@FunctionalInterface
	interface StateValueProvider {
		Map<String, RandomVariable> getStateValues(
				double evaluationTime,
				Map<String, Long> parameterIDsByName) throws CalculationException;
	}

	private static final String SCHEMA_VERSION = "1";
	private static final String FORMAT_NAME = "finmath-forward-sensitivity-exchange";
	private static final int IO_BUFFER_SIZE = 1024 * 1024;
	private static final double TIME_TOLERANCE = 1E-12;

	private final Mode mode;
	private final Path directory;
	private final TimeDiscretization rebalancingTimes;
	private final int numberOfPaths;
	private final List<String> targetNames;
	private final String experimentDescription;
	private final String experimentSignature;
	private final String datasetId;
	private final StateValueProvider stateValueProvider;
	private final HedgeRatioProvider defaultHedgeRatioProvider;
	private final RandomVariableFactory randomVariableFactory = new RandomVariableFromArrayFactory(true);
	private final boolean[] exportedSteps;
	private final Map<Integer, ProjectedHedgeRatioResult> importedResults = new HashMap<>();

	static ForwardSensitivityFileExchange forExport(
			final Path directory,
			final TimeDiscretization rebalancingTimes,
			final int numberOfPaths,
			final List<String> targetNames,
			final String experimentDescription,
			final StateValueProvider stateValueProvider) throws IOException {

		return new ForwardSensitivityFileExchange(
				Mode.EXPORT_RAW,
				directory,
				rebalancingTimes,
				numberOfPaths,
				targetNames,
				experimentDescription,
				stateValueProvider);
	}

	static ForwardSensitivityFileExchange forImport(
			final Path directory,
			final TimeDiscretization rebalancingTimes,
			final int numberOfPaths,
			final List<String> targetNames,
			final String experimentDescription,
			final StateValueProvider stateValueProvider) throws IOException {

		return new ForwardSensitivityFileExchange(
				Mode.IMPORT_PROJECTED,
				directory,
				rebalancingTimes,
				numberOfPaths,
				targetNames,
				experimentDescription,
				stateValueProvider);
	}

	private ForwardSensitivityFileExchange(
			final Mode mode,
			final Path directory,
			final TimeDiscretization rebalancingTimes,
			final int numberOfPaths,
			final List<String> targetNames,
			final String experimentDescription,
			final StateValueProvider stateValueProvider) throws IOException {

		this.mode = Objects.requireNonNull(mode, "mode must not be null.");
		this.directory = Objects.requireNonNull(directory, "directory must not be null.").toAbsolutePath().normalize();
		this.rebalancingTimes = Objects.requireNonNull(rebalancingTimes, "rebalancingTimes must not be null.");
		if(numberOfPaths <= 0) {
			throw new IllegalArgumentException("numberOfPaths must be positive.");
		}
		this.numberOfPaths = numberOfPaths;
		Objects.requireNonNull(targetNames, "targetNames must not be null.");
		if(targetNames.size() < 2) {
			throw new IllegalArgumentException("targetNames must contain the product and at least one hedge instrument.");
		}
		this.targetNames = List.copyOf(targetNames);
		this.experimentDescription = Objects.requireNonNull(experimentDescription, "experimentDescription must not be null.");
		this.experimentSignature = sha256(experimentDescription);
		this.stateValueProvider = Objects.requireNonNull(stateValueProvider, "stateValueProvider must not be null.");
		this.defaultHedgeRatioProvider = ForwardSensitivityDeltaHedgedPortfolio.getDefaultHedgeRatioProvider();
		this.exportedSteps = new boolean[rebalancingTimes.size()];

		if(mode == Mode.EXPORT_RAW) {
			this.datasetId = UUID.randomUUID().toString();
			initializeExportDataset();
		}
		else {
			final Map<String, String> manifest = readMetadata(manifestPath());
			validateManifest(manifest);
			this.datasetId = required(manifest, "dataset.id", manifestPath());
			validateProjectedCompleteMarker();
		}
	}

	Mode getMode() {
		return mode;
	}

	Path getDirectory() {
		return directory;
	}

	@Override
	public synchronized ProjectedHedgeRatioResult getHedgeRatios(
			final Map<String, Long> parameterIDsByName,
			final double evaluationTime,
			final Map<Long, RandomVariable> derivativeGradient,
			final List<Map<Long, RandomVariable>> hedgePortfolioGradients,
			final RandomVariable[] solutionBasisFunctions,
			final RandomVariable[] testBasisFunctions,
			final double regularizationLambda,
			final ReductionMethod reductionMethod,
			final int numberOfPaths) throws CalculationException {

		validateRuntimeDimensions(hedgePortfolioGradients, numberOfPaths);
		final int stepIndex = getStepIndex(evaluationTime);

		if(mode == Mode.EXPORT_RAW) {
			if(!exportedSteps[stepIndex]) {
				try {
				exportStep(
						stepIndex,
						evaluationTime,
						parameterIDsByName,
						derivativeGradient,
						hedgePortfolioGradients);
				}
				catch(final IOException exception) {
					throw new CalculationException("Could not export forward sensitivities to " + directory + ".", exception);
				}
			}

			return defaultHedgeRatioProvider.getHedgeRatios(
					parameterIDsByName,
					evaluationTime,
					derivativeGradient,
					hedgePortfolioGradients,
					solutionBasisFunctions,
					testBasisFunctions,
					regularizationLambda,
					reductionMethod,
					numberOfPaths);
		}

		final ProjectedHedgeRatioResult cachedResult = importedResults.get(stepIndex);
		if(cachedResult != null) {
			return cachedResult;
		}

		try {
			final ProjectedHedgeRatioResult importedResult = importProjectedStep(
					stepIndex,
					evaluationTime,
					parameterIDsByName,
					derivativeGradient,
					hedgePortfolioGradients);
			importedResults.put(stepIndex, importedResult);
			return importedResult;
		}
		catch(final IOException exception) {
			throw new CalculationException("Could not import projected forward sensitivities from " + directory + ".", exception);
		}
	}

	private void initializeExportDataset() throws IOException {
		if(Files.exists(directory)) {
			try(var entries = Files.list(directory)) {
				if(entries.findAny().isPresent()) {
					throw new IOException("Refusing to overwrite non-empty sensitivity exchange directory "
							+ directory + ". Choose a new directory.");
				}
			}
		}
		Files.createDirectories(directory);
		Files.createDirectories(directory.resolve("metadata"));
		Files.createDirectories(directory.resolve("raw"));
		Files.createDirectories(directory.resolve("state"));
		Files.createDirectories(directory.resolve("projected"));

		final Map<String, String> manifest = expectedManifest();
		manifest.put("dataset.id", datasetId);
		writeMetadataAtomic(manifestPath(), manifest);
	}

	private Map<String, String> expectedManifest() {
		final Map<String, String> manifest = new LinkedHashMap<>();
		manifest.put("format", FORMAT_NAME);
		manifest.put("schema.version", SCHEMA_VERSION);
		manifest.put("dtype", "float64");
		manifest.put("byte.order", "little-endian");
		manifest.put("source.realization.precision", "float32");
		manifest.put("raw.layout", "target,risk-factor,path");
		manifest.put("state.layout", "risk-factor,path");
		manifest.put("parameterization", "process-state-primitives");
		manifest.put("sensitivity.definition", "gradients-of-time-zero-proto-values-with-respect-to-process-state-at-step-time");
		manifest.put("experiment.description", experimentDescription);
		manifest.put("experiment.signature", experimentSignature);
		manifest.put("number.paths", Integer.toString(numberOfPaths));
		manifest.put("number.targets", Integer.toString(targetNames.size()));
		manifest.put("number.hedges", Integer.toString(targetNames.size() - 1));
		manifest.put("number.steps", Integer.toString(rebalancingTimes.size()));
		for(int targetIndex = 0; targetIndex < targetNames.size(); targetIndex++) {
			manifest.put(indexedKey("target", targetIndex), targetNames.get(targetIndex));
		}
		for(int stepIndex = 0; stepIndex < rebalancingTimes.size(); stepIndex++) {
			manifest.put(indexedKey("step.time", stepIndex), Double.toString(rebalancingTimes.getTime(stepIndex)));
		}
		return manifest;
	}

	private void validateManifest(final Map<String, String> manifest) throws IOException {
		final Map<String, String> expected = expectedManifest();
		for(final Map.Entry<String, String> entry : expected.entrySet()) {
			final String actualValue = manifest.get(entry.getKey());
			if(!entry.getValue().equals(actualValue)) {
				throw new IOException("Manifest mismatch for " + entry.getKey() + ": expected '"
						+ entry.getValue() + "' but found '" + actualValue + "' in " + manifestPath() + ".");
			}
		}
	}

	private void validateProjectedCompleteMarker() throws IOException {
		final Path markerPath = directory.resolve("projected").resolve("_COMPLETE");
		final Map<String, String> marker = readMetadata(markerPath);
		final String markerDatasetId = required(marker, "dataset.id", markerPath);
		if(!datasetId.equals(markerDatasetId)) {
			throw new IOException("Projected data belongs to dataset " + markerDatasetId
					+ " but the current manifest identifies dataset " + datasetId + ".");
		}
		final String numberOfSteps = required(marker, "number.steps", markerPath);
		if(!Integer.toString(rebalancingTimes.size()).equals(numberOfSteps)) {
			throw new IOException("Projected completion marker has number.steps=" + numberOfSteps
					+ ", expected " + rebalancingTimes.size() + ".");
		}
	}

	private void exportStep(
			final int stepIndex,
			final double evaluationTime,
			final Map<String, Long> parameterIDsByName,
			final Map<Long, RandomVariable> derivativeGradient,
			final List<Map<Long, RandomVariable>> hedgePortfolioGradients) throws IOException, CalculationException {

		final List<Map.Entry<String, Long>> riskFactors = new ArrayList<>(parameterIDsByName.entrySet());
		if(riskFactors.isEmpty()) {
			throw new IllegalArgumentException("parameterIDsByName must contain at least one risk factor.");
		}

		final Map<String, RandomVariable> stateValues = stateValueProvider.getStateValues(evaluationTime, parameterIDsByName);
		for(final Map.Entry<String, Long> riskFactor : riskFactors) {
			if(!stateValues.containsKey(riskFactor.getKey())) {
				throw new IllegalArgumentException("No state value was supplied for risk factor " + riskFactor.getKey() + ".");
			}
		}

		final MessageDigest rawDigest = newSha256Digest();
		writeBinaryAtomic(rawPath(stepIndex), channel -> {
			final ByteBuffer buffer = newLittleEndianBuffer();
			writeGradientTarget(channel, rawDigest, buffer, riskFactors, derivativeGradient);
			for(final Map<Long, RandomVariable> hedgeGradient : hedgePortfolioGradients) {
				writeGradientTarget(channel, rawDigest, buffer, riskFactors, hedgeGradient);
			}
			flush(channel, rawDigest, buffer);
		});

		final MessageDigest stateDigest = newSha256Digest();
		writeBinaryAtomic(statePath(stepIndex), channel -> {
			final ByteBuffer buffer = newLittleEndianBuffer();
			for(final Map.Entry<String, Long> riskFactor : riskFactors) {
				writeRandomVariable(channel, stateDigest, buffer, stateValues.get(riskFactor.getKey()));
			}
			flush(channel, stateDigest, buffer);
		});

		final Map<String, String> stepMetadata = expectedStepMetadata(stepIndex, evaluationTime, riskFactors);
		stepMetadata.put("raw.sha256", toHex(rawDigest.digest()));
		stepMetadata.put("state.sha256", toHex(stateDigest.digest()));
		writeMetadataAtomic(stepMetadataPath(stepIndex), stepMetadata);

		exportedSteps[stepIndex] = true;
		if(allStepsExported()) {
			final Map<String, String> complete = new LinkedHashMap<>();
			complete.put("dataset.id", datasetId);
			complete.put("number.steps", Integer.toString(rebalancingTimes.size()));
			writeMetadataAtomic(directory.resolve("raw").resolve("_COMPLETE"), complete);
		}
	}

	private ProjectedHedgeRatioResult importProjectedStep(
			final int stepIndex,
			final double evaluationTime,
			final Map<String, Long> parameterIDsByName,
			final Map<Long, RandomVariable> currentDerivativeGradient,
			final List<Map<Long, RandomVariable>> currentHedgeGradients) throws IOException, CalculationException {

		final List<Map.Entry<String, Long>> riskFactors = new ArrayList<>(parameterIDsByName.entrySet());
		final Map<String, String> metadata = readMetadata(stepMetadataPath(stepIndex));
		validateStepMetadata(metadata, stepIndex, evaluationTime, riskFactors);
		validateCurrentSensitivitySystem(
				metadata,
				stepIndex,
				evaluationTime,
				parameterIDsByName,
				riskFactors,
				currentDerivativeGradient,
				currentHedgeGradients);

		final int numberOfRiskFactors = riskFactors.size();
		final long expectedBytes = expectedBytes(targetNames.size(), numberOfRiskFactors, numberOfPaths);
		final Path projectedPath = projectedPath(stepIndex);
		final long actualBytes = Files.size(projectedPath);
		if(actualBytes != expectedBytes) {
			throw new IOException("Projected tensor " + projectedPath + " has " + actualBytes
					+ " bytes; expected " + expectedBytes + ".");
		}

		final Map<Long, RandomVariable> derivativeGradient = new LinkedHashMap<>();
		final List<Map<Long, RandomVariable>> hedgeGradients = new ArrayList<>();
		for(int hedgeIndex = 0; hedgeIndex < targetNames.size() - 1; hedgeIndex++) {
			hedgeGradients.add(new LinkedHashMap<>());
		}

		try(FileChannel channel = FileChannel.open(projectedPath, StandardOpenOption.READ)) {
			final ByteBuffer buffer = newLittleEndianBuffer();
			for(int targetIndex = 0; targetIndex < targetNames.size(); targetIndex++) {
				for(final Map.Entry<String, Long> riskFactor : riskFactors) {
					final double[] values = readVector(channel, buffer, projectedPath);
					final RandomVariable randomVariable = randomVariableFactory.createRandomVariable(evaluationTime, values);
					if(targetIndex == 0) {
						derivativeGradient.put(riskFactor.getValue(), randomVariable);
					}
					else {
						hedgeGradients.get(targetIndex - 1).put(riskFactor.getValue(), randomVariable);
					}
				}
			}
		}

		return ForwardSensitivities.getHedgeRatiosPathwise(
				parameterIDsByName,
				evaluationTime,
				derivativeGradient,
				hedgeGradients,
				numberOfPaths);
	}

	private void validateCurrentSensitivitySystem(
			final Map<String, String> metadata,
			final int stepIndex,
			final double evaluationTime,
			final Map<String, Long> parameterIDsByName,
			final List<Map.Entry<String, Long>> riskFactors,
			final Map<Long, RandomVariable> derivativeGradient,
			final List<Map<Long, RandomVariable>> hedgeGradients) throws IOException, CalculationException {

		final MessageDigest rawDigest = newSha256Digest();
		final ByteBuffer rawBuffer = newLittleEndianBuffer();
		writeGradientTarget(null, rawDigest, rawBuffer, riskFactors, derivativeGradient);
		for(final Map<Long, RandomVariable> hedgeGradient : hedgeGradients) {
			writeGradientTarget(null, rawDigest, rawBuffer, riskFactors, hedgeGradient);
		}
		flush(null, rawDigest, rawBuffer);
		validateDigest(metadata, stepIndex, "raw.sha256", rawDigest.digest());

		final Map<String, RandomVariable> stateValues = stateValueProvider.getStateValues(evaluationTime, parameterIDsByName);
		final MessageDigest stateDigest = newSha256Digest();
		final ByteBuffer stateBuffer = newLittleEndianBuffer();
		for(final Map.Entry<String, Long> riskFactor : riskFactors) {
			final RandomVariable stateValue = stateValues.get(riskFactor.getKey());
			if(stateValue == null) {
				throw new IOException("No current state value was supplied for risk factor " + riskFactor.getKey() + ".");
			}
			writeRandomVariable(null, stateDigest, stateBuffer, stateValue);
		}
		flush(null, stateDigest, stateBuffer);
		validateDigest(metadata, stepIndex, "state.sha256", stateDigest.digest());
	}

	private void validateDigest(
			final Map<String, String> metadata,
			final int stepIndex,
			final String key,
			final byte[] currentDigest) throws IOException {

		final String exportedDigest = required(metadata, key, stepMetadataPath(stepIndex));
		final String currentDigestHex = toHex(currentDigest);
		if(!exportedDigest.equals(currentDigestHex)) {
			throw new IOException("Current sensitivity run does not match the exported dataset for " + key
					+ " (exported " + exportedDigest + ", current " + currentDigestHex + ").");
		}
	}

	private Map<String, String> expectedStepMetadata(
			final int stepIndex,
			final double evaluationTime,
			final List<Map.Entry<String, Long>> riskFactors) {

		final Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("format", FORMAT_NAME);
		metadata.put("schema.version", SCHEMA_VERSION);
		metadata.put("dataset.id", datasetId);
		metadata.put("step.index", Integer.toString(stepIndex));
		metadata.put("step.time", Double.toString(evaluationTime));
		metadata.put("number.paths", Integer.toString(numberOfPaths));
		metadata.put("number.targets", Integer.toString(targetNames.size()));
		metadata.put("number.risk-factors", Integer.toString(riskFactors.size()));
		for(int riskIndex = 0; riskIndex < riskFactors.size(); riskIndex++) {
			metadata.put(indexedKey("risk-factor", riskIndex), riskFactors.get(riskIndex).getKey());
		}
		return metadata;
	}

	private void validateStepMetadata(
			final Map<String, String> metadata,
			final int stepIndex,
			final double evaluationTime,
			final List<Map.Entry<String, Long>> riskFactors) throws IOException {

		final Map<String, String> expected = expectedStepMetadata(stepIndex, evaluationTime, riskFactors);
		for(final Map.Entry<String, String> entry : expected.entrySet()) {
			final String actualValue = metadata.get(entry.getKey());
			if(!entry.getValue().equals(actualValue)) {
				throw new IOException("Step metadata mismatch for " + entry.getKey() + ": expected '"
						+ entry.getValue() + "' but found '" + actualValue + "' in " + stepMetadataPath(stepIndex) + ".");
			}
		}
	}

	private void writeGradientTarget(
			final FileChannel channel,
			final MessageDigest digest,
			final ByteBuffer buffer,
			final List<Map.Entry<String, Long>> riskFactors,
			final Map<Long, RandomVariable> gradient) throws IOException {

		for(final Map.Entry<String, Long> riskFactor : riskFactors) {
			writeRandomVariable(channel, digest, buffer, gradient.get(riskFactor.getValue()));
		}
	}

	private void writeRandomVariable(
			final FileChannel channel,
			final MessageDigest digest,
			final ByteBuffer buffer,
			final RandomVariable randomVariable) throws IOException {

		if(randomVariable != null && !randomVariable.isDeterministic() && randomVariable.size() != numberOfPaths) {
			throw new IOException("Random variable has " + randomVariable.size() + " paths; expected " + numberOfPaths + ".");
		}

		for(int pathIndex = 0; pathIndex < numberOfPaths; pathIndex++) {
			final double value = randomVariable == null ? 0.0
					: randomVariable.isDeterministic() ? randomVariable.doubleValue() : randomVariable.get(pathIndex);
			if(!Double.isFinite(value)) {
				throw new IOException("Cannot write non-finite value " + value + " at path " + pathIndex + ".");
			}
			if(buffer.remaining() < Double.BYTES) {
				flush(channel, digest, buffer);
			}
			buffer.putDouble(value);
		}
	}

	private double[] readVector(
			final FileChannel channel,
			final ByteBuffer buffer,
			final Path source) throws IOException {

		final double[] values = new double[numberOfPaths];
		int pathIndex = 0;
		while(pathIndex < numberOfPaths) {
			buffer.clear();
			final int numberOfDoubles = Math.min(buffer.capacity() / Double.BYTES, numberOfPaths - pathIndex);
			buffer.limit(numberOfDoubles * Double.BYTES);
			readFully(channel, buffer, source);
			buffer.flip();
			for(int index = 0; index < numberOfDoubles; index++) {
				final double value = buffer.getDouble();
				if(!Double.isFinite(value)) {
					throw new IOException("Projected tensor " + source + " contains non-finite value " + value + ".");
				}
				values[pathIndex++] = value;
			}
		}
		return values;
	}

	private void validateRuntimeDimensions(
			final List<Map<Long, RandomVariable>> hedgePortfolioGradients,
			final int runtimeNumberOfPaths) {

		if(runtimeNumberOfPaths != numberOfPaths) {
			throw new IllegalArgumentException("Runtime has " + runtimeNumberOfPaths
					+ " paths, but the exchange expects " + numberOfPaths + ".");
		}
		if(hedgePortfolioGradients.size() != targetNames.size() - 1) {
			throw new IllegalArgumentException("Runtime has " + hedgePortfolioGradients.size()
					+ " hedge instruments, but the exchange expects " + (targetNames.size() - 1) + ".");
		}
	}

	private int getStepIndex(final double evaluationTime) {
		for(int stepIndex = 0; stepIndex < rebalancingTimes.size(); stepIndex++) {
			if(Math.abs(evaluationTime - rebalancingTimes.getTime(stepIndex)) <= TIME_TOLERANCE) {
				return stepIndex;
			}
		}
		throw new IllegalArgumentException("Evaluation time " + evaluationTime + " is not an exchange rebalancing time.");
	}

	private boolean allStepsExported() {
		for(final boolean exported : exportedSteps) {
			if(!exported) {
				return false;
			}
		}
		return true;
	}

	private Path manifestPath() {
		return directory.resolve("manifest.tsv");
	}

	private Path stepMetadataPath(final int stepIndex) {
		return directory.resolve("metadata").resolve(stepFileName(stepIndex, ".tsv"));
	}

	private Path rawPath(final int stepIndex) {
		return directory.resolve("raw").resolve(stepFileName(stepIndex, ".f64"));
	}

	private Path statePath(final int stepIndex) {
		return directory.resolve("state").resolve(stepFileName(stepIndex, ".f64"));
	}

	private Path projectedPath(final int stepIndex) {
		return directory.resolve("projected").resolve(stepFileName(stepIndex, ".f64"));
	}

	private static String stepFileName(final int stepIndex, final String suffix) {
		return String.format(Locale.ROOT, "step-%05d%s", stepIndex, suffix);
	}

	private static String indexedKey(final String prefix, final int index) {
		return String.format(Locale.ROOT, "%s.%05d", prefix, index);
	}

	private static ByteBuffer newLittleEndianBuffer() {
		return ByteBuffer.allocateDirect(IO_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
	}

	private static long expectedBytes(final int... dimensions) throws IOException {
		try {
			long numberOfDoubles = 1L;
			for(final int dimension : dimensions) {
				numberOfDoubles = Math.multiplyExact(numberOfDoubles, dimension);
			}
			return Math.multiplyExact(numberOfDoubles, Double.BYTES);
		}
		catch(final ArithmeticException exception) {
			throw new IOException("Sensitivity tensor is too large.", exception);
		}
	}

	private static void flush(
			final FileChannel channel,
			final MessageDigest digest,
			final ByteBuffer buffer) throws IOException {

		buffer.flip();
		digest.update(buffer.asReadOnlyBuffer());
		if(channel != null) {
			while(buffer.hasRemaining()) {
				channel.write(buffer);
			}
		}
		buffer.clear();
	}

	private static void readFully(
			final FileChannel channel,
			final ByteBuffer buffer,
			final Path source) throws IOException {

		while(buffer.hasRemaining()) {
			if(channel.read(buffer) < 0) {
				throw new IOException("Unexpected end of file while reading " + source + ".");
			}
		}
	}

	@FunctionalInterface
	private interface ChannelWriter {
		void write(FileChannel channel) throws IOException;
	}

	private static void writeBinaryAtomic(final Path target, final ChannelWriter writer) throws IOException {
		Files.createDirectories(target.getParent());
		final Path temporary = temporarySibling(target);
		try {
			try(FileChannel channel = FileChannel.open(
					temporary,
					StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE)) {
				writer.write(channel);
				channel.force(true);
			}
			atomicReplace(temporary, target);
		}
		finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void writeMetadataAtomic(final Path target, final Map<String, String> metadata) throws IOException {
		Files.createDirectories(target.getParent());
		final Path temporary = temporarySibling(target);
		try {
			try(BufferedWriter writer = Files.newBufferedWriter(
					temporary,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE)) {
				for(final Map.Entry<String, String> entry : metadata.entrySet()) {
					validateMetadataToken(entry.getKey());
					validateMetadataToken(entry.getValue());
					writer.write(entry.getKey());
					writer.write('\t');
					writer.write(entry.getValue());
					writer.newLine();
				}
			}
			atomicReplace(temporary, target);
		}
		finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static Map<String, String> readMetadata(final Path source) throws IOException {
		final Map<String, String> metadata = new LinkedHashMap<>();
		try(BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
			String line;
			int lineNumber = 0;
			while((line = reader.readLine()) != null) {
				lineNumber++;
				if(line.isEmpty()) {
					continue;
				}
				final int separatorIndex = line.indexOf('\t');
				if(separatorIndex <= 0) {
					throw new IOException("Malformed metadata line " + lineNumber + " in " + source + ".");
				}
				final String key = line.substring(0, separatorIndex);
				final String value = line.substring(separatorIndex + 1);
				if(metadata.putIfAbsent(key, value) != null) {
					throw new IOException("Duplicate metadata key " + key + " in " + source + ".");
				}
			}
		}
		return metadata;
	}

	private static String required(
			final Map<String, String> metadata,
			final String key,
			final Path source) throws IOException {

		final String value = metadata.get(key);
		if(value == null) {
			throw new IOException("Missing metadata key " + key + " in " + source + ".");
		}
		return value;
	}

	private static void validateMetadataToken(final String value) throws IOException {
		if(value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
			throw new IOException("Metadata values must not contain tabs or line breaks: " + value);
		}
	}

	private static Path temporarySibling(final Path target) {
		return target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
	}

	private static void atomicReplace(final Path source, final Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch(final AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static MessageDigest newSha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch(final NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private static String sha256(final String value) {
		return toHex(newSha256Digest().digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	private static String toHex(final byte[] digest) {
		final StringBuilder hex = new StringBuilder(digest.length * 2);
		for(final byte element : digest) {
			hex.append(String.format(Locale.ROOT, "%02x", element & 0xff));
		}
		return hex.toString();
	}
}
