param(
  [Parameter(Mandatory=$true)][string]$Issue,
  [Parameter(Mandatory=$true)][string]$State,
  [Parameter(Mandatory=$true)][string]$Action,
  [Parameter(Mandatory=$true)][string]$Result,
  [Parameter(Mandatory=$true)][string]$Rationale,
  [string]$Title = "Fix summary",
  [string]$Commit = "HEAD"
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) | Split-Path -Parent
$memoryDir = Join-Path $repoRoot "memory\examples"
if (-not (Test-Path $memoryDir)) { New-Item -ItemType Directory -Path $memoryDir | Out-Null }

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$diff = git -C $repoRoot show --no-color $Commit
$path = Join-Path $memoryDir ("{0}_fix.md" -f $timestamp)

$content = @"
# $Title

Issue Description:
$Issue

State:
$State

Action:
$Action

Result:
$Result

Rationale:
$Rationale

Diff Patch:
```diff
$diff
```
"@

$content | Set-Content -Path $path -Encoding UTF8
Write-Host "Wrote memory entry: $path"
