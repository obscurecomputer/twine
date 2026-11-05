package computer.obscure.twine

import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

object TwineLogger {
    val DEBUG = CustomLevel("DEBUG", 500)
    val TRACE = CustomLevel("TRACE", 500)
    val INFO: Level = Level.INFO
    val WARNING: Level = Level.WARNING
    val SEVERE: Level = Level.SEVERE

    class CustomLevel(name: String, value: Int) : Level(name, value)

    private val logger = Logger.getLogger("Twine").apply {
        useParentHandlers = false
        val handler = ConsoleHandler()

        handler.formatter = object : SimpleFormatter() {
            override fun format(record: LogRecord): String {
                return "[Twine/${record.level}] ${record.message}\n"
            }
        }
        addHandler(handler)
    }

    var level: Level = INFO
        set(value) {
            field = value
            logger.level = value
            logger.handlers.forEach { it.level = value }
        }

    fun debug(message: String) = logger.log(DEBUG, message)
    fun trace(message: String) = logger.log(TRACE, message)

    fun info(message: String) = logger.log(INFO, message)
    fun warn(message: String) = logger.log(WARNING, message)
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.log(SEVERE, message, throwable)
        } else {
            logger.log(SEVERE, message)
        }
    }
}