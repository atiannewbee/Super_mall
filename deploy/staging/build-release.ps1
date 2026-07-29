param(
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $projectRoot '.artifacts'
}
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$expectedDefault = [System.IO.Path]::GetFullPath((Join-Path $projectRoot '.artifacts'))

Push-Location $projectRoot
try {
    if (git status --porcelain) {
        throw '工作区不干净，拒绝生成无法对应 Git 提交的发布包。'
    }

    $commit = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') {
        throw '无法读取当前 Git commit。'
    }

    $jdk17 = 'C:\Program Files\Java\jdk-17'
    if (Test-Path -LiteralPath (Join-Path $jdk17 'bin\java.exe')) {
        $env:JAVA_HOME = $jdk17
        $env:Path = "$jdk17\bin;$env:Path"
    }

    & .\mvnw.cmd clean test package
    if ($LASTEXITCODE -ne 0) { throw '后端测试或打包失败。' }

    Push-Location (Join-Path $projectRoot 'apps\storefront')
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw '消费者端依赖安装失败。' }
        & npm.cmd run test:run
        if ($LASTEXITCODE -ne 0) { throw '消费者端测试失败。' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw '消费者端构建失败。' }
    }
    finally {
        Pop-Location
    }

    Push-Location (Join-Path $projectRoot 'apps\merchant')
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw '商家端依赖安装失败。' }
        & npm.cmd run test:run
        if ($LASTEXITCODE -ne 0) { throw '商家端测试失败。' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw '商家端构建失败。' }
    }
    finally {
        Pop-Location
    }

    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $stagingRoot = Join-Path $OutputDirectory "release-$commit"
    if (Test-Path -LiteralPath $stagingRoot) {
        $resolvedStaging = [System.IO.Path]::GetFullPath($stagingRoot)
        if (-not $resolvedStaging.StartsWith($expectedDefault, [System.StringComparison]::OrdinalIgnoreCase) -and
            -not $resolvedStaging.StartsWith($OutputDirectory, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "拒绝清理输出目录之外的路径：$resolvedStaging"
        }
        Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
    }

    New-Item -ItemType Directory -Force -Path `
        (Join-Path $stagingRoot 'backend'), `
        (Join-Path $stagingRoot 'storefront'), `
        (Join-Path $stagingRoot 'merchant') | Out-Null

    Copy-Item -LiteralPath `
        (Join-Path $projectRoot 'target\Spring_boot_Demo1-0.0.1-SNAPSHOT.jar') `
        -Destination (Join-Path $stagingRoot 'backend\super-mall.jar')
    Copy-Item -Path (Join-Path $projectRoot 'apps\storefront\dist\*') `
        -Destination (Join-Path $stagingRoot 'storefront') -Recurse
    Copy-Item -Path (Join-Path $projectRoot 'apps\merchant\dist\*') `
        -Destination (Join-Path $stagingRoot 'merchant') -Recurse
    [System.IO.File]::WriteAllText(
        (Join-Path $stagingRoot 'VERSION'),
        "$commit`n",
        [System.Text.UTF8Encoding]::new($false)
    )

    $archive = Join-Path $OutputDirectory "super-mall-$commit.tar.gz"
    if (Test-Path -LiteralPath $archive) {
        Remove-Item -LiteralPath $archive -Force
    }
    & tar.exe -czf $archive -C $stagingRoot .
    if ($LASTEXITCODE -ne 0) { throw '发布包压缩失败。' }

    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
    Write-Host "Release: $archive"
    Write-Host "Commit:  $commit"
    Write-Host "SHA256:  $hash"
}
finally {
    Pop-Location
}
