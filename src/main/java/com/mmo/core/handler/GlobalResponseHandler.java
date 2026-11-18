package com.mmo.core.handler;

import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Compatibility advice retaining the existing global response envelope while
 * its implementation is owned by infrastructure.web.
 *
 * @deprecated Use the infrastructure-owned response handler for new code.
 */
@Deprecated(forRemoval = false)
@ControllerAdvice
public class GlobalResponseHandler extends com.mmo.infrastructure.web.GlobalResponseHandler {
}
