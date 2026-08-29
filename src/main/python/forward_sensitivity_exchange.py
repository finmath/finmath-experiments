#!/usr/bin/env python3
"""Utilities for the finmath pathwise forward-sensitivity file exchange.

The Java exporter writes one little-endian float64 shard per rebalancing time:

    raw[0, risk, path]       = dV/dM
    raw[1 + j, risk, path]   = dP_j/dM
    state[risk, path]        = conditioning process state M

An external estimator writes ``projected/step-NNNNN.f64`` with the same shape
as ``raw``. Java subsequently solves the projected pathwise hedge system and
runs the existing self-financing delta hedge.

Use ``--identity`` for an end-to-end round-trip check. Replace
``project_sensitivities`` when integrating a Tensor Network estimator.
"""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import uuid

import numpy as np


DTYPE = np.dtype("<f8")
FINITE_CHECK_PATH_CHUNK = 1_000_000
HASH_BUFFER_SIZE = 16 * 1024 * 1024


def contains_only_finite_values(array: np.ndarray) -> bool:
    """Check a three-dimensional tensor without an array-sized temporary."""

    for target_index in range(array.shape[0]):
        for risk_index in range(array.shape[1]):
            for path_start in range(0, array.shape[2], FINITE_CHECK_PATH_CHUNK):
                path_end = min(path_start + FINITE_CHECK_PATH_CHUNK, array.shape[2])
                if not np.isfinite(array[target_index, risk_index, path_start:path_end]).all():
                    return False
    return True


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        while chunk := file.read(HASH_BUFFER_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def read_metadata(path: Path) -> dict[str, str]:
    metadata: dict[str, str] = {}
    with path.open("r", encoding="utf-8") as file:
        for line_number, line in enumerate(file, start=1):
            line = line.rstrip("\r\n")
            if not line:
                continue
            if "\t" not in line:
                raise ValueError(f"Malformed metadata line {line_number} in {path}")
            key, value = line.split("\t", maxsplit=1)
            if key in metadata:
                raise ValueError(f"Duplicate metadata key {key!r} in {path}")
            metadata[key] = value
    return metadata


def write_metadata_atomic(path: Path, metadata: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f"{path.name}.tmp-{uuid.uuid4()}")
    try:
        with temporary.open("x", encoding="utf-8") as file:
            for key, value in metadata.items():
                if any(character in key + value for character in "\t\r\n"):
                    raise ValueError("Metadata keys and values cannot contain tabs or line breaks")
                file.write(f"{key}\t{value}\n")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


class ForwardSensitivityDataset:
    """Memory-mapped access to one exported Java dataset."""

    def __init__(self, directory: str | Path, *, verify_hashes: bool = True):
        self.directory = Path(directory).expanduser().resolve()
        self.verify_hashes = verify_hashes
        self._verified_steps: set[int] = set()
        self.manifest = read_metadata(self.directory / "manifest.tsv")
        if self.manifest.get("format") != "finmath-forward-sensitivity-exchange":
            raise ValueError(f"Unsupported dataset format in {self.directory}")
        if self.manifest.get("schema.version") != "1":
            raise ValueError(f"Unsupported schema version in {self.directory}")
        if self.manifest.get("dtype") != "float64":
            raise ValueError(f"Unsupported payload dtype in {self.directory}")
        if self.manifest.get("byte.order") != "little-endian":
            raise ValueError(f"Unsupported payload byte order in {self.directory}")
        if self.manifest.get("raw.layout") != "target,risk-factor,path":
            raise ValueError(f"Unsupported raw tensor layout in {self.directory}")
        if self.manifest.get("state.layout") != "risk-factor,path":
            raise ValueError(f"Unsupported state tensor layout in {self.directory}")

        self.dataset_id = self.manifest["dataset.id"]
        self.number_of_paths = int(self.manifest["number.paths"])
        self.number_of_targets = int(self.manifest["number.targets"])
        self.number_of_steps = int(self.manifest["number.steps"])

        complete = read_metadata(self.directory / "raw" / "_COMPLETE")
        if complete.get("dataset.id") != self.dataset_id:
            raise ValueError("The raw export is incomplete or belongs to an older dataset")
        if int(complete["number.steps"]) != self.number_of_steps:
            raise ValueError("The raw completion marker has the wrong number of steps")

    def step_metadata(self, step_index: int) -> dict[str, str]:
        metadata = read_metadata(self.directory / "metadata" / f"step-{step_index:05d}.tsv")
        if metadata.get("dataset.id") != self.dataset_id:
            raise ValueError(f"Step {step_index} belongs to a different dataset")
        if int(metadata["step.index"]) != step_index:
            raise ValueError(f"Step metadata index mismatch for step {step_index}")
        return metadata

    def open_step(self, step_index: int) -> tuple[np.memmap, np.memmap, dict[str, str]]:
        """Return ``(state, raw_sensitivities, metadata)`` as read-only memmaps."""

        metadata = self.step_metadata(step_index)
        number_of_risks = int(metadata["number.risk-factors"])
        if metadata.get("format") != self.manifest["format"]:
            raise ValueError(f"Step {step_index} has the wrong format")
        if metadata.get("schema.version") != self.manifest["schema.version"]:
            raise ValueError(f"Step {step_index} has the wrong schema version")
        if int(metadata["number.paths"]) != self.number_of_paths:
            raise ValueError(f"Step {step_index} has the wrong path count")
        if int(metadata["number.targets"]) != self.number_of_targets:
            raise ValueError(f"Step {step_index} has the wrong target count")
        raw_shape = (self.number_of_targets, number_of_risks, self.number_of_paths)
        state_shape = (number_of_risks, self.number_of_paths)

        raw_path = self.directory / "raw" / f"step-{step_index:05d}.f64"
        state_path = self.directory / "state" / f"step-{step_index:05d}.f64"
        expected_raw_bytes = int(np.prod(raw_shape)) * DTYPE.itemsize
        expected_state_bytes = int(np.prod(state_shape)) * DTYPE.itemsize
        if raw_path.stat().st_size != expected_raw_bytes:
            raise ValueError(f"Raw shard {raw_path} has the wrong size")
        if state_path.stat().st_size != expected_state_bytes:
            raise ValueError(f"State shard {state_path} has the wrong size")

        if self.verify_hashes and step_index not in self._verified_steps:
            if file_sha256(raw_path) != metadata.get("raw.sha256"):
                raise ValueError(f"Raw shard {raw_path} does not match its SHA-256 metadata")
            if file_sha256(state_path) != metadata.get("state.sha256"):
                raise ValueError(f"State shard {state_path} does not match its SHA-256 metadata")
            self._verified_steps.add(step_index)

        raw = np.memmap(
            raw_path,
            dtype=DTYPE,
            mode="r",
            shape=raw_shape,
            order="C",
        )
        state = np.memmap(
            state_path,
            dtype=DTYPE,
            mode="r",
            shape=state_shape,
            order="C",
        )
        return state, raw, metadata

    def write_projected(self, step_index: int, projected: np.ndarray) -> None:
        """Atomically write one projected tensor after shape/finite validation."""

        metadata = self.step_metadata(step_index)
        expected_shape = (
            self.number_of_targets,
            int(metadata["number.risk-factors"]),
            self.number_of_paths,
        )
        if projected.shape != expected_shape:
            raise ValueError(
                f"Projected step {step_index} has shape {projected.shape}; expected {expected_shape}"
            )
        destination = self.directory / "projected" / f"step-{step_index:05d}.f64"
        destination.parent.mkdir(parents=True, exist_ok=True)
        (destination.parent / "_COMPLETE").unlink(missing_ok=True)
        temporary = destination.with_name(f"{destination.name}.tmp-{uuid.uuid4()}")
        try:
            output = np.memmap(temporary, dtype=DTYPE, mode="w+", shape=expected_shape, order="C")
            try:
                output[:] = projected
                if not contains_only_finite_values(output):
                    raise ValueError(f"Projected step {step_index} contains NaN or infinity")
                output.flush()
            finally:
                del output
            os.replace(temporary, destination)
        finally:
            temporary.unlink(missing_ok=True)

    def mark_projected_complete(self) -> None:
        """Publish the completion marker only after every projected shard exists."""

        for step_index in range(self.number_of_steps):
            metadata = self.step_metadata(step_index)
            number_of_risks = int(metadata["number.risk-factors"])
            expected_bytes = (
                self.number_of_targets * number_of_risks * self.number_of_paths * DTYPE.itemsize
            )
            path = self.directory / "projected" / f"step-{step_index:05d}.f64"
            if not path.is_file() or path.stat().st_size != expected_bytes:
                raise ValueError(f"Missing or incorrectly sized projected shard {path}")

        write_metadata_atomic(
            self.directory / "projected" / "_COMPLETE",
            {
                "dataset.id": self.dataset_id,
                "number.steps": str(self.number_of_steps),
            },
        )


def project_sensitivities(
    state: np.ndarray, raw_sensitivities: np.ndarray, metadata: dict[str, str]
) -> np.ndarray:
    """Replace this identity map with the external Tensor Network estimator.

    The result must remain path-aligned and have the same shape as
    ``raw_sensitivities``. The estimator should return adapted projections of
    both the product and every hedge-instrument sensitivity.
    """

    del state, metadata
    return raw_sensitivities


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path, help="Dataset directory written by Java")
    parser.add_argument(
        "--identity",
        action="store_true",
        help="Copy raw sensitivities unchanged to verify the Java/Python round trip",
    )
    arguments = parser.parse_args()
    if not arguments.identity:
        parser.error("Pass --identity for a round-trip check, or call these utilities from your estimator")

    dataset = ForwardSensitivityDataset(arguments.directory)
    for step_index in range(dataset.number_of_steps):
        state, raw, metadata = dataset.open_step(step_index)
        projected = project_sensitivities(state, raw, metadata)
        dataset.write_projected(step_index, projected)
    dataset.mark_projected_complete()


if __name__ == "__main__":
    main()
