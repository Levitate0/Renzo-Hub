# Stamps System.AppUserModel.ID onto a .lnk shortcut. Run by the installer for
# the Start Menu and Desktop shortcuts so they carry the SAME AppUserModelID
# the app sets on its process (WindowsIntegration.kt) — that match is what
# makes the javaw-hosted window group with the shortcut and pin correctly.
param(
    [Parameter(Mandatory = $true)][string]$Lnk,
    [string]$Aumid = 'LevitateMedia.RenzoHub'
)

$code = @'
using System;
using System.Runtime.InteropServices;

public static class LnkAumid
{
    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern int SHGetPropertyStoreFromParsingName(
        string path, IntPtr zone, uint flags, ref Guid iid,
        [MarshalAs(UnmanagedType.Interface)] out IPropertyStore store);

    [ComImport, Guid("886d8eeb-8cf2-4446-8d02-cdba1dbdcf99"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IPropertyStore
    {
        int GetCount(out uint count);
        int GetAt(uint index, out PROPERTYKEY key);
        int GetValue(ref PROPERTYKEY key, out PROPVARIANT value);
        int SetValue(ref PROPERTYKEY key, ref PROPVARIANT value);
        int Commit();
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct PROPERTYKEY { public Guid fmtid; public uint pid; }

    // Union payload starts at offset 8 on x64 — the only arch we ship.
    [StructLayout(LayoutKind.Explicit)]
    public struct PROPVARIANT
    {
        [FieldOffset(0)] public ushort vt;
        [FieldOffset(8)] public IntPtr p;
    }

    public static void Set(string lnk, string aumid)
    {
        // PKEY_AppUserModel_ID
        var key = new PROPERTYKEY { fmtid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3"), pid = 5 };
        var iid = new Guid("886d8eeb-8cf2-4446-8d02-cdba1dbdcf99");
        IPropertyStore store;
        int hr = SHGetPropertyStoreFromParsingName(lnk, IntPtr.Zero, 0x2 /* GPS_READWRITE */, ref iid, out store);
        if (hr != 0) throw new Exception("SHGetPropertyStoreFromParsingName hr=0x" + hr.ToString("X8"));
        var pv = new PROPVARIANT { vt = 31 /* VT_LPWSTR */, p = Marshal.StringToCoTaskMemUni(aumid) };
        try
        {
            hr = store.SetValue(ref key, ref pv);
            if (hr != 0) throw new Exception("SetValue hr=0x" + hr.ToString("X8"));
            hr = store.Commit();
            if (hr != 0) throw new Exception("Commit hr=0x" + hr.ToString("X8"));
        }
        finally
        {
            Marshal.FreeCoTaskMem(pv.p);
            Marshal.ReleaseComObject(store);
        }
    }
}
'@

Add-Type -TypeDefinition $code
[LnkAumid]::Set($Lnk, $Aumid)
