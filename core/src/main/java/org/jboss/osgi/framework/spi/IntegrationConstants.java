/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.spi;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XAttachmentKey;

/**
 * A collection of propriatary constants.
 *
 * @author thomas.diesler@jboss.com
 * @since 08-Apr-2013
 */
public interface IntegrationConstants {

    /** The deployment attachment key */
    XAttachmentKey<Deployment> DEPLOYMENT_KEY = XAttachmentKey.create(Deployment.class);
    /** The metadata attachment key */
    XAttachmentKey<OSGiMetaData> OSGI_METADATA_KEY = XAttachmentKey.create(OSGiMetaData.class);
    /** The lock context attachment key */
    XAttachmentKey<LockContext> LOCK_CONTEXT_KEY = XAttachmentKey.create(LockContext.class);

}