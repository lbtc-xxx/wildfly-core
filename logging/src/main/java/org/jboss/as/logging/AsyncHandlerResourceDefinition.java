/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.logging;

import static org.jboss.as.logging.CommonAttributes.ADD_HANDLER_OPERATION_NAME;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.FILTER_SPEC;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.REMOVE_HANDLER_OPERATION_NAME;

import java.util.Locale;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.AttributeTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.resolvers.OverflowActionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.handlers.AsyncHandler;
import org.jboss.logmanager.handlers.AsyncHandler.OverflowAction;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class AsyncHandlerResourceDefinition extends AbstractHandlerDefinition {

    public static final String ADD_SUBHANDLER_OPERATION_NAME = "assign-subhandler";
    public static final String REMOVE_SUBHANDLER_OPERATION_NAME = "unassign-subhandler";
    public static final String ASYNC_HANDLER = "async-handler";
    static final PathElement ASYNC_HANDLER_PATH = PathElement.pathElement(ASYNC_HANDLER);

    public static final PropertyAttributeDefinition QUEUE_LENGTH = PropertyAttributeDefinition.Builder.of("queue-length", ModelType.INT)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setPropertyName("queueLength")
            .setValidator(new IntRangeValidator(1, false))
            .build();

    public static final PropertyAttributeDefinition OVERFLOW_ACTION = PropertyAttributeDefinition.Builder.of("overflow-action", ModelType.STRING)
            .setAllowExpression(true)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        String content = resourceModel.get(attribute.getName()).asString().toLowerCase(Locale.ENGLISH);
                        writer.writeAttribute("value", content);
                        writer.writeEndElement();
                    }
                }
            })
            .setDefaultValue(new ModelNode(OverflowAction.BLOCK.name()))
            .setPropertyName("overflowAction")
            .setResolver(OverflowActionResolver.INSTANCE)
            .setValidator(EnumValidator.create(OverflowAction.class, false, false))
            .build();

    public static final LogHandlerListAttributeDefinition SUBHANDLERS = LogHandlerListAttributeDefinition.Builder.of("subhandlers")
            .setAllowExpression(false)
            .setAllowNull(true)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = {ENABLED, LEVEL, FILTER_SPEC, QUEUE_LENGTH, OVERFLOW_ACTION, SUBHANDLERS};


    public AsyncHandlerResourceDefinition(final boolean includeLegacyAttributes) {
        super(ASYNC_HANDLER_PATH, AsyncHandler.class, (includeLegacyAttributes ? Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES) : ATTRIBUTES), QUEUE_LENGTH);
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration registration) {
        super.registerOperations(registration);
        final ResourceDescriptionResolver resourceDescriptionResolver = getResourceDescriptionResolver();
        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ADD_SUBHANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setDeprecated(ModelVersion.create(1, 2, 0))
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), HandlerOperations.ADD_SUBHANDLER);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(REMOVE_SUBHANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setDeprecated(ModelVersion.create(1, 2, 0))
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), HandlerOperations.REMOVE_SUBHANDLER);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ADD_HANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), HandlerOperations.ADD_SUBHANDLER);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(REMOVE_HANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), HandlerOperations.REMOVE_SUBHANDLER);
    }

    @Override
    protected void registerResourceTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
        final AttributeTransformationDescriptionBuilder attributeBuilder = resourceBuilder.getAttributeBuilder();
        switch (modelVersion) {
            case VERSION_1_3_0: {
                // These attributes at some point made it on the resource model, but should have never been there. They
                // are not used by the handler and not persisted to the XML. Discarding them should have no effect.
                attributeBuilder.setDiscard(DiscardAttributeChecker.ALWAYS, FORMATTER, ENCODING);
                break;
            }
        }
        attributeBuilder.end();
    }
}
