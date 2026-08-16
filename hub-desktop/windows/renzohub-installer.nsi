; Renzo Hub desktop installer (per-user, no admin required).
; Same shape as the Renzo Shiori installer this app replaces.
Unicode true
!define APPNAME "Renzo Hub"
!ifndef VERSION
  !define VERSION "1.0.0"
!endif
; SRC and OUTFILE are passed by tools/build-windows-exe.sh

Name "${APPNAME}"
OutFile "${OUTFILE}"
RequestExecutionLevel user
InstallDir "$LOCALAPPDATA\Programs\${APPNAME}"
Icon "${ICON}"
UninstallIcon "${ICON}"
SetCompressor /SOLID lzma

!include "MUI2.nsh"
!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\RenzoHub.exe"
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPNAME}"

; Close every process running from $INSTDIR. A running Renzo Hub IS
; javaw.exe loaded out of $INSTDIR\jre, so each mapped DLL stays
; write-locked and an in-place upgrade dies on "Error opening file for
; writing: ...\awt.dll". Matching by executable path (via an env var, so
; quotes/apostrophes in the path can't break the PowerShell string) kills
; only our own processes — never someone else's Java.
!macro CloseRunningApp
  System::Call 'Kernel32::SetEnvironmentVariable(t "RENZOHUB_DIR", t "$INSTDIR")i'
  nsExec::Exec `powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $$_.ExecutablePath -like ($$env:RENZOHUB_DIR + '\*') } | ForEach-Object { Stop-Process -Id $$_.ProcessId -Force -ErrorAction SilentlyContinue }"`
  Pop $0
  ; Give the OS a beat to release the file handles.
  Sleep 800
!macroend

Section "Install"
  !insertmacro CloseRunningApp
  ; Start the JRE clean: a newer JRE may drop files the old one shipped,
  ; and overwrite-in-place would leave a mixed runtime behind.
  RMDir /r "$INSTDIR\jre"
  SetOutPath "$INSTDIR"
  File /r "${SRC}/*"
  CreateShortcut "$SMPROGRAMS\${APPNAME}.lnk" "$INSTDIR\RenzoHub.exe" "" "$INSTDIR\RenzoHub.exe" 0
  CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\RenzoHub.exe" "" "$INSTDIR\RenzoHub.exe" 0
  ; Taskbar-pin support: stamp the shortcuts with the same AppUserModelID the
  ; app sets on its process (WindowsIntegration.kt). The window is javaw.exe,
  ; and this id match is what lets Windows group it with — and relaunch it
  ; from — the pinned shortcut instead of a bare javaw.
  File "/oname=set-aumid.ps1" "${__FILEDIR__}/set-aumid.ps1"
  nsExec::Exec `powershell -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\set-aumid.ps1" -Lnk "$SMPROGRAMS\${APPNAME}.lnk"`
  Pop $0
  nsExec::Exec `powershell -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\set-aumid.ps1" -Lnk "$DESKTOP\${APPNAME}.lnk"`
  Pop $0
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayName" "${APPNAME}"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINSTKEY}" "Publisher" "Levitate Media"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayIcon" "$INSTDIR\RenzoHub.exe"
  WriteRegStr HKCU "${UNINSTKEY}" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
  WriteRegStr HKCU "${UNINSTKEY}" "InstallLocation" "$INSTDIR"
  WriteRegDWORD HKCU "${UNINSTKEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTKEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
  !insertmacro CloseRunningApp
  Delete "$SMPROGRAMS\${APPNAME}.lnk"
  Delete "$DESKTOP\${APPNAME}.lnk"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "${UNINSTKEY}"
SectionEnd
