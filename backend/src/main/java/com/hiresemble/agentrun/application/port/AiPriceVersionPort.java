package com.hiresemble.agentrun.application.port;

import java.time.Instant;

/** Selects the immutable price catalog that applies to a newly created Agent Run. */
public interface AiPriceVersionPort {

    long currentPriceVersion(Instant effectiveAt);
}
