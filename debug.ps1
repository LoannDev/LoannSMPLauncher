param(
  [string]$LauncherUrl     = "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/main/launcher.py",
  [string]$RequirementsUrl = "https://raw.githubusercontent.com/LoannDev/LoannSMPLauncher/main/requirements.txt",
  [switch]$Force,
  [switch]$NoPause
)

$ErrorActionPreference = "Stop"

function Say([string]$m, [string]$c="Gray") { Write-Host $m -ForegroundColor $c }  # couleurs. [web:100]
function NL { Write-Host "" }

function Ask([string]$q) {
  if ($Force) { return $true }
  while ($true) {
    $r = Read-Host "$q (oui/non)"  # confirmation simple. [web:90]
    $v = $r.Trim().ToLowerInvariant()
    if ($v -in @("y","Oui","o","oui")) { return $true }
    if ($v -in @("n","Non","non")) { return $false }
  }
}

function Get-Desktop {
  $d = [Environment]::GetFolderPath("Desktop")
  if ($d -and (Test-Path $d)) { return $d }
  $f = Join-Path $HOME "Desktop"
  if (Test-Path $f) { return $f }
  return $HOME
}

function Get-PythonRunner {
  $py = Get-Command py -ErrorAction SilentlyContinue
  if ($py) { return @{ Cmd="py"; Prefix=@("-3") } }
  $python = Get-Command python -ErrorAction SilentlyContinue
  if ($python) { return @{ Cmd="python"; Prefix=@() } }
  $python3 = Get-Command python3 -ErrorAction SilentlyContinue
  if ($python3) { return @{ Cmd="python3"; Prefix=@() } }
  return $null
}

function Get-VenvPython([string]$venvDir) {
  $win  = Join-Path $venvDir "Scripts\python.exe"
  $unix = Join-Path $venvDir "bin/python"
  if (Test-Path $win) { return $win }
  if (Test-Path $unix) { return $unix }
  return $null
}

# Paths (Desktop by default, fallback HOME)
$baseDir = Join-Path (Get-Desktop) "LoannSMPLauncher"
New-Item -ItemType Directory -Force -Path $baseDir | Out-Null

$launcherPath = Join-Path $baseDir "launcher.py"
$reqPath      = Join-Path $baseDir "requirements.txt"
$venvDir      = Join-Path $baseDir ".venv"

NL
Say "LoannSMP Launcher (Debug/Linux)" "Cyan"
Say "Dossier d'installation : $baseDir" "DarkGray"
Say "Lien du Launcher : $LauncherUrl" "DarkGray"
NL

# 1) Python check
$runner = Get-PythonRunner
if (-not $runner) { Say "Python n'a pas ete trouve. Telecharge le ici --> https://www.python.org/downloads/" "Red"; if (-not $NoPause) { Read-Host "Press Enter" } ; exit 1 }
& $runner.Cmd @($runner.Prefix) --version

# If launcher already exists: skip download + pip, go run
if (Test-Path $launcherPath) {
  NL
  Say "Le launcher a deja ete installe."
  if (-not (Ask "Veux-tu lancer le launcher ?")) { Say "Annule." "Yellow"; exit 0 }

  $venvPython = Get-VenvPython $venvDir
  if ($venvPython) { & $venvPython $launcherPath } else { & $runner.Cmd @($runner.Prefix) $launcherPath }
  if (-not $NoPause) { NL; Read-Host "Appuyez sur Entrée pour quitter" }
  exit $LASTEXITCODE
}

# 2) Confirm download
NL
if (-not (Ask "Telecharger launcher.py depuis GitHub ?")) { Say "Annule" "Yellow"; exit 0 }
Invoke-WebRequest -Uri $LauncherUrl -OutFile $launcherPath  # download. [web:40]
Say "Telecharge : launcher.py" "Green"

# 3) Confirm pip install
NL
Say "Le launcher a des dependances Python (modules externes) qui doivent etre installees pour fonctionner." "DarkGray"
Say "Cela peut etre assez long, et il peut y avoir beaucoup de texte qui va s'afficher." "DarkGray"
if (-not (Ask "Les installer ?")) { Say "c'etait necessaire mais ok (tu as tout fait bug la)" "Yellow"; exit 0 }

$hasReq = $true
try {
  Invoke-WebRequest -Uri $RequirementsUrl -OutFile $reqPath
  Say "Telecharge : requirements.txt" "Green"
} catch {
  $hasReq = $false
  Say "Pas de requirements.txt (skip)" "Yellow"
}

if (-not (Test-Path $venvDir)) { & $runner.Cmd @($runner.Prefix) -m venv $venvDir }
$venvPython = Get-VenvPython $venvDir
if (-not $venvPython) { Say "Venv python not found." "Red"; exit 1 }

& $venvPython -m ensurepip --upgrade
& $venvPython -m pip install --upgrade pip
if ($hasReq) { & $venvPython -m pip install -r $reqPath }

# 4) Confirm run
NL
if (-not (Ask "Lancer le launcher maintenant ?")) { Say "Canceled." "Yellow"; exit 0 }
& $venvPython $launcherPath

if (-not $NoPause) { NL; Read-Host "Appuyez sur Entrée pour quitter" }
exit $LASTEXITCODE
