/*
<LICENCE>

Copyright (c) 2008, University of Southampton
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

  *    Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

  *    Neither the name of the University of Southampton nor the names of its
    contributors may be used to endorse or promote products derived from this
    software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

</LICENCE>
*/

package uk.ac.ed.ph.jqtiplus.node.outcome.processing;

import uk.ac.ed.ph.jqtiplus.control.ProcessingContext;
import uk.ac.ed.ph.jqtiplus.exception.QTIProcessingInterrupt;
import uk.ac.ed.ph.jqtiplus.exception2.RuntimeValidationException;
import uk.ac.ed.ph.jqtiplus.group.outcome.processing.OutcomeElseGroup;
import uk.ac.ed.ph.jqtiplus.group.outcome.processing.OutcomeElseIfGroup;
import uk.ac.ed.ph.jqtiplus.group.outcome.processing.OutcomeIfGroup;
import uk.ac.ed.ph.jqtiplus.node.XmlObject;

import java.util.ArrayList;
import java.util.List;


/**
 * Implementation of if-elseif-else outcome rule (behaviour is same like in other programming languages).
 * <p>
 * If the expression given in the outcomeIf or outcomeElseIf evaluates to true then the sub-rules contained within it are
 * followed and any following outcomeElseIf or outcomeElse parts are ignored for this outcome condition.
 * <p>
 * If the expression given in the outcomeIf or outcomeElseIf does not evaluate to true then consideration passes to the
 * next outcomeElseIf or, if there are no more outcomeElseIf parts then the sub-rules of the outcomeElse are followed
 * (if specified).
 * 
 * @author Jiri Kajaba
 */
public class OutcomeCondition extends OutcomeRule
{
    private static final long serialVersionUID = 1L;
    
    /** Name of this class in xml schema. */
    public static final String CLASS_TAG = "outcomeCondition";

    /**
     * Constructs rule.
     *
     * @param parent parent of this rule
     */
    public OutcomeCondition(XmlObject parent)
    {
        super(parent);

        getNodeGroups().add(new OutcomeIfGroup(this));
        getNodeGroups().add(new OutcomeElseIfGroup(this));
        getNodeGroups().add(new OutcomeElseGroup(this));
    }

    @Override
    public String getClassTag()
    {
        return CLASS_TAG;
    }

    /**
     * Gets IF child.
     *
     * @return IF child
     * @see #setOutcomeIf
     */
    public OutcomeIf getOutcomeIf()
    {
        return getNodeGroups().getOutcomeIfGroup().getOutcomeIf();
    }

    /**
     * Sets new IF child.
     *
     * @param outcomeIf new IF child
     * @see #getOutcomeIf
     */
    public void setOutcomeIf(OutcomeIf outcomeIf)
    {
        getNodeGroups().getOutcomeIfGroup().setOutcomeIf(outcomeIf);
    }

    /**
     * Gets ELSE-IF children.
     *
     * @return ELSE-IF children
     */
    public List<OutcomeElseIf> getOutcomeElseIfs()
    {
        return getNodeGroups().getOutcomeElseIfGroup().getOutcomeElseIfs();
    }

    /**
     * Gets ELSE child.
     *
     * @return ELSE child
     * @see #setOutcomeElse
     */
    public OutcomeElse getOutcomeElse()
    {
        return getNodeGroups().getOutcomeElseGroup().getOutcomeElse();
    }

    /**
     * Sets new ELSE child.
     *
     * @param outcomeElse new ELSE child
     * @see #getOutcomeElse
     */
    public void setOutcomeElse(OutcomeElse outcomeElse)
    {
        getNodeGroups().getOutcomeElseGroup().setOutcomeElse(outcomeElse);
    }

    /**
     * Gets all children (IF, ELSE-IF, ELSE) in one ordered list.
     *
     * @return all children (IF, ELSE-IF, ELSE) in one ordered list
     */
    private List<OutcomeConditionChild> getConditionChildren()
    {
        List<OutcomeConditionChild> children = new ArrayList<OutcomeConditionChild>();

        children.add(getOutcomeIf());
        children.addAll(getOutcomeElseIfs());
        if (getOutcomeElse() != null)
            children.add(getOutcomeElse());

        return children;
    }

    @Override
    public void evaluate(ProcessingContext context) throws QTIProcessingInterrupt, RuntimeValidationException
    {
        for (OutcomeConditionChild child : getConditionChildren())
            if (child.evaluate(context))
                return;
    }
}
