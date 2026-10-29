package com.guild.app.dll;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Versao da DLL no formato numerico "1.2.3" (1 a 4 partes). */
public final class DllVersion {

    public static final Pattern FORMAT = Pattern.compile("\\d{1,5}(\\.\\d{1,5}){0,3}");
    private static final Pattern USER_AGENT = Pattern.compile("CerberusBags/(" + FORMAT.pattern() + ")");

    private DllVersion() {}

    /** "CerberusBags/1.1.0" -> "1.1.0"; qualquer outro cliente -> null. */
    public static String fromUserAgent(String userAgent) {
        if (userAgent == null) return null;
        Matcher matcher = USER_AGENT.matcher(userAgent);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static boolean isValid(String version) {
        return version != null && FORMAT.matcher(version).matches();
    }

    /** Compara parte a parte ("1.10" > "1.9"; "1.0" == "1.0.0"). */
    public static int compare(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? Integer.parseInt(left[i]) : 0;
            int r = i < right.length ? Integer.parseInt(right[i]) : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }
}
