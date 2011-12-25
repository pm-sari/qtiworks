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

package uk.ac.ed.ph.jqtiplus.node;

import uk.ac.ed.ph.jqtiplus.attribute.value.IdentifierAttribute;
import uk.ac.ed.ph.jqtiplus.group.NodeGroup;
import uk.ac.ed.ph.jqtiplus.group.NodeGroupList;
import uk.ac.ed.ph.jqtiplus.node.item.AssessmentItem;
import uk.ac.ed.ph.jqtiplus.node.result.AssessmentResult;
import uk.ac.ed.ph.jqtiplus.node.test.AssessmentTest;
import uk.ac.ed.ph.jqtiplus.node.test.BranchRule;
import uk.ac.ed.ph.jqtiplus.types.Identifier;
import uk.ac.ed.ph.jqtiplus.validation.AttributeValidationError;
import uk.ac.ed.ph.jqtiplus.validation.ValidationResult;


/**
 * Parent of all xml objects.
 * 
 * @author Jiri Kajaba
 * @author Jonathon Hare
 */
public abstract class AbstractObject extends AbstractNode implements XmlObject
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs object.
     *
     * @param parent parent of constructed object (can be null for root objects)
     */
    public AbstractObject(XmlObject parent)
    {
        super(parent);
    }

    @Override
    public XmlObject getParent()
    {
        return (XmlObject) super.getParent();
    }

//    public void setParent(XmlObject parent)
//    {
//        super.setParent(parent);
//    }

    public AssessmentTest getParentTest()
    {
        XmlNode root = getParentRoot();
        if (root instanceof AssessmentTest)
            return (AssessmentTest) root;

        return null;
    }

    public AssessmentItem getParentItem()
    {
        XmlNode root = getParentRoot();
        if (root instanceof AssessmentItem)
            return (AssessmentItem) root;

        return null;
    }
    
    public AssessmentItemOrTest getParentItemOrTest() {
        XmlNode root = getParentRoot();
        if (root instanceof AssessmentItemOrTest) {
            return (AssessmentItemOrTest) root;
        }
        return null;
    }

    public AssessmentResult getParentResult()
    {
        XmlNode root = getParentRoot();
        if (root instanceof AssessmentResult)
            return (AssessmentResult) root;

        return null;
    }

    /** Helper method to validate a unique identifier (definition) attribute */
    protected void validateUniqueIdentifier(ValidationResult result, IdentifierAttribute identifierAttribute, Identifier identifier) {
        if (identifier!= null) {
            if (getParentTest() != null && BranchRule.isSpecial(identifier.toString())) {
                result.add(new AttributeValidationError(identifierAttribute, "Cannot uses this special target as identifier: " + identifierAttribute));
            }
            if (!validateUniqueIdentifier(getParentRoot(), identifier)) {
                result.add(new AttributeValidationError(identifierAttribute, "Duplicate identifier: " + identifierAttribute));
            }
        }
    }

    private boolean validateUniqueIdentifier(XmlNode parent, Object identifier) {
        if (parent != this && parent instanceof UniqueNode) {
            Object parentIdentifier = ((UniqueNode<?>) parent).getIdentifier();
            if (identifier.equals(parentIdentifier)) {
                return false;
            }
        }

        NodeGroupList groups = parent.getNodeGroups();
        for (int i = 0; i < groups.size(); i++) {
            NodeGroup group = groups.get(i);
            for (XmlNode child : group.getChildren())
                if (!validateUniqueIdentifier(child, identifier)) {
                    return false;
                }
        }

        return true;
    }
}
