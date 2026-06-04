param(
  [string]$SourceDir = "frontend\assets\drama_posters\source",
  [string]$OutputDir = "frontend\assets\drama_posters\generated"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$items = @(
  @{ Slug = "beipai_xunbao"; Title = "Bei Pai Xun Bao" },
  @{ Slug = "yunmiao"; Title = "Yun Miao" },
  @{ Slug = "winter_solstice"; Title = "Winter Solstice" },
  @{ Slug = "beiwang"; Title = "Bei Wang" },
  @{ Slug = "diyi_wanku"; Title = "Diyi Wanku" },
  @{ Slug = "eighteen_grandma"; Title = "Eighteen Grandma" },
  @{ Slug = "lucky_divorce"; Title = "Lucky Divorce" },
  @{ Slug = "famine_village"; Title = "Famine Village" },
  @{ Slug = "home_inside_out"; Title = "Home Inside Out" },
  @{ Slug = "siye"; Title = "Si Ye" }
)

function Resolve-PosterSource {
  param([string]$Slug)

  foreach ($extension in @(".jpg", ".jpeg", ".png")) {
    $path = Join-Path $SourceDir "$Slug$extension"
    if (Test-Path -LiteralPath $path) {
      return $path
    }
  }
  return $null
}

function Test-IsWhiteMargin {
  param([System.Drawing.Color]$Color)
  return $Color.A -lt 8 -or ($Color.R -gt 244 -and $Color.G -gt 244 -and $Color.B -gt 244)
}

function Get-ContentRect {
  param([System.Drawing.Bitmap]$Bitmap)

  $minX = $Bitmap.Width
  $minY = $Bitmap.Height
  $maxX = -1
  $maxY = -1

  for ($y = 0; $y -lt $Bitmap.Height; $y++) {
    for ($x = 0; $x -lt $Bitmap.Width; $x++) {
      $color = $Bitmap.GetPixel($x, $y)
      if (-not (Test-IsWhiteMargin $color)) {
        if ($x -lt $minX) { $minX = $x }
        if ($y -lt $minY) { $minY = $y }
        if ($x -gt $maxX) { $maxX = $x }
        if ($y -gt $maxY) { $maxY = $y }
      }
    }
  }

  if ($maxX -lt 0 -or $maxY -lt 0) {
    return [System.Drawing.Rectangle]::new(0, 0, $Bitmap.Width, $Bitmap.Height)
  }

  return [System.Drawing.Rectangle]::new($minX, $minY, $maxX - $minX + 1, $maxY - $minY + 1)
}

function Get-CoverCrop {
  param(
    [System.Drawing.Rectangle]$Rect,
    [double]$Aspect
  )

  $sourceAspect = $Rect.Width / $Rect.Height
  if ($sourceAspect -gt $Aspect) {
    $width = [int][Math]::Round($Rect.Height * $Aspect)
    $x = $Rect.X + [int][Math]::Round(($Rect.Width - $width) / 2)
    return [System.Drawing.Rectangle]::new($x, $Rect.Y, $width, $Rect.Height)
  }

  $height = [int][Math]::Round($Rect.Width / $Aspect)
  $y = $Rect.Y + [int][Math]::Round(($Rect.Height - $height) / 2)
  return [System.Drawing.Rectangle]::new($Rect.X, $y, $Rect.Width, $height)
}

function Save-Jpeg {
  param(
    [System.Drawing.Bitmap]$Bitmap,
    [string]$Path,
    [long]$Quality = 92
  )

  $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq "image/jpeg" }
  $encoder = [System.Drawing.Imaging.Encoder]::Quality
  $parameters = [System.Drawing.Imaging.EncoderParameters]::new(1)
  $parameters.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new($encoder, $Quality)
  $Bitmap.Save($Path, $codec, $parameters)
  $parameters.Dispose()
}

function Convert-Poster {
  param(
    [string]$SourcePath,
    [string]$OutputPath,
    [int]$OutputWidth,
    [int]$OutputHeight
  )

  $source = [System.Drawing.Bitmap]::FromFile((Resolve-Path -LiteralPath $SourcePath))
  try {
    $contentRect = Get-ContentRect $source
    $cropRect = Get-CoverCrop $contentRect ($OutputWidth / $OutputHeight)
    $target = [System.Drawing.Bitmap]::new($OutputWidth, $OutputHeight)
    try {
      $graphics = [System.Drawing.Graphics]::FromImage($target)
      try {
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0, 0, $OutputWidth, $OutputHeight), $cropRect, [System.Drawing.GraphicsUnit]::Pixel)
      } finally {
        $graphics.Dispose()
      }
      Save-Jpeg $target $OutputPath
    } finally {
      $target.Dispose()
    }
  } finally {
    $source.Dispose()
  }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$missing = @()
foreach ($item in $items) {
  $source = Resolve-PosterSource $item.Slug
  if (-not $source) {
    $missing += $item.Slug
    continue
  }

  Convert-Poster $source (Join-Path $OutputDir "$($item.Slug)_card.jpg") 900 1200
  Convert-Poster $source (Join-Path $OutputDir "$($item.Slug)_history.jpg") 324 396
  Write-Host "Generated $($item.Slug)"
}

if ($missing.Count) {
  Write-Host "Missing sources: $($missing -join ', ')"
}
