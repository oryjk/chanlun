import ast
from pathlib import Path, PurePosixPath
import unittest


ROOT = Path(__file__).resolve().parents[1]


FORBIDDEN_IMPORTS: dict[str, tuple[str, ...]] = {
    "backend.domain": (
        "backend.application",
        "backend.infrastructure",
        "backend.interfaces",
        "backend.app",
        "fastapi",
        "pydantic",
        "sqlite3",
        "urllib",
        "http",
        "socket",
        "threading",
        "concurrent",
    ),
    "backend.application": (
        "backend.infrastructure",
        "backend.interfaces",
        "backend.app",
        "fastapi",
        "pydantic",
        "sqlite3",
        "urllib",
        "http",
        "socket",
    ),
    "backend.infrastructure": (
        "backend.interfaces",
        "backend.app",
    ),
    "backend.interfaces": (
        "backend.infrastructure",
        "backend.app",
    ),
}


def _is_forbidden(module: str, forbidden_prefixes: tuple[str, ...]) -> bool:
    return any(
        module == prefix or module.startswith(f"{prefix}.")
        for prefix in forbidden_prefixes
    )


def _resolve_import_from(package_parts: list[str], node: ast.ImportFrom) -> list[str]:
    if node.level > 0:
        up = node.level - 1
        base = package_parts[: len(package_parts) - up] if up <= len(package_parts) else []
    else:
        base = []
    if node.module:
        resolved = ".".join([*base, node.module])
        return [resolved]
    return [".".join([*base, alias.name]) for alias in node.names]


def find_violations(sources: dict[str, str]) -> list[str]:
    violations: list[str] = []
    for path in sorted(sources):
        posix_path = PurePosixPath(path)
        parts = posix_path.parts
        if len(parts) < 2 or parts[0] != "backend":
            continue
        layer = f"backend.{parts[1]}"
        forbidden = FORBIDDEN_IMPORTS.get(layer)
        if not forbidden:
            continue
        package_parts = list(posix_path.parent.parts)
        tree = ast.parse(sources[path], filename=path)
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    if _is_forbidden(alias.name, forbidden):
                        violations.append(f"{path}:{node.lineno} imports {alias.name}")
            elif isinstance(node, ast.ImportFrom):
                for module in _resolve_import_from(package_parts, node):
                    if _is_forbidden(module, forbidden):
                        violations.append(f"{path}:{node.lineno} imports {module}")
                    elif node.module:
                        for alias in node.names:
                            candidate = f"{module}.{alias.name}"
                            if _is_forbidden(candidate, forbidden):
                                violations.append(f"{path}:{node.lineno} imports {candidate}")
    return violations


class ArchitectureGuardTests(unittest.TestCase):
    def test_guard_detects_relative_import_violation(self):
        sources = {"backend/domain/services/x.py": "from ...infrastructure.http.json_client import JsonHttpClient\n"}
        self.assertEqual(find_violations(sources),
                         ["backend/domain/services/x.py:1 imports backend.infrastructure.http.json_client"])

    def test_guard_allows_inward_imports(self):
        sources = {"backend/application/queries/q.py": "from ...domain.models import Stock\nfrom ..ports.readers import X\n"}
        self.assertEqual(find_violations(sources), [])

    def test_layer_dependencies_point_inward(self):
        sources = {
            p.relative_to(ROOT).as_posix(): p.read_text(encoding="utf-8")
            for p in (ROOT / "backend").rglob("*.py")
        }
        self.assertGreater(len(sources), 0)
        self.assertEqual(find_violations(sources), [])


if __name__ == "__main__":
    unittest.main()
