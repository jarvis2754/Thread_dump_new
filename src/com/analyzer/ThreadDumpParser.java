package com.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.*;

public class ThreadDumpParser {

    // ── Header line patterns ─────────────────────────────────────────────────
    // Format: "Name" #N [osId] daemon prio=N os_prio=N cpu=N.Nms elapsed=N.Ns tid=0x... nid=N/0x... ...
    private static final Pattern P_NAME       = Pattern.compile("^\"(.+?)\"");
    private static final Pattern P_THREAD_NUM = Pattern.compile("#(\\d+)");
    // [644252] — OS thread ID in brackets (newer JDK format)
    private static final Pattern P_BRACKET_ID = Pattern.compile("\\[(\\d+)\\]");
    private static final Pattern P_PRIO       = Pattern.compile("\\bprio=(\\d+)");
    private static final Pattern P_OS_PRIO    = Pattern.compile("os_prio=(\\d+)");
    private static final Pattern P_CPU        = Pattern.compile("cpu=([\\d.]+)ms");
    // elapsed can be in seconds (elapsed=18271.47s) or ms (elapsed=18271470ms)
    private static final Pattern P_ELAPSED_S  = Pattern.compile("elapsed=([\\d.]+)s\\b");
    private static final Pattern P_ELAPSED_MS = Pattern.compile("elapsed=([\\d.]+)ms\\b");
    private static final Pattern P_TID        = Pattern.compile("tid=(0x[0-9a-fA-F]+)");
    // nid can be hex (nid=0x1a2b) or decimal (nid=644252)
    private static final Pattern P_NID_HEX    = Pattern.compile("nid=(0x[0-9a-fA-F]+)");
    private static final Pattern P_NID_DEC    = Pattern.compile("\\bnid=(\\d+)\\b");

    // ── State line ───────────────────────────────────────────────────────────
    private static final Pattern P_STATE      = Pattern.compile(
            "java\\.lang\\.Thread\\.State:\\s*(\\S+)\\s*(.*)");

    // ── Lock lines inside stack trace ────────────────────────────────────────
    private static final Pattern P_LOCK_WAIT  = Pattern.compile(
            "-\\s*waiting (?:to lock|on) <([^>]+)>(?:\\s*\\((.+?)\\))?");
    private static final Pattern P_LOCKED     = Pattern.compile(
            "-\\s*locked <([^>]+)>(?:\\s*\\((.+?)\\))?");
    private static final Pattern P_PARKING    = Pattern.compile(
            "-\\s*parking to wait for\\s*<([^>]+)>(?:\\s*\\((.+?)\\))?");

    public List<ThreadInfo> parse(String content) {
        List<ThreadInfo> threads = new ArrayList<>();
        String[] lines = content.split("\n");
        int i = 0;

        while (i < lines.length) {
            String line = lines[i].trim();
            Matcher nameMatcher = P_NAME.matcher(line);
            if (!nameMatcher.find()) { i++; continue; }

            ThreadInfo t = new ThreadInfo(nameMatcher.group(1));

            // ── Parse header fields ──────────────────────────────────────────
            extractInt(P_THREAD_NUM, line).ifPresent(v -> t.threadNum  = v);
            extractInt(P_PRIO,       line).ifPresent(v -> t.priority   = v);
            extractInt(P_OS_PRIO,    line).ifPresent(v -> t.osPriority = v);
            extractDouble(P_CPU,     line).ifPresent(v -> t.cpuMs      = v);
            extractStr(P_TID,        line).ifPresent(v -> t.tid        = v);
            t.daemon = line.contains(" daemon ");

            // elapsed: prefer seconds format (newer JDKs), fall back to ms
            boolean elapsedFound = false;
            Matcher elS = P_ELAPSED_S.matcher(line);
            if (elS.find()) {
                t.elapsedMs = Double.parseDouble(elS.group(1)) * 1000.0; // convert s → ms
                elapsedFound = true;
            }
            if (!elapsedFound) {
                Matcher elMs = P_ELAPSED_MS.matcher(line);
                if (elMs.find()) t.elapsedMs = Double.parseDouble(elMs.group(1));
            }

            // nid: try hex first, then decimal
            Matcher nidHex = P_NID_HEX.matcher(line);
            if (nidHex.find()) {
                String hex = nidHex.group(1);
                t.nid = hex;
                try { t.nidDecimal = String.valueOf(Long.parseLong(hex.substring(2), 16)); }
                catch (NumberFormatException ignored) {}
            } else {
                // decimal nid (newer JDK: nid=644252)
                Matcher nidDec = P_NID_DEC.matcher(line);
                if (nidDec.find()) {
                    String dec = nidDec.group(1);
                    t.nidDecimal = dec;
                    t.nid = "0x" + Long.toHexString(Long.parseLong(dec)); // also store as hex
                }
            }

            // [osId] bracket — some JDKs put OS thread ID here too; use as nidDecimal if not set
            if (t.nidDecimal.isEmpty()) {
                // skip the first bracket match if it's the thread number area
                Matcher bm = P_BRACKET_ID.matcher(line);
                // skip past #N first
                int searchFrom = 0;
                Matcher tnm = P_THREAD_NUM.matcher(line);
                if (tnm.find()) searchFrom = tnm.end();
                bm.region(searchFrom, line.length());
                if (bm.find()) {
                    t.nidDecimal = bm.group(1);
                    t.nid = "0x" + Long.toHexString(Long.parseLong(bm.group(1)));
                }
            }

            // CPU% only when both cpu and elapsed are present
            if (t.cpuMs >= 0 && t.elapsedMs > 0)
                t.cpuPercent = (t.cpuMs / t.elapsedMs) * 100.0;

            int j = i + 1;

            // Skip blank lines before state line
            while (j < lines.length && lines[j].trim().isEmpty()) j++;

            // Parse Thread.State line
            if (j < lines.length) {
                Matcher sm = P_STATE.matcher(lines[j].trim());
                if (sm.find()) {
                    t.state       = sm.group(1).trim();
                    t.stateDetail = sm.group(2).trim();
                    j++;
                }
            }

            // Collect stack trace + lock info
            StringBuilder stack   = new StringBuilder();
            StringBuilder lockBuf = new StringBuilder();

            while (j < lines.length) {
                String sl = lines[j];
                String st = sl.trim();
                if (st.isEmpty())                { j++; break; }
                if (st.startsWith("\""))         { break; }
                if (st.startsWith("JNI global")) { break; }
                if (st.startsWith("Found "))     { break; }

                matchLock(P_LOCK_WAIT, st, lockBuf, "waiting on");
                matchLock(P_LOCKED,    st, lockBuf, "locked");
                matchLock(P_PARKING,   st, lockBuf, "parking for");

                stack.append(sl).append("\n");
                j++;
            }

            t.stackTrace = stack.toString().trim();
            t.lockInfo   = lockBuf.toString().trim();

            t.computeHealth();
            threads.add(t);
            i = j;
        }

        return threads;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void matchLock(Pattern p, String line, StringBuilder buf, String label) {
        Matcher m = p.matcher(line);
        if (m.find()) {
            if (buf.length() > 0) buf.append("; ");
            buf.append(label).append(" <").append(m.group(1)).append(">");
            if (m.groupCount() >= 2 && m.group(2) != null) buf.append(" (").append(m.group(2)).append(")");
        }
    }

    private Optional<Double>  extractDouble(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Optional.of(Double.parseDouble(m.group(1))) : Optional.empty();
    }
    private Optional<Integer> extractInt(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Optional.of(Integer.parseInt(m.group(1))) : Optional.empty();
    }
    private Optional<String>  extractStr(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
