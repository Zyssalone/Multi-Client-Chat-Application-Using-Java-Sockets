package com.csc3374.client.model;

// Copy of the server's message format. The two projects are separate Maven builds,
// so sharing one class would need a third module, which felt like overkill for a few constants.
//
//   client -> server : first line = username (empty = read-only), then chat text, "allUsers", "end"/"bye"
//   server -> client : WELCOME|name|FULL  or  WELCOME|name|READ_ONLY
//                      MSG|HH:mm|sender|text
//                      SYS|text
//                      USERS|name1,name2,...
public final class Protocol {
    public static final String WELCOME = "WELCOME";
    public static final String MSG = "MSG";
    public static final String SYS = "SYS";
    public static final String USERS = "USERS";

    public static final String READ_ONLY = "READ_ONLY";

    public static final String ALL_USERS_COMMAND = "allUsers";
    public static final String BYE = "bye";

    private Protocol() {
    }

    public static boolean isLeaveCommand(String text) {
        return text.equalsIgnoreCase("end") || text.equalsIgnoreCase("bye");
    }
}
