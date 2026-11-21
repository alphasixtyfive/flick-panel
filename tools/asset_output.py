"""Replace generated map assets only after a successful write."""

import os
import tempfile
from contextlib import contextmanager
from pathlib import Path


@contextmanager
def open_asset(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(dir=path.parent, prefix=f".{path.name}.", suffix=".tmp")
    staged = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            yield stream
        staged.replace(path)
    finally:
        staged.unlink(missing_ok=True)
