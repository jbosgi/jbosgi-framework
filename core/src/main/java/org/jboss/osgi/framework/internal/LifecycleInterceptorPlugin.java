package org.jboss.osgi.framework.internal;
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

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.interceptor.AbstractLifecycleInterceptorService;
import org.jboss.osgi.deployment.interceptor.InvocationContext;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorService;
import org.jboss.osgi.deployment.internal.InvocationContextImpl;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.spi.AttachmentSupport;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * A plugin that manages bundle lifecycle interceptors.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
final class LifecycleInterceptorPlugin extends AbstractService<LifecycleInterceptorPlugin> implements LifecycleInterceptorService {

    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private AbstractLifecycleInterceptorService delegate;
    private ServiceRegistration registration;

    static void addService(ServiceTarget serviceTarget) {
        LifecycleInterceptorPlugin service = new LifecycleInterceptorPlugin();
        ServiceBuilder<LifecycleInterceptorPlugin> builder = serviceTarget.addService(InternalServices.LIFECYCLE_INTERCEPTOR_PLUGIN, service);
        builder.addDependency(Services.FRAMEWORK_CREATE, BundleContext.class, service.injectedSystemContext);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private LifecycleInterceptorPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        final BundleContext systemContext = injectedSystemContext.getValue();
        delegate = new AbstractLifecycleInterceptorService(systemContext) {

            @Override
            protected InvocationContext getInvocationContext(Bundle bundle) {
                if (bundle == null)
                    throw MESSAGES.illegalArgumentNull("bundle");

                UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
                Deployment dep = userBundle.getDeployment();

                InvocationContext inv = dep.getAttachment(InvocationContext.class);
                if (inv == null) {
                    // TODO: support multiple roots defined in Bundle-ClassPath
                    RevisionContent revContent = userBundle.getFirstContentRoot();
                    VirtualFile rootFile = revContent != null ? revContent.getVirtualFile() : null;
                    LifecycleInterceptorAttachments att = new LifecycleInterceptorAttachments();
                    inv = new InvocationContextImpl(systemContext, userBundle, rootFile, att);
                    dep.addAttachment(InvocationContext.class, inv);
                }
                return inv;
            }
        };
        registration = systemContext.registerService(LifecycleInterceptorService.class.getName(), delegate, null);
        delegate.open();
    }

    @Override
    public void stop(StopContext context) {
        delegate.close();
        registration.unregister();
    }

    @Override
    public LifecycleInterceptorPlugin getValue() {
        return this;
    }

    /**
     * Invoke the registered set of interceptors for the given bundle state change.
     *
     * @param state The future state of the bundle
     * @param bundle The bundle that changes state
     * @throws LifecycleInterceptorException if the invocation of an interceptor fails
     */
    @Override
    public void handleStateChange(int state, Bundle bundle) {
        if (delegate != null)
            delegate.handleStateChange(state, bundle);
    }

    static class LifecycleInterceptorAttachments extends AttachmentSupport {
    }
}