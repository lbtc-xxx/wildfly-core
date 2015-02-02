/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2014, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */
package org.jboss.as.test.integration.mgmt.access.extension;

import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class ConstrainedResource extends SimpleResourceDefinition {

    static final SensitivityClassification DS_SECURITY = new SensitivityClassification("rbac", "data-source-security", false, true, true);
    static final SensitiveTargetAccessConstraintDefinition DS_SECURITY_DEF = new SensitiveTargetAccessConstraintDefinition(DS_SECURITY);

    private static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder("password", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeGroup("security")
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .addAccessConstraint(DS_SECURITY_DEF)
            .build();

    static SimpleAttributeDefinition SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder("security-domain", ModelType.STRING)
            .setAllowExpression(true)
            .setAttributeGroup("security")
            .setAllowNull(true)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN_REF)
            .addAccessConstraint(DS_SECURITY_DEF)
            .build();

    static SimpleAttributeDefinition NEW_CONNECTION_SQL = new SimpleAttributeDefinitionBuilder("new-connection-sql", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    static SimpleAttributeDefinition JNDI_NAME = new SimpleAttributeDefinitionBuilder("jndi-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeGroup("naming")
            .setValidator(new ParameterValidator() {
                @Override
                public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
                    if (value.isDefined()) {
                        if (value.getType() != ModelType.EXPRESSION) {
                            String str = value.asString();
                            if (!str.startsWith("java:/") && !str.startsWith("java:jboss/")) {
                                throw new OperationFailedException("Jndi name have to start with java:/ or java:jboss/");
                            } else if (str.endsWith("/") || str.contains("//")) {
                                throw new OperationFailedException("Jndi name shouldn't include '//' or end with '/'");
                            }
                        }
                    } else {
                        throw new OperationFailedException("Jndi name is required");
                    }
                }

                @Override
                public void validateResolvedParameter(String parameterName, ModelNode value) throws OperationFailedException {
                    validateParameter(parameterName, value.resolve());
                }
            })
            .build();

    private final List<AccessConstraintDefinition> accessConstraints;

    public ConstrainedResource(PathElement pathElement) {
        super(pathElement, new NonResolvingResourceDescriptionResolver(),
                new AbstractAddStepHandler(PASSWORD, SECURITY_DOMAIN), ReloadRequiredRemoveStepHandler.INSTANCE);
        ApplicationTypeConfig atc = new ApplicationTypeConfig("rbac", "datasource");
        accessConstraints = new ApplicationTypeAccessConstraintDefinition(atc).wrapAsList();
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return accessConstraints;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(PASSWORD, null, new BasicAttributeWriteHandler(PASSWORD));
        resourceRegistration.registerReadOnlyAttribute(SECURITY_DOMAIN, null);
        resourceRegistration.registerReadOnlyAttribute(JNDI_NAME, null);
        resourceRegistration.registerReadWriteAttribute(NEW_CONNECTION_SQL, null, new BasicAttributeWriteHandler(NEW_CONNECTION_SQL));
    }

    private static class BasicAttributeWriteHandler extends AbstractWriteAttributeHandler<Void> {

        protected BasicAttributeWriteHandler(AttributeDefinition def) {
            super(def);
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, AbstractWriteAttributeHandler.HandbackHolder<Void> voidHandbackHolder) throws OperationFailedException {
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {

        }
    }
}
