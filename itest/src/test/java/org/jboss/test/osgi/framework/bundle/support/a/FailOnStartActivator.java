/*
 * #%L
 * JBossOSGi Framework iTest
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
package org.jboss.test.osgi.framework.bundle.support.a;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A BundleActivator that fails on start.
 * 
 * @author thomas.diesler@jboss.com
 * @since 15-Dec-2009
 */
public class FailOnStartActivator implements BundleActivator {

    public void start(BundleContext context) throws Exception {
        ServiceReference sref = context.getServiceReference("org.jboss.test.osgi.framework.bundle.support.b.LifecycleService");
        if (sref == null)
            throw new IllegalStateException("Cannot obtain: LifecycleService");
    }

    public void stop(BundleContext context) throws Exception {
    }
}
