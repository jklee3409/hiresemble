package com.hiresemble.careerartifact.application;

import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;

public interface PortfolioPresentationRenderer {

    RenderedOfficeFile render(PortfolioContent content, CareerArtifactRenderProfile renderProfile);

    OfficeValidation validate(byte[] bytes);
}
