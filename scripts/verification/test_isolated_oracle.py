"""Pure harness checks: no Docker process and no database connection."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import json
import subprocess
import tempfile
import secrets
import re

spec = importlib.util.spec_from_file_location("isolated_oracle", Path(__file__).with_name("isolated_oracle.py"))
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)
RUN = "c" * 32
CONTAINER = "a" * 64


class ContainerGuardsTest(unittest.TestCase):
    def metadata(self):
        return {"Id": CONTAINER, "Name": "/saed-isolated-" + RUN[:12], "Image": harness.IMAGE_REF, "Labels": {harness.LABEL: RUN, harness.SCOPE_LABEL: harness.SCOPE}, "Running": True, "Ports": {"1521/tcp": [{"HostIp": "127.0.0.1", "HostPort": "11521"}]}, "Mounts": []}

    def check(self, metadata):
        result = subprocess.CompletedProcess([], 0, json.dumps(metadata), "")
        with patch.object(harness.subprocess, "run", return_value=result):
            return harness.inspect_container(CONTAINER, RUN)

    def test_accepts_exact_isolated_container(self):
        self.assertEqual(self.check(self.metadata())["Id"], "a" * 64)

    def test_rejects_existing_oracle_port(self):
        data = self.metadata()
        data["Ports"]["1521/tcp"][0]["HostPort"] = "1521"
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_external_interface(self):
        data = self.metadata()
        data["Ports"]["1521/tcp"][0]["HostIp"] = "0.0.0.0"
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_host_mount(self):
        data = self.metadata()
        data["Mounts"] = [{"Type": "bind"}]
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_wrong_label(self):
        data = self.metadata()
        data["Labels"][harness.LABEL] = "d" * 32
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_mutable_image(self):
        data = self.metadata()
        data["Image"] = "gvenzl/oracle-xe:latest"
        with self.assertRaises(ValueError):
            self.check(data)

    def test_section_omits_heading_suffix(self):
        self.assertEqual(harness.section("-- START (detail)\nselect 1;\n-- END (detail)\n", "-- START", "-- END"), "select 1;\n")

    def test_rejects_external_volume(self):
        data = self.metadata()
        data["Mounts"] = [{"Type": "volume"}]
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_additional_published_port(self):
        data = self.metadata()
        data["Ports"]["8080/tcp"] = [{"HostIp": "127.0.0.1", "HostPort": "8080"}]
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_wrong_scope(self):
        data = self.metadata()
        data["Labels"][harness.SCOPE_LABEL] = "production"
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_image_substring_trick(self):
        data = self.metadata()
        data["Image"] = harness.IMAGE_REF + "unexpected"
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_name_prefix_only(self):
        data = self.metadata()
        data["Name"] = "/saed-isolated-unrelated"
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_stopped_container(self):
        data = self.metadata()
        data["Running"] = False
        with self.assertRaises(ValueError):
            self.check(data)

    def test_rejects_short_identity_before_process(self):
        with patch.object(harness.subprocess, "run") as process:
            with self.assertRaises(ValueError):
                harness.inspect_container("a" * 12, RUN)
            process.assert_not_called()


class SourcePreparationTest(unittest.TestCase):
    def setUp(self):
        for mocker in (patch.object(harness, "STATE", "recovery-01"), patch.object(harness, "check_state_path")):
            mocker.start()
            self.addCleanup(mocker.stop)

    def prepared_manifest(self, directory):
        parts, sources, transformations = harness.extract_sources()
        hashes = ["$2b$12$" + "".join(secrets.choice("./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789") for _ in range(53)) for _ in range(11)]
        parts["030-synthetic-fixture.sql"] = harness.fixture_sql(harness.fixture_accounts(), hashes)
        parts["031-mixed-assignment.sql"] = harness.mixed_assignment_sql()
        manifest = {"format": 1, "state": harness.STATE, "status": "prepared-not-applied", "image": harness.IMAGE_REF, "port": harness.PORT, "pdb": harness.PDB, "label_key": harness.LABEL, "scope_label": harness.SCOPE_LABEL, "scope": harness.SCOPE, "files": {}, "sources": sources, "transformations": transformations}
        for name, content in parts.items():
            (directory / name).write_text(content, encoding="utf-8")
            manifest["files"][name] = harness.digest(content)
        return manifest

    def verifies(self, modify=None):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            manifest = self.prepared_manifest(directory)
            if modify:
                modify(manifest, directory)
            (directory / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            with patch.object(harness, "WORK", directory), patch.object(harness, "protect_windows_state", return_value=subprocess.CompletedProcess([], 0, "", "")):
                return harness.verify_prepared()

    def test_accepts_complete_prepared_inventory(self):
        self.assertEqual(len(self.verifies()["files"]), 37)

    def test_rejects_non_object_manifest_without_attribute_error(self):
        for value in (None, [], "invalid", 1):
            with tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                (directory / "manifest.json").write_text(json.dumps(value), encoding="utf-8")
                with patch.object(harness, "WORK", directory), patch.object(harness, "protect_windows_state", return_value=subprocess.CompletedProcess([], 0, "", "")):
                    with self.assertRaisesRegex(ValueError, "must be an object"):
                        harness.verify_prepared()

    def test_rejects_empty_artifact_inventory(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest.update(files={}))

    def test_rejects_missing_artifact(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest["files"].pop("010-structure.sql"))

    def test_rejects_extra_artifact(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest["files"].update({"999-extra.sql": "a" * 64}))

    def test_rejects_empty_source_inventory(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest.update(sources=[]))

    def test_rejects_missing_source(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest["sources"].pop())

    def test_rejects_extra_source(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest["sources"].append({"file": "database/migrations/V5.30__purge_demo_seed_users.sql", "sha256": "a" * 64}))

    def test_rejects_tampered_source_path(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest["sources"][0].update(file="../../outside.sql"))

    def test_rejects_tampered_source_digest(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest["sources"][0].update(sha256="a" * 64))

    def test_rejects_invalid_format_version(self):
        for version in (None, True, "1", 0, 2):
            with self.subTest(version=version), self.assertRaises(ValueError):
                self.verifies(lambda manifest, _, version=version: manifest.update(format=version))

    def test_rejects_missing_or_different_state_selector(self):
        for state in (None, "recovery-02"):
            with self.subTest(state=state), self.assertRaises(ValueError):
                self.verifies(lambda manifest, _, state=state: manifest.update(state=state))

    def test_rejects_malformed_artifact_digest(self):
        with self.assertRaises(ValueError):
            self.verifies(lambda manifest, _: manifest["files"].update({"010-structure.sql": "invalid"}))

    def test_rejects_artifact_edit_even_when_manifest_hash_is_updated(self):
        def edit(manifest, directory):
            content = "SELECT 1 FROM DUAL;\n"
            (directory / "010-structure.sql").write_text(content, encoding="utf-8")
            manifest["files"]["010-structure.sql"] = harness.digest(content)
        with self.assertRaises(ValueError):
            self.verifies(edit)

    def test_rejects_empty_fixture_even_when_manifest_hash_is_updated(self):
        def edit(manifest, directory):
            (directory / "030-synthetic-fixture.sql").write_text("", encoding="utf-8")
            manifest["files"]["030-synthetic-fixture.sql"] = harness.digest("")
        with self.assertRaises(ValueError):
            self.verifies(edit)

    def test_section_rejects_duplicate_boundary(self):
        with self.assertRaises(ValueError):
            harness.section("-- START\na\n-- START\nb\n-- END\n", "-- START", "-- END")

    def test_normalization_requires_exactly_one_match(self):
        for text in ("missing", "duplicate duplicate"):
            with self.assertRaises(ValueError):
                harness.replace_once(text, "duplicate", "correct", "test")

    def test_sqlplus_settings_removed_without_changing_update(self):
        text = "SET PAGESIZE 0 FEEDBACK ON\nUPDATE A\n SET ESTADO='ACTIVO';\nEXIT;\n"
        sanitized = harness.sanitize_source(text)
        self.assertNotIn("PAGESIZE", sanitized)
        self.assertIn(" SET ESTADO='ACTIVO';", sanitized)
        self.assertNotIn("EXIT", sanitized)

    def test_extraction_excludes_identity_and_purge(self):
        parts, sources, transformations = harness.extract_sources()
        self.assertEqual(len(sources), 30)
        self.assertEqual(len(parts), 35)
        self.assertGreaterEqual(len(transformations), 7)
        text = "\n".join(parts.values())
        self.assertNotIn("DROP USER", text)
        self.assertNotIn("IDENTIFIED BY", text)
        self.assertNotIn("MERGE INTO USUARIOS", text)
        self.assertNotIn("V5.30", "\n".join(parts))
        self.assertNotIn("SAED_APP", parts["103-V5.3__decouple_audit_propiedades_deadlock.sql"])
        self.assertIn('ALTER TRIGGER "TRG_HISTREP_INMUTABLE" COMPILE;', parts["010-structure.sql"])
        self.assertIn("'RESIDENTE_CONVIVENCIA'", parts["010-structure.sql"])

    def test_migrations_never_add_columns_with_non_constant_defaults(self):
        # recovery-05: V5.20 ADD (... DEFAULT CURRENT_TIMESTAMP) needs a full-table rewrite; active VPD raised ORA-28133.
        parts, _, transformations = harness.extract_sources()
        offending = re.compile(r"ADD\s*\([^;']*DEFAULT\s+(CURRENT_TIMESTAMP|SYSTIMESTAMP|SYSDATE|CURRENT_DATE|SYS_GUID)", re.IGNORECASE)
        for name, text in parts.items():
            self.assertIsNone(offending.search(text), name)
        v520 = parts["120-V5.20__trabajadores_arl_obras_authorization.sql"]
        self.assertIn("ALTER TABLE OBRA_TRABAJADORES ADD (FECHA_AUTORIZACION TIMESTAMP(6) WITH TIME ZONE)", v520)
        self.assertIn("ALTER TABLE OBRA_TRABAJADORES MODIFY (FECHA_AUTORIZACION DEFAULT CURRENT_TIMESTAMP)", v520)
        self.assertTrue(any("V5.20" in item for item in transformations))

    def test_structure_allows_every_fixture_resident_type(self):
        # Stage 030 runs before V5.9 extends CK_RESIDUNIDAD_TIPO; recovery-03 failed with ORA-02290 on CONVIVIENTE.
        parts, _, _ = harness.extract_sources()
        check = re.search(r'"CK_RESIDUNIDAD_TIPO" CHECK \(tipo_residente IN\s*\(([^)]*)\)\)', parts["010-structure.sql"])
        self.assertIsNotNone(check)
        allowed = set(re.findall(r"'([A-Z_]+)'", check.group(1)))
        accounts = harness.fixture_accounts()
        fixture = harness.fixture_sql(accounts, ["$2b$12$" + "a" * 53] * len(accounts))
        used = set(re.findall(r"INSERT INTO RESIDENTES_UNIDAD \([^)]*\) VALUES \([^,]+,[^,]+,[^,]+,'([A-Z_]+)'", fixture))
        self.assertTrue(used)
        self.assertLessEqual(used, allowed)

    def test_authentication_synonyms_use_explicit_owner(self):
        parts, _, _ = harness.extract_sources()
        synonyms = parts["050-auth-synonyms.sql"]
        self.assertIn("FOR SAED_ISO_OWNER.USUARIOS", synonyms)
        self.assertNotIn("SYNONYM SAED_SEC_MASTER.", synonyms)
        self.assertNotIn("|| USER ||", synonyms)
        self.assertNotIn("CREATE OR REPLACE PACKAGE SAED_SEC_MASTER.", parts["060-auth-package.sql"])

    def test_fixture_has_six_roles_and_two_organizations(self):
        accounts = harness.fixture_accounts()
        self.assertEqual(len(accounts), 11)
        self.assertEqual({account["role"] for account in accounts}, set(harness.ROLES))
        self.assertEqual({account["organization"] for account in accounts if account["organization"]}, {91001, 92001})
        self.assertEqual(len({account["password"] for account in accounts}), 11)
        self.assertTrue(all(account["email"].endswith("@example.invalid") for account in accounts))
        with self.assertRaises(ValueError):
            harness.fixture_sql(accounts, [])

    def test_hash_failure_never_emits_private_output(self):
        with patch.object(harness.Path, "is_file", return_value=True), patch.object(harness.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "PRIVATE_INPUT", "PRIVATE_EXCEPTION")):
            with self.assertRaisesRegex(ValueError, "private output suppressed") as captured:
                harness.generate_hashes(["b" * 48])
        self.assertNotIn("PRIVATE_INPUT", str(captured.exception))
        self.assertNotIn("PRIVATE_EXCEPTION", str(captured.exception))

    def test_mixed_role_assignment_preserves_existing_identity(self):
        fixture = harness.mixed_assignment_sql()
        self.assertIn("(93001,91005,2,91001,91021,NULL,'ACTIVA')", fixture)
        self.assertNotIn("USUARIOS (", fixture)
        self.assertNotIn("HASH_PASSWORD", fixture)

    def test_no_database_execution_surface_exposed(self):
        self.assertFalse(hasattr(harness, "sql"))

    def test_isolated_configuration_does_not_import_application_environment(self):
        config = (harness.ROOT / "backend/src/test/resources/application-isolated.yml").read_text(encoding="utf-8-sig")
        self.assertIn("jdbc:oracle:thin:@//127.0.0.1:11521/XEPDB1", config)
        self.assertIn("username: SAED_ISO_RUNTIME", config)
        self.assertIn("CURRENT_SCHEMA=SAED_ISO_OWNER", config)
        self.assertNotIn("config.import", config)
        self.assertNotIn("${DB_URL", config)
        self.assertNotIn("${DB_PASSWORD", config)

    def test_sqlplus_update_assignments_and_loop_exit_are_preserved(self):
        content = "BEGIN\n UPDATE X\n SET ESTADO='ACTIVO';\n LOOP\n EXIT;\n END LOOP;\nEND;\n/\nEXIT;\n"
        result = harness.sanitize_source(content)
        self.assertIn(" SET ESTADO='ACTIVO';", result)
        self.assertEqual(result.count("EXIT;"), 1)


class RecoveryStateTest(unittest.TestCase):
    def test_state_selector_rejects_traversal_absolute_and_reserved_paths(self):
        for state in (None, "", "..", "../recovery-01", "recovery-01/other", r"C:\outside", r"..\recovery-01", "CON", "recovery-01.", "recovery-01:stream", "Recovery-01"):
            with self.subTest(state=state), self.assertRaises(ValueError):
                harness.state_directory(state)

    def test_selected_successor_is_inside_fixed_root_and_legacy_is_unchanged(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(harness, "ROOT", Path(temporary)), patch.object(harness, "WORK"), patch.object(harness, "STATE", None):
            legacy = Path(temporary) / "local/isolated-oracle"
            legacy.mkdir(parents=True)
            for name in ("private-state.json", "manifest.json", "execution-state.json", "installation.lock"):
                (legacy / name).write_text("preserved failure", encoding="utf-8")
            before = {path.name: path.read_bytes() for path in legacy.iterdir()}
            harness.select_state("recovery-01")
            self.assertEqual(harness.WORK, legacy / "recovery-01")
            self.assertEqual(harness.STATE, "recovery-01")
            self.assertFalse(harness.WORK.exists())
            harness.check_state_path()
            self.assertEqual(before, {path.name: path.read_bytes() for path in legacy.iterdir()})

    def test_reparse_or_junction_at_any_ancestor_is_rejected(self):
        target = harness.ROOT / "local/isolated-oracle/recovery-01"
        for linked in (target, target.parent, harness.ROOT, harness.ROOT.parent):
            with self.subTest(location=linked.name), patch.object(harness.Path, "is_junction", lambda path: path == linked), self.assertRaises(ValueError):
                harness.state_directory("recovery-01")
        with patch.object(harness.Path, "is_symlink", lambda path: path == target.parent), self.assertRaises(ValueError):
            harness.state_directory("recovery-01")

    def test_mismatched_work_path_fails(self):
        with patch.object(harness, "STATE", "recovery-01"), patch.object(harness, "WORK", harness.ROOT / "local/outside"), self.assertRaises(ValueError):
            harness.check_state_path()

    def test_missing_explicit_state_fails(self):
        with patch.object(harness, "STATE", None), self.assertRaises(ValueError):
            harness.check_state_path()

    def test_prepare_refuses_existing_state_before_generating_secrets(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(harness, "ROOT", Path(temporary)), patch.object(harness, "WORK"), patch.object(harness, "STATE", None):
            harness.select_state("recovery-01")
            harness.WORK.mkdir(parents=True)
            with patch.object(harness, "extract_sources", return_value=({}, [], [])), patch.object(harness, "fixture_accounts") as fixture, patch.object(harness.secrets, "token_hex") as generated:
                with self.assertRaisesRegex(ValueError, "already exists"):
                    harness.prepare()
                fixture.assert_not_called()
                generated.assert_not_called()

    def test_mock_prepare_uses_30_ascii_passwords_and_independent_48_marker(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(harness, "ROOT", Path(temporary)), patch.object(harness, "WORK"), patch.object(harness, "STATE", None):
            legacy = Path(temporary) / "local/isolated-oracle"
            legacy.mkdir(parents=True)
            (legacy / "installation.lock").write_text("preserve", encoding="utf-8")
            (legacy / "execution-state.json").write_text('{"status":"blocked"}', encoding="utf-8")
            before = {path.name: path.read_bytes() for path in legacy.iterdir()}
            harness.select_state("recovery-01")
            success = subprocess.CompletedProcess([], 0, "", "")
            with patch.object(harness, "extract_sources", return_value=({}, [], [])), patch.object(harness, "fixture_accounts", return_value=[]), patch.object(harness, "generate_hashes", return_value=[]), patch.object(harness.subprocess, "run", return_value=success), patch.object(harness, "protect_windows_state", return_value=success), patch.object(harness.secrets, "token_hex", side_effect=lambda size: "a" * (size * 2)) as generator, patch.object(harness.secrets, "token_bytes", return_value=b"\x00" * 64), patch("builtins.print"):
                harness.prepare()
            data = json.loads((harness.WORK / "private-state.json").read_text(encoding="utf-8"))
            self.assertEqual([call.args[0] for call in generator.call_args_list], [15, 15, 15, 24])
            self.assertTrue(all(re.fullmatch(r"[0-9a-f]{30}", data[key]) for key in ("owner_password", "runtime_password", "security_password")))
            self.assertEqual(len(data["marker"]), 48)
            self.assertEqual(data["state"], "recovery-01")
            self.assertEqual(json.loads((harness.WORK / "manifest.json").read_text(encoding="utf-8"))["state"], "recovery-01")
            self.assertEqual(before, {path.name: path.read_bytes() for path in legacy.iterdir() if path.is_file()})

    def test_private_write_rejects_path_outside_selected_directory(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(harness, "WORK", Path(temporary) / "recovery-01"), self.assertRaises(ValueError):
            harness.write_private(Path(temporary) / "outside.txt", "synthetic")


if __name__ == "__main__":
    unittest.main()
