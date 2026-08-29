# Forward-sensitivity exchange

The exchange is opt-in; existing experiment runs are unchanged.

Enable export on the specific `ExperimentConfig` that should supply training
data:

```java
.withSensitivityExport(Path.of("results/forward-sensitivity/my-run"))
```

The directory must be absent or empty. An export run stops after producing the
raw pathwise system; it does not execute the remaining basis/lambda comparison.
The equivalent command-line option is

```bash
java ... ForwardSensitivityCapletHedgingExperiment \
  --export-sensitivities results/forward-sensitivity/my-run --no-plots
```

The Java run writes:

```text
my-run/
  manifest.tsv
  metadata/step-00000.tsv
  raw/step-00000.f64
  state/step-00000.f64
  raw/_COMPLETE
```

Each payload is an uncompressed, little-endian float64 array in C order. At a
given rebalancing step:

- `raw` has shape `[target, riskFactor, path]`;
- target `0` is `dV(0)/dM(t)`;
- targets `1..H` are `dP_j(0)/dM(t)` in manifest order;
- `state` has shape `[riskFactor, path]` and contains the conditioning process
  state used by the Java hedge.

The source Java simulation currently stores path realizations as float32. The
exchange serializes those values as float64, and imported projected tensors are
kept as float64 random variables.

The exported parameterization is the current process-state primitive vector.
In a Hull–White model these are not the same object as the tenor-forward curve.

The Python utility requires Python 3.10 or newer and NumPy, and exposes NumPy
memory maps. This command performs an identity projection and is useful as an
end-to-end check:

```bash
python3 src/main/python/forward_sensitivity_exchange.py \
  results/forward-sensitivity/my-run --identity
```

`ForwardSensitivityDataset` verifies each raw/state shard against its exported
SHA-256 hash when first opened. For a trusted dataset where the additional I/O
is undesirable, this can be disabled explicitly with `verify_hashes=False`.

For a Tensor Network estimator, import `ForwardSensitivityDataset`, estimate an
adapted projection of both the product and hedge sensitivities, and call
`write_projected` for every step followed by `mark_projected_complete`.
Projected tensors must retain shape `[target, riskFactor, path]` and the original
path ordering.

Then switch the same Java experiment configuration to:

```java
.withSensitivityImport(Path.of("results/forward-sensitivity/my-run"))
```

or use the matching command-line configuration with
`--import-projected-sensitivities results/forward-sensitivity/my-run`.

The run adds `External-Projected` to the hedge comparison. Java solves the
projected pathwise system and retains the existing trade-value, self-financing,
final-marking, statistics, and plotting logic.

The configuration, seed, path count, rebalancing grid, product, and ordered
hedge instruments must match the export run. Per-step hashes also require the
current raw gradients and process state to match exactly, while allowing AAD IDs
to change between JVM runs. Import rejects mismatched metadata, incomplete
output, incorrectly sized tensors, and non-finite values. The import run still
recomputes the Java AAD system for this validation and for the unchanged raw
comparison; it is a reproducible projection replay rather than a model-free
hedge replay.

Full-curve datasets are large. For example, 20,000 paths, 40 risk factors, 40
hedges, and 31 rebalancing dates require roughly 8 GB for the raw sensitivity
payload alone. Sharding by rebalancing date permits streaming or incremental
processing without loading the full dataset at once.
