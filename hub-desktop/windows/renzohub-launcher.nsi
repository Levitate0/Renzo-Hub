; Renzo Hub launcher — a real .exe that starts the bundled JRE against the
; app jar and exits. NSIS compiles this tiny stub cross-platform, which is
; what lets the whole Windows artifact be produced from the Linux build host.
Unicode true
OutFile "${OUTDIR}\RenzoHub.exe"
Icon "${ICON}"
SilentInstall silent
AutoCloseWindow true
ShowInstDetails nevershow
RequestExecutionLevel user

Section
  SetOutPath "$EXEDIR"
  Exec '"$EXEDIR\jre\bin\javaw.exe" -jar "$EXEDIR\RenzoHub.jar"'
SectionEnd
