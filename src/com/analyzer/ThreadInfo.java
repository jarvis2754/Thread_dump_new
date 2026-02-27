package com.analyzer;

/**
 * Represents a single thread parsed from a Java thread dump.
 *
 * Full header example:
 *   "ZLogsAgent-3" #45 daemon prio=5 os_prio=0 cpu=55553.41ms elapsed=123456.78ms tid=0x00007f1a2b3c nid=0x1a2b waiting on condition [0x...]
 *
 * Fields:
 *   name         - thread name (between quotes)
 *   threadNum    - #N  JVM-assigned serial number
 *   daemon       - presence of "daemon" keyword
 *   priority     - prio=N   JVM thread priority (1-10)
 *   osPriority   - os_prio=N  OS-level scheduling priority
 *   tid          - tid=0x...  JVM internal thread pointer
 *   nid          - nid=0x...  OS native thread ID (hex)
 *   nidDecimal   - nid in decimal — matches PID in `top -H` / `ps -eLf`
 *   cpuMs        - cpu=...ms  total CPU consumed since thread start (-1 = not in dump)
 *   elapsedMs    - elapsed=...ms wall-clock since thread start    (-1 = not in dump)
 *   cpuPercent   - cpuMs/elapsedMs*100  (-1 = not computable)
 *   state        - java.lang.Thread.State value
 *   stateDetail  - extra context e.g. "(sleeping)", "(on object monitor)"
 *   lockInfo     - waiting/locked/parking info extracted from stack lines
 *   health       - derived: HOT | ACTIVE | BLOCKED | IDLE
 *   stackTrace   - full "at ..." lines
 */
public class ThreadInfo {
    public String  name;
    public int     threadNum;      // -1 if not present
    public boolean daemon;
    public int     priority;       // -1 if not present
    public int     osPriority;     // -1 if not present
    public String  tid;            // "" if not present
    public String  nid;            // "" if not present
    public String  nidDecimal;     // "" if not present
    public double  cpuMs;          // -1 if not present
    public double  elapsedMs;      // -1 if not present
    public double  cpuPercent;     // -1 if not computable
    public String  state;
    public String  stateDetail;
    public String  lockInfo;
    public String  health;
    public String  stackTrace;

    public ThreadInfo(String name) {
        this.name        = name;
        this.threadNum   = -1;
        this.daemon      = false;
        this.priority    = -1;
        this.osPriority  = -1;
        this.tid         = "";
        this.nid         = "";
        this.nidDecimal  = "";
        this.cpuMs       = -1;
        this.elapsedMs   = -1;
        this.cpuPercent  = -1;
        this.state       = "UNKNOWN";
        this.stateDetail = "";
        this.lockInfo    = "";
        this.health      = "IDLE";
        this.stackTrace  = "";
    }

    public void computeHealth() {
        switch (state) {
            case "BLOCKED"  -> this.health = "BLOCKED";
            case "RUNNABLE" -> this.health = (cpuPercent > 50.0) ? "HOT" : "ACTIVE";
            default         -> this.health = "IDLE";
        }
    }
}
