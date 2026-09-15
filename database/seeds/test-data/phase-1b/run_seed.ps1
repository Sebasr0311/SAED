$ErrorActionPreference = "Stop"

Write-Host "1. Granting EXEMPT ACCESS POLICY temporarily to SAED_V39_FINAL_TEST..."
"GRANT EXEMPT ACCESS POLICY TO SAED_V39_FINAL_TEST;`nEXIT;" | sqlplus -S sys/saed2026@localhost:1521/XEPDB1 as sysdba

Write-Host "2. Loading Seed Data as SAED_V39_FINAL_TEST..."
sqlplus -S SAED_V39_FINAL_TEST/saed2026@localhost:1521/XEPDB1 "@database\test-data\phase-1b\seed_phase_1b_oracle.sql"

Write-Host "3. Revoking EXEMPT ACCESS POLICY from SAED_V39_FINAL_TEST..."
"REVOKE EXEMPT ACCESS POLICY FROM SAED_V39_FINAL_TEST;`nEXIT;" | sqlplus -S sys/saed2026@localhost:1521/XEPDB1 as sysdba

Write-Host "4. Checking EXEMPT ACCESS POLICY..."
$check = "SET HEADING OFF`nSET FEEDBACK OFF`nSELECT COUNT(*) FROM DBA_SYS_PRIVS WHERE GRANTEE = 'SAED_V39_FINAL_TEST' AND PRIVILEGE = 'EXEMPT ACCESS POLICY';`nEXIT;" | sqlplus -S sys/saed2026@localhost:1521/XEPDB1 as sysdba
$check = $check.Trim()

if ($check -eq "0") {
    Write-Host "Verification Passed: SAED_V39_FINAL_TEST does NOT have EXEMPT ACCESS POLICY."
} else {
    Write-Host "WARNING: EXEMPT ACCESS POLICY might still be present! Count = $check"
}

Write-Host "Seed completed."
