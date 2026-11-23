#!/bin/sh
# Runs the deploy-script tests. They are Pester 3.4 tests for Windows PowerShell 5.1, and CI is Linux,
# so without powershell.exe the hook skips (passes) instead of failing.
# $1, optional: the folder of Pester tests to run instead of scripts/tests. The tests pass it to test
# this script.
if ! command -v powershell.exe >/dev/null 2>&1; then
    echo "Pester tests skipped: no Windows PowerShell here."
    exit 0
fi
# Git gives a hook GIT_DIR and GIT_INDEX_FILE when the commit is made in a linked worktree. A git
# command a test aims at a temp folder would then act on this repository instead: a test's
# 'git init' re-initialized it and wrote core.bare = true into the config all its worktrees share
# (2026-11-23). So the tests never see the variables that point git at a repository; without the
# list of them the tests do not run.
repo_vars=$(git rev-parse --local-env-vars) || repo_vars=
if [ -z "$repo_vars" ]; then
    echo "Pester tests not run: git did not list its repository variables, so they cannot be cleared." >&2
    exit 1
fi
# Unquoted on purpose: one name per word.
unset $repo_vars
tests="${1:-scripts/tests}"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Import-Module Pester -RequiredVersion 3.4.0; Invoke-Pester -Path '$tests' -EnableExit"
exit $?
