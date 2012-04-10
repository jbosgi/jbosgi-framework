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

import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.interceptor.AbstractLifecycleInterceptor;
import org.jboss.osgi.deployment.interceptor.InvocationContext;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptor;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * The lifecycle interceptor that verifies that deployments ending in '.war' have a WEB-INF/web.xml descriptor.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Oct-2009
 */
final class WebXMLVerifierInterceptor extends AbstractPluginService<WebXMLVerifierInterceptor> implements LifecycleInterceptor {

    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private LifecycleInterceptor delegate;
    private ServiceRegistration registration;

    static void addService(ServiceTarget serviceTarget) {
        WebXMLVerifierInterceptor service = new WebXMLVerifierInterceptor();
        ServiceBuilder<WebXMLVerifierInterceptor> builder = serviceTarget.addService(InternalServices.WEBXML_VERIFIER_PLUGIN, service);
        builder.addDependency(Services.SYSTEM_CONTEXT, BundleContext.class, service.injectedSystemContext);
        builder.addDependencies(Services.FRAMEWORK_CREATE, InternalServices.LIFECYCLE_INTERCEPTOR_PLUGIN);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private WebXMLVerifierInterceptor() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        delegate = new AbstractLifecycleInterceptor() {
            @Override
            public void invoke(int state, InvocationContext context) throws LifecycleInterceptorException {
                if (state == Bundle.STARTING) {
                    try {
                        VirtualFile root = context.getRoot();
                        if (root != null) {
                            VirtualFile webXML = root.getChild("/WEB-INF/web.xml");
                            String contextPath = (String) context.getBundle().getHeaders().get("Web-ContextPath");
                            boolean isWebApp = contextPath != null || root.getName().endsWith(".war");
                            if (isWebApp == true && webXML == null) {
                                throw MESSAGES.lifecycleInterceptorCannotObtainWebXML(root.toURL());
                            }
                        }
                    } catch (RuntimeException rte) {
                        throw rte;
                    } catch (Exception ex) {
                        throw MESSAGES.lifecycleInterceptorCannotObtainWebXML(ex);
                    }
                }
            }
        };
        BundleContext systemContext = injectedSystemContext.getValue();
        registration = systemContext.registerService(LifecycleInterceptor.class.getName(), delegate, null);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        registration.unregister();
        delegate = null;
        registration = null;
    }

    @Override
    public WebXMLVerifierInterceptor getValue() {
        return this;
    }

    @Override
    public Set<Class<?>> getInput() {
        return delegate.getInput();
    }

    @Override
    public Set<Class<?>> getOutput() {
        return delegate.getOutput();
    }

    @Override
    public int getRelativeOrder() {
        return delegate.getRelativeOrder();
    }

    @Override
    public void invoke(int state, InvocationContext context) throws LifecycleInterceptorException {
        delegate.invoke(state, context);
    }
}