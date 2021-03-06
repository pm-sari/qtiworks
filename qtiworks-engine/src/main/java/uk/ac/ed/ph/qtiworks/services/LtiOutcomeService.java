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
package uk.ac.ed.ph.qtiworks.services;

import uk.ac.ed.ph.qtiworks.QtiWorksLogicException;
import uk.ac.ed.ph.qtiworks.config.beans.QtiWorksDeploymentSettings;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateSession;
import uk.ac.ed.ph.qtiworks.domain.entities.Delivery;
import uk.ac.ed.ph.qtiworks.domain.entities.LisOutcomeReportingStatus;
import uk.ac.ed.ph.qtiworks.domain.entities.LtiDomain;
import uk.ac.ed.ph.qtiworks.domain.entities.LtiUser;
import uk.ac.ed.ph.qtiworks.domain.entities.QueuedLtiOutcome;
import uk.ac.ed.ph.qtiworks.domain.entities.User;
import uk.ac.ed.ph.qtiworks.domain.entities.UserType;
import uk.ac.ed.ph.qtiworks.services.ScheduledService;
import uk.ac.ed.ph.qtiworks.services.base.AuditLogger;
import uk.ac.ed.ph.qtiworks.services.base.ServiceUtilities;
import uk.ac.ed.ph.qtiworks.services.dao.CandidateSessionDao;
import uk.ac.ed.ph.qtiworks.services.dao.QueuedLtiOutcomeDao;

import uk.ac.ed.ph.jqtiplus.internal.util.Assert;

import java.io.StringReader;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthServiceProvider;
import net.oauth.ParameterStyle;
import net.oauth.client.OAuthClient;
import net.oauth.client.httpclient4.HttpClient4;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.input.CharSequenceInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.InputSource;

/**
 * This service is responsible for sending outcome data back to LTI Tool Consumers.
 * <p>
 * The actual work of this service is performed asynchronously with some basic durability
 * provided by persisting the data to be sent within the entity model.
 *
 * @author David McKain
 */
@Service
@Transactional(propagation=Propagation.REQUIRED)
public class LtiOutcomeService {

    private static final Logger logger = LoggerFactory.getLogger(LtiOutcomeService.class);

    private static final String ENCODING = "UTF-8";

    @Resource
    private AuditLogger auditLogger;

    @Resource
    private CandidateSessionDao candidateSessionDao;

    @Resource
    private QueuedLtiOutcomeDao queuedLtiOutcomeDao;

    /**
     * Delays (in minutes) to wait until next retry. We try often to start with, then
     * less frequently. Then we give up.
     *
     * TODO: Maybe this should be configurable via {@link QtiWorksDeploymentSettings}?
     */
    private static final int[] retryDelays = new int[] {
            1, 5, 10, 60, 60, 60, 240, 240, 240
    };

    //-------------------------------------------------

    @Async
    public void queueLtiResult(final CandidateSession candidateSession, final double lisScore) {
        Assert.notNull(candidateSession);
        if (lisScore<0.0 || lisScore>1.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
        }

        /* Update session status to record final score and indicate results have been queued */
        candidateSession.setLisScore(Double.valueOf(lisScore));
        candidateSession.setLisOutcomeReportingStatus(LisOutcomeReportingStatus.TC_RETURN_SCHEDULED);
        candidateSessionDao.update(candidateSession);

        /* Persist new queued outcome */
        final QueuedLtiOutcome outcome = new QueuedLtiOutcome();
        outcome.setCandidateSession(candidateSession);
        outcome.setScore(lisScore);
        outcome.setFailureCount(0);
        queuedLtiOutcomeDao.persist(outcome);

        /* (Data will be sent to TC next time the service wakes up) */
        auditLogger.recordEvent("Queued new LTI outcome #" + outcome.getId()
                + " containing score " + lisScore
                + " to be returned to for CandidateSession #" + candidateSession.getId());
        logger.info("Queued new LTI outcome #{} containing score {} to be returned to for CandidateSession #{}",
                new Object[] { outcome.getId(), lisScore, candidateSession.getId() });
    }

    //-------------------------------------------------

    /**
     * Called by {@link ScheduledService} to attempt to send all queued outcomes deemed OK
     * to send next, taking into account retry/failure logic.
     */
    public int sendNextQueuedLtiOutcomes() {
        int failureCount = 0;
        final List<QueuedLtiOutcome> pendingOutcomes = queuedLtiOutcomeDao.getNextQueuedOutcomes();
        for (final QueuedLtiOutcome queuedLtiOutcome : pendingOutcomes) {
            final boolean successful = sendQueuedLtiOutcome(queuedLtiOutcome);
            if (!successful) {
                failureCount++;
            }
        }
        return failureCount;
    }

    /** Attempts to send ALL queued outcomes, regardless of their existing failure status
     * @return */
    public int sendAllQueuedLtiOutcomes() {
        int failureCount = 0;
        final List<QueuedLtiOutcome> pendingOutcomes = queuedLtiOutcomeDao.getAllQueuedOutcomes();
        for (final QueuedLtiOutcome queuedLtiOutcome : pendingOutcomes) {
            final boolean successful = sendQueuedLtiOutcome(queuedLtiOutcome);
            if (!successful) {
                failureCount++;
            }
        }
        return failureCount;
    }

    private boolean sendQueuedLtiOutcome(final QueuedLtiOutcome queuedLtiOutcome) {
        final CandidateSession candidateSession = queuedLtiOutcome.getCandidateSession();
        final boolean successful = trySendQueuedLtiOutcome(queuedLtiOutcome);
        if (successful) {
            /* Outcome sent successfully, so remove from queue */
            candidateSession.setLisOutcomeReportingStatus(LisOutcomeReportingStatus.TC_RETURN_SUCCESS);
            queuedLtiOutcomeDao.remove(queuedLtiOutcome);
            candidateSessionDao.update(candidateSession);
            auditLogger.recordEvent("Successfully sent LTI outcome #" + queuedLtiOutcome.getId()
                    + " to LIS outcome service at " + candidateSession.getLisOutcomeServiceUrl());
            logger.info("Successfully sent LTI outcome #{} to LIS outcome service at {}",
                    queuedLtiOutcome.getId(), candidateSession.getLisOutcomeServiceUrl());
        }
        else {
            /* Outcome failed. Retry up to limit of retries */
            final int failureCount = queuedLtiOutcome.getFailureCount();
            if (failureCount < retryDelays.length) {
                queuedLtiOutcome.setFailureCount(failureCount + 1);
                queuedLtiOutcome.setRetryTime(new Date(System.currentTimeMillis() + (1000L * 60 * retryDelays[failureCount])));
                candidateSession.setLisOutcomeReportingStatus(LisOutcomeReportingStatus.TC_RETURN_FAIL_TERMINAL);
                queuedLtiOutcomeDao.update(queuedLtiOutcome);
                candidateSessionDao.update(candidateSession);
                auditLogger.recordEvent("Failure #" + (failureCount+1)
                        + " to send LTI outcome #" + queuedLtiOutcome.getId()
                        + " to LIS outcome service at " + candidateSession.getLisOutcomeServiceUrl()
                        + ". Will try again at " + queuedLtiOutcome.getRetryTime());
                logger.warn("Failure #{} to send LTI outcome #{} to LIS outcome service at {}. Will try again at {}",
                        new Object[] { failureCount+1, queuedLtiOutcome.getId(), candidateSession.getLisOutcomeServiceUrl(), queuedLtiOutcome.getRetryTime() });
            }
            else {
                candidateSession.setLisOutcomeReportingStatus(LisOutcomeReportingStatus.TC_RETURN_FAIL_TERMINAL);
                queuedLtiOutcomeDao.remove(queuedLtiOutcome);
                candidateSessionDao.update(candidateSession);
                auditLogger.recordEvent("Final failure #" + (failureCount+1)
                        + " to send LTI outcome #" + queuedLtiOutcome.getId()
                        + " to LIS outcome service at " + candidateSession.getLisOutcomeServiceUrl()
                        + ". Outcome has been removed from queue");
                logger.error("Final failure #{} to send LTI outcome #{} to LIS outcome service at {}. Outcome has been removed from queue",
                        new Object[] { failureCount+1, queuedLtiOutcome.getId(), candidateSession.getLisOutcomeServiceUrl() });
            }
        }
        return successful;
    }

    private boolean trySendQueuedLtiOutcome(final QueuedLtiOutcome queuedLtiOutcome) {
        /* Extract relevant bits of info */
        final double score = queuedLtiOutcome.getScore();
        final CandidateSession candidateSession = queuedLtiOutcome.getCandidateSession();
        final User candidate = candidateSession.getCandidate();
        if (candidate.getUserType()!=UserType.LTI) {
            logger.warn("Candidate must be an LTI user - ignoring {}", queuedLtiOutcome);
            return false;
        }
        final LtiUser ltiCandidate = (LtiUser) candidate;
        final String ltiConsumerKey, ltiConsumerSecret;
        switch (ltiCandidate.getLtiLaunchType()) {
            case DOMAIN:
                final LtiDomain ltiDomain = ltiCandidate.getLtiDomain();
                ltiConsumerKey = ltiDomain.getConsumerKey();
                ltiConsumerSecret = ltiDomain.getConsumerSecret();
                break;

            case LINK:
                final Delivery delivery = ltiCandidate.getDelivery();
                ltiConsumerKey= delivery.getLtiConsumerKeyToken();
                ltiConsumerSecret = delivery.getLtiConsumerSecret();
                break;

            default:
                throw new QtiWorksLogicException("Unexpected switch case " + ltiCandidate.getLtiLaunchType());
        }

        /* Create POX XML envelope around message */
        final String poxMessage = buildPoxMessage(candidateSession, score);

        /* Compute hash for the message */
        final String bodyHash = computeBodyHash(poxMessage);

        /* Wrap as OAuth message */
        final OAuthServiceProvider serviceProvider = new OAuthServiceProvider(null, null, null);
        final OAuthConsumer consumer = new OAuthConsumer(null, ltiConsumerKey, ltiConsumerSecret, serviceProvider);
        final OAuthAccessor accessor = new OAuthAccessor(consumer);
        final String lisOutcomeServiceUrl = candidateSession.getLisOutcomeServiceUrl();
        final OAuthMessage oauthMessage;
        try {
            final Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("Content-Type", "text/xml; charset=" + ENCODING);
            parameters.put("oauth_body_hash", bodyHash);
            oauthMessage = accessor.newRequestMessage("POST",
                    lisOutcomeServiceUrl, parameters.entrySet(),
                    new CharSequenceInputStream(poxMessage, ENCODING));
        }
        catch (final Exception e) {
            logger.warn("Failed to construct OAuthMessage for reporting outcomes", e);
            return false;
        }

        /* Send message to TC result service endpoint */
        String responseBody;
        try {
            logger.debug("Attempting to send OAuth message {}", oauthMessage);
            final HttpClient4 httpClient4 = new HttpClient4();
            final OAuthClient client = new OAuthClient(httpClient4);
            final OAuthMessage result = client.invoke(oauthMessage, ParameterStyle.AUTHORIZATION_HEADER);
            responseBody = result.readBodyAsString();
        }
        catch (final Exception e) {
            logger.warn("Failed to send OAuthMessage {}", oauthMessage, e);
            return false;
        }

        /* Extract status */
        logger.debug("Received following result body from TP outcome service:\n{}", responseBody);
        final XPathFactory xPathFactory = XPathFactory.newInstance();
        final XPath xPath = xPathFactory.newXPath();
        xPath.setNamespaceContext(new PoxNamespaceContext());
        String resultStatus;
        try {
            resultStatus = xPath.evaluate("/x:imsx_POXEnvelopeResponse/x:imsx_POXHeader/x:imsx_POXResponseHeaderInfo/x:imsx_statusInfo/x:imsx_codeMajor",  new InputSource(new StringReader(responseBody)));
        }
        catch (final XPathExpressionException e) {
            throw QtiWorksLogicException.unexpectedException(e);
        }

        /* Expect status to be 'success' */
        final boolean successful = "success".equals(resultStatus);
        if (!successful) {
            logger.warn("Outcome service returned unsccessful response. message={}, responseBody={}", oauthMessage, responseBody);
        }
        return successful;
    }

    /**
     * Builds the appropriate POX message for sending the result back to the TC.
     */
    private String buildPoxMessage(final CandidateSession candidateSession, final double normalizedScore) {
        final String messageIdentifier = "QTIWORKS_RESULT_" + ServiceUtilities.createRandomAlphanumericToken(32);
        final String lisResultSourceDid = candidateSession.getLisResultSourcedid();
        return "<?xml version='1.0' encoding='" + ENCODING + "'?>\n"
                + "<imsx_POXEnvelopeRequest xmlns='http://www.imsglobal.org/services/ltiv1p1/xsd/imsoms_v1p0'>\n"
                + "  <imsx_POXHeader>\n"
                + "    <imsx_POXRequestHeaderInfo>\n"
                + "      <imsx_version>V1.0</imsx_version>\n"
                + "      <imsx_messageIdentifier>" + messageIdentifier + "</imsx_messageIdentifier>\n"
                + "    </imsx_POXRequestHeaderInfo>\n"
                + "  </imsx_POXHeader>\n"
                + "  <imsx_POXBody>\n"
                + "    <replaceResultRequest>\n"
                + "      <resultRecord>\n"
                + "        <sourcedGUID>\n"
                + "          <sourcedId>" + lisResultSourceDid + "</sourcedId>\n"
                + "        </sourcedGUID>\n"
                + "        <result>\n"
                + "          <resultScore>\n"
                + "            <language>en</language>\n"
                + "            <textString>" + normalizedScore + "</textString>\n"
                + "          </resultScore>\n"
                + "        </result>\n"
                + "      </resultRecord>\n"
                + "    </replaceResultRequest>\n"
                + "  </imsx_POXBody>\n"
                + "</imsx_POXEnvelopeRequest>\n\n";
    }

    /**
     * Computes the hash for the POX message body.
     * <p>
     * (The net.oauth library does not compute this for us!)
     *
     * @param poxMessage
     * @return
     */
    private String computeBodyHash(final String poxMessage) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA1");
            md.update(poxMessage.getBytes(ENCODING));
        }
        catch (final Exception e) {
            throw new QtiWorksLogicException("Unexpected failure computing body digest");
        }
        final byte[] output = Base64.encodeBase64(md.digest());
        return new String(output);
    }

    /**
     * Trivial implementation of {@link NamespaceContext} to handle POX messages
     */
    static final class PoxNamespaceContext implements NamespaceContext {

        public static final String POX_NAMESPACE_URI = "http://www.imsglobal.org/services/ltiv1p1/xsd/imsoms_v1p0";

        @Override
        public String getNamespaceURI(final String prefix) {
            return POX_NAMESPACE_URI;
        }

        @Override
        public String getPrefix(final String namespaceURI) {
            return "";
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Iterator getPrefixes(final String namespaceURI) {
            return Arrays.asList("").iterator();
        }
    }
}
