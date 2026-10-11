package gg.spaceclient.music;

import gg.spaceclient.SpaceClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
 *
 * It also answers "what is playing" now. That used to start a fresh PowerShell
 * every two and a half seconds for as long as the element was on - loading
 * .NET and the WinRT projections each time, most of a second of a CPU core on
 * every poll, which showed up in game as a stutter on a steady beat. Asking the
 * process that is already running costs next to nothing.
 */
public final class MusicHost {

    private static Process process;
    private static BufferedWriter toHost;
    private static BufferedReader fromHost;

    /**
     * Lines from the host, read on a thread of their own. Reading straight
     * from the pipe could block forever if PowerShell ever hung, taking the
     * music poll down with it; from a queue every wait has a limit.
     */
    private static LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private static final String EOF = "\u0000eof";
    private static final long REPLY_TIMEOUT_MS = 8_000;

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
            String reply = next();
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

    /**
     * Asks the host what the players are showing: one line per session in the
     * same shape MediaSession's script writes, or null when the host cannot be
     * used - the caller then falls back to a one-off script.
     */
    public static synchronized List<String> read() {
        if (broken) return null;
        if (!alive() && !start()) return null;

        try {
            toHost.write("read");
            toHost.newLine();
            toHost.flush();

            List<String> out = new ArrayList<>();
            while (true) {
                String line = next();
                if (line == null) {
                    stop();
                    return null;
                }
                if (line.equals("end")) return out;
                if (line.startsWith("err|")) {
                    status = line;
                    // An answer, just not a good one: the session interface
                    // failed this time. Reported like the one-off script does.
                    out.add("ERROR|" + line.substring(4));
                    return out;
                }
                out.add(line);
            }
        } catch (Throwable t) {
            status = "host failed: " + t.getMessage();
            stop();
            return null;
        }
    }

    /**
     * Has the host write the cover of the playing track to a file.
     * @return the host's answer line, or null when it could not be asked
     */
    public static synchronized String thumbnail(Path target) {
        if (broken) return null;
        if (!alive() && !start()) return null;

        try {
            toHost.write("thumb|" + target);
            toHost.newLine();
            toHost.flush();
            String reply = next();
            if (reply == null) stop();
            return reply;
        } catch (Throwable t) {
            status = "host failed: " + t.getMessage();
            stop();
            return null;
        }
    }

    /** The next line from the host, or null on timeout or when it has gone. */
    private static String next() throws InterruptedException {
        String line = lines.poll(REPLY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (line == null) {
            status = "host stopped answering";
            return null;
        }
        return line == EOF ? null : line;
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

            // A queue per process, so nothing a dead one said can be read as
            // the answer to a question put to its replacement
            LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
            lines = queue;
            BufferedReader reader = fromHost;
            Thread pump = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) queue.add(line);
                } catch (Throwable ignored) {
                    // The process went away
                }
                queue.add(EOF);
            }, "Space Client music host");
            pump.setDaemon(true);
            pump.start();

            // The host says when it is ready. Waiting for that here is what
            // makes the first real press fast: the loading happens now.
            String hello = next();
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
            Path path = Path.of(System.getProperty("java.io.tmpdir"), "spaceclient-musichost-2.ps1");

            String script = String.join("\n",
                    "$ErrorActionPreference = 'Stop'",
                    "# UTF-8 both ways: track names in any script, and a temp path",
                    "# under a user name with an umlaut, arrive intact",
                    "try { [Console]::InputEncoding = [System.Text.Encoding]::UTF8 } catch { }",
                    "try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }",
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
                    "  $null = [Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime]",
                    "",
                    "  # What every player shows, one line each, then 'end'",
                    "  function Read-Sessions {",
                    "    foreach ($s in $manager.GetSessions()) {",
                    "      $appId = $s.SourceAppUserModelId",
                    "      $state = $s.GetPlaybackInfo().PlaybackStatus",
                    "      $p = Await ($s.TryGetMediaPropertiesAsync()) " +
                            "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
                    "      $pos = -1; $dur = -1; $age = 0",
                    "      try {",
                    "        $t = $s.GetTimelineProperties()",
                    "        if ($t -ne $null) {",
                    "          $pos = $t.Position.TotalSeconds",
                    "          $dur = $t.EndTime.TotalSeconds",
                    "          $age = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - " +
                            "$t.LastUpdatedTime.ToUnixTimeMilliseconds()",
                    "        }",
                    "      } catch { }",
                    "      $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()",
                    "      $artist = ($p.Artist -replace '[\\r\\n|]', ' ')",
                    "      $title = ($p.Title -replace '[\\r\\n|]', ' ')",
                    "      Write-Output (\"$appId|$state|$artist|$title|\" + $pos + '|' + $dur + '|' + $age + '|' + $now)",
                    "    }",
                    "    Write-Output 'end'",
                    "  }",
                    "",
                    "  # The cover of the session Target picks, written to a file",
                    "  function Write-Thumb($file) {",
                    "    $s = Target",
                    "    if ($s -eq $null) { return 'THUMB|none' }",
                    "    $p = Await ($s.TryGetMediaPropertiesAsync()) " +
                            "([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
                    "    if ($p.Thumbnail -eq $null) { return 'THUMB|none' }",
                    "    $stream = Await ($p.Thumbnail.OpenReadAsync()) " +
                            "([Windows.Storage.Streams.IRandomAccessStreamWithContentType])",
                    "    $size = [uint32]$stream.Size",
                    "    if ($size -le 0) { return 'THUMB|none' }",
                    "    $reader = [Windows.Storage.Streams.DataReader]::new($stream.GetInputStreamAt(0))",
                    "    $null = Await ($reader.LoadAsync($size)) ([uint32])",
                    "    $bytes = New-Object byte[] $size",
                    "    $reader.ReadBytes($bytes)",
                    "    [System.IO.File]::WriteAllBytes($file, $bytes)",
                    "    return ('THUMB|ok|' + $size)",
                    "  }",
                    "",
                    "  Write-Output 'ready'",
                    "",
                    "  while ($true) {",
                    "    $line = [Console]::In.ReadLine()",
                    "    if ($line -eq $null) { break }",
                    "    try {",
                    "      $cmd = $line.Trim()",
                    "      # Questions first: they must answer even with no player open",
                    "      if ($cmd -eq 'read') { Read-Sessions; continue }",
                    "      if ($cmd.StartsWith('thumb|')) { Write-Output (Write-Thumb $cmd.Substring(6)); continue }",
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
                    "      # A read that failed half way still owes its 'end' - the",
                    "      # err line is what the reader stops on",
                    "      Write-Output ('err|' + ($_.Exception.Message -replace '[\\r\\n]', ' '))",
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
