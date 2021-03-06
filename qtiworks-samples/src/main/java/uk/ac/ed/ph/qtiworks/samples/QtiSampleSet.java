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
package uk.ac.ed.ph.qtiworks.samples;

import uk.ac.ed.ph.qtiworks.samples.QtiSampleAssessment.Feature;

import uk.ac.ed.ph.jqtiplus.internal.util.ObjectUtilities;
import uk.ac.ed.ph.jqtiplus.node.AssessmentObjectType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Encapsulates a set (actually a {@link List}) of QTI samples, with methods for
 * producing very specific subsets if required.
 *
 * @author David McKain
 */
public final class QtiSampleSet implements Serializable, Iterable<QtiSampleAssessment> {

    private static final long serialVersionUID = 5889801740586064839L;

    private final String title;
    private final String description;
    private final List<QtiSampleAssessment> assessments;

    public QtiSampleSet(final String title, final String description, final QtiSampleAssessment... assessments) {
        this(title, description, Arrays.asList(assessments));
    }

    public QtiSampleSet(final String title, final String description, final List<QtiSampleAssessment> assessments) {
        this.title = title;
        this.description = description;
        this.assessments = assessments;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public Iterator<QtiSampleAssessment> iterator() {
        return assessments.iterator();
    }

    public List<QtiSampleAssessment> getQtiSampleAssessments() {
        return assessments;
    }

    public QtiSampleSet havingType(final AssessmentObjectType type) {
        final List<QtiSampleAssessment> filtered = new ArrayList<QtiSampleAssessment>();
        for (final QtiSampleAssessment resource : assessments) {
            if (resource.getType()==type) {
                filtered.add(resource);
            }
        }
        return new QtiSampleSet(title, description, filtered);
    }

    public QtiSampleSet withoutFeatures(final QtiSampleAssessment.Feature... unwantedFeatures) {
        final List<QtiSampleAssessment> filtered = new ArrayList<QtiSampleAssessment>();
        for (final QtiSampleAssessment resource : assessments) {
            boolean hasUnwantedFeature = false;
            for (final Feature unwantedFeature : unwantedFeatures) {
                if (resource.getFeatures().contains(unwantedFeature)) {
                    hasUnwantedFeature = true;
                    break;
                }
            }
            if (!hasUnwantedFeature) {
                filtered.add(resource);
            }
        }
        return new QtiSampleSet(title, description, filtered);
    }

    @Override
    public String toString() {
        return ObjectUtilities.beanToString(this);
    }
}
