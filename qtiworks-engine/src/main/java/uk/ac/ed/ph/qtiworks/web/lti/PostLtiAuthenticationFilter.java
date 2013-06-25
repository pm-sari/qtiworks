/* Copyright (c) 2012-2013, University of Edinburgh.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 *
 * * Neither the name of the University of Edinburgh nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * This software is derived from (and contains code from) QTItools and MathAssessEngine.
 * QTItools is (c) 2008, University of Southampton.
 * MathAssessEngine is (c) 2010, University of Edinburgh.
 */
package uk.ac.ed.ph.qtiworks.web.lti;

import uk.ac.ed.ph.qtiworks.domain.entities.LtiUser;
import uk.ac.ed.ph.qtiworks.services.base.IdentityService;
import uk.ac.ed.ph.qtiworks.web.authn.AbstractWebAuthenticationFilter;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;

/**
 * Authentication filter for resources previously invoked by an LTI launch.
 * <p>
 * This simply checks the session for the relevant attributes, previously set by the LTI
 * launch process.
 *
 * @author David McKain
 */
public final class PostLtiAuthenticationFilter extends AbstractWebAuthenticationFilter {

    private static final Logger logger = LoggerFactory.getLogger(PostLtiAuthenticationFilter.class);

    public static final String LTI_LAUNCH_DATA_ATTRIBUTE_NAME = "qtiworks.web.authn.lti.launchData";
    public static final String LTI_USER_ATTRIBUTE_NAME = "qtiworks.web.authn.lti.ltiUser";

    private IdentityService identityService;

    @Override
    protected void initWithApplicationContext(final FilterConfig filterConfig, final WebApplicationContext webApplicationContext)
            throws Exception {
        identityService = webApplicationContext.getBean(IdentityService.class);
    }

    @Override
    protected void doFilterAuthenticated(final HttpServletRequest request,
            final HttpServletResponse response, final FilterChain chain,
            final HttpSession session)
            throws IOException, ServletException {
        /* Make sure the required data has already been stored in the session */
        final LtiUser ltiUser = (LtiUser) session.getAttribute(LTI_USER_ATTRIBUTE_NAME);
        if (ltiUser==null) {
            logger.warn("Failed to extract {} from Session", LTI_USER_ATTRIBUTE_NAME);
            sendForbidden(response);
            return;
        }
        final LtiLaunchData ltiLaunchData = (LtiLaunchData) session.getAttribute(LTI_LAUNCH_DATA_ATTRIBUTE_NAME);
        if (ltiLaunchData==null) {
            logger.warn("Failed to extract {} from Session", LTI_LAUNCH_DATA_ATTRIBUTE_NAME);
            sendForbidden(response);
            return;
        }

        /* Copy data to the request */
        request.setAttribute(LTI_USER_ATTRIBUTE_NAME, ltiUser);
        request.setAttribute(LTI_LAUNCH_DATA_ATTRIBUTE_NAME, ltiLaunchData);

        /* Set up identity and continue */
        identityService.setCurrentThreadUser(ltiUser);
        try {
            chain.doFilter(request, response);
        }
        finally {
            identityService.setCurrentThreadUser(null);
        }
    }

    private void sendForbidden(final HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden. Please try launching this LTI tool again");
    }
}