/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
final class LifecycleInterceptorPlugin extends AbstractPluginService<LifecycleInterceptorPlugin> implements LifecycleInterceptorService {

    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private AbstractLifecycleInterceptorService delegate;
    private ServiceRegistration registration;

    static void addService(ServiceTarget serviceTarget) {
        LifecycleInterceptorPlugin service = new LifecycleInterceptorPlugin();
        ServiceBuilder<LifecycleInterceptorPlugin> builder = serviceTarget.addService(InternalServices.LIFECYCLE_INTERCEPTOR_PLUGIN, service);
        builder.addDependency(Services.SYSTEM_CONTEXT, BundleContext.class, service.injectedSystemContext);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private LifecycleInterceptorPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
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
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        registration.unregister();
        registration = null;
        delegate = null;
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