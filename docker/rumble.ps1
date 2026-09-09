param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('validate', 'runtimes', 'sync')]
    [string] $Command,

    [Parameter(Position = 1)]
    [string] $Configuration = 'rumble-client.json',

    [Parameter(Position = 2)]
    [string] $Image = 'rumble-client:dev',

    [Parameter(Position = 3)]
    [ValidateSet('docker', 'podman')]
    [string] $Engine = ''
)

if ([string]::IsNullOrWhiteSpace($Engine)) {
    $Engine = if ([string]::IsNullOrWhiteSpace($env:CONTAINER_ENGINE)) { 'docker' } else { $env:CONTAINER_ENGINE }
}
if ($Engine -notin @('docker', 'podman')) {
    throw "Container engine must be 'docker' or 'podman'."
}

$clientArguments = switch ($Command) {
    'validate' { @('--validate-config', '/work/rumble-client.json') }
    'runtimes' { @('--check-runtimes') }
    'sync' { @('--sync', '/work/rumble-client.json') }
}

$containerArguments = @(
    'run', '--rm', '--read-only', '--tmpfs', '/tmp:rw,nosuid,nodev,size=1g',
    '--cpus', '4', '--memory', '8g', '--pids-limit', '512',
    '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges'
)
if ($IsLinux -or $IsMacOS) {
    $userId = (& id -u).Trim()
    $groupId = (& id -g).Trim()
    $containerArguments += @('--user', "${userId}:${groupId}")
}
if ($Command -eq 'runtimes') {
    $containerArguments += @('--network', 'none')
} else {
    $configurationPath = (Resolve-Path -LiteralPath $Configuration).Path
    $configurationDirectory = Split-Path -Parent $configurationPath
    $stateDirectory = Join-Path $configurationDirectory '.rumble-client'
    New-Item -ItemType Directory -Force -Path $stateDirectory | Out-Null
    $containerArguments += @(
        '--mount', "type=bind,source=$configurationPath,target=/work/rumble-client.json,readonly",
        '--mount', "type=bind,source=$stateDirectory,target=/work/.rumble-client"
    )
}
$containerArguments += $Image
$containerArguments += $clientArguments

& $Engine @containerArguments
exit $LASTEXITCODE
