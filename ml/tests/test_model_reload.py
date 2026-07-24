import hashlib
import importlib.util
import json
import os
import tempfile
import unittest
import sys
from pathlib import Path


def load_app(root: Path):
    os.environ["MODEL_ARTIFACT_ROOT"] = str(root)
    os.environ["ML_INTERNAL_TOKEN"] = "test-token"
    spec = importlib.util.spec_from_file_location("reload_app", Path(__file__).parents[1] / "app.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ReloadValidationTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.app = load_app(self.root)
        self.client = self.app.app.test_client()

    def write_artifact(self, version="train-test", corrupt=False):
        directory = self.root / version
        directory.mkdir()
        content = b"not-a-torch-model"
        (directory / "model.pt").write_bytes(content)
        digest = hashlib.sha256(content).hexdigest()
        checksum = hashlib.sha256(f"model.pt\n{digest}\n".encode()).hexdigest()
        manifest = {"modelVersion": version, "modelType": "LSTM", "files": [{"path": "model.pt", "sha256": digest}], "artifactChecksum": checksum}
        (directory / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        if corrupt:
            (directory / "model.pt").write_bytes(b"changed")
        return checksum

    def test_rejects_invalid_artifact_without_replacing_current_bundle(self):
        checksum = self.write_artifact(corrupt=True)
        response = self.client.post("/internal/models/reload", headers={"X-Internal-Token": "test-token"}, json={"modelVersion": "train-test", "artifactDir": "train-test", "artifactChecksum": checksum, "requestId": "one"})
        self.assertEqual(422, response.status_code)
        self.assertEqual("CHECKSUM_VERIFY", response.get_json()["stage"])
        self.assertIsNone(self.app._current_bundle)

    def test_rejects_path_traversal(self):
        response = self.client.post("/internal/models/reload", headers={"X-Internal-Token": "test-token"}, json={"modelVersion": "../outside", "artifactDir": "../outside", "artifactChecksum": "x", "requestId": "two"})
        self.assertEqual(422, response.status_code)
        self.assertEqual("PATH_VALIDATION", response.get_json()["stage"])


if __name__ == "__main__":
    unittest.main()
