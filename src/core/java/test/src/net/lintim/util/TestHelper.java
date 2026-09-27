package net.lintim.util;

import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 */
public class TestHelper {

    public static void disableLogging() {
        Logger globalLogger = Logger.getLogger("");
        Handler[] handlers = globalLogger.getHandlers();
        for (Handler handler : handlers) {
            globalLogger.removeHandler(handler);
        }
    }
}
