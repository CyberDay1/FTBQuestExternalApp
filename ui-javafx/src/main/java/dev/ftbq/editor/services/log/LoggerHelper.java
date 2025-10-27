package dev.ftbq.editor.services.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggerHelper {
    private LoggerHelper(){}
    public static Logger get(Class<?> cls){ return LoggerFactory.getLogger(cls); }
}


