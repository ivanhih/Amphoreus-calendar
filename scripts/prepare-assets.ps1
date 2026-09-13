$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$mapping = @(
    '47cbeaf398a133ae8652f7b555af238c1340190821.jpg',
    'bc49d560a18902b8b69e26778a98292e1340190821.jpg',
    'd56735a8ba541abcb455c34baea7dd5e1340190821.jpg',
    '6535b81bf213a2858b612671e3df169d1340190821.jpg',
    '7eacfb0abb2fc74ad318162d4ee3a7071340190821.jpg',
    'e763dd04a1cd036e72c9b5685ed16aae1340190821.jpg',
    'e5b24d6087b6d75d3bd129bffa763f151340190821.jpg',
    '6aa279435083f3b2b9b621044ff667951340190821.jpg',
    '9499fba216625535ac0e2cb8c76046781340190821.jpg',
    'dae36a17c6dee8705c70afb34769e3461340190821.jpg',
    '8784fb729960c5ab581f3dc9f87f6a061340190821.jpg',
    'ce73d5e5b8f7b61f6f043e8e653c49bb1340190821.jpg',
    '26a117aad747432f03dc10ca09b67cfa1340190821.jpg'
)
$destination = Join-Path $projectRoot 'app\src\main\assets\art'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
for ($i = 0; $i -lt $mapping.Length; $i++) {
    Copy-Item -LiteralPath (Join-Path $projectRoot $mapping[$i]) -Destination (Join-Path $destination ('month_{0:D2}.jpg' -f $i)) -Force
}
Write-Output 'Copied 13 original images; original files were not modified.'
