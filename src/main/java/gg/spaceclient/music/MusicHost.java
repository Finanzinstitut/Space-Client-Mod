package gg.spaceclient.music;

import gg.spaceclient.SpaceClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One PowerShell process, kept alive, that controls the music.
 *
 * Two things were wrong with pressing a media key per click. It was slow: every
 * press started a fresh PowerShell, and the first one also had to compile a C#
 * type - which is most of a second before anything happens. And it was aimed at
 * the wrong thing: a media key goes to whatever Windows currently considers the
 * media session, which is as easily a browser tab or a video as it is the
 * player the element is showing.
 *
 * This asks Windows for the session belonging to Spotify or Amazon Music by
 * name and tells that one directly. The process starts once, loads the WinRT
 * types once, and then sits reading commands - so the second press and every
 * one after it costs a line of text down a pipe.
 */
public final class MusicHost {

    private static Process process;
    private static BufferedWriter toHost;
    private static BufferedReader fromHost;

    private static volatile boolean broken = false;
    private static boolean hookAdded = false;
    private static volatile String status = "not started";

    private MusicHost() {}

    public static String status() { return status; }

    /**
     * Starts the process if it is not up yet.
     *
     * Worth calling when the music element is switched on rather than waiting
     * for the first press: starting is the slow part, and doing it while
     * somebody is looking at a button is exactly when it is felt.
     */
    public static synchronized void warm() {
        if (broken || alive()) return;
        start();
    }

    /** @return true when the command reached the player. */
    public static synchronized boolean send(String command) {
        if (broken) return false;
        if (!alive() && !start()) return false;

        try {
            toHost.write(command);
            toHost.newLine();
            toHost.flush();

            // The host answers one line per command, so a reply is also the
            // acknowledgement that it is still listening.
            String reply = fromHost.readLine();
            if (reply == null) {
                stop();
                return false;
            }
            status = reply.startsWith("ok") ? "ok" : reply;
            return reply.startsWith("ok");

        } catch (Throwable t) {
            status = "host failed: " + t.getMessage();
            stop();
            return false;
        }
    }

    private static boolean alive() {
        return process != null && process.isAlive() && toHost != null && fromHost != null;
    }

    private static boolean start() {
        try {
            Path script = writeScript();
            if (script == null) {
                broken = true;
                status = "script could not be written";
                return false;
            }

            process = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString())
                    .redirectErrorStream(true)
                    .start();

            toHost = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            fromHost = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));

            // The host says when it is ready. Waiting for that here is what
            // makes the first real press fast: the loading happens now.
            String hello = fromHost.readLine();
            if (hello == null || !hello.startsWith("ready")) {
                status = hello == null ? "host did not start" : hello;
                stop();
                broken = true;
                return false;
            }

            status = "ready";
            // Once per game, not once per start: a host that is restarted
            // would otherwise stack a hook for every attempt.
            if (!hookAdded) {
                hookAdded = true;
                Runtime.getRuntime().addShutdownHook(new Thread(MusicHost::stop));
            }
            return true;

        } catch (Throwable t) {
            status = "host would not start: " + t.getMessage();
            broken = true;
            return false;
        }
    }

    private static synchronized void stop() {
        try {
            if (process != null) process.destroyForcibly();
        } catch (Throwable ignored) {
            // Going away either way
        }
        process = null;
        toHost = null;
        fromHost = null;
    }

    /**
     * The host script: load the transport controls once, then read commands.
     *
     * The same Await shape MediaSession uses, because that one is known to work
     * on this machine - this is not the place to invent a second way of waiting
     * on a WinRT call.
     */
    private static Path writeScript() {
        try {
            Path path = Path.of(System.getProperty("java.io.tmpdir"), "spaceclient-musichost.ps1");

            String script = String.join("\n",
                    "$ErrorActionPreference = 'Stop'",
                    "try {",
                    "  Add-Type -AssemblyName System.Runtime.WindowsRuntime",
                    "  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() |",
                    "    Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and",
                    "      $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
                    "",
                    "  function Await($operation, $resultType) {",
                    "    $method = $asTask.MakeGenericMethod($resultType)",
                    "    $task = $method.Invoke($null, @($operation))",
                    "    if (-not $task.Wait(4000)) { throw 'media call timed out' }",
                    "    $task.Result",
                    "  }",
                    "",
                    "  $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,"
                            + "Windows.Media.Control,ContentType=WindowsRuntime]",
                    "  $manager = Await ($managerType::RequestAsync()) ([Windows.Media.Control."
                            + "GlobalSystemMediaTransportControlsSessionManager])",
                    "",
                    "  # Only these two. A media key would reach whatever Windows",
                    "  # last decided was the session, which is as easily a browser.",
                    "  function Target {",
                    "    $wanted = $null",
                    "    foreach ($s in $manager.GetSessions()) {",
                    "      $id = $s.SourceAppUserModelId.ToLower()",
                    "      if (-not ($id -like '*spotify*' -or $id -like '*amazon*')) { continue }",
                    "      if ($s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { return $s }",
                    "      if ($wanted -eq $null) { $wanted = $s }",
                    "    }",
                    "    return $wanted",
                    "  }",
                    "",
                    "  Write-Output 'ready'",
                    "",
                    "  while ($true) {",
                    "    $line = [Console]::In.ReadLine()",
                    "    if ($line -eq $null) { break }",
                    "    try {",
                    "      $session = Target",
                    "      if ($session -eq $null) { Write-Output 'err|no spotify or amazon session'; continue }",
                    "      # One line out per line in, always. 'continue' inside a",
                    "      # switch leaves the switch and not the loop, so an unknown",
                    "      # command would have printed its complaint AND 'ok' - two",
                    "      # answers to one question, and every reply after that",
                    "      # belongs to the press before it.",
                    "      $known = $true",
                    "      switch ($line.Trim()) {",
                    "        'toggle' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }",
                    "        'next'   { $null = Await ($session.TrySkipNextAsync()) ([bool]) }",
                    "        'prev'   { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }",
                    "        default  { $known = $false }",
                    "      }",
                    "      if ($known) { Write-Output 'ok' } else { Write-Output 'err|unknown command' }",
                    "    } catch {",
                    "      Write-Output ('err|' + $_.Exception.Message)",
                    "    }",
                    "  }",
                    "} catch {",
                    "  Write-Output ('err|' + $_.Exception.Message)",
                    "}");

            Files.writeString(path, script, StandardCharsets.UTF_8);
            return path;

        } catch (Throwable t) {
            SpaceClient.LOGGER.warn("Could not write the music host script: {}", t.getMessage());
            return null;
        }
    }
}
