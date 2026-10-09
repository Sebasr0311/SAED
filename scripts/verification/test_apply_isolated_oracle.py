"""SQL-process guard tests using mocks and a synthetic local Python child only.

Never starts Docker/Oracle or reads generated private fixture state.
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apply_isolated_oracle as executor


class ExecutorGuardTest(unittest.TestCase):
    def setUp(self):
        for mocker in (patch.object(executor.prepared, "STATE", "recovery-01"), patch.object(executor.prepared, "check_state_path")):
            mocker.start()
            self.addCleanup(mocker.stop)

    def installer(self):
        installer = object.__new__(executor.Installer)
        installer.container = executor.CONTAINER
        installer.run_id = executor.RUN_ID
        installer.state_id = "recovery-01"
        installer.private = {"state": installer.state_id, "owner_password": "a" * 30, "runtime_password": "b" * 30, "security_password": "c" * 30, "marker": "d" * 48}
        installer.candidate = "e" * 64
        installer.manifest = {}
        installer.state = {"format": 1, "state": installer.state_id, "candidate": installer.candidate, "container": installer.container, "run": installer.run_id, "status": "in-progress", "completed": [], "stage": "bootstrap"}
        installer.stages = {"bootstrap": ("SYS", installer.bootstrap()), "010-structure.sql": (executor.OWNER, "SELECT 1 FROM DUAL;"), "060-auth-package.sql": (executor.SECURITY, "BEGIN NULL; END;\n/"), "catalog": ("SYS", "SELECT 1 FROM DUAL;")}
        return installer

    def invoke(self, installer, stage, output=None):
        result = subprocess.CompletedProcess([], 0, output or "SAED_STAGE_OK:" + stage + "\n", "")
        with patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "candidate_identity", return_value=installer.candidate), patch.object(executor.prepared, "inspect_container") as metadata, patch.object(executor.subprocess, "run", return_value=result) as process:
            counts = installer.invoke(stage)
            metadata.assert_called_once()
            return counts, process.call_args

    def test_marker_precedes_owner_payload_in_same_connection(self):
        installer = self.installer()
        _, called = self.invoke(installer, "010-structure.sql")
        sql = called.kwargs["input"]
        self.assertEqual(sql.count("CONNECT "), 1)
        self.assertLess(sql.index("SAED_ISO_CONTROL.RUN_GUARD"), sql.index("SELECT 1 FROM DUAL"))
        self.assertIn("ISOLATED_PDB_OR_PRINCIPAL_MISMATCH", sql)
        self.assertIn("ISOLATED_MARKER_MISMATCH", sql)
        self.assertIn("/XEPDB1", sql)
        self.assertTrue(installer.private["owner_password"] not in " ".join(called.args[0]))
        self.assertTrue(installer.private["owner_password"] not in json.dumps(called.kwargs["env"]))

    def test_security_payload_uses_its_own_session_and_guard(self):
        _, called = self.invoke(self.installer(), "060-auth-package.sql")
        sql = called.kwargs["input"]
        self.assertIn("CONNECT SAED_SEC_MASTER/", sql)
        self.assertEqual(sql.count("CONNECT "), 1)
        self.assertLess(sql.index("SAED_ISO_CONTROL.RUN_GUARD"), sql.index("BEGIN NULL;"))

    def test_sys_stages_select_pdb_before_marker_and_payload(self):
        _, called = self.invoke(self.installer(), "catalog", "SAED_CATALOG:TABLES=98,INVALID=0,POLICIES=94\nSAED_STAGE_OK:catalog\n")
        sql = called.kwargs["input"]
        self.assertLess(sql.index("ALTER SESSION SET CONTAINER=XEPDB1"), sql.index("SAED_ISO_CONTROL.RUN_GUARD"))
        self.assertLess(sql.index("SAED_ISO_CONTROL.RUN_GUARD"), sql.index("SELECT 1 FROM DUAL"))

    def test_bootstrap_is_bounded_freshness_and_account_creation(self):
        installer = self.installer()
        _, called = self.invoke(installer, "bootstrap")
        sql = called.kwargs["input"]
        self.assertLess(sql.index("ISOLATED_SCHEMAS_ALREADY_EXIST"), sql.index("CREATE USER"))
        self.assertLess(sql.index("ISOLATED_OBJECTS_ALREADY_EXIST"), sql.index("CREATE USER"))
        self.assertLess(sql.index("ISOLATED_CONTEXT_ALREADY_EXISTS"), sql.index("CREATE USER"))
        self.assertIn("CREATE USER SAED_ISO_CONTROL NO AUTHENTICATION ACCOUNT LOCK", sql)
        control_line = next(line for line in sql.splitlines() if line.startswith("CREATE USER SAED_ISO_CONTROL"))
        self.assertNotIn("IDENTIFIED BY", control_line)
        self.assertTrue(installer.private["marker"] not in control_line)
        self.assertIn("CREATE CONTEXT SAED_CTX USING SAED_ISO_OWNER.PKG_SAED_SESSION", sql)
        self.assertNotIn("DROP USER", sql)
        self.assertNotIn(" ANY ", sql)
        self.assertNotIn("GRANT DBA", sql)
        runtime_lines = [line for line in sql.splitlines() if line.startswith("GRANT") and line.endswith("TO SAED_ISO_RUNTIME;")]
        self.assertTrue(all("EXEMPT" not in line and "CREATE TABLE" not in line for line in runtime_lines))

    def test_fixture_stages_precede_policy_activation(self):
        installer = self.installer()
        installer.manifest = {"files": {f"{100 + version}-V5.{version}__mock.sql": "a" * 64 for version in range(1, 30)}}
        with patch.object(executor.Path, "read_text", return_value='CREATE TABLE "EXAMPLE" (ID NUMBER);'):
            stages = list(installer.build_stages())
        for fixture in ("030-synthetic-fixture.sql", "031-mixed-assignment.sql"):
            self.assertLess(stages.index(fixture), stages.index("900-baseline-policies.sql"))
        self.assertLess(stages.index("900-baseline-policies.sql"), stages.index("101-V5.1__mock.sql"))
        self.assertLess(stages.index("129-V5.29__mock.sql"), stages.index("lifecycle-fixture"))

    def test_post_policy_lifecycle_fixture_has_context_before_dml(self):
        installer = self.installer()
        installer.stages["lifecycle-fixture"] = (executor.OWNER, "UPDATE USUARIOS SET ESTADO='INACTIVO' WHERE ID_USUARIO=92005;")
        _, called = self.invoke(installer, "lifecycle-fixture")
        sql = called.kwargs["input"]
        self.assertLess(sql.index("SAED_ISO_CONTROL.RUN_GUARD"), sql.index("SET_BOOTSTRAP_CONTEXT"))
        self.assertLess(sql.index("SET_CONTEXT(1,0,NULL,'SUPERADMIN')"), sql.index("UPDATE USUARIOS"))

    def test_metadata_mismatch_prevents_sql_process(self):
        installer = self.installer()
        with patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "candidate_identity", return_value=installer.candidate), patch.object(executor.prepared, "inspect_container", side_effect=ValueError("metadata mismatch")), patch.object(executor.subprocess, "run") as process:
            with self.assertRaisesRegex(ValueError, "metadata mismatch"):
                installer.invoke("010-structure.sql")
            process.assert_not_called()

    def test_candidate_change_prevents_metadata_and_sql(self):
        installer = self.installer()
        with patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "candidate_identity", return_value="f" * 64), patch.object(executor.prepared, "inspect_container") as metadata, patch.object(executor.subprocess, "run") as process:
            with self.assertRaisesRegex(ValueError, "candidate changed"):
                installer.invoke("010-structure.sql")
            metadata.assert_not_called()
            process.assert_not_called()

    def test_no_unrecognized_sql_stage(self):
        with self.assertRaises(ValueError):
            self.installer().invoke("arbitrary-sql")

    def test_pdb_or_marker_errors_expose_only_codes(self):
        for code in ("ORA-20990", "ORA-20991", "ORA-00942"):
            with self.subTest(code=code), self.assertRaises(ValueError) as captured:
                executor.output_protocol(subprocess.CompletedProcess([], 1, code + ": PRIVATE_SQL_PRIVATE_PASSWORD", ""), "catalog")
            self.assertTrue("PRIVATE" not in str(captured.exception))
            self.assertIn(code, str(captured.exception))

    def test_unexpected_output_is_suppressed(self):
        with self.assertRaisesRegex(ValueError, "Unexpected SQL output suppressed") as captured:
            executor.output_protocol(subprocess.CompletedProcess([], 0, "PRIVATE_SQL_PRIVATE_PASSWORD\nSAED_STAGE_OK:bootstrap\n", ""), "bootstrap")
        self.assertTrue("PRIVATE" not in str(captured.exception))

    def test_compile_warning_fails_even_with_success_code(self):
        with self.assertRaises(ValueError):
            executor.output_protocol(subprocess.CompletedProcess([], 0, "Warning: Package Body created with compilation errors.\nSAED_STAGE_OK:bootstrap\n", ""), "bootstrap")

    def test_missing_or_duplicate_completion_fails(self):
        for output in ("", "SAED_STAGE_OK:bootstrap\nSAED_STAGE_OK:bootstrap\n"):
            with self.assertRaises(ValueError):
                executor.output_protocol(subprocess.CompletedProcess([], 0, output, ""), "bootstrap")

    def test_catalog_only_allows_bounded_numeric_output(self):
        result = executor.output_protocol(subprocess.CompletedProcess([], 0, "SAED_CATALOG:TABLES=98,INVALID=0,POLICIES=94\nSAED_STAGE_OK:catalog\n", ""), "catalog")
        self.assertEqual(result, {"tables": 98, "invalid": 0, "policies": 94})
        with self.assertRaises(ValueError):
            executor.output_protocol(subprocess.CompletedProcess([], 0, "SAED_STAGE_OK:catalog\n", ""), "catalog")

    def test_clean_environment_drops_datasource_and_shell_overrides(self):
        with patch.dict(os.environ, {"DB_URL": "forbidden", "SPRING_APPLICATION_JSON": "forbidden", "JAVA_TOOL_OPTIONS": "forbidden", "DOCKER_CONTEXT": "remote", "DOCKER_HOST": "tcp://remote", "API_KEY": "forbidden"}, clear=False):
            env = executor.child_environment()
        self.assertTrue(all(key not in env for key in ("DB_URL", "SPRING_APPLICATION_JSON", "JAVA_TOOL_OPTIONS", "DOCKER_CONTEXT", "API_KEY")))
        self.assertEqual(env["DOCKER_HOST"], "npipe:////./pipe/dockerDesktopLinuxEngine")

    def test_constructor_rejects_different_container_before_reading_secrets(self):
        for container, run_id in (("a" * 64, executor.RUN_ID), ("4cc90670a9c69c5340985e355463cab8084e8c17bfbcdde5b920e9d4a7a81b6f", "cf2540e417164a4eada01516b8bb87b5"), (executor.CONTAINER, "f" * 32)):
            with self.subTest(container=container, run=run_id), patch.object(executor, "credentials") as secrets_reader:
                with self.assertRaises(ValueError):
                    executor.Installer(container, run_id)
                secrets_reader.assert_not_called()

    def test_apply_failure_preserves_blocked_state_and_does_not_continue(self):
        installer = self.installer()
        with tempfile.TemporaryDirectory() as temporary, patch.object(executor.prepared, "WORK", Path(temporary)), patch.object(executor, "persist_state") as state_writer, patch.object(installer, "invoke", side_effect=ValueError("SQL stage failed: ORA-20991")) as invoke:
            with self.assertRaises(ValueError):
                installer.apply()
            self.assertEqual(installer.state["status"], "blocked")
            self.assertEqual(installer.state["completed"], [])
            invoke.assert_called_once_with("bootstrap")
            self.assertGreaterEqual(state_writer.call_count, 3)

    def test_existing_installation_lock_prevents_sql(self):
        installer = self.installer()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "installation.lock").write_text("existing", encoding="utf-8")
            with patch.object(executor.prepared, "WORK", directory), patch.object(installer, "invoke") as invoke:
                with self.assertRaises(ValueError):
                    installer.apply()
                invoke.assert_not_called()

    def test_complete_apply_rechecks_sealed_catalog_without_recreate(self):
        installer = self.installer()
        installer.state["status"] = "complete"
        with patch.object(installer, "invoke") as invoke, patch("builtins.print"):
            installer.apply()
            invoke.assert_called_once_with("sealed-catalog")

    def test_constructor_rejects_partial_or_wrong_candidate_state(self):
        for change in ({"status": "blocked"}, {"status": "in-progress"}, {"candidate": "f" * 64}, {"format": True}, {"completed": []}, {"state": "recovery-02"}):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                state = {"format": 1, "state": "recovery-01", "candidate": "e" * 64, "container": executor.CONTAINER, "run": executor.RUN_ID, "status": "complete", "completed": ["bootstrap", "sealed-catalog"]}
                state.update(change)
                (directory / executor.STATE_NAME).write_text(json.dumps(state), encoding="utf-8")
                with patch.object(executor.prepared, "WORK", directory), patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "credentials", return_value={}), patch.object(executor, "candidate_identity", return_value="e" * 64), patch.object(executor.Installer, "build_stages", return_value={"bootstrap": ("SYS", ""), "sealed-catalog": ("SYS", "")}):
                    with self.assertRaises(ValueError):
                        executor.Installer(executor.CONTAINER, executor.RUN_ID)

    def test_generated_private_credentials_reject_injection(self):
        with patch.object(executor, "object_json", return_value={"state": "recovery-01", "owner_password": "quote'; DROP USER", "runtime_password": "b" * 30, "security_password": "c" * 30, "marker": "d" * 48}):
            with self.assertRaisesRegex(ValueError, "private state is invalid"):
                executor.credentials()

    def test_credentials_accept_oracle_length_passwords_and_long_marker(self):
        private = self.installer().private
        with patch.object(executor, "object_json", return_value=private):
            self.assertIs(executor.credentials(), private)

    def test_credentials_reject_old_overlength_passwords_or_wrong_marker(self):
        for change in ({"owner_password": "a" * 48}, {"runtime_password": "b" * 31}, {"security_password": "c" * 29}, {"marker": "d" * 30}, {"state": "recovery-02"}):
            private = dict(self.installer().private)
            private.update(change)
            with self.subTest(fields=list(change)), patch.object(executor, "object_json", return_value=private), self.assertRaises(ValueError):
                executor.credentials()

    def test_changed_state_selector_prevents_metadata_and_sql(self):
        installer = self.installer()
        with patch.object(executor.prepared, "STATE", "recovery-02"), patch.object(executor.prepared, "inspect_container") as metadata, patch.object(executor.subprocess, "run") as process:
            with self.assertRaisesRegex(ValueError, "selector changed"):
                installer.invoke("bootstrap")
            metadata.assert_not_called()
            process.assert_not_called()

    def test_catalog_fails_on_invalid_counts_and_sealed_output_missing(self):
        for output in ("SAED_CATALOG:TABLES=98,INVALID=1,POLICIES=94\nSAED_STAGE_OK:sealed-catalog\n", "SAED_STAGE_OK:sealed-catalog\n"):
            with self.assertRaises(ValueError):
                executor.output_protocol(subprocess.CompletedProcess([], 0, output, ""), "sealed-catalog")

    def test_sql_process_declares_strict_utf8_for_all_three_streams(self):
        _, called = self.invoke(self.installer(), "010-structure.sql")
        self.assertEqual(called.kwargs.get("encoding"), "utf-8")
        self.assertEqual(called.kwargs.get("errors"), "strict")
        self.assertIs(called.kwargs.get("text"), True)
        self.assertIs(called.kwargs.get("capture_output"), True)

    def test_sqlplus_client_encoding_is_fixed_inside_container_only(self):
        with patch.dict(os.environ, {"NLS_LANG": "AMERICAN_AMERICA.WE8MSWIN1252"}, clear=False):
            _, called = self.invoke(self.installer(), "010-structure.sql")
        args = called.args[0]
        self.assertEqual(args.count("NLS_LANG=AMERICAN_AMERICA.AL32UTF8"), 1)
        setting = args.index("NLS_LANG=AMERICAN_AMERICA.AL32UTF8")
        self.assertEqual(args[setting - 1], "--env")
        self.assertLess(setting, args.index(executor.CONTAINER))
        self.assertNotIn("NLS_LANG", called.kwargs["env"])

    def test_non_ascii_sql_survives_simulated_windows_ansi_encoding(self):
        installer = self.installer()
        sample = "\u0081\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1\u00bf\u00a1"
        payload = "-- Synthetic Unicode: " + sample + "\nSELECT 1 FROM DUAL;"
        installer.stages["010-structure.sql"] = (executor.OWNER, payload)
        captured = []

        def transport(args, **kwargs):
            encoding = kwargs.get("encoding", "cp1252")
            errors = kwargs.get("errors", "strict")
            try:
                captured.append(kwargs["input"].encode(encoding, errors).decode("utf-8", "strict"))
            except UnicodeError:
                self.fail("SQL input depended on a Windows ANSI code page")
            return subprocess.CompletedProcess(args, 0, "SAED_STAGE_OK:010-structure.sql\n", "")

        with patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "candidate_identity", return_value=installer.candidate), patch.object(executor.prepared, "inspect_container"), patch.object(executor.subprocess, "run", side_effect=transport) as process:
            installer.invoke("010-structure.sql")
        self.assertTrue(payload in captured[0], "Unicode SQL changed during transport")
        self.assertTrue(installer.stages["010-structure.sql"][1] == payload)
        process.assert_called_once()

    def test_utf8_roundtrip_through_real_synthetic_child_ignores_ansi_default(self):
        installer = self.installer()
        sample = "\u0081\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1\u00bf\u00a1"
        installer.stages["010-structure.sql"] = (executor.OWNER, "-- " + sample + "\nSELECT 1 FROM DUAL;")
        child = "import sys; value='\\u0081\\u00e1\\u00e9\\u00ed\\u00f3\\u00fa\\u00f1\\u00bf\\u00a1'; data=sys.stdin.buffer.read().decode('utf-8','strict'); assert value in data; sys.stdout.buffer.write(value.encode('utf-8')); sys.stderr.buffer.write(value.encode('utf-8'))"
        local_run = subprocess.run

        def transport(args, **kwargs):
            try:
                with patch.object(subprocess, "_text_encoding", return_value="cp1252"):
                    result = local_run([sys.executable, "-B", "-I", "-c", child], **kwargs)
            except UnicodeError:
                self.fail("Synthetic stdin/stdout/stderr transport used an ANSI default")
            self.assertEqual(result.returncode, 0, "Synthetic Unicode child failed")
            self.assertTrue(result.stdout == sample, "UTF-8 stdout roundtrip changed characters")
            self.assertTrue(result.stderr == sample, "UTF-8 stderr roundtrip changed characters")
            return subprocess.CompletedProcess(args, 0, "SAED_STAGE_OK:010-structure.sql\n", "")

        with patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "candidate_identity", return_value=installer.candidate), patch.object(executor.prepared, "inspect_container"), patch.object(executor.subprocess, "run", side_effect=transport) as process:
            installer.invoke("010-structure.sql")
        process.assert_called_once()

    def test_unicode_transport_errors_are_sanitized_without_retry_or_fallback(self):
        invalid_input = "PRIVATE_SQL_PRIVATE_PASSWORD\ud800"
        failures = (
            UnicodeEncodeError("utf-8", invalid_input, len(invalid_input) - 1, len(invalid_input), "PRIVATE_DETAIL"),
            UnicodeDecodeError("utf-8", b"\xffPRIVATE_SQL_PRIVATE_PASSWORD", 0, 1, "PRIVATE_DETAIL"),
        )
        for failure in failures:
            installer = self.installer()
            with self.subTest(error=type(failure).__name__), patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "candidate_identity", return_value=installer.candidate), patch.object(executor.prepared, "inspect_container"), patch.object(executor.subprocess, "run", side_effect=failure) as process:
                with self.assertRaises(ValueError) as captured:
                    installer.invoke("010-structure.sql")
                self.assertEqual(str(captured.exception), "SQL stage failed: UTF-8 transport failure")
                self.assertIsNone(captured.exception.__cause__)
                self.assertTrue(captured.exception.__suppress_context__)
                process.assert_called_once()

    def test_unicode_unknown_stdout_and_stderr_remain_suppressed(self):
        for stream in ("stdout", "stderr"):
            private = "PRIVATE_\u00f1\u0081"
            result = subprocess.CompletedProcess([], 0, "SAED_STAGE_OK:bootstrap\n", "")
            setattr(result, stream, getattr(result, stream) + private)
            with self.subTest(stream=stream), self.assertRaises(ValueError) as captured:
                executor.output_protocol(result, "bootstrap")
            self.assertEqual(str(captured.exception), "Unexpected SQL output suppressed")

    def test_unicode_oracle_failures_expose_codes_only(self):
        result = subprocess.CompletedProcess([], 1, "ORA-20995: PRIVATE_\u00f1\u0081\n", "PRIVATE_DETAIL")
        with self.assertRaises(ValueError) as captured:
            executor.output_protocol(result, "010-structure.sql")
        self.assertEqual(str(captured.exception), "SQL stage failed: ORA-20995")

    def test_unrecognized_principal_cannot_reach_sql_process_in_any_stage(self):
        for stage in ("bootstrap", "010-structure.sql"):
            installer = self.installer()
            installer.stages[stage] = ("UNEXPECTED", "SELECT 1 FROM DUAL;")
            result = subprocess.CompletedProcess([], 0, "SAED_STAGE_OK:" + stage + "\n", "")
            with self.subTest(stage=stage), patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "candidate_identity", return_value=installer.candidate), patch.object(executor.prepared, "inspect_container"), patch.object(executor.subprocess, "run", return_value=result) as process:
                with self.assertRaisesRegex(ValueError, "Unexpected database principal"):
                    installer.invoke(stage)
                process.assert_not_called()

    def test_utf8_transport_preserves_reviewed_principal_and_pdb_bindings(self):
        for principal in ("SYS", executor.OWNER, executor.SECURITY, executor.RUNTIME):
            installer = self.installer()
            installer.stages["bound-protocol"] = (principal, "BEGIN NULL; END;\n/")
            _, called = self.invoke(installer, "bound-protocol")
            sql = called.kwargs["input"]
            self.assertTrue("SESSION_USER') != '" + principal + "'" in sql)
            self.assertTrue("CON_NAME') != 'XEPDB1'" in sql)
            self.assertEqual(sql.count("CONNECT "), 1)
            self.assertTrue(all(value not in json.dumps(called.args) + json.dumps(called.kwargs["env"]) for value in installer.private.values() if value != installer.state_id))

    def test_bootstrap_requires_sys_before_credentials_or_sql_process(self):
        test = self

        class UnreadablePrivateState(dict):
            def __getitem__(self, key):
                test.fail("Non-SYS bootstrap accessed credentials before rejecting its principal")

        for principal in (executor.OWNER, executor.SECURITY, executor.RUNTIME):
            installer = self.installer()
            installer.stages["bootstrap"] = (principal, "BEGIN NULL; END;\n/")
            installer.private = UnreadablePrivateState()
            with self.subTest(principal=principal), patch.object(executor.prepared, "verify_prepared", return_value={}), patch.object(executor, "candidate_identity", return_value=installer.candidate), patch.object(executor.prepared, "inspect_container"), patch.object(executor.subprocess, "run") as process:
                with self.assertRaisesRegex(ValueError, "Unexpected database principal"):
                    installer.invoke("bootstrap")
                process.assert_not_called()

    def test_partial_apply_encoding_failure_keeps_completed_stage_and_lock(self):
        installer = self.installer()
        with tempfile.TemporaryDirectory() as temporary, patch.object(executor.prepared, "WORK", Path(temporary)), patch.object(executor, "persist_state"), patch.object(installer, "invoke", side_effect=({}, ValueError("SQL stage failed: UTF-8 transport failure"))) as invoke:
            with self.assertRaises(ValueError):
                installer.apply()
            self.assertEqual(installer.state["status"], "blocked")
            self.assertEqual(installer.state["completed"], ["bootstrap"])
            self.assertEqual(installer.state["stage"], "010-structure.sql")
            self.assertEqual(installer.state["reason"], "SQL stage failed: UTF-8 transport failure")
            self.assertTrue((Path(temporary) / "installation.lock").is_file())
            self.assertEqual(invoke.call_count, 2)


if __name__ == "__main__":
    unittest.main()
