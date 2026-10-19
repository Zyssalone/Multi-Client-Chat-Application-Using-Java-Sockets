package com.csc3374.server.model;

// Message format shared with the client. I went with plain text lines split by '|'
// instead of sending Java objects, mostly so I could test the server by hand with a raw socket.
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

    public static final String FULL_ACCESS = "FULL";
    public static final String READ_ONLY = "READ_ONLY";

    public static final String ALL_USERS_COMMAND = "allUsers";

    // limits so one client can't flood everyone else's screen
    public static final int MAX_NAME_LENGTH = 20;
    public static final int MAX_MESSAGE_LENGTH = 500;

    private Protocol() {
    }

    public static boolean isLeaveCommand(String text) {
        return text.equalsIgnoreCase("end") || text.equalsIgnoreCase("bye");
    }

    public static String build(String type, String... parts) {
        return type + "|" + String.join("|", parts);
    }
}
