param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('validate', 'runtimes', 'sync', 'run', 'submit')]
    [string] $Command,

    [Parameter(Position = 1)]
    [string] $Configuration = 'rumble-client.json',

    [Parameter(Position = 2)]
    [string] $Image = 'rumble-client:dev'
)

$clientArguments = switch ($Command) {
    'validate' { @('--validate-config', '/work/rumble-client.json') }
    'runtimes' { @('--check-runtimes') }
    'sync' { @('--sync', '/work/rumble-client.json') }
    # --run re-synchronizes before executing a battle (same as --sync), so unlike
    # runtimes/validate it cannot be run with --network none here.
    'run' { @('--run', '/work/rumble-client.json') }
    'submit' { @('--submit', '/work/rumble-client.json') }
}

$dockerArguments = @(
    'run', '--rm', '--read-only', '--tmpfs', '/tmp:rw,nosuid,nodev,size=1g',
    '--cpus', '4', '--memory', '8g', '--pids-limit', '512',
    '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges'
)
if ($IsLinux -or $IsMacOS) {
    $userId = (& id -u).Trim()
    $groupId = (& id -g).Trim()
    $dockerArguments += @('--user', "${userId}:${groupId}")
}
if ($Command -eq 'runtimes') {
    $dockerArguments += @('--network', 'none')
} else {
    $configurationPath = (Resolve-Path -LiteralPath $Configuration).Path
    $configurationDirectory = Split-Path -Parent $configurationPath
    $stateDirectory = Join-Path $configurationDirectory '.rumble-client'
    New-Item -ItemType Directory -Force -Path $stateDirectory | Out-Null
    $dockerArguments += @(
        '--mount', "type=bind,source=$configurationPath,target=/work/rumble-client.json,readonly",
        '--mount', "type=bind,source=$stateDirectory,target=/work/.rumble-client"
    )
}
if ($Command -eq 'submit') {
    if (-not (Test-Path env:RUMBLE_CLIENT_TOKEN) -or [string]::IsNullOrEmpty($env:RUMBLE_CLIENT_TOKEN)) {
        throw 'RUMBLE_CLIENT_TOKEN must be set in the environment to submit results'
    }
    $dockerArguments += @('--env', 'RUMBLE_CLIENT_TOKEN')
}
$dockerArguments += $Image
$dockerArguments += $clientArguments

& docker @dockerArguments
exit $LASTEXITCODE
