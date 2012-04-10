/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.BundleException;

/**
 * A bundle validator for OSGi R3.
 * 
 * @author thomas.diesler@jboss.com
 * @since 19-Dec-2009
 */
final class BundleValidatorR3 implements BundleValidator {

    @Override
    public void validateBundle(UserBundleState userBundle, OSGiMetaData osgiMetaData) throws BundleException {
        int manifestVersion = osgiMetaData.getBundleManifestVersion();
        if (manifestVersion != 1)
            throw MESSAGES.bundleUnsupportedBundleManifestVersion(manifestVersion, userBundle);
    }
}
