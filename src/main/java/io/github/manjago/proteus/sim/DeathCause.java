package io.github.manjago.proteus.sim;

/**
 * Причины смерти организма.
 */
public enum DeathCause {
    /** Слишком много ошибок выполнения */
    ERRORS,
    
    /** Убит Reaper'ом (по возрасту или другой метрике) */
    REAPED,
    
    /** Перезаписан другим организмом */
    OVERWRITTEN
}
