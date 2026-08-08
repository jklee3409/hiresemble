package com.hiresemble.careerartifact.application;

import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;

public interface ResumeDocumentRenderer {

    RenderedOfficeFile render(ResumeContent content, CareerArtifactRenderProfile renderProfile);

    OfficeValidation validate(byte[] bytes);
}
