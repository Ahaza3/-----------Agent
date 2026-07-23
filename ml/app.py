"""Flask inference service with verified, atomic model bundle reloads."""

from __future__ import annotations

import hashlib
import hmac
import json
import math
import os
import pickle
import re
import threading
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from flask import Flask, jsonify, request

app = Flask(__name__)

MODEL_ROOT = Path(os.environ.get("MODEL_ARTIFACT_ROOT", "models")).resolve()
INITIAL_VERSION = os.environ.get("MODEL_VERSION", "").strip()
INTERNAL_TOKEN = os.environ.get("ML_INTERNAL_TOKEN", "")
VERSION_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
_reload_lock = threading.RLock()
_current_bundle = None
_last_load_error = None
_reload_results: dict[str, dict] = {}


@dataclass(frozen=True)
class ModelBundle:
    model_version: str
    artifact_checksum: str
    loaded_at: str
    model: object
    model_type: str
    scaler_x: object
    scaler_y: object
    weather_scaler: object
    feature_cols: list[str]
    seq_length: int
    forecast_horizon: int
    future_weather_features: int
    source_directory: Path


class ReloadFailure(Exception):
    def __init__(self, stage: str, message: str):
        self.stage = stage
        self.message = message
        super().__init__(message)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _artifact_checksum(entries: list[tuple[str, str]]) -> str:
    content = "".join(f"{relative}\n{checksum}\n" for relative, checksum in sorted(entries))
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def _resolve_artifact_dir(artifact_dir: str) -> Path:
    if not isinstance(artifact_dir, str) or not VERSION_PATTERN.fullmatch(artifact_dir):
        raise ReloadFailure("PATH_VALIDATION", "invalid artifact directory")
    directory = (MODEL_ROOT / artifact_dir).resolve()
    if MODEL_ROOT not in directory.parents or not directory.is_dir():
        raise ReloadFailure("PATH_VALIDATION", "artifact directory is unavailable")
    return directory


def _read_and_verify_manifest(artifact_dir: str, expected_checksum: str) -> tuple[Path, dict]:
    directory = _resolve_artifact_dir(artifact_dir)
    manifest_path = directory / "manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        raise ReloadFailure("MANIFEST_READ", "manifest is unavailable")
    if manifest.get("modelVersion") != artifact_dir or not isinstance(manifest.get("files"), list):
        raise ReloadFailure("MANIFEST_READ", "manifest identity is invalid")
    entries: list[tuple[str, str]] = []
    for entry in manifest["files"]:
        relative = entry.get("path") if isinstance(entry, dict) else None
        expected_file_checksum = entry.get("sha256") if isinstance(entry, dict) else None
        if (not isinstance(relative, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._/-]{0,255}", relative)
                or ".." in relative or not isinstance(expected_file_checksum, str) or len(expected_file_checksum) != 64):
            raise ReloadFailure("MANIFEST_READ", "manifest file entry is invalid")
        file_path = (directory / relative).resolve()
        if directory not in file_path.parents or not file_path.is_file():
            raise ReloadFailure("CHECKSUM_VERIFY", "artifact file is missing")
        actual_file_checksum = _sha256_file(file_path)
        if not hmac.compare_digest(actual_file_checksum.lower(), expected_file_checksum.lower()):
            raise ReloadFailure("CHECKSUM_VERIFY", "artifact file checksum mismatch")
        entries.append((relative, actual_file_checksum))
    actual_paths = sorted(path.relative_to(directory).as_posix() for path in directory.rglob("*")
                          if path.is_file() and path.name != "manifest.json")
    if actual_paths != [relative for relative, _ in sorted(entries)]:
        raise ReloadFailure("MANIFEST_READ", "manifest does not cover all artifact files")
    checksum = _artifact_checksum(entries)
    if not hmac.compare_digest(checksum.lower(), str(manifest.get("artifactChecksum", "")).lower()):
        raise ReloadFailure("CHECKSUM_VERIFY", "artifact checksum mismatch")
    if expected_checksum and not hmac.compare_digest(checksum.lower(), expected_checksum.lower()):
        raise ReloadFailure("CHECKSUM_VERIFY", "requested checksum mismatch")
    return directory, manifest


def _load_candidate(artifact_dir: str, expected_checksum: str) -> ModelBundle:
    directory, manifest = _read_and_verify_manifest(artifact_dir, expected_checksum)
    try:
        import torch

        required = ["model.pt", "meta.pt", "scaler_x.pkl", "scaler_y.pkl", "weather_scaler.pkl", "feature_cols.pkl"]
        if any(not (directory / name).is_file() for name in required):
            raise ReloadFailure("MODEL_LOAD", "required LSTM artifact file is missing")
        model = torch.jit.load(str(directory / "model.pt"), map_location="cpu")
        model.eval()
        meta = torch.load(directory / "meta.pt", weights_only=True, map_location="cpu")
        with (directory / "scaler_x.pkl").open("rb") as handle:
            scaler_x = pickle.load(handle)
        with (directory / "scaler_y.pkl").open("rb") as handle:
            scaler_y = pickle.load(handle)
        with (directory / "weather_scaler.pkl").open("rb") as handle:
            weather_scaler = pickle.load(handle)
        with (directory / "feature_cols.pkl").open("rb") as handle:
            feature_cols = pickle.load(handle)
        bundle = ModelBundle(
            model_version=artifact_dir,
            artifact_checksum=str(manifest["artifactChecksum"]),
            loaded_at=datetime.now(timezone.utc).isoformat(),
            model=model,
            model_type="torchscript",
            scaler_x=scaler_x,
            scaler_y=scaler_y,
            weather_scaler=weather_scaler,
            feature_cols=list(feature_cols),
            seq_length=int(meta.get("seq_length", 168)),
            forecast_horizon=int(meta.get("forecast_horizon", 24)),
            future_weather_features=int(meta.get("future_weather_features", 0)),
            source_directory=directory,
        )
    except ReloadFailure:
        raise
    except Exception:
        raise ReloadFailure("MODEL_LOAD", "model bundle cannot be loaded")
    _smoke_inference(bundle)
    return bundle


def _run_model(bundle: ModelBundle, tensor, weather_tensor):
    import torch

    with torch.no_grad():
        try:
            return bundle.model(tensor, weather_tensor)
        except RuntimeError:
            return bundle.model(tensor)


def _smoke_inference(bundle: ModelBundle) -> None:
    try:
        import torch
        import numpy as np

        feature_count = len(bundle.feature_cols)
        tensor = torch.zeros((1, bundle.seq_length, feature_count), dtype=torch.float32)
        weather = torch.zeros((1, bundle.forecast_horizon, bundle.future_weather_features), dtype=torch.float32)
        output = _run_model(bundle, tensor, weather)
        if not np.isfinite(output.detach().cpu().numpy()).all():
            raise ValueError("non-finite output")
    except Exception:
        raise ReloadFailure("SMOKE_INFERENCE", "candidate model smoke inference failed")


def _load_legacy_bundle() -> ModelBundle | None:
    """Read-only compatibility for the pre-version-directory artifacts."""
    try:
        import torch

        if not (MODEL_ROOT / "lstm_scripted.pt").is_file():
            return None
        model = torch.jit.load(str(MODEL_ROOT / "lstm_scripted.pt"), map_location="cpu")
        model.eval()
        meta = torch.load(MODEL_ROOT / "lstm_meta.pt", weights_only=True, map_location="cpu")
        with (MODEL_ROOT / "lstm_scaler_x.pkl").open("rb") as handle: scaler_x = pickle.load(handle)
        with (MODEL_ROOT / "lstm_scaler.pkl").open("rb") as handle: scaler_y = pickle.load(handle)
        with (MODEL_ROOT / "lstm_weather_scaler.pkl").open("rb") as handle: weather_scaler = pickle.load(handle)
        with (MODEL_ROOT / "lstm_feature_cols.pkl").open("rb") as handle: feature_cols = pickle.load(handle)
        return ModelBundle("LEGACY_UNVERIFIED", "", datetime.now(timezone.utc).isoformat(), model,
                           "torchscript", scaler_x, scaler_y, weather_scaler, list(feature_cols),
                           int(meta.get("seq_length", 168)), int(meta.get("forecast_horizon", 24)),
                           int(meta.get("future_weather_features", 0)), MODEL_ROOT)
    except Exception:
        return None


def _engineer_features(raw_rows: list[dict], bundle: ModelBundle) -> np.ndarray:
    import pandas as pd
    import numpy as np

    df = pd.DataFrame(raw_rows)
    df["time"] = pd.to_datetime(df["time"])
    df = df.sort_values("time").reset_index(drop=True)
    df["hour_sin"] = np.sin(2 * math.pi * df["hour"] / 24)
    df["hour_cos"] = np.cos(2 * math.pi * df["hour"] / 24)
    df["dow_sin"] = np.sin(2 * math.pi * df["day_of_week"] / 7)
    df["dow_cos"] = np.cos(2 * math.pi * df["day_of_week"] / 7)
    df["month_sin"] = np.sin(2 * math.pi * df["month"] / 12)
    df["month_cos"] = np.cos(2 * math.pi * df["month"] / 12)
    df["load_lag_1"] = df["load_mw"].shift(1)
    df["load_lag_24"] = df["load_mw"].shift(24)
    df["load_lag_168"] = df["load_mw"].shift(168)
    df["temp_lag_1"] = df["temperature"].shift(1)
    df["temp_lag_24"] = df["temperature"].shift(24)
    df["load_roll_mean_24"] = df["load_mw"].rolling(24, min_periods=1).mean()
    df["load_roll_std_24"] = df["load_mw"].rolling(24, min_periods=1).std().fillna(0)
    for col in bundle.feature_cols:
        if col not in df.columns: df[col] = 0
        df[col] = df[col].bfill().ffill().fillna(0)
    return df[bundle.feature_cols].values.astype(np.float32)


def _future_weather_tensor(raw_rows: list[dict], rows: list[dict], bundle: ModelBundle):
    import torch
    import numpy as np

    last_row = raw_rows[-1] if raw_rows else {}
    fallback = np.array([float(last_row.get("temperature") or 0), float(last_row.get("humidity") or 0)], dtype=np.float32)
    values = np.zeros((bundle.forecast_horizon, bundle.future_weather_features), dtype=np.float32)
    used_fallback = len(rows) < bundle.forecast_horizon
    has_real_weather = False
    for index in range(bundle.forecast_horizon):
        source = rows[index] if index < len(rows) else {}
        try: value = [float(source["temperature"]), float(source["humidity"])]
        except (KeyError, TypeError, ValueError): value, used_fallback = fallback, True
        else: has_real_weather = True
        values[index] = value
    if bundle.weather_scaler is None:
        values, used_fallback = np.zeros_like(values), True
    else:
        values = bundle.weather_scaler.transform(values).astype(np.float32)
    return torch.from_numpy(values).unsqueeze(0), bool(has_real_weather and bundle.weather_scaler is not None), used_fallback


@app.post("/predict/forecast")
def predict_forecast():
    data = request.get_json(silent=True) or {}
    raw_rows = data.get("data")
    bundle = _current_bundle  # one immutable snapshot for this complete request
    if not isinstance(raw_rows, list): return jsonify({"error": "missing data"}), 400
    if bundle is None:
        return jsonify({"predictions": [1000.0] * 24, "model": "placeholder", "future_weather_applied": False, "future_weather_fallback": False})
    if len(raw_rows) < bundle.seq_length:
        return jsonify({"error": f"at least {bundle.seq_length} rows are required"}), 400
    try:
        import torch
        import numpy as np

        features = _engineer_features(raw_rows, bundle)[-bundle.seq_length:]
        if bundle.scaler_x is not None: features = bundle.scaler_x.transform(features)
        tensor = torch.from_numpy(features.astype(np.float32)).unsqueeze(0)
        weather, applied, fallback = _future_weather_tensor(raw_rows, data.get("future_weather", []), bundle)
        scaled = _run_model(bundle, tensor, weather).squeeze().detach().cpu().numpy()
        values = bundle.scaler_y.inverse_transform(scaled.reshape(-1, 1)).flatten().tolist() if bundle.scaler_y is not None else scaled.tolist()
        return jsonify({"predictions": [round(float(value), 1) for value in values[:bundle.forecast_horizon]], "model": bundle.model_type,
                        "future_weather_received": len(data.get("future_weather", [])), "future_weather_applied": applied,
                        "future_weather_fallback": fallback})
    except Exception:
        return jsonify({"error": "model inference failed"}), 500


@app.post("/internal/models/reload")
def reload_model():
    global _current_bundle, _last_load_error
    if not INTERNAL_TOKEN or not hmac.compare_digest(request.headers.get("X-Internal-Token", ""), INTERNAL_TOKEN):
        return jsonify({"success": False, "stage": "PATH_VALIDATION", "message": "internal authorization failed"}), 403
    payload = request.get_json(silent=True) or {}
    request_id = payload.get("requestId")
    if not isinstance(request_id, str) or not request_id.strip():
        return jsonify({"success": False, "stage": "PATH_VALIDATION", "message": "requestId is required"}), 400
    with _reload_lock:
        if request_id in _reload_results:
            return jsonify(_reload_results[request_id])
        version, artifact_dir, checksum = payload.get("modelVersion"), payload.get("artifactDir"), payload.get("artifactChecksum")
        if artifact_dir != version:
            result = {"success": False, "modelVersion": version or "", "artifactChecksum": checksum or "", "loadedAt": "", "stage": "PATH_VALIDATION", "message": "artifact directory does not match model version"}
            _reload_results[request_id] = result
            return jsonify(result), 422
        if _current_bundle and version == _current_bundle.model_version and checksum == _current_bundle.artifact_checksum:
            result = {"success": True, "modelVersion": version, "artifactChecksum": checksum, "loadedAt": _current_bundle.loaded_at, "stage": "ATOMIC_SWAP", "message": "already active"}
            _reload_results[request_id] = result
            return jsonify(result)
        try:
            candidate = _load_candidate(artifact_dir, checksum)
            _current_bundle = candidate
            _last_load_error = None
            result = {"success": True, "modelVersion": candidate.model_version, "artifactChecksum": candidate.artifact_checksum,
                      "loadedAt": candidate.loaded_at, "stage": "ATOMIC_SWAP", "message": "loaded"}
        except ReloadFailure as error:
            _last_load_error = f"{error.stage}: {error.message}"
            result = {"success": False, "modelVersion": version or "", "artifactChecksum": checksum or "", "loadedAt": "",
                      "stage": error.stage, "message": error.message}
        _reload_results[request_id] = result
        if len(_reload_results) > 200: _reload_results.pop(next(iter(_reload_results)))
        return jsonify(result), (200 if result["success"] else 422)


@app.get("/health")
def health():
    bundle = _current_bundle
    return jsonify({"status": "ok" if bundle else "degraded", "model_loaded": bundle is not None,
                    "model_type": bundle.model_type if bundle else None, "modelVersion": bundle.model_version if bundle else None,
                    "artifactChecksum": bundle.artifact_checksum if bundle else None, "loadedAt": bundle.loaded_at if bundle else None,
                    "runtimeStatus": "ACTIVE" if bundle and bundle.model_version != "LEGACY_UNVERIFIED" else "LEGACY_UNVERIFIED" if bundle else "LOAD_FAILED",
                    "lastLoadError": _last_load_error, "future_weather_supported": bool(bundle and bundle.future_weather_features > 0),
                    "future_weather_features": bundle.future_weather_features if bundle else 0,
                    "forecast_horizon": bundle.forecast_horizon if bundle else 24})


if INITIAL_VERSION:
    try:
        _current_bundle = _load_candidate(INITIAL_VERSION, "")
    except ReloadFailure as failure:
        _last_load_error = f"{failure.stage}: {failure.message}"
else:
    _current_bundle = _load_legacy_bundle()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
