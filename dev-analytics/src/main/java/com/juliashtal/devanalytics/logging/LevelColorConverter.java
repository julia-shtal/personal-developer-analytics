package com.juliashtal.devanalytics.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

/**
 * Logback composite converter that colorizes log levels:
 * ERROR → red (bold), WARN → yellow, INFO → green, DEBUG/TRACE → black.
 *
 * Register in {@code logback-spring.xml}:
 * <pre>{@code
 * <conversionRule conversionWord="levelColor"
 *                 class="com.juliashtal.devanalytics.logging.LevelColorConverter"/>
 * }</pre>
 * Then use {@code %levelColor(%-5level)} in your pattern.
 */
public class LevelColorConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return switch (event.getLevel().toInt()) {
            case Level.ERROR_INT -> ANSIConstants.BOLD + ANSIConstants.RED_FG;
            case Level.WARN_INT  -> ANSIConstants.YELLOW_FG;
            case Level.INFO_INT  -> ANSIConstants.GREEN_FG;
            default              -> ANSIConstants.BLACK_FG; // DEBUG, TRACE
        };
    }
}
