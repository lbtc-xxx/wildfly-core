/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment;

import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayIndex;
import org.jboss.as.server.services.security.AbstractVaultReader;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.value.InjectedValue;
import org.jboss.vfs.VirtualFile;

/**
 * The top-level service corresponding to a deployment unit.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RootDeploymentUnitService extends AbstractDeploymentUnitService {
    private final InjectedValue<DeploymentMountProvider> serverDeploymentRepositoryInjector = new InjectedValue<DeploymentMountProvider>();
    private final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<PathManager>();
    private final String name;
    private final String managementName;
    final InjectedValue<VirtualFile> contentsInjector = new InjectedValue<VirtualFile>();
    private final DeploymentUnit parent;
    private final ImmutableManagementResourceRegistration registration;
    private final ManagementResourceRegistration mutableRegistration;
    private Resource resource;
    private final AbstractVaultReader vaultReader;
    private final DeploymentOverlayIndex deploymentOverlays;

    /**
     * Construct a new instance.
     *
     * @param name the deployment unit simple name
     * @param managementName the deployment's domain-wide unique name
     * @param parent the parent deployment unit
     * @param registration the registration
     * @param mutableRegistration the mutable registration
     * @param resource the model
     * @param vaultReader the vault reader
     * @param deploymentOverlays the deployment overlays
     */
    public RootDeploymentUnitService(final String name, final String managementName, final DeploymentUnit parent, final ImmutableManagementResourceRegistration registration, final ManagementResourceRegistration mutableRegistration, Resource resource, final AbstractVaultReader vaultReader, DeploymentOverlayIndex deploymentOverlays) {
        assert name != null : "name is null";
        this.name = name;
        this.managementName = managementName;
        this.parent = parent;
        this.registration = registration;
        this.mutableRegistration = mutableRegistration;
        this.resource = resource;
        this.vaultReader = vaultReader;
        this.deploymentOverlays = deploymentOverlays;
    }

    protected DeploymentUnit createAndInitializeDeploymentUnit(final ServiceRegistry registry) {
        final DeploymentUnit deploymentUnit = new DeploymentUnitImpl(parent, name, registry);
        deploymentUnit.putAttachment(Attachments.RUNTIME_NAME, name);
        deploymentUnit.putAttachment(Attachments.MANAGEMENT_NAME, managementName);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_CONTENTS, contentsInjector.getValue());
        deploymentUnit.putAttachment(DeploymentModelUtils.REGISTRATION_ATTACHMENT, registration);
        deploymentUnit.putAttachment(DeploymentModelUtils.MUTABLE_REGISTRATION_ATTACHMENT, mutableRegistration);
        deploymentUnit.putAttachment(DeploymentModelUtils.DEPLOYMENT_RESOURCE, resource);
        deploymentUnit.putAttachment(Attachments.VAULT_READER_ATTACHMENT_KEY, vaultReader);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_OVERLAY_INDEX, deploymentOverlays);
        deploymentUnit.putAttachment(Attachments.PATH_MANAGER, pathManagerInjector.getValue());

        // Attach the deployment repo
        deploymentUnit.putAttachment(Attachments.SERVER_DEPLOYMENT_REPOSITORY, serverDeploymentRepositoryInjector.getValue());

        // For compatibility only
        addSVH(deploymentUnit);

        return deploymentUnit;
    }

    Injector<DeploymentMountProvider> getServerDeploymentRepositoryInjector() {
        return serverDeploymentRepositoryInjector;
    }

    InjectedValue<PathManager> getPathManagerInjector() {
        return pathManagerInjector;
    }

    @SuppressWarnings("deprecation")
    private static void addSVH(DeploymentUnit deploymentUnit) {
        deploymentUnit.putAttachment(Attachments.SERVICE_VERIFICATION_HANDLER, ServiceVerificationHandler.INSTANCE);
    }
}
