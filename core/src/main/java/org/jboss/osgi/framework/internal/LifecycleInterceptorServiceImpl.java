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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.interceptor.AbstractInvocationContext;
import org.jboss.osgi.deployment.interceptor.AbstractLifecycleInterceptorService;
import org.jboss.osgi.deployment.interceptor.InvocationContext;
import org.jboss.osgi.spi.AttachmentSupport;
import org.jboss.osgi.spi.Attachments;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * A plugin that manages bundle lifecycle interceptors.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
public final class LifecycleInterceptorServiceImpl extends AbstractLifecycleInterceptorService {

	private final BundleContext systemContext;

    public LifecycleInterceptorServiceImpl(BundleContext systemContext) {
		this.systemContext = systemContext;
	}

    @Override
    protected InvocationContext getInvocationContext(Bundle bundle) {
        if (bundle == null)
            throw MESSAGES.illegalArgumentNull("bundle");

        UserBundleState<?> userBundle = UserBundleState.assertBundleState(bundle);
        Deployment dep = userBundle.getDeployment();

        InvocationContext inv = dep.getAttachment(InvocationContext.class);
        if (inv == null) {
            // TODO: support multiple roots defined in Bundle-ClassPath
            VirtualFile rootFile = userBundle.getDeployment().getRoot();
            Attachments att = new AttachmentSupport(){};
            inv = new AbstractInvocationContext(systemContext, userBundle, rootFile, att);
            dep.addAttachment(InvocationContext.class, inv);
        }
        return inv;
    }
}