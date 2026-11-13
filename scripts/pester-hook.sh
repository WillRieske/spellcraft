#!/bin/sh
# Runs the deploy-script tests. They are Pester 3.4 tests for Windows PowerShell 5.1, and CI is Linux,
# so without powershell.exe the hook skips (passes) instead of failing.
if ! command -v powershell.exe >/dev/null 2>&1; then
    echo "Pester tests skipped: no Windows PowerShell here."
    exit 0
fi
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Import-Module Pester -RequiredVersion 3.4.0; Invoke-Pester -Path scripts/tests -EnableExit"
exit $?
