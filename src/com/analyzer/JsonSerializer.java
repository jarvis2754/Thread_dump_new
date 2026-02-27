package com.analyzer;

import java.util.List;

public class JsonSerializer {

    public static String toJson(List<ThreadInfo> threads) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < threads.size(); i++) {
            ThreadInfo t = threads.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"name\":"       ).append(str(t.name)        ).append(",")
              .append("\"threadNum\":"  ).append(t.threadNum         ).append(",")
              .append("\"state\":"      ).append(str(t.state)        ).append(",")
              .append("\"stateDetail\":").append(str(t.stateDetail)  ).append(",")
              .append("\"daemon\":"     ).append(t.daemon             ).append(",")
              .append("\"priority\":"   ).append(t.priority           ).append(",")
              .append("\"osPriority\":" ).append(t.osPriority         ).append(",")
              .append("\"tid\":"        ).append(str(t.tid)           ).append(",")
              .append("\"nid\":"        ).append(str(t.nid)           ).append(",")
              .append("\"nidDecimal\":" ).append(str(t.nidDecimal)    ).append(",")
              .append("\"cpuMs\":"      ).append(r2(t.cpuMs)          ).append(",")
              .append("\"elapsedMs\":"  ).append(r2(t.elapsedMs)      ).append(",")
              .append("\"cpuPercent\":" ).append(r2(t.cpuPercent)     ).append(",")
              .append("\"lockInfo\":"   ).append(str(t.lockInfo)      ).append(",")
              .append("\"health\":"     ).append(str(t.health)        ).append(",")
              .append("\"stackTrace\":" ).append(str(t.stackTrace)    )
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String str(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n",  "\\n")
                       .replace("\r",  "\\r")
                       .replace("\t",  "\\t") + "\"";
    }

    // -1 means "not available" — serialize as JSON null so the frontend can detect absence
    private static String r2(double v) {
        if (v < 0) return "null";
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }
}
