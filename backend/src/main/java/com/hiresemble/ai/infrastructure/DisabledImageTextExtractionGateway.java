package com.hiresemble.ai.infrastructure;

import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ImageTextExtractionGateway;

public final class DisabledImageTextExtractionGateway implements ImageTextExtractionGateway {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public AiGatewayResponse extract(ImageTextExtractionRequest request) {
        throw DisabledChatGateway.disabled();
    }
}
