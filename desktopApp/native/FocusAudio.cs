// FocusAudio.exe: tiny Windows helper used by Focused Up's desktop app.
// Mutes every app's audio session except the ones you keep (Adobe apps by default),
// remembers what it muted, and unmutes exactly those afterwards.
//
//   FocusAudio.exe list
//   FocusAudio.exe mute    <stateFile> <keep1,keep2,...>
//   FocusAudio.exe restore <stateFile>
//
// Build (no Visual Studio needed, the compiler ships with Windows):
//   C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe /nologo /target:winexe /optimize /out:FocusAudio.exe FocusAudio.cs
// Built as a windowless app so no console flashes when Focused Up calls it.

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;

namespace FocusedUp
{
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    class MMDeviceEnumerator { }

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator
    {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice
    {
        int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
    }

    [Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionManager2
    {
        int GetAudioSessionControl(IntPtr sessionGuid, int flags, out IntPtr control);
        int GetSimpleAudioVolume(IntPtr sessionGuid, int flags, out IntPtr volume);
        int GetSessionEnumerator(out IAudioSessionEnumerator sessionEnum);
    }

    [Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionEnumerator
    {
        int GetCount(out int count);
        int GetSession(int index, out IAudioSessionControl2 session);
    }

    [Guid("bfb7ff88-7239-4fc9-8fa2-07c950be9c6d"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioSessionControl2
    {
        // IAudioSessionControl
        int GetState(out int state);
        int GetDisplayName(out IntPtr name);
        int SetDisplayName([MarshalAs(UnmanagedType.LPWStr)] string value, ref Guid context);
        int GetIconPath(out IntPtr path);
        int SetIconPath([MarshalAs(UnmanagedType.LPWStr)] string value, ref Guid context);
        int GetGroupingParam(out Guid group);
        int SetGroupingParam(ref Guid group, ref Guid context);
        int RegisterAudioSessionNotification(IntPtr client);
        int UnregisterAudioSessionNotification(IntPtr client);
        // IAudioSessionControl2
        int GetSessionIdentifier(out IntPtr id);
        int GetSessionInstanceIdentifier(out IntPtr id);
        int GetProcessId(out uint pid);
        int IsSystemSoundsSession();
        int SetDuckingPreference(bool optOut);
    }

    [Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface ISimpleAudioVolume
    {
        int SetMasterVolume(float level, ref Guid context);
        int GetMasterVolume(out float level);
        int SetMute(bool mute, ref Guid context);
        int GetMute(out bool mute);
    }

    static class Program
    {
        class Session { public uint Pid; public ISimpleAudioVolume Volume; }

        static List<Session> Sessions()
        {
            var list = new List<Session>();
            var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumerator();
            IMMDevice device;
            Marshal.ThrowExceptionForHR(enumerator.GetDefaultAudioEndpoint(0 /*eRender*/, 1 /*eMultimedia*/, out device));
            Guid iid = typeof(IAudioSessionManager2).GUID;
            object o;
            Marshal.ThrowExceptionForHR(device.Activate(ref iid, 23 /*CLSCTX_ALL*/, IntPtr.Zero, out o));
            var manager = (IAudioSessionManager2)o;
            IAudioSessionEnumerator sessions;
            Marshal.ThrowExceptionForHR(manager.GetSessionEnumerator(out sessions));
            int count;
            sessions.GetCount(out count);
            for (int i = 0; i < count; i++)
            {
                IAudioSessionControl2 control;
                if (sessions.GetSession(i, out control) != 0 || control == null) continue;
                uint pid;
                control.GetProcessId(out pid);
                list.Add(new Session { Pid = pid, Volume = (ISimpleAudioVolume)control });
            }
            return list;
        }

        static string NameOf(uint pid)
        {
            if (pid == 0) return "system sounds";
            try { return Process.GetProcessById((int)pid).ProcessName.ToLowerInvariant(); }
            catch { return ""; }
        }

        static int Main(string[] args)
        {
            try
            {
                string mode = args.Length > 0 ? args[0].ToLowerInvariant() : "list";
                Guid ctx = Guid.Empty;

                if (mode == "list")
                {
                    foreach (var s in Sessions())
                    {
                        bool m; s.Volume.GetMute(out m);
                        Console.WriteLine(s.Pid + "\t" + NameOf(s.Pid) + "\t" + (m ? "muted" : "on"));
                    }
                    return 0;
                }

                if (args.Length < 2) return 2;
                string stateFile = args[1];
                var muted = new HashSet<string>(File.Exists(stateFile)
                    ? File.ReadAllLines(stateFile).Select(l => l.Trim()).Where(l => l.Length > 0)
                    : new string[0]);

                if (mode == "mute")
                {
                    var keep = (args.Length > 2 ? args[2] : "").Split(',')
                        .Select(k => k.Trim().ToLowerInvariant()).Where(k => k.Length > 0).ToArray();
                    foreach (var s in Sessions())
                    {
                        string name = NameOf(s.Pid);
                        if (keep.Any(k => name.Contains(k))) continue;
                        bool m; s.Volume.GetMute(out m);
                        if (m) continue; // already muted by you: leave it alone, and don't unmute it later
                        s.Volume.SetMute(true, ref ctx);
                        muted.Add(s.Pid.ToString());
                        Console.WriteLine("muted\t" + s.Pid + "\t" + name);
                    }
                    File.WriteAllLines(stateFile, muted.ToArray());
                    return 0;
                }

                if (mode == "restore")
                {
                    foreach (var s in Sessions())
                    {
                        if (!muted.Contains(s.Pid.ToString())) continue;
                        s.Volume.SetMute(false, ref ctx);
                        Console.WriteLine("restored\t" + s.Pid + "\t" + NameOf(s.Pid));
                    }
                    if (File.Exists(stateFile)) File.Delete(stateFile);
                    return 0;
                }
                return 2;
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e.Message);
                return 1;
            }
        }
    }
}
